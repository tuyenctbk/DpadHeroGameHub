package com.tdpham.games.fruit

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object FruitOptionsDialog {
    private const val PREFS_NAME = "fruit_settings"
    private const val KEY_BLADE = "selected_blade"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val bladeNames = arrayOf(
            context.getString(R.string.fruit_blade_steel),
            context.getString(R.string.fruit_blade_flame),
            context.getString(R.string.fruit_blade_cyber),
            context.getString(R.string.fruit_blade_shadow)
        )

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.settings))
            .addOption(
                label = context.getString(R.string.game_fruit),
                valueProvider = {
                    val index = prefs.getInt(KEY_BLADE, 0)
                    bladeNames[index % bladeNames.size]
                },
                descProvider = {
                    context.getString(R.string.hero_customization_hint)
                },
                onClick = {
                    val index = prefs.getInt(KEY_BLADE, 0)
                    val nextIndex = (index + 1) % bladeNames.size
                    prefs.edit { putInt(KEY_BLADE, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
