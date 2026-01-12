package com.govphoto.resizer.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.govphoto.resizer.data.model.PhotoPreset
import com.govphoto.resizer.data.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel for AllFormsScreen to manage preset loading.
 */
@HiltViewModel
class AllFormsViewModel @Inject constructor(
    private val presetRepository: PresetRepository
) : ViewModel() {
    
    private val _presets = MutableStateFlow<List<PhotoPreset>>(emptyList())
    val presets: StateFlow<List<PhotoPreset>> = _presets.asStateFlow()
    
    init {
        loadPresets()
    }
    
    private fun loadPresets() {
        _presets.value = presetRepository.getAllPresets()
    }
}
