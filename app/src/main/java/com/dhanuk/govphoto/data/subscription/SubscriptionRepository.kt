package com.dhanuk.govphoto.data.subscription

import android.app.Activity
import android.content.Context
import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferings
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.purchasePackageWithPromoOfferDialog
import com.revenuecat.purchases.restorePurchases
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
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
            Purchases.sharedInstance.customerInfoFlow
                .onEach { info -> applyCustomerInfo(info) }
                .launchIn(scope)
        }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val pro = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        _isPro.value = pro
        scope.launch { cachedStore.setCachedIsPro(pro) }
    }

    suspend fun loadOfferings(): Offerings = withContext(Dispatchers.IO) {
        Purchases.sharedInstance.getOfferings()
    }

    suspend fun purchase(activity: Activity, packageToBuy: Package): Result<CustomerInfo> =
        runCatching {
            withContext(Dispatchers.Main) {
                Purchases.sharedInstance.purchasePackageWithPromoOfferDialog(activity, packageToBuy, null)
            }.let { info ->
                applyCustomerInfo(info.first)
                info.first
            }
        }

    suspend fun restorePurchases(): Result<CustomerInfo> = runCatching {
        withContext(Dispatchers.IO) { Purchases.sharedInstance.restorePurchases() }.also { info ->
            applyCustomerInfo(info)
        }
    }
}
