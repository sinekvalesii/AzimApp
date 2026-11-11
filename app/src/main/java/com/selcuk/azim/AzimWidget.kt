package com.selcuk.azim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import androidx.core.content.edit
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ana ekran widget'ı: gün, seri, ilerleme ve sıradaki 3 görev.
 * Onay kutusuna dokununca görev widget üzerinde hemen işaretlenir; uygulama bir sonraki açılışta
 * bu işlemi (XP, seri, istatistik) kendi verisine işler.
 */
class AzimWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHECK) {
            val qid = intent.getStringExtra(EXTRA_QID) ?: return
            markDone(context, qid)
            updateAll(context)
        }
    }

    companion object {
        private const val ACTION_CHECK = "com.selcuk.azim.WIDGET_CHECK"
        private const val EXTRA_QID = "qid"
        private const val PENDING = "widget_pending"

        private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        private val ROWS = listOf(
            intArrayOf(R.id.w_r1, R.id.w_tm1, R.id.w_t1, R.id.w_ck1),
            intArrayOf(R.id.w_r2, R.id.w_tm2, R.id.w_t2, R.id.w_ck2),
            intArrayOf(R.id.w_r3, R.id.w_tm3, R.id.w_t3, R.id.w_ck3)
        )

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, AzimWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_azim)
            val s = Reminders.summary(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.w_root, open)

            val day = s.optInt("day", 0)
            v.setTextViewText(R.id.w_day, if (day > 0) ctx.getString(R.string.widget_day, day) else ctx.getString(R.string.app_name))
            v.setTextViewText(R.id.w_streak, "🔥 " + s.optInt("streak", 0))

            val quests = s.optJSONArray("quests") ?: JSONArray()
            val fresh = s.optString("date") == today()
            val total = quests.length()
            val done = (0 until total).count { quests.getJSONObject(it).optBoolean("done") }
            v.setProgressBar(R.id.w_bar, 100, if (total > 0 && fresh) done * 100 / total else 0, false)

            val message = when {
                day == 0 -> ctx.getString(R.string.widget_setup)
                !fresh -> ctx.getString(R.string.widget_open)
                total > 0 && done == total -> ctx.getString(R.string.widget_all_done)
                else -> null
            }
            if (message != null) {
                v.setViewVisibility(R.id.w_empty, View.VISIBLE)
                v.setTextViewText(R.id.w_empty, message)
                ROWS.forEach { v.setViewVisibility(it[0], View.GONE) }
                return v
            }
            v.setViewVisibility(R.id.w_empty, View.GONE)

            // Önce bekleyen görevler, sonra bitenler
            val ordered = (0 until total).map { quests.getJSONObject(it) }.sortedBy { if (it.optBoolean("done")) 1 else 0 }
            ROWS.forEachIndexed { i, r ->
                val q = ordered.getOrNull(i)
                if (q == null) {
                    v.setViewVisibility(r[0], View.GONE)
                    return@forEachIndexed
                }
                val isDone = q.optBoolean("done")
                v.setViewVisibility(r[0], View.VISIBLE)
                v.setTextViewText(r[1], q.optString("time").ifBlank { "—" })
                v.setTextViewText(r[2], q.optString("title"))
                v.setTextColor(r[2], if (isDone) 0xFF5F5952.toInt() else 0xFFF1EBE2.toInt())
                v.setImageViewResource(r[3], if (isDone) R.drawable.widget_check_on else R.drawable.widget_check_off)
                if (!isDone) {
                    val qid = q.optString("id")
                    val pi = PendingIntent.getBroadcast(
                        ctx, qid.hashCode(),
                        Intent(ctx, AzimWidget::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_QID, qid),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    v.setOnClickPendingIntent(r[3], pi)
                }
            }
            return v
        }

        /** Görevi özet içinde tamamlandı yapar ve uygulamanın işlemesi için kuyruğa ekler. */
        private fun markDone(ctx: Context, qid: String) {
            val p = Reminders.prefs(ctx)
            val s = Reminders.summary(ctx)
            if (s.optString("date") != today()) return
            val quests = s.optJSONArray("quests") ?: return
            var changed = false
            for (i in 0 until quests.length()) {
                val q = quests.getJSONObject(i)
                if (q.optString("id") == qid && !q.optBoolean("done")) {
                    q.put("done", true); changed = true
                }
            }
            if (!changed) return
            s.put("left", (s.optInt("left", 1) - 1).coerceAtLeast(0))
            val queue = runCatching { JSONArray(p.getString(PENDING, "[]")) }.getOrDefault(JSONArray())
            queue.put(JSONObject().put("id", qid).put("date", today()))
            p.edit { putString("summary", s.toString()); putString(PENDING, queue.toString()) }
        }

        /** Uygulama açıldığında: bugüne ait widget işlemlerini döndürür ve kuyruğu temizler. */
        fun takeActions(ctx: Context): String {
            val p = Reminders.prefs(ctx)
            val queue = runCatching { JSONArray(p.getString(PENDING, "[]")) }.getOrDefault(JSONArray())
            p.edit { remove(PENDING) }
            val out = JSONArray()
            val d = today()
            for (i in 0 until queue.length()) {
                val o = queue.getJSONObject(i)
                if (o.optString("date") == d) out.put(o.optString("id"))
            }
            return out.toString()
        }
    }
}
