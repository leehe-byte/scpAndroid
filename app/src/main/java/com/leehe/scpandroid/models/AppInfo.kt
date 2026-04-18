package com.leehe.scpandroid.models

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable?,
    val sourceDir: String,
    val uid: Int,
    val fileSize: Long,
    val versionName: String,
    val isSystemApp: Boolean
)
