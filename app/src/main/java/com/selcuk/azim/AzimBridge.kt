package com.selcuk.azim

import android.webkit.JavascriptInterface
import com.selcuk.azim.health.HcState

/**
 * JavaScript <-> Android köprüsü. Web arayüzü bu nesneye window.AzimNative olarak erişir.
 * Tüm veriler cihazdaki Room veritabanında saklanır; uygulama kapanıp açıldığında
 * kullanıcı kaldığı yerden devam eder. Android Otomatik Yedekleme de bu dosyayı kapsar.
 */
class AzimBridge(private val activity: MainActivity, private val vm: MainViewModel) {

    @JavascriptInterface
    fun get(key: String): String? = vm.get(key)

    @JavascriptInterface
    fun set(key: String, value: String) {
        vm.set(key, value)
    }

    @JavascriptInterface
    fun remove(key: String) {
        vm.remove(key)
    }

    @JavascriptInterface
    fun remindersEnabled(): Boolean = Reminders.isEnabled(activity)

    @JavascriptInterface
    fun setReminders(on: Boolean) {
        Reminders.setEnabled(activity, on)
    }

    @JavascriptInterface
    fun hasNotifPermission(): Boolean = activity.hasNotifPermission()

    @JavascriptInterface
    fun requestNotifPermission() {
        activity.runOnUiThread { activity.askNotifPermission() }
    }

    /** Bildirimleri ve widget'ı kişiselleştirmek için günlük özet (isim, dil, seri, bugünkü görevler). */
    @JavascriptInterface
    fun syncSummary(json: String) {
        Reminders.saveSummary(activity, json)
        AzimWidget.updateAll(activity)
    }

    /** Uyanış/uyku saatine ve görev saatlerine göre hesaplanmış bildirim planı. */
    @JavascriptInterface
    fun setSchedule(json: String) {
        Reminders.saveSchedule(activity, json)
    }

    /** Widget'tan işaretlenen görevler (bugüne ait olanlar). */
    @JavascriptInterface
    fun takeWidgetActions(): String = AzimWidget.takeActions(activity)

    @JavascriptInterface
    fun isPremium(): Boolean = activity.billing.isPremium()

    @JavascriptInterface
    fun loadProducts() {
        activity.billing.loadProducts()
    }

    @JavascriptInterface
    fun buy(plan: String) {
        activity.billing.buy(plan)
    }

    @JavascriptInterface
    fun restorePurchases() {
        activity.billing.start()
    }

    @JavascriptInterface
    fun manageSubscription() {
        activity.billing.manage()
    }

    @JavascriptInterface
    fun share(text: String) {
        activity.runOnUiThread { activity.shareText(text) }
    }

    /** Profil ekranındaki "Örnek bildirim gönder" butonu: su hatırlatmasını hemen gösterir. */
    @JavascriptInterface
    fun testNotification() {
        Reminders.testNotification(activity)
    }

    // ---------- Health Connect ----------
    @JavascriptInterface
    fun healthState(): String = when (vm.hcState) {
        HcState.READY -> "ready"
        HcState.NO_PERMISSION -> "noperm"
        HcState.NEEDS_UPDATE -> "update"
        HcState.UNAVAILABLE -> "unavailable"
    }

    @JavascriptInterface
    fun healthDays(): String = vm.healthJson()

    @JavascriptInterface
    fun requestHealth() {
        activity.runOnUiThread { activity.requestHealth() }
    }

    // ---------- Firebase Authentication ----------
    @JavascriptInterface
    fun authAvailable(): Boolean = vm.fbAvailable

    @JavascriptInterface
    fun auth(op: String, email: String, pass: String) {
        vm.auth(op, email, pass)
    }

    @JavascriptInterface
    fun currentEmail(): String = vm.currentEmail()

    @JavascriptInterface
    fun signOut() {
        vm.signOut()
    }

    @JavascriptInterface
    fun appVersion(): String = BuildConfig.VERSION_NAME

}
