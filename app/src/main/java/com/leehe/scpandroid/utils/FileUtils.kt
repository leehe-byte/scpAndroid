package com.leehe.scpandroid.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.leehe.scpandroid.models.FileItem
import java.io.File
import java.io.FileInputStream
import java.util.*

object FileUtils {
    
    /**
     * 通过读取文件头部字节判断是否为文本文件 (类似 file 命令原理)
     */
    fun isTextFile(file: File): Boolean {
        if (file.isDirectory) return false
        if (file.length() == 0L) return true
        
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read <= 0) return true
                
                var nullCount = 0
                for (i in 0 until read) {
                    val b = buffer[i].toInt()
                    // 检查是否存在空字符或大量非法控制字符 (二进制特征)
                    if (b == 0) nullCount++
                    if (nullCount > 2) return false
                }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取文件的 MIME 类型
     */
    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    /**
     * 调用系统 Intent 打开文件
     */
    fun openWithSystem(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val mime = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "找不到支持此格式的应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFile(file: File): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    fun copyFile(source: File, destDir: File): Boolean {
        return try {
            val destFile = File(destDir, source.name)
            if (source.isDirectory) source.copyRecursively(destFile, overwrite = true)
            else source.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) { false }
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceAtMost(3)
        return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
