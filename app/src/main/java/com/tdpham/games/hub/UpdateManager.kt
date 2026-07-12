package com.tdpham.games.hub

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import com.tdpham.games.R
import com.tdpham.games.common.ConfigManager

object UpdateManager {
    private const val PREFS_NAME = "update_prefs"
    private const val LAST_CHECK_TIME = "last_check_time"
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours

    fun checkForUpdates(context: Context, onComplete: (Boolean) -> Unit) {
        val latestVersionCode = ConfigManager.getLatestVersionCode()
        val minVersionCode = ConfigManager.getMinVersionCode()
        val currentVersionCode = getCurrentVersionCode(context)

        val isForceUpdate = currentVersionCode < minVersionCode
        val isUpdateAvailable = latestVersionCode > currentVersionCode

        // If it's a force update, we show it regardless of the last check time
        if (isForceUpdate) {
            showUpdateDialog(context, true) {
                onComplete(true)
            }
            return
        }

        // For regular updates, check if 24 hours have passed since the last check
        if (isUpdateAvailable && shouldCheckForUpdate(context)) {
            showUpdateDialog(context, false) {
                onComplete(true)
            }
            updateLastCheckTime(context)
        } else {
            onComplete(false)
        }
    }

    private fun shouldCheckForUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(LAST_CHECK_TIME, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL
    }

    private fun updateLastCheckTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    private fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Throwable) {
            Log.e("UpdateManager", "Failed to get version code: ${e.message}")
            0L
        }
    }

    private fun showUpdateDialog(context: Context, isForceUpdate: Boolean, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_update)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        if (isForceUpdate) {
            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                    (context as? androidx.appcompat.app.AppCompatActivity)?.finish()
                    true
                } else false
            }
        }

        val btnUpdate = dialog.findViewById<Button>(R.id.btn_update_now)
        val btnLater = dialog.findViewById<Button>(R.id.btn_update_later)

        if (isForceUpdate) {
            btnLater.visibility = View.GONE
        }

        btnUpdate.setOnClickListener {
            val packageName = context.packageName
            val uri = Uri.parse("market://details?id=$packageName")
            val goToMarket = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            try {
                context.startActivity(goToMarket)
            } catch (e: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                } catch (ex: Exception) {
                    Log.e("UpdateManager", "Could not launch Play Store link", ex)
                }
            }
            
            if (!isForceUpdate) {
                dialog.dismiss()
                onDismiss()
            }
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }

        setupFocusEffect(btnUpdate)
        setupFocusEffect(btnLater)

        try {
            dialog.show()
            btnUpdate.requestFocus()
        } catch (t: Throwable) {
            Log.e("UpdateManager", "Failed to show update dialog: ${t.message}")
            onDismiss()
        }
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
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
