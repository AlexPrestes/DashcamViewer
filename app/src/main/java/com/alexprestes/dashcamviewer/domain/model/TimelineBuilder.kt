package com.alexprestes.dashcamviewer.domain.model

import android.content.Context
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
suspend fun buildTimeline(context: Context, documentFile: DocumentFile): Timeline = withContext(Dispatchers.Default) {
    // Passa o context para o repositório
    val allVideos = VideoRepository.loadVideosFrom(context, documentFile)
    if (allVideos.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    val clipsMap = allVideos.groupBy { it.timestamp }
    val videoClips = clipsMap.mapNotNull { (zonedDateTime, videos) ->
        val frontVideo = videos.find { it.cameraType == CameraType.FRONT }
        if (frontVideo != null) {
            val rearVideo = videos.find { it.cameraType == CameraType.INSIDE }
            // CORREÇÃO APLICADA AQUI: Usando a duração real do arquivo frontal
            VideoClip(frontVideo, rearVideo, zonedDateTime, frontVideo.duration)
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
        // Considera um pequeno buffer de 1 segundo para juntar clipes
        val gap = Duration.between(prevClip.startTime.plus(prevClip.duration), currentClip.startTime)

        if (gap.seconds > 1) { // Se o gap for maior que 1s, cria novo segmento
            segments.add(createSegment(currentSegmentClips))
            currentSegmentClips = mutableListOf(currentClip)
        } else {
            currentSegmentClips.add(currentClip)
        }
    }
    segments.add(createSegment(currentSegmentClips))

    val earliest = segments.first().startTime
    val latest = segments.last().endTime
    val totalDuration = segments.fold(Duration.ZERO) { acc, segment -> acc.plus(segment.duration) }

    return@withContext Timeline(segments, earliest, latest, totalDuration)
}

/**
 * Função auxiliar para criar um RecordingSegment a partir de uma lista de clipes.
 */
private fun createSegment(clips: List<VideoClip>): RecordingSegment {
    val startTime = clips.first().startTime
    // CORREÇÃO APLICADA AQUI: O fim é o início do último clipe mais a sua duração real
    val endTime = clips.last().startTime.plus(clips.last().duration)
    return RecordingSegment(
        clips = clips,
        startTime = startTime.toLocalDateTime(),
        endTime = endTime.toLocalDateTime(),
        duration = Duration.between(startTime, endTime)
    )
}