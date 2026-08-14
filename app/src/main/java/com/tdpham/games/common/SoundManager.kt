package com.tdpham.games.common

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.Executors

object SoundManager {
    private var toneGenerator: ToneGenerator? = null
    private var isSoundEnabled = true
    private var prefs: SharedPreferences? = null
    private const val PREFS_NAME = "game_settings"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private val soundExecutor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isSoundEnabled = prefs?.getBoolean(KEY_SOUND_ENABLED, true) ?: true

        ensureToneGenerator()
    }

    private fun ensureToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e: Throwable) {
                android.util.Log.e("SoundManager", "Failed to initialize ToneGenerator: ${e.message}", e)
            }
        }
        return toneGenerator
    }

    fun toggleSound(): Boolean {
        isSoundEnabled = !isSoundEnabled
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, isSoundEnabled)?.apply()
        if (isSoundEnabled) {
            playTone(ToneGenerator.TONE_PROP_BEEP)
        }
        return isSoundEnabled
    }

    fun isSoundEnabled(): Boolean = isSoundEnabled

    fun playTone(toneType: Int) {
        if (isSoundEnabled) {
            try {
                ensureToneGenerator()?.startTone(toneType, 100)
            } catch (_: Throwable) {
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
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_SUP_ERROR, 250)
                    Thread.sleep(150)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_0, 200)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playJump() {
        if (isSoundEnabled) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_4, 70)
                    Thread.sleep(50)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_8, 70)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playSwoosh() {
        if (isSoundEnabled) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_D, 50)
                    Thread.sleep(40)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_B, 50)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playSlice() {
        if (isSoundEnabled) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_2, 80)
                    Thread.sleep(40)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_6, 80)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playMonkeyEat() {
        if (isSoundEnabled) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_8, 70)
                    Thread.sleep(50)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_A, 90)
                } catch (_: Throwable) {}
            }
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
