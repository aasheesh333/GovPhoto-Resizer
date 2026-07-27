package com.dhanuk.govphoto.data.ads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds ad-free state for the app. Composed of:
 *  - adStateProvider.isPro (always false since RevenueCat was removed and the
 *    app pivoted to ads-only; kept as aninterface field for future re-enablement)
 *  - adFreeUntilMs (24h ad-free reward; from SettingsRepository, Task 9 via adStateProvider)
 *  - forceNoAds (debug-only; sourced from AdStateProvider, bound to BuildConfig.DEBUG in AppModule)
 */
interface AdStateProvider {
    /** Always false in the current ads-only build. */
    val isPro: Boolean
    /** Reactive stream of the Pro state so AdsRepository would pick up changes
     *  immediately if Pro were ever re-enabled. Currently a constant false flow. */
    val isProFlow: StateFlow<Boolean>
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        refresh()
        scope.launch {
            adStateProvider.isProFlow.collect { refresh() }
        }
    }

    /** Re-read external state. Called by SettingsRepository when adFreeUntilMs
     *  changes (e.g. after a rewarded-ad reward is granted). */
    fun refresh() {
        val now = System.currentTimeMillis()
        _isAdFree.value =
            adStateProvider.forceNoAds ||
            adStateProvider.isPro ||
            (adStateProvider.adFreeUntilMs > now)
    }
}
