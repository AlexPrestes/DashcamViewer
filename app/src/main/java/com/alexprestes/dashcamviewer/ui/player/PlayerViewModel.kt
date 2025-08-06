package com.alexprestes.dashcamviewer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alexprestes.dashcamviewer.domain.model.Timeline
import com.alexprestes.dashcamviewer.domain.usecase.GetTimelineForVolumeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Success(val timeline: Timeline) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val getTimelineUseCase = GetTimelineForVolumeUseCase(application)

    init {
        val volumeName: String? = savedStateHandle["volumeName"]
        if (volumeName != null) {
            loadTimeline(volumeName)
        } else {
            _uiState.value = PlayerUiState.Error("Volume name not provided")
        }
    }

    private fun loadTimeline(volumeName: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            getTimelineUseCase.execute(volumeName)
                .onSuccess { timeline ->
                    _uiState.value = PlayerUiState.Success(timeline)
                }
                .onFailure { error ->
                    _uiState.value = PlayerUiState.Error(error.message ?: "An unknown error occurred")
                }
        }
    }
}
