package com.musicdownloader.downloader

import android.content.Context
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
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AudioDownloader(private val context: Context) {

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

            val name = outputFileName ?: "${song.fileName}.mp3"
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

            val mp3File = convertToMp3(tempFile, finalFile)
            if (mp3File.exists()) tempFile.delete()

            // Validar que sea un MP3 real; si el proxy devolvió data corrupta o de otro
            // formato, fallar limpio en vez de marcar éxito con un archivo inservible.
            if (!isValidMp3(mp3File)) {
                mp3File.delete()
                Log.e(TAG, "Archivo inválido/corrupto tras descarga: ${mp3File.name}")
                return@withContext Result.failure(Exception("Archivo corrupto o formato no soportado"))
            }

            writeMetadata(mp3File, song)

            Log.e(TAG, "OK: ${mp3File.name} (${mp3File.length() / 1024}KB)")
            Result.success(mp3File)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloadAudio: ${e.message}")
            Result.failure(e)
        }
    }

    private fun isValidMp3(file: File): Boolean {
        return try {
            java.io.FileInputStream(file).use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                if (read < 2) return false
                // ID3v2
                if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) return true
                // Frame sync de MPEG: 0xFF 0xE0-0xFF
                header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun convertToMp3(input: File, output: File): File {
        return if (output.exists()) output
        else {
            input.copyTo(output, overwrite = true)
            output
        }
    }

    private fun writeMetadata(file: File, song: Song) {
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setEncoding(StandardCharsets.UTF_16)
            setField(tag, FieldKey.TITLE, song.title)
            setField(tag, FieldKey.ARTIST, song.artist)
            setField(tag, FieldKey.ALBUM, song.album)
            setField(tag, FieldKey.GENRE, com.musicdownloader.metadata.MetadataFetcher.sanitizeGenre(song.genre))
            setField(tag, FieldKey.YEAR, song.year)
            if (song.trackNumber > 0) setField(tag, FieldKey.TRACK, song.trackNumber.toString())
            if (song.lyrics.isNotBlank()) setField(tag, FieldKey.LYRICS, song.lyrics)
            if (song.thumbnailUrl.isNotBlank()) {
                try { embedArtwork(tag, song.thumbnailUrl) } catch (e: Exception) {
                    Log.e(TAG, "Error artwork: ${e.message}")
                }
            }
            try {
                AudioFileIO.write(audioFile)
                Log.e(TAG, "Tags escritos en descarga: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error AudioFileIO.write en descarga: ${file.name} - ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writeMetadata en descarga: ${file.name} - ${e.message}")
        }
    }

    private fun setField(tag: org.jaudiotagger.tag.Tag, key: FieldKey, value: String) {
        try {
            if (value.isNotBlank()) tag.setField(key, value)
        } catch (_: Exception) {}
    }

    private fun embedArtwork(tag: org.jaudiotagger.tag.Tag, thumbnailUrl: String) {
        try {
            val artwork = when {
                thumbnailUrl.startsWith("http") -> {
                    val bytes = downloadArtworkBytes(thumbnailUrl) ?: return
                    val tmp = File.createTempFile("artwork", ".jpg", context.cacheDir)
                    tmp.writeBytes(bytes)
                    ArtworkFactory.createArtworkFromFile(tmp).also { tmp.delete() }
                }
                else -> {
                    val file = File(thumbnailUrl)
                    if (!file.exists()) return
                    ArtworkFactory.createArtworkFromFile(file)
                }
            }
            tag.deleteArtworkField()
            tag.setField(artwork)
        } catch (_: Exception) {}
    }

    private fun downloadArtworkBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "MusicDownloader"
    }
}
