package com.alexprestes.dashcamviewer.domain.usecase

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.domain.model.Timeline
import com.alexprestes.dashcamviewer.domain.model.buildTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GetTimelineForVolumeUseCase(private val context: Context) {

    suspend fun execute(volumeName: String): Result<Timeline> {
        return withContext(Dispatchers.IO) {
            try {
                // Logic to find the DocumentFile for the given volumeName
                val storageVolumes = context.getExternalFilesDirs(null)
                var targetVolume: DocumentFile? = null

                for (volume in storageVolumes) {
                    val root = DocumentFile.fromFile(volume.parentFile?.parentFile?.parentFile?.parentFile!!)
                    if (root.name == volumeName) {
                        targetVolume = root
                        break
                    }
                }

                if (targetVolume != null) {
                    val timeline = buildTimeline(targetVolume)
                    Result.success(timeline)
                } else {
                    Result.failure(Exception("Volume not found: $volumeName"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
