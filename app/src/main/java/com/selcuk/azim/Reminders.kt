package com.selcuk.azim

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import androidx.core.content.edit
import org.json.JSONObject
import java.util.Calendar

/**
 * Günlük hatırlatıcılar.
 * Plan web arayüzünden gelir (AzimNative.setSchedule): kullanıcının uyanış/uyku saatine göre
 * 7 genel bildirim (motivasyon, 3× su, antrenman, görev kontrolü, gece) + saati olan her görev için bir bildirim.
 *
 * Kesin alarm izni (SCHEDULE_EXACT_ALARM) KULLANILMAZ; Google Play bu izni yalnızca alarm/saat uygulamalarına veriyor.
 * setAndAllowWhileIdle birkaç dakikalık sapmayla tetiklenir. Her bildirim tetiklendiğinde ertesi güne yeniden kurulur;
 * cihaz yeniden başlarsa BootReceiver hepsini tekrar kurar.
 */
object Reminders {

    const val CHANNEL = "azim_daily"
    const val PREFS = "azim_reminders"

    data class Item(
        val id: Int, val kind: String, val hour: Int, val minute: Int,
        val days: List<Int>?, val title: String?, val body: String?, val qid: String?
    )

    /** Uygulama hiç açılmadıysa kullanılacak varsayılan plan (07:00 uyanış, 23:00 uyku). */
    private val DEFAULT = listOf(
        Item(1, "morning", 7, 30, null, null, null, null),
        Item(2, "water", 10, 0, null, null, null, null),
        Item(3, "water", 13, 0, null, null, null, null),
        Item(4, "water", 16, 0, null, null, null, null),
        Item(5, "training", 18, 0, null, null, null, null),
        Item(6, "tasks", 20, 0, null, null, null, null),
        Item(7, "night", 22, 15, null, null, null, null)
    )

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean("enabled", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit { putBoolean("enabled", on) }
        if (on) scheduleAll(ctx) else cancelAll(ctx)
    }

    fun saveSummary(ctx: Context, json: String) {
        prefs(ctx).edit { putString("summary", json) }
    }

    fun summary(ctx: Context): JSONObject =
        runCatching { JSONObject(prefs(ctx).getString("summary", "{}") ?: "{}") }.getOrDefault(JSONObject())

    /** Web arayüzünden gelen planı kaydeder ve hepsini yeniden kurar. */
    fun saveSchedule(ctx: Context, json: String) {
        cancelAll(ctx)
        prefs(ctx).edit { putString("schedule", json) }
        scheduleAll(ctx)
    }

    fun items(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString("schedule", null) ?: return DEFAULT
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val (h, m) = o.getString("time").split(":").map { it.toInt() }
                val days = o.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) } }
                Item(
                    o.getInt("id"), o.getString("kind"), h, m, days,
                    o.optString("title").ifBlank { null }, o.optString("body").ifBlank { null },
                    o.optString("qid").ifBlank { null }
                )
            }
        }.getOrDefault(DEFAULT)
    }

    fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = ctx.getString(R.string.channel_desc) }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun scheduleAll(ctx: Context) {
        if (!isEnabled(ctx)) return
        val list = items(ctx)
        list.forEach { schedule(ctx, it) }
        prefs(ctx).edit { putString("ids", list.joinToString(",") { it.id.toString() }) }
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val ids = (prefs(ctx).getString("ids", "") ?: "").split(",").mapNotNull { it.toIntOrNull() } +
            items(ctx).map { it.id }
        ids.toSet().forEach { am.cancel(pending(ctx, it)) }
    }

    fun schedule(ctx: Context, item: Item) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, item.id))
    }

    private fun pending(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun hasPermission(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Bugün bu bildirimin gösterilip gösterilmeyeceği (gün filtresi, görev zaten bitti mi). */
    fun shouldShow(ctx: Context, item: Item): Boolean {
        val jsDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0 = Pazar
        if (item.days != null && jsDow !in item.days) return false
        if (item.qid != null) {
            val s = summary(ctx)
            val qs = s.optJSONArray("quests") ?: return true
            for (i in 0 until qs.length()) {
                val q = qs.getJSONObject(i)
                if (q.optString("id") == item.qid && q.optBoolean("done")) return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission") // izin hasPermission() ile kontrol ediliyor
    fun show(ctx: Context, item: Item) {
        if (!hasPermission(ctx)) return
        val (title, body) = if (item.kind == "quest")
            (item.title ?: "AZİM") to (item.body ?: "")
        else Messages.build(item, summary(ctx))
        val open = PendingIntent.getActivity(
            ctx, 1000 + item.id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFEE7A2F.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(ctx).notify(item.id, n)
    }

    fun testNotification(ctx: Context) {
        ensureChannel(ctx)
        show(ctx, items(ctx).firstOrNull { it.kind == "water" } ?: DEFAULT[1])
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val item = Reminders.items(context).firstOrNull { it.id == intent.getIntExtra("id", -1) } ?: return
        if (!Reminders.isEnabled(context)) return
        Reminders.ensureChannel(context)
        if (Reminders.shouldShow(context, item)) Reminders.show(context, item)
        Reminders.schedule(context, item) // ertesi gün aynı saat
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Reminders.ensureChannel(context)
                Reminders.scheduleAll(context)
                AzimWidget.updateAll(context)
            }
        }
    }
}
