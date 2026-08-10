package com.musicdownloader.downloader

import android.content.Context
import android.util.Log
import com.musicdownloader.data.AudioTagWriter
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.toLocalSong
import com.musicdownloader.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AudioDownloader(private val context: Context) {

    private val musicRepository by lazy { MusicRepository(context) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadAudio(
        audioUrl: String,
        mimeType: String,
        song: Song,
        outputDir: File,
        outputFileName: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.e(TAG, "INICIO descarga: ${audioUrl.take(80)}...")

            if (!outputDir.exists()) outputDir.mkdirs()

            val ext = detectExtension(mimeType)
            val base = outputFileName ?: song.fileName
            val name = "$base.$ext"
            val tempFile = File(outputDir, "${name}_temp")
            val finalFile = File(outputDir, name)

            if (finalFile.exists()) {
                if (finalFile.length() > 0) {
                    Log.e(TAG, "Ya existe: ${finalFile.name} (${finalFile.length() / 1024}KB)")
                    return@withContext Result.success(finalFile)
                } else {
                    Log.e(TAG, "Archivo vacio previo, borrando: ${finalFile.name}")
                    finalFile.delete()
                }
            }
            tempFile.delete()

            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body
            val code = response.code

            Log.e(TAG, "HTTP $code len=${body?.contentLength()} type=${body?.contentType()}")

            if (code != 200) {
                val err = try { body?.string()?.take(300) } catch (_: Exception) { null }
                Log.e(TAG, "HTTP $code body=${err ?: "vacio"}")
                response.close()
                return@withContext Result.failure(Exception("HTTP $code"))
            }

            val totalBytes = body?.contentLength() ?: -1L
            var downloadedBytes = 0L

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
            response.close()

            if (downloadedBytes == 0L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("0 bytes descargados"))
            }

            val resultFile = finalizeFile(tempFile, finalFile)
            writeMetadata(resultFile, song)

            Log.e(TAG, "OK: ${resultFile.name} (${resultFile.length() / 1024}KB)")
            Result.success(resultFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloadAudio: ${e.message}")
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

    private fun writeMetadata(file: File, song: Song) {
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

            val localSong = song.toLocalSong().copy(
                filePath = file.absolutePath,
                thumbnailUrl = artPath ?: song.thumbnailUrl
            )

            AudioTagWriter.writeTags(file, localSong)
        } catch (e: Exception) {
            Log.e(TAG, "Error writeMetadata en descarga: ${file.name} - ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MusicDownloader"
    }
}
