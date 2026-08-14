package com.tdpham.games.retrodriver

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object RetroDriverOptionsDialog {
    const val PREFS_NAME = "retrodriver_settings"
    const val KEY_CAR_INDEX = "selected_car_index"
    const val KEY_MAP_INDEX = "selected_map_index"

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
                    val nextIndex = (index + 1) % carNames.size
                    prefs.edit { putInt(KEY_CAR_INDEX, nextIndex) }
                }
            )
            .addOption(
                label = "Racing Map (1–20)",
                valueProvider = {
                    val mapIndex = prefs.getInt(KEY_MAP_INDEX, 0).coerceIn(0, RetroDriverMapCatalog.maps.size - 1)
                    val map = RetroDriverMapCatalog.getMap(mapIndex)
                    map.name
                },
                descProvider = {
                    val mapIndex = prefs.getInt(KEY_MAP_INDEX, 0).coerceIn(0, RetroDriverMapCatalog.maps.size - 1)
                    val map = RetroDriverMapCatalog.getMap(mapIndex)
                    map.description
                },
                onClick = {
                    val mapIndex = prefs.getInt(KEY_MAP_INDEX, 0)
                    val nextMapIndex = (mapIndex + 1) % RetroDriverMapCatalog.maps.size
                    prefs.edit { putInt(KEY_MAP_INDEX, nextMapIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
