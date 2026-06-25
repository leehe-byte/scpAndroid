package com.leehe.scpandroid.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.cgutman.adblib.AdbConnection
import com.cgutman.adblib.AdbCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

object AdbManager {
    private const val TAG = "AdbManager"
    private var crypto: AdbCrypto? = null

    @Volatile
    private var cryptoInitFailed = false

    @Synchronized
    private fun getCrypto(context: Context): AdbCrypto? {
        if (cryptoInitFailed) return null
        crypto?.let { return it }

        val privKeyFile = File(context.filesDir, "adb_priv.key")
        val pubKeyFile = File(context.filesDir, "adb_pub.key")
        val base64 = AdbBase64Impl()

        try {
            crypto = if (privKeyFile.exists() && pubKeyFile.exists()) {
                try {
                    AdbCrypto.loadAdbKeyPair(base64, privKeyFile, pubKeyFile)
                } catch (e: Exception) {
                    generateAndSaveKeyPair(base64, privKeyFile, pubKeyFile)
                }
            } else {
                generateAndSaveKeyPair(base64, privKeyFile, pubKeyFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal: cannot initialize ADB crypto", e)
            cryptoInitFailed = true
            return null
        }
        return crypto
    }

    private fun generateAndSaveKeyPair(base64: AdbBase64Impl, priv: File, pub: File): AdbCrypto? {
        return try {
            val newCrypto = AdbCrypto.generateAdbKeyPair(base64)
            try {
                newCrypto.saveAdbKeyPair(priv, pub)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save ADB key pair, continuing in-memory", e)
            }
            newCrypto
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate ADB keys", e)
            null
        }
    }

    suspend fun runShellCommand(context: Context, host: String, port: Int, command: String): String = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var connection: AdbConnection? = null
        try {
            val keyCrypto = getCrypto(context)
                ?: return@withContext "Error: ADB 密钥初始化失败"

            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            connection = AdbConnection.create(socket, keyCrypto)
            connection.connect()

            val stream = connection.open("shell:$command")
            val output = StringBuilder()
            var stuckCounter = 0

            while (!stream.isClosed) {
                val data = stream.read()
                if (data != null && data.isNotEmpty()) {
                    output.append(String(data, Charsets.UTF_8))
                    stuckCounter = 0
                } else {
                    stuckCounter++
                    if (stuckCounter > 10) break
                    Thread.sleep(50)
                }
            }
            output.toString()
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            Log.e(TAG, "ADB Shell failed: $msg")
            if (msg.contains("closed", ignoreCase = true)) {
                "Error: 连接被关闭，请检查设备授权"
            } else {
                "Error: $msg"
            }
        } finally {
            try { connection?.close() } catch (e: Exception) { Log.w(TAG, "Close connection failed", e) }
            try { socket?.close() } catch (e: Exception) { Log.w(TAG, "Close socket failed", e) }
        }
    }

    suspend fun pushFileContent(context: Context, host: String, port: Int, content: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val res = runShellCommand(context, host, port, "echo '$b64' | base64 -d > \"$remotePath\"")
        !res.startsWith("Error")
    }

    suspend fun pullFileContent(context: Context, host: String, port: Int, remotePath: String): String {
        val res = runShellCommand(context, host, port, "base64 \"$remotePath\"")
        if (res.startsWith("Error")) return res
        return try {
            String(Base64.decode(res.trim(), Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            "Error: 解码失败 - ${e.message}"
        }
    }
}
