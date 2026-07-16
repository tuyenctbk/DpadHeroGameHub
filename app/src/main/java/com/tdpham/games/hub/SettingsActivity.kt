package com.tdpham.games.hub

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tdpham.games.R
import com.tdpham.games.common.SoundManager

class SettingsActivity : AppCompatActivity() {

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

        val btnBack = findViewById<Button>(R.id.btn_back)
        btnBack.setOnClickListener { finish() }
        setupFocusEffect(btnBack)
        
        soundToggleLayout.requestFocus()
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
