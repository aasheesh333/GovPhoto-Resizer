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

        // UMP consent flow -> MobileAds.initialize()
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
                    }
                }
            },
            object : com.google.android.ump.ConsentInformation.OnConsentInfoUpdateFailureListener {
                override fun onConsentInfoUpdateFailure(error: com.google.android.ump.FormError) {
                    initializeMobileAds()
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
            val settings by hiltViewModel<SettingsViewModel>().state.collectAsState()
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
                        GovPhotoNavHost()
                    }
                }
            }
        }
    }

    private fun initializeMobileAds() {
        com.google.android.gms.ads.MobileAds.initialize(this)
    }

    private fun applyLocale(base: Context, tag: String): Context {
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(locale)
        return base.createConfigurationContext(config) ?: base
    }
}
