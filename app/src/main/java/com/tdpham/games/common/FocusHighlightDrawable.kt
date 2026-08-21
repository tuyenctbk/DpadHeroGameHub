package com.tdpham.games.common

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Custom dynamic Drawable that renders an animated multi-layered neon glow border
 * and ambient halo for focused interactive UI elements on Android TV.
 */
class FocusHighlightDrawable(
    private var glowColor: Int = Color.parseColor("#00E5FF"),
    private var cornerRadius: Float = 24f,
    private var strokeWidth: Float = 6f
) : Drawable() {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@FocusHighlightDrawable.strokeWidth
        color = glowColor
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@FocusHighlightDrawable.strokeWidth * 2.5f
        color = glowColor
        alpha = 90
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }

    private val boundsRect = RectF()
    private var pulseAlpha = 1.0f

    fun setGlowColor(color: Int) {
        glowColor = color
        strokePaint.color = color
        glowPaint.color = color
        invalidateSelf()
    }

    fun setPulseAlpha(alpha: Float) {
        pulseAlpha = alpha.coerceIn(0f, 1f)
        strokePaint.alpha = (255 * pulseAlpha).toInt()
        glowPaint.alpha = (100 * pulseAlpha).toInt()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val inset = strokeWidth / 2f + 4f
        boundsRect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
    }

    override fun draw(canvas: Canvas) {
        if (boundsRect.isEmpty) return

        // 1. Draw outer ambient blurred neon glow
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, glowPaint)

        // 2. Draw crisp foreground accent border
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        glowPaint.alpha = (alpha * 0.4f).toInt()
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /**
         * Attaches high-polish TV D-pad focus scaling, glowing elevation, and breathing pulse
         * using ViewPropertyAnimator to any interactive View.
         */
        fun attach(
            view: View,
            scaleFactor: Float = 1.18f,
            elevationZ: Float = 36f,
            onFocusChanged: ((Boolean) -> Unit)? = null
        ) {
            view.setOnFocusChangeListener { v, hasFocus ->
                val runningAnim = v.getTag(com.tdpham.games.R.id.btn_snake) as? ValueAnimator
                runningAnim?.cancel()

                if (hasFocus) {
                    SoundManager.playDpadMove()
                    HapticManager.vibrateClick(v.context)

                    v.animate()
                        .scaleX(scaleFactor)
                        .scaleY(scaleFactor)
                        .translationZ(elevationZ)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .setDuration(280)
                        .withEndAction {
                            if (v.hasFocus()) {
                                val pulseAnim = ValueAnimator.ofFloat(scaleFactor, scaleFactor + 0.04f, scaleFactor).apply {
                                    duration = 1600
                                    repeatCount = ValueAnimator.INFINITE
                                    repeatMode = ValueAnimator.RESTART
                                    addUpdateListener { animator ->
                                        val s = animator.animatedValue as Float
                                        v.scaleX = s
                                        v.scaleY = s
                                    }
                                    start()
                                }
                                v.setTag(com.tdpham.games.R.id.btn_snake, pulseAnim)
                            }
                        }
                        .start()
                } else {
                    v.setTag(com.tdpham.games.R.id.btn_snake, null)
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setInterpolator(DecelerateInterpolator())
                        .setDuration(200)
                        .start()
                }
                onFocusChanged?.invoke(hasFocus)
            }

            view.setOnHoverListener { v, event ->
                if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                    v.requestFocus()
                }
                false
            }
        }
    }
}
