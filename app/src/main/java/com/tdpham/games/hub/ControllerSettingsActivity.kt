package com.tdpham.games.hub

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.tdpham.games.R
import com.tdpham.games.common.HapticManager
import com.tdpham.games.common.SettingsManager
import com.tdpham.games.common.SoundManager

class ControllerSettingsActivity : AppCompatActivity() {

    private var selectedTestProfile: String = "click"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controller_settings)

        // 1. Hardware Diagnostic Info
        val tvGamepadStatus = findViewById<TextView>(R.id.tv_gamepad_status)
        val tvVibratorInfo = findViewById<TextView>(R.id.tv_vibrator_hardware_info)

        val isGamepadConnected = SoundManager.isGamepadConnected(this)
        val gamepadSummary = SoundManager.getGamepadStatusSummary(this)
        tvGamepadStatus.text = gamepadSummary

        val vibrator = HapticManager.getVibrator(this)
        val hasVibrator = vibrator?.hasVibrator() ?: false
        val hasAmplitude = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() ?: false
        } else false

        val apiLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getString(R.string.vibrator_manager_api) else getString(R.string.vibrator_legacy)
        val statusYes = getString(R.string.status_yes)
        val statusNo = getString(R.string.status_no)
        val statusFallback = getString(R.string.status_fallback)
        tvVibratorInfo.text = getString(
            R.string.motor_hardware_info,
            apiLevel,
            if (hasVibrator) statusYes else statusNo,
            if (hasAmplitude) statusYes else statusFallback
        )

        // 2. Master Haptic Switch
        val layoutMasterToggle = findViewById<LinearLayout>(R.id.layout_haptic_master_toggle)
        val switchMaster = findViewById<SwitchCompat>(R.id.switch_haptic_master)
        switchMaster.isChecked = SettingsManager.isHapticEnabled(this)

        layoutMasterToggle.setOnClickListener {
            val newState = !switchMaster.isChecked
            switchMaster.isChecked = newState
            SettingsManager.setHapticEnabled(this, newState)
            SoundManager.playClick()
            if (newState) {
                HapticManager.vibrateSuccess(this)
            }
        }
        setupFocusEffect(layoutMasterToggle)

        // 3. Visual Haptic Intensity Slider
        val tvIntensityPercent = findViewById<TextView>(R.id.tv_haptic_intensity_percent)
        val seekbarIntensity = findViewById<SeekBar>(R.id.seekbar_haptic_intensity)
        val currentIntensity = SettingsManager.getHapticIntensity(this)

        seekbarIntensity.progress = currentIntensity
        tvIntensityPercent.text = "$currentIntensity%"

        seekbarIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvIntensityPercent.text = "$progress%"
                if (fromUser) {
                    SettingsManager.setHapticIntensity(this@ControllerSettingsActivity, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 100
                SettingsManager.setHapticIntensity(this@ControllerSettingsActivity, progress)
                HapticManager.vibrateClick(this@ControllerSettingsActivity)
            }
        })
        setupFocusEffect(seekbarIntensity)

        // 4. Test Vibration Profile Buttons
        val btnClick = findViewById<Button>(R.id.btn_profile_click)
        val btnScore = findViewById<Button>(R.id.btn_profile_score)
        val btnDamage = findViewById<Button>(R.id.btn_profile_damage)
        val btnExplosion = findViewById<Button>(R.id.btn_profile_explosion)
        val btnSuccess = findViewById<Button>(R.id.btn_profile_success)
        val btnTestNow = findViewById<Button>(R.id.btn_test_vibration)

        val profileButtons = listOf(
            btnClick to "click",
            btnScore to "score",
            btnDamage to "damage",
            btnExplosion to "explosion",
            btnSuccess to "success"
        )

        fun updateProfileSelection(selected: String) {
            selectedTestProfile = selected
            for ((btn, type) in profileButtons) {
                if (type == selected) {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                    btn.setTextColor(Color.parseColor("#0A0E17"))
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B263B"))
                    btn.setTextColor(Color.WHITE)
                }
            }
        }
        updateProfileSelection("click")

        for ((btn, type) in profileButtons) {
            btn.setOnClickListener {
                updateProfileSelection(type)
                SoundManager.playClick()
                HapticManager.testVibration(this, type)
            }
            setupFocusEffect(btn)
        }

        btnTestNow.setOnClickListener {
            SoundManager.playClick()
            HapticManager.testVibration(this, selectedTestProfile)
            val msg = getString(R.string.haptic_test_fired, selectedTestProfile, SettingsManager.getHapticIntensity(this))
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        setupFocusEffect(btnTestNow)

        // 5. Controller Sensitivity
        val layoutSensitivity = findViewById<LinearLayout>(R.id.layout_controller_sensitivity)
        val btnSensitivityBadge = findViewById<Button>(R.id.btn_controller_sensitivity_badge)

        fun updateSensitivityDisplay() {
            val preset = SettingsManager.getSensitivityPreset(this)
            btnSensitivityBadge.text = preset.label
        }
        updateSensitivityDisplay()

        val onSensitivityCycle = View.OnClickListener {
            val next = SettingsManager.cycleSensitivity(this)
            updateSensitivityDisplay()
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            val msg = getString(R.string.sensitivity_toast, next.label)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        layoutSensitivity.setOnClickListener(onSensitivityCycle)
        btnSensitivityBadge.setOnClickListener(onSensitivityCycle)
        setupFocusEffect(layoutSensitivity)
        setupFocusEffect(btnSensitivityBadge)

        // 6. Controller Deadzone Adjustment Slider
        val tvDeadzonePercent = findViewById<TextView>(R.id.tv_deadzone_percent)
        val seekbarDeadzone = findViewById<SeekBar>(R.id.seekbar_controller_deadzone)
        val tvDeadzoneFeedback = findViewById<TextView>(R.id.tv_deadzone_live_feedback)

        val currentDeadzone = SettingsManager.getControllerDeadzonePercent(this)
        // Seekbar range: 0..55 maps to 5%..60%
        seekbarDeadzone.progress = (currentDeadzone - 5).coerceIn(0, 55)
        tvDeadzonePercent.text = "$currentDeadzone%"

        seekbarDeadzone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualPercent = progress + 5
                tvDeadzonePercent.text = "$actualPercent%"
                if (fromUser) {
                    SettingsManager.setControllerDeadzonePercent(this@ControllerSettingsActivity, actualPercent)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val actualPercent = (seekBar?.progress ?: 20) + 5
                SettingsManager.setControllerDeadzonePercent(this@ControllerSettingsActivity, actualPercent)
                HapticManager.vibrateClick(this@ControllerSettingsActivity)
            }
        })
        setupFocusEffect(seekbarDeadzone)

        // 7. Navigation Buttons
        findViewById<Button>(R.id.btn_back_header)?.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            finish()
        }
        findViewById<Button>(R.id.btn_done)?.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(this)
            finish()
        }
        findViewById<Button>(R.id.btn_done)?.let { setupFocusEffect(it) }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) {
            val axisX = event.getAxisValue(MotionEvent.AXIS_X)
            val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
            val (filteredX, filteredY) = SettingsManager.applyDeadzone(this, axisX, axisY)
            val tvFeedback = findViewById<TextView>(R.id.tv_deadzone_live_feedback)
            if (tvFeedback != null) {
                val activeStr = if (filteredX != 0f || filteredY != 0f) {
                    val dir = when {
                        filteredY < -0.3f -> "UP ⬆️"
                        filteredY > 0.3f -> "DOWN ⬇️"
                        filteredX < -0.3f -> "LEFT ⬅️"
                        filteredX > 0.3f -> "RIGHT ➡️"
                        else -> "ACTIVE"
                    }
                    "🎮 Stick: Active ($dir) Raw: (%.2f, %.2f) Filtered: (%.2f, %.2f)".format(axisX, axisY, filteredX, filteredY)
                } else {
                    "🕹️ Stick: Inside Deadzone Neutral Raw: (%.2f, %.2f)".format(axisX, axisY)
                }
                tvFeedback.text = activeStr
            }
            return true
        }
        return super.onGenericMotionEvent(event)
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
