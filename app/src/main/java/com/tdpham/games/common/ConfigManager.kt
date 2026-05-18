package com.tdpham.games.common

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object ConfigManager {
    private val remoteConfig = Firebase.remoteConfig

    fun init() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate()
    }

    fun getSnakeSpeed(): Long {
        val speed = remoteConfig.getLong("snake_speed")
        return if (speed == 0L) 150L else speed
    }
}
