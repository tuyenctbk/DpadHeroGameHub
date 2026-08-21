package com.tdpham.games.common

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {

    private const val PREFS_NAME = "game_settings"

    const val KEY_SOUND_ENABLED = "sound_enabled"
    const val KEY_SOUND_VOLUME = "sound_volume" // 0..100
    const val KEY_MUSIC_ENABLED = "music_enabled"
    const val KEY_MUSIC_VOLUME = "music_volume" // 0..100
    const val KEY_CONTROLLER_SENSITIVITY = "controller_sensitivity" // Float 0.5f - 2.0f
    const val KEY_CONTROLLER_DEADZONE = "controller_deadzone" // Int percent 5..60 (default 25%)
    const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    const val KEY_HAPTIC_INTENSITY = "haptic_intensity" // 0..100
    const val KEY_CRT_SCANLINE = "crt_scanline_enabled"
    const val KEY_SCREEN_SHAKE = "screen_shake_enabled"

    enum class Sensitivity(val label: String, val multiplier: Float) {
        LOW("0.6x (Smooth)", 0.6f),
        NORMAL("1.0x (Standard)", 1.0f),
        HIGH("1.4x (Fast)", 1.4f),
        ULTRA("1.8x (Arcade Pro)", 1.8f);

        companion object {
            fun fromMultiplier(value: Float): Sensitivity {
                return entries.minByOrNull { kotlin.math.abs(it.multiplier - value) } ?: NORMAL
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- SOUND PROFILES (Arcade, Retro, Modern) ---
    enum class SoundProfilePreset(val label: String, val description: String) {
        ARCADE("Arcade 8-Bit", "Punchy 8-bit pulse waves"),
        RETRO("Retro Synth", "Warm analog synth tones"),
        MODERN("Modern Digital", "Crisp polyphonic chimes");

        companion object {
            fun fromName(name: String?): SoundProfilePreset {
                return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ARCADE
            }
        }
    }

    const val KEY_SOUND_PROFILE_PRESET = "sound_profile_preset"

    fun getSoundProfilePreset(context: Context): SoundProfilePreset {
        val name = getPrefs(context).getString(KEY_SOUND_PROFILE_PRESET, SoundProfilePreset.ARCADE.name)
        return SoundProfilePreset.fromName(name)
    }

    fun setSoundProfilePreset(context: Context, preset: SoundProfilePreset) {
        getPrefs(context).edit().putString(KEY_SOUND_PROFILE_PRESET, preset.name).apply()
        SoundManager.setSoundProfilePreset(preset)
    }

    fun cycleSoundProfilePreset(context: Context): SoundProfilePreset {
        val current = getSoundProfilePreset(context)
        val next = when (current) {
            SoundProfilePreset.ARCADE -> SoundProfilePreset.RETRO
            SoundProfilePreset.RETRO -> SoundProfilePreset.MODERN
            SoundProfilePreset.MODERN -> SoundProfilePreset.ARCADE
        }
        setSoundProfilePreset(context, next)
        return next
    }

    // --- SOUND EFFECTS ---
    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        SoundManager.setSoundEnabled(enabled)
    }

    fun getSoundVolume(context: Context): Int {
        return getPrefs(context).getInt(KEY_SOUND_VOLUME, 100).coerceIn(0, 100)
    }

    fun setSoundVolume(context: Context, volume: Int) {
        val safeVol = volume.coerceIn(0, 100)
        getPrefs(context).edit().putInt(KEY_SOUND_VOLUME, safeVol).apply()
        SoundManager.setSoundEffectsVolume(safeVol)
    }

    // --- RETRO BACKGROUND MUSIC ---
    fun isMusicEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MUSIC_ENABLED, true)
    }

    fun setMusicEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply()
        SoundManager.setMusicEnabled(enabled)
    }

    fun getMusicVolume(context: Context): Int {
        return getPrefs(context).getInt(KEY_MUSIC_VOLUME, 80).coerceIn(0, 100)
    }

    fun setMusicVolume(context: Context, volume: Int) {
        val safeVol = volume.coerceIn(0, 100)
        getPrefs(context).edit().putInt(KEY_MUSIC_VOLUME, safeVol).apply()
        SoundManager.setMusicVolume(safeVol)
    }

    // --- CONTROLLER SENSITIVITY & DEADZONE ---
    fun getControllerSensitivity(context: Context): Float {
        return getPrefs(context).getFloat(KEY_CONTROLLER_SENSITIVITY, 1.0f)
    }

    fun getSensitivityPreset(context: Context): Sensitivity {
        val mult = getControllerSensitivity(context)
        return Sensitivity.fromMultiplier(mult)
    }

    fun setControllerSensitivity(context: Context, sensitivity: Sensitivity) {
        getPrefs(context).edit().putFloat(KEY_CONTROLLER_SENSITIVITY, sensitivity.multiplier).apply()
    }

    fun cycleSensitivity(context: Context): Sensitivity {
        val current = getSensitivityPreset(context)
        val next = when (current) {
            Sensitivity.LOW -> Sensitivity.NORMAL
            Sensitivity.NORMAL -> Sensitivity.HIGH
            Sensitivity.HIGH -> Sensitivity.ULTRA
            Sensitivity.ULTRA -> Sensitivity.LOW
        }
        setControllerSensitivity(context, next)
        return next
    }

    fun getControllerDeadzonePercent(context: Context): Int {
        return getPrefs(context).getInt(KEY_CONTROLLER_DEADZONE, 25).coerceIn(5, 60)
    }

    fun getControllerDeadzone(context: Context): Float {
        return getControllerDeadzonePercent(context) / 100f
    }

    fun setControllerDeadzonePercent(context: Context, percent: Int) {
        val safe = percent.coerceIn(5, 60)
        getPrefs(context).edit().putInt(KEY_CONTROLLER_DEADZONE, safe).apply()
    }

    /**
     * Filters analog stick (AXIS_X, AXIS_Y or AXIS_HAT_X, AXIS_HAT_Y) coordinates using
     * the configured hardware deadzone threshold, returning (0f, 0f) if within deadzone.
     */
    fun applyDeadzone(context: Context, x: Float, y: Float): Pair<Float, Float> {
        val deadzone = getControllerDeadzone(context)
        val mag = kotlin.math.sqrt(x * x + y * y)
        if (mag < deadzone) {
            return Pair(0f, 0f)
        }
        // Rescale normalized vector beyond deadzone
        val scale = (mag - deadzone) / (1.0f - deadzone)
        val normX = (x / mag) * scale
        val normY = (y / mag) * scale
        return Pair(normX.coerceIn(-1f, 1f), normY.coerceIn(-1f, 1f))
    }

    // --- HAPTIC FEEDBACK & VIBRATION INTENSITY ---
    fun isHapticEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    fun getHapticIntensity(context: Context): Int {
        return getPrefs(context).getInt(KEY_HAPTIC_INTENSITY, 100).coerceIn(0, 100)
    }

    fun setHapticIntensity(context: Context, intensity: Int) {
        val safe = intensity.coerceIn(0, 100)
        getPrefs(context).edit().putInt(KEY_HAPTIC_INTENSITY, safe).apply()
    }

    // --- VISUAL EFFECTS ---
    fun isScanlineEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CRT_SCANLINE, true)
    }

    fun setScanlineEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CRT_SCANLINE, enabled).apply()
    }

    fun isScreenShakeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SCREEN_SHAKE, true)
    }

    fun setScreenShakeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SCREEN_SHAKE, enabled).apply()
    }
}
