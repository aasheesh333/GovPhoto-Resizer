package com.dhanuk.govphoto

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
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

        // A CoroutineExceptionHandler that swallows any uncaught exception so a
        // misbehaving SDK call on a background thread can't crash the whole app.
        // Third-party SDKs (OneSignal) must never take down the app.
        val silentHandler = CoroutineExceptionHandler { _, _ -> /* best-effort SDK init */ }

        val entryPoint = EntryPointAccessors.fromApplication(this, GovPhotoAppEntryPoint::class.java)

        // OneSignal — init in background to avoid blocking onCreate. Best-effort.
        CoroutineScope(Dispatchers.IO + SupervisorJob() + silentHandler).launch {
            runCatching { entryPoint.pushRepository().init() }
        }
    }
}
