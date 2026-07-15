package com.tdpham.games.flappy

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object FlappyHeroOptionsDialog {
    private const val PREFS_NAME = "flappy_hero_settings"
    private const val KEY_DIFFICULTY = "difficulty_index"
    private const val KEY_CHARACTER = "character_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.flappy_hero_settings_title))
            .addOption(
                label = context.getString(R.string.snake_level_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.flappy_hero_level_1
                        1 -> R.string.flappy_hero_level_2
                        2 -> R.string.flappy_hero_level_3
                        3 -> R.string.flappy_hero_level_4
                        4 -> R.string.flappy_hero_level_5
                        else -> R.string.flappy_hero_level_2
                    })
                },
                descProvider = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    context.getString(when(index) {
                        0 -> R.string.flappy_hero_level_1_desc
                        1 -> R.string.flappy_hero_level_2_desc
                        2 -> R.string.flappy_hero_level_3_desc
                        3 -> R.string.flappy_hero_level_4_desc
                        4 -> R.string.flappy_hero_level_5_desc
                        else -> R.string.flappy_hero_level_2_desc
                    })
                },
                onClick = {
                    val index = prefs.getInt(KEY_DIFFICULTY, 1)
                    val nextIndex = (index + 1) % 5
                    prefs.edit { putInt(KEY_DIFFICULTY, nextIndex) }
                }
            )
            .addOption(
                label = context.getString(R.string.flappy_hero_char_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CHARACTER, 0)
                    context.getString(when(index) {
                        1 -> R.string.flappy_char_swift
                        2 -> R.string.flappy_char_heavy
                        3 -> R.string.flappy_char_floaty
                        else -> R.string.flappy_char_classic
                    })
                },
                descProvider = {
                    val index = prefs.getInt(KEY_CHARACTER, 0)
                    context.getString(when(index) {
                        1 -> R.string.flappy_char_swift_desc
                        2 -> R.string.flappy_char_heavy_desc
                        3 -> R.string.flappy_char_floaty_desc
                        else -> R.string.flappy_char_classic_desc
                    })
                },
                onClick = {
                    val index = prefs.getInt(KEY_CHARACTER, 0)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_CHARACTER, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
