package com.tdpham.games.minesweeper

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object MinesweeperOptionsDialog {
    private const val PREFS_NAME = "minesweeper_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.minesweeper_settings_title))
            .addOption(
                label = context.getString(R.string.snake_level_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 0)
                    context.getString(when(index) {
                        0 -> R.string.minesweeper_level_1
                        1 -> R.string.minesweeper_level_2
                        else -> R.string.minesweeper_level_3
                    })
                },
                descProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 0)
                    context.getString(when(index) {
                        0 -> R.string.minesweeper_instructions
                        1 -> R.string.minesweeper_instructions
                        else -> R.string.minesweeper_instructions
                    }).split("\n").first() // Just a hint
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 0)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
