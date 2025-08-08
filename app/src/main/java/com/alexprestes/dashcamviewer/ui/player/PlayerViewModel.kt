package com.alexprestes.dashcamviewer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alexprestes.dashcamviewer.domain.model.RecordingSegment
import com.alexprestes.dashcamviewer.domain.model.Timeline
import com.alexprestes.dashcamviewer.domain.usecase.GetTimelineForVolumeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// Novo estado para a UI, agora ciente da data selecionada
sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Success(
        val fullTimeline: Timeline, // Guarda a timeline completa
        val timelineForSelectedDate: Timeline, // A timeline filtrada para a UI
        val availableDates: Set<LocalDate>, // Datas que têm gravações
        val selectedDate: LocalDate // Data atualmente selecionada
    ) : PlayerUiState
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
            loadFullTimeline(volumeName)
        } else {
            _uiState.value = PlayerUiState.Error("Volume name not provided")
        }
    }

    private fun loadFullTimeline(volumeName: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            getTimelineUseCase.execute(volumeName)
                .onSuccess { timeline ->
                    if (timeline.segments.isEmpty()) {
                        _uiState.value = PlayerUiState.Error("Nenhum vídeo encontrado.")
                        return@onSuccess
                    }

                    val availableDates = timeline.segments
                        .map { it.startTime.toLocalDate() }
                        .toSet()

                    val latestDate = availableDates.maxOrNull() ?: LocalDate.now()

                    val segmentsForLatestDate = timeline.segments.filter {
                        it.startTime.toLocalDate() == latestDate
                    }

                    _uiState.value = PlayerUiState.Success(
                        fullTimeline = timeline,
                        timelineForSelectedDate = timeline.copy(segments = segmentsForLatestDate),
                        availableDates = availableDates,
                        selectedDate = latestDate
                    )
                }
                .onFailure { error ->
                    _uiState.value = PlayerUiState.Error(error.message ?: "An unknown error occurred")
                }
        }
    }

    // Nova função para ser chamada pelo calendário
    fun selectDate(date: LocalDate) {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            val segmentsForDate = currentState.fullTimeline.segments.filter {
                it.startTime.toLocalDate() == date
            }
            _uiState.update {
                currentState.copy(
                    timelineForSelectedDate = currentState.fullTimeline.copy(segments = segmentsForDate),
                    selectedDate = date
                )
            }
        }
    }
}