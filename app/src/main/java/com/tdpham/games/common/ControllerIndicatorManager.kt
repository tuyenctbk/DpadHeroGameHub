package com.tdpham.games.common

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tdpham.games.R
import com.tdpham.games.hub.ControllerSettingsActivity

/**
 * Unified Controller & GamePad Detection & Visual Indicator System.
 * Detects whether a physical/Bluetooth GamePad or TV Remote is active,
 * listens in real-time to connect/disconnect events, and updates the header badge.
 */
object ControllerIndicatorManager {

    enum class ControllerMode {
        GAMEPAD,
        REMOTE
    }

    data class ControllerStatus(
        val mode: ControllerMode,
        val deviceNames: List<String>,
        val primaryDeviceName: String
    )

    fun getControllerStatus(context: Context): ControllerStatus {
        val gamepads = getConnectedGamepads()
        return if (gamepads.isNotEmpty()) {
            val names = gamepads.map { it.name }
            ControllerStatus(
                mode = ControllerMode.GAMEPAD,
                deviceNames = names,
                primaryDeviceName = names.firstOrNull() ?: "Bluetooth GamePad"
            )
        } else {
            ControllerStatus(
                mode = ControllerMode.REMOTE,
                deviceNames = emptyList(),
                primaryDeviceName = "D-Pad Remote"
            )
        }
    }

    fun isGamepadConnected(): Boolean {
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.isVirtual) continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepad || isJoystick) {
                return true
            }
        }
        return false
    }

    fun getConnectedGamepads(): List<InputDevice> {
        val list = mutableListOf<InputDevice>()
        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.isVirtual) continue
            val sources = device.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (isGamepad || isJoystick) {
                list.add(device)
            }
        }
        return list
    }

    /**
     * Binds an indicator layout in any Activity header with lifecycle-aware real-time updates.
     */
    fun setupHeaderIndicator(
        activity: AppCompatActivity,
        container: View,
        iconView: ImageView,
        textView: TextView,
        statusDot: View? = null,
        onCustomClick: (() -> Unit)? = null
    ) {
        val inputManager = activity.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val mainHandler = Handler(Looper.getMainLooper())

        fun updateUI() {
            val status = getControllerStatus(activity)
            if (status.mode == ControllerMode.GAMEPAD) {
                iconView.setImageResource(R.drawable.ic_gamepad_connected)
                iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                textView.text = activity.getString(R.string.gamepad_connected)
                textView.setTextColor(Color.parseColor("#00E5FF"))
                statusDot?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                container.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1F00E5FF"))
            } else {
                iconView.setImageResource(R.drawable.ic_remote_mode)
                iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#90A4AE"))
                textView.text = activity.getString(R.string.remote_mode)
                textView.setTextColor(Color.parseColor("#B0BEC5"))
                statusDot?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#78909C"))
                container.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1AFFFFFF"))
            }
        }

        // Initial update
        updateUI()

        // Focus & Click effects for Android TV D-pad / GamePad navigation
        container.isFocusable = true
        container.isClickable = true
        container.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(activity)
            if (onCustomClick != null) {
                onCustomClick()
            } else {
                activity.startActivity(Intent(activity, ControllerSettingsActivity::class.java))
            }
        }

        container.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                HapticManager.vibrateClick(activity)
                view.animate().scaleX(1.08f).scaleY(1.08f).translationZ(8f).setDuration(200).start()
                view.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3300E5FF"))
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(200).start()
                val status = getControllerStatus(activity)
                val bgColor = if (status.mode == ControllerMode.GAMEPAD) "#1F00E5FF" else "#1AFFFFFF"
                view.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColor))
            }
        }

        // Real-time InputDeviceListener
        val deviceListener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                mainHandler.post { updateUI() }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                mainHandler.post { updateUI() }
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                mainHandler.post { updateUI() }
            }
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                inputManager?.registerInputDeviceListener(deviceListener, mainHandler)
                updateUI()
            }

            override fun onPause(owner: LifecycleOwner) {
                inputManager?.unregisterInputDeviceListener(deviceListener)
            }
        })
    }
}
