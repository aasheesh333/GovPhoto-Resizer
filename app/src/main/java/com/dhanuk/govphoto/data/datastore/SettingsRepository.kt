package com.dhanuk.govphoto.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dhanuk.govphoto.data.push.PushCategory
import com.dhanuk.govphoto.data.push.PushCategoryStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val cachedIsPro: Boolean = false,
    val adFreeUntilMs: Long = 0L,
    val saveCount: Int = 0,
    val releaseNotificationsEnabled: Boolean = true,
    val examDeadlineNotificationsEnabled: Boolean = false,
    val supportNotificationsEnabled: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context)
    : CachedIsProStore, PushCategoryStore {

    private object Keys {
        val LANGUAGE         = stringPreferencesKey("language")
        val DYNAMIC_COLOR    = booleanPreferencesKey("dynamic_color")
        val DARK_MODE        = stringPreferencesKey("dark_mode")
        val LARGE_BUTTONS    = booleanPreferencesKey("large_buttons")
        val HIGH_CONTRAST    = booleanPreferencesKey("high_contrast")
        val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        val LAST_PRESET_ID   = stringPreferencesKey("last_preset_id")
        val CACHED_IS_PRO            = booleanPreferencesKey("cached_is_pro")
        val AD_FREE_UNTIL_MS         = longPreferencesKey("ad_free_until_ms")
        val SAVE_COUNT               = intPreferencesKey("save_count")
        val NOTIFY_RELEASE           = booleanPreferencesKey("notify_release")
        val NOTIFY_EXAM_DEADLINES    = booleanPreferencesKey("notify_exam_deadlines")
        val NOTIFY_SUPPORT_REPLIES   = booleanPreferencesKey("notify_support_replies")
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
            cachedIsPro = prefs[Keys.CACHED_IS_PRO] ?: false,
            adFreeUntilMs = prefs[Keys.AD_FREE_UNTIL_MS] ?: 0L,
            saveCount = prefs[Keys.SAVE_COUNT] ?: 0,
            releaseNotificationsEnabled = prefs[Keys.NOTIFY_RELEASE] ?: true,
            examDeadlineNotificationsEnabled = prefs[Keys.NOTIFY_EXAM_DEADLINES] ?: false,
            supportNotificationsEnabled = prefs[Keys.NOTIFY_SUPPORT_REPLIES] ?: true,
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

    // CachedIsProStore
    override suspend fun getCachedIsPro(): Boolean =
        context.dataStore.data.first()[Keys.CACHED_IS_PRO] ?: false
    override suspend fun setCachedIsPro(value: Boolean) {
        context.dataStore.edit { it[Keys.CACHED_IS_PRO] = value }
    }

    // PushCategoryStore
    override suspend fun isEnabled(category: PushCategory): Boolean {
        return when (category) {
            PushCategory.RELEASE_NOTES -> context.dataStore.data.first()[Keys.NOTIFY_RELEASE] ?: true
            PushCategory.EXAM_DEADLINES -> context.dataStore.data.first()[Keys.NOTIFY_EXAM_DEADLINES] ?: false
            PushCategory.SUPPORT_REPLIES -> context.dataStore.data.first()[Keys.NOTIFY_SUPPORT_REPLIES] ?: true
        }
    }
    override suspend fun setEnabled(category: PushCategory, enabled: Boolean) {
        val key = when (category) {
            PushCategory.RELEASE_NOTES -> Keys.NOTIFY_RELEASE
            PushCategory.EXAM_DEADLINES -> Keys.NOTIFY_EXAM_DEADLINES
            PushCategory.SUPPORT_REPLIES -> Keys.NOTIFY_SUPPORT_REPLIES
        }
        context.dataStore.edit { it[key] = enabled }
    }

    // Ad-free reward
    suspend fun setAdFreeUntilMs(untilMs: Long) {
        context.dataStore.edit { it[Keys.AD_FREE_UNTIL_MS] = untilMs }
    }
    suspend fun bumpSaveCount() {
        context.dataStore.edit { it[Keys.SAVE_COUNT] = (it[Keys.SAVE_COUNT] ?: 0) + 1 }
    }

    fun getCachedLanguageTag(): String? = sharedPrefs.getString("language", null)
}

interface CachedIsProStore {
    suspend fun getCachedIsPro(): Boolean
    suspend fun setCachedIsPro(value: Boolean)
}
