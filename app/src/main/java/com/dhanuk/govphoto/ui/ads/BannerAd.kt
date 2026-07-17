package com.dhanuk.govphoto.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.ads.AdsRepository
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import dagger.hilt.android.EntryPointAccessors

/**
 * Banner ad slot.
 *
 *  - Transparent container background so it never paints a white box over app
 *    content; the ad creative itself fills the area only when an ad loads.
 *  - Collapses to 0dp when no ad is loaded (initial load + no-fill), so the
 *    bottom of the screen is never covered by empty white space.
 *  - Respects UMP consent via [com.google.android.ump.ConsentInformation.canRequestAds].
 *  - AdView lifecycle (destroy) is bound to composition via DisposableEffect.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // Skip entirely in debug or when ad-free
    if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
    val context = LocalContext.current
    val adsRepository = remember {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AdsEntryPoint::class.java,
            ).adsRepository()
        }.getOrNull()
    }
    val isAdFree by (adsRepository?.isAdFree?.collectAsState() ?: remember { mutableStateOf(true) })
    if (isAdFree) return

    // UMP consent gate: do not request ads until the user has consented.
    val canRequestAds = remember {
        runCatching {
            com.google.android.ump.UserMessagingPlatform
                .getConsentInformation(context.applicationContext).canRequestAds()
        }.getOrDefault(false)
    }
    if (!canRequestAds) return

    var adLoaded by remember { mutableStateOf(false) }
    val adView = remember { AdView(context.applicationContext) }

    DisposableEffect(adView) {
        adView.apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT
            // Transparent container so no white box shows behind/around the ad.
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() { adLoaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { adLoaded = false }
            }
            loadAd(AdRequest.Builder().build())
        }
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(if (adLoaded) Modifier.height(50.dp) else Modifier.height(0.dp)),
        factory = { adView }
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdsEntryPoint {
    fun adsRepository(): AdsRepository
}