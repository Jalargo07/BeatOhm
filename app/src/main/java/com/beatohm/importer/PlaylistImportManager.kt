package com.beatohm.importer

import android.content.Context
import android.util.Log
import com.beatohm.DeviceUtils
import com.beatohm.data.AppDatabase
import com.beatohm.data.LocalSong
import com.beatohm.data.TagWriteCoordinator
import com.beatohm.downloader.ProxyDownloader
import com.beatohm.metadata.LyricsFetcher
import com.beatohm.metadata.MetadataFetcher
import com.beatohm.model.Song
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.beatohm.network.NetworkModule
import okhttp3.Request
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Orchestrates playlist import from Deezer/Spotify.
 *
 * Black hole fixes implemented:
 * 1. Persistent state — ImportSession + ImportTrackStatus in Room DB (resume on restart)
 * 2. YouTube pickBestMatch scoring — duration(60%) + title quality(30%) + lyrics(10%)
 * 3. Cooperative cancellation — ensureActive() + cancelAndJoin() + temp file cleanup
 * 4. Metadata/lyrics non-blocking — async parallel, minimal tags on failure
 * 5. Memory optimization — batch processing of 20 tracks at a time
 */
class PlaylistImportManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistImportManager"

        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val MIN_SEARCH_DELAY_MS = 300L
        private const val MAX_SEARCH_DELAY_MS = 800L
        private const val BATCH_SIZE = 20
        private const val MAX_RETRIES = 3
    }

    private val db = AppDatabase.getInstance(context)
    private val sessionDao = db.importSessionDao()
    private val trackStatusDao = db.importTrackStatusDao()

    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private var importJob: Job? = null
    private var importScope: CoroutineScope? = null
    @Volatile
    private var isCancelled = false

    private val proxyDownloader = ProxyDownloader()
    private val metadataFetcher = MetadataFetcher()
    private val lyricsFetcher = LyricsFetcher()
    private val tagWriteCoordinator = TagWriteCoordinator()
    private val httpClient = NetworkModule.newClient(
        connectTimeoutSec = 30,
        readTimeoutSec = 300,
        writeTimeoutSec = 300
    )

    var onProgress: ((completed: Int, total: Int, currentTrack: String) -> Unit)? = null
    var onComplete: ((imported: Int, failed: Int, skipped: Int) -> Unit)? = null
    var onTrackCompleted: ((title: String, artist: String, filePath: String) -> Unit)? = null

    /**
     * Start importing a playlist from URL.
     * Detects platform, fetches tracks, downloads each with rate limiting.
     */
    suspend fun startImport(url: String, parentJob: Job? = null): Long = withContext(Dispatchers.IO) {
        isCancelled = false

        val (importer, playlistId) = detectPlatform(url) ?: run {
            Log.e(TAG, "Could not detect platform from URL: $url")
            return@withContext -1L
        }

        Log.d(TAG, "Starting import: platform=${importer::class.simpleName}, id=$playlistId")

        val session = ImportSession(
            playlistUrl = url,
            platform = importer::class.simpleName?.lowercase() ?: "unknown"
        )
        val sessionId = sessionDao.insert(session)

        val tracks = importer.fetchTracks(playlistId)
        if (tracks.isEmpty()) {
            sessionDao.updateStatus(sessionId, "COMPLETED")
            return@withContext sessionId
        }

        val trackEntities = tracks.map { track ->
            ImportTrackStatus(
                sessionId = sessionId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSec = track.durationSec,
                status = "PENDING"
            )
        }
        trackStatusDao.insertAll(trackEntities)
        sessionDao.updateProgress(sessionId, 0, 0)

        importJob = parentJob ?: CoroutineScope(Dispatchers.IO + SupervisorJob()).also { importScope = it }.launch {
            try {
                processImport(sessionId, tracks.size)
            } catch (e: CancellationException) {
                Log.d(TAG, "Import cancelled")
                isCancelled = true
            } catch (e: Exception) {
                Log.e(TAG, "Import error: ${e.message}")
            }
        }

        importJob?.join()

        val completed = trackStatusDao.getCompletedCount(sessionId)
        val failed = trackStatusDao.getFailedCount(sessionId)
        val skipped = tracks.size - completed - failed

        sessionDao.updateProgress(sessionId, completed, failed)
        sessionDao.updateStatus(sessionId, "COMPLETED")

        onComplete?.invoke(completed, failed, skipped)
        Log.d(TAG, "Import complete: $completed imported, $failed failed, $skipped skipped")

        sessionId
    }

    /**
     * Cancel the current import.
     * (Black hole #3: cooperative cancellation)
     */
    fun cancel() {
        isCancelled = true
        importJob?.cancel()
        importJob = null
        importScope?.cancel()
        importScope = null
    }

    /**
     * Resume an interrupted import.
     * (Black hole #1: persistent state)
     */
    suspend fun resumeImport(sessionId: Long) = withContext(Dispatchers.IO) {
        val session = sessionDao.getActiveSession() ?: run {
            Log.e(TAG, "No active session to resume")
            return@withContext
        }

        if (session.sessionId != sessionId) {
            Log.e(TAG, "Session mismatch: expected ${session.sessionId}, got $sessionId")
            return@withContext
        }

        Log.d(TAG, "Resuming import for session $sessionId")
        processImport(sessionId, session.totalTracks)
    }

    /**
     * Get the current active session (for UI display).
     */
    suspend fun getActiveSession(): ImportSession? = sessionDao.getActiveSession()

    private fun detectPlatform(url: String): Pair<IPlaylistImporter, String>? {
        when {
            DeezerImporter.canHandle(url) -> {
                val id = DeezerImporter.extractPlaylistId(url)
                return if (id != null) DeezerImporter to id else null
            }
            SpotifyImporter.canHandle(url) -> {
                val id = SpotifyImporter.extractPlaylistId(url)
                return if (id != null) SpotifyImporter to id else null
            }
            YouTubeImporter.canHandle(url) -> {
                val id = YouTubeImporter.extractPlaylistId(url)
                return if (id != null) YouTubeImporter to id else null
            }
        }
        return null
    }

    /**
     * Process the import in batches.
     * (Black hole #5: batch processing for memory)
     */
    private suspend fun processImport(sessionId: Long, totalTracks: Int) {
        var completedCount = 0
        var failedCount = 0

        while (!isCancelled) {
            val pendingTracks = trackStatusDao.getPendingTracks(sessionId, BATCH_SIZE)
            if (pendingTracks.isEmpty()) break

            Log.d(TAG, "Batch: ${pendingTracks.size} pending tracks remaining")

            for (track in pendingTracks) {
                currentCoroutineContext().ensureActive()
                if (isCancelled) break

                downloadSemaphore.withPermit {
                    delay(Random.nextLong(MIN_SEARCH_DELAY_MS, MAX_SEARCH_DELAY_MS + 1))

                    trackStatusDao.markDownloading(track.id)

                    try {
                        val success = importSingleTrackWithRetry(track)

                        if (success) {
                            completedCount++
                        } else {
                            failedCount++
                        }

                        sessionDao.updateProgress(sessionId, completedCount, failedCount)

                        onProgress?.invoke(
                            completedCount + failedCount,
                            totalTracks,
                            "${track.artist} - ${track.title}"
                        )

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error processing track: ${e.message}")
                        trackStatusDao.markFailed(track.id, e.message ?: "Unknown error")
                        failedCount++
                    }
                }
            }
        }
    }

    /**
     * Import a single track with exponential backoff retry.
     * (Black hole #2: scoring + Black hole #4: non-blocking metadata)
     */
    private suspend fun importSingleTrackWithRetry(
        track: ImportTrackStatus,
        attempt: Int = 0
    ): Boolean {
        return try {
            importSingleTrack(track)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt < MAX_RETRIES) {
                val backoffMs = min(2000L * (1L shl attempt), 30_000L)
                val jitterMs = Random.nextLong(0, 1000)
                Log.w(TAG, "Track failed (attempt ${attempt + 1}), retrying in ${backoffMs + jitterMs}ms: ${e.message}")
                delay(backoffMs + jitterMs)
                importSingleTrackWithRetry(track, attempt + 1)
            } else {
                Log.e(TAG, "Track failed after $MAX_RETRIES retries: ${track.artist} - ${track.title}")
                trackStatusDao.markFailed(track.id, e.message ?: "Max retries exceeded")
                false
            }
        }
    }

    /**
     * Import a single track: search YouTube → download → metadata → tags → save.
     * (Black hole #2: scoring, Black hole #4: parallel metadata)
     */
    private suspend fun importSingleTrack(track: ImportTrackStatus) {
        val searchQuery = ImportedTrack(track.title, track.artist, track.album, track.durationSec).searchQuery
        Log.d(TAG, "Importing track ${track.artist} - ${track.title} (query: $searchQuery)")
        val searchResults = searchYouTube(searchQuery)

        if (searchResults.isEmpty()) {
            throw Exception("No YouTube results for: $searchQuery")
        }

        val bestMatch = pickBestMatch(searchResults, track.durationSec)
            ?: throw Exception("No suitable match found for: $searchQuery")

        val tempFile = downloadToTemp(bestMatch.url)
        Log.d(TAG, "Downloaded to temp: ${tempFile.absolutePath} (${tempFile.length() / 1024}KB)")

        try {
            // Step 1: Fetch metadata FIRST (iTunes/MusicBrainz) to get correct artist/title
            val metadata = fetchMetadata(track)
            val finalMetadata = metadata ?: MetadataResult(
                title = track.title,
                artist = track.artist,
                album = track.album,
                genre = "",
                year = "",
                trackNumber = 0
            )

            // Step 2: Fetch lyrics using CORRECTED metadata (not raw Spotify/Deezer data)
            val lyrics = fetchLyricsWithMetadata(finalMetadata.artist, finalMetadata.title)

            // Step 3: Write tags with everything
            writeTags(tempFile, finalMetadata, lyrics)
            Log.d(TAG, "Tags written to: ${tempFile.absolutePath}")

            val finalPath = moveToOrganizedFolder(tempFile, finalMetadata)

            // Step 4: Save to Room DB with lyrics so the player can show them
            saveSongToDb(finalMetadata, lyrics, finalPath)

            trackStatusDao.markCompleted(track.id, finalPath)

            // Notify that this track completed (for UI updates in DownloadsFragment)
            onTrackCompleted?.invoke(
                finalMetadata.title,
                finalMetadata.artist,
                finalPath
            )

            // Generate waveform in background (don't block)
            importScope?.launch {
                generateWaveform(finalPath)
            }

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private suspend fun searchYouTube(query: String): List<YouTubeSearchResult> {
        return try {
            val extractor = com.beatohm.extractor.YouTubeExtractor()
            val result = extractor.searchSongs(query)
            if (result.isSuccess) {
                result.getOrNull()?.map { searchResult ->
                    YouTubeSearchResult(
                        url = searchResult.youtubeUrl,
                        title = searchResult.title,
                        durationSec = searchResult.durationSeconds.toInt()
                    )
                } ?: emptyList()
            } else {
                Log.e(TAG, "YouTube search failed: ${result.exceptionOrNull()?.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube search error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Pick best YouTube match using weighted scoring.
     * (Black hole #2: scoring system)
     */
    private fun pickBestMatch(results: List<YouTubeSearchResult>, targetDuration: Int): YouTubeSearchResult? {
        if (results.isEmpty()) return null

        return results.maxByOrNull { result ->
            var score = 0.0

            val durationDiff = abs(result.durationSec - targetDuration)
            val durationScore = (1.0 - durationDiff.toDouble() / targetDuration.coerceAtLeast(1))
            score += durationScore * 0.6

            val title = result.title.lowercase()
            if (title.contains("audio") || title.contains("official") || title.contains("topic")) {
                score += 0.3
            }

            if (title.contains("lyric")) {
                score += 0.1
            }

            if (title.contains("live") || title.contains("cover") ||
                title.contains("reaction") || title.contains("extended")) {
                score -= 0.4
            }

            score
        }
    }

    private suspend fun downloadToTemp(youtubeUrl: String): java.io.File {
        val proxyResult = proxyDownloader.getDownloadUrl(youtubeUrl)
        if (proxyResult.isFailure) {
            throw Exception("Failed to get download URL: ${proxyResult.exceptionOrNull()?.message}")
        }
        val proxyUrl = proxyResult.getOrThrow()

        // Download to /Music/BeatOhm/Unknown/ — tag here, then rename to final name
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MUSIC
        )
        val stagingDir = java.io.File(musicDir, "${DeviceUtils.MUSIC_FOLDER_NAME}/Unknown")
        stagingDir.mkdirs()
        val tempFile = java.io.File(stagingDir, "${System.currentTimeMillis()}.mp3")

        val request = Request.Builder()
            .url(proxyUrl.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty response body")

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} downloading audio")
            }

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        if (tempFile.length() == 0L) {
            tempFile.delete()
            throw Exception("Downloaded file is empty")
        }

        Log.d(TAG, "Downloaded ${tempFile.length() / 1024}KB to ${tempFile.name}")
        return tempFile
    }

    private suspend fun fetchMetadata(track: ImportTrackStatus): MetadataResult? {
        return try {
            val song = Song(
                title = track.title,
                artist = track.artist,
                album = track.album
            )
            // ADAPTACIÓN MÍNIMA POR COMPILACIÓN (T4): la firma nueva devuelve el
            // MetadataResult del paquete metadata (nombre completo para no chocar con
            // el MetadataResult interno de este manager). T5 refina este manejo.
            val result = metadataFetcher.fetchFullMetadata(song)
            val candidate = when (result) {
                is com.beatohm.metadata.MetadataResult.ClearMatch -> result.candidate
                is com.beatohm.metadata.MetadataResult.AmbiguousMatches ->
                    result.candidates.maxByOrNull { it.score }
                com.beatohm.metadata.MetadataResult.NoMatch -> null
            }
            if (candidate != null) {
                MetadataResult(
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    genre = candidate.genre,
                    year = candidate.year,
                    trackNumber = 0
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMetadata error: ${e.message}")
            null
        }
    }

    private suspend fun fetchLyrics(track: ImportTrackStatus): String? {
        return try {
            val result = lyricsFetcher.fetchLyrics(track.artist, track.title)
            if (result.isSuccess) {
                result.getOrThrow().plainText
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLyrics error: ${e.message}")
            null
        }
    }

    /**
     * Fetch lyrics using corrected metadata (after iTunes/MusicBrainz lookup).
     * Uses the clean artist/title instead of raw Spotify/Deezer data.
     */
    private suspend fun fetchLyricsWithMetadata(artist: String, title: String): String? {
        return try {
            Log.d(TAG, "fetchLyricsWithMetadata: '$artist' - '$title'")
            val result = lyricsFetcher.fetchLyrics(artist, title)
            if (result.isSuccess) {
                val lyrics = result.getOrThrow().plainText
                Log.d(TAG, "fetchLyricsWithMetadata OK: ${lyrics.length} chars")
                lyrics
            } else {
                Log.d(TAG, "fetchLyricsWithMetadata: no lyrics found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLyricsWithMetadata error: ${e.message}")
            null
        }
    }

    private suspend fun writeTags(file: java.io.File, metadata: MetadataResult, lyrics: String?) {
        try {
            val localSong = LocalSong(
                id = file.absolutePath,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                genre = metadata.genre,
                year = metadata.year,
                trackNumber = metadata.trackNumber,
                lyrics = lyrics ?: ""
            )
            tagWriteCoordinator.writeMetadata(file, localSong)
        } catch (e: Exception) {
            Log.e(TAG, "writeTags error: ${e.message}")
        }
    }

    private fun moveToOrganizedFolder(
        tempFile: java.io.File,
        metadata: MetadataResult
    ): String {
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MUSIC
        )

        // Use the same folder pattern as normal downloads (user-configurable in Settings)
        val pattern = context.getSharedPreferences(
            com.beatohm.util.FolderPatternParser.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        ).getString(
            com.beatohm.util.FolderPatternParser.KEY_FOLDER_PATTERN,
            com.beatohm.util.FolderPatternParser.DEFAULT_PATTERN
        ) ?: com.beatohm.util.FolderPatternParser.DEFAULT_PATTERN

        val song = Song(
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            genre = metadata.genre,
            year = metadata.year,
            trackNumber = metadata.trackNumber
        )

        val (subDir, fileName) = com.beatohm.util.FolderPatternParser.resolvePattern(pattern, song)

        val targetDir = java.io.File(musicDir, "${DeviceUtils.MUSIC_FOLDER_NAME}/$subDir")
        targetDir.mkdirs()

        val targetFile = java.io.File(targetDir, "$fileName.${tempFile.extension}")

        if (targetFile.exists()) targetFile.delete()

        // copyTo works across filesystems; renameTo does NOT on Android
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()

        Log.d(TAG, "Moved to: ${targetFile.absolutePath} (${targetFile.length() / 1024}KB)")
        return targetFile.absolutePath
    }

    /**
     * Save imported song to Room DB so the player can show lyrics and metadata.
     */
    private suspend fun saveSongToDb(metadata: MetadataResult, lyrics: String?, filePath: String) {
        try {
            val localSong = LocalSong(
                id = filePath,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                genre = metadata.genre,
                year = metadata.year,
                trackNumber = metadata.trackNumber,
                filePath = filePath,
                lyrics = lyrics ?: ""
            )
            db.songDao().insertSong(localSong)
            Log.d(TAG, "Saved to DB: ${metadata.artist} - ${metadata.title} (lyrics: ${(lyrics ?: "").length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "saveSongToDb error: ${e.message}")
        }
    }

    private suspend fun generateWaveform(path: String) {
        try {
            val waveform = com.beatohm.audio.WaveformExtractor.extract(path)
            val waveformJson = Gson().toJson(waveform.toList())
            val songId = db.songDao().getIdByPath(path)
            if (songId != null) {
                db.songDao().updateWaveform(songId, waveformJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateWaveform error: ${e.message}")
        }
    }

    data class YouTubeSearchResult(
        val url: String,
        val title: String,
        val durationSec: Int
    )

    data class MetadataResult(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: String,
        val trackNumber: Int
    )

}
