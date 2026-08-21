package com.tdpham.games.common

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tdpham.games.R

class DailyRewardDialog(
    private val context: Context,
    private val onClaimed: ((Int) -> Unit)? = null
) : Dialog(context) {

    private val container: LinearLayout
    private val btnClaim: Button
    private val btnClose: Button
    private val tvStreakStatus: TextView
    private val tvCoinBalance: TextView

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_daily_reward)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(true)

        container = findViewById(R.id.rewards_cards_container)
        btnClaim = findViewById(R.id.btn_claim_reward)
        btnClose = findViewById(R.id.btn_close_daily_reward)
        tvStreakStatus = findViewById(R.id.tv_streak_status)
        tvCoinBalance = findViewById(R.id.tv_dialog_coin_balance)

        setupUI()
        setupFocusEffects()
    }

    private fun setupUI() {
        val currentStreak = DailyRewardManager.getCurrentStreak(context)
        val isClaimedToday = DailyRewardManager.isClaimedToday(context)
        val nextDay = DailyRewardManager.getNextDayToClaim(context)
        val balance = DailyRewardManager.getCoinBalance(context)

        tvCoinBalance.text = balance.toString()

        tvStreakStatus.text = if (isClaimedToday) {
            "🔥 Current Streak: $currentStreak days! You've already collected today's bonus."
        } else {
            "🔥 Current Streak: $currentStreak days. Ready to collect Day $nextDay reward!"
        }

        container.removeAllViews()

        DailyRewardManager.REWARD_STREAKS.forEach { rewardDay ->
            val card = LayoutInflater.from(context).inflate(R.layout.item_daily_reward_card, container, false)
            val root = card.findViewById<LinearLayout>(R.id.daily_reward_card_root)
            val tvDay = card.findViewById<TextView>(R.id.reward_day_label)
            val tvCoin = card.findViewById<TextView>(R.id.reward_coin_amount)
            val tvStatus = card.findViewById<TextView>(R.id.reward_status_badge)

            tvDay.text = if (rewardDay.isJackpot) "👑 DAY 7" else "DAY ${rewardDay.dayNumber}"
            tvCoin.text = "+${rewardDay.coins}"

            when {
                // Already completed days in streak
                rewardDay.dayNumber < nextDay || (rewardDay.dayNumber == nextDay && isClaimedToday) -> {
                    root.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#142E1F"))
                    tvDay.setTextColor(Color.parseColor("#81C784"))
                    tvCoin.setTextColor(Color.parseColor("#A5D6A7"))
                    tvStatus.text = "✓ CLAIMED"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                // Ready to claim today
                rewardDay.dayNumber == nextDay && !isClaimedToday -> {
                    root.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
                    tvDay.setTextColor(Color.parseColor("#000000"))
                    tvCoin.setTextColor(Color.parseColor("#1B263B"))
                    tvStatus.text = "READY!"
                    tvStatus.setTextColor(Color.parseColor("#D32F2F"))
                    root.animate().scaleX(1.06f).scaleY(1.06f).setDuration(250).start()
                }
                // Upcoming / locked
                else -> {
                    root.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#141C2E"))
                    tvDay.setTextColor(Color.parseColor("#607D8B"))
                    tvCoin.setTextColor(Color.parseColor("#90A4AE"))
                    tvStatus.text = "LOCKED"
                    tvStatus.setTextColor(Color.parseColor("#455A64"))
                }
            }

            container.addView(card)
        }

        if (isClaimedToday) {
            btnClaim.isEnabled = false
            btnClaim.alpha = 0.5f
            btnClaim.text = "✓ CLAIMED TODAY"
            btnClose.requestFocus()
        } else {
            btnClaim.isEnabled = true
            btnClaim.alpha = 1.0f
            btnClaim.text = "🎁 CLAIM DAY $nextDay (+${DailyRewardManager.REWARD_STREAKS.find { it.dayNumber == nextDay }?.coins ?: 100})"
            btnClaim.requestFocus()
        }

        btnClaim.setOnClickListener {
            SoundManager.playSuccess()
            HapticManager.vibrateSuccess(context)
            val result = DailyRewardManager.claimTodayReward(context)
            if (result.success) {
                Toast.makeText(context, "🎉 ${result.message}", Toast.LENGTH_SHORT).show()
                onClaimed?.invoke(result.newTotalCoins)
                setupUI()
            } else {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener {
            SoundManager.playClick()
            HapticManager.vibrateClick(context)
            dismiss()
        }
    }

    private fun setupFocusEffects() {
        listOf(btnClaim, btnClose).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    HapticManager.vibrateClick(context)
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }
    }
}
