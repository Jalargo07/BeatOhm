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
    fun getSongsByAlbum(album: String): Flow<List<LocalSong>> = dao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String): Flow<List<LocalSong>> = dao.getSongsByArtist(artist)
    fun getSongsByGenre(genre: String): Flow<List<LocalSong>> = dao.getSongsByGenre(genre)
    fun getSongsByYear(year: String): Flow<List<LocalSong>> = dao.getSongsByYear(year)
    fun getMostPlayedSongs(): Flow<List<LocalSong>> = dao.getMostPlayedSongs()
    fun getSongCount(): Flow<Int> = dao.getSongCount()
    fun getFavoriteSongs(): Flow<List<LocalSong>> = dao.getFavoriteSongs()
    suspend fun setFavorite(songId: String, isFavorite: Boolean) = dao.setFavorite(songId, isFavorite)
    suspend fun getSongById(id: String): LocalSong? = dao.getSongById(id)

    suspend fun insertSong(song: LocalSong) = dao.insertSong(song)
    suspend fun deleteSong(song: LocalSong) = dao.deleteSong(song)
    suspend fun incrementPlayCount(songId: String) = dao.incrementPlayCount(songId)

    suspend fun updateWaveform(songId: String, json: String) {
        dao.updateWaveform(songId, json)
    }

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

        cleanupDuplicates()
        val existing = dao.getAllSongsNow().associate { it.id to it }
        val artCacheDir = getAlbumArtCacheDir()
        if (!artCacheDir.exists()) artCacheDir.mkdirs()

        val processed = mutableSetOf<String>()
        val songs = mutableListOf<LocalSong>()

        for (folderPath in getLibraryFolders()) {
            val dir = File(folderPath)
            if (!dir.isDirectory) continue
            try {
                for (file in audioFilesIn(dir, MAX_SCAN_DEPTH)) {
                    val path = file.absolutePath
                    if (processed.add(path)) {
                        songs.add(extractSong(file, artCacheDir, existing[path]))
                    }
                }
            } catch (_: Exception) {}
        }

        if (songs.isNotEmpty()) {
            dao.insertSongs(songs)
        }

        val songsNeedingArt = songs.filter { it.thumbnailUrl.isBlank() }.take(20)
        for (song in songsNeedingArt) {
            val artUrl = fetchArtFromITunes(song, artCacheDir)
            if (artUrl.isNotBlank()) {
                dao.insertSong(song.copy(thumbnailUrl = artUrl))
                val songIndex = songs.indexOfFirst { it.id == song.id }
                if (songIndex >= 0) {
                    songs[songIndex] = songs[songIndex].copy(thumbnailUrl = artUrl)
                }
            }
        }

        val incompleteSongs = songs.filter {
            it.artist.isBlank() || it.album.isBlank() || it.genre.isBlank()
                || it.thumbnailUrl.isBlank() || it.lyrics.isBlank() || it.year.isBlank()
        }

        return ScanResult(songs, incompleteSongs)
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

        val semaphore = Semaphore(3)
        val total = songsNeedingWaveform.size
        var done = 0

        songsNeedingWaveform.map { song ->
            async {
                semaphore.withPermit {
                    try {
                        val numBars = WaveformExtractor.barsForDuration(song.duration)
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

    private fun extractSong(
        file: File,
        artCacheDir: File,
        existing: LocalSong?
    ): LocalSong {
        val path = file.absolutePath
        return try {
            val meta = MediaMetadataRetriever()
            meta.setDataSource(path)
            val title = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension)
            val artist = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "")
            val album = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "")
            val genre = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "")
            val year = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val track = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0
            val duration = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            var thumbnailUrl = ""
            try {
                val art = meta.embeddedPicture
                if (art != null) {
                    val artFile = File(artCacheDir, "${file.nameWithoutExtension}.jpg")
                    if (!artFile.exists()) {
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        if (bitmap != null) {
                            FileOutputStream(artFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                        }
                    }
                    if (artFile.exists()) {
                        thumbnailUrl = artFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Art extraction failed for ${file.name}: ${e.message}")
            }
            meta.release()

            LocalSong(
                id = path,
                title = title,
                artist = artist.ifBlank { existing?.artist.orEmpty() },
                album = album.ifBlank { existing?.album.orEmpty() },
                genre = genre.ifBlank { existing?.genre.orEmpty() },
                year = year.ifBlank { existing?.year.orEmpty() },
                trackNumber = if (track > 0) track else (existing?.trackNumber ?: 0),
                duration = duration,
                filePath = path,
                thumbnailUrl = thumbnailUrl.ifBlank { existing?.thumbnailUrl.orEmpty() },
                lyrics = existing?.lyrics ?: "",
                isFavorite = existing?.isFavorite ?: false,
                playCount = existing?.playCount ?: 0,
                waveformData = existing?.waveformData ?: ""
            )
        } catch (_: Exception) {
            LocalSong(
                id = path,
                title = file.nameWithoutExtension,
                filePath = path,
                lyrics = existing?.lyrics ?: "",
                isFavorite = existing?.isFavorite ?: false,
                playCount = existing?.playCount ?: 0,
                waveformData = existing?.waveformData ?: ""
            )
        }
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
        val normalized = folderPath.trimEnd('/')
        val keepFolders = getLibraryFolders().filter { it != normalized }
        val prefix = "$normalized${File.separator}"
        val songs = dao.getAllSongsNow()
        for (song in songs) {
            if (song.filePath.startsWith(prefix)) {
                val inOtherFolder = keepFolders.any { song.filePath.startsWith("$it${File.separator}") }
                if (!inOtherFolder) dao.deleteSong(song)
            }
        }
    }

    suspend fun enrichSong(
        song: LocalSong,
        skipTagWrite: Boolean = false,
        fetchLyrics: Boolean = false
    ): LocalSong {
        Log.e("MusicRepository", "enrichSong INICIO: '${song.artist}' - '${song.title}' fetchLyrics=$fetchLyrics")
        val existing = dao.getSongById(song.id)?.let {
            it.copy(
                title = fixMojibake(it.title),
                artist = fixMojibake(it.artist),
                album = fixMojibake(it.album)
            )
        } ?: song
        val enriched = metadataFetcher.fetchFullMetadata(song.toSong()).getOrNull() ?: song.toSong()
        Log.e("MusicRepository", "enrichSong metadata: '${enriched.artist}' - '${enriched.title}' [${enriched.album}]")

        var thumbnailUrl = enriched.thumbnailUrl
        if (thumbnailUrl.startsWith("http")) {
            val dest = File(getAlbumArtCacheDir(), "${song.id.hashCode()}.jpg")
            downloadArtwork(thumbnailUrl, dest)
            if (dest.exists()) thumbnailUrl = dest.absolutePath
        }

        var updated = LocalSong(
            id = song.id,
            title = bestValue(existing.title, enriched.title),
            artist = bestValue(existing.artist, enriched.artist),
            album = bestValue(existing.album, enriched.album),
            genre = bestValue(existing.genre, enriched.genre),
            year = bestValue(existing.year, enriched.year),
            trackNumber = if (enriched.trackNumber > 0) enriched.trackNumber else song.trackNumber,
            duration = song.duration,
            filePath = song.filePath,
            thumbnailUrl = thumbnailUrl.ifBlank { existing.thumbnailUrl },
            lyrics = existing.lyrics,
            isFavorite = existing.isFavorite,
            playCount = existing.playCount
        )

        if (fetchLyrics && updated.lyrics.isBlank()) {
            try {
                val lyricsResult = lyricsFetcher.fetchLyrics(updated.artist, updated.title)
                if (lyricsResult.isSuccess) {
                    val result = lyricsResult.getOrNull()
                    val lyrics = (result?.syncedLrc ?: result?.plainText).orEmpty()
                    if (lyrics.isNotBlank()) {
                        updated = updated.copy(lyrics = lyrics)
                    }
                }
            } catch (_: Exception) {}
        }

        dao.insertSong(updated)

        // Renombrar archivo si cambió artista o título
        if (!skipTagWrite) {
            val oldFile = File(song.filePath)
            if (oldFile.exists()) {
                val newFileName = "${fixMojibake(updated.artist)} - ${fixMojibake(updated.title)}".replace(Regex("[/\\\\:*?\"<>|]"), "_")
                val newFile = File(oldFile.parent, "${newFileName}.${oldFile.extension}")
                if (newFile.absolutePath != oldFile.absolutePath && !newFile.exists()) {
                    if (oldFile.renameTo(newFile)) {
                        val oldId = updated.id
                        updated = updated.copy(id = newFile.absolutePath, filePath = newFile.absolutePath)
                        dao.insertSong(updated)
                        if (oldId != updated.id) {
                            dao.deleteSongById(oldId)
                        }
                        Log.e("MusicRepository", "Archivo renombrado: ${oldFile.name} → ${newFile.name}")
                    }
                }
            }
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

    private fun downloadArtwork(url: String, dest: File) {
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

    private fun foldersPrefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMusicDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val target = File(musicDir, "MusicDownloader")
        if (!target.exists()) target.mkdirs()
        return target
    }

    companion object {
        private const val PREFS_NAME = "library_prefs"
        private const val KEY_FOLDERS = "library_folders"
        private const val MAX_SCAN_DEPTH = 4
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav")

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
