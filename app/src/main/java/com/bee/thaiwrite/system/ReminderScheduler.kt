package com.bee.thaiwrite.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bee.thaiwrite.MainActivity
import com.bee.thaiwrite.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReminderScheduler(private val context: Context) {
    fun scheduleDaily(hour: Int, minute: Int) {
        val delay = computeDelay(hour, minute)
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun computeDelay(hour: Int, minute: Int): Duration {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        return computeNextReminderDelay(now, hour, minute)
    }

    companion object {
        private const val WORK_NAME = "thaiwrite.daily.reminder"
    }
}

internal fun computeNextReminderDelay(now: LocalDateTime, hour: Int, minute: Int): Duration {
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return Duration.between(now, next)
}

class StudyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        postReminder()
        val settings = AppSettings(applicationContext)
        val state = runBlocking { settings.settings.first() }
        ReminderScheduler(applicationContext).scheduleDaily(state.reminderHour, state.reminderMinute)
        return Result.success()
    }

    private fun postReminder() {
        if (
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily study",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            701,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ThaiWrite")
            .setContentText("Write one small piece of Thai today.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(701, notification)
    }

    companion object {
        private const val CHANNEL_ID = "thaiwrite_daily_review"
    }
}
