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
                "ads_min_days" to 0L,
                "ads_min_opens" to 1L,
                "ads_min_session_seconds" to 30L,
                "ads_min_interval_ms" to 90000L,
                "ads_max_per_session" to 4L,
                "ads_game_over_frequency" to 4L,
                "ads_max_per_session_idle" to 5L,
                "ads_idle_menu_corner_sec" to 30L,
                "ads_idle_menu_full_sec" to 90L,
                "ads_idle_wait_corner_sec" to 30L,
                "ads_idle_wait_full_sec" to 90L,
                "ads_idle_play_corner_sec" to 60L,
                "ads_idle_play_full_sec" to 180L,
                "ads_idle_refresh_sec" to 180L,
                "ads_snooze_dismiss_threshold_sec" to 15L,
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


    fun isAdsEnabled(): Boolean {
        val rc = remoteConfig ?: return true
        return try {
            rc.getBoolean("is_ads_enabled")
        } catch (e: Exception) {
            Log.e("ConfigManager", "Failed to get is_ads_enabled: ${e.message}")
            true
        }
    }

    fun getAdsMinDays(): Int {
        val rc = remoteConfig ?: return 0
        return try {
            rc.getLong("ads_min_days").toInt()
        } catch (_: Exception) {
            0
        }
    }

    fun getAdsMinOpens(): Int {
        val rc = remoteConfig ?: return 1
        return try {
            rc.getLong("ads_min_opens").toInt()
        } catch (_: Exception) {
            1
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
        val rc = remoteConfig ?: return 90000L
        return try {
            rc.getLong("ads_min_interval_ms")
        } catch (_: Exception) {
            90000L
        }
    }

    fun getAdsMaxPerSession(): Int {
        val rc = remoteConfig ?: return 4
        return try {
            rc.getLong("ads_max_per_session").toInt()
        } catch (_: Exception) {
            4
        }
    }

    fun getAdsGameOverFrequency(): Int {
        val rc = remoteConfig ?: return 4
        return try {
            rc.getLong("ads_game_over_frequency").toInt()
        } catch (_: Exception) {
            4
        }
    }

    fun getAdsMaxPerSessionIdle(): Int {
        val rc = remoteConfig ?: return 5
        return try {
            rc.getLong("ads_max_per_session_idle").toInt()
        } catch (_: Exception) {
            5
        }
    }

    fun getAdsIdleMenuCornerSec(): Int {
        val rc = remoteConfig ?: return 30
        return try {
            rc.getLong("ads_idle_menu_corner_sec").toInt()
        } catch (_: Exception) {
            30
        }
    }

    fun getAdsIdleMenuFullSec(): Int {
        val rc = remoteConfig ?: return 90
        return try {
            rc.getLong("ads_idle_menu_full_sec").toInt()
        } catch (_: Exception) {
            90
        }
    }

    fun getAdsIdleWaitCornerSec(): Int {
        val rc = remoteConfig ?: return 30
        return try {
            rc.getLong("ads_idle_wait_corner_sec").toInt()
        } catch (_: Exception) {
            30
        }
    }

    fun getAdsIdleWaitFullSec(): Int {
        val rc = remoteConfig ?: return 90
        return try {
            rc.getLong("ads_idle_wait_full_sec").toInt()
        } catch (_: Exception) {
            90
        }
    }

    fun getAdsIdlePlayCornerSec(): Int {
        val rc = remoteConfig ?: return 60
        return try {
            rc.getLong("ads_idle_play_corner_sec").toInt()
        } catch (_: Exception) {
            60
        }
    }

    fun getAdsIdlePlayFullSec(): Int {
        val rc = remoteConfig ?: return 180
        return try {
            rc.getLong("ads_idle_play_full_sec").toInt()
        } catch (_: Exception) {
            180
        }
    }

    fun getAdsIdleRefreshSec(): Int {
        val rc = remoteConfig ?: return 180
        return try {
            rc.getLong("ads_idle_refresh_sec").toInt()
        } catch (_: Exception) {
            180
        }
    }

    fun getAdsSnoozeDismissThresholdSec(): Int {
        val rc = remoteConfig ?: return 15
        return try {
            rc.getLong("ads_snooze_dismiss_threshold_sec").toInt()
        } catch (_: Exception) {
            15
        }
    }
}
