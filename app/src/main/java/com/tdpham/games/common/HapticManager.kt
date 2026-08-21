package com.tdpham.games.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import java.util.concurrent.Executors

object HapticManager {

    private val hapticExecutor = Executors.newSingleThreadExecutor()

    fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isHapticAllowed(context: Context): Boolean {
        return SettingsManager.isHapticEnabled(context) && SettingsManager.getHapticIntensity(context) > 0
    }

    private fun scaleAmplitude(context: Context, baseAmplitude: Int): Int {
        val intensity = SettingsManager.getHapticIntensity(context) // 0..100
        val scaled = (baseAmplitude * (intensity / 100f)).toInt()
        return scaled.coerceIn(1, 255)
    }

    /**
     * Subtle 15ms tick for button navigations, focus transitions, card selections.
     */
    fun vibrateClick(context: Context) {
        if (!isHapticAllowed(context)) return
        hapticExecutor.execute {
            try {
                val vibrator = getVibrator(context) ?: return@execute
                if (!vibrator.hasVibrator()) return@execute

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator.hasAmplitudeControl()) {
                    val amp = scaleAmplitude(context, 100)
                    vibrator.vibrate(VibrationEffect.createOneShot(15, amp))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = scaleAmplitude(context, 80)
                    vibrator.vibrate(VibrationEffect.createOneShot(15, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Crisp 35ms pulse for scoring, eating dots, clearing lines, or flipping cards.
     */
    fun vibrateScore(context: Context) {
        if (!isHapticAllowed(context)) return
        hapticExecutor.execute {
            try {
                val vibrator = getVibrator(context) ?: return@execute
                if (!vibrator.hasVibrator()) return@execute

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = scaleAmplitude(context, 150)
                    vibrator.vibrate(VibrationEffect.createOneShot(35, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Distinct dual-pulse waveform for achievements, milestones, revives, level completions.
     */
    fun vibrateSuccess(context: Context) {
        if (!isHapticAllowed(context)) return
        hapticExecutor.execute {
            try {
                val vibrator = getVibrator(context) ?: return@execute
                if (!vibrator.hasVibrator()) return@execute

                val timings = longArrayOf(0, 45, 60, 80)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val a1 = scaleAmplitude(context, 160)
                    val a2 = scaleAmplitude(context, 240)
                    val amplitudes = intArrayOf(0, a1, 0, a2)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * 70ms thud for player damage, hazard collision, wrong move, or life loss.
     */
    fun vibrateDamage(context: Context) {
        if (!isHapticAllowed(context)) return
        hapticExecutor.execute {
            try {
                val vibrator = getVibrator(context) ?: return@execute
                if (!vibrator.hasVibrator()) return@execute

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = scaleAmplitude(context, 220)
                    vibrator.vibrate(VibrationEffect.createOneShot(70, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(70)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Heavy rumble waveform for game over, bomb detonation, or boss explosions.
     */
    fun vibrateExplosion(context: Context) {
        if (!isHapticAllowed(context)) return
        hapticExecutor.execute {
            try {
                val vibrator = getVibrator(context) ?: return@execute
                if (!vibrator.hasVibrator()) return@execute

                val timings = longArrayOf(0, 80, 50, 140, 60, 180)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val a1 = scaleAmplitude(context, 200)
                    val a2 = scaleAmplitude(context, 255)
                    val a3 = scaleAmplitude(context, 180)
                    val amplitudes = intArrayOf(0, a1, 0, a2, 0, a3)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Custom Test Vibration for Hardware Calibration & Verification.
     */
    fun testVibration(context: Context, testType: String) {
        when (testType) {
            "click" -> vibrateClick(context)
            "score" -> vibrateScore(context)
            "damage" -> vibrateDamage(context)
            "explosion" -> vibrateExplosion(context)
            "success" -> vibrateSuccess(context)
            else -> vibrateClick(context)
        }
    }
}
