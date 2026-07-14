package com.tdpham.games.spinball

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object SpinballOptionsDialog {
    private const val PREFS_NAME = "spinball_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.spinball_settings_title))
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.spinball_difficulty_1
                        2 -> R.string.spinball_difficulty_3
                        else -> R.string.spinball_difficulty_2
                    })
                },
                descProvider = {
                    context.getString(R.string.spinball_difficulty_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .addOption(
                label = "Ball Selection",
                valueProvider = {
                    val ballType = prefs.getInt("selected_ball_type", 0)
                    when (ballType) {
                        1 -> "Fireball"
                        2 -> "Plasma Pulse"
                        else -> "Neon Classic"
                    }
                },
                descProvider = {
                    val ballType = prefs.getInt("selected_ball_type", 0)
                    when (ballType) {
                        1 -> "Leaves a fiery red trail of sparks."
                        2 -> "Pulsates green in size dynamically."
                        else -> "Clean neon-blue energy ball."
                    }
                },
                onClick = {
                    val ballType = prefs.getInt("selected_ball_type", 0)
                    val nextType = (ballType + 1) % 3
                    prefs.edit { putInt("selected_ball_type", nextType) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
