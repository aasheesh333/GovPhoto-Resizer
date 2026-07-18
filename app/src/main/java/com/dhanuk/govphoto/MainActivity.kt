package com.dhanuk.govphoto

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.datastore.DarkModePref
import com.dhanuk.govphoto.ui.navigation.GovPhotoNavHost
import com.dhanuk.govphoto.ui.components.NotificationPermissionGate
import com.dhanuk.govphoto.ui.theme.GovPhotoTheme
import com.dhanuk.govphoto.ui.theme.LocalAppLanguage
import com.dhanuk.govphoto.ui.theme.LocalHighContrast
import com.dhanuk.govphoto.ui.theme.LocalLargeButtons
import com.dhanuk.govphoto.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Main Activity - Entry point of the application.
 * Uses Jetpack Compose for UI rendering.
 *
 * Dark theme and dynamic-color flags are driven by SettingsViewModel.state
 * in real time. The persisted language Locale is applied synchronously in
 * attachBaseContext via a SharedPreferences cache so that R.string.* resolve
 * in the selected language without waiting for the async DataStore flow.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("govphoto_locale_cache", Context.MODE_PRIVATE)
        val tag = prefs.getString("language", null)
        val base = if (tag != null) applyLocale(newBase, tag) else newBase
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val adsManager = runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                AdsManagerEntryPoint::class.java,
            ).adsManager()
        }.getOrNull()

        // UMP consent flow -> MobileAds.initialize() + AdsManager.onConsentReady()
        val consentInfo = com.google.android.ump.UserMessagingPlatform.getConsentInformation(this)
        val params = com.google.android.ump.ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        consentInfo.requestConsentInfoUpdate(
            this,
            params,
            object : com.google.android.ump.ConsentInformation.OnConsentInfoUpdateSuccessListener {
                override fun onConsentInfoUpdateSuccess() {
                    com.google.android.ump.UserMessagingPlatform.loadAndShowConsentFormIfRequired(this@MainActivity) { _ ->
                        initializeMobileAds()
                        adsManager?.onConsentReady()
                    }
                }
            },
            object : com.google.android.ump.ConsentInformation.OnConsentInfoUpdateFailureListener {
                override fun onConsentInfoUpdateFailure(error: com.google.android.ump.FormError) {
                    initializeMobileAds()
                    // Try loading anyway — AdsManager.canRequestAds() will short-circuit
                    // if consent is still unavailable.
                    adsManager?.onConsentReady()
                }
            }
        )

        // Always-on uncaught handler: write last_crash.txt then delegate.
        // Helps diagnose "instant app close on Save" after process death.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("GovPhotoCrash", "Uncaught on ${thread.name}", throwable)
                val f = java.io.File(filesDir, "last_crash.txt")
                f.writeText(
                    buildString {
                        appendLine(java.util.Date().toString())
                        appendLine("Uncaught on ${thread.name}")
                        appendLine(throwable::class.java.name + ": " + throwable.message)
                        appendLine(throwable.stackTraceToString().take(4000))
                        if (BuildConfig.DEBUG) appendLine("DEBUG=true")
                    }
                )
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.state.collectAsState()
            // Resolve dark theme from pref
            val isDark = when (settings.darkMode) {
                DarkModePref.SYSTEM -> isSystemInDarkTheme()
                DarkModePref.LIGHT  -> false
                DarkModePref.DARK   -> true
            }
            // Apply locale to CompositionLocal for downstream string resolution
            CompositionLocalProvider(
                LocalAppLanguage provides settings.language,
                LocalLargeButtons provides settings.largeButtons,
                LocalHighContrast provides settings.highContrast,
            ) {
                GovPhotoTheme(darkTheme = isDark, dynamicColor = settings.dynamicColor, highContrast = settings.highContrast) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NotificationPermissionGate(settingsViewModel = settingsViewModel) {
                            GovPhotoNavHost()
                        }
                    }
                }
            }
        }
    }

    private fun initializeMobileAds() {
        com.google.android.gms.ads.MobileAds.initialize(this)
        // Single consolidated AdMob config dump so a "why is my banner not
        // filling" investigation only needs `adb logcat | grep GovPhotoAd`.
        // Banner / Interstitial / Reward units that are still Google's demo
        // IDs (3940256099942544) while the app ID is a real one is the most
        // common cause of "Ad Inspector only shows interstitial Fill".
        val demoApp = "3940256099942544"
        val isDemoApp = BuildConfig.ADMOB_APP_ID.startsWith(demoApp)
        android.util.Log.i(
            "GovPhotoAd",
            "appId=${BuildConfig.ADMOB_APP_ID} demoApp=$isDemoApp | " +
                "banner=${BuildConfig.ADMOB_BANNER_UNIT} | " +
                "interstitial=${BuildConfig.ADMOB_INTERSTITIAL_UNIT} | " +
                "reward=${BuildConfig.ADMOB_REWARDED_UNIT}",
        )
        listOf(
            "banner" to BuildConfig.ADMOB_BANNER_UNIT,
            "interstitial" to BuildConfig.ADMOB_INTERSTITIAL_UNIT,
            "reward" to BuildConfig.ADMOB_REWARDED_UNIT,
        ).forEach { (label, unit) ->
            // Units under Google's demo app have the demoApp prefix.
            if (!isDemoApp && unit.startsWith(demoApp)) {
                android.util.Log.w(
                    "GovPhotoAd",
                    "$label unit ($unit) is a Google DEMO ID but appId is real — " +
                        "this unit will NOT fill in production. Set the real unit " +
                        "ID in ADMOB_${label.uppercase()}_UNIT.",
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                AdsManagerEntryPoint::class.java,
            ).adsManager().resume()
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                AdsManagerEntryPoint::class.java,
            ).adsManager().pause()
        }
    }

    /**
     * When the app is UI-hidden under memory pressure, pause the banner ad so
     * its network/refresh work stops competing for resources. The ad resumes
     * on onResume. Mirrors AdView.pause() semantics that the AdMob docs
     * recommend for parent activities.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            runCatching {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    applicationContext,
                    AdsManagerEntryPoint::class.java,
                ).adsManager().pause()
            }
        }
    }

    // NOTE: AdsManager is a process-scoped @Singleton holding the shared
    // banner AdView. Calling destroy() here would tear down the AdView on
    // every config change / language recreate and the banner would be dead
    // for the rest of the process (the singleton survives Activity recreate).
    // The OS reclaims the AdView when the process dies; onDestroy is left
    // empty intentionally.

    private fun applyLocale(base: Context, tag: String): Context {
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(locale)
        return base.createConfigurationContext(config) ?: base
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
private interface AdsManagerEntryPoint {
    fun adsManager(): com.dhanuk.govphoto.data.ads.AdsManager
}
