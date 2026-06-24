package com.tdpham.games.common

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator

object SoundManager {
    private var toneGenerator: ToneGenerator? = null
    private var isSoundEnabled = true
    private var prefs: SharedPreferences? = null
    private const val PREFS_NAME = "game_settings"
    private const val KEY_SOUND_ENABLED = "sound_enabled"

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isSoundEnabled = prefs?.getBoolean(KEY_SOUND_ENABLED, true) ?: true

        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e: Exception) {
                android.util.Log.e("SoundManager", "Failed to initialize ToneGenerator: ${e.message}", e)
            }
        }
    }

    fun toggleSound(): Boolean {
        isSoundEnabled = !isSoundEnabled
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, isSoundEnabled)?.apply()

        if (isSoundEnabled) {
            playTone(ToneGenerator.TONE_PROP_BEEP)
        }
        return isSoundEnabled
    }

    fun isSoundEnabled() = isSoundEnabled

    fun playTone(toneType: Int) {
        if (isSoundEnabled) {
            try {
                toneGenerator?.startTone(toneType, 100)
            } catch (e: Exception) {
                // Fallback or ignore
            }
        }
    }

    fun playScore() = playTone(ToneGenerator.TONE_DTMF_0)
    fun playError() = playTone(ToneGenerator.TONE_SUP_ERROR)
    fun playClick() = playTone(ToneGenerator.TONE_PROP_BEEP)
    fun playSuccess() = playTone(ToneGenerator.TONE_PROP_PROMPT)
    fun playFlag() = playTone(ToneGenerator.TONE_PROP_ACK)

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
