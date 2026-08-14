package com.tdpham.games.monkey

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object MonkeyOptionsDialog {
    private const val PREFS_NAME = "monkey_settings"
    private const val KEY_CHARACTER = "selected_character"
    private const val KEY_SEASON = "selected_season"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.monkey_settings_title))
            .addOption(
                label = context.getString(R.string.monkey_character_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CHARACTER, 0).coerceIn(0, 3)
                    when (index) {
                        1 -> context.getString(R.string.monkey_char_climber)
                        2 -> context.getString(R.string.monkey_char_spider)
                        3 -> context.getString(R.string.monkey_char_chimp)
                        else -> context.getString(R.string.monkey_char_gibbon)
                    }
                },
                descProvider = {
                    context.getString(R.string.monkey_character_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_CHARACTER, 0)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_CHARACTER, nextIndex) }
                }
            )
            .addOption(
                label = context.getString(R.string.monkey_season_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_SEASON, 0).coerceIn(0, 3)
                    when (index) {
                        1 -> context.getString(R.string.monkey_summer)
                        2 -> context.getString(R.string.monkey_autumn)
                        3 -> context.getString(R.string.monkey_winter)
                        else -> context.getString(R.string.monkey_spring)
                    }
                },
                descProvider = {
                    context.getString(R.string.monkey_season_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_SEASON, 0)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_SEASON, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
