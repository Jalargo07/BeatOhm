package com.beatohm.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import com.beatohm.DeviceUtils
import com.beatohm.metadata.LyricsFetcher
import com.beatohm.metadata.MetadataFetcher
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MusicRepository(
    private val context: Context,
    private val libraryRepo: LibraryRepository = LibraryRepository(context)
) : IMusicRepository {

    private val dao = AppDatabase.getInstance(context).songDao()
    private val metadataFetcher = MetadataFetcher()
    private val lyricsFetcher = LyricsFetcher()

    override suspend fun getAllSongsNow(): List<LocalSong> = dao.getAllSongsNow()

    override fun getAllSongs(): Flow<List<LocalSong>> = dao.getAllSongs()
    override fun getAllSongsByTitle(): Flow<List<LocalSong>> = dao.getAllSongsByTitle()
    override fun getAllSongsByArtist(): Flow<List<LocalSong>> = dao.getAllSongsByArtist()
    override fun getAllSongsByAlbum(): Flow<List<LocalSong>> = dao.getAllSongsByAlbum()
    override fun getAllSongsByDuration(): Flow<List<LocalSong>> = dao.getAllSongsByDuration()
    override fun getAllAlbums(): Flow<List<String>> = dao.getAllAlbums()
    override fun getAllArtists(): Flow<List<String>> = dao.getAllArtists()
    override fun getAllGenres(): Flow<List<String>> = dao.getAllGenres()
    override fun getAllYears(): Flow<List<String>> = dao.getAllYears()
    override fun getAllAlbumsWithCover(): Flow<List<AlbumWithCover>> = dao.getAlbumsWithCover()
    override fun getAllArtistsWithCover(): Flow<List<ArtistWithCover>> = dao.getArtistsWithCover()
    override fun getSongsByAlbum(album: String): Flow<List<LocalSong>> = dao.getSongsByAlbum(album)
    override fun getSongsByArtist(artist: String): Flow<List<LocalSong>> = dao.getSongsByArtist(artist)
    override fun getSongsByGenre(genre: String): Flow<List<LocalSong>> = dao.getSongsByGenre(genre)
    override fun getSongsByYear(year: String): Flow<List<LocalSong>> = dao.getSongsByYear(year)
    override fun getMostPlayedSongs(): Flow<List<LocalSong>> = dao.getMostPlayedSongs()
    override fun getSongCount(): Flow<Int> = dao.getSongCount()
    override fun getFavoriteSongs(): Flow<List<LocalSong>> = dao.getFavoriteSongs()
    override suspend fun setFavorite(songId: String, isFavorite: Boolean) = dao.setFavorite(songId, isFavorite)
    override suspend fun getSongById(id: String): LocalSong? = dao.getSongById(id)

    override fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || song.genre.isBlank()
            || song.thumbnailUrl.isBlank() || song.lyrics.isBlank() || song.year.isBlank()
    }

    override suspend fun insertSong(song: LocalSong) = dao.insertSong(song)
    override suspend fun deleteSong(song: LocalSong) = dao.deleteSong(song)
    override suspend fun incrementPlayCount(songId: String) = dao.incrementPlayCount(songId)

    /**
     * Gradually enriches songs missing metadata (artist, album, duration) in the background.
     * Collects all updated songs and does a single batch insert for performance.
     * Call this AFTER fastScan() so songs are already visible in the UI.
     */
    override suspend fun enrichMetadataGradually(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int, title: String) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            val artCacheDir = libraryRepo.getAlbumArtCacheDir()
            if (!artCacheDir.exists()) artCacheDir.mkdirs()

            val songsNeedingMetadata = songs.filter {
                it.artist.isBlank() || it.album.isBlank() || it.duration == 0L
            }
            val total = songsNeedingMetadata.size
            val done = AtomicInteger(0)
            val threads = DeviceUtils.getOptimalThreadCount(context)
            val semaphore = Semaphore(threads)

            Log.d(TAG, "Enriching $total songs with $threads threads (batched by 10)")

            val batches = songsNeedingMetadata.chunked(10)

            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        val batchResults = mutableListOf<LocalSong>()
                        for (song in batch) {
                            try {
                                val file = File(song.filePath)
                                if (file.exists()) {
                                    val updated = extractSong(file, artCacheDir, song)
                                    if (updated.thumbnailUrl.isBlank()) {
                                        val artUrl = fetchArtFromITunes(updated, artCacheDir)
                                        batchResults.add(if (artUrl.isNotBlank()) updated.copy(thumbnailUrl = artUrl) else updated)
                                    } else {
                                        batchResults.add(updated)
                                    }
                                }
                            } catch (_: Exception) {}
                            val current = done.incrementAndGet()
                            onProgress?.invoke(current, total, song.title)
                        }
                        if (batchResults.isNotEmpty()) {
                            dao.insertSongs(batchResults)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun extractSong(file: File, artCacheDir: File, existing: LocalSong?): LocalSong {
        val path = file.absolutePath
        return try {
            val meta = MediaMetadataRetriever()
            meta.setDataSource(path)
            val raw = readMetadataFromRetriever(meta, file)
            val artPath = extractEmbeddedArt(meta, artCacheDir, file)
            meta.release()
            buildSongFromRaw(path, raw, artPath, existing)
        } catch (_: Exception) {
            LocalSong(id = path, title = file.nameWithoutExtension, filePath = path,
                lyrics = existing?.lyrics ?: "", isFavorite = existing?.isFavorite ?: false,
                playCount = existing?.playCount ?: 0, waveformData = existing?.waveformData ?: "")
        }
    }

    private data class RawMetadata(val title: String, val artist: String, val album: String,
        val genre: String, val year: String, val track: Int, val duration: Long)

    private fun readMetadataFromRetriever(meta: MediaMetadataRetriever, file: File): RawMetadata {
        return RawMetadata(
            title = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension),
            artist = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""),
            album = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""),
            genre = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""),
            year = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "",
            track = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0,
            duration = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        )
    }

    private fun extractEmbeddedArt(meta: MediaMetadataRetriever, artCacheDir: File, file: File): String {
        if (file.extension.lowercase() == "opus") return ""
        return try {
            val art = meta.embeddedPicture ?: return ""
            val artFile = File(artCacheDir, "${file.nameWithoutExtension}.jpg")
            if (!artFile.exists()) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return ""
                java.io.FileOutputStream(artFile).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out) }
            }
            if (artFile.exists()) artFile.absolutePath else ""
        } catch (_: Exception) { "" }
    }

    private fun buildSongFromRaw(path: String, raw: RawMetadata, artPath: String, existing: LocalSong?): LocalSong {
        return LocalSong(
            id = path, title = raw.title,
            artist = raw.artist.ifBlank { existing?.artist.orEmpty() },
            album = raw.album.ifBlank { existing?.album.orEmpty() },
            genre = raw.genre.ifBlank { existing?.genre.orEmpty() },
            year = raw.year.ifBlank { existing?.year.orEmpty() },
            trackNumber = if (raw.track > 0) raw.track else (existing?.trackNumber ?: 0),
            duration = raw.duration, filePath = path,
            thumbnailUrl = artPath.ifBlank { existing?.thumbnailUrl.orEmpty() },
            lyrics = existing?.lyrics ?: "", isFavorite = existing?.isFavorite ?: false,
            playCount = existing?.playCount ?: 0, waveformData = existing?.waveformData ?: ""
        )
    }

    private suspend fun fetchArtFromITunes(song: LocalSong, artCacheDir: File): String {
        try {
            val query = "${song.artist} ${song.album}".ifBlank { song.title }
            val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&limit=1"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return@use
                if (results.length() > 0) {
                    val artworkUrl = results.getJSONObject(0).optString("artworkUrl100", "")
                    if (artworkUrl.isNotBlank()) {
                        val hdUrl = artworkUrl.replace("100x100bb", "600x600bb")
                        val artFile = File(artCacheDir, "${song.title.hashCode()}.jpg")
                        libraryRepo.downloadArtwork(hdUrl, artFile)
                        if (artFile.exists()) return artFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "iTunes art fetch failed for ${song.title}: ${e.message}")
        }
        return ""
    }

    // === Funciones con responsabilidad única ===

    override suspend fun fetchMetadata(song: LocalSong): LocalSong {
        val existing = dao.getSongById(song.id) ?: return song
        val enriched = metadataFetcher.fetchFullMetadata(song.toSong()).getOrNull() ?: return song
        Log.d(TAG, "fetchMetadata: '${enriched.artist}' - '${enriched.title}'")
        return existing.copy(
            title = bestValue(existing.title, enriched.title),
            artist = bestValue(existing.artist, enriched.artist),
            album = bestValue(existing.album, enriched.album),
            genre = bestValue(existing.genre, enriched.genre),
            year = bestValue(existing.year, enriched.year),
            trackNumber = if (enriched.trackNumber > 0) enriched.trackNumber else existing.trackNumber
        )
    }

    override suspend fun downloadArtworkForSong(song: LocalSong): LocalSong {
        val thumbnailUrl = song.thumbnailUrl
        if (thumbnailUrl.isBlank() || !thumbnailUrl.startsWith("http")) return song
        val dest = File(libraryRepo.getAlbumArtCacheDir(), "${song.id.hashCode()}.jpg")
        libraryRepo.downloadArtwork(thumbnailUrl, dest)
        return if (dest.exists()) song.copy(thumbnailUrl = dest.absolutePath) else song
    }

    override suspend fun extractDominantColor(song: LocalSong): LocalSong {
        val thumbnailUrl = song.thumbnailUrl
        if (thumbnailUrl.isBlank()) {
            Log.w(TAG, "extractDominantColor: skipped '${song.title}' - thumbnailUrl is blank")
            return song
        }
        val file = File(thumbnailUrl)
        if (!file.exists()) {
            Log.w(TAG, "extractDominantColor: skipped '${song.title}' - file not found: $thumbnailUrl")
            return song
        }
        try {
            val bitmap = BitmapFactory.decodeFile(thumbnailUrl)
            if (bitmap != null) {
                val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                val dominant = palette.getDominantColor(0)
                Log.d(TAG, "extractDominantColor: #${Integer.toHexString(dominant)} for '${song.title}'")
                bitmap.recycle()
                if (dominant != 0) return song.copy(dominantColor = dominant)
                Log.w(TAG, "extractDominantColor: palette returned 0 for '${song.title}'")
            } else {
                Log.w(TAG, "extractDominantColor: BitmapFactory returned null for '${song.title}' ($thumbnailUrl)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractDominantColor: FAILED for '${song.title}': ${e.message}")
        }
        return song
    }

    override suspend fun fetchLyricsForSong(song: LocalSong): LocalSong {
        if (song.lyrics.isNotBlank()) return song
        try {
            val result = lyricsFetcher.fetchLyrics(song.artist, song.title)
            if (result.isSuccess) {
                val lyrics = (result.getOrNull()?.syncedLrc ?: result.getOrNull()?.plainText).orEmpty()
                if (lyrics.isNotBlank()) {
                    Log.d(TAG, "fetchLyricsForSong: found lyrics for '${song.title}'")
                    return song.copy(lyrics = lyrics)
                }
            }
        } catch (_: Exception) {}
        return song
    }

    override fun renameSongFile(song: LocalSong): LocalSong {
        val oldFile = File(song.filePath)
        if (!oldFile.exists()) return song
        val newFileName = "${fixMojibake(song.artist)} - ${fixMojibake(song.title)}".replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val newFile = File(oldFile.parent, "${newFileName}.${oldFile.extension}")
        if (newFile.absolutePath == oldFile.absolutePath || newFile.exists()) return song
        return if (oldFile.renameTo(newFile)) {
            val updated = song.copy(id = newFile.absolutePath, filePath = newFile.absolutePath)
            Log.d(TAG, "renameSongFile: '${oldFile.name}' → '${newFile.name}'")
            updated
        } else song
    }

    override suspend fun saveSong(song: LocalSong) {
        dao.insertSong(song)
    }

    // === Composición: enriquecer canción completa ===

    override suspend fun enrichSong(
        song: LocalSong,
        skipTagWrite: Boolean,
        fetchLyrics: Boolean
    ): LocalSong {
        var updated = song
        updated = fetchMetadata(updated)
        updated = downloadArtworkForSong(updated)
        updated = extractDominantColor(updated)
        if (fetchLyrics) updated = fetchLyricsForSong(updated)
        dao.insertSong(updated)
        if (!skipTagWrite) {
            updated = renameSongFile(updated)
            if (updated.filePath != song.filePath) dao.insertSong(updated)
            AudioTagWriter.writeTags(File(updated.filePath), updated)
        }
        return updated
    }

    private fun bestValue(existing: String, newValue: String): String =
        if (isUsableMetadata(newValue)) newValue else existing

    private fun isUsableMetadata(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return trimmed.lowercase() !in setOf("unknown", "various artists", "desconocido")
    }

    companion object {
        private const val TAG = "MusicRepository"

        /**
         * Detecta y repara doble encoding UTF-8 (mojibake).
         * Ejemplo: "Â¿QuÃ©" → "¿Qué"
         */
        fun fixMojibake(text: String): String {
            if (!text.contains("Â") && !text.contains("Ã")) return text
            try {
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded != text && !decoded.contains("�")) return decoded
            } catch (_: Exception) {}
            return text
        }
    }
}
