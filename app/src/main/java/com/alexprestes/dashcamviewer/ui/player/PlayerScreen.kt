package com.alexprestes.dashcamviewer.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.alexprestes.dashcamviewer.domain.model.Timeline
import com.alexprestes.dashcamviewer.domain.model.VideoFile
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    volumeName: String?,
    playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application,
            volumeName
        )
    )
) {
    val uiState by playerViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }

            is PlayerUiState.Error -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "Error: ${state.message}")
                }
            }

            is PlayerUiState.Success -> {
                PlayerContent(
                    timeline = state.timelineForSelectedDate,
                    onCalendarClick = { showBottomSheet = true }
                )

                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState
                    ) {
                        CalendarSheetContent(
                            availableDates = state.availableDates,
                            selectedDate = state.selectedDate,
                            onDateSelected = { date ->
                                playerViewModel.selectDate(date)
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        showBottomSheet = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PlayerContent(timeline: Timeline, onCalendarClick: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val playbackSpeeds = listOf(0.5f, 1.0f, 2.0f)
    var currentSpeedIndex by remember { mutableStateOf(1) }

    // Lista de vídeos para o dia selecionado, usada para cálculos de posição
    val frontVideos = remember(timeline) {
        timeline.segments.flatMap { it.clips }.map { it.frontVideo }
    }
    val insideVideos = remember(timeline) {
        timeline.segments.flatMap { it.clips }.mapNotNull { it.rearVideo }
    }

    val frontPlayer = remember { ExoPlayer.Builder(context).build() }
    val insidePlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(timeline) {
        frontPlayer.prepareWithVideos(frontVideos, context)
        insidePlayer.prepareWithVideos(insideVideos, context)
        isPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose {
            frontPlayer.release()
            insidePlayer.release()
        }
    }

    // --- LÓGICA DE POSIÇÃO E SEEK CORRIGIDA ---
    var absolutePosition by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            var basePosition = 0L
            val currentWindowIndex = frontPlayer.currentMediaItemIndex

            // Soma a duração REAL dos vídeos anteriores na playlist
            for (i in 0 until currentWindowIndex) {
                // Acessa a duração do objeto VideoFile, que agora deve ser precisa
                basePosition += frontVideos.getOrNull(i)?.duration?.toMillis() ?: (60 * 1000L) // Fallback para 1 min
            }
            absolutePosition = basePosition + frontPlayer.currentPosition
            delay(250) // Atualiza 4x por segundo
        }
    }

    fun seekPlayers(playlistPosition: Long) {
        var targetWindowIndex = 0
        var accumulatedDuration = 0L
        var positionInWindow = 0L

        // Encontra em qual vídeo da playlist a posição desejada está, usando a duração real
        for ((index, video) in frontVideos.withIndex()) {
            val videoDuration = video.duration.toMillis()
            if (playlistPosition < accumulatedDuration + videoDuration) {
                targetWindowIndex = index
                positionInWindow = playlistPosition - accumulatedDuration
                break
            }
            accumulatedDuration += videoDuration
        }

        frontPlayer.seekTo(targetWindowIndex, positionInWindow)
        insidePlayer.seekTo(targetWindowIndex, positionInWindow)
        absolutePosition = playlistPosition
    }

    // --- FIM DA LÓGICA DE POSIÇÃO E SEEK ---

    LaunchedEffect(frontPlayer) {
        frontPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) insidePlayer.play() else insidePlayer.pause()
            }
        })
    }

    LaunchedEffect(isMuted) {
        val volume = if (isMuted) 0f else 1f
        frontPlayer.volume = volume
        insidePlayer.volume = volume
    }

    LaunchedEffect(currentSpeedIndex) {
        val speed = playbackSpeeds[currentSpeedIndex]
        frontPlayer.playbackParameters = PlaybackParameters(speed)
        insidePlayer.playbackParameters = PlaybackParameters(speed)
    }

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            PlayerViewComposable(player = frontPlayer, modifier = Modifier.weight(1f))
            PlayerViewComposable(player = insidePlayer, modifier = Modifier.weight(1f))
        }

        TimelineView(
            timeline = timeline,
            currentAbsolutePosition = absolutePosition,
            onSeek = ::seekPlayers
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ... (Botões de controle permanecem os mesmos)
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = "Record")
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Screenshot")
            }
            IconButton(onClick = {
                if (frontPlayer.isPlaying) frontPlayer.pause() else frontPlayer.play()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { isMuted = !isMuted }) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute/Unmute"
                )
            }
            TextButton(onClick = {
                currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
            }) {
                Text(
                    text = "${playbackSpeeds[currentSpeedIndex]}x",
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCalendarClick) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Open Calendar")
            }
        }
    }
}

// ... (O resto do arquivo PlayerScreen.kt, incluindo CalendarSheetContent, Day, etc., permanece o mesmo)

@Composable
private fun CalendarSheetContent(
    availableDates: Set<LocalDate>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val currentMonth = YearMonth.now()
    val startMonth = remember { currentMonth.minusMonths(100) }
    val endMonth = remember { currentMonth.plusMonths(100) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = YearMonth.from(selectedDate),
        firstDayOfWeek = firstDayOfWeek
    )

    Column(modifier = Modifier.fillMaxHeight(0.8f)) {
        Text(
            text = "Selecione uma data",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        VerticalCalendar(
            state = state,
            dayContent = { day ->
                Day(
                    day = day,
                    isSelected = day.date == selectedDate,
                    isAvailable = day.date in availableDates
                ) { onDateSelected(it.date) }
            },
            monthHeader = { month ->
                val monthName = month.yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                val year = month.yearMonth.year
                Text(
                    text = "${monthName.replaceFirstChar { it.uppercase() }} $year",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        )
    }
}

@Composable
private fun Day(day: CalendarDay, isSelected: Boolean, isAvailable: Boolean, onClick: (CalendarDay) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .background(
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                shape = MaterialTheme.shapes.medium
            )
            .border(
                width = if (isAvailable && !isSelected) 1.dp else 0.dp,
                color = if (isAvailable && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                enabled = isAvailable,
                onClick = { onClick(day) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun PlayerViewComposable(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> PlayerView(context).apply { useController = false } },
        update = { view -> view.player = player },
        modifier = modifier
    )
}

private fun ExoPlayer.prepareWithVideos(videos: List<VideoFile>, context: Context) {
    if (videos.isEmpty()) {
        clearMediaItems()
        return
    }
    val mediaItems = videos.map { MediaItem.fromUri(it.uri) }
    setMediaItems(mediaItems)
    prepare()
}

// Factory para injetar parâmetros no ViewModel
private class PlayerViewModelFactory(
    private val application: android.app.Application,
    private val volumeName: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            val savedStateHandle = SavedStateHandle(mapOf("volumeName" to volumeName))
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application, savedStateHandle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}