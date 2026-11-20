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
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zero-allocation Canvas-based Retro Synthwave Splash View.
 * Features scrolling neon perspective horizon grid, twinkling starfield,
 * glowing hexagonal D-Pad Arcade emblem, neon title bloom, and progress bar.
 */
class RetroSplashCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onSplashFinished: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EC4899")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val dpadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val dpadAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.14f
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.24f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
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
        color = Color.parseColor("#12FFFFFF")
        strokeWidth = 1.5f
    }

    // Pre-allocated geometry to guarantee zero allocations in onDraw()
    private val hexPath = Path()
    private val tempRect = RectF()
    private val barBgRect = RectF()
    private val barFillRect = RectF()

    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var badgeGradient: LinearGradient? = null
    private var barGradient: LinearGradient? = null

    private var progress: Float = 0f
    private var animator: ValueAnimator? = null

    private data class Particle(
        val angle: Float,
        val speedFactor: Float,
        val size: Float,
        val color: Int
    )

    private data class Star(
        val xNorm: Float,
        val yNorm: Float,
        val radius: Float,
        val phase: Float
    )

    private val particles = List(40) {
        val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
        val speedFactor = 0.16f + Random.nextFloat() * 0.34f
        val size = 2.5f + Random.nextFloat() * 4.5f
        val color = when (it % 3) {
            0 -> Color.parseColor("#00E5FF")
            1 -> Color.parseColor("#EC4899")
            else -> Color.parseColor("#FACC15")
        }
        Particle(angle, speedFactor, size, color)
    }

    private val stars = List(48) {
        Star(
            xNorm = Random.nextFloat(),
            yNorm = Random.nextFloat() * 0.56f,
            radius = 1.2f + Random.nextFloat() * 2.2f,
            phase = Random.nextFloat() * 6.28f
        )
    }

    init {
        isFocusable = false
    }

    fun startAnimation(durationMs: Long = 1400L) {
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    progress = 1f
                    invalidate()
                    onSplashFinished?.invoke()
                }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w.toFloat()
        cachedHeight = h.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = cachedWidth / 2f
        val cy = cachedHeight / 2f
        val unit = min(cachedWidth, cachedHeight)

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, cachedHeight,
            intArrayOf(
                Color.parseColor("#040712"),
                Color.parseColor("#0B1024"),
                Color.parseColor("#1A0B2E")
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )

        auraPaint.shader = RadialGradient(
            cx, cy - unit * 0.11f, unit * 0.42f,
            intArrayOf(
                Color.parseColor("#5500E5FF"),
                Color.parseColor("#28EC4899"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val emblemR = unit * 0.145f
        val emblemY = cy - unit * 0.11f
        badgeGradient = LinearGradient(
            cx - emblemR, emblemY - emblemR,
            cx + emblemR, emblemY + emblemR,
            intArrayOf(
                Color.parseColor("#131C3A"),
                Color.parseColor("#1E1245"),
                Color.parseColor("#28104E")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        badgeFillPaint.shader = badgeGradient

        badgeBorderPaint.shader = LinearGradient(
            cx - emblemR, emblemY - emblemR,
            cx + emblemR, emblemY + emblemR,
            intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#A855F7"),
                Color.parseColor("#EC4899")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        val barWidth = (unit * 0.46f).coerceIn(220f, 440f)
        val barLeft = cx - barWidth / 2f
        val barRight = cx + barWidth / 2f
        val barY = cy + unit * 0.255f
        barGradient = LinearGradient(
            barLeft, barY, barRight, barY,
            intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#EC4899")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        barFillPaint.shader = barGradient
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            startAnimation(1400L)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = cachedWidth
        val h = cachedHeight
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val unit = min(w, h)

        // 1. Deep Synthwave Gradient & Radial Aurora
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        auraPaint.alpha = (180 + (75 * progress)).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, auraPaint)

        // 2. Twinkling Starfield
        for (star in stars) {
            val twinkle = 0.45f + 0.55f * sin(star.phase + progress * 9f)
            starPaint.alpha = (twinkle * 210 * progress.coerceAtLeast(0.25f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(star.xNorm * w, star.yNorm * h, star.radius, starPaint)
        }

        // 3. Scrolling Perspective Neon Grid
        val horizonY = cy + unit * 0.08f
        gridPaint.alpha = (35 + (115 * progress)).toInt().coerceIn(0, 255)
        gridPaint.strokeWidth = (unit * 0.0025f).coerceAtLeast(1.5f)
        val hLines = 9
        val scrollFrac = (progress * 1.6f) % 1f
        for (i in 0 until hLines) {
            val ratio = (i + scrollFrac) / hLines.toFloat()
            val y = horizonY + (h - horizonY) * (ratio * ratio)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        val vLines = 14
        for (i in 0..vLines) {
            val startX = (w / vLines) * i
            val bottomX = cx + (startX - cx) * 2.9f
            canvas.drawLine(startX, horizonY, bottomX, h, gridPaint)
        }
        horizonPaint.alpha = (110 + (120 * progress)).toInt().coerceIn(0, 255)
        canvas.drawLine(0f, horizonY, w, horizonY, horizonPaint)

        // 4. Radial Particle Burst
        val burstProgress = (progress * 1.15f).coerceIn(0f, 1f)
        for (p in particles) {
            val dist = unit * p.speedFactor * burstProgress
            val px = cx + cos(p.angle) * dist
            val py = (cy - unit * 0.11f) + sin(p.angle) * dist * 0.72f
            particlePaint.color = p.color
            particlePaint.alpha = ((1f - burstProgress) * 240).toInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, p.size, particlePaint)
        }

        // 5. Center Hexagonal D-Pad Arcade Emblem
        val easeOutBack = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        val emblemScale = (0.45f + 0.55f * easeOutBack).coerceIn(0.45f, 1.05f)
        val emblemR = unit * 0.145f * emblemScale
        val emblemY = cy - unit * 0.11f

        // Outer Neon Pulse Rings
        glowRingPaint.strokeWidth = (unit * 0.0045f).coerceAtLeast(2.5f)
        glowRingPaint.color = Color.parseColor("#00E5FF")
        glowRingPaint.alpha = ((1f - progress * 0.35f) * 190).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, emblemY, emblemR * (1.22f + progress * 0.14f), glowRingPaint)

        glowRingPaint.color = Color.parseColor("#EC4899")
        glowRingPaint.alpha = (210 * progress).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, emblemY, emblemR * 1.08f, glowRingPaint)

        // Hexagon Badge
        hexPath.reset()
        for (i in 0 until 6) {
            val deg = 60.0 * i - 30.0
            val rad = Math.toRadians(deg)
            val vx = cx + (emblemR * cos(rad)).toFloat()
            val vy = emblemY + (emblemR * sin(rad)).toFloat()
            if (i == 0) hexPath.moveTo(vx, vy) else hexPath.lineTo(vx, vy)
        }
        hexPath.close()

        val badgeAlpha = (255 * (progress * 1.5f).coerceIn(0f, 1f)).toInt()
        badgeFillPaint.alpha = badgeAlpha
        canvas.drawPath(hexPath, badgeFillPaint)

        badgeBorderPaint.strokeWidth = (unit * 0.007f).coerceAtLeast(4f)
        badgeBorderPaint.alpha = badgeAlpha
        canvas.drawPath(hexPath, badgeBorderPaint)

        // Crisp Stylized D-Pad + A/B Arcade Buttons inside Hexagon
        dpadFillPaint.alpha = badgeAlpha
        val dpadCx = cx - emblemR * 0.18f
        val armLen = emblemR * 0.42f
        val armThick = emblemR * 0.155f
        val cornerR = armThick * 0.32f
        // Horizontal D-pad bar
        tempRect.set(dpadCx - armLen, emblemY - armThick, dpadCx + armLen, emblemY + armThick)
        canvas.drawRoundRect(tempRect, cornerR, cornerR, dpadFillPaint)
        // Vertical D-pad bar
        tempRect.set(dpadCx - armThick, emblemY - armLen, dpadCx + armThick, emblemY + armLen)
        canvas.drawRoundRect(tempRect, cornerR, cornerR, dpadFillPaint)
        // Center pivot dot
        dpadAccentPaint.color = Color.parseColor("#00E5FF")
        dpadAccentPaint.alpha = badgeAlpha
        canvas.drawCircle(dpadCx, emblemY, armThick * 0.45f, dpadAccentPaint)

        // Dual Neon Arcade Action Buttons (Right side of emblem)
        val btnR = emblemR * 0.135f
        dpadAccentPaint.color = Color.parseColor("#EC4899")
        dpadAccentPaint.alpha = badgeAlpha
        canvas.drawCircle(cx + emblemR * 0.42f, emblemY - emblemR * 0.16f, btnR, dpadAccentPaint)
        dpadAccentPaint.color = Color.parseColor("#FACC15")
        dpadAccentPaint.alpha = badgeAlpha
        canvas.drawCircle(cx + emblemR * 0.28f, emblemY + emblemR * 0.20f, btnR, dpadAccentPaint)

        // 6. Brand Typography ("D-PAD ARCADE")
        val textAlpha = (255 * (progress * 1.45f).coerceIn(0f, 1f)).toInt()
        val slideOffset = (1f - easeOutBack) * (unit * 0.03f)
        val titleY = cy + unit * 0.14f + slideOffset

        titlePaint.textSize = (unit * 0.068f).coerceIn(28f, 68f)
        titlePaint.alpha = textAlpha
        titlePaint.setShadowLayer(16f * progress, 0f, 0f, Color.parseColor("#00E5FF"))
        canvas.drawText(context.getString(com.tdpham.games.R.string.splash_title), cx, titleY, titlePaint)
        titlePaint.clearShadowLayer()

        subTextPaint.textSize = (unit * 0.024f).coerceIn(13f, 24f)
        subTextPaint.alpha = (textAlpha * 0.9f).toInt()
        canvas.drawText(context.getString(com.tdpham.games.R.string.splash_subtitle), cx, titleY + unit * 0.055f, subTextPaint)

        // 7. Sleek Neon Loading Progress Bar
        val barWidth = (unit * 0.46f).coerceIn(220f, 440f)
        val barHeight = (unit * 0.013f).coerceIn(7f, 12f)
        val barY = cy + unit * 0.255f
        val barLeft = cx - barWidth / 2f
        val barRight = cx + barWidth / 2f
        val barRadius = barHeight / 2f

        barBgRect.set(barLeft, barY, barRight, barY + barHeight)
        canvas.drawRoundRect(barBgRect, barRadius, barRadius, barBgPaint)

        val fillWidth = (barWidth * progress).coerceAtLeast(barHeight)
        barFillRect.set(barLeft, barY, (barLeft + fillWidth).coerceAtMost(barRight), barY + barHeight)
        canvas.drawRoundRect(barFillRect, barRadius, barRadius, barFillPaint)

        // 8. Subtle CRT Scanlines
        var scanY = 0f
        while (scanY < h) {
            canvas.drawLine(0f, scanY, w, scanY, scanlinePaint)
            scanY += 5f
        }
    }
}
