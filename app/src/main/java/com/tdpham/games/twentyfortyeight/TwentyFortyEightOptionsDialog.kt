package com.tdpham.games.twentyfortyeight

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object TwentyFortyEightOptionsDialog {
    private const val PREFS_NAME = "twentyfortyeight_settings"
    private const val KEY_GRID_SIZE = "grid_size"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.game_4096_settings_title))
            .addOption(
                label = context.getString(R.string.tictactoe_size_label),
                valueProvider = {
                    val size = prefs.getInt(KEY_GRID_SIZE, 4)
                    context.getString(if (size == 5) R.string.game_4096_size_5 else R.string.game_4096_size_4)
                },
                descProvider = {
                    context.getString(R.string.game_4096_size_desc)
                },
                onClick = {
                    val size = prefs.getInt(KEY_GRID_SIZE, 4)
                    val nextSize = if (size == 4) 5 else 4
                    prefs.edit { putInt(KEY_GRID_SIZE, nextSize) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
