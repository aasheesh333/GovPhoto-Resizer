package com.dhanuk.govphoto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto.data.datastore.AppLanguage
import com.dhanuk.govphoto.data.datastore.DarkModePref
import com.dhanuk.govphoto.data.datastore.SettingsRepository
import com.dhanuk.govphoto.data.datastore.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    val adsManager: com.dhanuk.govphoto.data.ads.AdsManager,
) : ViewModel() {
    val state: StateFlow<SettingsState> = repo.state.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsState()
    )

    val adDiagnosticInfo = adsManager.diagnosticInfo.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), adsManager.diagnosticInfo.value
    )

    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { repo.setLanguage(lang) }

    /**
     * Apply a language change so that an immediately-following Activity.recreate()
     * picks up the new locale in attachBaseContext() without racing the async
     * DataStore write. Writes the locale cache synchronously, then persists the
     * DataStore value asynchronously (source of truth for cold starts).
     */
    fun applyLanguage(lang: AppLanguage) {
        repo.setLanguageSync(lang)
        viewModelScope.launch { repo.setLanguage(lang) }
    }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }
    fun setDarkMode(pref: DarkModePref) = viewModelScope.launch { repo.setDarkMode(pref) }
    fun setLargeButtons(enabled: Boolean) = viewModelScope.launch { repo.setLargeButtons(enabled) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { repo.setHighContrast(enabled) }
    fun setOnboardingComplete(done: Boolean) = viewModelScope.launch { repo.setOnboardingComplete(done) }
    fun setNotificationPermissionAsked(asked: Boolean) =
        viewModelScope.launch { repo.setNotificationPermissionAsked(asked) }
    fun setLastPresetId(id: String?) = viewModelScope.launch { repo.setLastPresetId(id) }
    fun setCachedIsPro(cached: Boolean) = viewModelScope.launch { repo.setCachedIsPro(cached) }
    fun setAdFreeUntilMs(untilMs: Long) = viewModelScope.launch { repo.setAdFreeUntilMs(untilMs) }
    fun recordSave() = viewModelScope.launch { repo.bumpSaveCount() }
    fun refreshAdDiagnostics() = adsManager.refreshDiagnosticInfo()
}
