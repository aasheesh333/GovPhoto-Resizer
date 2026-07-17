package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.data.ads.AdsManager
import dagger.hilt.android.EntryPointAccessors

/**
 * Banner ad slot backed by the app-wide shared [AdsManager] AdView.
 *
 * The banner is loaded ONCE by [AdsManager] and reused across every screen —
 * navigating between screens no longer fires a fresh ad request (the previous
 * implementation created a new AdView + loadAd on every screen entry). The
 * container is transparent and collapses to 0dp until an ad is Loaded, so no
 * empty white box covers the bottom of the screen on no-fill.
 *
 * This composable only re-hosts the shared AdView; it never creates/loads/destroys
 * it — that lifecycle is owned by [AdsManager] (Activity-scoped).
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsManager = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AdsEntryPoint::class.java,
            ).adsManager()
        }.getOrNull()
    } ?: return

    val state by adsManager.bannerState.collectAsState()
    // Get the shared AdView (starts a load on first access if needed). Null when
    // ads are disabled (debug / force-no-ads / ad-free / no consent / destroyed).
    val adView = adsManager.getBannerAdView()

    if (adView == null || state != AdsManager.BannerState.Loaded) {
        // Disabled, loading, or failed -> collapse to 0dp (no white space).
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = {
            // Detach from any previous host (AdView allows one parent at a time)
            // then hand the shared instance to this screen's AndroidView.
            adsManager.reparent(adView)
            adView
        }
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdsEntryPoint {
    fun adsManager(): AdsManager
}