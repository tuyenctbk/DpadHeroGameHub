package com.tdpham.games.wordquest

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object WordQuestOptionsDialog {
    private const val PREFS_NAME = "wordquest_settings"
    private const val KEY_CATEGORY = "selected_category_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.word_quest_settings_title))
            .addOption(
                label = context.getString(R.string.category_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CATEGORY, 0)
                    context.getString(when(index) {
                        1 -> R.string.word_quest_category_nature
                        2 -> R.string.word_quest_category_tech
                        else -> R.string.word_quest_category_all
                    })
                },
                descProvider = {
                    context.getString(R.string.word_quest_category_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_CATEGORY, 0)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_CATEGORY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
