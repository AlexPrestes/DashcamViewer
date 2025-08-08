package com.alexprestes.dashcamviewer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexprestes.dashcamviewer.domain.model.Timeline
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.Dp

private val DpPerSecond = 8.dp
private val TotalTimelineHeight = 100.dp
private val SegmentsTrackHeight = 60.dp
private val MajorTickHeight = 12.dp
private val MinorTickHeight = 6.dp

@Composable
fun TimelineView(
    timeline: Timeline,
    currentAbsolutePosition: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    dpPerSecond: Dp, // <-- NOVO PARÂMETRO
    modifier: Modifier = Modifier
) {
    if (timeline.segments.isEmpty()) {
        Box(
            modifier = modifier.height(TotalTimelineHeight).fillMaxWidth().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhuma gravação para esta data.", color = Color.White)
        }
        return
    }

    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0f) }
    val pxPerSecond = with(density) { dpPerSecond.toPx() }

    var scrollOffsetPx by remember { mutableStateOf(0f) }
    val dayStart = remember(timeline) {
        timeline.segments.first().startTime.toLocalDate().atStartOfDay()
    }

    val centerTime = remember(scrollOffsetPx, containerWidthPx, dayStart, pxPerSecond) {
        val centerPx = scrollOffsetPx + (containerWidthPx / 2)
        val secondsFromStartOfDay = (centerPx / pxPerSecond).toLong()
        dayStart.plusSeconds(secondsFromStartOfDay)
    }

    val scrollableState = rememberScrollableState { delta ->
        val totalWidthPx = Duration.ofDays(1).seconds * pxPerSecond
        scrollOffsetPx = (scrollOffsetPx - delta).coerceIn(0f, totalWidthPx - containerWidthPx)
        delta
    }

    // Efeito para buscar a posição quando o usuário solta a timeline
    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (!scrollableState.isScrollInProgress) {
            val seekPositionMs = timeToPlaylistPosition(timeline, centerTime)
            onSeek(seekPositionMs)
        }
    }

    // Efeito para atualizar a posição da timeline quando o vídeo está tocando
    LaunchedEffect(currentAbsolutePosition) {
        if (isPlaying && !scrollableState.isScrollInProgress) {
            val positionInSeconds = playlistPositionToSecondsOfDay(timeline, currentAbsolutePosition)
            val targetScrollOffset = (positionInSeconds * pxPerSecond) - (containerWidthPx / 2)
            val totalWidthPx = Duration.ofDays(1).seconds * pxPerSecond
            scrollOffsetPx = targetScrollOffset.coerceIn(0f, totalWidthPx - containerWidthPx)
        }
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
    ) {
        Text(
            text = centerTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TotalTimelineHeight)
                .scrollable(state = scrollableState, orientation = Orientation.Horizontal)
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
        ) {
            TimelineCanvas(
                modifier = Modifier.fillMaxSize(),
                timeline = timeline,
                scrollOffsetPx = scrollOffsetPx,
                pxPerSecond = pxPerSecond,
                dayStart = dayStart
            )

            // Marcador central fixo
            Spacer(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.Red)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun TimelineCanvas(
    modifier: Modifier,
    timeline: Timeline,
    scrollOffsetPx: Float,
    pxPerSecond: Float,
    dayStart: LocalDateTime
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = Color.LightGray, fontSize = 10.sp)
    val totalDayDurationSeconds = Duration.ofDays(1).seconds

    Canvas(modifier = modifier) {
        val visibleStartPx = scrollOffsetPx
        val visibleEndPx = scrollOffsetPx + size.width

        // --- Desenha os Segmentos de Vídeo ---
        timeline.segments.forEach { segment ->
            val startSeconds = Duration.between(dayStart, segment.startTime).seconds
            val endSeconds = Duration.between(dayStart, segment.endTime).seconds
            val startPx = startSeconds * pxPerSecond
            val endPx = endSeconds * pxPerSecond

            if (startPx < visibleEndPx && endPx > visibleStartPx) {
                drawRect(
                    color = Color(0xFF81C784), // Um verde claro, como no exemplo
                    topLeft = Offset(startPx - scrollOffsetPx, 0f),
                    size = androidx.compose.ui.geometry.Size((endPx - startPx), SegmentsTrackHeight.toPx())
                )
            }
        }

        // --- Desenha os Marcadores de Tempo ---
        val hourInSeconds = 3600L
        val fiveMinutesInSeconds = 5 * 60L
        for (second in 0..totalDayDurationSeconds step fiveMinutesInSeconds) {
            val currentPx = second * pxPerSecond
            if (currentPx >= visibleStartPx - 50 && currentPx <= visibleEndPx + 50) {
                val isHourMark = second % hourInSeconds == 0L
                val tickHeight = if (isHourMark) MajorTickHeight else MinorTickHeight
                val lineStart = Offset(currentPx - scrollOffsetPx, SegmentsTrackHeight.toPx())
                val lineEnd = Offset(currentPx - scrollOffsetPx, SegmentsTrackHeight.toPx() + tickHeight.toPx())
                drawLine(Color.LightGray, lineStart, lineEnd, strokeWidth = 1.dp.toPx())

                if (isHourMark) {
                    val time = dayStart.plusSeconds(second)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = textStyle,
                        topLeft = Offset(currentPx - scrollOffsetPx + 4.dp.toPx(), lineEnd.y)
                    )
                }
            }
        }
    }
}

private fun timeToPlaylistPosition(timeline: Timeline, time: LocalDateTime): Long {
    if (timeline.segments.isEmpty()) return 0L

    var accumulatedDurationMs = 0L
    for (segment in timeline.segments) {
        // Se o tempo estiver dentro deste segmento
        if (time >= segment.startTime && time < segment.endTime) {
            val positionInSegment = Duration.between(segment.startTime, time).toMillis()
            return accumulatedDurationMs + positionInSegment
        }
        accumulatedDurationMs += segment.duration.toMillis()
    }

    // Se o tempo for antes do primeiro vídeo, busca o início.
    if (time < timeline.segments.first().startTime) {
        return 0L
    }

    // Se o tempo for depois do último vídeo, busca o final.
    return accumulatedDurationMs
}

private fun playlistPositionToSecondsOfDay(timeline: Timeline, positionMs: Long): Long {
    if (timeline.segments.isEmpty()) return 0L

    var accumulatedDurationMs = 0L
    val dayStart = timeline.segments.first().startTime.toLocalDate().atStartOfDay()

    for (segment in timeline.segments) {
        val segmentDurationMs = segment.duration.toMillis()
        if (positionMs <= accumulatedDurationMs + segmentDurationMs) {
            val timeIntoSegment = positionMs - accumulatedDurationMs
            val actualTime = segment.startTime.plus(Duration.ofMillis(timeIntoSegment))
            return Duration.between(dayStart, actualTime).seconds
        }
        accumulatedDurationMs += segmentDurationMs
    }

    // Se a posição for além do último vídeo, retorna a posição do final do último vídeo
    return Duration.between(dayStart, timeline.segments.last().endTime).seconds
}