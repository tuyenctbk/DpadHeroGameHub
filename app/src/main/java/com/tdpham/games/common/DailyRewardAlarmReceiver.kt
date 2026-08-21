package com.tdpham.games.common

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tdpham.games.R
import com.tdpham.games.hub.SplashActivity
import java.util.Calendar

class DailyRewardAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (DailyRewardManager.canClaimReward(context)) {
                val currentStreak = DailyRewardManager.getCurrentStreak(context)
                val nextDay = (currentStreak % 7) + 1
                val rewardCoins = DailyRewardManager.REWARD_STREAKS.firstOrNull { it.dayNumber == nextDay }?.coins ?: 100

                showNotification(context, nextDay, rewardCoins, currentStreak)
            }
            // Reschedule for the next day
            DailyRewardNotificationScheduler.scheduleNextDailyReminder(context)
        } catch (e: Throwable) {
            Log.e("DailyRewardAlarm", "Error receiving daily reward alarm: ${e.message}", e)
        }
    }

    private fun showNotification(context: Context, dayNumber: Int, rewardCoins: Int, streak: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = "daily_rewards_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.app_name) + " Daily Rewards",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily bonus reminders and streak protection alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_DAILY_REWARDS", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle("🎁 Day $dayNumber Daily Bonus Ready!")
            .setContentText("Claim +$rewardCoins coins today! Keep your $streak-day streak going strong!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your Day $dayNumber arcade login reward is ready to be claimed (+$rewardCoins coins). Jump back in to unlock new themes, avatars, and keep your streak alive!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(2002, notification)
        } catch (_: SecurityException) {}
    }
}
