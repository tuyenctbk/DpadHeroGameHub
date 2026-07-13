package com.tdpham.games.brickbreak

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object BrickBreakOptionsDialog {
    private const val PREFS_NAME = "brick_break_settings"
    private const val KEY_PADDLE_SIZE = "paddle_size_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.brick_break_settings_title))
            .addOption(
                label = context.getString(R.string.brick_break_paddle_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_PADDLE_SIZE, 1)
                    context.getString(when(index) {
                        0 -> R.string.brick_break_paddle_large
                        2 -> R.string.brick_break_paddle_small
                        else -> R.string.brick_break_paddle_medium
                    })
                },
                descProvider = {
                    context.getString(R.string.brick_break_paddle_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_PADDLE_SIZE, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_PADDLE_SIZE, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
