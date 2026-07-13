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

        // 1. Character Selection
        val charLayout = dialog.findViewById<LinearLayout>(R.id.opt_char_layout)
        val charValue = dialog.findViewById<TextView>(R.id.opt_char_value)
        
        val characters = arrayOf("RANDOM", "DADDY", "NINJA", "ASTRONAUT", "BABY", "GRANDPA", "SCIENTIST", "PIRATE", "MUMMY", "TEENAGER", "CHEF", "ATHLETE", "DRAGON", "ZOMBIE", "ROBOT", "KING")
        
        fun getCharDisplayName(key: String): String {
            if (key == "RANDOM") return "RANDOM"
            val resId = when(key) {
                "DADDY" -> R.string.trex_daddy
                "NINJA" -> R.string.trex_athlete // Shared
                "ASTRONAUT" -> R.string.trex_astronaut
                "BABY" -> R.string.trex_baby
                "GRANDPA" -> R.string.trex_grandpa
                "SCIENTIST" -> R.string.trex_scientist
                "PIRATE" -> R.string.trex_pirate
                "MUMMY" -> R.string.trex_mummy
                "TEENAGER" -> R.string.trex_teenager
                "CHEF" -> R.string.trex_chef
                "ATHLETE" -> R.string.trex_athlete
                "DRAGON" -> R.string.trex_dragon
                "ZOMBIE" -> R.string.trex_zombie
                "ROBOT" -> R.string.trex_robot
                "KING" -> R.string.trex_king
                else -> return key
            }
            return context.getString(resId).uppercase()
        }

        fun updateCharText() {
            val mode = prefs.getString("trex_char_mode", "specific")
            if (mode == "random") {
                charValue.text = "RANDOM"
            } else {
                val index = prefs.getInt("selected_char_index", 0)
                val key = if (index >= 0 && index < characters.size - 1) characters[index + 1] else "DADDY"
                charValue.text = getCharDisplayName(key)
            }
        }
        updateCharText()
        charLayout.setOnClickListener {
            val mode = prefs.getString("trex_char_mode", "specific")
            if (mode == "random") {
                prefs.edit().putString("trex_char_mode", "specific").putInt("selected_char_index", 0).apply()
            } else {
                val index = prefs.getInt("selected_char_index", 0)
                if (index < characters.size - 2) {
                    prefs.edit().putInt("selected_char_index", index + 1).apply()
                } else {
                    prefs.edit().putString("trex_char_mode", "random").apply()
                }
            }
            updateCharText()
        }
        setupFocusEffect(charLayout)

        // 2. Time Mode
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

        // 3. Season Mode
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

        // 4. Weather Mode
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

        val btnDone = dialog.findViewById<Button>(R.id.btn_close_opts)
        btnDone.setOnClickListener { dialog.dismiss() }
        setupFocusEffect(btnDone)

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
