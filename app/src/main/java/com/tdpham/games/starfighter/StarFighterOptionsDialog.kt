package com.tdpham.games.starfighter

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object StarFighterOptionsDialog {
    private const val PREFS_NAME = "starfighter_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.starfighter_settings_title))
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.starfighter_difficulty_1
                        2 -> R.string.starfighter_difficulty_3
                        else -> R.string.starfighter_difficulty_2
                    })
                },
                descProvider = {
                    context.getString(R.string.starfighter_difficulty_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
