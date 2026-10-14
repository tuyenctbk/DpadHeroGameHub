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
 * Real-time GamePad & Bluetooth Controller Monitoring System.
 * Detects connected physical controllers/gamepads versus Android TV remotes,
 * and dynamically updates the header UI status indicator.
 */
class GamePadMonitor(private val context: Context) {

    enum class InputMode {
        GAMEPAD,
        REMOTE
    }

    data class ControllerInfo(
        val mode: InputMode,
        val deviceNames: List<String>,
        val primaryDeviceName: String
    )

    fun interface OnModeChangeListener {
        fun onModeChanged(mode: InputMode, primaryDeviceName: String)
    }

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val listeners = mutableListOf<OnModeChangeListener>()

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

    fun isGamePadConnected(): Boolean {
        return getConnectedGamepads().isNotEmpty()
    }

    fun getCurrentMode(): InputMode {
        return if (isGamePadConnected()) InputMode.GAMEPAD else InputMode.REMOTE
    }

    fun getControllerInfo(): ControllerInfo {
        val gamepads = getConnectedGamepads()
        return if (gamepads.isNotEmpty()) {
            val names = gamepads.map { it.name }
            ControllerInfo(
                mode = InputMode.GAMEPAD,
                deviceNames = names,
                primaryDeviceName = names.firstOrNull() ?: "Bluetooth GamePad"
            )
        } else {
            ControllerInfo(
                mode = InputMode.REMOTE,
                deviceNames = emptyList(),
                primaryDeviceName = "D-Pad Remote"
            )
        }
    }

    fun addListener(listener: OnModeChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: OnModeChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Binds real-time controller monitoring to an Activity lifecycle.
     */
    fun startMonitoring(
        lifecycleOwner: LifecycleOwner,
        onModeChanged: (mode: InputMode, deviceName: String) -> Unit
    ) {
        val listener = OnModeChangeListener { mode, name ->
            onModeChanged(mode, name)
        }
        addListener(listener)

        val inputDeviceListener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                notifyChange()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                notifyChange()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                notifyChange()
            }

            private fun notifyChange() {
                val info = getControllerInfo()
                Handler(Looper.getMainLooper()).post {
                    listener.onModeChanged(info.mode, info.primaryDeviceName)
                }
            }
        }

        inputManager?.registerInputDeviceListener(inputDeviceListener, Handler(Looper.getMainLooper()))

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val info = getControllerInfo()
                onModeChanged(info.mode, info.primaryDeviceName)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                removeListener(listener)
                inputManager?.unregisterInputDeviceListener(inputDeviceListener)
            }
        })
    }

    companion object {
        private var instance: GamePadMonitor? = null

        fun getInstance(context: Context): GamePadMonitor {
            return instance ?: synchronized(this) {
                instance ?: GamePadMonitor(context.applicationContext).also { instance = it }
            }
        }

        fun isConnected(context: Context): Boolean {
            return getInstance(context).isGamePadConnected()
        }

        /**
         * Binds an indicator layout in any Activity header with real-time GamePad / Remote status updates.
         */
        fun bindHeaderIndicator(
            activity: AppCompatActivity,
            container: View,
            iconView: ImageView,
            textView: TextView,
            statusDot: View? = null,
            onCustomClick: (() -> Unit)? = null
        ) {
            val monitor = getInstance(activity)

            fun updateUi(mode: InputMode, primaryName: String) {
                if (mode == InputMode.GAMEPAD) {
                    iconView.setImageResource(R.drawable.ic_gamepad_connected)
                    iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                    textView.text = activity.getString(R.string.gamepad_connected)
                    textView.setTextColor(Color.parseColor("#00E5FF"))
                    statusDot?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                    container.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2200E5FF"))
                } else {
                    iconView.setImageResource(R.drawable.ic_remote_mode)
                    iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                    textView.text = activity.getString(R.string.remote_mode)
                    textView.setTextColor(Color.parseColor("#94A3B8"))
                    statusDot?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
                    container.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#15FFFFFF"))
                }
            }

            container.isFocusable = true
            container.isFocusableInTouchMode = true
            container.setOnClickListener {
                SoundManager.playClick()
                IdleAdManager.notifyInteraction()
                if (onCustomClick != null) {
                    onCustomClick()
                } else {
                    val intent = Intent(activity, ControllerSettingsActivity::class.java)
                    activity.startActivity(intent)
                }
            }

            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    SoundManager.playDpadMove()
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            // Initial UI state
            val initial = monitor.getControllerInfo()
            updateUi(initial.mode, initial.primaryDeviceName)

            // Start monitoring across activity lifecycle
            monitor.startMonitoring(activity) { mode, name ->
                updateUi(mode, name)
            }
        }
    }
}
