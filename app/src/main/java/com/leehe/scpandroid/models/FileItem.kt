package com.leehe.scpandroid.models

import android.graphics.drawable.Drawable
import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val archivePath: String? = null,
    val appIcon: Drawable? = null,
    val packageName: String? = null
) {
    fun getFormattedSize(): String {
        if (isDirectory) return ""
        val b = size
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return "%.2f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.2f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
