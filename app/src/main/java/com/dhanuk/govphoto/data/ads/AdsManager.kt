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
import kotlinx.coroutines.CoroutineExceptionHandler
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
 *  - Verbose lifecycle logs via the "AdsManager" tag — when the banner
 *    "isn't loading", `adb logcat | grep AdsManager` is the single answer.
 *
 * Interstitials remain in [InterstitialController] (rate-limited per save).
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    enum class BannerState { Disabled, Loading, Loaded, Failed }

    private val tag = "AdsManager"

    private val _bannerState = MutableStateFlow(BannerState.Disabled)
    val bannerState: StateFlow<BannerState> = _bannerState.asStateFlow()

    private var bannerAdView: AdView? = null
    private var retryJob: Job? = null
    private var retryCount = 0
    private var destroyed = false

    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler { _, t ->
            android.util.Log.e(tag, "scope error", t)
        }
    )

    init {
        // One-line constructor snapshot so a 'banner not loading' investigation
        // starts with the truth about which variant / unit is wired.
        val summary = "init: variant=${if (BuildConfig.DEBUG) "debug" else "release"} " +
            "forceNoAds=${BuildConfig.FORCE_NO_ADS} " +
            "isAdFree=${adsRepository.isAdFree.value} " +
            "bannerUnit=${BuildConfig.ADMOB_BANNER_UNIT} " +
            "canRequestAds=${canRequestAds()}"
        android.util.Log.i(tag, summary)
    }

    private fun createBannerAdView(): AdView =
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT
            // Transparent container so no white box shows behind/around the ad.
            setBackgroundColor(Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    android.util.Log.i(tag, "onAdLoaded: unit=${BuildConfig.ADMOB_BANNER_UNIT}")
                    retryCount = 0
                    retryJob?.cancel()
                    _bannerState.value = BannerState.Loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    val msg = "onAdFailedToLoad: code=${error.code} " +
                        "domain=${error.domain} message=${error.message} " +
                        "(NO_FILL=3 means the unit does not serve ads for this app " +
                        "account; check the banner unit ID belongs to the same AdMob " +
                        "account as ${BuildConfig.ADMOB_APP_ID})"
                    android.util.Log.w(tag, msg)
                    _bannerState.value = BannerState.Failed
                    scheduleRetry()
                }
            }
        }

    private fun shouldSkipAds(): Boolean {
        val skip = BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS || adsRepository.isAdFree.value
        if (skip) {
            android.util.Log.d(
                tag,
                "shouldSkipAds=true (debug=${BuildConfig.DEBUG} " +
                    "forceNoAds=${BuildConfig.FORCE_NO_ADS} isAdFree=${adsRepository.isAdFree.value})"
            )
        }
        return skip
    }

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
        if (destroyed || shouldSkipAds() || !canRequestAds()) {
            android.util.Log.d(
                tag,
                "ensureBannerLoaded: skip " +
                    "(destroyed=$destroyed skipAds=${shouldSkipAds()} canRequest=${canRequestAds()})"
            )
            return
        }
        if (bannerAdView == null) {
            bannerAdView = createBannerAdView()
            android.util.Log.i(tag, "created shared banner AdView parent=${bannerAdView?.parent}")
        }
        if (_bannerState.value == BannerState.Loaded) return
        if (_bannerState.value == BannerState.Loading) return
        loadBanner()
    }

    private fun loadBanner() {
        retryJob?.cancel()
        _bannerState.value = BannerState.Loading
        android.util.Log.i(tag, "loadBanner: requesting unit=${BuildConfig.ADMOB_BANNER_UNIT}")
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
        android.util.Log.i(tag, "scheduleRetry attempt=$retryCount in ${delayMs}ms")
        retryJob = scope.launch {
            delay(delayMs)
            if (!destroyed && !shouldSkipAds() && canRequestAds()) loadBanner()
        }
    }

    /** Called from MainActivity once UMP consent completes so the banner can
     *  start loading without waiting for a screen to mount BannerAd. */
    fun onConsentReady() {
        android.util.Log.i(tag, "onConsentReady")
        ensureBannerLoaded()
    }

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