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
        val charDesc = dialog.findViewById<TextView>(R.id.opt_char_desc)
        
        // Move RANDOM to the end for a better default experience
        val characters = arrayOf("DADDY", "NINJA", "ASTRONAUT", "BABY", "GRANDPA", "SCIENTIST", "PIRATE", "MUMMY", "TEENAGER", "CHEF", "ATHLETE", "DRAGON", "ZOMBIE", "ROBOT", "KING", "RANDOM")
        
        fun getCharDisplayName(key: String): String {
            if (key == "RANDOM") return context.getString(R.string.trex_random)
            val resId = when(key) {
                "DADDY" -> R.string.trex_daddy
                "NINJA" -> R.string.trex_ninja
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
                charValue.text = context.getString(R.string.trex_random)
                charDesc.text = context.getString(R.string.trex_desc_char_random)
            } else {
                val index = prefs.getInt("selected_char_index", 0)
                val key = if (index >= 0 && index < characters.size - 1) characters[index] else "DADDY"
                charValue.text = getCharDisplayName(key)
                charDesc.text = context.getString(R.string.trex_desc_char_specific)
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
        val timeDesc = dialog.findViewById<TextView>(R.id.opt_time_desc)
        val timeModes = arrayOf("random", "day", "night")
        fun updateTimeText() {
            val mode = prefs.getString("trex_time_mode", "random")
            val (valId, descId) = when(mode) {
                "day" -> R.string.trex_day to R.string.trex_desc_time_day
                "night" -> R.string.trex_night to R.string.trex_desc_time_night
                else -> R.string.trex_random to R.string.trex_desc_time_random
            }
            timeValue.text = context.getString(valId).uppercase()
            timeDesc.text = context.getString(descId)
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
        val seasonDesc = dialog.findViewById<TextView>(R.id.opt_season_desc)
        val seasonModes = arrayOf("random", "spring", "summer", "autumn", "winter")
        fun updateSeasonText() {
            val mode = prefs.getString("trex_season_mode", "random")
            val (valId, descId) = when(mode) {
                "spring" -> R.string.trex_spring to R.string.trex_desc_season_spring
                "summer" -> R.string.trex_summer to R.string.trex_desc_season_summer
                "autumn" -> R.string.trex_autumn to R.string.trex_desc_season_autumn
                "winter" -> R.string.trex_winter to R.string.trex_desc_season_winter
                else -> R.string.trex_random to R.string.trex_desc_season_random
            }
            seasonValue.text = context.getString(valId).uppercase()
            seasonDesc.text = context.getString(descId)
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
        val weatherDesc = dialog.findViewById<TextView>(R.id.opt_weather_desc)
        val weatherModes = arrayOf("random", "sunny", "rainy", "snowy")
        fun updateWeatherText() {
            val mode = prefs.getString("trex_weather_mode", "random")
            val (valId, descId) = when(mode) {
                "sunny" -> R.string.trex_sunny to R.string.trex_desc_weather_sunny
                "rainy" -> R.string.trex_rainy to R.string.trex_desc_weather_rainy
                "snowy" -> R.string.trex_snowy to R.string.trex_desc_weather_snowy
                else -> R.string.trex_random to R.string.trex_desc_weather_random
            }
            weatherValue.text = context.getString(valId).uppercase()
            weatherDesc.text = context.getString(descId)
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
