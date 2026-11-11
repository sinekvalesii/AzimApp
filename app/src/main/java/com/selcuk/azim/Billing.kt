package com.selcuk.azim

import android.content.Context
import android.content.Intent
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONObject

/**
 * Google Play Billing (abonelik, sunucu gerektirmez). Play Console'da şu iki abonelik ürünü oluşturulmalı:
 *   azim_plus_monthly  (öneri ₺19,99/ay)
 *   azim_plus_yearly   (öneri ₺149,99/yıl; isteğe bağlı ücretsiz deneme teklifi)
 * Ürünler oluşturulana ve uygulama bir test kanalına yüklenene kadar satın alma ekranı açılmaz.
 */
class Billing(private val act: MainActivity) : PurchasesUpdatedListener {

    private val prefs = act.getSharedPreferences("azim_billing", Context.MODE_PRIVATE)
    private var details: Map<String, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(act)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun isPremium(): Boolean = prefs.getBoolean("premium", false)

    fun start() {
        if (client.isReady) {
            refresh(); return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh(); loadProducts()
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    fun loadProducts() {
        if (!client.isReady) {
            start(); return
        }
        val products = IDS.keys.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, res ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = res.productDetailsList.associateBy { it.productId }
            val prices = JSONObject()
            details.forEach { (id, pd) ->
                // Temel planın fiyatı = son fiyatlandırma aşaması (deneme süresinden sonraki fiyat)
                val price = pd.subscriptionOfferDetails
                    ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                if (price != null) prices.put(IDS[id] ?: id, price)
            }
            act.js("window.azimProducts && window.azimProducts(${JSONObject.quote(prices.toString())})")
        }
    }

    /** key: "monthly" veya "yearly" */
    fun buy(key: String) {
        val id = IDS.entries.firstOrNull { it.value == key }?.key ?: return
        val pd = details[id]
        if (pd == null) {
            act.js("window.toast && toast(t('billing_na'))")
            loadProducts(); return
        }
        // Ücretsiz deneme gibi bir teklif varsa onu, yoksa temel planı seç
        val offer = pd.subscriptionOfferDetails?.maxByOrNull { it.pricingPhases.pricingPhaseList.size } ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            ).build()
        act.runOnUiThread { client.launchBillingFlow(act, params) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases)
    }

    fun refresh() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(list)
        }
    }

    private fun handle(list: List<Purchase>) {
        val active = list.filter { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in IDS.keys }
        }
        active.filter { !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()
            ) { }
        }
        setPremium(active.isNotEmpty())
    }

    private fun setPremium(on: Boolean) {
        val old = isPremium()
        prefs.edit { putBoolean("premium", on) }
        if (old != on) act.js("window.azimOnPremium && window.azimOnPremium($on)")
    }

    fun manage() {
        val url = "https://play.google.com/store/account/subscriptions?package=${act.packageName}"
        act.runOnUiThread { runCatching { act.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    }

    fun end() {
        runCatching { client.endConnection() }
    }

    companion object {
        val IDS = mapOf("azim_plus_monthly" to "monthly", "azim_plus_yearly" to "yearly")
    }
}
