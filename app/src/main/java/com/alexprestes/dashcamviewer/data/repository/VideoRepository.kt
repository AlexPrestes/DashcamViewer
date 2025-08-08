package com.alexprestes.dashcamviewer.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.domain.model.CameraType
import com.alexprestes.dashcamviewer.domain.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object VideoRepository {

    private const val TAG = "VideoRepository"
    private val FILENAME_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val VIDEO_PATHS = listOf(
        "Normal/F",
        "Normal/I",
        "Event/F",
        "Event/I",
    )

    /**
     * Carrega todos os arquivos de vídeo de um diretório raiz de forma assíncrona.
     * Esta função é uma "suspend function", o que significa que deve ser chamada de uma coroutine.
     * Ela executa a operação de I/O de arquivos em uma thread de fundo (Dispatchers.IO).
     */
    suspend fun loadVideosFrom(context: Context, root: DocumentFile): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()

        for (path in VIDEO_PATHS) {
            val directory = findDirectory(root, path) ?: continue
            val isEvent = path.startsWith("Event")

            directory.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                if (!name.endsWith(".mp4", ignoreCase = true)) return@forEach

                val parsedInfo = parseFileName(name)
                if (parsedInfo == null) {
                    Log.w(TAG, "Could not parse file name: $name")
                    return@forEach
                }

                val duration = getVideoDuration(context, file)

                val (localTimestamp, cameraType) = parsedInfo
                val zonedDateTime = localTimestamp.atZone(ZoneId.systemDefault())

                videos.add(
                    VideoFile(
                        uri = file.uri,
                        name = name,
                        timestamp = zonedDateTime,
                        cameraType = cameraType,
                        isEvent = isEvent,
                        duration = duration // Usando a duração real
                    )
                )
            }
        }

        return@withContext videos.sortedBy { it.timestamp }
    }

    /**
     * Analisa o nome de um arquivo de vídeo.
     * Continua sendo uma função síncrona, pois é rápida e não faz I/O.
     */
    private fun getVideoDuration(context: Context, file: DocumentFile): Duration {
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                return Duration.ofMillis(durationStr?.toLongOrNull() ?: 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get duration for ${file.name}", e)
        } finally {
            retriever.release()
        }
        return Duration.ZERO // Retorna 0 se falhar
    }

    private fun parseFileName(fileName: String): Pair<LocalDateTime, CameraType>? {
        try {
            val parts = fileName.removeSuffix(".MP4").split('_')
            if (parts.size != 3) {
                // Log.w(TAG, "Invalid filename format: $fileName") // Comentado para não poluir o log
                return null
            }
            val timestampString = parts[0]
            val localDateTime = LocalDateTime.parse(timestampString, FILENAME_TIMESTAMP_FORMATTER)

            val cameraChar = parts[2].firstOrNull()
            val cameraType = when (cameraChar) {
                'F' -> CameraType.FRONT
                'I' -> CameraType.INSIDE
                else -> return null
            }

            return Pair(localDateTime, cameraType)
        } catch (e: Exception) {
            // Log.e(TAG, "Failed to parse filename: $fileName", e) // Comentado
            return null
        }
    }

    private fun findDirectory(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.split("/")
        var currentDir = root
        for (part in parts) {
            currentDir = currentDir.findFile(part) ?: return null
        }
        return if (currentDir.isDirectory) currentDir else null
    }
}