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
        if (file.isDirectory || file.length() == 0L) return file.length() == 0L

        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(4096)
                val read = input.read(buffer)
                if (read <= 0) return true

                var nullCount = 0
                for (i in 0 until read) {
                    val b = buffer[i].toInt() and 0xFF
                    if (b == 0) nullCount++
                    // 超过 3 个空字节很可能就是二进制
                    if (nullCount > 3) return false
                    // 包含非 ASCII 的控制字符（除常用外）判为二进制
                    if (b < 0x09 || (b in 0x0E..0x1F) || b == 0x7F) return false
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
            val msg = if (e is IllegalArgumentException && e.message?.contains("provider") == true)
                "FileProvider 配置错误" else "找不到支持此格式的应用"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteFile(file: File): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    fun copyFile(source: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
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
