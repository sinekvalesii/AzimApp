package com.selcuk.azim.ui

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import com.selcuk.azim.BuildConfig
import com.selcuk.azim.billing.Billing
import com.selcuk.azim.data.done
import com.selcuk.azim.health.HcState
import com.selcuk.azim.health.HealthRepo

private const val PRIVACY_URL = "https://sinekvalesii.github.io/AzimApp/privacy.html"
private val Orange = Color(0xFFEE7A2F)
private val Muted = Color(0xFF8B837A)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val rationale = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        rationale.value = isRationale(intent)
        Reminder.schedule(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange, onPrimary = Color(0xFF140A03), background = Color(0xFF0A0908),
                    surface = Color(0xFF131110), surfaceVariant = Color(0xFF1A1816), surfaceContainer = Color(0xFF131110)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val session by vm.session.collectAsStateWithLifecycle()
                    if (session == null) AuthScreen(vm) else Home(vm)
                    if (rationale.value) PrivacyDialog { rationale.value = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isRationale(intent)) rationale.value = true
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun isRationale(i: Intent?) = i?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
        i?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
}

@Composable
private fun PrivacyDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sağlık verilerin") },
        text = {
            Text(
                "AZİM, Health Connect üzerinden yalnızca adım sayını ve nabız verini OKUR. Bu veriler adım görevini otomatik " +
                    "ilerletmek, panelde göstermek ve görev zorluğunu ayarlamak için kullanılır. Veriler cihazında saklanır; " +
                    "sunucuya gönderilmez, üçüncü taraflarla paylaşılmaz, reklam için kullanılmaz."
            )
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tamam") } },
        dismissButton = {
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }) { Text("Gizlilik politikası") }
        }
    )
}

@Composable
private fun AuthScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    val msg by vm.msg.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("AZİM", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Orange)
        Text("Sağlığını oyuna çevir.", color = Muted)
        Spacer(Modifier.height(24.dp))
        if (vm.auth.available) {
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-posta") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Şifre") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(16.dp))
            Button({ vm.signIn(email, pass) }, Modifier.fillMaxWidth()) { Text("Giriş yap") }
            OutlinedButton({ vm.signUp(email, pass) }, Modifier.fillMaxWidth()) { Text("Kayıt ol") }
            TextButton({ vm.reset(email) }) { Text("Şifremi unuttum") }
        } else {
            Text("Hesap girişi bu derlemede yapılandırılmamış. Misafir olarak devam edebilirsin.", color = Muted)
            Spacer(Modifier.height(16.dp))
        }
        OutlinedButton({ vm.auth.guest() }, Modifier.fillMaxWidth()) { Text("Misafir olarak devam et") }
        msg?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        Text("Kayıt olarak Gizlilik Politikası'nı kabul edersin.", Modifier.padding(top = 12.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Home(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.msg.value = null } }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                listOf("Bugün" to Icons.Filled.Home, "Sağlık" to Icons.Filled.Favorite, "Profil" to Icons.Filled.Person)
                    .forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(tab == i, { tab = i }, { Icon(icon, label) }, label = { Text(label) })
                    }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tab) {
                0 -> TodayTab(s, vm)
                1 -> HealthTab(s, vm)
                else -> ProfileTab(vm)
            }
        }
    }
}

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

@Composable
private fun Section(title: String) =
    Text(title.uppercase(), Modifier.padding(top = 20.dp, bottom = 8.dp), color = Muted, style = MaterialTheme.typography.labelMedium)

