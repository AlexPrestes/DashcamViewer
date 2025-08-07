package com.alexprestes.dashcamviewer.ui.player

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
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
        PlayerContent(volumeName = volumeName) {
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
fun PlayerContent(volumeName: String?, onCalendarClick: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    val playbackSpeeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    var speedIndex by remember { mutableStateOf(1) }
    var currentPosition by remember { mutableStateOf(0L) }

    val sampleTimeline = Timeline(
        segments = listOf(
            RecordingSegment(listOf(VideoClip("a", false)), 1000, 100),
            RecordingSegment(listOf(VideoClip("b", true)), 2000, 200),
            RecordingSegment(listOf(VideoClip("c", false)), 1500, 0),
        ),
        totalDuration = 4800
    )

    Column {
        Text(text = "Player screen for $volumeName")
        Row(modifier = Modifier.weight(1f)) {
            // Video players will go here.
        }
        TimelineView(
            timeline = sampleTimeline,
            onSeek = { newPosition ->
                currentPosition = newPosition
            },
            currentPosition = currentPosition
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onCalendarClick() }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Open Calendar")
            }
            // Other controls...
        }
    }
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
