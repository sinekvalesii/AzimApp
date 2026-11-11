package com.selcuk.azim

import org.json.JSONObject
import java.util.Calendar

/**
 * Bildirim metinleri. Her gün farklı bir cümle seçilir (yılın günü + saat dilimi),
 * böylece aynı mesaj art arda gelmez. Kullanıcının adı, serisi ve kalan görevleriyle kişiselleştirilir.
 */
object Messages {

    private val MOTIVATION = listOf(
        "Motivasyon seni başlatır, alışkanlık devam ettirir.",
        "Bugün sadece bir sonraki görevi bitir. Gerisi kendiliğinden gelir.",
        "Küçük bir gün, sıfır günden iyidir.",
        "Kendine verdiğin sözü tuttukça kendine güvenin artar.",
        "Disiplin, istemediğin anlarda da yapabilmektir.",
        "Her tamamlanan görev, olmak istediğin kişiye verilmiş bir oydur.",
        "Zor olanı önce yap. Günün geri kalanı hafifler.",
        "Mükemmel olmak zorunda değilsin, sadece zinciri kırma.",
        "Bugün yaptığın, 66. günde kim olacağını belirliyor.",
        "Kimse izlemiyorken yaptıkların karakterindir.",
        "Yorgunken yapılan tek set, hiç yapılmayan on setten değerlidir.",
        "Ertelemek kolaydır; pişmanlık ağırdır. Başla.",
        "Rahatlık alanının dışı, gelişimin başladığı yerdir.",
        "Bugün de kendine bir söz ver ve tut."
    )

    private val WATER = listOf(
        "Bir bardak su iç. Vücut ağırlığının yaklaşık %2'si kadar sıvı kaybı bile dikkat ve performansı düşürebilir.",
        "Su molası. Susamayı bekleme; susama hissi, ihtiyacın başladıktan sonra gelir.",
        "Masanın üzerinde bir şişe su tut. Görünen su daha çok içilir.",
        "Kahveden önce bir bardak su. Odak için en ucuz destek.",
        "Bir bardak su iç ve görevine işaretle. Küçük adım, büyük zincir.",
        "Baş ağrısı ve halsizlik bazen sadece susuzluktur. Bir bardak su iç."
    )

    private val TRAINING = listOf(
        "Antrenman kuralı: 5–10 dakika ısınmadan ağırlığa başlama.",
        "Antrenman kuralı: Setler arası 60–90 saniye dinlen. Ağır setlerde 2–3 dakika.",
        "Antrenman kuralı: Aynı kas grubunu tekrar çalıştırmadan önce yaklaşık 48 saat dinlendir.",
        "Antrenman kuralı: Formdan ödün verme. Doğru tekrar, fazla ağırlıktan önemlidir.",
        "Antrenman kuralı: Her hafta ağırlığı ya da tekrarı biraz artır. Gelişim kademeli yüklenmeyle gelir.",
        "Antrenman kuralı: Antrenmandan sonraki öğünde yeterli protein almayı unutma.",
        "Antrenman kuralı: Uyku, kas onarımının en büyük kısmının gerçekleştiği zamandır. 7–9 saat hedefle.",
        "Antrenman kuralı: Ağrı ile yorgunluğu ayırt et. Keskin ağrıda dur."
    )

    private val NIGHT = listOf(
        "Ekranı bırakma saati. Yatmadan önceki ekran ışığı uykuya dalmayı geciktirebilir.",
        "Yarın için tek bir hedef belirle ve ışıkları kapat.",
        "Her gün aynı saatte yatmak, sabah kalkışı kolaylaştırır.",
        "Günü kapatmadan önce 3 satır yaz: ne iyi gitti, ne zorladı, yarın ne yapacaksın?"
    )

