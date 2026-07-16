package com.dhanuk.govphoto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto.data.model.PhotoPreset
import com.dhanuk.govphoto.data.model.PresetCategory
import com.dhanuk.govphoto.data.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        viewModelScope.launch(Dispatchers.IO) {
            val custom = PhotoPreset(
                id = PhotoPreset.MANUAL_PRESET_ID,
                examName = "Custom Size",
                examNameHi = "मैन्युअल साइज",
                authority = "Manual",
                category = PresetCategory.CUSTOM,
                widthPx = 350,
                heightPx = 450,
                widthCm = 3.5f,
                heightCm = 4.5f,
                maxFileSizeKb = 500,
                format = "jpg",
                lastUpdated = System.currentTimeMillis().toString(),
                notes = "Adjust width, height, format on the next screen",
            )
            val all = listOf(custom) + presetRepository.getAllPresets()
            _presets.value = all
        }
    }
}
