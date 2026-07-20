package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.GovPhotoAppEntryPoint
import com.dhanuk.govphoto.data.ads.AdsManager
import dagger.hilt.android.EntryPointAccessors

/**
 * Application-wide banner ad slot. Placed in each screen's [Scaffold.bottomBar] so
 * it never scrolls with content.
 *
 * Behaviour:
 *  - Hidden for Pro users (watches SubscriptionRepository.isPro).
 *  - Not composed until an ad actually loads (no white gap when no ad).
 *  - Uses an adaptive banner so the ad width matches the screen exactly and
 *    its height is the ad's natural height, avoiding any empty border.
 *  - Uses the same shared AdView from AdsManager across the whole session.
 *  - Auto-refresh and failed-reload are handled by AdsManager on a timer.
 *
 * @param applyNavBarPadding Set to false when this banner is already above a
 *        bottom navigation bar or action surface that applies its own navigation
 *        bar insets, otherwise two layers of nav-bar padding create a double gap.
 */
@Composable
fun GlobalBannerAd(
    modifier: Modifier = Modifier,
    applyNavBarPadding: Boolean = true,
) {
    val context = LocalContext.current
    val entryPoint = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                GovPhotoAppEntryPoint::class.java,
            )
        }.getOrNull()
    }
    val adsManager = entryPoint?.adsManager() ?: return
    val subscriptionRepository = entryPoint.subscriptionRepository()

    val bannerState by adsManager.bannerState.collectAsState()
    val isPro by subscriptionRepository.isPro.collectAsState()
    if (isPro || bannerState != AdsManager.BannerState.Loaded) return

    val adView = adsManager.getBannerAdView() ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(
                if (applyNavBarPadding) {
                    Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                } else {
                    Modifier
                }
            )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                // Detach from any previous host; for the global banner there
                // should be none, but keep it safe.
                adsManager.reparent(adView)
                adView
            }
        )
    }
}
