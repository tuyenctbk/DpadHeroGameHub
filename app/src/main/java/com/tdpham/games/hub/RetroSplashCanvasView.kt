package com.tdpham.games.hub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 1-Second Canvas-based Retro Splash Animation View.
 * Features an 80s arcade neon perspective grid, floating particle stars,
 * pulsing arcade diamond emblem, glowing typography, and a CRT scanline raster effect.
 */
class RetroSplashCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSplashFinished: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.25f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val scanlinePaint = Paint().apply {
        color = Color.parseColor("#15FFFFFF")
        strokeWidth = 1.5f
    }

    private var progress: Float = 0f
    private var animator: ValueAnimator? = null

    private data class Particle(
        val angle: Float,
        val speed: Float,
        val size: Float,
        val color: Int
    )

    private val particles = List(36) {
        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
        val speed = 100f + Random.nextFloat() * 350f
        val size = 3f + Random.nextFloat() * 4f
        val color = if (it % 2 == 0) Color.parseColor("#00E5FF") else Color.parseColor("#EC4899")
        Particle(angle, speed, size, color)
    }

    init {
        isFocusable = false
    }

    fun startAnimation(durationMs: Long = 1000L) {
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    progress = 1f
                    invalidate()
                    onSplashFinished?.invoke()
                }
            })
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            startAnimation(1000L)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f

        // 1. Deep Space Gradient Background
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#050811"),
                Color.parseColor("#0A0E1A"),
                Color.parseColor("#150A21")
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Perspective Neon Grid at bottom half
        val horizonY = cy + 40f
        gridPaint.alpha = (40 + (120 * progress)).toInt().coerceIn(0, 255)
        val gridLines = 10
        for (i in 0..gridLines) {
            val ratio = i / gridLines.toFloat()
            val y = horizonY + (h - horizonY) * (ratio * ratio)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        val vLines = 14
        for (i in 0..vLines) {
            val startX = (w / vLines) * i
            val bottomX = cx + (startX - cx) * 2.8f
            canvas.drawLine(startX, horizonY, bottomX, h, gridPaint)
        }

        // 3. Radial Particle Burst from Center
        for (p in particles) {
            val dist = p.speed * progress
            val px = cx + cos(p.angle) * dist
            val py = cy + sin(p.angle) * dist * 0.7f
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            particlePaint.color = p.color
            particlePaint.alpha = alpha
            canvas.drawCircle(px, py, p.size, particlePaint)
        }

        // 4. Center Glowing Emblem (Retro Diamond / Gamepad Shield)
        val emblemScale = (0.3f + (0.7f * (1f - (1f - progress) * (1f - progress)))).coerceIn(0f, 1.15f)
        val emblemRadius = 70f * emblemScale
        val emblemCenterY = cy - 50f

        // Outer Glow Rings
        glowPaint.strokeWidth = 3f
        glowPaint.color = Color.parseColor("#00E5FF")
        glowPaint.alpha = (180 * progress).toInt().coerceIn(0, 255)
        glowPaint.setShadowLayer(16f, 0f, 0f, Color.parseColor("#00E5FF"))
        canvas.drawCircle(cx, emblemCenterY, emblemRadius * 1.25f, glowPaint)

        glowPaint.color = Color.parseColor("#EC4899")
        glowPaint.alpha = (220 * progress).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, emblemCenterY, emblemRadius * 1.05f, glowPaint)
        glowPaint.clearShadowLayer()

        // Diamond / Star core
        val diamondPath = Path()
        diamondPath.moveTo(cx, emblemCenterY - emblemRadius)
        diamondPath.lineTo(cx + emblemRadius * 0.85f, emblemCenterY)
        diamondPath.lineTo(cx, emblemCenterY + emblemRadius)
        diamondPath.lineTo(cx - emblemRadius * 0.85f, emblemCenterY)
        diamondPath.close()

        val diamondFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - emblemRadius, emblemCenterY - emblemRadius,
                cx + emblemRadius, emblemCenterY + emblemRadius,
                intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899")),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
            alpha = (230 * progress).toInt().coerceIn(0, 255)
        }
        canvas.drawPath(diamondPath, diamondFillPaint)

        // Center Arcade Crosshair / Pixel Icon
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = (255 * progress).toInt().coerceIn(0, 255)
        }
        val crossSize = emblemRadius * 0.35f
        canvas.drawLine(cx - crossSize, emblemCenterY, cx + crossSize, emblemCenterY, innerPaint)
        canvas.drawLine(cx, emblemCenterY - crossSize, cx, emblemCenterY + crossSize, innerPaint)

        // 5. Glowing Title Typography ("RETRO ARCADE")
        val textAlpha = (255 * (progress * 1.4f).coerceIn(0f, 1f)).toInt()
        textPaint.textSize = 38f
        textPaint.alpha = textAlpha
        textPaint.setShadowLayer(14f * progress, 0f, 0f, Color.parseColor("#00E5FF"))

        val titleY = cy + 65f
        canvas.drawText("RETRO ARCADE", cx, titleY, textPaint)
        textPaint.clearShadowLayer()

        // Subtitle
        subTextPaint.textSize = 15f
        subTextPaint.alpha = (textAlpha * 0.85f).toInt()
        canvas.drawText("INITIALIZING SYSTEM", cx, titleY + 34f, subTextPaint)

        // 6. Retro Loading Progress Bar at bottom
        val barWidth = 260f
        val barHeight = 8f
        val barY = titleY + 68f
        val barLeft = cx - barWidth / 2f
        val barRight = cx + barWidth / 2f

        val bgRect = RectF(barLeft, barY, barRight, barY + barHeight)
        canvas.drawRoundRect(bgRect, 4f, 4f, barBgPaint)

        val fillWidth = barWidth * progress
        val fillRect = RectF(barLeft, barY, barLeft + fillWidth, barY + barHeight)
        barFillPaint.shader = LinearGradient(
            barLeft, barY, barRight, barY,
            intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#EC4899")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(fillRect, 4f, 4f, barFillPaint)

        // 7. Subtle CRT Scanline overlay
        var scanY = 0f
        while (scanY < h) {
            canvas.drawLine(0f, scanY, w, scanY, scanlinePaint)
            scanY += 4f
        }
    }
}
