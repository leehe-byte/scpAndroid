package com.leehe.scpandroid.utils

import android.content.Context
import com.leehe.scpandroid.models.NetworkStorage
import com.leehe.scpandroid.models.StorageType
import org.json.JSONArray
import org.json.JSONObject

object StoragePrefs {
    private const val PREFS_NAME = "network_storages"
    private const val KEY_STORAGES = "storages"

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
            obj.put("password", storage.password)
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
                val obj = array.getJSONObject(i)
                list.add(
                    NetworkStorage(
                        obj.getString("name"),
                        StorageType.valueOf(obj.getString("type")),
                        obj.getString("host"),
                        obj.getInt("port"),
                        obj.getString("username"),
                        obj.getString("password"),
                        obj.getString("rootPath")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
