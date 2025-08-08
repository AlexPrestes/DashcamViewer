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
import kotlin.math.roundToLong

// --- Constantes de Configuração da Timeline ---
private val DpPerSecond = 8.dp
private val TotalTimelineHeight = 100.dp
private val SegmentsTrackHeight = 60.dp
private val TimeMarkerTrackHeight = 40.dp
private val MajorTickHeight = 12.dp
private val MinorTickHeight = 6.dp

@Composable
fun TimelineView(
    timeline: Timeline,
    currentAbsolutePosition: Long, // Posição em milissegundos desde o início da playlist
    onSeek: (Long) -> Unit, // Ação de seek com a posição em milissegundos
    modifier: Modifier = Modifier
) {
    if (timeline.segments.isEmpty()) {
        Box(
            modifier = modifier.height(TotalTimelineHeight),
            contentAlignment = Alignment.Center
        ) {
            Text("Nenhuma gravação para esta data.")
        }
        return
    }

    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0f) }
    val pxPerSecond = with(density) { DpPerSecond.toPx() }

    // Offset de scroll em pixels, controlado pelo gesto de arrastar
    var scrollOffsetPx by remember { mutableStateOf(0f) }

    // O timestamp do início do dia, para referência de cálculo
    val dayStart = remember(timeline) {
        timeline.segments.first().startTime.toLocalDate().atStartOfDay()
    }

    // O timestamp exato que está no centro da tela (para o seek)
    val centerTime = remember(scrollOffsetPx, containerWidthPx, dayStart, pxPerSecond) {
        val centerPx = scrollOffsetPx + (containerWidthPx / 2)
        val secondsFromStartOfDay = (centerPx / pxPerSecond).toLong()
        dayStart.plusSeconds(secondsFromStartOfDay)
    }

    // Lógica para detectar quando o usuário para de arrastar e então chamar onSeek
    val scrollableState = rememberScrollableState { delta ->
        scrollOffsetPx = (scrollOffsetPx - delta).coerceIn(0f, (Duration.ofDays(1).seconds * pxPerSecond) - containerWidthPx)
        delta
    }
    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (!scrollableState.isScrollInProgress) {
            // Converte a hora do dia para uma posição na playlist do ExoPlayer
            val seekPositionMs = timeToPlaylistPosition(timeline, centerTime)
            onSeek(seekPositionMs)
        }
    }

    // Efeito para a timeline seguir o vídeo enquanto ele toca
    LaunchedEffect(currentAbsolutePosition) {
        if (!scrollableState.isScrollInProgress) {
            val positionInSeconds = playlistPositionToSecondsOfDay(timeline, currentAbsolutePosition)
            val targetScrollOffset = (positionInSeconds * pxPerSecond) - (containerWidthPx / 2)
            scrollOffsetPx = targetScrollOffset.coerceIn(0f, (Duration.ofDays(1).seconds * pxPerSecond) - containerWidthPx)
        }
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
    ) {
        // --- Texto Centralizado com a Hora Atual ---
        Text(
            text = centerTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(4.dp)
        )

        // --- Container Principal com a Agulha ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TotalTimelineHeight)
                .scrollable(state = scrollableState, orientation = Orientation.Horizontal)
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
        ) {
            // --- Desenho da Timeline (Segmentos e Marcadores) ---
            TimelineCanvas(
                modifier = Modifier.fillMaxSize(),
                timeline = timeline,
                scrollOffsetPx = scrollOffsetPx,
                pxPerSecond = pxPerSecond,
                dayStart = dayStart
            )

            // --- Agulha Vermelha Fixa no Centro ---
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
                    color = if (segment.clips.any { it.frontVideo.isEvent }) Color(0xFFFFA500) else Color.Blue,
                    topLeft = Offset(startPx - scrollOffsetPx, 0f),
                    size = androidx.compose.ui.geometry.Size((endPx - startPx), SegmentsTrackHeight.toPx())
                )
            }
        }

        // --- Desenha os Marcadores de Tempo ---
        val hourInSeconds = 3600L
        for (second in 0..totalDayDurationSeconds step 15 * 60) { // Marca a cada 15 minutos
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


// --- Funções Auxiliares de Conversão de Tempo ---

// Converte um tempo absoluto do dia para a posição correspondente na playlist do ExoPlayer
private fun timeToPlaylistPosition(timeline: Timeline, time: LocalDateTime): Long {
    var positionMs = 0L
    for (segment in timeline.segments) {
        if (time in segment.startTime..segment.endTime) {
            positionMs += Duration.between(segment.startTime, time).toMillis()
            return positionMs
        }
        positionMs += segment.duration.toMillis()
    }
    // Se não encontrar, retorna a última posição
    return timeline.segments.sumOf { it.duration.toMillis() }
}

// Converte a posição da playlist do ExoPlayer para o segundo correspondente no dia
private fun playlistPositionToSecondsOfDay(timeline: Timeline, positionMs: Long): Long {
    var accumulatedDurationMs = 0L
    val dayStart = timeline.segments.firstOrNull()?.startTime?.toLocalDate()?.atStartOfDay() ?: return 0

    for (segment in timeline.segments) {
        val segmentDurationMs = segment.duration.toMillis()
        if (positionMs <= accumulatedDurationMs + segmentDurationMs) {
            val timeIntoSegment = positionMs - accumulatedDurationMs
            val actualTime = segment.startTime.plus(Duration.ofMillis(timeIntoSegment))
            return Duration.between(dayStart, actualTime).seconds
        }
        accumulatedDurationMs += segmentDurationMs
    }
    return 0
}