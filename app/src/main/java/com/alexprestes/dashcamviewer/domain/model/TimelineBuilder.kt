package com.alexprestes.dashcamviewer.domain.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

private const val MAX_GAP_SECONDS = 60L

// --- INÍCIO DA CORREÇÃO ---
// A função agora recebe a lista de vídeos pronta, em vez de um DocumentFile.
suspend fun buildTimeline(allVideos: List<VideoFile>): Timeline = withContext(Dispatchers.Default) {
// --- FIM DA CORREÇÃO ---

    if (allVideos.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    val clipsMap = allVideos.groupBy { it.timestamp }
    val videoClips = clipsMap.mapNotNull { (zonedDateTime, videos) ->
        val frontVideo = videos.find { it.cameraType == CameraType.FRONT }
        if (frontVideo != null) {
            val rearVideo = videos.find { it.cameraType == CameraType.INSIDE }
            VideoClip(frontVideo, rearVideo, zonedDateTime, Duration.ofMinutes(1))
        } else {
            null
        }
    }.sortedBy { it.startTime }

    if (videoClips.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    val segments = mutableListOf<RecordingSegment>()
    var currentSegmentClips = mutableListOf(videoClips.first())

    for (i in 1 until videoClips.size) {
        val prevClip = videoClips[i - 1]
        val currentClip = videoClips[i]
        val gap = Duration.between(prevClip.startTime.plus(prevClip.duration), currentClip.startTime)

        if (gap.seconds > MAX_GAP_SECONDS) {
            segments.add(createSegment(currentSegmentClips))
            currentSegmentClips = mutableListOf(currentClip)
        } else {
            currentSegmentClips.add(currentClip)
        }
    }
    segments.add(createSegment(currentSegmentClips))

    val earliest = segments.first().startTime
    val latest = segments.last().endTime

    // --- CORREÇÃO DO CRASH ---
    // Calcula a duração total somando a duração de cada segmento.
    val totalDuration = segments.fold(Duration.ZERO) { acc, segment -> acc.plus(segment.duration) }

    return@withContext Timeline(segments, earliest, latest, totalDuration)
}

private fun createSegment(clips: List<VideoClip>): RecordingSegment {
    val startTime = clips.first().startTime
    val endTime = clips.last().startTime.plus(clips.last().duration)
    return RecordingSegment(
        clips = clips,
        startTime = startTime.toLocalDateTime(),
        endTime = endTime.toLocalDateTime(),
        duration = Duration.between(startTime, endTime)
    )
}