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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): SubscriptionRepository
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
    fun interstitialController(): com.dhanuk.govphoto.data.ads.InterstitialController
}

/**
 * Main Application class for GovPhoto Resizer.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class GovPhotoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can be done here

        // Firebase Crashlytics + Analytics. Best-effort: unit tests / Robolectric
        // may lack the INTERNET permission etc. Don't crash the app if SDK init fails.
        runCatching {
            FirebaseApp.initializeApp(this)
            // Default: collection enabled in release only.
            Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        // RevenueCat. Best-effort: validateConfiguration() throws if INTERNET
        // permission is missing (Robolectric) orapiKey blank.
        runCatching {
            com.revenuecat.purchases.Purchases.configure(
                com.revenuecat.purchases.PurchasesConfiguration.Builder(
                    this,
                    BuildConfig.REVENUECAT_API_KEY,
                ).build()
            )
        }

        val entryPoint = EntryPointAccessors.fromApplication(this, GovPhotoAppEntryPoint::class.java)
        runCatching {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                entryPoint.subscriptionRepository().bind()
            }
        }

        // OneSignal — init in background to avoid blocking onCreate. Best-effort.
        runCatching {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                entryPoint.pushRepository().init()
            }
        }
    }
}
