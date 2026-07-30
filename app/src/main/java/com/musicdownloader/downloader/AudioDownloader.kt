package com.musicdownloader.downloader

import android.util.Log
import com.musicdownloader.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AudioDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadAudio(
        audioUrl: String,
        song: Song,
        outputDir: File,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Descargando audio: $audioUrl")
            Log.d(TAG, "Directorio: ${outputDir.absolutePath}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                Log.d(TAG, "Directorio creado: ${outputDir.exists()}")
            }

            val ext = detectExtension(audioUrl)
            val tempFile = File(outputDir, "${song.fileName}_temp$ext")
            val finalFile = File(outputDir, "${song.fileName}.mp3")

            if (finalFile.exists()) {
                return@withContext Result.success(finalFile)
            }

            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            val buffer = ByteArray(8192)
            val fileStream = FileOutputStream(tempFile)
            val inputStream = body.byteStream()

            inputStream.use { input ->
                fileStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            onProgress(progress.coerceIn(0, 100))
                        }
                    }
                }
            }

            val mp3File = convertToMp3(tempFile, finalFile)
            if (mp3File.exists()) {
                tempFile.delete()
            }

            writeMetadata(mp3File, song)

            Result.success(mp3File)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertToMp3(input: File, output: File): File {
        return if (input.extension == "mp3" || input.extension == "m4a") {
            if (output.exists()) output
            else {
                input.copyTo(output, overwrite = true)
                output
            }
        } else {
            if (output.exists()) output
            else {
                input.copyTo(output, overwrite = true)
                output
            }
        }
    }

    private fun writeMetadata(file: File, song: Song) {
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, song.title)
            tag.setField(FieldKey.ARTIST, song.artist)
            tag.setField(FieldKey.ALBUM, song.album)
            tag.setField(FieldKey.GENRE, song.genre)
            tag.setField(FieldKey.YEAR, song.year)
            if (song.trackNumber > 0) {
                tag.setField(FieldKey.TRACK, song.trackNumber.toString())
            }

            if (song.thumbnailUrl.isNotBlank()) {
                try {
                    val artwork = ArtworkFactory.createLinkedArtworkFromURL(song.thumbnailUrl)
                    tag.setField(artwork)
                } catch (_: Exception) {}
            }

            AudioFileIO.write(audioFile)
        } catch (_: Exception) {}
    }

    private fun detectExtension(url: String): String {
        val path = url.substringBefore("?").substringAfterLast("/")
        return when {
            path.contains(".mp3") -> ".mp3"
            path.contains(".m4a") || path.contains(".aac") -> ".m4a"
            path.contains(".webm") -> ".webm"
            path.contains(".opus") || path.contains(".ogg") -> ".opus"
            else -> ".m4a"
        }
    }

    companion object {
        private const val TAG = "MusicDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
    }
}
