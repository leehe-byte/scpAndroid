package com.leehe.scpandroid.utils

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.InvocationTargetException

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001
    
    private var isBinderReceived = false

    fun init() {
        Shizuku.addBinderReceivedListener {
            Log.d(TAG, "Binder received successfully")
            isBinderReceived = true
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Exception) { false }
    }

    fun checkPermission(callback: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) {
            callback(false)
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            callback(true)
        } else {
            Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode == REQUEST_CODE) {
                        Shizuku.removeRequestPermissionResultListener(this)
                        callback(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            })
            try {
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Shizuku permission", e)
                callback(false)
            }
        }
    }

    fun listFiles(path: String): List<RemoteFileItem> {
        val res = runCommand("ls -alF \"$path\"")
        if (res.startsWith("Error:") || res.startsWith("Exception:")) {
            Log.e(TAG, "ADB list error for $path: $res")
            return emptyList()
        }
        
        return res.split("\n")
            .filter { it.isNotBlank() && !it.startsWith("total") }
            .mapNotNull { line ->
                try {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 8) return@mapNotNull null
                    var name = parts.last()
                    val isDir = line.startsWith("d") || name.endsWith("/")
                    name = name.removeSuffix("*").removeSuffix("/")
                    val size = parts[4].toLongOrNull() ?: 0L
                    RemoteFileItem(name, isDir, size)
                } catch (e: Exception) {
                    null
                }
            }
    }

    fun runCommand(command: String): String {
        return try {
            val shizukuClass = Shizuku::class.java
            val method = shizukuClass.getDeclaredMethod(
                "newProcess", 
                Array<String>::class.java, 
                Array<String>::class.java, 
                String::class.java
            )
            method.isAccessible = true
            
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as ShizukuRemoteProcess
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            if (error.isNotBlank()) "Error: $error" else output
        } catch (e: InvocationTargetException) {
            "Exception: ${e.targetException?.message ?: e.message}"
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}

data class RemoteFileItem(val name: String, val isDirectory: Boolean, val size: Long)
