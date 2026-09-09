package com.leehe.scpandroid.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailProvider {
    private val cache = LruCache<String, Bitmap>(64)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val RE_IMAGE = Regex("(?i)jpg|jpeg|png|gif|webp|bmp|heic|heif")
    private val RE_VIDEO = Regex("(?i)mp4|mkv|avi|mov|webm|flv|3gp|m4v|ts")

    fun clear() { cache.evictAll() }

    suspend fun getThumbnail(context: Context, file: File): Bitmap? = withContext(Dispatchers.IO) {
        loadThumbnail(context, file)
    }

    /** 异步加载，缓存命中时回调同步执行，否则在后台线程解码 */
    fun loadAsync(context: Context, file: File, onResult: (Bitmap?) -> Unit) {
        val key = file.absolutePath
        cache.get(key)?.let { onResult(it); return }
        scope.launch {
            val bmp = loadThumbnail(context, file)
            withContext(Dispatchers.Main) { onResult(bmp) }
        }
    }

    private fun loadThumbnail(context: Context, file: File): Bitmap? {
        val key = file.absolutePath
        cache.get(key)?.let { return it }

        val bitmap = when {
            file.extension.equals("apk", ignoreCase = true) -> loadApkIcon(context, file)
            RE_IMAGE.matches(file.extension) -> loadImageThumbnail(file)
            RE_VIDEO.matches(file.extension) -> loadVideoThumbnail(file)
            else -> null
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun loadApkIcon(context: Context, file: File): Bitmap? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
            if (info != null) {
                val appInfo = info.applicationInfo ?: return null
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                drawableToBitmap(appInfo.loadIcon(pm), 96)
            } else null
        } catch (e: Exception) { null }
    }

    private fun loadImageThumbnail(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 256)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) { null }
    }

    private fun loadVideoThumbnail(file: File): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            retriever.frameAtTime?.let { frame ->
                val scale = calculateInSampleSize(frame.width, frame.height, 256)
                if (scale > 1) Bitmap.createScaledBitmap(frame, frame.width / scale, frame.height / scale, true)
                else frame
            }
        } catch (e: Exception) { null } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    private fun calculateInSampleSize(w: Int, h: Int, maxSide: Int): Int {
        var scale = 1
        while (w / scale > maxSide || h / scale > maxSide) scale *= 2
        return scale
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable) {
            val b = drawable.bitmap
            if (b.width <= size && b.height <= size) return b
            return Bitmap.createScaledBitmap(b, size, size, true)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
