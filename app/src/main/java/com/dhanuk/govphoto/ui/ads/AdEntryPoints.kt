package com.dhanuk.govphoto.ui.ads

import com.dhanuk.govphoto.data.ads.AdsManager
import com.dhanuk.govphoto.data.ads.InterstitialController
import com.dhanuk.govphoto.data.ads.RewardedAdController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Single entry point for all ad controllers + the banner coordinator.
 * Centralising this avoids duplicating the interface across screens.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdEntryPoint {
    fun adsManager(): AdsManager
    fun interstitialController(): InterstitialController
    fun rewardedAdController(): RewardedAdController
}
