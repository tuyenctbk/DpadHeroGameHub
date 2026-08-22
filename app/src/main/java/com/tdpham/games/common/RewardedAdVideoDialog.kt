package com.tdpham.games.common

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.tdpham.games.R
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fullscreen Interactive Rewarded Video Ad Experience.
 * Ensures the player MUST watch the entire video ad duration to receive +1 Life reward.
 * Features animated gaming showcase, live countdown timer, smooth progress bar, and skip gating.
 */
object RewardedAdVideoDialog {

    fun show(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdDismissed()
            return
        }

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.setCancelable(false)

        val context = activity
        val mainHandler = Handler(Looper.getMainLooper())
        val adDurationSeconds = 5
        var remainingSeconds = adDurationSeconds
        var isRewardClaimed = false
        var isDismissed = false

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0A0E17"))
        }

        // 1. Dynamic Animated Video Showcase Canvas
        val animatedCanvasView = AdVideoCanvasView(context)
        root.addView(
            animatedCanvasView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 2. Top Header Bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 24, 32, 16)
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val tvAdBadge = TextView(context).apply {
            text = "AD • REWARD VIDEO"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D32F2F"))
                cornerRadius = 12f
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val tvTimer = TextView(context).apply {
            text = "Reward in ${remainingSeconds}s"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E676"))
            setPadding(24, 10, 24, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F2937"))
                setStroke(2, Color.parseColor("#00E676"))
                cornerRadius = 20f
            }
        }

        topBar.addView(tvAdBadge)
        topBar.addView(spacer)
        topBar.addView(tvTimer)

        val topBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
        }
        root.addView(topBar, topBarParams)

        // 3. Top Progress Bar
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = adDurationSeconds * 1000
            progress = 0
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 4f
            }
        }
        val progressParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            12
        ).apply {
            gravity = Gravity.TOP
            topMargin = 96
        }
        root.addView(progressBar, progressParams)

        // 4. Bottom Action Card
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 24, 32, 32)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }

        val tvAdTitle = TextView(context).apply {
            text = "🎮 DPAD HEROES ARCADE: BATTLE EDITION"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val tvAdSubtitle = TextView(context).apply {
            text = "Watch the full video to claim +1 Extra Life & continue your game!"
            textSize = 13f
            setTextColor(Color.parseColor("#90A4AE"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 16)
        }

        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnSkip = Button(context).apply {
            text = "✕ SKIP (NO REWARD)"
            textSize = 13f
            setTextColor(Color.parseColor("#FF5252"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#263238"))
                setStroke(2, Color.parseColor("#FF5252"))
                cornerRadius = 18f
            }
            setPadding(36, 16, 36, 16)
            isFocusable = true
        }

        val btnClaim = Button(context).apply {
            text = "⏳ WATCHING AD..."
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#78909C"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C2833"))
                cornerRadius = 18f
            }
            setPadding(48, 16, 48, 16)
            isEnabled = false
            isFocusable = true
        }

        buttonsLayout.addView(btnSkip)
        val btnSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(32, 1)
        }
        buttonsLayout.addView(btnSpacer)
        buttonsLayout.addView(btnClaim)

        bottomBar.addView(tvAdTitle)
        bottomBar.addView(tvAdSubtitle)
        bottomBar.addView(buttonsLayout)

        val bottomBarParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(bottomBar, bottomBarParams)

        dialog.setContentView(root)

        // ValueAnimator for the progress bar
        val progressAnimator = ValueAnimator.ofInt(0, adDurationSeconds * 1000).apply {
            duration = (adDurationSeconds * 1000).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                progressBar.progress = animator.animatedValue as Int
            }
        }

        fun completeAdAndGrantReward() {
            if (isDismissed) return
            isDismissed = true
            isRewardClaimed = true
            animatedCanvasView.stopAnimation()
            progressAnimator.cancel()
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
            onRewardEarned()
            onAdDismissed()
        }

        fun dismissWithoutReward() {
            if (isDismissed) return
            isDismissed = true
            animatedCanvasView.stopAnimation()
            progressAnimator.cancel()
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
            onAdDismissed()
        }

        // Countdown Timer Loop
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (isDismissed || activity.isFinishing || activity.isDestroyed) return
                remainingSeconds--
                if (remainingSeconds > 0) {
                    tvTimer.text = "Reward in ${remainingSeconds}s"
                    mainHandler.postDelayed(this, 1000L)
                } else {
                    tvTimer.text = "✓ REWARD READY!"
                    tvTimer.setTextColor(Color.parseColor("#00E676"))
                    btnClaim.isEnabled = true
                    btnClaim.text = "❤️ CLAIM +1 LIFE!"
                    btnClaim.setTextColor(Color.parseColor("#0A0E17"))
                    btnClaim.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#00E676"))
                        cornerRadius = 18f
                    }
                    btnClaim.requestFocus()

                    // Auto-claim reward after short 0.6s grace period so player doesn't have to click
                    mainHandler.postDelayed({
                        if (!isDismissed) {
                            completeAdAndGrantReward()
                        }
                    }, 600L)
                }
            }
        }

        btnSkip.setOnClickListener {
            dismissWithoutReward()
        }

        btnClaim.setOnClickListener {
            if (remainingSeconds <= 0) {
                completeAdAndGrantReward()
            }
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (remainingSeconds <= 0) {
                        completeAdAndGrantReward()
                    } else {
                        dismissWithoutReward()
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }

        try {
            dialog.show()
            progressAnimator.start()
            mainHandler.postDelayed(countdownRunnable, 1000L)
            btnSkip.requestFocus()
        } catch (_: Throwable) {
            onAdDismissed()
        }
    }

    /**
     * Canvas view rendering a vibrant animated game trailer video background.
     */
    private class AdVideoCanvasView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private var animTime = 0f
        private var isRunning = true
        private val handler = Handler(Looper.getMainLooper())

        private val renderRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    animTime += 0.04f
                    invalidate()
                    handler.postDelayed(this, 16L) // ~60fps
                }
            }
        }

        init {
            handler.post(renderRunnable)
        }

        fun stopAnimation() {
            isRunning = false
            handler.removeCallbacks(renderRunnable)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            // 1. Futuristic Cyber Ocean / Arcade Video Background
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#091326"), Color.parseColor("#0F2042"), Color.parseColor("#070B19")),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            // 2. Animated Energy Waves
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            for (layer in 0..2) {
                paint.color = when (layer) {
                    0 -> Color.parseColor("#3300E5FF")
                    1 -> Color.parseColor("#337C4DFF")
                    else -> Color.parseColor("#3300E676")
                }
                path.reset()
                val waveY = h * 0.45f + layer * 60f
                path.moveTo(0f, waveY)
                var x = 0f
                while (x <= w) {
                    val y = waveY + sin((x * 0.008f + animTime * 2f + layer * 1.2f).toDouble()).toFloat() * 32f
                    path.lineTo(x, y)
                    x += 24f
                }
                canvas.drawPath(path, paint)
            }

            // 3. Animated Glowing Hero Mascot in Center
            paint.style = Paint.Style.FILL
            val centerX = w * 0.5f + sin(animTime.toDouble() * 1.5).toFloat() * 40f
            val centerY = h * 0.45f + sin(animTime.toDouble() * 2.2).toFloat() * 20f

            // Aura Ring
            paint.color = Color.parseColor("#2200E676")
            canvas.drawCircle(centerX, centerY, 110f + sin(animTime.toDouble() * 3.0).toFloat() * 15f, paint)

            paint.color = Color.parseColor("#4400E5FF")
            canvas.drawCircle(centerX, centerY, 85f, paint)

            // Inner Core
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawCircle(centerX, centerY, 55f, paint)

            // Eye / Details
            paint.color = Color.WHITE
            canvas.drawCircle(centerX + 18f, centerY - 12f, 14f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(centerX + 22f, centerY - 12f, 7f, paint)

            // Floating Star Particles
            paint.color = Color.parseColor("#FFFFEE58")
            for (i in 0..12) {
                val px = ((i * 137.5f + animTime * 80f) % w)
                val py = (h * 0.25f + sin((animTime + i).toDouble() * 2.0).toFloat() * 90f + i * 25f) % (h * 0.75f)
                canvas.drawCircle(px, py, (i % 3 + 3).toFloat(), paint)
            }
        }
    }
}
