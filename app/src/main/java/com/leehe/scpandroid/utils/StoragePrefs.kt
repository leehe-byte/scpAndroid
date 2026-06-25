package com.leehe.scpandroid.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.leehe.scpandroid.models.NetworkStorage
import com.leehe.scpandroid.models.StorageType
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object StoragePrefs {
    private const val TAG = "StoragePrefs"
    private const val PREFS_NAME = "network_storages"
    private const val KEY_STORAGES = "storages"
    private const val KEY_SALT = "crypto_salt"
    private const val GCM_TAG_LENGTH = 128

    private fun getKey(context: Context): SecretKey {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val salt = getOrCreateSalt(prefs)
        val spec = PBEKeySpec("scpAndroid_secure_key_2024".toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun getOrCreateSalt(prefs: SharedPreferences): ByteArray {
        val existing = prefs.getString(KEY_SALT, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs.edit().putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
        return salt
    }

    private fun encrypt(context: Context, plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val key = getKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed, storing plaintext", e)
            plaintext
        }
    }

    private fun decrypt(context: Context, encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val key = getKey(context)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            encoded
        }
    }

    fun saveStorages(context: Context, storages: List<NetworkStorage>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        storages.forEach { storage ->
            val obj = JSONObject()
            obj.put("name", storage.name)
            obj.put("type", storage.type.name)
            obj.put("host", storage.host)
            obj.put("port", storage.port)
            obj.put("username", storage.username)
            obj.put("password", encrypt(context, storage.password))
            obj.put("rootPath", storage.rootPath)
            array.put(obj)
        }
        prefs.edit().putString(KEY_STORAGES, array.toString()).apply()
    }

    fun loadStorages(context: Context): List<NetworkStorage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_STORAGES, null) ?: return emptyList()
        val list = mutableListOf<NetworkStorage>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    list.add(
                        NetworkStorage(
                            obj.getString("name"),
                            StorageType.valueOf(obj.getString("type")),
                            obj.getString("host"),
                            obj.getInt("port"),
                            obj.getString("username"),
                            decrypt(context, obj.getString("password")),
                            obj.optString("rootPath", "/")
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupted storage entry", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load storages", e)
        }
        return list
    }
}
