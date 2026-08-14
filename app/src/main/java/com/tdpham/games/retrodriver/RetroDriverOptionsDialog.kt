package com.tdpham.games.retrodriver

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object RetroDriverOptionsDialog {
    private const val PREFS_NAME = "retrodriver_settings"
    private const val KEY_CAR_INDEX = "selected_car_index"
    private const val KEY_THEME_INDEX = "selected_theme_index"

    private val carNames = arrayOf("Suzuki Red", "Yamaha Yellow", "Cyber Cyan", "Kawasaki Neon")

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.retrodriver_settings_title))
            .addOption(
                label = context.getString(R.string.retrodriver_vehicle_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CAR_INDEX, 0).coerceIn(0, 3)
                    carNames[index]
                },
                descProvider = {
                    context.getString(R.string.retrodriver_vehicle_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_CAR_INDEX, 0)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_CAR_INDEX, nextIndex) }
                }
            )
            .addOption(
                label = context.getString(R.string.retrodriver_theme_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_THEME_INDEX, 0).coerceIn(0, 3)
                    when (index) {
                        1 -> context.getString(R.string.retrodriver_theme_desert)
                        2 -> context.getString(R.string.retrodriver_theme_cyber)
                        3 -> context.getString(R.string.retrodriver_theme_snow)
                        else -> context.getString(R.string.retrodriver_theme_neon)
                    }
                },
                descProvider = {
                    context.getString(R.string.retrodriver_theme_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_THEME_INDEX, 0)
                    val nextIndex = (index + 1) % 4
                    prefs.edit { putInt(KEY_THEME_INDEX, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
