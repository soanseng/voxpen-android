package com.voxink.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PurchasesUpdatedListener {
        private val _proStatus = MutableStateFlow<ProStatus>(ProStatus.Free)
        val proStatus: StateFlow<ProStatus> = _proStatus.asStateFlow()

        private var billingClient: BillingClient? = null
        private var productDetails: ProductDetails? = null

        fun initialize() {
            billingClient =
                BillingClient
                    .newBuilder(context)
                    .setListener(this)
                    .enablePendingPurchases()
                    .build()

            billingClient?.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            Timber.d("Billing client connected")
                            queryExistingPurchases()
                            queryProductDetails()
                        } else {
                            Timber.w("Billing setup failed: ${result.debugMessage}")
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Timber.d("Billing service disconnected")
                    }
                },
            )
        }

        private fun queryExistingPurchases() {
            val params =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

            billingClient?.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val hasPro =
                        purchases.any { purchase ->
                            purchase.products.contains(PRODUCT_ID_PRO) &&
                                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                    _proStatus.value = if (hasPro) ProStatus.Pro else ProStatus.Free
                    Timber.d("Pro status: ${_proStatus.value}")
                }
            }
        }

        private fun queryProductDetails() {
            val product =
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(PRODUCT_ID_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(listOf(product))
                    .build()

            billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = detailsList.firstOrNull()
                    Timber.d("Product details loaded: ${productDetails?.name}")
                }
            }
        }

        fun launchPurchaseFlow(activity: Activity): Boolean {
            val details = productDetails ?: return false
            val client = billingClient ?: return false

            val flowParams =
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams
                                .newBuilder()
                                .setProductDetails(details)
                                .build(),
                        ),
                    ).build()

            val result = client.launchBillingFlow(activity, flowParams)
            return result.responseCode == BillingClient.BillingResponseCode.OK
        }

        fun restorePurchases() {
            queryExistingPurchases()
        }

        override fun onPurchasesUpdated(
            result: BillingResult,
            purchases: MutableList<Purchase>?,
        ) {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { purchase ->
                        if (purchase.products.contains(PRODUCT_ID_PRO) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        ) {
                            _proStatus.value = ProStatus.Pro
                            Timber.d("Pro purchased successfully")
                        }
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    Timber.d("Purchase cancelled by user")
                }
                else -> {
                    Timber.w("Purchase failed: ${result.debugMessage}")
                }
            }
        }

        fun debugOverrideProStatus(isPro: Boolean) {
            if (!com.voxink.app.BuildConfig.DEBUG) return
            _proStatus.value = if (isPro) ProStatus.Pro else ProStatus.Free
            Timber.d("Debug override pro status: ${_proStatus.value}")
        }

        fun destroy() {
            billingClient?.endConnection()
            billingClient = null
        }

        companion object {
            const val PRODUCT_ID_PRO = "voxink_pro"
        }
    }
