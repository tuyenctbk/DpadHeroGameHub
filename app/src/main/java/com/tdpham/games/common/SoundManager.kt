package com.tdpham.games.common

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import java.util.concurrent.Executors

object SoundManager {
    private var toneGenerator: ToneGenerator? = null
    private var isSoundEnabled = true
    private var isMusicEnabled = true
    private var soundEffectsVolume = 100
    private var musicVolume = 80
    private var prefs: SharedPreferences? = null
    private const val PREFS_NAME = "game_settings"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_SOUND_VOLUME = "sound_volume"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_MUSIC_VOLUME = "music_volume"

    private val soundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isMusicPlaying = false
    private var musicThread: Thread? = null

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isSoundEnabled = prefs?.getBoolean(KEY_SOUND_ENABLED, true) ?: true
        isMusicEnabled = prefs?.getBoolean(KEY_MUSIC_ENABLED, true) ?: true
        soundEffectsVolume = prefs?.getInt(KEY_SOUND_VOLUME, 100) ?: 100
        musicVolume = prefs?.getInt(KEY_MUSIC_VOLUME, 80) ?: 80

        ensureToneGenerator()
    }

    // --- GAMEPAD & CONTROLLER DETECTION ---
    fun isGamepadConnected(context: Context? = null): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            val isDpad = (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            if (!device.isVirtual && (isGamepad || isJoystick || isDpad)) {
                return true
            }
        }
        return false
    }

    fun getConnectedGamepads(): List<InputDevice> {
        val list = mutableListOf<InputDevice>()
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            val isDpad = (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            if (!device.isVirtual && (isGamepad || isJoystick || isDpad)) {
                list.add(device)
            }
        }
        return list
    }

    fun getGamepadStatusSummary(context: Context): String {
        val gamepads = getConnectedGamepads()
        return if (gamepads.isEmpty()) {
            "No External Gamepad (Virtual D-Pad Active)"
        } else {
            val names = gamepads.joinToString(", ") { it.name }
            "🎮 Connected: $names"
        }
    }

    // --- SOUND EFFECTS (SFX) MANAGEMENT ---
    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, enabled)?.apply()
    }

    fun isSoundEnabled(): Boolean = isSoundEnabled

    fun setSoundEffectsVolume(volume: Int) {
        soundEffectsVolume = volume.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_SOUND_VOLUME, soundEffectsVolume)?.apply()
        recreateToneGenerator()
    }

    fun getSoundEffectsVolume(): Int = soundEffectsVolume

    // Alias for backward compatibility
    fun setVolume(volume: Int) = setSoundEffectsVolume(volume)
    fun getVolume(): Int = soundEffectsVolume

    // --- BACKGROUND MUSIC MANAGEMENT ---
    fun setMusicEnabled(enabled: Boolean) {
        isMusicEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_MUSIC_ENABLED, enabled)?.apply()
        if (!enabled) {
            stopAmbientMusic()
        }
    }

    fun isMusicEnabled(): Boolean = isMusicEnabled

    fun setMusicVolume(volume: Int) {
        musicVolume = volume.coerceIn(0, 100)
        prefs?.edit()?.putInt(KEY_MUSIC_VOLUME, musicVolume)?.apply()
    }

    fun getMusicVolume(): Int = musicVolume

    fun toggleSound(): Boolean {
        isSoundEnabled = !isSoundEnabled
        prefs?.edit()?.putBoolean(KEY_SOUND_ENABLED, isSoundEnabled)?.apply()
        if (isSoundEnabled) {
            playTone(ToneGenerator.TONE_PROP_BEEP)
        }
        return isSoundEnabled
    }

    fun toggleMusic(): Boolean {
        isMusicEnabled = !isMusicEnabled
        prefs?.edit()?.putBoolean(KEY_MUSIC_ENABLED, isMusicEnabled)?.apply()
        return isMusicEnabled
    }

    // --- AUDIO GENERATION ---
    private fun ensureToneGenerator(): ToneGenerator? {
        if (toneGenerator == null && soundEffectsVolume > 0) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, soundEffectsVolume)
            } catch (e: Throwable) {
                android.util.Log.e("SoundManager", "Failed to initialize ToneGenerator: ${e.message}", e)
            }
        }
        return toneGenerator
    }

    private fun recreateToneGenerator() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            if (soundEffectsVolume > 0) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, soundEffectsVolume)
            }
        } catch (e: Throwable) {
            android.util.Log.e("SoundManager", "Failed to recreate ToneGenerator: ${e.message}", e)
        }
    }

    fun playTone(toneType: Int, durationMs: Int = 100) {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            try {
                ensureToneGenerator()?.startTone(toneType, durationMs)
            } catch (_: Throwable) {}
        }
    }

    fun playScore() = playTone(ToneGenerator.TONE_DTMF_0, 80)
    fun playError() = playTone(ToneGenerator.TONE_SUP_ERROR, 150)
    fun playClick() = playTone(ToneGenerator.TONE_PROP_BEEP, 50)
    fun playSuccess() = playTone(ToneGenerator.TONE_PROP_PROMPT, 150)
    fun playFlag() = playTone(ToneGenerator.TONE_PROP_ACK, 80)
    fun playGameOver() = playTone(ToneGenerator.TONE_SUP_ERROR, 350)

    // --- SOUND PROFILES & PRESETS (Arcade, Retro, Modern) ---
    private var activePreset: SettingsManager.SoundProfilePreset = SettingsManager.SoundProfilePreset.ARCADE

    fun setSoundProfilePreset(preset: SettingsManager.SoundProfilePreset) {
        activePreset = preset
    }

    fun getSoundProfilePreset(): SettingsManager.SoundProfilePreset = activePreset

    enum class SoundProfile {
        RETRO_ARCADE,     // Snake, RetroDriver, Tanks, StarFighter, Froggy, Monkey
        CLASSIC_PUZZLE,   // Tetris, 2048, Minesweeper, Sudoku, Slide Puzzle, Lines98, Sokoban
        CASINO_TABLE,     // Blackjack, Solitaire, Checkers
        TRIVIA_QUIZ,      // Trivia, Mental Math, Word Quest, Hangman
        ACTION_FRENZY,    // Feeding Frenzy, Fruit Slice, Flappy Hero, Brick Break, Spinball, TRex
        STRATEGY_TACTICS  // Connect Four, Tic Tac Toe, Simon Says, Dungeon Escape
    }

    enum class GameSoundEvent {
        START,
        MOVE,
        SELECT,
        SCORE,
        COMBO,
        ERROR,
        WIN,
        GAME_OVER,
        POWER_UP,
        LIFELINE,
        TICK
    }

    fun playProfileSound(profile: SoundProfile, event: GameSoundEvent, combo: Int = 1) {
        if (!isSoundEnabled || soundEffectsVolume <= 0) return

        // Adapt sound characteristics according to activePreset (ARCADE, RETRO, MODERN)
        when (activePreset) {
            SettingsManager.SoundProfilePreset.ARCADE -> {
                when (event) {
                    GameSoundEvent.START -> playTone(ToneGenerator.TONE_DTMF_D, 90)
                    GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_BEEP, 30)
                    GameSoundEvent.SELECT -> playTone(ToneGenerator.TONE_DTMF_4, 50)
                    GameSoundEvent.SCORE -> playTone(ToneGenerator.TONE_DTMF_9, 60)
                    GameSoundEvent.COMBO -> playCombo(combo)
                    GameSoundEvent.ERROR -> playTone(ToneGenerator.TONE_SUP_ERROR, 120)
                    GameSoundEvent.WIN -> playTone(ToneGenerator.TONE_PROP_PROMPT, 140)
                    GameSoundEvent.GAME_OVER -> playTone(ToneGenerator.TONE_SUP_ERROR, 300)
                    GameSoundEvent.POWER_UP -> playTone(ToneGenerator.TONE_DTMF_A, 100)
                    GameSoundEvent.LIFELINE -> playTone(ToneGenerator.TONE_DTMF_B, 100)
                    GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_DTMF_0, 30)
                }
            }
            SettingsManager.SoundProfilePreset.RETRO -> {
                when (profile) {
                    SoundProfile.RETRO_ARCADE -> when (event) {
                        GameSoundEvent.START -> playTone(ToneGenerator.TONE_DTMF_D, 120)
                        GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_BEEP, 30)
                        GameSoundEvent.SELECT -> playTone(ToneGenerator.TONE_DTMF_4, 60)
                        GameSoundEvent.SCORE -> playSnakeEat()
                        GameSoundEvent.COMBO -> playCombo(combo)
                        GameSoundEvent.ERROR -> playError()
                        GameSoundEvent.WIN -> playSuccess()
                        GameSoundEvent.GAME_OVER -> playExplosion()
                        GameSoundEvent.POWER_UP -> playPowerUp()
                        GameSoundEvent.LIFELINE -> playPowerUp()
                        GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_DTMF_0, 40)
                    }
                    SoundProfile.CLASSIC_PUZZLE -> when (event) {
                        GameSoundEvent.START -> playTone(ToneGenerator.TONE_PROP_PROMPT, 100)
                        GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                        GameSoundEvent.SELECT -> playTone(ToneGenerator.TONE_DTMF_1, 50)
                        GameSoundEvent.SCORE -> playTone(ToneGenerator.TONE_DTMF_8, 70)
                        GameSoundEvent.COMBO -> playCombo(combo)
                        GameSoundEvent.ERROR -> playError()
                        GameSoundEvent.WIN -> playSuccess()
                        GameSoundEvent.GAME_OVER -> playTone(ToneGenerator.TONE_SUP_ERROR, 200)
                        GameSoundEvent.POWER_UP -> playPowerUp()
                        GameSoundEvent.LIFELINE -> playTone(ToneGenerator.TONE_DTMF_A, 80)
                        GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_DTMF_0, 30)
                    }
                    SoundProfile.CASINO_TABLE -> when (event) {
                        GameSoundEvent.START -> playCardDeal()
                        GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_BEEP, 35)
                        GameSoundEvent.SELECT -> playChipBet()
                        GameSoundEvent.SCORE -> playCardDeal()
                        GameSoundEvent.COMBO -> playBlackjackWin()
                        GameSoundEvent.ERROR -> playBust()
                        GameSoundEvent.WIN -> playBlackjackWin()
                        GameSoundEvent.GAME_OVER -> playBust()
                        GameSoundEvent.POWER_UP -> playChipBet()
                        GameSoundEvent.LIFELINE -> playCardDeal()
                        GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_PROP_BEEP, 25)
                    }
                    SoundProfile.TRIVIA_QUIZ -> when (event) {
                        GameSoundEvent.START -> playTone(ToneGenerator.TONE_PROP_PROMPT, 120)
                        GameSoundEvent.MOVE -> playDpadMove()
                        GameSoundEvent.SELECT -> playDpadSelect()
                        GameSoundEvent.SCORE -> playTriviaCorrect()
                        GameSoundEvent.COMBO -> playCombo(combo)
                        GameSoundEvent.ERROR -> playTriviaWrong()
                        GameSoundEvent.WIN -> playTriviaGauntletWin()
                        GameSoundEvent.GAME_OVER -> playTone(ToneGenerator.TONE_SUP_ERROR, 220)
                        GameSoundEvent.POWER_UP -> playTriviaLifeline()
                        GameSoundEvent.LIFELINE -> playTriviaLifeline()
                        GameSoundEvent.TICK -> playTriviaTick()
                    }
                    SoundProfile.ACTION_FRENZY -> when (event) {
                        GameSoundEvent.START -> playTone(ToneGenerator.TONE_DTMF_C, 100)
                        GameSoundEvent.MOVE -> playSwoosh()
                        GameSoundEvent.SELECT -> playClick()
                        GameSoundEvent.SCORE -> playSlice()
                        GameSoundEvent.COMBO -> playCombo(combo)
                        GameSoundEvent.ERROR -> playError()
                        GameSoundEvent.WIN -> playSuccess()
                        GameSoundEvent.GAME_OVER -> playExplosion()
                        GameSoundEvent.POWER_UP -> playPowerUp()
                        GameSoundEvent.LIFELINE -> playPowerUp()
                        GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_DTMF_0, 40)
                    }
                    SoundProfile.STRATEGY_TACTICS -> when (event) {
                        GameSoundEvent.START -> playTone(ToneGenerator.TONE_DTMF_1, 100)
                        GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                        GameSoundEvent.SELECT -> playConnectFourDrop()
                        GameSoundEvent.SCORE -> playTone(ToneGenerator.TONE_DTMF_6, 80)
                        GameSoundEvent.COMBO -> playCombo(combo)
                        GameSoundEvent.ERROR -> playError()
                        GameSoundEvent.WIN -> playConnectFourWin()
                        GameSoundEvent.GAME_OVER -> playTone(ToneGenerator.TONE_SUP_ERROR, 200)
                        GameSoundEvent.POWER_UP -> playPowerUp()
                        GameSoundEvent.LIFELINE -> playTone(ToneGenerator.TONE_DTMF_D, 80)
                        GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_DTMF_0, 30)
                    }
                }
            }
            SettingsManager.SoundProfilePreset.MODERN -> {
                when (event) {
                    GameSoundEvent.START -> playTone(ToneGenerator.TONE_PROP_PROMPT, 130)
                    GameSoundEvent.MOVE -> playTone(ToneGenerator.TONE_PROP_ACK, 35)
                    GameSoundEvent.SELECT -> playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    GameSoundEvent.SCORE -> playTone(ToneGenerator.TONE_DTMF_A, 80)
                    GameSoundEvent.COMBO -> playCombo(combo)
                    GameSoundEvent.ERROR -> playTone(ToneGenerator.TONE_SUP_ERROR, 160)
                    GameSoundEvent.WIN -> playTone(ToneGenerator.TONE_PROP_PROMPT, 220)
                    GameSoundEvent.GAME_OVER -> playTone(ToneGenerator.TONE_SUP_ERROR, 280)
                    GameSoundEvent.POWER_UP -> playTone(ToneGenerator.TONE_DTMF_D, 120)
                    GameSoundEvent.LIFELINE -> playTone(ToneGenerator.TONE_DTMF_C, 110)
                    GameSoundEvent.TICK -> playTone(ToneGenerator.TONE_PROP_ACK, 25)
                }
            }
        }
    }

    // --- NAVIGATION & INTERACTION SFX ---
    fun playDpadMove() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 35)
        }
    }

    fun playDpadSelect() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_4, 50)
                    Thread.sleep(40)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_8, 60)
                } catch (_: Throwable) {}
            }
        }
    }

    // --- SNAKE SFX ---
    fun playSnakeEat() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_5, 50)
                    Thread.sleep(35)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_9, 65)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playSnakeTurn() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 25)
        }
    }

    fun playSnakeDie() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_SUP_ERROR, 160)
                    Thread.sleep(120)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_1, 180)
                } catch (_: Throwable) {}
            }
        }
    }

    // --- TRIVIA SFX ---
    fun playTriviaTick() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            playTone(ToneGenerator.TONE_DTMF_0, 30)
        }
    }

    fun playTriviaCorrect() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_6, 60)
                    Thread.sleep(50)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_9, 70)
                    Thread.sleep(60)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_C, 100)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playTriviaWrong() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_SUP_ERROR, 120)
                    Thread.sleep(90)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_1, 140)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playTriviaLifeline() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_3, 50)
                    Thread.sleep(40)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_7, 60)
                    Thread.sleep(50)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_B, 80)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playTriviaGauntletWin() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                val victoryTones = intArrayOf(
                    ToneGenerator.TONE_DTMF_1,
                    ToneGenerator.TONE_DTMF_4,
                    ToneGenerator.TONE_DTMF_7,
                    ToneGenerator.TONE_DTMF_C,
                    ToneGenerator.TONE_PROP_PROMPT
                )
                for (t in victoryTones) {
                    try {
                        ensureToneGenerator()?.startTone(t, 90)
                        Thread.sleep(85)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // --- CASINO SFX ---
    fun playCardDeal() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_D, 40)
                    Thread.sleep(30)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playChipBet() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_8, 35)
                    Thread.sleep(25)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_A, 45)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playBlackjackWin() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_5, 70)
                    Thread.sleep(60)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_9, 80)
                    Thread.sleep(70)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_PROP_PROMPT, 130)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playBust() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_SUP_ERROR, 160)
                } catch (_: Throwable) {}
            }
        }
    }

    // --- CONNECT FOUR SFX ---
    fun playConnectFourDrop() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_7, 45)
                    Thread.sleep(35)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_3, 55)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playConnectFourWin() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                val winTones = intArrayOf(
                    ToneGenerator.TONE_DTMF_3,
                    ToneGenerator.TONE_DTMF_5,
                    ToneGenerator.TONE_DTMF_7,
                    ToneGenerator.TONE_DTMF_9
                )
                for (t in winTones) {
                    try {
                        ensureToneGenerator()?.startTone(t, 80)
                        Thread.sleep(70)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // --- GENERAL RETRO SFX ---
    fun playLaser() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_C, 35)
                    Thread.sleep(25)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_7, 45)
                } catch (_: Throwable) {}
            }
        }
    }

    fun playPowerUp() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                val tones = intArrayOf(
                    ToneGenerator.TONE_DTMF_1,
                    ToneGenerator.TONE_DTMF_4,
                    ToneGenerator.TONE_DTMF_7,
                    ToneGenerator.TONE_DTMF_A
                )
                for (t in tones) {
                    try {
                        ensureToneGenerator()?.startTone(t, 60)
                        Thread.sleep(50)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    fun playCombo(combo: Int) {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            val clamped = combo.coerceIn(1, 8)
            val tone = when (clamped) {
                1 -> ToneGenerator.TONE_DTMF_1
                2 -> ToneGenerator.TONE_DTMF_3
                3 -> ToneGenerator.TONE_DTMF_5
                4 -> ToneGenerator.TONE_DTMF_7
                5 -> ToneGenerator.TONE_DTMF_9
                6 -> ToneGenerator.TONE_DTMF_A
                7 -> ToneGenerator.TONE_DTMF_B
                else -> ToneGenerator.TONE_DTMF_C
            }
            playTone(tone, 60 + clamped * 10)
        }
    }

    fun playLevelUp() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                val melody = intArrayOf(
                    ToneGenerator.TONE_DTMF_4,
                    ToneGenerator.TONE_DTMF_6,
                    ToneGenerator.TONE_DTMF_8,
                    ToneGenerator.TONE_PROP_PROMPT
                )
                for (t in melody) {
                    try {
                        ensureToneGenerator()?.startTone(t, 90)
                        Thread.sleep(80)
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    fun playExplosion() {
        if (isSoundEnabled && soundEffectsVolume > 0) {
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
        if (isSoundEnabled && soundEffectsVolume > 0) {
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
        if (isSoundEnabled && soundEffectsVolume > 0) {
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
        if (isSoundEnabled && soundEffectsVolume > 0) {
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
        if (isSoundEnabled && soundEffectsVolume > 0) {
            soundExecutor.execute {
                try {
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_8, 70)
                    Thread.sleep(50)
                    ensureToneGenerator()?.startTone(ToneGenerator.TONE_DTMF_A, 90)
                } catch (_: Throwable) {}
            }
        }
    }

    fun startAmbientMusic() {
        if (!isMusicEnabled || musicVolume <= 0 || isMusicPlaying) return
        isMusicPlaying = true
        musicThread = Thread {
            val melody = intArrayOf(
                ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_7,
                ToneGenerator.TONE_DTMF_8, ToneGenerator.TONE_DTMF_5
            )
            var index = 0
            while (isMusicPlaying && isMusicEnabled) {
                try {
                    if (musicVolume > 0) {
                        playTone(melody[index % melody.size], 90)
                    }
                    index++
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Throwable) {}
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stopAmbientMusic() {
        isMusicPlaying = false
        musicThread?.interrupt()
        musicThread = null
    }

    fun release() {
        stopAmbientMusic()
        toneGenerator?.release()
        toneGenerator = null
    }
}
