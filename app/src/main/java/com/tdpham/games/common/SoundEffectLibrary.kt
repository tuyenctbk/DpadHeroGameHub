package com.tdpham.games.common

import android.media.ToneGenerator
import java.util.concurrent.Executors

/**
 * Dedicated Sound Effect Library mapped to high-action game events for
 * Rank 2-5 arcade games (4096/2048, Retro Driver, Tetris, Brick Break, StarFighter).
 */
object SoundEffectLibrary {

    private val audioExecutor = Executors.newSingleThreadExecutor()

    enum class SoundEffectEvent {
        MOVE,
        JUMP,
        FALL,
        SCORE_INCREMENT,
        COMBO,
        TILE_MERGE,
        LINE_CLEAR,
        HAZARD_IMPACT,
        ENGINE_BOOST,
        TIRE_SCREECH,
        PADDLE_HIT,
        BRICK_DESTROY,
        LASER_FIRE,
        EXPLOSION,
        POWERUP_PICKUP,
        LEVEL_UP,
        GAME_OVER,
        VICTORY_FANFARE
    }

    /**
     * Universal dispatcher for game events with optional combo multiplier level.
     */
    fun play(event: SoundEffectEvent, comboLevel: Int = 1) {
        when (event) {
            SoundEffectEvent.MOVE -> SoundManager.playDpadMove()
            SoundEffectEvent.JUMP -> playJump()
            SoundEffectEvent.FALL -> SoundManager.playTone(ToneGenerator.TONE_DTMF_0, 40)
            SoundEffectEvent.SCORE_INCREMENT -> SoundManager.playScore()
            SoundEffectEvent.COMBO -> playComboArpeggio(comboLevel)
            SoundEffectEvent.TILE_MERGE -> playTileMerge(comboLevel)
            SoundEffectEvent.LINE_CLEAR -> playLineClear(comboLevel)
            SoundEffectEvent.HAZARD_IMPACT -> playHazardImpact()
            SoundEffectEvent.ENGINE_BOOST -> playEngineBoost()
            SoundEffectEvent.TIRE_SCREECH -> playTireScreech()
            SoundEffectEvent.PADDLE_HIT -> playPaddleHit()
            SoundEffectEvent.BRICK_DESTROY -> playBrickDestroy()
            SoundEffectEvent.LASER_FIRE -> playLaserFire()
            SoundEffectEvent.EXPLOSION -> SoundManager.playExplosion()
            SoundEffectEvent.POWERUP_PICKUP -> SoundManager.playPowerUp()
            SoundEffectEvent.LEVEL_UP -> playLevelUp()
            SoundEffectEvent.GAME_OVER -> SoundManager.playGameOver()
            SoundEffectEvent.VICTORY_FANFARE -> playVictoryFanfare()
        }
    }

    // --- RANK 2: 4096 / 2048 TILE MERGE PITCH SCALING ---
    fun playTileMerge(tileValue: Int) {
        audioExecutor.execute {
            val tone = when (tileValue) {
                4 -> ToneGenerator.TONE_DTMF_1
                8 -> ToneGenerator.TONE_DTMF_2
                16 -> ToneGenerator.TONE_DTMF_3
                32 -> ToneGenerator.TONE_DTMF_4
                64 -> ToneGenerator.TONE_DTMF_5
                128 -> ToneGenerator.TONE_DTMF_6
                256 -> ToneGenerator.TONE_DTMF_7
                512 -> ToneGenerator.TONE_DTMF_8
                1024 -> ToneGenerator.TONE_DTMF_9
                2048 -> ToneGenerator.TONE_DTMF_A
                4096 -> ToneGenerator.TONE_DTMF_B
                else -> ToneGenerator.TONE_DTMF_C
            }
            SoundManager.playTone(tone, 65)
            if (tileValue >= 256) {
                try {
                    Thread.sleep(45)
                    SoundManager.playTone(ToneGenerator.TONE_DTMF_D, 90)
                } catch (_: InterruptedException) {}
            }
        }
    }

    // --- RANK 4: RETRO DRIVER / ROAD RACER ---
    fun playEngineBoost() {
        audioExecutor.execute {
            SoundManager.playTone(ToneGenerator.TONE_DTMF_3, 40)
            try {
                Thread.sleep(30)
                SoundManager.playTone(ToneGenerator.TONE_DTMF_6, 60)
                Thread.sleep(40)
                SoundManager.playTone(ToneGenerator.TONE_DTMF_9, 80)
            } catch (_: InterruptedException) {}
        }
    }

    fun playTireScreech() {
        SoundManager.playTone(ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK, 120)
    }

    // --- RANK 5: TETRIS / BRICK BREAK / STARFIGHTER ---
    fun playLineClear(lineCount: Int) {
        audioExecutor.execute {
            val steps = lineCount.coerceIn(1, 4)
            val tones = intArrayOf(
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_4,
                ToneGenerator.TONE_DTMF_7,
                ToneGenerator.TONE_DTMF_A
            )
            for (i in 0 until steps) {
                SoundManager.playTone(tones[i], 50)
                try {
                    Thread.sleep(45)
                } catch (_: InterruptedException) {}
            }
        }
    }

    fun playJump() {
        audioExecutor.execute {
            SoundManager.playTone(ToneGenerator.TONE_DTMF_1, 35)
            try {
                Thread.sleep(25)
                SoundManager.playTone(ToneGenerator.TONE_DTMF_5, 55)
            } catch (_: InterruptedException) {}
        }
    }

    fun playPaddleHit() {
        SoundManager.playTone(ToneGenerator.TONE_PROP_ACK, 40)
    }

    fun playBrickDestroy() {
        SoundManager.playTone(ToneGenerator.TONE_DTMF_8, 45)
    }

    fun playLaserFire() {
        audioExecutor.execute {
            SoundManager.playTone(ToneGenerator.TONE_DTMF_D, 30)
            try {
                Thread.sleep(20)
                SoundManager.playTone(ToneGenerator.TONE_DTMF_9, 40)
            } catch (_: InterruptedException) {}
        }
    }

    fun playHazardImpact() {
        SoundManager.playTone(ToneGenerator.TONE_SUP_ERROR, 160)
    }

    fun playComboArpeggio(combo: Int) {
        audioExecutor.execute {
            val tones = intArrayOf(
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_5,
                ToneGenerator.TONE_DTMF_7,
                ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_DTMF_B,
                ToneGenerator.TONE_DTMF_D
            )
            val count = (combo + 1).coerceIn(2, tones.size)
            for (i in 0 until count) {
                SoundManager.playTone(tones[i], 45)
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {}
            }
        }
    }

    fun playLevelUp() {
        audioExecutor.execute {
            val melody = intArrayOf(
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_5,
                ToneGenerator.TONE_DTMF_7,
                ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_DTMF_D
            )
            for (tone in melody) {
                SoundManager.playTone(tone, 65)
                try {
                    Thread.sleep(55)
                } catch (_: InterruptedException) {}
            }
        }
    }

    fun playVictoryFanfare() {
        audioExecutor.execute {
            val victoryNotes = intArrayOf(
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_5,
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_5,
                ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_DTMF_D
            )
            val durations = intArrayOf(80, 80, 80, 80, 140, 260)
            for (i in victoryNotes.indices) {
                SoundManager.playTone(victoryNotes[i], durations[i])
                try {
                    Thread.sleep(durations[i] + 30L)
                } catch (_: InterruptedException) {}
            }
        }
    }
}
