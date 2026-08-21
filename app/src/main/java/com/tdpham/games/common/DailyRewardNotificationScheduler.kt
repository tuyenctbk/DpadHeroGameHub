package com.tdpham.games.common

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object DailyRewardNotificationScheduler {

    private const val TAG = "DailyRewardScheduler"
    private const val REQUEST_CODE = 9001

    /**
     * Initializes both the local AlarmManager precision trigger and the WorkManager background worker.
     */
    fun init(context: Context) {
        try {
            DailyRetentionWorker.schedule(context)
            scheduleNextDailyReminder(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize daily reward scheduler: ${e.message}", e)
        }
    }

    /**
     * Computes the exact time for the next daily reward notification (either immediate or tomorrow at 10:00 AM)
     * and sets an exact/inexact alarm via AlarmManager.
     */
    fun scheduleNextDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, DailyRewardAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val targetCalendar = Calendar.getInstance().apply {
            // Default target: 10:00 AM
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If 10 AM today has already passed, schedule for tomorrow 10 AM
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetCalendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    targetCalendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next daily reward notification for: ${targetCalendar.time}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting alarm: ${e.message}", e)
        }
    }

    fun cancelReminder(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyRewardAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error cancelling reminder: ${e.message}", e)
        }
    }
}
