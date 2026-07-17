package com.dhanuk.govphoto.data.ads

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import com.dhanuk.govphoto.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central ads coordinator. Owns a SINGLE banner [AdView] for the whole app so the
 * banner is loaded ONCE and reused across screens — navigating between screens no
 * longer fires a fresh ad request each time (the previous per-screen BannerAd
 * created a new AdView + loadAd on every screen entry).
 *
 * Responsibilities:
 *  - Lazy-create + load the banner once, gated on UMP [canRequestAds] and
 *    [AdsRepository.isAdFree].
 *  - Retry failed loads with increasing backoff (5s, 15s, 45s), then stop.
 *  - Expose [bannerState] so the UI can collapse to 0dp until an ad is loaded
 *    (no empty white box) and expand to 50dp only when Loaded.
 *  - App-level lifecycle (resume/pause/destroy) wired from MainActivity.
 *
 * Interstitials remain in [InterstitialController] (rate-limited per save).
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    enum class BannerState { Disabled, Loading, Loaded, Failed }

    private val _bannerState = MutableStateFlow(BannerState.Disabled)
    val bannerState: StateFlow<BannerState> = _bannerState.asStateFlow()

    private var bannerAdView: AdView? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    private var destroyed = false

    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler { _, t ->
            android.util.Log.e("AdsManager", "scope error", t)
        }
    )

    private fun createBannerAdView(): AdView =
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT
            // Transparent container so no white box shows behind/around the ad.
            setBackgroundColor(Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    retryCount = 0
                    retryJob?.cancel()
                    _bannerState.value = BannerState.Loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _bannerState.value = BannerState.Failed
                    scheduleRetry()
                }
            }
        }

    private fun shouldSkipAds(): Boolean =
        BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS || adsRepository.isAdFree.value

    private fun canRequestAds(): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }.getOrDefault(false)

    /**
     * Returns the shared banner AdView, starting a load if needed. Null when ads
     * are disabled (debug / force-no-ads / ad-free / no consent / destroyed), in
     * which case the caller renders nothing. The same AdView instance is returned
     * to every screen so the banner is not reloaded on navigation.
     */
    @Synchronized
    fun getBannerAdView(): AdView? {
        if (shouldSkipAds() || destroyed) return null
        ensureBannerLoaded()
        return bannerAdView
    }

    /** Kick off the banner load if not already loaded/loading. Cheap when loaded. */
    @Synchronized
    fun ensureBannerLoaded() {
        if (destroyed || shouldSkipAds() || !canRequestAds()) return
        if (bannerAdView == null) bannerAdView = createBannerAdView()
        if (_bannerState.value == BannerState.Loaded) return
        if (_bannerState.value == BannerState.Loading) return
        loadBanner()
    }

    private fun loadBanner() {
        retryJob?.cancel()
        _bannerState.value = BannerState.Loading
        bannerAdView?.loadAd(AdRequest.Builder().build())
    }

    private fun scheduleRetry() {
        if (destroyed || retryCount >= MAX_RETRIES) return
        retryCount++
        val delayMs = when (retryCount) {
            1 -> 5_000L
            2 -> 15_000L
            else -> 45_000L
        }
        retryJob = scope.launch {
            delay(delayMs)
            if (!destroyed && !shouldSkipAds() && canRequestAds()) loadBanner()
        }
    }

    /** Called from MainActivity once UMP consent completes so the banner can
     *  start loading without waiting for a screen to mount BannerAd. */
    fun onConsentReady() { ensureBannerLoaded() }

    /** Detach the shared AdView from any prior parent so a new screen's AndroidView
     *  can re-host it. AdView allows only one parent at a time. */
    fun reparent(adView: AdView) {
        (adView.parent as? ViewGroup)?.removeView(adView)
    }

    fun resume() { bannerAdView?.resume() }
    fun pause() { bannerAdView?.pause() }

    /** Tear down the banner + cancel retries. Call from Activity.onDestroy. */
    fun destroy() {
        destroyed = true
        retryJob?.cancel()
        bannerAdView?.destroy()
        bannerAdView = null
        _bannerState.value = BannerState.Disabled
    }

    companion object {
        private const val MAX_RETRIES = 3
    }
}