    private val MOTIVATION_EN = listOf(
        "Motivation gets you started; habit keeps you going.",
        "Today, just finish the next quest. The rest follows.",
        "A small day beats a zero day.",
        "Every promise you keep to yourself builds self-trust.",
        "Discipline is doing it even when you don't feel like it.",
        "Each completed quest is a vote for the person you want to become.",
        "Do the hard thing first. The rest of the day gets lighter.",
        "You don't have to be perfect. Just don't break the chain.",
        "What you do today decides who you are on day 66.",
        "Character is what you do when nobody is watching.",
        "One tired set beats ten skipped ones.",
        "Putting it off is easy; regret is heavy. Start.",
        "Growth starts outside your comfort zone.",
        "Make yourself a promise today, and keep it."
    )
    private val WATER_EN = listOf(
        "Drink a glass of water. Losing about 2% of body weight in fluid can already hurt focus and performance.",
        "Water break. Don't wait for thirst; it shows up after the need has started.",
        "Keep a bottle on your desk. Visible water gets drunk.",
        "One glass of water before coffee. The cheapest focus boost there is.",
        "Drink a glass and tick it off. Small step, long chain.",
        "Headache and fatigue are sometimes just dehydration. Drink a glass of water."
    )
    private val TRAINING_EN = listOf(
        "Training rule: warm up 5–10 minutes before lifting.",
        "Training rule: rest 60–90 s between sets, 2–3 min on heavy sets.",
        "Training rule: give a muscle group about 48 hours before training it again.",
        "Training rule: never trade form for weight. A clean rep beats a heavy one.",
        "Training rule: add a little weight or a rep each week. Progress comes from progressive overload.",
        "Training rule: get enough protein in the meal after training.",
        "Training rule: most muscle repair happens during sleep. Aim for 7–9 hours.",
        "Training rule: know pain from fatigue. Stop on sharp pain."
    )
    private val NIGHT_EN = listOf(
        "Time to put the screen down. Screen light before bed can delay sleep.",
        "Pick one goal for tomorrow and turn off the lights.",
        "Going to bed at the same time makes waking up easier.",
        "Before you close the day, write 3 lines: what went well, what was hard, what's next."
    )

    fun build(item: Reminders.Item, s: JSONObject): Pair<String, String> {
        val en = s.optString("lang") == "en"
        val name = s.optString("name", "").ifBlank { null }
        val day = s.optInt("day", 0)
        val streak = s.optInt("streak", 0)
        val left = s.optInt("left", -1)
        val total = s.optInt("total", 0)
        val water = s.optDouble("water", 2.0)
        val dayIndex = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        fun <T> List<T>.pick(): T = this[(dayIndex * 7 + item.id) % size]
        val mot = if (en) MOTIVATION_EN else MOTIVATION

        if (en) return when (item.kind) {
            "morning" -> (if (day > 0) "Day $day / 66" else "Good morning") to (mot.pick() + if (total > 0) " You have $total quests today." else "")
            "water" -> "Water break" to (WATER_EN.pick() + if (water > 0) " Daily goal: ${formatL(water, false)} L." else "")
            "training" -> "Training time" to TRAINING_EN.pick()
            "tasks" -> when {
                left == 0 && total > 0 -> "Perfect day" to "All of today's quests are done. Streak: $streak days. Keep it up."
                left > 0 -> "$left quests left" to "${name?.let { "$it, " } ?: ""}a few hours left in the day. ${mot.pick()}"
                else -> "Quest check" to mot.pick()
            }
            else -> when {
                left > 0 && streak > 0 -> "Your streak is at risk" to "You have a $streak-day streak and $left quests left. The day closes at 23:59."
                left > 0 -> "Before the day ends" to "$left quests left. Finish a small one now."
                else -> "Good night" to NIGHT_EN.pick()
            }
        }

        return when (item.kind) {
            "morning" -> {
                val t = if (day > 0) "Gün $day / 66" else "Günaydın"
                val tasks = if (total > 0) " Bugün $total görevin var." else ""
                t to (mot.pick() + tasks)
            }
            "water" -> {
                val goal = if (water > 0) " Günlük hedefin: ${formatL(water, true)} L." else ""
                "Su molası" to (WATER.pick() + goal)
            }
            "training" -> "Antrenman zamanı" to TRAINING.pick()
            "tasks" -> when {
                left == 0 && total > 0 -> "Kusursuz gün" to "Bugünün tüm görevleri tamam. Serin: $streak gün. Böyle devam."
                left > 0 -> "$left görev kaldı" to "${name?.let { "$it, " } ?: ""}günün bitmesine birkaç saat var. ${mot.pick()}"
                else -> "Görev kontrolü" to mot.pick()
            }
            else -> when {
                left > 0 && streak > 0 -> "Serin tehlikede" to "$streak günlük serin var ve $left görev kaldı. Gün 23:59'da kapanır."
                left > 0 -> "Günü kapatmadan" to "$left görev kaldı. Küçük bir tanesini şimdi bitir."
                else -> "İyi geceler" to NIGHT.pick()
            }
        }
    }

    private fun formatL(v: Double, tr: Boolean): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else if (tr) v.toString().replace('.', ',') else v.toString()
}
