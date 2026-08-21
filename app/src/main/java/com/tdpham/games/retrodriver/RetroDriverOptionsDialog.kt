package com.tdpham.games.retrodriver

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object RetroDriverOptionsDialog {
    const val PREFS_NAME = "retrodriver_settings"
    const val KEY_CAR_INDEX = "selected_car_index"
    const val KEY_MAP_INDEX = "selected_map_index"
    const val KEY_LAPS = "selected_laps_count"

    private val carNames = arrayOf("Suzuki Red (Balanced)", "Yamaha Yellow (Accel)", "Cyber Cyan (Handling)", "Kawasaki Neon (Top Speed)")
    private val carDescriptions = arrayOf(
        "Top Speed: 150 MPH | Accel: 8/10 | Handling: 8/10 | Balanced",
        "Top Speed: 145 MPH | Accel: 10/10 | Handling: 7/10 | Quick Burst",
        "Top Speed: 155 MPH | Accel: 7/10 | Handling: 10/10 | Sharp Drift",
        "Top Speed: 165 MPH | Accel: 9/10 | Handling: 6/10 | High Velocity"
    )
    private val lapOptions = intArrayOf(1, 2, 3, 5)

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.retrodriver_settings_title))
            .addOption(
                label = context.getString(R.string.retrodriver_vehicle_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_CAR_INDEX, 0).coerceIn(0, carNames.size - 1)
                    carNames[index]
                },
                descProvider = {
                    val index = prefs.getInt(KEY_CAR_INDEX, 0).coerceIn(0, carNames.size - 1)
                    carDescriptions[index]
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
            .addOption(
                label = "Race Length",
                valueProvider = {
                    val laps = prefs.getInt(KEY_LAPS, 3)
                    "$laps Laps"
                },
                descProvider = {
                    val laps = prefs.getInt(KEY_LAPS, 3)
                    when (laps) {
                        1 -> "Sprint race: 1 fast lap to the finish line."
                        2 -> "Short race: 2 laps competition."
                        3 -> "Standard championship: 3 laps."
                        else -> "Endurance challenge: 5 grueling laps."
                    }
                },
                onClick = {
                    val currentLaps = prefs.getInt(KEY_LAPS, 3)
                    val currentIndex = lapOptions.indexOf(currentLaps).let { if (it < 0) 2 else it }
                    val nextIndex = (currentIndex + 1) % lapOptions.size
                    prefs.edit { putInt(KEY_LAPS, lapOptions[nextIndex]) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}

