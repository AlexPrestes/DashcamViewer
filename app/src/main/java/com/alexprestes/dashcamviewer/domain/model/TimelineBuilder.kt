package com.alexprestes.dashcamviewer.domain.model

import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

private const val MAX_GAP_SECONDS = 60L

/**
 * Constrói um objeto Timeline a partir de um DocumentFile, de forma assíncrona.
 * Primeiro, carrega os vídeos (I/O), depois processa a lista (CPU).
 */
suspend fun buildTimeline(documentFile: DocumentFile): Timeline = withContext(Dispatchers.Default) {
    // 1. Carregar os vídeos (operação de I/O, já tratada no repositório)
    val allVideos = VideoRepository.loadVideosFrom(documentFile)
    if (allVideos.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    // 2. Agrupar vídeos frontais (F) e traseiros (I) pelo mesmo timestamp.
    val clipsMap = allVideos.groupBy { it.timestamp }
    val videoClips = clipsMap.mapNotNull { (zonedDateTime, videos) ->
        val frontVideo = videos.find { it.cameraType == CameraType.FRONT }
        if (frontVideo != null) {
            val rearVideo = videos.find { it.cameraType == CameraType.INSIDE }
            // A duração será ajustada no futuro para usar a duração real do vídeo.
            VideoClip(frontVideo, rearVideo, zonedDateTime, Duration.ofMinutes(1))
        } else {
            null
        }
    }.sortedBy { it.startTime }

    if (videoClips.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    // 3. Montar os segmentos de gravação contínua.
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

    // 4. Montar o objeto Timeline final.
    val earliest = segments.first().startTime
    val latest = segments.last().endTime
    val totalDuration = Duration.between(earliest, latest)

    return@withContext Timeline(segments, earliest, latest, totalDuration)
}

/**
 * Função auxiliar para criar um RecordingSegment a partir de uma lista de clipes.
 */
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
