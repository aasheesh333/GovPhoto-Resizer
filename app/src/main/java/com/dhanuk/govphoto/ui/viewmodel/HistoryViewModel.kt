package com.dhanuk.govphoto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.govphoto.data.local.entity.PhotoHistoryEntity
import com.dhanuk.govphoto.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepo: HistoryRepository
) : ViewModel() {

    val history: StateFlow<List<PhotoHistoryEntity>> = historyRepo.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(item: PhotoHistoryEntity) {
        viewModelScope.launch { historyRepo.delete(item) }
    }
}
