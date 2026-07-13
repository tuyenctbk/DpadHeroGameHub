package com.tdpham.games.simon

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object SimonOptionsDialog {
    private const val PREFS_NAME = "simon_settings"
    private const val KEY_SPEED = "speed_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.simon_says_settings_title))
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_SPEED, 1)
                    context.getString(when(index) {
                        0 -> R.string.simon_says_speed_1
                        2 -> R.string.simon_says_speed_3
                        else -> R.string.simon_says_speed_2
                    })
                },
                descProvider = {
                    context.getString(R.string.simon_says_speed_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_SPEED, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_SPEED, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
