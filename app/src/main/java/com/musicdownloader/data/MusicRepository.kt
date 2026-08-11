package com.musicdownloader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.musicdownloader.audio.WaveformExtractor
import com.musicdownloader.metadata.LyricsFetcher
import com.musicdownloader.metadata.MetadataFetcher
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MusicRepository(private val context: Context) {

    data class ScanResult(val songs: List<LocalSong>, val incompleteSongs: List<LocalSong>)

    private val dao = AppDatabase.getInstance(context).songDao()
    private val metadataFetcher = MetadataFetcher()
    private val lyricsFetcher = LyricsFetcher()

    private val _regenProgress = _regenProgressStatic
    val regenProgress: LiveData<Pair<Int, Int>?> = _regenProgressStatic

    suspend fun getAllSongsNow(): List<LocalSong> = dao.getAllSongsNow()

    fun getAllSongs(): Flow<List<LocalSong>> = dao.getAllSongs()
    fun getAllSongsByTitle(): Flow<List<LocalSong>> = dao.getAllSongsByTitle()
    fun getAllSongsByArtist(): Flow<List<LocalSong>> = dao.getAllSongsByArtist()
    fun getAllSongsByAlbum(): Flow<List<LocalSong>> = dao.getAllSongsByAlbum()
    fun getAllSongsByDuration(): Flow<List<LocalSong>> = dao.getAllSongsByDuration()
    fun getAllAlbums(): Flow<List<String>> = dao.getAllAlbums()
    fun getAllArtists(): Flow<List<String>> = dao.getAllArtists()
    fun getAllGenres(): Flow<List<String>> = dao.getAllGenres()
    fun getAllYears(): Flow<List<String>> = dao.getAllYears()
    fun getAllAlbumsWithCover(): Flow<List<AlbumWithCover>> = dao.getAlbumsWithCover()
    fun getAllArtistsWithCover(): Flow<List<ArtistWithCover>> = dao.getArtistsWithCover()
    fun getSongsByAlbum(album: String): Flow<List<LocalSong>> = dao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String): Flow<List<LocalSong>> = dao.getSongsByArtist(artist)
    fun getSongsByGenre(genre: String): Flow<List<LocalSong>> = dao.getSongsByGenre(genre)
    fun getSongsByYear(year: String): Flow<List<LocalSong>> = dao.getSongsByYear(year)
    fun getMostPlayedSongs(): Flow<List<LocalSong>> = dao.getMostPlayedSongs()
    fun getSongCount(): Flow<Int> = dao.getSongCount()
    fun getFavoriteSongs(): Flow<List<LocalSong>> = dao.getFavoriteSongs()
    suspend fun setFavorite(songId: String, isFavorite: Boolean) = dao.setFavorite(songId, isFavorite)
    suspend fun getSongById(id: String): LocalSong? = dao.getSongById(id)

    fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || song.genre.isBlank()
            || song.thumbnailUrl.isBlank() || song.lyrics.isBlank() || song.year.isBlank()
    }

    suspend fun insertSong(song: LocalSong) = dao.insertSong(song)
    suspend fun deleteSong(song: LocalSong) = dao.deleteSong(song)
    suspend fun incrementPlayCount(songId: String) = dao.incrementPlayCount(songId)

    suspend fun updateWaveform(songId: String, json: String) {
        dao.updateWaveform(songId, json)
    }

    suspend fun resetWaveform(song: LocalSong) {
        val realDurationMs = getRealDurationMs(song)
        Log.d("MusicRepository", "resetWaveform: '${song.title}' dbDuration=${song.duration} realDurationMs=$realDurationMs")
        dao.clearWaveform(song.id)
        val numBars = WaveformExtractor.barsForDuration(realDurationMs)
        val data = WaveformExtractor.extract(song.filePath, numBars)
        val json = Gson().toJson(data.toList())
        dao.updateWaveform(song.id, json)
        Log.d("MusicRepository", "resetWaveform: saved ${data.size} bars to DB")
    }

    private fun getRealDurationMs(song: LocalSong): Long {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(song.filePath)
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    if (fmt.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        durationUs = fmt.getLong(android.media.MediaFormat.KEY_DURATION)
                    }
                    break
                }
            }
            if (durationUs > 0L) durationUs / 1000L else song.duration
        } catch (_: Exception) { song.duration } finally { extractor.release() }
    }

    fun startRegenProgress(total: Int) {
        _regenProgress.postValue(0 to total)
    }

    fun updateRegenProgress(done: Int, total: Int) {
        _regenProgress.postValue(done to total)
    }

    fun finishRegenProgress() {
        _regenProgress.postValue(null)
    }

    // Regen status tracking
    suspend fun markPending(songIds: List<String>) = dao.markPending(songIds)
    suspend fun markSuccess(songId: String) = dao.markSuccess(songId)
    suspend fun markFailed(songId: String) = dao.markFailed(songId)
    fun getFailedSongs(): Flow<List<LocalSong>> = dao.getFailedSongs()
    fun getPendingAndFailedSongs(): Flow<List<LocalSong>> = dao.getPendingAndFailedSongs()
    suspend fun getFailedSongsNow(): List<LocalSong> = dao.getFailedSongsNow()
    suspend fun clearRegenStatus() = dao.clearRegenStatus()
    suspend fun getFailedCount(): Int = dao.getFailedCount()

    fun getLibraryFolders(): List<String> {
        val defaultDir = getMusicDir().absolutePath
        val set = foldersPrefs().getStringSet(KEY_FOLDERS, null)
        val folders = if (set.isNullOrEmpty()) {
            listOf(defaultDir)
        } else {
            val merged = HashSet(set)
            merged.add(defaultDir)
            merged.toList()
        }
        return folders.map { it.trimEnd('/') }
    }

    fun getSongsInFolder(folderPath: String): Flow<List<LocalSong>> = dao.getSongsInFolder(folderPath)

    fun addLibraryFolder(path: String) {
        val normalized = path.trimEnd('/')
        val current = foldersPrefs().getStringSet(KEY_FOLDERS, null) ?: mutableSetOf()
        val updated = HashSet(current)
        updated.add(normalized)
        foldersPrefs().edit().putStringSet(KEY_FOLDERS, updated).apply()
    }

    fun removeLibraryFolder(path: String) {
        val normalized = path.trimEnd('/')
        val current = foldersPrefs().getStringSet(KEY_FOLDERS, null) ?: return
        val updated = HashSet(current)
        updated.remove(normalized)
        foldersPrefs().edit().putStringSet(KEY_FOLDERS, updated).apply()
    }

    fun getAlbumArtCacheDir(): File = File(getMusicDir(), ".albumart")

    suspend fun scanMusicFolder(): List<LocalSong> = scanLibrary().songs

    suspend fun scanLibrary(): ScanResult {
        // One-time: clear old waveform data so new density formula is applied
        val prefs = context.getSharedPreferences("waveform_migration", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("v2_cleared", false)) {
            dao.clearAllWaveforms()
            prefs.edit().putBoolean("v2_cleared", true).apply()
        }
        return fastScan()
    }

    /**
     * Fast scan: finds new audio files and inserts them with minimal metadata
     * (no MediaMetadataRetriever calls). Returns all songs for immediate UI display.
     * Metadata enrichment happens later in the background via enrichMetadataGradually().
     */
    suspend fun fastScan(): ScanResult = withContext(Dispatchers.IO) {
        cleanupDuplicates()
        val existing = dao.getAllSongsNow().associate { it.id to it }
        val newSongs = discoverNewFiles(existing)
        if (newSongs.isNotEmpty()) dao.insertSongs(newSongs)
        val allSongs = dao.getAllSongsNow()
        ScanResult(allSongs, allSongs.filter { isIncomplete(it) })
    }

    private fun discoverNewFiles(existing: Map<String, LocalSong>): List<LocalSong> {
        val newSongs = mutableListOf<LocalSong>()
        val processed = mutableSetOf<String>()
        for (folderPath in getLibraryFolders()) {
            val dir = File(folderPath)
            if (!dir.isDirectory) continue
            try {
                for (file in audioFilesIn(dir, MAX_SCAN_DEPTH)) {
                    val path = file.absolutePath
                    if (processed.add(path) && existing[path] == null) {
                        newSongs.add(LocalSong(id = path, title = file.nameWithoutExtension, duration = 0L, filePath = path))
                    }
                }
            } catch (_: Exception) {}
        }
        return newSongs
    }

    /**
     * Gradually enriches songs missing metadata (artist, album, duration) in the background.
     * Processes one song at a time with a small delay between each to avoid CPU overload.
     * Call this AFTER fastScan() so songs are already visible in the UI.
     */
    suspend fun enrichMetadataGradually(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int, title: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val artCacheDir = getAlbumArtCacheDir()
        if (!artCacheDir.exists()) artCacheDir.mkdirs()

        val songsNeedingMetadata = songs.filter {
            it.artist.isBlank() || it.album.isBlank() || it.duration == 0L
        }
        val total = songsNeedingMetadata.size
        var done = 0

        for (song in songsNeedingMetadata) {
            try {
                val file = File(song.filePath)
                if (file.exists()) {
                    val updated = extractSong(file, artCacheDir, song)
                    if (updated.thumbnailUrl.isBlank()) {
                        val artUrl = fetchArtFromITunes(updated, artCacheDir)
                        dao.insertSong(
                            if (artUrl.isNotBlank()) updated.copy(thumbnailUrl = artUrl) else updated
                        )
                    } else {
                        dao.insertSong(updated)
                    }
                }
            } catch (_: Exception) {}
            done++
            onProgress?.invoke(done, total, song.title)
            delay(100)
        }
    }

    /**
     * Extracts waveforms for songs missing them. Runs in background with concurrency limit.
     * Call this AFTER scanLibrary() completes, from a coroutine scope.
     */
    suspend fun extractMissingWaveforms(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val songsNeedingWaveform = songs.filter { it.waveformData.isBlank() }
        if (songsNeedingWaveform.isEmpty()) return@withContext

        val semaphore = Semaphore(2)
        val total = songsNeedingWaveform.size
        var done = 0

        songsNeedingWaveform.map { song ->
            async {
                semaphore.withPermit {
                    try {
                        val realDurationMs = getRealDurationMs(song)
                        val numBars = WaveformExtractor.barsForDuration(realDurationMs)
                        val data = WaveformExtractor.extract(song.filePath, numBars)
                        val json = Gson().toJson(data.toList())
                        dao.updateWaveform(song.id, json)
                    } catch (_: Exception) {} finally {
                        done++
                        onProgress?.invoke(done, total)
                    }
                }
            }
        }.awaitAll()
    }

    private fun audioFilesIn(root: File, maxDepth: Int): List<File> {
        val visited = HashSet<String>()
        val result = mutableListOf<File>()
        fun visit(dir: File, depth: Int) {
            if (depth > maxDepth) return
            val canonical = try { dir.canonicalPath } catch (_: Exception) { return }
            if (!visited.add(canonical)) return
            val children = try { dir.listFiles() ?: return } catch (_: Exception) { return }
            for (child in children) {
                if (child.name.startsWith(".") || child.name == "Android") continue
                if (child.isDirectory) {
                    visit(child, depth + 1)
                } else if (child.extension.lowercase() in AUDIO_EXTENSIONS) {
                    result.add(child)
                }
            }
        }
        visit(root, 0)
        return result
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
                FileOutputStream(artFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
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
                        downloadArtwork(hdUrl, artFile)
                        if (artFile.exists()) return artFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "iTunes art fetch failed for ${song.title}: ${e.message}")
        }
        return ""
    }

    suspend fun deleteSongsInFolder(folderPath: String) {
        val songs = getSongsExclusiveToFolder(folderPath)
        for (song in songs) dao.deleteSong(song)
    }

    private suspend fun getSongsExclusiveToFolder(folderPath: String): List<LocalSong> {
        val normalized = folderPath.trimEnd('/')
        val keepFolders = getLibraryFolders().filter { it != normalized }
        val prefix = "$normalized${File.separator}"
        return dao.getAllSongsNow().filter { song ->
            song.filePath.startsWith(prefix) &&
                keepFolders.none { song.filePath.startsWith("$it${File.separator}") }
        }
    }

    // === Funciones con responsabilidad única ===

    suspend fun fetchMetadata(song: LocalSong): LocalSong {
        val existing = dao.getSongById(song.id) ?: return song
        val enriched = metadataFetcher.fetchFullMetadata(song.toSong()).getOrNull() ?: return song
        Log.d("MusicRepository", "fetchMetadata: '${enriched.artist}' - '${enriched.title}'")
        return existing.copy(
            title = bestValue(existing.title, enriched.title),
            artist = bestValue(existing.artist, enriched.artist),
            album = bestValue(existing.album, enriched.album),
            genre = bestValue(existing.genre, enriched.genre),
            year = bestValue(existing.year, enriched.year),
            trackNumber = if (enriched.trackNumber > 0) enriched.trackNumber else existing.trackNumber
        )
    }

    suspend fun downloadArtworkForSong(song: LocalSong): LocalSong {
        val thumbnailUrl = song.thumbnailUrl
        if (thumbnailUrl.isBlank() || !thumbnailUrl.startsWith("http")) return song
        val dest = File(getAlbumArtCacheDir(), "${song.id.hashCode()}.jpg")
        downloadArtwork(thumbnailUrl, dest)
        return if (dest.exists()) song.copy(thumbnailUrl = dest.absolutePath) else song
    }

    suspend fun extractDominantColor(song: LocalSong): LocalSong {
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
            val bitmap = android.graphics.BitmapFactory.decodeFile(thumbnailUrl)
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

    suspend fun fetchLyricsForSong(song: LocalSong): LocalSong {
        if (song.lyrics.isNotBlank()) return song
        try {
            val result = lyricsFetcher.fetchLyrics(song.artist, song.title)
            if (result.isSuccess) {
                val lyrics = (result.getOrNull()?.syncedLrc ?: result.getOrNull()?.plainText).orEmpty()
                if (lyrics.isNotBlank()) {
                    Log.d("MusicRepository", "fetchLyricsForSong: found lyrics for '${song.title}'")
                    return song.copy(lyrics = lyrics)
                }
            }
        } catch (_: Exception) {}
        return song
    }

    fun renameSongFile(song: LocalSong): LocalSong {
        val oldFile = File(song.filePath)
        if (!oldFile.exists()) return song
        val newFileName = "${fixMojibake(song.artist)} - ${fixMojibake(song.title)}".replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val newFile = File(oldFile.parent, "${newFileName}.${oldFile.extension}")
        if (newFile.absolutePath == oldFile.absolutePath || newFile.exists()) return song
        return if (oldFile.renameTo(newFile)) {
            val oldId = song.id
            val updated = song.copy(id = newFile.absolutePath, filePath = newFile.absolutePath)
            Log.d("MusicRepository", "renameSongFile: '${oldFile.name}' → '${newFile.name}'")
            updated
        } else song
    }

    suspend fun saveSong(song: LocalSong) {
        dao.insertSong(song)
    }

    // === Composición: enriquecer canción completa ===

    suspend fun enrichSong(
        song: LocalSong,
        skipTagWrite: Boolean = false,
        fetchLyrics: Boolean = false
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

    fun downloadArtwork(url: String, dest: File) {
        try {
            if (dest.exists()) return
            val request = Request.Builder().url(url).get().build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { it.write(bytes) }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun cleanupDuplicates() {
        try {
            val allSongs = dao.getAllSongsNow()
            val orphaned = allSongs.filter { !File(it.id).exists() }
            orphaned.forEach { dao.deleteSongById(it.id) }
            if (orphaned.isNotEmpty()) {
                Log.e("MusicRepository", "Eliminados ${orphaned.size} registros huérfanos")
            }
            val groups = allSongs.filter { File(it.id).exists() }
                .groupBy { "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}" }
            var dedupCount = 0
            for ((_, songs) in groups) {
                if (songs.size > 1) {
                    for (dupe in songs.drop(1)) {
                        dao.deleteSongById(dupe.id)
                        dedupCount++
                    }
                }
            }
            if (dedupCount > 0) {
                Log.e("MusicRepository", "Eliminados $dedupCount duplicados")
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "cleanupDuplicates error: ${e.message}")
        }
    }

    fun getAlbumCoverOverride(album: String): String? = albumCoverPrefs().getString(album, null)

    fun setAlbumCoverOverride(album: String, coverPath: String) {
        albumCoverPrefs().edit().putString(album, coverPath).apply()
    }

    private fun albumCoverPrefs() =
        context.getSharedPreferences(ALBUM_COVERS_PREFS, Context.MODE_PRIVATE)

    private fun foldersPrefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMusicDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val target = File(musicDir, "MusicDownloader")
        if (!target.exists()) target.mkdirs()
        return target
    }

    companion object {
        private const val TAG = "MusicRepository"
        private const val PREFS_NAME = "library_prefs"
        private const val KEY_FOLDERS = "library_folders"
        private const val ALBUM_COVERS_PREFS = "album_covers"
        private const val MAX_SCAN_DEPTH = 4
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "webm")

        // Static regen progress - survives across MusicRepository instances
        private val _regenProgressStatic = MutableLiveData<Pair<Int, Int>?>()
        val regenProgressStatic: LiveData<Pair<Int, Int>?> = _regenProgressStatic

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
