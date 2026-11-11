package com.selcuk.azim.billing

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Google Play abonelikleri (sunucu gerektirmez, ücretsiz altyapı).
 * Play Console'da şu iki abonelik oluşturulmalı; fiyatlar oradan okunur:
 *   azim_plus_monthly (öneri: ₺19,99/ay)  ·  azim_plus_yearly (öneri: ₺149,99/yıl)
 */
class Billing(ctx: Context) : PurchasesUpdatedListener {
    private val prefs = ctx.getSharedPreferences("billing", Context.MODE_PRIVATE)
    val premium = MutableStateFlow(prefs.getBoolean("premium", false))
    /** ürün kimliği -> Play'den gelen yerel fiyat metni */
    val prices = MutableStateFlow<Map<String, String>>(emptyMap())
    private var details: Map<String, ProductDetails> = emptyMap()

    private val client = BillingClient.newBuilder(ctx).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()

    /** Bağlanır, ürünleri ve mevcut abonelikleri sorgular. Her açılışta çağrılması güvenlidir. */
    fun refresh() {
        if (client.isReady) return query()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) query()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun query() {
        val products = IDS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { r, res ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = res.productDetailsList.associateBy { it.productId }
            prices.value = details.mapNotNull { (id, pd) ->
                pd.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice?.let { id to it }
            }.toMap()
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(list)
        }
    }

    /** Satın alma ekranını açar. Ürün henüz yüklenmediyse false döner. */
    fun buy(activity: Activity, id: String): Boolean {
        val pd = details[id] ?: run { refresh(); return false }
        val offer = pd.subscriptionOfferDetails?.firstOrNull() ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(offer.offerToken).build())
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases)
    }

    private fun handle(list: List<Purchase>) {
        val active = list.filter { p -> p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in IDS } }
        active.filter { !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) { }
        }
        premium.value = active.isNotEmpty()
        prefs.edit { putBoolean("premium", active.isNotEmpty()) }
    }

    companion object {
        const val MONTHLY = "azim_plus_monthly"
        const val YEARLY = "azim_plus_yearly"
        val IDS = listOf(MONTHLY, YEARLY)
    }
}
