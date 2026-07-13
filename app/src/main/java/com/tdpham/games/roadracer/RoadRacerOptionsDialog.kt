package com.tdpham.games.roadracer

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object RoadRacerOptionsDialog {
    private const val PREFS_NAME = "roadracer_settings"
    private const val KEY_DENSITY = "traffic_density_index"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.road_racer_settings_title))
            .addOption(
                label = context.getString(R.string.road_racer_density_label),
                valueProvider = {
                    val index = prefs.getInt(KEY_DENSITY, 1)
                    context.getString(when(index) {
                        0 -> R.string.road_racer_density_low
                        2 -> R.string.road_racer_density_high
                        else -> R.string.road_racer_density_normal
                    })
                },
                descProvider = {
                    context.getString(R.string.road_racer_density_desc)
                },
                onClick = {
                    val index = prefs.getInt(KEY_DENSITY, 1)
                    val nextIndex = (index + 1) % 3
                    prefs.edit { putInt(KEY_DENSITY, nextIndex) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
