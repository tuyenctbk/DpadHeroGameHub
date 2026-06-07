package com.tdpham.games.common

import android.graphics.Canvas
import java.util.*

class ScreenShake {
    private var shakeDuration = 0
    private var shakeIntensity = 0f
    private val random = Random()
    
    fun trigger(duration: Int, intensity: Float) {
        shakeDuration = duration
        shakeIntensity = intensity
    }
    
    fun apply(canvas: Canvas): Boolean {
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
