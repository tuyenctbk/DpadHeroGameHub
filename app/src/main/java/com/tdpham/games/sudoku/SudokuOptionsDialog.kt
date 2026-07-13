package com.tdpham.games.sudoku

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object SudokuOptionsDialog {
    private const val PREFS_NAME = "sudoku_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.sudoku_settings_title))
            .addOption(
                label = context.getString(R.string.snake_level_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.sudoku_level_1
                        2 -> R.string.sudoku_level_3
                        else -> R.string.sudoku_level_2
                    })
                },
                descProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.sudoku_level_1_desc
                        2 -> R.string.sudoku_level_3_desc
                        else -> R.string.sudoku_level_2_desc
                    })
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
