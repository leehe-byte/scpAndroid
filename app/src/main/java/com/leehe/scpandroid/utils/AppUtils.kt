package com.leehe.scpandroid.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.leehe.scpandroid.models.AppInfo
import java.io.File

object AppUtils {
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appInfoList = mutableListOf<AppInfo>()

        for (app in apps) {
            val label = app.loadLabel(pm).toString()
            val packageName = app.packageName
            val icon = app.loadIcon(pm)
            val sourceDir = app.sourceDir ?: continue
            val file = File(sourceDir)
            val size = if (file.exists()) file.length() else 0
            val uid = app.uid
            
            val packageInfo = try {
                pm.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                null
            }
            
            val versionName = packageInfo?.versionName ?: "Unknown"
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            appInfoList.add(
                AppInfo(
                    appName = label,
                    packageName = packageName,
                    appIcon = icon,
                    sourceDir = sourceDir,
                    uid = uid,
                    fileSize = size,
                    versionName = versionName,
                    isSystemApp = isSystemApp
                )
            )
        }
        return appInfoList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }

    fun extractApk(context: Context, appInfo: AppInfo, targetDir: File): Boolean {
        val sourceFile = File(appInfo.sourceDir)
        if (!sourceFile.exists()) return false
        
        val targetFile = File(targetDir, "${appInfo.appName}_${appInfo.versionName}.apk")
        return try {
            sourceFile.copyTo(targetFile, overwrite = true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
