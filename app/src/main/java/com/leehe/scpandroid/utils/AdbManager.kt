package com.leehe.scpandroid.utils

import android.content.Context
import android.util.Log
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.cgutman.adblib.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object AdbManager {
    private const val TAG = "AdbManager"
    private var crypto: AdbCrypto? = null

    private fun getCrypto(context: Context): AdbCrypto {
        if (crypto != null) return crypto!!
        
        val privKeyFile = File(context.filesDir, "adb_priv.key")
        val pubKeyFile = File(context.filesDir, "adb_pub.key")
        val base64 = AdbBase64Impl()

        crypto = if (privKeyFile.exists() && pubKeyFile.exists()) {
            try {
                AdbCrypto.loadAdbKeyPair(base64, privKeyFile, pubKeyFile)
            } catch (e: Exception) {
                generateAndSaveKeyPair(base64, privKeyFile, pubKeyFile)
            }
        } else {
            generateAndSaveKeyPair(base64, privKeyFile, pubKeyFile)
        }
        return crypto!!
    }

    private fun generateAndSaveKeyPair(base64: AdbBase64Impl, priv: File, pub: File): AdbCrypto {
        return try {
            val newCrypto = AdbCrypto.generateAdbKeyPair(base64)
            newCrypto.saveAdbKeyPair(priv, pub)
            newCrypto
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate/save ADB keys", e)
            AdbCrypto.generateAdbKeyPair(base64)
        }
    }

    suspend fun runShellCommand(context: Context, host: String, port: Int, command: String): String = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var connection: AdbConnection? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            connection = AdbConnection.create(socket, getCrypto(context))
            
            // 这一步会处理握手和授权
            connection.connect()
            
            val stream = connection.open("shell:$command")
            val output = StringBuilder()
            
            while (!stream.isClosed) {
                val data = stream.read()
                if (data != null && data.isNotEmpty()) {
                    output.append(String(data))
                } else {
                    break 
                }
            }
            output.toString()
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            Log.e(TAG, "ADB Shell failed: $msg")
            if (msg.contains("closed", ignoreCase = true)) {
                "Error: 连接被关闭。请检查手机是否已弹出授权对话框并确认。"
            } else {
                "Error: $msg"
            }
        } finally {
            try { connection?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    suspend fun pushFileContent(context: Context, host: String, port: Int, content: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val escapedContent = content.replace("'", "'\\''")
        val res = runShellCommand(context, host, port, "echo '$escapedContent' > \"$remotePath\"")
        !res.startsWith("Error")
    }

    suspend fun pullFileContent(context: Context, host: String, port: Int, remotePath: String): String {
        return runShellCommand(context, host, port, "cat \"$remotePath\"")
    }
}
