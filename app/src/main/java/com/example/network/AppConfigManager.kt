package com.example.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppConfigManager {
    private const val TAG = "AppConfigManager"
    private const val PREF_NAME = "homeair_app_config"
    private const val KEY_BACKEND_URL = "backend_api_url"

    // Default & Fallback Backend URLs
    const val DEFAULT_BACKEND_URL = "https://homeair-backend.up.railway.app"
    const val BACKUP_BACKEND_URL = "https://homeair-backend.hmair.xyz"

    // Firebase RTDB Config Endpoint
    const val RTDB_CONFIG_URL =
        "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/configs/appControl.json"

    // Direct Firebase RTDB Sports Events Endpoints
    const val RTDB_SPORTS_EVENTS_URL =
        "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/sportsEvents.json"
    const val RTDB_SPORTS_CONFIG_URL =
        "https://home-air-tv-xwdc-default-rtdb.asia-southeast1.firebasedatabase.app/configs/sportsEventsConfig.json"

    fun getBackendUrl(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
        var clean = saved.trim()
        if (!clean.endsWith("/")) {
            clean += "/"
        }
        return clean
    }

    fun setBackendUrl(context: Context, url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("onrender.com")) {
            cleanUrl = DEFAULT_BACKEND_URL
        }
        if (!cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND_URL, cleanUrl).apply()
        Log.d(TAG, "Dynamic Backend URL updated to: $cleanUrl")
    }

    /**
     * Fetches the latest backend URL from Firebase RTDB (configs/appControl.json)
     */
    suspend fun fetchLatestBackendUrl(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(RTDB_CONFIG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                if (jsonString.isNotBlank() && jsonString.trim() != "null") {
                    val jsonObject = JSONObject(jsonString)
                    val possibleKeys = listOf("backendApiUrl", "backend_api_url", "backendUrl", "serverUrl", "apiUrl")
                    for (key in possibleKeys) {
                        if (jsonObject.has(key)) {
                            val serverUrl = jsonObject.optString(key, "").trim()
                            if (serverUrl.isNotBlank() && serverUrl.startsWith("http") && !serverUrl.contains("onrender.com")) {
                                setBackendUrl(context, serverUrl)
                                return@withContext getBackendUrl(context)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching latest backend URL: ${e.message}")
        }
        return@withContext getBackendUrl(context)
    }
}
