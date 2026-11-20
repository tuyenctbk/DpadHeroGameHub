package com.tdpham.games.common

import android.app.Activity
import android.app.Dialog
import android.view.KeyEvent
import android.view.View

/**
 * Universal helper for Android TV D-pad and Gamepad Controller navigation.
 * Ensures consistent behavior across TV remotes and Bluetooth/USB gamepads:
 * - BUTTON_A / DPAD_CENTER / ENTER: Click focused element
 * - BUTTON_B / BACK / ESCAPE: Back / Exit / Dismiss
 * - BUTTON_START / BUTTON_SELECT / MENU: Menu / Pause
 * - DPAD navigation between interactive elements
 */
object ControllerNavigationHelper {

    fun isConfirmKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A
    }

    fun isBackKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_BUTTON_B
    }

    fun isMenuKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_SETTINGS ||
                keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
    }

    fun isDpadKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    }

    /**
     * Intercepts and routes UI key events for standard Activities and menu screens.
     * Guarantees Controller Button A clicks focused views, and Controller Button B triggers Back.
     */
    fun handleUiKeyEvent(
        activity: Activity,
        event: KeyEvent,
        onBackAction: (() -> Boolean)? = null
    ): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN

        if (isDown) {
            IdleAdManager.notifyInteraction()

            if (isBackKey(keyCode)) {
                if (onBackAction != null) {
                    return onBackAction()
                }
                if (activity is androidx.activity.ComponentActivity) {
                    activity.onBackPressedDispatcher.onBackPressed()
                } else {
                    @Suppress("DEPRECATION")
                    activity.onBackPressed()
                }
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                val focused = activity.currentFocus
                if (focused != null && focused.isClickable) {
                    focused.performClick()
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isBackKey(keyCode)) {
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                val focused = activity.currentFocus
                if (focused != null && focused.isClickable) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Intercepts and routes UI key events for Dialog instances.
     */
    fun handleDialogKeyEvent(
        dialog: Dialog,
        event: KeyEvent,
        onBackAction: (() -> Boolean)? = null
    ): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN

        if (isDown) {
            IdleAdManager.notifyInteraction()

            if (isBackKey(keyCode)) {
                if (onBackAction != null) {
                    return onBackAction()
                }
                dialog.dismiss()
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                val focused = dialog.currentFocus
                if (focused != null && focused.isClickable) {
                    focused.performClick()
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isBackKey(keyCode)) {
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                val focused = dialog.currentFocus
                if (focused != null && focused.isClickable) {
                    return true
                }
            }
        }
        return false
    }
}

