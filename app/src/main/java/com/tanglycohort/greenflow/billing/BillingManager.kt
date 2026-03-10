package com.tanglycohort.greenflow.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

object ProductIds {
    const val MONTHLY_SUB = "greenflow_monthly"
}

object OfferTags {
    const val BASE = "monthly-auto"
    const val TRIAL = "free-trial"
}

/**
 * Handles Google Play Billing: connect, query subscription products, launch purchase.
 * Only after a successful purchase should the app/server be updated (via backend verification).
 */
class BillingManager(private val context: Context) {

    /**
     * Select offer token: prefer offer with TRIAL tag, else BASE tag.
     */
    private fun selectOfferToken(productDetails: ProductDetails): String? {
        val offers = productDetails.subscriptionOfferDetails ?: return null
        val trialOffer = offers.firstOrNull { offer ->
            offer.offerTags.contains(OfferTags.TRIAL)
        }
        if (trialOffer != null) return trialOffer.offerToken
        val baseOffer = offers.firstOrNull { offer ->
            offer.offerTags.contains(OfferTags.BASE)
        }
        return baseOffer?.offerToken
    }

    @Volatile
    private var purchaseFlowCallback: ((BillingResult, List<Purchase>?) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        purchaseFlowCallback?.invoke(billingResult, purchases)
        purchaseFlowCallback = null
    }

    private var _billingClient: BillingClient? = null

    private val billingClient: BillingClient?
        get() {
            if (_billingClient == null) {
                _billingClient = BillingClient.newBuilder(context)
                    .setListener(purchasesUpdatedListener)
                    .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                            .enableOneTimeProducts()
                            .enablePrepaidPlans()
                            .build()
                    )
                    .build()
                _billingClient!!.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {}
                    override fun onBillingServiceDisconnected() {}
                })
            }
            return _billingClient
        }

    fun connect() {
        billingClient
    }

    fun endConnection() {
        purchaseFlowCallback = null
        _billingClient?.endConnection()
        _billingClient = null
    }

    private fun getClient(): BillingClient? = billingClient

    /**
     * Query current subscription purchases from Google Play. Use to detect purchases not yet synced to Supabase.
     */
    fun queryCurrentSubscriptions(callback: (List<Purchase>) -> Unit) {
        val client = getClient()
        if (client == null || !client.isReady) {
            callback(emptyList())
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            val valid = purchases?.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            } ?: emptyList()
            callback(valid)
        }
    }

    /**
     * Query product details for the monthly subscription (ProductIds.MONTHLY_SUB).
     */
    fun querySubscriptionProductDetails(callback: (ProductDetails?) -> Unit) {
        val client = getClient()
        if (client == null || !client.isReady) {
            callback(null)
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProductIds.MONTHLY_SUB)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null && productDetailsList.isNotEmpty()) {
                callback(productDetailsList[0])
            } else {
                callback(null)
            }
        }
    }

    /**
     * Launches the Google Play subscription purchase flow.
     * @param onSuccess called with Purchase when user completes purchase successfully
     * @param onError error message or null if user cancelled
     */
    fun launchSubscriptionPurchase(
        activity: Activity,
        productDetails: ProductDetails,
        onSuccess: (Purchase) -> Unit,
        onError: (String?) -> Unit
    ) {
        val client = getClient()
        if (client == null || !client.isReady) {
            onError("Billing not ready")
            return
        }
        val offerToken = selectOfferToken(productDetails)
        if (offerToken == null) {
            onError("No offer for this subscription")
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        purchaseFlowCallback = { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val purchase = purchases?.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    if (purchase != null) {
                        onSuccess(purchase)
                    } else {
                        onError(billingResult.debugMessage ?: "Purchase failed")
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> onError(null)
                else -> onError(billingResult.debugMessage ?: "Error ${billingResult.responseCode}")
            }
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /** Call after backend verified the purchase; required so Google considers the purchase consumed. */
    fun acknowledgePurchaseIfNeeded(purchase: Purchase, onDone: () -> Unit) {
        if (purchase.isAcknowledged) {
            onDone()
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        getClient()?.acknowledgePurchase(params) { _ ->
            onDone()
        } ?: onDone()
    }
}
