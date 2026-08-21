package com.tdpham.games.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.tdpham.games.R
import com.tdpham.games.hub.SplashActivity
import java.util.concurrent.TimeUnit

class DailyRetentionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val canClaim = DailyRewardManager.canClaimReward(context)
            val currentStreak = DailyRewardManager.getCurrentStreak(context)

            // Log retention heartbeat to Firebase Analytics
            try {
                val bundle = android.os.Bundle().apply {
                    putInt("current_streak", currentStreak)
                    putBoolean("reward_available", canClaim)
                }
                Firebase.analytics.logEvent("daily_retention_check", bundle)
            } catch (_: Throwable) {}

            if (canClaim) {
                showRetentionNotification(currentStreak)
            }

            return Result.success()
        } catch (e: Throwable) {
            return Result.retry()
        }
    }

    private fun showRetentionNotification(streak: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = "daily_rewards_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.app_name) + " Daily Rewards",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for daily login bonuses and streak rewards"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_DAILY_REWARDS", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextDay = (streak % 7) + 1
        val rewardAmount = DailyRewardManager.REWARD_STREAKS.firstOrNull { it.dayNumber == nextDay }?.coins ?: 100

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle("🎁 Day $nextDay Daily Reward Ready!")
            .setContentText("Claim +$rewardAmount arcade coins today to keep your $streak-day streak alive!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(2001, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted, safe to ignore
        }
    }

    companion object {
        private const val WORK_TAG = "DailyRetentionWork"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<DailyRetentionWorker>(
                24, TimeUnit.HOURS,
                6, TimeUnit.HOURS // flex interval
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
