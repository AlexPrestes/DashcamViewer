package com.alexprestes.dashcamviewer.domain.usecase

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.data.repository.VideoRepository // Importe o repositório
import com.alexprestes.dashcamviewer.domain.model.VolumeInfo
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
            try {
                val volumeRoot = getVolumeRootFromAppDir(dir) ?: continue
                val documentFile = DocumentFile.fromFile(volumeRoot)
                val name = volumeRoot.name.takeIf { !it.isNullOrBlank() } ?: "External Storage"

                // --- INÍCIO DA CORREÇÃO ---
                // Usamos o VideoRepository para carregar os vídeos e depois contamos o tamanho da lista.
                val videos = VideoRepository.loadVideosFrom(context, name)
                val videoCount = videos.size
                // --- FIM DA CORREÇÃO ---

                // Cria a instância final com a contagem correta
                if (videoCount > 0) {
                    volumes.add(VolumeInfo(documentFile, name, videoCount))
                }
            } catch (e: Exception) {
                // Ignora volumes que não podem ser acessados (como a memória interna sem as pastas)
            }
        }

        return@withContext volumes
    }

    private fun getVolumeRootFromAppDir(appDir: File?): File? {
        var current = appDir ?: return null
        // Navega para cima na árvore de diretórios para encontrar a raiz do volume.
        repeat(4) {
            current = current.parentFile ?: return null
        }
        return current
    }
}