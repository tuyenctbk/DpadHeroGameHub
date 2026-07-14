package com.tdpham.games.tanks

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object BattleTanksOptionsDialog {
    private const val PREFS_NAME = "battle_tanks_settings"
    private const val KEY_START_LEVEL = "start_level"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.battle_tanks_settings_title))
            .addOption(
                label = context.getString(R.string.level_label),
                valueProvider = {
                    val level = prefs.getInt(KEY_START_LEVEL, 1)
                    context.getString(when(level) {
                        1 -> R.string.battle_tanks_difficulty_1
                        2 -> R.string.battle_tanks_difficulty_2
                        else -> R.string.battle_tanks_difficulty_3
                    })
                },
                descProvider = {
                    context.getString(R.string.battle_tanks_difficulty_desc)
                },
                onClick = {
                    val level = prefs.getInt(KEY_START_LEVEL, 1)
                    val nextLevel = if (level >= 3) 1 else level + 1
                    prefs.edit { putInt(KEY_START_LEVEL, nextLevel) }
                }
            )
            .addOption(
                label = "Tank Selection",
                valueProvider = {
                    val tankType = prefs.getInt("selected_tank_type", 0)
                    when (tankType) {
                        1 -> "Firestorm (Fast Fire)"
                        2 -> "Mammoth (Heavy Armor)"
                        else -> "Titan (Balanced)"
                    }
                },
                descProvider = {
                    val tankType = prefs.getInt("selected_tank_type", 0)
                    when (tankType) {
                        1 -> "1 Life, extremely fast reloading."
                        2 -> "5 Lives, slow reloading."
                        else -> "3 Lives, normal reloading."
                    }
                },
                onClick = {
                    val tankType = prefs.getInt("selected_tank_type", 0)
                    val nextType = (tankType + 1) % 3
                    prefs.edit { putInt("selected_tank_type", nextType) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
