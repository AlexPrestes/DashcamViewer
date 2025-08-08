package com.alexprestes.dashcamviewer.domain.usecase

import android.content.Context
import com.alexprestes.dashcamviewer.data.repository.VideoRepository
import com.alexprestes.dashcamviewer.domain.model.Timeline
import com.alexprestes.dashcamviewer.domain.model.buildTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetTimelineForVolumeUseCase(private val context: Context) {

    suspend fun execute(volumeName: String): Result<Timeline> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Chama o repositório para carregar os vídeos do volume.
                val videos = VideoRepository.loadVideosFrom(context, volumeName)

                // 2. Chama o builder para construir a timeline com a lista de vídeos.
                val timeline = buildTimeline(videos)

                Result.success(timeline)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}