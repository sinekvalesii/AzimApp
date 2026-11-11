package com.selcuk.azim.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

/**
 * Uyarlanabilir zorluk.
 * Son 7 günün tamamlama oranına bakar:
 *  - oran >= %85 ve son 3 gün üst üste tamamlandıysa hedef ~%10 artar,
 *  - oran <= %40 ise hedef ~%15 azalır,
 *  - arada ise hedef sabit kalır.
 * Hedef, en son değişiklikten sonra en az 3 gün sabit tutulur (her gün oynamasın diye).
 * Ortalama nabız kişisel ortalamanın %8 üzerindeyse (yorgunluk işareti) artış ertelenir.
 */
object Difficulty {
    fun next(q: Quest, history: List<DayLog>, fatigued: Boolean): Double {
        if (history.size < 3) return q.target
        val last3 = history.takeLast(3)
        if (last3.any { it.target != q.target }) return q.target
        val rate = history.count { it.done }.toDouble() / history.size
        val dir = when {
            rate >= 0.85 && last3.all { it.done } && !fatigued -> 1
            rate <= 0.40 -> -1
            else -> 0
        }
        if (dir == 0) return q.target
        val raw = q.target * if (dir > 0) 1.10 else 0.85
        var t = (raw / q.step).roundToLong() * q.step
        if (t == q.target) t = q.target + dir * q.step // en az bir adım değişsin
        return t.coerceIn(q.min, q.max)
    }

    fun fatigued(health: List<HealthDay>): Boolean {
        val hr = health.mapNotNull { it.avgHr }
        if (hr.size < 4) return false
        return hr.last() > hr.dropLast(1).average() * 1.08
    }
}

class Repo(private val db: AzimDb) {
    private val dao = db.dao()
    val quests = dao.quests()
    val allLogs = dao.allLogs()
    val health = dao.health()

    /** Bugünün görevlerini (gerekirse yeni hedeflerle) oluşturur. Gün içinde tekrar çağrılması güvenlidir. */
    suspend fun ensureToday(today: String) {
        var qs = dao.questsOnce()
        if (qs.isEmpty()) {
            dao.upsertQuests(DEFAULTS); qs = DEFAULTS
        }
        val logs = dao.allLogsOnce()
        if (logs.any { it.date == today }) return
        val fatigued = Difficulty.fatigued(dao.healthOnce())
        val updated = qs.map { q ->
            q.copy(target = Difficulty.next(q, logs.filter { it.questId == q.id }.takeLast(7), fatigued))
        }
        dao.upsertQuests(updated)
        dao.upsertLogs(updated.map { DayLog(today, it.id, it.target) })
    }

    suspend fun setProgress(log: DayLog, progress: Double) =
        dao.upsertLog(log.copy(progress = progress.coerceAtLeast(0.0)))

    /** Sağlık verisini kaydeder ve adım görevini otomatik ilerletir. */
    suspend fun saveHealth(days: List<HealthDay>, today: String) {
        if (days.isEmpty()) return
        dao.upsertHealth(days)
        val steps = days.find { it.date == today }?.steps ?: return
        dao.logsOnce(today).find { it.questId == "steps" }?.let {
            if (steps > it.progress) dao.upsertLog(it.copy(progress = steps.toDouble()))
        }
    }

    suspend fun wipe() = withContext(Dispatchers.IO) { db.clearAllTables() }

    companion object {
        val DEFAULTS = listOf(
            Quest("pushup", "Şınav", "tekrar", 10.0, 2.0, 4.0, 100.0, 30),
            Quest("water", "Su iç", "bardak", 6.0, 1.0, 4.0, 12.0, 20),
            Quest("steps", "Adım", "adım", 6000.0, 500.0, 3000.0, 15000.0, 40, auto = true),
            Quest("squat", "Squat", "tekrar", 15.0, 5.0, 5.0, 150.0, 30),
            Quest("plank", "Plank", "sn", 30.0, 10.0, 20.0, 300.0, 30)
        )
    }
}
