package com.tdpham.games.roadracer

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.tdpham.games.R

object RoadRacerOptionsDialog {
    private const val PREFS_NAME = "roadracer_settings"
    private const val KEY_DENSITY = "traffic_density_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_road_racer_settings)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val densityLayout = dialog.findViewById<LinearLayout>(R.id.opt_density_layout)
        val densityValue = dialog.findViewById<TextView>(R.id.opt_density_value)

        fun updateDensityText() {
            val index = prefs.getInt(KEY_DENSITY, 1)
            val valRes = when(index) {
                0 -> R.string.road_racer_density_low
                2 -> R.string.road_racer_density_high
                else -> R.string.road_racer_density_normal
            }
            densityValue.text = context.getString(valRes)
        }

        updateDensityText()

        densityLayout.setOnClickListener {
            val index = prefs.getInt(KEY_DENSITY, 1)
            val nextIndex = (index + 1) % 3
            prefs.edit { putInt(KEY_DENSITY, nextIndex) }
            updateDensityText()
        }

        setupFocusEffect(densityLayout)

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        densityLayout.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200).start()
                v.setBackgroundColor("#33FFFFFF".toColorInt())
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.setBackgroundColor(Color.TRANSPARENT)
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
