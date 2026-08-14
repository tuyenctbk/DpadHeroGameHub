package com.tdpham.games.common

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.tdpham.games.R

object ReviveDialog {

    fun show(
        activity: Activity,
        onReviveConfirmed: () -> Unit,
        onGiveUp: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_revive_continue)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val tvTitle = dialog.findViewById<TextView>(R.id.tv_revive_title)
        val btnContinue = dialog.findViewById<Button>(R.id.btn_revive_continue)
        val btnGiveUp = dialog.findViewById<Button>(R.id.btn_revive_give_up)

        val mainHandler = Handler(Looper.getMainLooper())
        var remainingSeconds = 6
        var isActionTaken = false

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (isActionTaken || activity.isFinishing || activity.isDestroyed) return
                remainingSeconds--
                if (remainingSeconds > 0) {
                    tvTitle.text = activity.getString(R.string.continue_countdown, remainingSeconds)
                    mainHandler.postDelayed(this, 1000)
                } else {
                    isActionTaken = true
                    if (dialog.isShowing) {
                        try {
                            dialog.dismiss()
                        } catch (_: Exception) {}
                    }
                    onGiveUp()
                }
            }
        }

        tvTitle.text = activity.getString(R.string.continue_countdown, remainingSeconds)
        mainHandler.postDelayed(countdownRunnable, 1000)

        btnContinue.setOnClickListener {
            if (isActionTaken) return@setOnClickListener
            isActionTaken = true
            mainHandler.removeCallbacks(countdownRunnable)
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
            onReviveConfirmed()
        }

        btnGiveUp.setOnClickListener {
            if (isActionTaken) return@setOnClickListener
            isActionTaken = true
            mainHandler.removeCallbacks(countdownRunnable)
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
            onGiveUp()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (!isActionTaken) {
                        isActionTaken = true
                        mainHandler.removeCallbacks(countdownRunnable)
                        try {
                            dialog.dismiss()
                        } catch (_: Exception) {}
                        onGiveUp()
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }

        try {
            dialog.show()
            btnContinue.requestFocus()
        } catch (_: Throwable) {
            onGiveUp()
        }
    }
}
