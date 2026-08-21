package com.tdpham.games.common

import android.content.Context
import android.graphics.Canvas
import java.util.*

class ScreenShake {
    private var shakeDuration = 0
    private var shakeIntensity = 0f
    private val random = Random()

    companion object {
        var isEnabled: Boolean = true

        fun syncSettings(context: Context) {
            val prefs = context.getSharedPreferences("game_settings", Context.MODE_PRIVATE)
            isEnabled = prefs.getBoolean("screen_shake_enabled", true)
        }
    }
    
    fun trigger(duration: Int, intensity: Float) {
        if (!isEnabled) return
        shakeDuration = duration
        shakeIntensity = intensity
    }
    
    fun apply(canvas: Canvas): Boolean {
        if (!isEnabled) {
            shakeDuration = 0
            return false
        }
        if (shakeDuration > 0) {
            val dx = (random.nextFloat() - 0.5f) * 2 * shakeIntensity
            val dy = (random.nextFloat() - 0.5f) * 2 * shakeIntensity
            canvas.translate(dx, dy)
            shakeDuration--
            return true
        }
        return false
    }
}

