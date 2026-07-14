package com.dhanuk.govphoto_resizer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto_resizer.data.datastore.AppLanguage
import com.dhanuk.govphoto_resizer.data.datastore.DarkModePref
import com.dhanuk.govphoto_resizer.data.datastore.SettingsRepository
import com.dhanuk.govphoto_resizer.data.datastore.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {
    val state: StateFlow<SettingsState> = repo.state.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsState()
    )

    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { repo.setLanguage(lang) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }
    fun setDarkMode(pref: DarkModePref) = viewModelScope.launch { repo.setDarkMode(pref) }
    fun setLargeButtons(enabled: Boolean) = viewModelScope.launch { repo.setLargeButtons(enabled) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { repo.setHighContrast(enabled) }
    fun setOnboardingComplete(done: Boolean) = viewModelScope.launch { repo.setOnboardingComplete(done) }
    fun setLastPresetId(id: String?) = viewModelScope.launch { repo.setLastPresetId(id) }
}
