package com.tdpham.games.trex

import android.content.Context
import androidx.core.content.edit
import com.tdpham.games.R
import com.tdpham.games.common.BaseOptionsDialog

object TRexOptionsDialog {
    private const val PREFS_NAME = "trex_settings"

    fun show(context: Context, onDismiss: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val characters = arrayOf("DADDY", "NINJA", "ASTRONAUT", "BABY", "GRANDPA", "SCIENTIST", "PIRATE", "MUMMY", "TEENAGER", "CHEF", "ATHLETE", "DRAGON", "ZOMBIE", "ROBOT", "KING", "RANDOM")

        fun getCharDisplayName(key: String): String {
            if (key == "RANDOM") return context.getString(R.string.trex_random)
            val resId = when(key) {
                "DADDY" -> R.string.trex_daddy
                "NINJA" -> R.string.trex_ninja
                "ASTRONAUT" -> R.string.trex_astronaut
                "BABY" -> R.string.trex_baby
                "GRANDPA" -> R.string.trex_grandpa
                "SCIENTIST" -> R.string.trex_scientist
                "PIRATE" -> R.string.trex_pirate
                "MUMMY" -> R.string.trex_mummy
                "TEENAGER" -> R.string.trex_teenager
                "CHEF" -> R.string.trex_chef
                "ATHLETE" -> R.string.trex_athlete
                "DRAGON" -> R.string.trex_dragon
                "ZOMBIE" -> R.string.trex_zombie
                "ROBOT" -> R.string.trex_robot
                "KING" -> R.string.trex_king
                else -> return key
            }
            return context.getString(resId).uppercase()
        }

        BaseOptionsDialog(context)
            .setTitle(context.getString(R.string.game_trex).uppercase() + " " + context.getString(R.string.settings).uppercase())
            .addOption(
                label = context.getString(R.string.trex_character_label),
                valueProvider = {
                    val mode = prefs.getString("trex_char_mode", "specific")
                    if (mode == "random") {
                        context.getString(R.string.trex_random)
                    } else {
                        val index = prefs.getInt("selected_char_index", 0)
                        val key = if (index >= 0 && index < characters.size - 1) characters[index] else "DADDY"
                        getCharDisplayName(key)
                    }
                },
                descProvider = {
                    val mode = prefs.getString("trex_char_mode", "specific")
                    context.getString(if (mode == "random") R.string.trex_desc_char_random else R.string.trex_desc_char_specific)
                },
                onClick = {
                    val mode = prefs.getString("trex_char_mode", "specific")
                    if (mode == "random") {
                        prefs.edit { putString("trex_char_mode", "specific").putInt("selected_char_index", 0) }
                    } else {
                        val index = prefs.getInt("selected_char_index", 0)
                        if (index < characters.size - 2) {
                            prefs.edit { putInt("selected_char_index", index + 1) }
                        } else {
                            prefs.edit { putString("trex_char_mode", "random") }
                        }
                    }
                }
            )
            .addOption(
                label = context.getString(R.string.trex_time_of_day),
                valueProvider = {
                    val mode = prefs.getString("trex_time_mode", "random")
                    val resId = when(mode) {
                        "day" -> R.string.trex_day
                        "night" -> R.string.trex_night
                        else -> R.string.trex_random
                    }
                    context.getString(resId).uppercase()
                },
                descProvider = {
                    val mode = prefs.getString("trex_time_mode", "random")
                    val resId = when(mode) {
                        "day" -> R.string.trex_desc_time_day
                        "night" -> R.string.trex_desc_time_night
                        else -> R.string.trex_desc_time_random
                    }
                    context.getString(resId)
                },
                onClick = {
                    val modes = arrayOf("random", "day", "night")
                    val current = prefs.getString("trex_time_mode", "random")
                    val next = modes[(modes.indexOf(current) + 1) % modes.size]
                    prefs.edit { putString("trex_time_mode", next) }
                }
            )
            .addOption(
                label = context.getString(R.string.trex_season),
                valueProvider = {
                    val mode = prefs.getString("trex_season_mode", "random")
                    val resId = when(mode) {
                        "spring" -> R.string.trex_spring
                        "summer" -> R.string.trex_summer
                        "autumn" -> R.string.trex_autumn
                        "winter" -> R.string.trex_winter
                        else -> R.string.trex_random
                    }
                    context.getString(resId).uppercase()
                },
                descProvider = {
                    val mode = prefs.getString("trex_season_mode", "random")
                    val resId = when(mode) {
                        "spring" -> R.string.trex_desc_season_spring
                        "summer" -> R.string.trex_desc_season_summer
                        "autumn" -> R.string.trex_desc_season_autumn
                        "winter" -> R.string.trex_desc_season_winter
                        else -> R.string.trex_desc_season_random
                    }
                    context.getString(resId)
                },
                onClick = {
                    val modes = arrayOf("random", "spring", "summer", "autumn", "winter")
                    val current = prefs.getString("trex_season_mode", "random")
                    val next = modes[(modes.indexOf(current) + 1) % modes.size]
                    prefs.edit { putString("trex_season_mode", next) }
                }
            )
            .addOption(
                label = context.getString(R.string.trex_weather),
                valueProvider = {
                    val mode = prefs.getString("trex_weather_mode", "random")
                    val resId = when(mode) {
                        "sunny" -> R.string.trex_sunny
                        "rainy" -> R.string.trex_rainy
                        "snowy" -> R.string.trex_snowy
                        else -> R.string.trex_random
                    }
                    context.getString(resId).uppercase()
                },
                descProvider = {
                    val mode = prefs.getString("trex_weather_mode", "random")
                    val resId = when(mode) {
                        "sunny" -> R.string.trex_desc_weather_sunny
                        "rainy" -> R.string.trex_desc_weather_rainy
                        "snowy" -> R.string.trex_desc_weather_snowy
                        else -> R.string.trex_desc_weather_random
                    }
                    context.getString(resId)
                },
                onClick = {
                    val modes = arrayOf("random", "sunny", "rainy", "snowy")
                    val current = prefs.getString("trex_weather_mode", "random")
                    val next = modes[(modes.indexOf(current) + 1) % modes.size]
                    prefs.edit { putString("trex_weather_mode", next) }
                }
            )
            .setOnDismiss(onDismiss)
            .show()
    }
}
