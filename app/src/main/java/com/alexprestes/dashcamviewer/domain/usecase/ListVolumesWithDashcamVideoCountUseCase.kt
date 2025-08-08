package com.alexprestes.dashcamviewer.domain.usecase

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.domain.model.VolumeInfo
import com.alexprestes.dashcamviewer.domain.model.buildTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ListVolumesWithDashcamVideoCountUseCase(private val context: Context) {

    /**
     * Executa o caso de uso de forma assíncrona para listar os volumes e contar os vídeos.
     * Esta função deve ser chamada a partir de uma coroutine.
     */
    suspend fun execute(): List<VolumeInfo> = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<VolumeInfo>()
        val externalDirs = context.getExternalFilesDirs(null)

        for (dir in externalDirs) {
            val volumeRoot = getVolumeRootFromAppDir(dir) ?: continue
            val documentFile = DocumentFile.fromFile(volumeRoot)
            val name = volumeRoot.name.takeIf { !it.isNullOrBlank() } ?: "External Storage"

            // Chama a função suspend para construir a timeline.
            val timeline = buildTimeline(context, documentFile)
            val videoCount = timeline.segments.sumOf { it.clips.size }

            // Cria a instância final com a contagem correta
            if (videoCount > 0) {
                volumes.add(VolumeInfo(documentFile, name, videoCount))
            }
        }

        return@withContext volumes
    }

    private fun getVolumeRootFromAppDir(appDir: File?): File? {
        var current = appDir ?: return null
        // Navega para cima na árvore de diretórios para encontrar a raiz do volume.
        // O número de vezes pode precisar de ajuste dependendo da versão do Android.
        repeat(4) {
            current = current.parentFile ?: return null
        }
        return current
    }
}
