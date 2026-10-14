package com.tdpham.games.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.tdpham.games.common.GameEnvironment
import java.util.*

class BackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgType = GameEnvironment.BackgroundType.GRADIENT
    private var weather = GameEnvironment.WeatherType.NONE
    private var isNight = true
    
    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 33)
        }
    }

    private val themeRotator = object : Runnable {
        override fun run() {
            // Pick from a curated list of subtle backgrounds for the hub
            val safeBgs = listOf(
                GameEnvironment.BackgroundType.GRADIENT,
                GameEnvironment.BackgroundType.GRID
            )
            
            bgType = safeBgs.random()
            weather = GameEnvironment.WeatherType.NONE
            isNight = true
            handler.postDelayed(this, 30000) // Change theme smoothly
        }
    }

    init {
        repeat(30) {
            particles.add(GameEnvironment.Particle(
                Random().nextFloat() * 2000, 
                Random().nextFloat() * 2000, 
                Random().nextFloat() * 0.8f + 0.4f,
                Random().nextFloat() * 0.5f - 0.25f,
                size = Random().nextFloat() * 1.5f + 1.0f,
                color = 0x2000E5FF.toInt()
            ))
        }
        handler.post(ticker)
        handler.postDelayed(themeRotator, 15000)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        GameEnvironment.draw(
            canvas = canvas,
            bgType = bgType,
            weather = weather,
            isNight = isNight,
            paint = paint,
            particles = if (weather != GameEnvironment.WeatherType.NONE) particles else null
        )
        // Subtle floating ambient neon cyber particles
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0f && h > 0f) {
            paint.style = Paint.Style.FILL
            for (p in particles) {
                paint.color = p.color
                canvas.drawCircle(p.x % w, p.y % h, p.size, paint)
                p.y -= p.speed
                p.x += p.vx
                if (p.y < 0f) {
                    p.y = h
                    p.x = Random().nextFloat() * w
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(themeRotator)
    }
}
