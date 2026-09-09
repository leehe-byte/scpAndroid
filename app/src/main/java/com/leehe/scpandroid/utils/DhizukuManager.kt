package com.leehe.scpandroid.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Dhizuku 免 Root 提权管理器
 * 原理：Dhizuku 借助 Android Device Owner API 获得系统级权限，
 * 通过 Binder IPC 向客户端应用暴露 shell 执行能力。
 *
 * 使用前需要安装 Dhizuku APK：
 * https://github.com/rosan/dhizuku
 *
 * 激活命令（一次性）：
 * adb shell dpm set-device-owner com.rosan.dhizuku/.server.DhizukuService
 */
object DhizukuManager {
    private const val TAG = "DhizukuManager"

    private val PACKAGE_DHIZUKU = "com.rosan.dhizuku"

    @Volatile private var initialized = false
    @Volatile private var available = false
    @Volatile private var dhizukuMethod: java.lang.reflect.Method? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            val pm = context.packageManager
            pm.getPackageInfo(PACKAGE_DHIZUKU, 0)
            available = true
            Log.d(TAG, "Dhizuku 已安装")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Dhizuku 未安装，跳过")
            available = false
        }
    }

    fun isAvailable(): Boolean = available

    /**
     * 引导用户安装/激活 Dhizuku
     */
    fun getSetupIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            val uri = android.net.Uri.parse("https://github.com/rosan/dhizuku/releases")
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 通过 Dhizuku ContentProvider 执行 shell 命令
     * 免 Root，无需 ADB 连接
     */
    fun runCommand(command: String): String {
        if (!available) return "Error: Dhizuku 不可用"
        return try {
            var m = dhizukuMethod
            if (m == null) {
                val clazz = Class.forName("com.rosan.dhizuku.api.DhizukuRequest")
                m = clazz.getMethod("runShellCommand", String::class.java)
                dhizukuMethod = m
            }
            m.invoke(null, command)?.toString() ?: "Error: 命令返回空"
        } catch (e: ClassNotFoundException) {
            "Error: Dhizuku API 类未找到，请添加依赖 implementation('com.rosan.dhizuku:api:latest')"
        } catch (e: Exception) {
            Log.e(TAG, "Dhizuku 命令执行失败", e)
            "Error: ${e.message}"
        }
    }
}
