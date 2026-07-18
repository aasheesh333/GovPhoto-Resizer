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
 * Central ads coordinator. Owns a SINGLE banner [AdView] for the whole app.
 *
 * Responsibilities:
 *  - Lazy-create + load the banner once.
 *  - Keep the banner in MainActivity's root, never per-screen, so navigation
 *    between screens does NOT reload it.
 *  - Auto-retry failures with long, throttling-safe intervals (30s, 60s, 120s).
 *  - Silently auto-rotate the banner every [AUTO_REFRESH_MS]. Silent refreshes
 *    keep the previous ad visible if the new request fails, so the user never
 *    sees an empty white box during a refresh.
 *  - Expose [bannerState] so the UI collapses to 0dp until an ad is loaded
 *    and expands to 50dp only when Loaded (no white gap).
 *  - App-level lifecycle (resume/pause/destroy) wired from MainActivity.
 *
 * Interstitials remain in [InterstitialController] (rate-limited per app usage).
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    enum class BannerState { Disabled, Loading, Loaded, Failed }

    data class DiagnosticInfo(
        val variant: String,
        val forceNoAds: Boolean,
        val isAdFree: Boolean,
        val appId: String,
        val bannerUnitId: String,
        val bannerState: BannerState,
        val consentStatus: String,
        val privacyOptionsRequirementStatus: String,
        val canRequestAds: Boolean,
        val lastErrorCode: Int?,
        val lastErrorMessage: String?,
        val lastErrorDomain: String?,
        val retryCount: Int,
        val destroyed: Boolean,
        val warning: String?,
    )

    private val tag = "AdsManager"

    private val _bannerState = MutableStateFlow(BannerState.Disabled)
    val bannerState: StateFlow<BannerState> = _bannerState.asStateFlow()

    private var bannerAdView: AdView? = null
    private var retryJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var retryCount = 0
    private var destroyed = false
    private var lastLoadWasSilent = false
    private var lastErrorCode: Int? = null
    private var lastErrorMessage: String? = null
    private var lastErrorDomain: String? = null

    private val _diagnosticInfo = MutableStateFlow(buildDiagnosticInfo())
    val diagnosticInfo: StateFlow<DiagnosticInfo> = _diagnosticInfo.asStateFlow()

    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler { _, t ->
            loge { "scope error: ${t.message ?: t.toString()}" }
        }
    )

    init {
        // One-line constructor snapshot so a 'banner not loading' investigation
        // starts with the truth about which variant / unit is wired.
        val variant = if (BuildConfig.DEBUG) "debug" else "release"
        val noAds = BuildConfig.FORCE_NO_ADS
        val adFree = adsRepository.isAdFree.value
        val consent = canRequestAds()
        logi { "init: variant=$variant forceNoAds=$noAds isAdFree=$adFree consent=$consent" }
        updateDiagnostic()
    }

    private fun buildDiagnosticInfo(): DiagnosticInfo {
        val demoPrefix = "ca-app-pub-3940256099942544"
        val appId = BuildConfig.ADMOB_APP_ID
        val bannerUnit = BuildConfig.ADMOB_BANNER_UNIT
        val appIsDemo = appId.startsWith(demoPrefix)
        val bannerIsDemo = bannerUnit.startsWith(demoPrefix)
        val warning = when {
            !appIsDemo && bannerIsDemo ->
                "Real app ID is used but banner unit ID is Google's demo ID. " +
                    "Set the real ADMOB_BANNER_UNIT secret."
            appIsDemo && !bannerIsDemo ->
                "Demo app ID is used but banner unit ID is real. Either both " +
                    "should be real (release) or both demo (local testing)."
            else -> null
        }
        val consentInfo = UserMessagingPlatform.getConsentInformation(context)
        val consentStatus = runCatching { consentInfo.consentStatus.toString() }.getOrDefault("unknown")
        val privacyStatus = runCatching {
            consentInfo.privacyOptionsRequirementStatus.toString()
        }.getOrDefault("unknown")
        return DiagnosticInfo(
            variant = if (BuildConfig.DEBUG) "debug" else "release",
            forceNoAds = BuildConfig.FORCE_NO_ADS,
            isAdFree = adsRepository.isAdFree.value,
            appId = maskId(appId),
            bannerUnitId = maskId(bannerUnit),
            bannerState = _bannerState.value,
            consentStatus = consentStatus,
            privacyOptionsRequirementStatus = privacyStatus,
            canRequestAds = canRequestAds(),
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastErrorMessage,
            lastErrorDomain = lastErrorDomain,
            retryCount = retryCount,
            destroyed = destroyed,
            warning = warning,
        )
    }

    private fun maskId(id: String): String {
        val tail = id.substringAfterLast("/", "").takeLast(6)
        return if (tail.isNotEmpty()) "...$tail" else id.take(12) + "..."
    }

    private fun updateDiagnostic() {
        _diagnosticInfo.value = buildDiagnosticInfo()
    }

    private fun createBannerAdView(): AdView =
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT
            // Transparent container so no white box shows behind/around the ad.
            setBackgroundColor(Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    logi { "onAdLoaded" }
                    retryCount = 0
                    lastErrorCode = null
                    lastErrorMessage = null
                    lastErrorDomain = null
                    lastLoadWasSilent = false
                    retryJob?.cancel()
                    _bannerState.value = BannerState.Loaded
                    updateDiagnostic()
                    scheduleAutoRefresh()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    lastErrorCode = error.code
                    lastErrorDomain = error.domain ?: ""
                    lastErrorMessage = error.message ?: ""
                    if (lastLoadWasSilent) {
                        logw { "silent refresh failed: code=$lastErrorCode domain=$lastErrorDomain message=$lastErrorMessage; keeping previous banner" }
                        lastLoadWasSilent = false
                        // Failed silent refresh doesn't collapse the UI.
                        scheduleAutoRefresh(delayMs = AUTO_REFRESH_ON_FAILURE_MS)
                    } else {
                        logw { "onAdFailedToLoad: code=$lastErrorCode domain=$lastErrorDomain message=$lastErrorMessage (NO_FILL=3 usually means the banner unit ID does not belong to this AdMob app account)" }
                        _bannerState.value = BannerState.Failed
                        updateDiagnostic()
                        scheduleRetry()
                    }
                }
            }
        }

    private fun shouldSkipAds(): Boolean {
        val skip = BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS || adsRepository.isAdFree.value
        if (skip) {
            logd { "shouldSkipAds=true debug=${BuildConfig.DEBUG} forceNoAds=${BuildConfig.FORCE_NO_ADS} adFree=${adsRepository.isAdFree.value}" }
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
        if (destroyed || shouldSkipAds()) {
            logd { "ensureBannerLoaded skip destroyed=$destroyed skipAds=${shouldSkipAds()}" }
            updateDiagnostic()
            return
        }
        if (bannerAdView == null) {
            bannerAdView = createBannerAdView()
            logi { "created shared banner AdView" }
        }
        updateDiagnostic()
        if (_bannerState.value == BannerState.Loaded) return
        if (_bannerState.value == BannerState.Loading) return
        loadBanner()
    }

    private fun loadBanner(silent: Boolean = false) {
        retryJob?.cancel()
        if (silent) {
            lastLoadWasSilent = true
            logi { "silent refresh banner" }
        } else {
            lastLoadWasSilent = false
            _bannerState.value = BannerState.Loading
            logi { "loadBanner: requesting banner" }
            updateDiagnostic()
        }
        bannerAdView?.loadAd(AdRequest.Builder().build())
    }

    private fun scheduleRetry() {
        if (destroyed) return
        retryCount++
        val delayMs = when (retryCount) {
            1 -> 30_000L
            2 -> 60_000L
            else -> 120_000L
        }
        logi { "scheduleRetry attempt=$retryCount in ${delayMs}ms" }
        retryJob = scope.launch {
            delay(delayMs)
            if (!destroyed && !shouldSkipAds()) loadBanner(silent = false)
        }
    }

    /**
     * Silently refresh the banner every [AUTO_REFRESH_MS] when it is Loaded.
     * A silent refresh keeps showing the previous ad if the new request fails,
     * so the user never sees a white box during a refresh.
     */
    private fun scheduleAutoRefresh(delayMs: Long = AUTO_REFRESH_MS) {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            delay(delayMs)
            if (!destroyed && !shouldSkipAds()) loadBanner(silent = true)
        }
    }

    /** Called from MainActivity once UMP consent completes so the banner can
     *  start loading without waiting for a screen to mount BannerAd. */
    fun onConsentReady() {
        logi { "onConsentReady" }
        ensureBannerLoaded()
    }

    /** Refresh the cached diagnostic snapshot (called from the in-app status UI). */
    fun refreshDiagnosticInfo() { updateDiagnostic() }

    /**
     * Re-run the UMP consent-info update and show the consent form if required.
     * Exposed on the Ad diagnostics dialog so a user without adb can re-prompt
     * themselves when Consent OK is false.
     */
    fun requestConsent(activity: android.app.Activity) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(context)
        val params = com.google.android.ump.ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    updateDiagnostic()
                    ensureBannerLoaded()
                }
            },
            { _ -> updateDiagnostic() }
        )
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
        autoRefreshJob?.cancel()
        bannerAdView?.destroy()
        bannerAdView = null
        _bannerState.value = BannerState.Disabled
        updateDiagnostic()
    }

    // Helpers that avoid the android.util.Log overload-resolution issues seen
    // when passing platform-typed BuildConfig/String! values directly.
    private inline fun logi(msg: () -> String) { android.util.Log.i(tag, msg()) }
    private inline fun logd(msg: () -> String) { android.util.Log.d(tag, msg()) }
    private inline fun logw(msg: () -> String) { android.util.Log.w(tag, msg()) }
    private inline fun loge(msg: () -> String) { android.util.Log.e(tag, msg()) }

    companion object {
        private const val AUTO_REFRESH_MS = 60_000L
        private const val AUTO_REFRESH_ON_FAILURE_MS = 120_000L
    }
}
