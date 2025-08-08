package com.alexprestes.dashcamviewer.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.alexprestes.dashcamviewer.domain.model.CameraType
import com.alexprestes.dashcamviewer.domain.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

    // --- INÍCIO DA CORREÇÃO ---
    // A função agora recebe o nome do volume (String) e se encarrega de encontrar a pasta.
    suspend fun loadVideosFrom(context: Context, volumeName: String): List<VideoFile> = withContext(Dispatchers.IO) {
        val root = findVolumeRoot(context, volumeName)
            ?: throw Exception("Volume '$volumeName' não encontrado. Verifique as permissões e o nome do volume.")
        // --- FIM DA CORREÇÃO ---

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

                // Usaremos a duração real no futuro, por enquanto mantemos o placeholder
                // val duration = getVideoDuration(context, file)
                val duration = Duration.ofMinutes(1)

                val (localTimestamp, cameraType) = parsedInfo
                val zonedDateTime = localTimestamp.atZone(ZoneId.systemDefault())

                videos.add(
                    VideoFile(
                        uri = file.uri,
                        name = name,
                        timestamp = zonedDateTime,
                        cameraType = cameraType,
                        isEvent = isEvent,
                        duration = duration // Adicionaremos este campo depois
                    )
                )
            }
        }

        return@withContext videos.sortedBy { it.timestamp }
    }

    // --- INÍCIO DA NOVA FUNÇÃO AUXILIAR ---
    private fun findVolumeRoot(context: Context, volumeName: String): DocumentFile? {
        val externalDirs = context.getExternalFilesDirs(null)
        for (dir in externalDirs) {
            if (dir != null) {
                // Sobe na árvore de diretórios para encontrar a raiz do volume.
                var current: File? = dir
                repeat(4) {
                    current = current?.parentFile
                }
                if (current?.name == volumeName) {
                    return DocumentFile.fromFile(current!!)
                }
            }
        }
        return null // Retorna null se não encontrar
    }
    // --- FIM DA NOVA FUNÇÃO AUXILIAR ---

    // (O resto do arquivo: getVideoDuration, parseFileName, findDirectory permanecem iguais)
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
        return Duration.ZERO
    }

    private fun parseFileName(fileName: String): Pair<LocalDateTime, CameraType>? {
        try {
            val parts = fileName.removeSuffix(".MP4").split('_')
            if (parts.size != 3) {
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