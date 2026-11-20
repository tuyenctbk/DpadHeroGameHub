package com.tdpham.games.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.tdpham.games.common.GameEnvironment
import java.util.Random

class BackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private var bgType = GameEnvironment.BackgroundType.GRADIENT
    private var weather = GameEnvironment.WeatherType.NONE
    private var isNight = true
    private var isLoopActive = false

    private val particles = mutableListOf<GameEnvironment.Particle>()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!isLoopActive) return
            invalidate()
            handler.postDelayed(this, 33)
        }
    }

    private val themeRotator = object : Runnable {
        override fun run() {
            if (!isLoopActive) return
            val safeBgs = listOf(
                GameEnvironment.BackgroundType.GRADIENT,
                GameEnvironment.BackgroundType.GRID
            )
            bgType = safeBgs[random.nextInt(safeBgs.size)]
            weather = GameEnvironment.WeatherType.NONE
            isNight = true
            handler.postDelayed(this, 30000)
        }
    }

    init {
        val palette = intArrayOf(0x2800E5FF, 0x24EC4899, 0x22A855F7, 0x1CFACC15)
        repeat(36) { idx ->
            particles.add(
                GameEnvironment.Particle(
                    x = random.nextFloat() * 2000f,
                    y = random.nextFloat() * 2000f,
                    speed = random.nextFloat() * 0.75f + 0.35f,
                    vx = random.nextFloat() * 0.45f - 0.225f,
                    size = random.nextFloat() * 2.2f + 1.2f,
                    color = palette[idx % palette.size]
                )
            )
        }
    }

    private fun startLoop() {
        if (isLoopActive) return
        isLoopActive = true
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(themeRotator)
        handler.post(ticker)
        handler.postDelayed(themeRotator, 15000)
    }

    private fun stopLoop() {
        isLoopActive = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(themeRotator)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) startLoop()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            startLoop()
        } else {
            stopLoop()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && visibility == VISIBLE && isAttachedToWindow) {
            startLoop()
        } else {
            stopLoop()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        GameEnvironment.draw(
            canvas = canvas,
            bgType = bgType,
            weather = weather,
            isNight = isNight,
            paint = paint,
            particles = if (weather != GameEnvironment.WeatherType.NONE) particles else null
        )

        // Subtle floating ambient neon synthwave bokeh particles (zero per-frame allocations)
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val drawX = ((p.x % w) + w) % w
            val drawY = ((p.y % h) + h) % h
            paint.color = p.color
            canvas.drawCircle(drawX, drawY, p.size * 2.1f, paint)
            canvas.drawCircle(drawX, drawY, p.size, paint)
            p.y -= p.speed
            p.x += p.vx
            if (p.y < 0f) {
                p.y = h
                p.x = random.nextFloat() * w
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }
}
