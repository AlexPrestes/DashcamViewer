package com.alexprestes.dashcamviewer.ui.volume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexprestes.dashcamviewer.domain.model.VolumeInfo
import com.alexprestes.dashcamviewer.domain.usecase.ListVolumesWithDashcamVideoCountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class VolumeUiState {
    object Loading : VolumeUiState()
    data class Success(val volumes: List<VolumeInfo>) : VolumeUiState()
    data class Error(val message: String) : VolumeUiState()
}

class VolumeViewModel(
    private val listVolumesUseCase: ListVolumesWithDashcamVideoCountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<VolumeUiState>(VolumeUiState.Loading)
    val uiState: StateFlow<VolumeUiState> = _uiState

    init {
        loadVolumes()
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            _uiState.value = VolumeUiState.Loading
            try {
                val volumes = listVolumesUseCase.execute()
                _uiState.value = VolumeUiState.Success(volumes)
            } catch (e: Exception) {
                _uiState.value = VolumeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
