package com.alexprestes.dashcamviewer.domain.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

private const val MAX_GAP_SECONDS = 1L // Uma tolerância pequena e justa

suspend fun buildTimeline(allVideos: List<VideoFile>): Timeline = withContext(Dispatchers.Default) {

    if (allVideos.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    // 1. Agrupa vídeos por timestamp (Frente e Interna)
    val initialClips = allVideos.groupBy { it.timestamp }
        .mapNotNull { (zonedDateTime, videos) ->
            val frontVideo = videos.find { it.cameraType == CameraType.FRONT }
            if (frontVideo != null) {
                val rearVideo = videos.find { it.cameraType == CameraType.INSIDE }
                VideoClip(frontVideo, rearVideo, zonedDateTime, frontVideo.duration)
            } else {
                null
            }
        }.sortedBy { it.startTime }

    if (initialClips.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    // 2. Garante que cada clipe tenha uma duração válida (LÓGICA CORRIGIDA)
    val refinedClips = initialClips.mapIndexed { index, clip ->
        val finalDuration: Duration

        // A REGRA DE OURO: Se a duração lida do arquivo é maior que zero, CONFIE NELA.
        if (!clip.duration.isZero() && !clip.duration.isNegative) {
            finalDuration = clip.duration
        } else {
            // Se a leitura do metadado falhou (duração é zero), tentamos um fallback inteligente.
            val nextClip = initialClips.getOrNull(index + 1)
            if (nextClip != null) {
                val gapToNext = Duration.between(clip.startTime, nextClip.startTime)
                // Se o gap for razoável (sugere um clipe padrão de 1 min), usamos ele.
                if (!gapToNext.isNegative && gapToNext.seconds > 0 && gapToNext.seconds <= 65) {
                    finalDuration = gapToNext
                } else {
                    // Se o gap for muito grande, é uma parada real. Assumimos 1 min como padrão para o clipe sem duração.
                    finalDuration = Duration.ofMinutes(1)
                }
            } else {
                // É o último clipe e não tem duração. Nossa única opção é assumir 1 minuto.
                finalDuration = Duration.ofMinutes(1)
            }
        }
        clip.copy(duration = finalDuration)
    }

    // 3. Monta os segmentos contínuos
    val segments = mutableListOf<RecordingSegment>()
    if (refinedClips.isNotEmpty()) {
        var currentSegmentClips = mutableListOf(refinedClips.first())

        for (i in 1 until refinedClips.size) {
            val prevClip = refinedClips[i - 1]
            val currentClip = refinedClips[i]
            val timeAfterPrevClip = prevClip.startTime.plus(prevClip.duration)
            val gap = Duration.between(timeAfterPrevClip, currentClip.startTime)

            if (gap.abs().seconds > MAX_GAP_SECONDS) {
                segments.add(createSegment(currentSegmentClips))
                currentSegmentClips = mutableListOf(currentClip)
            } else {
                currentSegmentClips.add(currentClip)
            }
        }
        if (currentSegmentClips.isNotEmpty()) {
            segments.add(createSegment(currentSegmentClips))
        }
    }


    if (segments.isEmpty()) {
        return@withContext Timeline(emptyList(), null, null, Duration.ZERO)
    }

    val earliest = segments.first().startTime
    val latest = segments.last().endTime
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