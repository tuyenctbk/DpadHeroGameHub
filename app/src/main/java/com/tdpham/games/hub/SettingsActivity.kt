package com.tdpham.games.hub

import android.os.Bundle
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

        val soundToggleLayout = findViewById<LinearLayout>(R.id.layout_sound_toggle)
        val soundSwitch = findViewById<SwitchCompat>(R.id.switch_sound)
        val btnBack = findViewById<Button>(R.id.btn_back)

        soundSwitch.isChecked = SoundManager.isSoundEnabled()

        soundToggleLayout.setOnClickListener {
            val isEnabled = SoundManager.toggleSound()
            soundSwitch.isChecked = isEnabled
        }

        btnBack.setOnClickListener {
            finish()
        }
        
        soundToggleLayout.requestFocus()
    }
}
