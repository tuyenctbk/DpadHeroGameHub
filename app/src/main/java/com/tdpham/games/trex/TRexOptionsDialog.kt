package com.tdpham.games.trex

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.tdpham.games.R

object TRexOptionsDialog {
    private const val PREFS_NAME = "trex_settings"

    fun show(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_trex_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Character Mode
        val charLayout = dialog.findViewById<LinearLayout>(R.id.opt_char_layout)
        val charValue = dialog.findViewById<TextView>(R.id.opt_char_value)
        fun updateCharText() {
            charValue.text = prefs.getString("trex_char_mode", "specific")?.replaceFirstChar { it.uppercase() }
        }
        updateCharText()
        charLayout.setOnClickListener {
            val current = prefs.getString("trex_char_mode", "specific")
            val next = if (current == "specific") "random" else "specific"
            prefs.edit().putString("trex_char_mode", next).apply()
            updateCharText()
        }
        setupFocusEffect(charLayout)

        // Time Mode
        val timeLayout = dialog.findViewById<LinearLayout>(R.id.opt_time_layout)
        val timeValue = dialog.findViewById<TextView>(R.id.opt_time_value)
        val timeModes = arrayOf("random", "day", "night")
        fun updateTimeText() {
            timeValue.text = prefs.getString("trex_time_mode", "random")?.uppercase()
        }
        updateTimeText()
        timeLayout.setOnClickListener {
            val current = prefs.getString("trex_time_mode", "random")
            val next = timeModes[(timeModes.indexOf(current) + 1) % timeModes.size]
            prefs.edit().putString("trex_time_mode", next).apply()
            updateTimeText()
        }
        setupFocusEffect(timeLayout)

        // Season Mode
        val seasonLayout = dialog.findViewById<LinearLayout>(R.id.opt_season_layout)
        val seasonValue = dialog.findViewById<TextView>(R.id.opt_season_value)
        val seasonModes = arrayOf("random", "spring", "summer", "autumn", "winter")
        fun updateSeasonText() {
            seasonValue.text = prefs.getString("trex_season_mode", "random")?.uppercase()
        }
        updateSeasonText()
        seasonLayout.setOnClickListener {
            val current = prefs.getString("trex_season_mode", "random")
            val next = seasonModes[(seasonModes.indexOf(current) + 1) % seasonModes.size]
            prefs.edit().putString("trex_season_mode", next).apply()
            updateSeasonText()
        }
        setupFocusEffect(seasonLayout)

        // Weather Mode
        val weatherLayout = dialog.findViewById<LinearLayout>(R.id.opt_weather_layout)
        val weatherValue = dialog.findViewById<TextView>(R.id.opt_weather_value)
        val weatherModes = arrayOf("random", "sunny", "rainy", "snowy")
        fun updateWeatherText() {
            weatherValue.text = prefs.getString("trex_weather_mode", "random")?.uppercase()
        }
        updateWeatherText()
        weatherLayout.setOnClickListener {
            val current = prefs.getString("trex_weather_mode", "random")
            val next = weatherModes[(weatherModes.indexOf(current) + 1) % weatherModes.size]
            prefs.edit().putString("trex_weather_mode", next).apply()
            updateWeatherText()
        }
        setupFocusEffect(weatherLayout)

        val btnClose = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        setupFocusEffect(btnClose)

        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        charLayout.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200).start()
                v.setBackgroundColor(Color.parseColor("#33FFFFFF"))
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
