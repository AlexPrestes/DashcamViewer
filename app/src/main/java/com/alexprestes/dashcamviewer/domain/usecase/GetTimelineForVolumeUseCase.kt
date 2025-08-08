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
                // This logic might need to be adjusted based on how storage volumes are accessed
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                var targetVolume: DocumentFile? = null

                for (volume in storageManager.storageVolumes) {
                    val root = DocumentFile.fromTreeUri(context, volume.createOpenDocumentTreeIntent().data!!)
                    if (volume.getDescription(context) == volumeName || root?.name == volumeName) {
                        // A better way to get the root DocumentFile might be needed
                        // This is a common point of failure depending on Android version and permissions
                        // For now, we'll assume a simplified (but potentially fragile) way to get the root
                        val intent = volume.createOpenDocumentTreeIntent()
                        // This would require user interaction, so we need a different approach for background loading.
                        // A more robust solution involves SAF (Storage Access Framework) URIs saved from user selection.

                        // Let's revert to a simpler file-based approach if possible, assuming broad file access
                        val externalFilesDirs = context.getExternalFilesDirs(null)
                        val volFile = externalFilesDirs.firstOrNull { it.absolutePath.contains(volumeName) }
                        if (volFile != null) {
                            targetVolume = DocumentFile.fromFile(volFile.parentFile?.parentFile?.parentFile?.parentFile!!)
                            if (targetVolume.name == volumeName) {
                                break
                            }
                        }
                    }
                }

                // Temporary fallback for emulators/common paths
                if (targetVolume == null) {
                    val storageVolumes = context.getExternalFilesDirs(null)
                    for (volume in storageVolumes) {
                        if (volume != null) {
                            val root = DocumentFile.fromFile(volume.parentFile?.parentFile?.parentFile?.parentFile!!)
                            if (root.name == volumeName) {
                                targetVolume = root
                                break
                            }
                        }
                    }
                }


                if (targetVolume != null) {
                    // CORREÇÃO APLICADA AQUI: Passando o context para o buildTimeline
                    val timeline = buildTimeline(context, targetVolume)
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