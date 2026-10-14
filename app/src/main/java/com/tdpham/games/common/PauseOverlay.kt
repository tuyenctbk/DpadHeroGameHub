package com.tdpham.games.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.tdpham.games.R

/**
 * Reusable, high-performance PauseOverlay layout designed for the BaseGameView / BaseGameActivity architecture.
 * Provides D-pad / Gamepad accessible navigation across Resume, Settings/Options, Guide, Restart, and Exit functions.
 */
class PauseOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cardContainer: View
    private val titleView: TextView
    private val gameTitleView: TextView
    val btnResume: Button
    val btnOptions: Button
    val btnGuide: Button
    val btnRestart: Button
    val btnExit: Button

    var onResumeAction: (() -> Unit)? = null
    var onOptionsAction: (() -> Unit)? = null
    var onGuideAction: (() -> Unit)? = null
    var onRestartAction: (() -> Unit)? = null
    var onExitAction: (() -> Unit)? = null

    private var isAnimating = false

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_pause_overlay, this, true)

        cardContainer = findViewById(R.id.pause_card_container)
        titleView = findViewById(R.id.pause_overlay_title)
        gameTitleView = findViewById(R.id.pause_overlay_game_title)
        btnResume = findViewById(R.id.btn_pause_resume)
        btnOptions = findViewById(R.id.btn_pause_options)
        btnGuide = findViewById(R.id.btn_pause_guide)
        btnRestart = findViewById(R.id.btn_pause_restart)
        btnExit = findViewById(R.id.btn_pause_exit)

        isFocusable = true
        isFocusableInTouchMode = true
        visibility = View.GONE
        alpha = 0f

        setupButton(btnResume) {
            hide { onResumeAction?.invoke() }
        }
        setupButton(btnOptions) {
            hide { onOptionsAction?.invoke() }
        }
        setupButton(btnGuide) {
            hide { onGuideAction?.invoke() }
        }
        setupButton(btnRestart) {
            hide { onRestartAction?.invoke() }
        }
        setupButton(btnExit) {
            hide { onExitAction?.invoke() }
        }

        // Prevent touches/clicks from passing through to background game
        setOnClickListener { /* Consume clicks */ }
    }

    private fun setupButton(button: Button, action: () -> Unit) {
        button.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(context)
            IdleAdManager.notifyInteraction()
            action()
        }

        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                SoundManager.playDpadMove()
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }

        button.setOnHoverListener { view, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                view.requestFocus()
            }
            false
        }
    }

    fun configure(
        gameTitle: String? = null,
        hasOptions: Boolean = true,
        hasGuide: Boolean = true,
        onResume: () -> Unit,
        onOptions: (() -> Unit)? = null,
        onGuide: (() -> Unit)? = null,
        onRestart: (() -> Unit)? = null,
        onExit: () -> Unit
    ) {
        if (!gameTitle.isNullOrEmpty()) {
            gameTitleView.visibility = View.VISIBLE
            gameTitleView.text = gameTitle
        } else {
            gameTitleView.visibility = View.GONE
        }

        this.onResumeAction = onResume
        this.onOptionsAction = onOptions
        this.onGuideAction = onGuide
        this.onRestartAction = onRestart
        this.onExitAction = onExit

        btnOptions.visibility = if (hasOptions && onOptions != null) View.VISIBLE else View.GONE
        btnGuide.visibility = if (hasGuide && onGuide != null) View.VISIBLE else View.GONE
        btnRestart.visibility = if (onRestart != null) View.VISIBLE else View.GONE

        // Update D-pad focus chain dynamically
        updateFocusChain()
    }

    private fun updateFocusChain() {
        val visibleButtons = listOf(btnResume, btnOptions, btnGuide, btnRestart, btnExit)
            .filter { it.visibility == View.VISIBLE }

        for (i in visibleButtons.indices) {
            val current = visibleButtons[i]
            val prev = if (i > 0) visibleButtons[i - 1] else visibleButtons.last()
            val next = if (i < visibleButtons.size - 1) visibleButtons[i + 1] else visibleButtons.first()
            current.nextFocusUpId = prev.id
            current.nextFocusDownId = next.id
        }
    }

    fun show() {
        if (visibility == View.VISIBLE && !isAnimating) return
        isAnimating = true
        visibility = View.VISIBLE
        alpha = 0f
        cardContainer.scaleX = 0.9f
        cardContainer.scaleY = 0.9f

        animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(null)
            .start()

        cardContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    btnResume.requestFocus()
                }
            })
            .start()

        SoundManager.playDpadSelect()
    }

    fun hide(onHidden: (() -> Unit)? = null) {
        if (visibility != View.VISIBLE && !isAnimating) {
            onHidden?.invoke()
            return
        }
        isAnimating = true
        animate()
            .alpha(0f)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    isAnimating = false
                    onHidden?.invoke()
                }
            })
            .start()
    }

    val isShowing: Boolean
        get() = visibility == View.VISIBLE

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            IdleAdManager.notifyInteraction()
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    hide { onResumeAction?.invoke() }
                    return true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_O -> {
                    if (btnOptions.visibility == View.VISIBLE) {
                        hide { onOptionsAction?.invoke() }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        /**
         * Attaches and binds a PauseOverlay to any root ViewGroup container or BaseGameView container.
         */
        fun attach(
            container: ViewGroup,
            gameTitle: String? = null,
            hasOptions: Boolean = true,
            hasGuide: Boolean = true,
            onResume: () -> Unit,
            onOptions: (() -> Unit)? = null,
            onGuide: (() -> Unit)? = null,
            onRestart: (() -> Unit)? = null,
            onExit: () -> Unit
        ): PauseOverlay {
            // Check if already attached
            var overlay = container.findViewWithTag<PauseOverlay>("PAUSE_OVERLAY_TAG")
            if (overlay == null) {
                overlay = PauseOverlay(container.context).apply {
                    tag = "PAUSE_OVERLAY_TAG"
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(overlay)
            }

            overlay.configure(
                gameTitle = gameTitle,
                hasOptions = hasOptions,
                hasGuide = hasGuide,
                onResume = onResume,
                onOptions = onOptions,
                onGuide = onGuide,
                onRestart = onRestart,
                onExit = onExit
            )

            return overlay
        }
    }
}
