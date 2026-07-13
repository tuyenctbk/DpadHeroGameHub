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
    private var weather = GameEnvironment.WeatherType.RAIN
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
                GameEnvironment.BackgroundType.GRID,
                GameEnvironment.BackgroundType.DOTS,
                GameEnvironment.BackgroundType.SOLID
            )
            val safeWeather = listOf(
                GameEnvironment.WeatherType.NONE,
                GameEnvironment.WeatherType.RAIN,
                GameEnvironment.WeatherType.SNOW,
                GameEnvironment.WeatherType.FOG
            )
            
            bgType = safeBgs.random()
            weather = safeWeather.random()
            isNight = Random().nextBoolean()
            handler.postDelayed(this, 15000) // Change theme every 15 seconds
        }
    }

    init {
        repeat(50) {
            particles.add(GameEnvironment.Particle(
                Random().nextFloat() * 2000, 
                Random().nextFloat() * 2000, 
                Random().nextFloat() * 5 + 5,
                Random().nextFloat() * 2 - 1
            ))
        }
        handler.post(ticker)
        handler.postDelayed(themeRotator, 10000)
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(themeRotator)
    }
}
