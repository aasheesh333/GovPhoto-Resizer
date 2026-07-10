package com.dhanuk.govphoto_resizer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity
import com.dhanuk.govphoto_resizer.data.repository.PresetRepository
import com.dhanuk.govphoto_resizer.data.repository.RecentPresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecentPresetUiItem(
    val presetId: String,
    val examName: String,
    val category: String,
    val dimensions: String,
    val useCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    recentPresetRepo: RecentPresetRepository,
    private val presetRepo: PresetRepository
) : ViewModel() {

    val recentPresets: StateFlow<List<RecentPresetUiItem>> = recentPresetRepo
        .observeRecent(5)
        .map { entities -> entities.map { it.toUiItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun RecentPresetEntity.toUiItem(): RecentPresetUiItem {
        val preset = presetRepo.getPreset(presetId)
        val displayName = preset?.examName ?: examName
        val displayCategory = preset?.category?.displayName ?: category
        val dimensions = preset?.getFormattedDimensions() ?: ""
        return RecentPresetUiItem(
            presetId = presetId,
            examName = displayName,
            category = displayCategory,
            dimensions = dimensions,
            useCount = useCount
        )
    }
}
