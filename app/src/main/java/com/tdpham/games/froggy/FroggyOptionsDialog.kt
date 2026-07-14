package com.tdpham.games.froggy

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object FroggyOptionsDialog {
    private const val PREFS_NAME = "froggy_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.froggy_cross_settings_title))
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.froggy_cross_difficulty_1
                        2 -> R.string.froggy_cross_difficulty_3
                        else -> R.string.froggy_cross_difficulty_2
                    })
                },
                descProvider = {
                    context.getString(R.string.froggy_cross_difficulty_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .addOption(
                label = "Frog Selection",
                valueProvider = {
                    val frogType = prefs.getInt("selected_frog_type", 0)
                    when (frogType) {
                        1 -> "Golden Dart Frog"
                        2 -> "Heavy Bullfrog"
                        else -> "Classic Tree Frog"
                    }
                },
                descProvider = {
                    val frogType = prefs.getInt("selected_frog_type", 0)
                    when (frogType) {
                        1 -> "1 Life. Beautiful gold & blue pattern."
                        2 -> "5 Lives. Deep purple bullfrog."
                        else -> "3 Lives. Classic vibrant green."
                    }
                },
                onClick = {
                    val frogType = prefs.getInt("selected_frog_type", 0)
                    val nextType = (frogType + 1) % 3
                    prefs.edit { putInt("selected_frog_type", nextType) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
