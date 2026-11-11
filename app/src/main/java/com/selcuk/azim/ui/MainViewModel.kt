package com.selcuk.azim.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.selcuk.azim.AzimApp
import com.selcuk.azim.data.DayLog
import com.selcuk.azim.data.Difficulty
import com.selcuk.azim.data.HealthDay
import com.selcuk.azim.data.Quest
import com.selcuk.azim.data.done
import com.selcuk.azim.health.HcState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuestUi(val quest: Quest, val log: DayLog)
data class DayStat(val date: String, val steps: Long, val hr: Long?, val rate: Float?)
data class UiState(
    val quests: List<QuestUi> = emptyList(),
    val xp: Int = 0,
    val streak: Int = 0,
    val week: List<DayStat> = emptyList(),
    val insight: String = "",
    val hc: HcState = HcState.UNAVAILABLE,
    val fatigued: Boolean = false
) {
    val level get() = 1 + xp / 500
    val levelProgress get() = (xp % 500) / 500f
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as AzimApp
    val auth = a.auth
    val session = auth.session
    val msg = MutableStateFlow<String?>(null)
    private val today = MutableStateFlow(LocalDate.now().toString())
    private val hc = MutableStateFlow(HcState.UNAVAILABLE)

    val state: StateFlow<UiState> =
        combine(a.repo.quests, a.repo.allLogs, a.repo.health, today, hc, ::build)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private fun build(qs: List<Quest>, logs: List<DayLog>, health: List<HealthDay>, t: String, h: HcState): UiState {
        val qmap = qs.associateBy { it.id }
        val byDate = logs.groupBy { it.date }
        fun rate(d: String): Float? = byDate[d]?.let { l -> l.count { it.done }.toFloat() / l.size }
        // Seri: tamamlama oranı >= %60 olan ardışık günler
        var d = LocalDate.parse(t)
        if ((rate(t) ?: 0f) < 0.6f) d = d.minusDays(1)
        var streak = 0
        while ((rate(d.toString()) ?: 0f) >= 0.6f) {
            streak++; d = d.minusDays(1)
        }
        val hmap = health.associateBy { it.date }
        val week = (6 downTo 0).map { LocalDate.parse(t).minusDays(it.toLong()).toString() }
            .map { DayStat(it, hmap[it]?.steps ?: 0, hmap[it]?.avgHr, rate(it)) }
        return UiState(
            quests = byDate[t].orEmpty().mapNotNull { l -> qmap[l.questId]?.let { QuestUi(it, l) } },
            xp = logs.filter { it.done }.sumOf { qmap[it.questId]?.xp ?: 0 },
            streak = streak,
            week = week,
            insight = insight(health.filter { it.date != t }.mapNotNull { hd -> rate(hd.date)?.let { hd.steps to it } }),
            hc = h,
            fatigued = Difficulty.fatigued(health)
        )
    }

    /** Adım sayısı ile görev tamamlama arasındaki ilişki: medyanın üstündeki ve altındaki günleri karşılaştırır. */
    private fun insight(pairs: List<Pair<Long, Float>>): String {
        if (pairs.size < 4) return "İlişki analizi için en az 4 günlük adım ve görev verisi gerekiyor."
        val median = pairs.map { it.first }.sorted()[pairs.size / 2]
        val hi = pairs.filter { it.first >= median }.map { it.second }.average() * 100
        val lo = pairs.filter { it.first < median }.map { it.second }.average() * 100
        if (lo.isNaN()) return "Adım sayın günden güne çok benzer; karşılaştırma için daha fazla veri gerekiyor."
        val diff = (hi - lo).toInt()
        return when {
            diff >= 10 -> "Daha çok yürüdüğün günlerde görevlerin %${hi.toInt()}'ini, az yürüdüğün günlerde %${lo.toInt()}'ini tamamlıyorsun. Hareketli günler sana iyi geliyor."
            diff <= -10 -> "Az yürüdüğün günlerde görev tamamlama oranın daha yüksek (%${lo.toInt()} / %${hi.toInt()}). Yoğun günlerde hedefleri küçültmeyi dene."
            else -> "Adım sayın ile görev tamamlama oranın arasında belirgin bir fark yok (%${hi.toInt()} / %${lo.toInt()})."
        }
    }

    fun refresh() = viewModelScope.launch {
        val t = LocalDate.now().toString()
        a.repo.ensureToday(t)
        today.value = t
        hc.value = a.health.state()
        if (hc.value == HcState.READY) a.repo.saveHealth(a.health.lastDays(), t)
    }

    fun step(q: QuestUi, dir: Int) = viewModelScope.launch {
        a.repo.setProgress(q.log, (q.log.progress + dir * q.quest.step).coerceAtMost(q.log.target))
    }

    fun toggle(q: QuestUi) = viewModelScope.launch {
        a.repo.setProgress(q.log, if (q.log.done) 0.0 else q.log.target)
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            msg.value = "Güvenlik için çıkış yapıp tekrar giriş yap, sonra yeniden dene."
        } catch (e: Exception) {
            msg.value = e.localizedMessage ?: "Bir hata oluştu."
        }
    }

    private fun valid(email: String, pass: String?): Boolean {
        val ok = email.contains("@") && (pass == null || pass.length >= 6)
        if (!ok) msg.value = "Geçerli bir e-posta ve en az 6 karakterli şifre gir."
        return ok
    }

    fun signIn(e: String, p: String) { if (valid(e, p)) run { auth.signIn(e.trim(), p) } }
    fun signUp(e: String, p: String) { if (valid(e, p)) run { auth.signUp(e.trim(), p) } }
    fun reset(e: String) { if (valid(e, null)) run { auth.reset(e.trim()); msg.value = "Şifre sıfırlama e-postası gönderildi." } }
    fun deleteAccount() = run { auth.delete(); a.repo.wipe(); refresh() }
}
