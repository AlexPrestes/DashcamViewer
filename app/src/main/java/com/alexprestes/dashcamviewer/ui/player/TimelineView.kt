package com.alexprestes.dashcamviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.collections.forEachIndexed
import kotlin.math.roundToLong

@Composable
fun TimelineView(
    timeline: com.alexprestes.dashcamviewer.domain.model.Timeline,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var timelineContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val totalDuration = timeline.totalDuration
    if (totalDuration <= 0) return

    // This factor converts milliseconds to pixels.
    // We adjust it by density to make it somewhat consistent across devices.
    val scaleFactor = 0.2f * density.density

    // Calculate the total width of the timeline content in pixels based on zoom
    val timelineContentWidthPx = totalDuration * zoomLevel * scaleFactor

    fun pxToMillis(pixelValue: Float): Long {
        return (pixelValue / (zoomLevel * scaleFactor)).roundToLong()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clipToBounds() // Prevents drawing outside the bounds
                .onSizeChanged { timelineContainerSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.5f, 5f)
                        val maxPan = (timelineContentWidthPx - timelineContainerSize.width).coerceAtLeast(0f)
                        panOffset = (panOffset + pan.x).coerceIn(-maxPan, 0f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val tappedPositionPx = -panOffset + offset.x
                            val newPositionMs = pxToMillis(tappedPositionPx)
                            onSeek(newPositionMs)
                        }
                    )
                }
        ) {
            // Timeline content (segments and gaps)
            Row(
                modifier = Modifier
                    .offset { IntOffset(panOffset.toInt(), 0) }
                    .width(with(density) { timelineContentWidthPx.toDp() })
                    .fillMaxHeight()
            ) {
                timeline.segments.forEachIndexed { index, segment ->
                    val segmentColor = if (segment.isEvent) Color.Red else Color.Blue
                    val segmentWidthPx = segment.duration * zoomLevel * scaleFactor
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(with(density) { segmentWidthPx.toDp() })
                            .background(segmentColor)
                    )

                    if (index < timeline.segments.lastIndex) {
                        val nextSegment = timeline.segments[index + 1]
                        val gapDuration = nextSegment.startTime - segment.endTime
                        if (gapDuration > 0) {
                            val gapWidthPx = gapDuration * zoomLevel * scaleFactor
                            Spacer(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(with(density) { gapWidthPx.toDp() })
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }

            // Current position indicator
            val indicatorPositionPx = (currentPosition * zoomLevel * scaleFactor) + panOffset
            // Only show indicator if it's within the visible area
            if (indicatorPositionPx >= 0 && indicatorPositionPx <= timelineContainerSize.width) {
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset { IntOffset(indicatorPositionPx.toInt(), 0) }
                        .background(Color.White)
                )
            }
        }

        // Zoom controls
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            IconButton(onClick = { zoomLevel = (zoomLevel * 0.8f).coerceIn(0.5f, 5f) }) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
            IconButton(onClick = { zoomLevel = (zoomLevel * 1.25f).coerceIn(0.5f, 5f) }) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
        }
    }
}
