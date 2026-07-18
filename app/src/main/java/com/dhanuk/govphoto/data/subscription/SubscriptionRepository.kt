package com.dhanuk.govphoto.data.subscription

import android.app.Activity
import android.content.Context
import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val cachedStore: CachedIsProStore,
) {
    companion object {
        const val ENTITLEMENT_ID = "pro"
        private const val REWARD_REPOS_SCOPE_TAG = "SubscriptionRepo"
    }

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    suspend fun bind() {
        if (Purchases.isConfigured) {
            _isPro.value = cachedStore.getCachedIsPro()
            Purchases.sharedInstance.getCustomerInfo(object : com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback {
                override fun onReceived(info: CustomerInfo) = applyCustomerInfo(info)
                override fun onError(error: com.revenuecat.purchases.PurchasesError) = Unit
            })
        }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val pro = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        _isPro.value = pro
        scope.launch { cachedStore.setCachedIsPro(pro) }
    }

    suspend fun loadOfferings(): Offerings = withContext(Dispatchers.IO) {
        Purchases.sharedInstance.awaitOfferings()
    }

    suspend fun purchase(activity: Activity, packageToBuy: Package): Result<CustomerInfo> =
        runCatching {
            val result = withContext(Dispatchers.Main) {
                Purchases.sharedInstance.awaitPurchase(
                    com.revenuecat.purchases.PurchaseParams.Builder(activity, packageToBuy).build()
                )
            }
            applyCustomerInfo(result.customerInfo)
            result.customerInfo
        }

    suspend fun restorePurchases(): Result<CustomerInfo> = runCatching {
        withContext(Dispatchers.IO) { Purchases.sharedInstance.awaitRestore() }.also { info ->
            applyCustomerInfo(info)
        }
    }

    /**
     * Attach contact details (email, phone) to the current RevenueCat app user so
     * the developer can identify the buyer from the RevenueCat dashboard.
     * Safe no-op when RevenueCat isn't configured yet (e.g. fresh install before
     * `bind()` completes).
     */
    suspend fun setUserContact(email: String?, phone: String?) {
        if (!Purchases.isConfigured) return
        val attrs = mutableMapOf<String, String>()
        email?.trim()?.takeIf { it.isNotEmpty() }?.let { attrs["\$email"] = it }
        phone?.trim()?.takeIf { it.isNotEmpty() }?.let { attrs["\$phoneNumber"] = it }
        if (attrs.isNotEmpty()) {
            runCatching {
                Purchases.sharedInstance.setAttributes(attrs)
            }.onFailure { android.util.Log.w(REWARD_REPOS_SCOPE_TAG, "setAttributes failed: ${it.message}") }
        }
    }
}
