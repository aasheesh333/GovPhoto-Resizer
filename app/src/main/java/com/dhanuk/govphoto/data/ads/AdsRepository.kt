package com.dhanuk.govphoto.data.ads

import android.content.Context
import com.dhanuk.govphoto.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds ad-free state for the app. Composed of:
 *  - SubscriptionRepository.isPro (wired in Task 4 via isProProvider)
 *  - adFreeUntilMs (24h ad-free reward; from SettingsRepository, Task 9 via adStateProvider)
 *  - FORCE_NO_ADS BuildConfig flag (debug-only)
 */
interface AdStateProvider {
    /** False when subscriptions have not been wired yet. */
    val isPro: Boolean
    /** ms epoch; 0 if no reward active. */
    val adFreeUntilMs: Long
    /** True if BuildConfig.DEBUG (forces no-ads in debug screenshots/review). */
    val forceNoAds: Boolean
}

@Singleton
class AdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adStateProvider: AdStateProvider,
) {
    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    init {
        refresh()
    }

    /** Re-read external state. Called by SubscriptionRepository after a purchase
     *  completes and by SettingsRepository when adFreeUntilMs changes. */
    fun refresh() {
        val now = System.currentTimeMillis()
        _isAdFree.value =
            BuildConfig.FORCE_NO_ADS ||
            adStateProvider.forceNoAds ||
            adStateProvider.isPro ||
            (adStateProvider.adFreeUntilMs > now)
    }
}
