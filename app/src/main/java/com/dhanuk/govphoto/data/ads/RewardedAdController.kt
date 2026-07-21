package com.dhanuk.govphoto.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dhanuk.govphoto.BuildConfig
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entrypoint for showing rewarded video ads. Mirrors
 * [InterstitialController]'s policy:
 *  - preloads one rewarded ad;
 *  - retries failed loads with backoff (5s, 15s, 45s, then stops);
 *  - enforces a 2-minute cooldown between shows and a per-session cap of 5;
 *  - gated on UMP [canRequestAds] and [AdsRepository.isAdFree].
 *
 * The reward payload (a [com.google.android.gms.ads.reward.RewardItem]) is
 * reported to the caller via [onRewardEarned]; we don't unlock content in
 * this controller — the caller (typically [com.dhanuk.govphoto.ui.screens.SaveSuccessScreen])
 * decides what to unlock, e.g. a one-off "we got a save + watched" reward.
 */
@Singleton
class RewardedAdController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsRepository: AdsRepository,
) {
    private val tag = "RewardedAdController"
    private val rateLimiter = RateLimiter()
    private var loadedAd: RewardedAd? = null

    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() +
            CoroutineExceptionHandler { _, t -> Log.e(tag, "scope error", t) }
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    enum class State { Idle, Loading, Loaded, Failed, Showing }

    /** Preload the ad (no-op if disabled / already loaded). Idempotent. */
    fun preloadIfNeeded() {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return
        if (adsRepository.isAdFree.value) return
        if (loadedAd != null) return
        if (_state.value == State.Loading) return
        if (_state.value == State.Loaded) return
        load()
    }

    private var retryCount = 0
    private fun load() {
        retryCount = 0
        _state.value = State.Loading
        Log.d(tag, "load: unit=${BuildConfig.ADMOB_REWARDED_UNIT}")
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.i(tag, "onAdLoaded")
                    loadedAd = ad
                    _state.value = State.Loaded
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(tag, "onAdFailedToLoad code=${error.code} msg=${error.message}")
                    loadedAd = null
                    _state.value = State.Failed
                    scheduleRetry()
                }
            }
        )
    }

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRIES) return
        retryCount++
        val delayMs = when (retryCount) {
            1 -> 5_000L
            2 -> 15_000L
            else -> 45_000L
        }
        scope.launch {
            delay(delayMs)
            if (!BuildConfig.DEBUG && !BuildConfig.FORCE_NO_ADS) load()
        }
    }

    /**
     * Show the rewarded ad once it is loaded and the rate-limiter passes.
     * Mirrors InterstitialController.tryShow: 2-minute cooldown, every save
     * eligible, per-session cap. Falls back to a no-op (returns false) when
     * ads are disabled, no ad is loaded, or the user is rate-limited.
     *
     * @param onRewardEarned invoked on the main thread when the user has
     *        watched the ad to completion. The caller decides what to grant.
     */
    fun tryShow(activity: Activity, onRewardEarned: () -> Unit): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_NO_ADS) return false
        if (adsRepository.isAdFree.value) return false
        if (FullScreenAdLock.isShowing.value) return false
        val ad = loadedAd ?: run {
            Log.d(tag, "tryShow: no loaded ad, will preload and skip this time")
            preloadIfNeeded()
            return false
        }
        if (!rateLimiter.canShow(minIntervalMs = 120_000L, perSessionCap = 5, minSaveCount = 1)) {
            Log.d(tag, "tryShow: rate-limited (saveCount=${rateLimiter.saveCount} shownInSession=${rateLimiter.shownInSession})")
            return false
        }
        if (!FullScreenAdLock.acquire()) return false
        _state.value = State.Showing
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.i(tag, "rewarded ad dismissed")
                loadedAd = null
                rateLimiter.markShown()
                FullScreenAdLock.release()
                _state.value = State.Idle
                preloadIfNeeded()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(tag, "rewarded ad failed to show: ${error.message}")
                loadedAd = null
                FullScreenAdLock.release()
                _state.value = State.Failed
                scheduleRetry()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { rewardItem ->
            Log.i(tag, "user earned reward: type=${rewardItem.type} amount=${rewardItem.amount}")
            onRewardEarned()
        })
        return true
    }

    /** Increment saveCount once after a successful save so the rate-limiter gates. */
    fun recordSaveReceived(ms: Long = System.currentTimeMillis()) {
        rateLimiter.saveCount += 1
        rateLimiter.now = { ms }
        preloadIfNeeded()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}