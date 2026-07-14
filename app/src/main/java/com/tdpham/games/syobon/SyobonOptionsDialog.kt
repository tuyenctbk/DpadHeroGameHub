package com.tdpham.games.syobon

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.common.BaseOptionsDialog
import com.tdpham.games.R

object SyobonOptionsDialog {
    private const val PREFS_NAME = "cat_meowio_settings"
    const val KEY_LIVES_TYPE = "lives_type"
    const val KEY_DIFFICULTY = "difficulty_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.syobon_settings_title))
            .addOption(
                label = context.getString(R.string.syobon_lives_label),
                valueProvider = {
                    when (prefs.getInt(KEY_LIVES_TYPE, 0)) {
                        1 -> context.getString(R.string.syobon_lives_1)
                        2 -> context.getString(R.string.syobon_lives_inf)
                        else -> context.getString(R.string.syobon_lives_3)
                    }
                },
                descProvider = {
                    context.getString(R.string.syobon_lives_desc)
                },
                onClick = {
                    val current = prefs.getInt(KEY_LIVES_TYPE, 0)
                    prefs.edit { putInt(KEY_LIVES_TYPE, (current + 1) % 3) }
                }
            )
            .addOption(
                label = context.getString(R.string.mode_label),
                valueProvider = {
                    when (prefs.getInt(KEY_DIFFICULTY, 1)) {
                        0 -> context.getString(R.string.simon_says_speed_1) // Reusing Calm/Slow
                        2 -> context.getString(R.string.simon_says_speed_3) // Reusing Chaotic/Fast
                        else -> context.getString(R.string.simon_says_speed_2) // Reusing Brisk/Normal
                    }
                },
                descProvider = {
                    context.getString(R.string.syobon_difficulty_desc)
                },
                onClick = {
                    val current = prefs.getInt(KEY_DIFFICULTY, 1)
                    prefs.edit { putInt(KEY_DIFFICULTY, (current + 1) % 3) }
                }
            )
            .addOption(
                label = context.getString(R.string.syobon_character_label),
                valueProvider = {
                    when (prefs.getInt("selected_cat_type", 0)) {
                        1 -> context.getString(R.string.syobon_char_gold)
                        2 -> context.getString(R.string.syobon_char_shadow)
                        else -> context.getString(R.string.syobon_char_classic)
                    }
                },
                descProvider = {
                    val index = prefs.getInt("selected_cat_type", 0)
                    // Simplified desc provider for characters
                    context.getString(R.string.syobon_character_label) + " " + (index + 1)
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
