package com.leehe.scpandroid.utils

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001

    fun init() {
        Shizuku.addBinderReceivedListener {
            Log.d(TAG, "Binder received successfully")
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
            return
        }

        val latch = CountDownLatch(1)
        var result = false
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == REQUEST_CODE) {
                    result = grantResult == PackageManager.PERMISSION_GRANTED
                    Shizuku.removeRequestPermissionResultListener(this)
                    latch.countDown()
                }
            }
        }

        try {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
            // 超时 30 秒后放弃等待
            latch.await(30, TimeUnit.SECONDS)
            callback(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            Shizuku.removeRequestPermissionResultListener(listener)
            callback(false)
        }
    }

    fun listFiles(path: String): List<RemoteFileItem> {
        val res = runCommand("ls -alF \"$path\"")
        if (res.startsWith("Error:") || res.startsWith("Exception:")) {
            Log.e(TAG, "Shizuku list error for $path: $res")
            return emptyList()
        }

        return res.split("\n")
            .filter { it.isNotBlank() && !it.startsWith("total") }
            .mapNotNull { line ->
                try {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 8) return@mapNotNull null
                    // 取最后一列为文件名 (从索引7开始，合并所有剩余部分)
                    val name = parts.drop(7).joinToString(" ")
                    val isDir = line.startsWith("d") || name.endsWith("/")
                    val cleanName = name.removeSuffix("*").removeSuffix("/")
                    val size = parts[4].toLongOrNull() ?: 0L
                    RemoteFileItem(cleanName, isDir, size)
                } catch (e: Exception) {
                    null
                }
            }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
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

            val stdoutBuf = ByteArrayOutputStream()
            val stderrBuf = ByteArrayOutputStream()

            val stdoutThread = Thread {
                try { process.inputStream.copyTo(stdoutBuf) } catch (_: Exception) {}
            }
            val stderrThread = Thread {
                try { process.errorStream.copyTo(stderrBuf) } catch (_: Exception) {}
            }
            stdoutThread.start()
            stderrThread.start()
            stdoutThread.join(30000)
            stderrThread.join(30000)
            process.waitFor()

            val stdout = stdoutBuf.toString(Charsets.UTF_8.name())
            val stderr = stderrBuf.toString(Charsets.UTF_8.name())

            if (stderr.isNotBlank()) "Error: $stderr" else stdout
        } catch (e: InvocationTargetException) {
            "Exception: ${e.targetException?.message ?: e.message}"
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}

data class RemoteFileItem(val name: String, val isDirectory: Boolean, val size: Long)
