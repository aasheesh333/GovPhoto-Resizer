package com.dhanuk.govphoto.data.ads

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
 * Single entrypoint for showing interstitials. Called from SaveSuccessScreen after a save succeeds.
 * Failures are silent (per Google's RTB-safe policy guidance).
 */
@Singleton
class InterstitialController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    private val rateLimiter = RateLimiter()
    private var loadedAd: InterstitialAd? = null

    /** Increment saveCount once after a successful save so the controller can gate. */
    fun recordSaveReceived(ms: Long = System.currentTimeMillis()) {
        rateLimiter.saveCount += 1
        rateLimiter.now = { ms }
        maybePreload()
    }

    private fun maybePreload() {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
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

    fun tryShow(activity: android.app.Activity): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return false
        if (adsRepository.isAdFree.value) return false
        val ad = loadedAd ?: return false.also { maybePreload(); }
        if (!rateLimiter.canShow(minIntervalMs = 60_000L, perSessionCap = 3, minSaveCount = 2)) return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                rateLimiter.markShown()
                maybePreload()
            }
        }
        ad.show(activity)
        return true
    }

    private var retryCount = 0
    private fun loadRetry() { if (retryCount++ > 3) return; maybePreload() }
}
