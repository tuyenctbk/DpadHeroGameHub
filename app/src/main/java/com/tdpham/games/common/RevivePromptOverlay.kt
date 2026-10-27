package com.tdpham.games.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.tdpham.games.R

/**
 * Themed "Continue playing? Watch an ad" prompt. Matches the pause menu look, is fully D-pad
 * navigable, animates in with a spring-like pop and gently pulses the primary action.
 */
class RevivePromptOverlay(context: Context) : FrameLayout(context) {

    private val card: View
    private val icon: View
    private val btnWatch: View
    private val btnDecline: View
    private var pulse: ObjectAnimator? = null
    private var iconBounce: ObjectAnimator? = null
    private var isClosing = false

    var onWatch: (() -> Unit)? = null
    var onDecline: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_revive_overlay, this, true)
        card = findViewById(R.id.revive_card_container)
        icon = findViewById(R.id.revive_icon)
        btnWatch = findViewById(R.id.btn_revive_watch)
        btnDecline = findViewById(R.id.btn_revive_decline)

        isFocusable = true
        isFocusableInTouchMode = true
        alpha = 0f
        setOnClickListener { /* swallow touches so the game underneath doesn't react */ }

        wire(btnWatch) { close { onWatch?.invoke() } }
        wire(btnDecline) { close { onDecline?.invoke() } }
    }

    private fun wire(button: View, action: () -> Unit) {
        button.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(context)
            IdleAdManager.notifyInteraction()
            action()
        }
        button.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) SoundManager.playDpadMove()
            v.animate().scaleX(if (hasFocus) 1.05f else 1f).scaleY(if (hasFocus) 1.05f else 1f)
                .setDuration(120).start()
        }
        button.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) v.requestFocus()
            false
        }
    }

    fun show() {
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.translationY = 40f
        animate().alpha(1f).setDuration(220).start()
        card.animate().scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(320).setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                btnWatch.requestFocus()
                startAttentionAnimations()
            }.start()
        SoundManager.playDpadSelect()
    }

    private fun startAttentionAnimations() {
        iconBounce = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f)
        ).apply { duration = 1400; repeatCount = ObjectAnimator.INFINITE; start() }
        pulse = ObjectAnimator.ofFloat(btnWatch, View.ALPHA, 1f, 0.82f, 1f).apply {
            duration = 1400; repeatCount = ObjectAnimator.INFINITE; start()
        }
    }

    private fun close(after: () -> Unit) {
        if (isClosing) return
        isClosing = true
        pulse?.cancel()
        iconBounce?.cancel()
        card.animate().scaleX(0.92f).scaleY(0.92f).setDuration(140).start()
        animate().alpha(0f).setDuration(160).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                (parent as? ViewGroup)?.removeView(this@RevivePromptOverlay)
                after()
            }
        }).start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            IdleAdManager.notifyInteraction()
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                close { onDecline?.invoke() }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        iconBounce?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        fun show(
            container: ViewGroup,
            onWatch: () -> Unit,
            onDecline: () -> Unit
        ): RevivePromptOverlay {
            val overlay = RevivePromptOverlay(container.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                this.onWatch = onWatch
                this.onDecline = onDecline
            }
            container.addView(overlay)
            overlay.show()
            return overlay
        }
    }
}
