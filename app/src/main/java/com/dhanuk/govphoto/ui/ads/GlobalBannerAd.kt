package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.data.ads.AdsManager
import dagger.hilt.android.EntryPointAccessors

/**
 * Application-wide banner ad slot. This single composable is placed once in
 * MainActivity's Scaffold bottomBar; it is the only place the shared AdView
 * is hosted, so the banner never reloads just because the screen changes.
 *
 * Behaviour:
 *  - Not composed until an ad actually loads (no white gap when no ad).
 *  - Uses an adaptive banner so the ad width matches the screen exactly and
 *    its height is the ad's natural height, avoiding any empty border.
 *  - Uses the same shared AdView from AdsManager across the whole session.
 *  - Auto-refresh and failed-reload are handled by AdsManager on a timer.
 */
@Composable
fun GlobalBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsManager = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AdEntryPoint::class.java,
            ).adsManager()
        }.getOrNull()
    } ?: return

    val state by adsManager.bannerState.collectAsState()
    if (state != AdsManager.BannerState.Loaded) return

    val adView = adsManager.getBannerAdView() ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
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
