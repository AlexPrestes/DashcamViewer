package com.alexprestes.dashcamviewer.domain.model

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * O objeto principal que representa toda a linha do tempo de um volume.
 * Contém uma lista de segmentos de gravação contínua.
 */
data class Timeline(
    val segments: List<RecordingSegment>,
    val earliestTimestamp: LocalDateTime?,
    val latestTimestamp: LocalDateTime?,
    val totalDuration: Duration
)

/**
 * Representa um bloco de gravação contínuo, sem interrupções significativas.
 * Contém uma lista de clipes individuais que compõem este segmento.
 * Perfeito para ser renderizado como um único bloco em uma UI de timeline "zoom out".
 */
data class RecordingSegment(
    val clips: List<VideoClip>,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val duration: Duration
)

/**
 * A unidade mais granular: um único "momento" de gravação,
 * geralmente consistindo de um vídeo frontal e um traseiro.
 */
data class VideoClip(
    val frontVideo: VideoFile,
    val rearVideo: VideoFile?, // Vídeo traseiro é opcional
    val startTime: ZonedDateTime,
    val duration: Duration // Ex: 1 minuto por clipe
)
