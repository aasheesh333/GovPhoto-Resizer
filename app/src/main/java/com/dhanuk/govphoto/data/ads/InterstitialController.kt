package com.dhanuk.govphoto.data.ads

import android.app.Activity
import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Rate limiter state — survives across the controller instance. */
class RateLimiter(
    var now: () -> Long = { System.currentTimeMillis() },
    var lastShowMs: Long = 0L,
    var shownInSession: Int = 0,
    var saveCount: Int = 0,               // Incremented by SettingsRepository.saveCount
) {
    /** Returns true if all policy gates pass for "show an interstitial now". */
    fun canShow(minIntervalMs: Long, perSessionCap: Int, minSaveCount: Int): Boolean {
        if (saveCount < minSaveCount) return false
        if (shownInSession >= perSessionCap) return false
        if (lastShowMs > 0L && now() - lastShowMs < minIntervalMs) return false
        return true
    }

    /** Record that we showed an ad right now; advance the counters. */
    fun markShown() {
        lastShowMs = now()
        shownInSession += 1
    }
}

/**
 * Single entrypoint for showing interstitials.
 *
 * Two independent paths:
 *  - [tryShow] / [recordSaveReceived]: legacy save-triggered path, kept gated per
 *    the 2-minute cooldown and 5/session cap.
 *  - [tryShowAppUsage]: periodic path driven by MainActivity foreground time
 *    (first ad shown after [APP_USAGE_INITIAL_DELAY_MS], then every
 *    [APP_USAGE_INTERVAL_MS]).
 *
 * Failures are silent (per Google's RTB-safe policy guidance).
 */
@Singleton
class InterstitialController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    private val saveRateLimiter = RateLimiter()
    private val appUsageRateLimiter = RateLimiter()
    private var loadedAd: InterstitialAd? = null
    private val createdAtMs = System.currentTimeMillis()

    /** Preload the ad (no-op if disabled / already loaded / already loading). */
    fun preloadIfNeeded() = maybePreload()

    private fun maybePreload() {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
        if (adsRepository.isAdFree.value) return
        if (loadedAd != null) return
        val req = com.google.android.gms.ads.AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT,
            req,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { loadedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { loadedAd = null; loadRetry() }
            }
        )
    }

    /** Increment saveCount once after a successful save so the save-rate-limiter gates. */
    fun recordSaveReceived(ms: Long = System.currentTimeMillis()) {
        saveRateLimiter.saveCount += 1
        saveRateLimiter.now = { ms }
        maybePreload()
    }

    /**
     * Show an interstitial after a save, guarded by a 2-minute cooldown and a
     * per-session cap of 5.
     */
    fun tryShow(activity: Activity): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return false
        if (adsRepository.isAdFree.value) return false
        if (FullScreenAdLock.isShowing.value) return false
        val ad = loadedAd ?: return false.also { maybePreload() }
        if (!saveRateLimiter.canShow(minIntervalMs = 120_000L, perSessionCap = 5, minSaveCount = 1)) return false

        if (!FullScreenAdLock.acquire()) return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                saveRateLimiter.markShown()
                FullScreenAdLock.release()
                maybePreload()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadedAd = null
                FullScreenAdLock.release()
                maybePreload()
            }
        }
        ad.show(activity)
        return true
    }

    /**
     * Periodic interstitial shown while the user is actively using the app.
     * First show is delayed [APP_USAGE_INITIAL_DELAY_MS]; after that the cadence
     * is [APP_USAGE_INTERVAL_MS] while the app stays in the foreground.
     */
    fun tryShowAppUsage(activity: Activity): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return false
        if (adsRepository.isAdFree.value) return false
        if (FullScreenAdLock.isShowing.value) return false

        val now = System.currentTimeMillis()
        if (now - createdAtMs < APP_USAGE_INITIAL_DELAY_MS) return false
        if (appUsageRateLimiter.lastShowMs > 0 && now - appUsageRateLimiter.lastShowMs < APP_USAGE_INTERVAL_MS) return false

        // Try to refresh the pool if we don't have an ad ready.
        val ad = loadedAd ?: run { maybePreload(); return false }

        if (!FullScreenAdLock.acquire()) return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                appUsageRateLimiter.markShown()
                FullScreenAdLock.release()
                maybePreload()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadedAd = null
                FullScreenAdLock.release()
                maybePreload()
            }
        }
        ad.show(activity)
        return true
    }

    private var retryCount = 0
    private fun loadRetry() { if (retryCount++ > 3) return; maybePreload() }

    private companion object {
        private const val APP_USAGE_INITIAL_DELAY_MS = 120_000L
        private const val APP_USAGE_INTERVAL_MS = 180_000L
    }
}
