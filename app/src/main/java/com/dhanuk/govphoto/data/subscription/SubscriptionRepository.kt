package com.dhanuk.govphoto.data.subscription

import android.app.Activity
import android.content.Context
import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
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

    /**
     * The currently active RevenueCat App User ID. Empty until `bind()` has
     * populated it. When the SDK is on a randomly-generated identity (no
     * sign-in yet) the value still starts with `$RCAnonymousID:` — UI code
     * should treat that as anonymous.
     */
    private val _appUserId = MutableStateFlow("")
    val appUserId: StateFlow<String> = _appUserId.asStateFlow()

    /** True when the SDK is on a randomly-generated anonymous identity. */
    private val _isAnonymous = MutableStateFlow(true)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    suspend fun bind() {
        if (Purchases.isConfigured) {
            _isPro.value = cachedStore.getCachedIsPro()
            _appUserId.value = Purchases.sharedInstance.appUserID
            _isAnonymous.value = Purchases.sharedInstance.isAnonymous
            Purchases.sharedInstance.getCustomerInfo(object : com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback {
                override fun onReceived(info: CustomerInfo) = applyCustomerInfo(info)
                override fun onError(error: com.revenuecat.purchases.PurchasesError) = Unit
            })
        }
    }

    private fun applyCustomerInfo(info: CustomerInfo) {
        val pro = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        _isPro.value = pro
        // Reflect the freshly-applied identity in the exposed flows.
        if (Purchases.isConfigured) {
            _appUserId.value = Purchases.sharedInstance.appUserID
            _isAnonymous.value = Purchases.sharedInstance.isAnonymous
        }
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

    /**
     * Restore Purchases on a new device by signing the user in with the email
     * they used on the original device. RevenueCat will:
     *  - transfer any anonymous entitlements to the email-keyed account, and
     *  - on a different device, return the same Pro entitlement for the
     *    same email-keyed account.
     *
     * No Firebase / Google Sign-In required — this is the bare-metal pattern
     * documented by RevenueCat for apps without their own auth system.
     */
    suspend fun signIn(email: String): Result<CustomerInfo> = runCatching {
        val cleanedEmail = email.trim()
        require(cleanedEmail.isNotEmpty()) { "Email is required" }
        require(Purchases.isConfigured) { "RevenueCat is not configured" }
        val result = withContext(Dispatchers.IO) {
            Purchases.sharedInstance.awaitLogIn(cleanedEmail)
        }
        applyCustomerInfo(result.customerInfo)
        result.customerInfo
    }

    /**
     * Sign the current user out of RevenueCat. The SDK resets to a fresh
     * anonymous identity on the next operation. We keep the locally cached
     * Pro entitlement intact (the user may still be entitled on this device
     * until RevenueCat confirms otherwise).
     */
    suspend fun signOut(): Result<CustomerInfo> = runCatching {
        val result = withContext(Dispatchers.IO) {
            Purchases.sharedInstance.awaitLogOut()
        }
        applyCustomerInfo(result)
        result
    }
}
