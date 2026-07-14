package com.tdpham.games.syobon

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.common.BaseOptionsDialog

object SyobonOptionsDialog {
    private const val PREFS_NAME = "syobon_settings"
    const val KEY_LIVES_TYPE = "lives_type"
    const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle("CAT MEOWIO SETTINGS")
            .addOption(
                label = "Starting Lives",
                valueProvider = {
                    when (prefs.getInt(KEY_LIVES_TYPE, 0)) {
                        1 -> "Hardcore (1 Life)"
                        2 -> "Syobon Trolled (-99 Lives)"
                        else -> "Standard (3 Lives)"
                    }
                },
                descProvider = {
                    "Choose starting lives. Warning: -99 Lives starts you with negative lives!"
                },
                onClick = {
                    val current = prefs.getInt(KEY_LIVES_TYPE, 0)
                    prefs.edit { putInt(KEY_LIVES_TYPE, (current + 1) % 3) }
                }
            )
            .addOption(
                label = "Difficulty Mode",
                valueProvider = {
                    when (prefs.getInt(KEY_DIFFICULTY, 1)) {
                        0 -> "Calm (Less Traps)"
                        2 -> "Chaotic (Maximum Traps!)"
                        else -> "Brisk (Normal)"
                    }
                },
                descProvider = {
                    "Calm mode disables some hidden traps; Chaotic increases trap triggers."
                },
                onClick = {
                    val current = prefs.getInt(KEY_DIFFICULTY, 1)
                    prefs.edit { putInt(KEY_DIFFICULTY, (current + 1) % 3) }
                }
            )
            .addOption(
                label = "Cat Selection",
                valueProvider = {
                    when (prefs.getInt("selected_cat_type", 0)) {
                        1 -> "Golden Neko"
                        2 -> "Shadow Nya"
                        else -> "Classic Syobon"
                    }
                },
                descProvider = {
                    when (prefs.getInt("selected_cat_type", 0)) {
                        1 -> "A sparkly, auspicious golden cat."
                        2 -> "A sleek, mysterious shadow cat."
                        else -> "The classic, iconic white Syobon cat."
                    }
                },
                onClick = {
                    val current = prefs.getInt("selected_cat_type", 0)
                    prefs.edit { putInt("selected_cat_type", (current + 1) % 3) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
