package com.tdpham.games.tictactoe

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object TicTacToeOptionsDialog {
    private const val PREFS_NAME = "tictactoe_settings"
    private const val KEY_BOARD_SIZE = "board_size"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.tictactoe_settings_title))
            .addOption(
                label = context.getString(R.string.tictactoe_size_label),
                valueProvider = {
                    val size = prefs.getInt(KEY_BOARD_SIZE, 3)
                    context.getString(when(size) {
                        4 -> R.string.tictactoe_size_4
                        5 -> R.string.tictactoe_size_5
                        else -> R.string.tictactoe_size_3
                    })
                },
                descProvider = {
                    val size = prefs.getInt(KEY_BOARD_SIZE, 3)
                    context.getString(when(size) {
                        4 -> R.string.tictactoe_size_4_desc
                        5 -> R.string.tictactoe_size_5_desc
                        else -> R.string.tictactoe_size_3_desc
                    })
                },
                onClick = {
                    val size = prefs.getInt(KEY_BOARD_SIZE, 3)
                    val nextSize = when(size) {
                        3 -> 4
                        4 -> 5
                        else -> 3
                    }
                    prefs.edit { putInt(KEY_BOARD_SIZE, nextSize) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
