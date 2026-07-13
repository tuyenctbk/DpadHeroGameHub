package com.tdpham.games.lines98

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object Lines98OptionsDialog {
    private const val PREFS_NAME = "lines98_settings"
    private const val KEY_COLOR_COUNT = "color_count"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.lines98_settings_title))
            .addOption(
                label = context.getString(R.string.snake_level_label),
                valueProvider = {
                    val count = prefs.getInt(KEY_COLOR_COUNT, 7)
                    context.getString(when(count) {
                        5 -> R.string.lines98_difficulty_easy
                        9 -> R.string.lines98_difficulty_hard
                        else -> R.string.lines98_difficulty_normal
                    })
                },
                descProvider = {
                    context.getString(R.string.lines98_difficulty_desc)
                },
                onClick = {
                    val count = prefs.getInt(KEY_COLOR_COUNT, 7)
                    val nextCount = when(count) {
                        5 -> 7
                        7 -> 9
                        else -> 5
                    }
                    prefs.edit { putInt(KEY_COLOR_COUNT, nextCount) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
