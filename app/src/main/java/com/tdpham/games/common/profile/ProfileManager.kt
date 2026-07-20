package com.tdpham.games.common.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {
    private const val PREFS_NAME = "profile_settings"
    private const val KEY_PROFILES = "profiles_list"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_LAST_PLAYER_ID = "last_player_id"

    fun getProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        
        val profiles = mutableListOf<UserProfile>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                profiles.add(UserProfile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    avatarColor = obj.getInt("avatarColor"),
                    avatarId = if (obj.has("avatarId")) obj.getInt("avatarId") else 0,
                    createdAt = obj.getLong("createdAt"),
                    pin = if (obj.has("pin")) obj.getString("pin") else null
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return profiles
    }

    fun saveProfile(context: Context, profile: UserProfile) {
        val profiles = getProfiles(context).toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveAllProfiles(context, profiles)
    }

    private fun saveAllProfiles(context: Context, profiles: List<UserProfile>) {
        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("avatarColor", p.avatarColor)
            obj.put("avatarId", p.avatarId)
            obj.put("createdAt", p.createdAt)
            obj.put("pin", p.pin)
            jsonArray.put(obj)
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROFILES, jsonArray.toString()).apply()
    }

    fun deleteProfile(context: Context, profileId: String) {
        val profiles = getProfiles(context).filter { it.id != profileId }
        saveAllProfiles(context, profiles)
        
        if (getActiveProfileId(context) == profileId) {
            setActiveProfileId(context, null)
        }
        if (getLastPlayerId(context) == profileId) {
            setLastPlayerId(context, null)
        }
    }

    fun getActiveProfileId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun setActiveProfileId(context: Context, profileId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
        if (profileId != null) {
            setLastPlayerId(context, profileId)
        }
    }

    fun getLastPlayerId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_PLAYER_ID, null)
    }

    fun setLastPlayerId(context: Context, profileId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_PLAYER_ID, profileId).apply()
    }

    fun getActiveProfile(context: Context): UserProfile? {
        val activeId = getActiveProfileId(context) ?: return null
        return getProfiles(context).find { it.id == activeId }
    }
    
    fun verifyPin(profile: UserProfile, pin: String): Boolean {
        return profile.pin == null || profile.pin == pin
    }
}
