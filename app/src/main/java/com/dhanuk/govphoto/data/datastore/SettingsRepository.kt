package com.dhanuk.govphoto.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "govphoto_settings")

enum class AppLanguage(val tag: String) { ENGLISH("en"), HINDI("hi") }
enum class DarkModePref(val label: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

data class SettingsState(
val language: AppLanguage = AppLanguage.ENGLISH,
val dynamicColor: Boolean = false,
val darkMode: DarkModePref = DarkModePref.LIGHT,
val largeButtons: Boolean = false,
val highContrast: Boolean = false,
val onboardingComplete: Boolean = false,
val lastPresetId: String? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val LANGUAGE         = stringPreferencesKey("language")
        val DYNAMIC_COLOR    = booleanPreferencesKey("dynamic_color")
        val DARK_MODE        = stringPreferencesKey("dark_mode")
        val LARGE_BUTTONS    = booleanPreferencesKey("large_buttons")
        val HIGH_CONTRAST    = booleanPreferencesKey("high_contrast")
        val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        val LAST_PRESET_ID   = stringPreferencesKey("last_preset_id")
    }

    // Synchronous SharedPreferences shim for the locale cache so that
    // attachBaseContext() can read the persisted language without awaiting DataStore.
    private val sharedPrefs by lazy {
        context.getSharedPreferences("govphoto_locale_cache", Context.MODE_PRIVATE)
    }

    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            language = prefs[Keys.LANGUAGE]?.let { tag ->
                AppLanguage.entries.firstOrNull { it.tag == tag }
            } ?: AppLanguage.ENGLISH,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            darkMode = prefs[Keys.DARK_MODE]?.let { label ->
                DarkModePref.entries.firstOrNull { it.label == label }
            } ?: DarkModePref.LIGHT,
            largeButtons = prefs[Keys.LARGE_BUTTONS] ?: false,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            onboardingComplete = prefs[Keys.ONBOARDING_DONE] ?: false,
            lastPresetId = prefs[Keys.LAST_PRESET_ID],
        )
    }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang.tag }
        sharedPrefs.edit().putString("language", lang.tag).apply()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = enabled }
    }
    suspend fun setDarkMode(pref: DarkModePref) { context.dataStore.edit { prefs -> prefs[Keys.DARK_MODE] = pref.label } }
    suspend fun setLargeButtons(enabled: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.LARGE_BUTTONS] = enabled } }
    suspend fun setHighContrast(enabled: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.HIGH_CONTRAST] = enabled } }
    suspend fun setOnboardingComplete(done: Boolean) { context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_DONE] = done } }
    suspend fun setLastPresetId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.LAST_PRESET_ID) else prefs[Keys.LAST_PRESET_ID] = id
        }
    }

    fun getCachedLanguageTag(): String? = sharedPrefs.getString("language", null)
}
