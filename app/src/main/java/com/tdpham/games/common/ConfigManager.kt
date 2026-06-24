package com.tdpham.games.common

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object ConfigManager {
    private val remoteConfig: FirebaseRemoteConfig? by lazy {
        try {
            Firebase.remoteConfig
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to obtain Firebase RemoteConfig: ${e.message}")
            null
        }
    }

    fun init() {
        val rc = remoteConfig ?: return
        try {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600
            }
            rc.setConfigSettingsAsync(configSettings)
            rc.setDefaultsAsync(mapOf(
                "is_ads_enabled" to true,
                "snake_speed" to 150L,
                "latest_version_code" to 7L
            ))
            rc.fetchAndActivate()
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to initialize RemoteConfig: ${e.message}")
        }
    }

    fun getLatestVersionCode(): Long {
        val rc = remoteConfig ?: return 7L
        return try {
            rc.getLong("latest_version_code")
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get latest_version_code: ${e.message}")
            7L
        }
    }

    fun getSnakeSpeed(): Long {
        val rc = remoteConfig ?: return 150L
        return try {
            val speed = rc.getLong("snake_speed")
            if (speed == 0L) 150L else speed
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get snake_speed: ${e.message}")
            150L
        }
    }

    fun isAdsEnabled(): Boolean {
        val rc = remoteConfig ?: return false
        return try {
            rc.getBoolean("is_ads_enabled")
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get is_ads_enabled: ${e.message}")
            false
        }
    }
}
