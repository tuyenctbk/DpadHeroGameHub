package com.tdpham.games.common

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object RewardAnimationOverlay {

    fun show(activity: Activity, titleText: String = "REVIVED! +1 EXTRA LIFE", onFinished: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: run {
            onFinished()
            return
        }
        val context = activity

        // 1. Overlay Container
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent dark backdrop
            isClickable = true
            isFocusable = true
        }

        // 2. Center Card Container
        val centerCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C2536"))
                setStroke(6, Color.parseColor("#00E676")) // Neon emerald border
                cornerRadius = 36f
            }
            elevation = 24f
            scaleX = 0f
            scaleY = 0f
        }

        // 3. Heart Icon View
        val tvHeart = TextView(context).apply {
            text = "❤️"
            textSize = 54f
            gravity = Gravity.CENTER
        }

        // 4. Title Text
        val tvTitle = TextView(context).apply {
            text = titleText
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        // 5. Subtitle
        val tvSub = TextView(context).apply {
            text = "Keep current score & continue run!"
            textSize = 14f
            setTextColor(Color.parseColor("#B0BEC5"))
            gravity = Gravity.CENTER
        }

        centerCard.addView(tvHeart)
        centerCard.addView(tvTitle)
        centerCard.addView(tvSub)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        overlay.addView(centerCard, cardParams)

        // 6. Particle Burst Views
        val particleSymbols = arrayOf("❤️", "✨", "🌟", "💚", "⭐", "🎉")
        val particles = mutableListOf<TextView>()

        repeat(22) {
            val p = TextView(context).apply {
                text = particleSymbols.random()
                textSize = Random.nextFloat() * 16f + 20f
                alpha = 0f
            }
            val pParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            overlay.addView(p, pParams)
            particles.add(p)
        }

        rootView.addView(overlay)

        // Sound & Haptics
        SoundManager.playSuccess()
        HapticManager.vibrateSuccess(context)

        // Animate Center Card (Spring Overshoot)
        centerCard.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        // Animate Particles radiating outwards & floating up
        particles.forEachIndexed { i, p ->
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 280f + 140f
            val targetX = cos(angle.toDouble()).toFloat() * speed
            val targetY = sin(angle.toDouble()).toFloat() * speed - 120f // Float upward

            p.alpha = 1f
            p.animate()
                .translationX(targetX)
                .translationY(targetY)
                .alpha(0f)
                .setDuration(1100L + (i * 20))
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // Auto Dismiss Overlay after 1.4s
        Handler(Looper.getMainLooper()).postDelayed({
            centerCard.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    try {
                        rootView.removeView(overlay)
                    } catch (_: Exception) {}
                    onFinished()
                }
                .start()
        }, 1400L)
    }
}
