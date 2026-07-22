package com.dhanuk.govphoto

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): SubscriptionRepository
    fun engagementStore(): com.dhanuk.govphoto.data.subscription.EngagementStore
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
    fun interstitialController(): com.dhanuk.govphoto.data.ads.InterstitialController
}

/**
 * Main Application class for GovPhoto Resizer.
 * Annotated with @HiltAndroidApp to enable dependency injection.
 */
@HiltAndroidApp
class GovPhotoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Crashlytics + Analytics. Best-effort: unit tests / Robolectric
        // may lack the INTERNET permission etc. Don't crash the app if SDK init fails.
        runCatching {
            FirebaseApp.initializeApp(this)
            // Default: collection enabled in release only.
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        // RevenueCat — DORMANT. BillDesk KYC was rejected, so real Google Play
        // IAPs cannot be created and the app has pivoted to an ads-only model.
        // The configure() call and the bind() coroutine below are intentionally
        // skipped so the SDK stays "unconfigured" and Purchases.shared points to
        // a no-op singleton. SubscriptionRepository.isPro therefore always
        // resolves to false and all Pro-gated features are unlocked by default.
        // To re-enable: replace `false` with the runCatching{ configure(...) }
        // block below (kept commented for reference) and ensure
        // BuildConfig.REVENUECAT_API_KEY points at a live project key.
        // ---
        // val rcConfigured = runCatching {
        //     com.revenuecat.purchases.Purchases.configure(
        //         com.revenuecat.purchases.PurchasesConfiguration.Builder(
        //             this,
        //             BuildConfig.REVENUECAT_API_KEY,
        //         ).build()
        //     )
        // }.isSuccess
        val rcConfigured = false

        // A CoroutineExceptionHandler that swallows any uncaught exception so a
        // misbehaving SDK call on a background thread can't crash the whole app.
        // Third-party SDKs (RevenueCat, OneSignal) must never take down the app.
        val silentHandler = CoroutineExceptionHandler { _, _ -> /* best-effort SDK init */ }

        val entryPoint = EntryPointAccessors.fromApplication(this, GovPhotoAppEntryPoint::class.java)

        if (rcConfigured) {
            CoroutineScope(Dispatchers.IO + SupervisorJob() + silentHandler).launch {
                runCatching { entryPoint.subscriptionRepository().bind() }
            }
        }

        // OneSignal — init in background to avoid blocking onCreate. Best-effort.
        CoroutineScope(Dispatchers.IO + SupervisorJob() + silentHandler).launch {
            runCatching { entryPoint.pushRepository().init() }
        }
    }
}
