package com.tdpham.games.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.tdpham.games.R

class InGamePauseDialog(
    context: Context,
    private val title: String? = null,
    private val hasOptions: Boolean = true,
    private val onResumeAction: () -> Unit,
    private val onOptionsAction: () -> Unit,
    private val onGuideAction: () -> Unit,
    private val onRestartAction: () -> Unit,
    private val onExitAction: () -> Unit
) : Dialog(context) {

    private val btnResume: Button
    private val btnOptions: Button
    private val btnGuide: Button
    private val btnRestart: Button
    private val btnExit: Button
    private val titleView: TextView

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_in_game_pause)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setCancelable(false)

        titleView = findViewById(R.id.pause_title)
        btnResume = findViewById(R.id.btn_pause_resume)
        btnOptions = findViewById(R.id.btn_pause_options)
        btnGuide = findViewById(R.id.btn_pause_guide)
        btnRestart = findViewById(R.id.btn_pause_restart)
        btnExit = findViewById(R.id.btn_pause_exit)

        title?.let { titleView.text = it }

        if (!hasOptions) {
            btnOptions.visibility = View.GONE
        } else {
            btnOptions.visibility = View.VISIBLE
            btnOptions.setOnClickListener {
                SoundManager.playClick()
                IdleAdManager.notifyInteraction()
                dismiss()
                onOptionsAction()
            }
            setupFocusEffect(btnOptions)
        }

        btnResume.setOnClickListener {
            SoundManager.playClick()
            IdleAdManager.notifyInteraction()
            dismiss()
            onResumeAction()
        }

        btnGuide.setOnClickListener {
            SoundManager.playClick()
            IdleAdManager.notifyInteraction()
            dismiss()
            onGuideAction()
        }

        btnRestart.setOnClickListener {
            SoundManager.playClick()
            IdleAdManager.notifyInteraction()
            dismiss()
            onRestartAction()
        }

        btnExit.setOnClickListener {
            SoundManager.playClick()
            IdleAdManager.notifyInteraction()
            dismiss()
            onExitAction()
        }

        setupFocusEffect(btnResume)
        setupFocusEffect(btnGuide)
        setupFocusEffect(btnRestart)
        setupFocusEffect(btnExit)

        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                IdleAdManager.notifyInteraction()
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss()
                    onResumeAction()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    override fun show() {
        super.show()
        btnResume.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                IdleAdManager.notifyInteraction()
                SoundManager.playClick()
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
        view.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                IdleAdManager.notifyInteraction()
                v.requestFocus()
            }
            false
        }
    }

    companion object {
        fun show(
            context: Context,
            title: String? = null,
            hasOptions: Boolean = true,
            onResume: () -> Unit,
            onOptions: () -> Unit,
            onGuide: () -> Unit,
            onRestart: () -> Unit,
            onExit: () -> Unit
        ): InGamePauseDialog {
            val dialog = InGamePauseDialog(
                context = context,
                title = title,
                hasOptions = hasOptions,
                onResumeAction = onResume,
                onOptionsAction = onOptions,
                onGuideAction = onGuide,
                onRestartAction = onRestart,
                onExitAction = onExit
            )
            dialog.show()
            return dialog
        }
    }
}
