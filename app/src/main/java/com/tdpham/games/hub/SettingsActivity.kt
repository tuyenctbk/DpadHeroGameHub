package com.tdpham.games.hub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tdpham.games.R
import com.tdpham.games.common.SoundManager

class SettingsActivity : AppCompatActivity() {

    private val PREFS_TREX = "trex_settings"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // General
        val soundToggleLayout = findViewById<LinearLayout>(R.id.layout_sound_toggle)
        val soundSwitch = findViewById<SwitchCompat>(R.id.switch_sound)
        
        soundSwitch.isChecked = SoundManager.isSoundEnabled()
        soundToggleLayout.setOnClickListener {
            val isEnabled = SoundManager.toggleSound()
            soundSwitch.isChecked = isEnabled
        }
        setupFocusEffect(soundToggleLayout)

        // T-Rex Customization
        setupTRexSettings()

        // Information
        val privacyPolicyLayout = findViewById<LinearLayout>(R.id.layout_privacy_policy)
        privacyPolicyLayout.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tuyenctbk/DpadHeroGameHub/blob/main/PRIVACY_POLICY.md"))
                startActivity(browserIntent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, getString(R.string.privacy_policy_url), android.widget.Toast.LENGTH_LONG).show()
            }
        }
        setupFocusEffect(privacyPolicyLayout)

        val btnBack = findViewById<Button>(R.id.btn_back)
        btnBack.setOnClickListener { finish() }
        setupFocusEffect(btnBack)
        
        soundToggleLayout.requestFocus()
    }

    private fun setupTRexSettings() {
        val prefs = getSharedPreferences(PREFS_TREX, MODE_PRIVATE)
        
        // Character Setting
        val charLayout = findViewById<LinearLayout>(R.id.setting_trex_char)
        val charText = findViewById<TextView>(R.id.txt_trex_char)
        
        fun updateCharText() {
            val mode = prefs.getString("trex_char_mode", "specific")
            if (mode == "random") {
                charText.text = "Mode: Random"
            } else {
                charText.text = "Mode: Specific (Change in game)"
            }
        }
        updateCharText()
        charLayout.setOnClickListener {
            val currentMode = prefs.getString("trex_char_mode", "specific")
            val nextMode = if (currentMode == "specific") "random" else "specific"
            prefs.edit().putString("trex_char_mode", nextMode).apply()
            updateCharText()
        }
        setupFocusEffect(charLayout)

        // Time Setting
        val timeLayout = findViewById<LinearLayout>(R.id.setting_trex_time)
        val timeText = findViewById<TextView>(R.id.txt_trex_time)
        val timeModes = arrayOf("random", "day", "night")
        
        fun updateTimeText() {
            timeText.text = prefs.getString("trex_time_mode", "random")?.uppercase()
        }
        updateTimeText()
        timeLayout.setOnClickListener {
            val current = prefs.getString("trex_time_mode", "random")
            val next = timeModes[(timeModes.indexOf(current) + 1) % timeModes.size]
            prefs.edit().putString("trex_time_mode", next).apply()
            updateTimeText()
        }
        setupFocusEffect(timeLayout)

        // Season Setting
        val seasonLayout = findViewById<LinearLayout>(R.id.setting_trex_season)
        val seasonText = findViewById<TextView>(R.id.txt_trex_season)
        val seasonModes = arrayOf("random", "spring", "summer", "autumn", "winter")
        
        fun updateSeasonText() {
            seasonText.text = prefs.getString("trex_season_mode", "random")?.uppercase()
        }
        updateSeasonText()
        seasonLayout.setOnClickListener {
            val current = prefs.getString("trex_season_mode", "random")
            val next = seasonModes[(seasonModes.indexOf(current) + 1) % seasonModes.size]
            prefs.edit().putString("trex_season_mode", next).apply()
            updateSeasonText()
        }
        setupFocusEffect(seasonLayout)

        // Weather Setting
        val weatherLayout = findViewById<LinearLayout>(R.id.setting_trex_weather)
        val weatherText = findViewById<TextView>(R.id.txt_trex_weather)
        val weatherModes = arrayOf("random", "sunny", "rainy", "snowy")
        
        fun updateWeatherText() {
            weatherText.text = prefs.getString("trex_weather_mode", "random")?.uppercase()
        }
        updateWeatherText()
        weatherLayout.setOnClickListener {
            val current = prefs.getString("trex_weather_mode", "random")
            val next = weatherModes[(weatherModes.indexOf(current) + 1) % weatherModes.size]
            prefs.edit().putString("trex_weather_mode", next).apply()
            updateWeatherText()
        }
        setupFocusEffect(weatherLayout)
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
