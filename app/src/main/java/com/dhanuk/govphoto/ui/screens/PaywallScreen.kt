package com.dhanuk.govphoto.ui.screens

import androidx.compose.runtime.Composable
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.Paywall
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.ui.revenuecatui.PaywallOptions

/**
 * Paywall screen rendered by the RevenueCat dashboard paywall.
 *
 * The UI, plans and prices are configured in the RevenueCat dashboard, so no
 * local design is needed. Purchase and restore results are reported through
 * [PaywallListener] callbacks.
 */
@Composable
fun PaywallScreen(
    onNavigateBack: () -> Unit,
    onSubscribeSuccess: () -> Unit,
) {
    Paywall(
        options = PaywallOptions.Builder(dismissRequest = onNavigateBack)
            .setShouldDisplayDismissButton(true)
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(
                    customerInfo: CustomerInfo,
                    storeTransaction: StoreTransaction,
                ) {
                    onSubscribeSuccess()
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    onSubscribeSuccess()
                }
            })
            .build()
    )
}
