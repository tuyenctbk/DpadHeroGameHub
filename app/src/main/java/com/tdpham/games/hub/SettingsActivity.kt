package com.tdpham.games.hub

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tdpham.games.R
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.common.SettingsManager
import com.tdpham.games.common.SoundManager
import com.tdpham.games.hub.profile.ProfileSelectionActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.settings_title)?.let {
            androidx.core.view.ViewCompat.setTransitionName(it, "hub_settings_transition")
        }

        // 1. Sound Effects Toggle & Volume
        val soundToggleLayout = findViewById<LinearLayout>(R.id.layout_sound_toggle)
        val soundSwitch = findViewById<SwitchCompat>(R.id.switch_sound)
        val btnSoundVolume = findViewById<Button>(R.id.btn_sound_volume)
        val tvSoundDesc = findViewById<TextView>(R.id.tv_sound_desc)

        fun updateSoundUI() {
            val isEnabled = SettingsManager.isSoundEnabled(this)
            val volume = SettingsManager.getSoundVolume(this)
            soundSwitch.isChecked = isEnabled
            btnSoundVolume.text = "$volume%"
            tvSoundDesc.text = getString(R.string.volume_desc_format, volume)
        }
        updateSoundUI()

        soundToggleLayout.setOnClickListener {
            val newState = !SettingsManager.isSoundEnabled(this)
            SettingsManager.setSoundEnabled(this, newState)
            updateSoundUI()
            if (newState) SoundManager.playClick()
            HapticManager.vibrateClick(this)
        }

        btnSoundVolume.setOnClickListener {
            val current = SettingsManager.getSoundVolume(this)
            val next = when (current) {
                100 -> 75
                75 -> 50
                50 -> 25
                else -> 100
            }
            SettingsManager.setSoundVolume(this, next)
            updateSoundUI()
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
        }
        setupFocusEffect(soundToggleLayout)
        setupFocusEffect(btnSoundVolume)

        // 2. Sound Profile Preset (Arcade, Retro, Modern)
        val soundProfileLayout = findViewById<LinearLayout>(R.id.layout_sound_profile)
        val btnSoundProfile = findViewById<Button>(R.id.btn_sound_profile)
        val tvSoundProfileDesc = findViewById<TextView>(R.id.tv_sound_profile_desc)

        fun updateSoundProfileUI() {
            val preset = SettingsManager.getSoundProfilePreset(this)
            btnSoundProfile.text = getString(preset.labelResId)
            tvSoundProfileDesc.text = getString(R.string.sound_profile_desc_format, getString(preset.descResId))
        }
        updateSoundProfileUI()

        val onSoundProfileCycle = View.OnClickListener {
            val nextPreset = SettingsManager.cycleSoundProfilePreset(this)
            updateSoundProfileUI()
            SoundManager.playProfileSound(
                SoundManager.SoundProfile.CLASSIC_PUZZLE,
                SoundManager.GameSoundEvent.SCORE
            )
            HapticManager.vibrateClick(this)
            Toast.makeText(this, getString(R.string.sound_profile_toast, getString(nextPreset.labelResId)), Toast.LENGTH_SHORT).show()
        }
        soundProfileLayout.setOnClickListener(onSoundProfileCycle)
        btnSoundProfile.setOnClickListener(onSoundProfileCycle)
        setupFocusEffect(soundProfileLayout)
        setupFocusEffect(btnSoundProfile)

        // 3. Retro Background Music Toggle
        val musicToggleLayout = findViewById<LinearLayout>(R.id.layout_music_toggle)
        val musicSwitch = findViewById<SwitchCompat>(R.id.switch_music)
        musicSwitch.isChecked = SettingsManager.isMusicEnabled(this)

        musicToggleLayout.setOnClickListener {
            val newState = !musicSwitch.isChecked
            musicSwitch.isChecked = newState
            SettingsManager.setMusicEnabled(this, newState)
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
        }
        setupFocusEffect(musicToggleLayout)

        // 3. Controller & Gamepad Sensitivity
        val layoutSensitivity = findViewById<LinearLayout>(R.id.layout_sensitivity)
        val btnSensitivity = findViewById<Button>(R.id.btn_sensitivity)

        fun updateSensitivityUI() {
            val preset = SettingsManager.getSensitivityPreset(this)
            btnSensitivity.text = preset.label
        }
        updateSensitivityUI()

        val onSensitivityCycle = View.OnClickListener {
            val next = SettingsManager.cycleSensitivity(this)
            updateSensitivityUI()
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            val msg = getString(R.string.sensitivity_toast, next.label)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        layoutSensitivity.setOnClickListener(onSensitivityCycle)
        btnSensitivity.setOnClickListener(onSensitivityCycle)
        setupFocusEffect(layoutSensitivity)
        setupFocusEffect(btnSensitivity)

        // 4. Haptic Feedback Vibration & Controller Calibration
        val hapticToggleLayout = findViewById<LinearLayout>(R.id.layout_haptic_toggle)
        val hapticSwitch = findViewById<SwitchCompat>(R.id.switch_haptic)
        val btnCalibrate = findViewById<Button>(R.id.btn_open_controller_settings)
        hapticSwitch.isChecked = SettingsManager.isHapticEnabled(this)

        hapticToggleLayout.setOnClickListener {
            val newState = !hapticSwitch.isChecked
            hapticSwitch.isChecked = newState
            SettingsManager.setHapticEnabled(this, newState)
            if (newState) {
                HapticManager.vibrateSuccess(this)
            }
            SoundManager.playClick()
        }
        btnCalibrate.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            startActivity(Intent(this, ControllerSettingsActivity::class.java))
        }
        setupFocusEffect(hapticToggleLayout)
        setupFocusEffect(btnCalibrate)

        // 5. Scanline CRT Filter Toggle
        val scanlineToggleLayout = findViewById<LinearLayout>(R.id.layout_scanline_toggle)
        val scanlineSwitch = findViewById<SwitchCompat>(R.id.switch_scanline)
        scanlineSwitch.isChecked = SettingsManager.isScanlineEnabled(this)

        scanlineToggleLayout.setOnClickListener {
            val newState = !scanlineSwitch.isChecked
            scanlineSwitch.isChecked = newState
            SettingsManager.setScanlineEnabled(this, newState)
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
        }
        setupFocusEffect(scanlineToggleLayout)

        // 6. Screen Shake Toggle
        val shakeToggleLayout = findViewById<LinearLayout>(R.id.layout_shake_toggle)
        val shakeSwitch = findViewById<SwitchCompat>(R.id.switch_shake)
        shakeSwitch.isChecked = SettingsManager.isScreenShakeEnabled(this)

        shakeToggleLayout.setOnClickListener {
            val newState = !shakeSwitch.isChecked
            shakeSwitch.isChecked = newState
            SettingsManager.setScreenShakeEnabled(this, newState)
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
        }
        setupFocusEffect(shakeToggleLayout)

        // 7. Profile Management Button
        findViewById<Button>(R.id.btn_switch_profile).apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@SettingsActivity)
                startActivity(Intent(this@SettingsActivity, ProfileSelectionActivity::class.java))
            }
            setupFocusEffect(this)
        }

        // 8. Reset High Scores Button
        findViewById<Button>(R.id.btn_reset_scores).apply {
            setOnClickListener {
                SoundManager.playClick()
                HapticManager.vibrateClick(this@SettingsActivity)
                AlertDialog.Builder(this@SettingsActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.reset_high_scores)
                    .setMessage(R.string.reset_scores_confirm)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        ScoreManager.clearHighScoresForActiveProfile(this@SettingsActivity)
                        SoundManager.playSuccess()
                        HapticManager.vibrateSuccess(this@SettingsActivity)
                        Toast.makeText(this@SettingsActivity, R.string.scores_cleared, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            setupFocusEffect(this)
        }

        // 9. Back Button
        val btnBack = findViewById<Button>(R.id.btn_back)
        btnBack.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            finish()
        }
        setupFocusEffect(btnBack)

        soundToggleLayout.requestFocus()
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                HapticManager.vibrateClick(this)
                v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(8f).setDuration(200).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(200).start()
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
