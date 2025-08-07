package com.alexprestes.dashcamviewer.model

data class Timeline(
    val segments: List<RecordingSegment>
) {
    val totalDuration: Long by lazy {
        segments.lastOrNull()?.endTime ?: 0L
    }
}

data class RecordingSegment(
    val startTime: Long,
    val endTime: Long,
    val isEvent: Boolean,
    val frontVideoPath: String,
    val insideVideoPath: String
) {
    val duration: Long get() = endTime - startTime
}
