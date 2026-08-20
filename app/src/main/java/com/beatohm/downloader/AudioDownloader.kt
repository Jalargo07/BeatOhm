package com.beatohm.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.navigation.NavDeepLinkBuilder
import com.beatohm.R
import com.beatohm.data.AppDatabase
import com.beatohm.data.LibraryRepository
import com.beatohm.data.LocalSong
import com.beatohm.data.MetadataCandidateRepository
import com.beatohm.data.TagWriteCoordinator
import com.beatohm.data.toLocalSong
import com.beatohm.metadata.MetadataFetcher
import com.beatohm.metadata.MetadataResult
import com.beatohm.model.Song
import com.beatohm.util.AppLogger
import com.beatohm.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AudioDownloader(private val context: Context) {

    private val musicRepository by lazy { LibraryRepository(context) }
    private val metadataFetcher by lazy { MetadataFetcher() }
    private val metadataCandidateRepo by lazy {
        MetadataCandidateRepository(AppDatabase.getInstance(context).metadataCandidateDao())
    }
    private val tagWriteCoordinator = TagWriteCoordinator()

    init {
        createMetadataNotificationChannel()
    }

    private val client = NetworkModule.newClient(
        connectTimeoutSec = 30,
        readTimeoutSec = 300,
        writeTimeoutSec = 300
    )

    suspend fun downloadAudio(
        audioUrl: String,
        mimeType: String,
        song: Song,
        outputDir: File,
        outputFileName: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "download start: ${mimeType}, output=${outputDir.name}")

            if (!outputDir.exists()) outputDir.mkdirs()

            val ext = detectExtension(mimeType)
            val base = outputFileName ?: song.fileName
            val name = "$base.$ext"
            val tempFile = File(outputDir, "${name}_temp")
            val finalFile = File(outputDir, name)

            if (finalFile.exists()) {
                if (finalFile.length() > 0) {
                    AppLogger.d(TAG, "already exists: ${finalFile.name} (${finalFile.length() / 1024}KB)")
                    return@withContext Result.success(finalFile)
                } else {
                    AppLogger.d(TAG, "empty prior file, deleting: ${finalFile.name}")
                    finalFile.delete()
                }
            }
            tempFile.delete()

            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            var downloadedBytes = 0L

            client.newCall(request).execute().use { response ->
                val body = response.body
                val code = response.code

                AppLogger.d(TAG, "HTTP $code contentLength=${body?.contentLength()} type=${body?.contentType()}")

                if (code != 200) {
                    val errLen = try { body?.string()?.length } catch (_: Exception) { null }
                    Log.e(TAG, "HTTP $code bodyLen=${errLen ?: "empty"}")
                    return@withContext Result.failure(Exception("HTTP $code"))
                }

                val totalBytes = body?.contentLength() ?: -1L

                FileOutputStream(tempFile).use { output ->
                    body!!.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            if (downloadedBytes == 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("0 bytes descargados"))
            }

            val resultFile = finalizeFile(tempFile, finalFile)
            writeMetadata(resultFile, song)

            AppLogger.d(TAG, "OK: ${resultFile.name} (${resultFile.length() / 1024}KB)")
            Result.success(resultFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadAudio error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun finalizeFile(temp: File, final: File): File {
        if (final.exists()) return final
        if (temp.renameTo(final)) return final
        temp.copyTo(final, overwrite = true)
        temp.delete()
        return final
    }

    private fun detectExtension(mimeType: String): String {
        val mime = mimeType.lowercase()
        return when {
            "opus" in mime -> "opus"
            "ogg" in mime -> "ogg"
            "mp4" in mime || "m4a" in mime || "mp4a" in mime || "aac" in mime -> "m4a"
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "flac" in mime -> "flac"
            "wav" in mime -> "wav"
            "webm" in mime -> "webm"
            else -> "mp3"
        }
    }

    private suspend fun writeMetadata(file: File, song: Song) {
        try {
            var artUrl = song.thumbnailUrl
            var artPath: String? = null

            if (artUrl.isNotBlank() && (artUrl.startsWith("http://") || artUrl.startsWith("https://"))) {
                val artFileName = "art_${song.youtubeId.ifBlank { file.name.hashCode().toString() }}.jpg"
                val artFile = File(context.cacheDir, artFileName)
                try {
                    musicRepository.downloadArtwork(artUrl, artFile)
                    if (artFile.exists() && artFile.length() > 0) {
                        artPath = artFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading artwork: ${e.message}")
                }
            } else if (artUrl.isNotBlank() && File(artUrl).exists()) {
                artPath = artUrl
            }

            // Buscar metadata con el algoritmo de 5 fuentes
            val result = metadataFetcher.fetchFullMetadata(song)

            when (result) {
                is MetadataResult.ClearMatch -> {
                    val candidate = result.candidate
                    val localSong = song.toLocalSong().copy(
                        title = candidate.title.ifBlank { song.title },
                        artist = candidate.artist.ifBlank { song.artist },
                        album = candidate.album.ifBlank { song.album },
                        year = candidate.year.ifBlank { song.year },
                        genre = candidate.genre.ifBlank { song.genre },
                        filePath = file.absolutePath,
                        thumbnailUrl = artPath ?: candidate.artworkUrl.ifBlank { song.thumbnailUrl }
                    )
                    tagWriteCoordinator.writeMetadata(file, localSong)
                    renameFileIfNeeded(file, localSong)
                }
                is MetadataResult.AmbiguousMatches -> {
                    // 1. Guardar metadata básica primero
                    val localSong = song.toLocalSong().copy(
                        filePath = file.absolutePath,
                        thumbnailUrl = artPath ?: song.thumbnailUrl
                    )
                    tagWriteCoordinator.writeMetadata(file, localSong)

                    // 2. Persistir candidatos para que el usuario elija
                    metadataCandidateRepo.saveCandidates(localSong.id, result.candidates)
                    Log.d(TAG, "writeMetadata: ambiguo '${song.title}' → candidatos guardados (${result.candidates.size})")

                    // 3. Mostrar notificación
                    showMetadataNotification(localSong)
                }
                is MetadataResult.NoMatch -> {
                    // Sin resultados — usar metadata básica
                    val localSong = song.toLocalSong().copy(
                        filePath = file.absolutePath,
                        thumbnailUrl = artPath ?: song.thumbnailUrl
                    )
                    tagWriteCoordinator.writeMetadata(file, localSong)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writeMetadata for ${file.name}: ${e.message}")
        }
    }

    /**
     * Renombra el archivo si la metadata trae artist/título distintos al nombre actual.
     * Patrón idéntico a MusicRepository.renameSongFile.
     */
    private fun renameFileIfNeeded(oldFile: File, song: LocalSong) {
        val newFileName = "${Song.fixMojibake(song.artist)} - ${Song.fixMojibake(song.title)}"
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val newFile = File(oldFile.parent, "${newFileName}.${oldFile.extension}")
        if (newFile.absolutePath == oldFile.absolutePath) return
        if (newFile.exists()) {
            Log.w(TAG, "renameFileIfNeeded: target already exists '${newFile.name}', skipping")
            return
        }
        if (oldFile.renameTo(newFile)) {
            Log.d(TAG, "renameFileIfNeeded: '${oldFile.name}' → '${newFile.name}'")
        } else {
            Log.w(TAG, "renameFileIfNeeded: rename failed for '${oldFile.name}'")
        }
    }

    private fun createMetadataNotificationChannel() {
        val channel = NotificationChannel(
            METADATA_CHANNEL_ID,
            context.getString(R.string.metadata_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.metadata_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun showMetadataNotification(song: LocalSong) {
        val pendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .addDestination(R.id.metadataCandidatesFragment)
            .createPendingIntent()

        val notification = NotificationCompat.Builder(context, METADATA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle(context.getString(R.string.metadata_notification_title))
            .setContentText(context.getString(R.string.metadata_notification_message, song.title))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = song.id.hashCode()
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "BeatOhm"
        private const val METADATA_CHANNEL_ID = "metadata_channel"
    }
}
