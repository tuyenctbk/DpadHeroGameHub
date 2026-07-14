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
                "ads_min_days" to 20L,
                "ads_min_opens" to 3L,
                "ads_min_session_seconds" to 30L,
                "ads_min_interval_ms" to 120000L,
                "ads_max_per_session" to 3L,
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
        val rc = remoteConfig ?: return 20
        return try {
            rc.getLong("ads_min_days").toInt()
        } catch (_: Exception) {
            20
        }
    }

    fun getAdsMinOpens(): Int {
        val rc = remoteConfig ?: return 3
        return try {
            rc.getLong("ads_min_opens").toInt()
        } catch (_: Exception) {
            3
        }
    }

    fun getAdsMinSessionSeconds(): Int {
        val rc = remoteConfig ?: return 30
        return try {
            rc.getLong("ads_min_session_seconds").toInt()
        } catch (_: Exception) {
            30
        }
    }

    fun getAdsMinIntervalMs(): Long {
        val rc = remoteConfig ?: return 120000L
        return try {
            rc.getLong("ads_min_interval_ms")
        } catch (_: Exception) {
            120000L
        }
    }

    fun getAdsMaxPerSession(): Int {
        val rc = remoteConfig ?: return 3
        return try {
            rc.getLong("ads_max_per_session").toInt()
        } catch (_: Exception) {
            3
        }
    }
}
