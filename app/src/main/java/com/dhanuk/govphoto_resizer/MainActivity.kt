package com.dhanuk.govphoto_resizer

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
import com.dhanuk.govphoto_resizer.data.datastore.DarkModePref
import com.dhanuk.govphoto_resizer.ui.navigation.GovPhotoNavHost
import com.dhanuk.govphoto_resizer.ui.theme.GovPhotoTheme
import com.dhanuk.govphoto_resizer.ui.theme.LocalAppLanguage
import com.dhanuk.govphoto_resizer.ui.theme.LocalLargeButtons
import com.dhanuk.govphoto_resizer.ui.viewmodel.SettingsViewModel
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
            ) {
                GovPhotoTheme(darkTheme = isDark, dynamicColor = settings.dynamicColor) {
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

    private fun applyLocale(base: Context, tag: String): Context {
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(locale)
        return base.createConfigurationContext(config) ?: base
    }
}
