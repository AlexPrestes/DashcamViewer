package com.alexprestes.dashcamviewer.ui.player

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.alexprestes.dashcamviewer.model.RecordingSegment
import com.alexprestes.dashcamviewer.model.Timeline
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(volumeName: String?) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerContent {
            showBottomSheet = true
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                },
                sheetState = sheetState
            ) {
                CalendarSheetContent {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerContent(onCalendarClick: () -> Unit) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val playbackSpeeds = listOf(0.5f, 1.0f, 2.0f)
    var currentSpeedIndex by remember { mutableStateOf(1) }
    var currentPosition by remember { mutableStateOf(0L) }

    val frontPlayer = remember { ExoPlayer.Builder(context).build() }
    val insidePlayer = remember { ExoPlayer.Builder(context).build() }

    // Sample timeline for demonstration
    val sampleTimeline = remember {
        Timeline(
            segments = listOf(
                RecordingSegment(0, 60000, false, "front1.mp4", "inside1.mp4"),
                RecordingSegment(65000, 125000, true, "front2.mp4", "inside2.mp4"),
                RecordingSegment(130000, 190000, false, "front3.mp4", "inside3.mp4")
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            frontPlayer.release()
            insidePlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            frontPlayer.play()
            insidePlayer.play()
        } else {
            frontPlayer.pause()
            insidePlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        val volume = if (isMuted) 0f else 1f
        frontPlayer.volume = volume
        insidePlayer.volume = volume
    }

    LaunchedEffect(currentSpeedIndex) {
        val speed = playbackSpeeds[currentSpeedIndex]
        val playbackParameters = PlaybackParameters(speed)
        frontPlayer.playbackParameters = playbackParameters
        insidePlayer.playbackParameters = playbackParameters
    }

    // Update currentPosition while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = frontPlayer.currentPosition
            delay(100) // Update every 100ms
        }
    }

    fun seekPlayers(position: Long) {
        frontPlayer.seekTo(position)
        insidePlayer.seekTo(position)
        currentPosition = position
    }


    Column {
        Row(modifier = Modifier.weight(1f)) {
            PlayerViewComposable(
                modifier = Modifier.weight(1f),
                player = frontPlayer
            )
            PlayerViewComposable(
                modifier = Modifier.weight(1f),
                player = insidePlayer
            )
        }
        TimelineView(
            timeline = sampleTimeline,
            currentPosition = currentPosition,
            onSeek = ::seekPlayers,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color.Gray)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /*TODO*/ }) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = "Record")
            }
            IconButton(onClick = { /*TODO*/ }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Screenshot")
            }
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
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
                Text(text = "${playbackSpeeds[currentSpeedIndex]}x")
            }
            IconButton(onClick = { onCalendarClick() }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Open Calendar")
            }
        }
    }
}

@Composable
fun PlayerViewComposable(modifier: Modifier = Modifier, player: ExoPlayer) {
    AndroidView(
        modifier = modifier
            .border(BorderStroke(2.dp, Color.Black))
            .padding(4.dp),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
            }
        }
    )
}


@Composable
fun CalendarSheetContent(onDateSelected: () -> Unit) {
    val context = LocalContext.current
    val currentMonth = YearMonth.now()
    val startMonth = currentMonth.minusMonths(100)
    val endMonth = currentMonth.plusMonths(100)
    val firstDayOfWeek = firstDayOfWeekFromLocale()

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    VerticalCalendar(
        state = state,
        dayContent = { day ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .clickable {
                        Toast
                            .makeText(context, "Selected: ${day.date}", Toast.LENGTH_SHORT)
                            .show()
                        onDateSelected()
                    }
            ) {
                Text(text = day.date.dayOfMonth.toString())
            }
        },
        monthHeader = { month ->
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
            Text(text = month.yearMonth.format(formatter))
        }
    )
}
