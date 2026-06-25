package com.leehe.scpandroid.utils

import android.util.Log
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object ApkSignerUtils {
    private const val TAG = "ApkSignerUtils"

    /**
     * 使用 Android 调试密钥签名 APK
     * 如果调试密钥不可用，会直接返回 false
     */
    fun signWithDebugKey(inputApk: File, outputApk: File): Boolean {
        return try {
            val debugKeystore = KeyStore.getInstance("AndroidDebugKey")
            debugKeystore.load(null, null)
            val alias = "androiddebugkey"
            val password = "android".toCharArray()

            if (!debugKeystore.containsAlias(alias)) {
                Log.e(TAG, "Debug keystore does not contain alias: $alias")
                return false
            }

            val privateKey = debugKeystore.getKey(alias, password) as? PrivateKey
                ?: return false
            val cert = debugKeystore.getCertificate(alias) as? X509Certificate
                ?: return false

            val signerConfig = ApkSigner.SignerConfig.Builder("debug", privateKey, listOf(cert)).build()
            val builder = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)

            builder.build().sign()
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK signing failed", e)
            false
        }
    }
}
