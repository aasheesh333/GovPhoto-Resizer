package com.dhanuk.govphoto

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GovPhotoAppEntryPoint {
    fun subscriptionRepository(): SubscriptionRepository
    fun pushRepository(): com.dhanuk.govphoto.data.push.PushRepository
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

        // Firebase Crashlytics + Analytics
        FirebaseApp.initializeApp(this)
        // Default: collection enabled in release only; this is belt-and-suspenders
        // and matches the crashlyticsCollectionEnabled default. Override via manifest
        // or here for explicit control in the future.
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // RevenueCat
        com.revenuecat.purchases.Purchases.configure(
            com.revenuecat.purchases.Purchases.Configuration.Builder(
                this,
                BuildConfig.REVENUECAT_API_KEY,
            ).build()
        )

        val entryPoint = EntryPointAccessors.fromApplication(this, GovPhotoAppEntryPoint::class.java)
        entryPoint.subscriptionRepository().bind()

        // OneSignal — init in background to avoid blocking onCreate
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            entryPoint.pushRepository().init()
        }

    }
}
