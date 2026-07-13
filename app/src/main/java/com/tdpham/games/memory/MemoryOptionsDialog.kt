package com.tdpham.games.memory

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object MemoryOptionsDialog {
    private const val PREFS_NAME = "memory_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.memory_settings_title))
            .addOption(
                label = context.getString(R.string.snake_level_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.memory_level_1
                        2 -> R.string.memory_level_3
                        3 -> R.string.memory_level_4
                        else -> R.string.memory_level_2
                    })
                },
                descProvider = {
                    context.getString(R.string.game_memory_instructions).split("\n").first()
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
