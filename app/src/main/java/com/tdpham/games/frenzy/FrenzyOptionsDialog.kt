package com.tdpham.games.frenzy

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object FrenzyOptionsDialog {
    private const val PREFS_NAME = "frenzy_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"
    private const val KEY_STARTING_SIZE = "starting_size"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.frenzy_settings_title))
            .addOption(
                label = context.getString(R.string.frenzy_difficulty_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1).coerceIn(0, 2)
                    when (index) {
                        0 -> context.getString(R.string.frenzy_difficulty_easy)
                        2 -> context.getString(R.string.frenzy_difficulty_hard)
                        else -> context.getString(R.string.frenzy_difficulty_normal)
                    }
                },
                descProvider = {
                    context.getString(R.string.frenzy_difficulty_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .addOption(
                label = context.getString(R.string.frenzy_starting_size_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_STARTING_SIZE, 1).coerceIn(1, 2)
                    if (index == 2) context.getString(R.string.frenzy_starting_size_angelfish)
                    else context.getString(R.string.frenzy_starting_size_guppy)
                },
                descProvider = {
                    context.getString(R.string.frenzy_starting_size_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_STARTING_SIZE, 1)
                    val nextIndex = if (index == 1) 2 else 1
                    prefs.edit { putInt(KEY_STARTING_SIZE, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
