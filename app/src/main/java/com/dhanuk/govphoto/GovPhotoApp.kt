package com.dhanuk.govphoto

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

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
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // UMP consent flow -> MobileAds.initialize()
        val consentInfo = com.google.android.ump.ConsentInformation.getInstance(this)
        val params = com.google.android.ump.ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInfo.requestConsentInfoUpdate(
            this,
            params,
            {
                if (consentInfo.isConsentFormAvailable) {
                    consentInfo.loadConsentForm { _ -> initializeMobileAds() }
                } else {
                    initializeMobileAds()
                }
            },
            { initializeMobileAds() }
        )
    }

    private fun initializeMobileAds() {
        com.google.android.gms.ads.MobileAds.initialize(this)
    }
}
