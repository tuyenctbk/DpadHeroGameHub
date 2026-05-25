package com.tdpham.games.hub

import android.content.Intent
import android.net.Uri
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
        val privacyPolicyLayout = findViewById<LinearLayout>(R.id.layout_privacy_policy)
        val btnBack = findViewById<Button>(R.id.btn_back)

        soundSwitch.isChecked = SoundManager.isSoundEnabled()

        soundToggleLayout.setOnClickListener {
            val isEnabled = SoundManager.toggleSound()
            soundSwitch.isChecked = isEnabled
        }

        privacyPolicyLayout.setOnClickListener {
            // Replace with your actual Privacy Policy URL
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tuyenctbk/DpadHeroGameHub/blob/main/PRIVACY_POLICY.md"))
            startActivity(browserIntent)
        }

        btnBack.setOnClickListener {
            finish()
        }
        
        soundToggleLayout.requestFocus()
    }
}
