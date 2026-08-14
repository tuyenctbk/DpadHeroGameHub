package com.tdpham.games.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.tdpham.games.R

class BaseOptionsDialog(context: Context) : Dialog(context) {

    private val container: LinearLayout
    private val titleView: TextView
    private val btnDone: Button
    private val btnHelp: Button
    private var onDismissAction: (() -> Unit)? = null
    private var onHelpAction: (() -> Unit)? = null

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_base_settings)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setCancelable(true)

        container = findViewById(R.id.options_container)
        titleView = findViewById(R.id.dialog_title)
        btnDone = findViewById(R.id.btn_done)
        btnHelp = findViewById(R.id.btn_help)

        btnDone.setOnClickListener {
            dismiss()
        }
        setupFocusEffect(btnDone)

        btnHelp.setOnClickListener {
            dismiss()
            onHelpAction?.invoke() ?: (context as? BaseGameActivity)?.showGameGuide()
        }
        setupFocusEffect(btnHelp)

        setOnDismissListener { onDismissAction?.invoke() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            IdleAdManager.notifyInteraction()
            if (event.keyCode == KeyEvent.KEYCODE_H || event.keyCode == KeyEvent.KEYCODE_HELP) {
                btnHelp.performClick()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        IdleAdManager.notifyInteraction()
        return super.dispatchTouchEvent(ev)
    }

    fun setTitle(title: String): BaseOptionsDialog {
        titleView.text = title
        return this
    }

    fun setTitleColor(color: Int): BaseOptionsDialog {
        titleView.setTextColor(color)
        return this
    }

    fun setBackgroundResource(resId: Int): BaseOptionsDialog {
        findViewById<View>(R.id.root_container).setBackgroundResource(resId)
        return this
    }

    fun setOnDismiss(action: () -> Unit): BaseOptionsDialog {
        onDismissAction = action
        return this
    }

    fun setOnHelp(action: () -> Unit): BaseOptionsDialog {
        onHelpAction = action
        return this
    }

    fun addOption(
        label: String,
        valueProvider: () -> String,
        descProvider: () -> String,
        onClick: () -> Unit
    ): BaseOptionsDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.item_dialog_option, container, false)
        val lbl = view.findViewById<TextView>(R.id.option_label)
        val valTxt = view.findViewById<TextView>(R.id.option_value)
        val descTxt = view.findViewById<TextView>(R.id.option_desc)

        lbl.text = label

        fun update() {
            valTxt.text = valueProvider()
            descTxt.text = descProvider()
        }

        update()

        view.setOnClickListener {
            onClick()
            update()
        }

        setupFocusEffect(view)
        container.addView(view)
        return this
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                IdleAdManager.notifyInteraction()
                SoundManager.playClick()
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(180).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180).start()
            }
        }
        view.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                v.requestFocus()
            }
            false
        }
    }
}
