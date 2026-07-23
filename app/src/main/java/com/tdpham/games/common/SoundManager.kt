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
        isSoundEnabled = true
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, true)?.apply()

        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e: Throwable) {
                android.util.Log.e("SoundManager", "Failed to initialize ToneGenerator: ${e.message}", e)
            }
        }
    }

    fun toggleSound(): Boolean {
        isSoundEnabled = true
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, true)?.apply()
        playTone(ToneGenerator.TONE_PROP_BEEP)
        return true
    }

    fun isSoundEnabled() = true

    fun playTone(toneType: Int) {
        if (isSoundEnabled) {
            try {
                toneGenerator?.startTone(toneType, 100)
            } catch (e: Throwable) {
                // Fallback or ignore
            }
        }
    }

    fun playScore() = playTone(ToneGenerator.TONE_DTMF_0)
    fun playError() = playTone(ToneGenerator.TONE_SUP_ERROR)
    fun playClick() = playTone(ToneGenerator.TONE_PROP_BEEP)
    fun playSuccess() = playTone(ToneGenerator.TONE_PROP_PROMPT)
    fun playFlag() = playTone(ToneGenerator.TONE_PROP_ACK)
    
    fun playExplosion() {
        if (isSoundEnabled) {
            Thread {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 250)
                    Thread.sleep(150)
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0, 200)
                } catch (e: Throwable) {
                    // Ignore
                }
            }.start()
        }
    }

    fun playJump() {
        if (isSoundEnabled) {
            Thread {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_4, 70)
                    Thread.sleep(50)
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_8, 70)
                } catch (_: Throwable) {}
            }.start()
        }
    }

    fun playSwoosh() {
        if (isSoundEnabled) {
            Thread {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 50)
                    Thread.sleep(40)
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_B, 50)
                } catch (_: Throwable) {}
            }.start()
        }
    }

    fun playSlice() {
        if (isSoundEnabled) {
            Thread {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_2, 80)
                    Thread.sleep(40)
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_6, 80)
                } catch (_: Throwable) {}
            }.start()
        }
    }

    fun playMonkeyEat() {
        if (isSoundEnabled) {
            Thread {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_8, 70)
                    Thread.sleep(50)
                    toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 90)
                } catch (_: Throwable) {}
            }.start()
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