@Composable
private fun TodayTab(s: UiState, vm: MainViewModel) {
    val done = s.quests.count { it.log.done }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SEVİYE ${s.level}", color = Orange, style = MaterialTheme.typography.labelLarge)
                    Text("${s.xp} XP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                Text("🔥 ${s.streak} gün", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator({ s.levelProgress }, Modifier.fillMaxWidth())
            Text("Bugün $done / ${s.quests.size} görev", Modifier.padding(top = 8.dp), color = Muted)
        }
    }
    if (s.fatigued) Text(
        "Ortalama nabzın son günlere göre yüksek. Bugün hedefler artırılmadı; dinlenmeye öncelik ver.",
        Modifier.padding(top = 12.dp), color = Muted
    )
    Section("Günlük görevler")
    s.quests.forEach { q ->
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(q.quest.title, fontWeight = FontWeight.Bold)
                    Text(
                        "${fmt(q.log.progress)} / ${fmt(q.log.target)} ${q.quest.unit} · +${q.quest.xp} XP" +
                            if (q.quest.auto && s.hc == HcState.READY) " · otomatik" else "",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        { (q.log.progress / q.log.target).toFloat().coerceIn(0f, 1f) },
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                TextButton({ vm.step(q, -1) }) { Text("−") }
                TextButton({ vm.step(q, 1) }) { Text("+") }
                Checkbox(q.log.done, { vm.toggle(q) })
            }
        }
    }
    Text(
        "Hedefler son 7 günlük performansına göre otomatik artar ya da azalır.",
        Modifier.padding(top = 8.dp), color = Muted, style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun HealthTab(s: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
    Text("Sağlık", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    if (s.hc != HcState.READY) {
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Health Connect bağlantısı", fontWeight = FontWeight.Bold)
                Text(
                    when (s.hc) {
                        HcState.NO_PERMISSION -> "Adım ve nabız verini otomatik çekmek için izin ver. AZİM bu verileri yalnızca okur."
                        HcState.NEEDS_UPDATE -> "Health Connect uygulamasını yükle ya da güncelle."
                        else -> "Bu cihaz Health Connect'i desteklemiyor. Görevleri elle işaretleyebilirsin."
                    }, Modifier.padding(vertical = 8.dp), color = Muted
                )
                if (s.hc == HcState.NO_PERMISSION) Button({ perm.launch(HealthRepo.PERMS) }) { Text("İzin ver") }
                if (s.hc == HcState.NEEDS_UPDATE) Button({
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri()))
                    }
                }) { Text("Health Connect'i aç") }
            }
        }
        return
    }
    val today = s.week.lastOrNull()
    val stepGoal = s.quests.find { it.quest.id == "steps" }?.log?.target ?: 6000.0
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.weight(1f)) {
            Column(Modifier.padding(14.dp)) {
                Text("ADIM", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text("${today?.steps ?: 0}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                LinearProgressIndicator({ ((today?.steps ?: 0) / stepGoal).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text("Hedef ${fmt(stepGoal)}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.weight(1f)) {
            Column(Modifier.padding(14.dp)) {
                Text("ORT. NABIZ", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(today?.hr?.let { "$it" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("atım/dk · bugün", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val premium by vm.billing.premium.collectAsStateWithLifecycle()
    if (!premium) {
        Section("Detaylı analiz")
        PlusCard(vm, "7 günlük adım ve görev grafiği ile sağlık-başarı ilişkisi analizi AZİM Plus'ta.")
        return
    }
    Section("Son 7 gün: adım (çubuk) ve görev tamamlama (nokta)")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            val max = (s.week.maxOfOrNull { it.steps } ?: 0L).coerceAtLeast(1L).toFloat()
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val slot = size.width / s.week.size
                s.week.forEachIndexed { i, d ->
                    val h = size.height * (d.steps / max)
                    drawRect(Orange.copy(alpha = 0.75f), Offset(i * slot + slot * 0.2f, size.height - h), Size(slot * 0.6f, h))
                    d.rate?.let { drawCircle(Color(0xFF62D08E), 6.dp.toPx(), Offset(i * slot + slot / 2, (size.height * (1 - it)).coerceIn(6.dp.toPx(), size.height - 6.dp.toPx()))) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                s.week.forEach { Text(it.date.takeLast(2), Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    Section("Sağlık ve başarı ilişkisi")
    Card(Modifier.fillMaxWidth()) { Text(s.insight, Modifier.padding(14.dp)) }
    Text(
        "AZİM tıbbi bir cihaz değildir; gösterilen bilgiler teşhis ya da tedavi amacı taşımaz.",
        Modifier.padding(top = 12.dp), color = Muted, style = MaterialTheme.typography.bodySmall
    )
}

/** Abonelik kartı: fiyatlar Google Play'den okunur; ürünler henüz yoksa butonlar bilgi mesajı gösterir. */
@Composable
private fun PlusCard(vm: MainViewModel, text: String) {
    val ctx = LocalContext.current
    val prices by vm.billing.prices.collectAsStateWithLifecycle()
    fun buy(id: String) {
        if ((ctx as? Activity)?.let { vm.billing.buy(it, id) } != true) vm.msg.value = "Mağaza bağlantısı şu an kurulamadı. Biraz sonra tekrar dene."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("AZİM Plus", color = Orange, fontWeight = FontWeight.Black)
            Text(text, Modifier.padding(vertical = 8.dp), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ buy(Billing.MONTHLY) }, Modifier.weight(1f)) { Text("Aylık ${prices[Billing.MONTHLY] ?: ""}") }
                Button({ buy(Billing.YEARLY) }, Modifier.weight(1f)) { Text("Yıllık ${prices[Billing.YEARLY] ?: ""}") }
            }
            Text("Google Play üzerinden, istediğin zaman iptal edebilirsin.", Modifier.padding(top = 6.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Text("Profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(session?.email ?: "Misafir", fontWeight = FontWeight.Bold)
            Text("Görev ve sağlık verilerin bu cihazda saklanır.", color = Muted)
        }
    }
    Spacer(Modifier.height(12.dp))
    val premium by vm.billing.premium.collectAsStateWithLifecycle()
    if (premium) {
        Card(Modifier.fillMaxWidth()) { Text("AZİM Plus aktif. Teşekkürler!", Modifier.padding(16.dp), color = Orange, fontWeight = FontWeight.Bold) }
        TextButton({
            ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/account/subscriptions?package=${ctx.packageName}".toUri()))
        }) { Text("Aboneliği yönet") }
    } else PlusCard(vm, "Tüm görevler, uyarlanabilir zorluk ve sağlık özeti ücretsiz. Plus, detaylı analizi açar ve geliştirmeyi destekler.")
    Spacer(Modifier.height(12.dp))
    OutlinedButton({ vm.auth.signOut() }, Modifier.fillMaxWidth()) { Text("Çıkış yap") }
    OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }, Modifier.fillMaxWidth()) { Text("Gizlilik politikası") }
    TextButton({ confirm = true }) { Text("Hesabı ve tüm verileri sil", color = MaterialTheme.colorScheme.error) }
    Text("AZİM ${BuildConfig.VERSION_NAME} · Yapım: selcukyus", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Emin misin?") },
        text = { Text("Hesabın ve cihazdaki tüm ilerleme kalıcı olarak silinir.") },
        confirmButton = { TextButton({ confirm = false; vm.deleteAccount() }) { Text("Sil") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Vazgeç") } }
    )
}
