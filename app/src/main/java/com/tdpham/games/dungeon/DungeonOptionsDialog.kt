package com.tdpham.games.dungeon

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object DungeonOptionsDialog {
    private const val PREFS_NAME = "dungeon_settings"
    private const val KEY_START_LEVEL = "start_level"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.dungeon_escape_settings_title))
            .addOption(
                label = context.getString(R.string.dungeon_escape_start_level),
                valueProvider = {
                    prefs.getInt(KEY_START_LEVEL, 1).toString()
                },
                descProvider = {
                    context.getString(R.string.dungeon_escape_level_desc)
                },
                onClick = {
                    val level = prefs.getInt(KEY_START_LEVEL, 1)
                    val nextLevel = if (level >= 10) 1 else level + 1
                    prefs.edit { putInt(KEY_START_LEVEL, nextLevel) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
