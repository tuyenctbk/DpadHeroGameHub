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
        } catch (e: Throwable) {
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
                "ads_min_days" to 100L,
                "ads_min_opens" to 10L,
                "ads_min_session_seconds" to 10L,
                "snake_speed" to 150L,
                "latest_version_code" to 1L,
                "min_version_code" to 1L
            ))
            rc.fetchAndActivate()
        } catch (e: Throwable) {
            Log.e("ConfigManager", "Failed to initialize RemoteConfig: ${e.message}")
        }
    }

    fun getLatestVersionCode(): Long {
        val rc = remoteConfig ?: return 0L
        return try {
            rc.getLong("latest_version_code")
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get latest_version_code: ${e.message}")
            0L
        }
    }

    fun getMinVersionCode(): Long {
        val rc = remoteConfig ?: return 0L
        return try {
            rc.getLong("min_version_code")
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get min_version_code: ${e.message}")
            0L
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

    fun getAdsMinDays(): Int {
        val rc = remoteConfig ?: return 100
        return try {
            rc.getLong("ads_min_days").toInt()
        } catch (_: Exception) {
            100
        }
    }

    fun getAdsMinOpens(): Int {
        val rc = remoteConfig ?: return 10
        return try {
            rc.getLong("ads_min_opens").toInt()
        } catch (_: Exception) {
            10
        }
    }

    fun getAdsMinSessionSeconds(): Int {
        val rc = remoteConfig ?: return 10
        return try {
            rc.getLong("ads_min_session_seconds").toInt()
        } catch (_: Exception) {
            10
        }
    }
}
