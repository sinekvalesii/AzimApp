package com.selcuk.azim.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.selcuk.azim.AzimApp
import com.selcuk.azim.R
import com.selcuk.azim.data.done
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Alışkanlık döngüsünün "işaret" adımı: her akşam 20:00 civarı, kalan görev varsa hatırlatır. */
object Reminder {
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var at = now.withHour(20).withMinute(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, at).toMinutes(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val logs = (ctx as AzimApp).db.dao().logsOnce(LocalDate.now().toString())
        val left = logs.count { !it.done }
        if (logs.isNotEmpty() && left == 0) return Result.success()
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return Result.success()
        nm.createNotificationChannel(
            NotificationChannel("daily", ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (logs.isEmpty()) "Bugünün görevleri seni bekliyor." else "$left görev kaldı. Seriyi koru."
        nm.notify(
            1, NotificationCompat.Builder(ctx, "daily").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("AZİM").setContentText(text).setContentIntent(open).setAutoCancel(true).build()
        )
        return Result.success()
    }
}
