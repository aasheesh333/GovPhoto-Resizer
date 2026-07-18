package com.dhanuk.govphoto.data.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prevents full-screen interstitial and rewarded ads from stacking on top of
 * each other. Both controllers check this lock before calling `show()` and clear
 * it when the ad dismisses/fails to show.
 */
object FullScreenAdLock {
    private val _showing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _showing.asStateFlow()

    /** Try to acquire the lock. Returns true if this caller may show an ad. */
    @Synchronized
    fun acquire(): Boolean {
        if (_showing.value) return false
        _showing.value = true
        return true
    }

    /** Release the lock after the ad dismisses or fails to show. */
    @Synchronized
    fun release() {
        _showing.value = false
    }
}
