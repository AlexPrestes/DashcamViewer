package com.alexprestes.dashcamviewer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

// TODO: These data classes should be moved to a more appropriate location (e.g., domain layer)
data class Timeline(
    val segments: List<RecordingSegment>,
    val totalDuration: Long
)

data class RecordingSegment(
    val clips: List<VideoClip>,
    val duration: Long,
    val gapToNext: Long
)

data class VideoClip(
    val path: String,
    val isEvent: Boolean
)

@Composable
fun TimelineView(timeline: Timeline, onSeek: (position: Long) -> Unit, currentPosition: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.Gray)
    ) {
        timeline.segments.forEach { segment ->
            val weight = segment.duration.toFloat() / timeline.totalDuration.toFloat()
            Box(
                modifier = Modifier
                    .weight(weight)
                    .background(if (segment.clips.any { it.isEvent }) Color.Yellow else Color.Blue)
                    .clickable {
                        // This is a simplified seek. A more accurate calculation would be needed
                        // based on the total width of the timeline and the click position.
                        val estimatedPosition =
                            (timeline.totalDuration * Random.nextFloat()).toLong()
                        onSeek(estimatedPosition)
                    }
            )
            if (segment.gapToNext > 0) {
                val gapWeight = segment.gapToNext.toFloat() / timeline.totalDuration.toFloat()
                Spacer(modifier = Modifier.width((gapWeight * 100).dp)) // This width calculation is incorrect and needs fixing
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(50.dp)) {
        val needlePosition = (currentPosition.toFloat() / timeline.totalDuration.toFloat()) * size.width
        drawLine(
            color = Color.Red,
            start = androidx.compose.ui.geometry.Offset(needlePosition, 0f),
            end = androidx.compose.ui.geometry.Offset(needlePosition, size.height),
            strokeWidth = 2f
        )
    }
}
