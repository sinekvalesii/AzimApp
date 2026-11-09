package com.selcuk.azim

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.core.content.edit

/**
 * JavaScript <-> Android köprüsü. Web arayüzü bu nesneye window.AzimNative olarak erişir.
 * Tüm veriler cihazdaki SharedPreferences içinde saklanır; uygulama kapanıp açıldığında
 * kullanıcı kaldığı yerden devam eder. Android Otomatik Yedekleme de bu dosyayı kapsar.
 */
class AzimBridge(private val activity: MainActivity) {

    private val prefs = activity.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    @JavascriptInterface
    fun get(key: String): String? = prefs.getString(key, null)

    @JavascriptInterface
    fun set(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    @JavascriptInterface
    fun remove(key: String) {
        prefs.edit { remove(key) }
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

    @JavascriptInterface
    fun appVersion(): String = BuildConfig.VERSION_NAME

    companion object {
        const val STORE = "azim_store"
    }
}
