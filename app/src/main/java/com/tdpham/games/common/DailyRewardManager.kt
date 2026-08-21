package com.tdpham.games.common

import android.content.Context
import android.content.SharedPreferences
import com.tdpham.games.common.profile.ProfileManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyRewardManager {

    private const val PREFS_NAME = "daily_rewards_prefs"
    private const val KEY_COIN_BALANCE = "coin_balance"
    private const val KEY_STREAK_DAY = "streak_day"
    private const val KEY_LAST_CLAIM_DATE = "last_claim_date"

    val REWARD_STREAKS = listOf(
        RewardDay(1, 100, "100 COINS", "Day 1 Welcome Bonus"),
        RewardDay(2, 150, "150 COINS", "Day 2 Combo Booster"),
        RewardDay(3, 250, "250 COINS", "Day 3 Arcade Fuel"),
        RewardDay(4, 400, "400 COINS", "Day 4 Power Pack"),
        RewardDay(5, 600, "600 COINS", "Day 5 High Roller"),
        RewardDay(6, 800, "800 COINS", "Day 6 Master Class"),
        RewardDay(7, 1500, "1,500 COINS", "Day 7 JACKPOT + GOLDEN CROWN", isJackpot = true)
    )

    data class RewardDay(
        val dayNumber: Int,
        val coins: Int,
        val title: String,
        val subtitle: String,
        val isJackpot: Boolean = false
    )

    data class ClaimResult(
        val success: Boolean,
        val dayClaimed: Int,
        val coinsAwarded: Int,
        val newTotalCoins: Int,
        val message: String
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getProfileKey(context: Context, baseKey: String): String {
        val activeId = ProfileManager.getActiveProfileId(context) ?: "default_player"
        return "${activeId}_$baseKey"
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(cal.time)
    }

    fun getCoinBalance(context: Context): Int {
        val prefs = getPrefs(context)
        val key = getProfileKey(context, KEY_COIN_BALANCE)
        return prefs.getInt(key, 100) // 100 starter coins
    }

    fun addCoins(context: Context, amount: Int): Int {
        val prefs = getPrefs(context)
        val key = getProfileKey(context, KEY_COIN_BALANCE)
        val current = getCoinBalance(context)
        val updated = (current + amount).coerceAtLeast(0)
        prefs.edit().putInt(key, updated).apply()
        return updated
    }

    fun getCurrentStreak(context: Context): Int {
        val prefs = getPrefs(context)
        val lastClaim = prefs.getString(getProfileKey(context, KEY_LAST_CLAIM_DATE), "") ?: ""
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        val storedStreak = prefs.getInt(getProfileKey(context, KEY_STREAK_DAY), 0)

        return when {
            lastClaim == today -> storedStreak
            lastClaim == yesterday -> storedStreak
            lastClaim.isEmpty() -> 0
            else -> 0 // Streak broken if more than 1 day missed
        }
    }

    fun getNextDayToClaim(context: Context): Int {
        val currentStreak = getCurrentStreak(context)
        val isClaimedToday = isClaimedToday(context)
        return if (isClaimedToday) {
            currentStreak
        } else {
            val next = (currentStreak % 7) + 1
            next
        }
    }

    fun isClaimedToday(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lastClaim = prefs.getString(getProfileKey(context, KEY_LAST_CLAIM_DATE), "") ?: ""
        return lastClaim == getTodayDateString()
    }

    fun canClaimReward(context: Context): Boolean {
        return !isClaimedToday(context)
    }

    fun claimTodayReward(context: Context): ClaimResult {
        val prefs = getPrefs(context)
        val today = getTodayDateString()
        val yesterday = getYesterdayDateString()
        val lastClaim = prefs.getString(getProfileKey(context, KEY_LAST_CLAIM_DATE), "") ?: ""

        if (lastClaim == today) {
            return ClaimResult(
                success = false,
                dayClaimed = getCurrentStreak(context),
                coinsAwarded = 0,
                newTotalCoins = getCoinBalance(context),
                message = "Already claimed today! Come back tomorrow."
            )
        }

        val previousStreak = if (lastClaim == yesterday) {
            prefs.getInt(getProfileKey(context, KEY_STREAK_DAY), 0)
        } else {
            0
        }

        val newStreak = (previousStreak % 7) + 1
        val rewardDay = REWARD_STREAKS.firstOrNull { it.dayNumber == newStreak } ?: REWARD_STREAKS[0]
        val newCoins = addCoins(context, rewardDay.coins)

        prefs.edit()
            .putInt(getProfileKey(context, KEY_STREAK_DAY), newStreak)
            .putString(getProfileKey(context, KEY_LAST_CLAIM_DATE), today)
            .apply()

        return ClaimResult(
            success = true,
            dayClaimed = newStreak,
            coinsAwarded = rewardDay.coins,
            newTotalCoins = newCoins,
            message = "Claimed +${rewardDay.coins} coins for Day $newStreak!"
        )
    }
}
