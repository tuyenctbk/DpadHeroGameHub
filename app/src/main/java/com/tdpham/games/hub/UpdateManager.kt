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
    fun checkForUpdates(context: Context, onComplete: (Boolean) -> Unit) {
        val latestVersionCode = ConfigManager.getLatestVersionCode()
        val currentVersionCode = getCurrentVersionCode(context)
        
        // Don't show update dialog if we're on the initial version (prevents review issues)
        // or if we're already up to date
        if (currentVersionCode > 1 && latestVersionCode > currentVersionCode) {
            showUpdateDialog(context) {
                onComplete(true)
            }
        } else {
            onComplete(false)
        }
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

    private fun showUpdateDialog(context: Context, onDismiss: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_update)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val btnUpdate = dialog.findViewById<Button>(R.id.btn_update_now)
        val btnLater = dialog.findViewById<Button>(R.id.btn_update_later)

        btnUpdate.setOnClickListener {
            dialog.dismiss()
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
            onDismiss()
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
