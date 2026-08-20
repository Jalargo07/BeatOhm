package com.beatohm.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.beatohm.DeviceUtils
import com.beatohm.data.AppDatabase
import com.beatohm.data.LocalSong
import com.beatohm.data.MetadataCandidateRepository
import com.beatohm.data.MusicRepository
import com.beatohm.downloader.AudioDownloader
import com.beatohm.downloader.ProxyDownloader
import com.beatohm.extractor.YouTubeExtractor
import com.beatohm.metadata.MetadataCandidate
import com.beatohm.metadata.MetadataFetcher
import com.beatohm.metadata.LyricsFetcher
import com.beatohm.metadata.MetadataResult
import com.beatohm.model.DownloadState
import com.beatohm.model.DownloadStatus
import com.beatohm.model.SearchResult
import com.beatohm.util.FolderPatternParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloads = MutableLiveData<List<DownloadState>>(emptyList())
    val downloads: LiveData<List<DownloadState>> = _downloads

    private val _isDownloading = MutableLiveData(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    private val _searchResults = MutableLiveData<List<SearchResult>>(emptyList())
    val searchResults: LiveData<List<SearchResult>> = _searchResults

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private val _searchError = MutableLiveData<String?>(null)
    val searchError: LiveData<String?> = _searchError

    private val extractor = YouTubeExtractor()
    private val metadataFetcher = MetadataFetcher()
    private val lyricsFetcher = LyricsFetcher()
    private val audioDownloader = AudioDownloader(application)
    private val proxyDownloader = ProxyDownloader()
    val musicRepository = MusicRepository(
        application,
        metadataCandidateRepo = MetadataCandidateRepository(AppDatabase.getInstance(application).metadataCandidateDao())
    )

    fun startDownload(url: String) {
        Log.e(TAG, "startDownload: $url")
        val downloadId = UUID.randomUUID().toString()

        val newDownload = DownloadState(id = downloadId, url = url, status = DownloadStatus.QUEUED)
        addDownload(newDownload)

        viewModelScope.launch {
            try {
                Log.e(TAG, "Iniciando corrutina de descarga")
                updateState(downloadId, DownloadStatus.EXTRACTING, 0, "Extrayendo información...")

                val isPlaylist = extractor.isPlaylistUrl(url)
                val songs: List<com.beatohm.model.Song>

                if (isPlaylist) {
                    val result = extractor.extractPlaylist(url)
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: Exception("Failed to extract playlist")
                    }
                    songs = result.getOrThrow()
                } else {
                    val result = extractor.extractSong(url)
                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: Exception("Failed to extract song")
                    }
                    songs = listOf(result.getOrThrow())
                }

                var successCount = 0
                for (index in songs.indices) {
                    val song = songs[index]
                    Log.e(TAG, "Procesando: ${song.title}")
                    updateState(downloadId, DownloadStatus.FETCHING_METADATA, 0,
                        "Metadata: ${song.title}... (${index + 1}/${songs.size})")

                    // ADAPTACIÓN MÍNIMA POR COMPILACIÓN (T4): la firma nueva devuelve
                    // MetadataResult. T5 refina este manejo (bottom sheet de elección
                    // para los casos ambiguos).
                    val metaResult = metadataFetcher.fetchFullMetadata(song)
                    val enrichedSong = when (metaResult) {
                        is MetadataResult.ClearMatch -> applyCandidateMetadata(song, metaResult.candidate)
                        is MetadataResult.AmbiguousMatches ->
                            metaResult.candidates.maxByOrNull { it.score }
                                ?.let { applyCandidateMetadata(song, it) } ?: song
                        MetadataResult.NoMatch -> song
                    }
                    Log.e(TAG, "Metadata: artist=${enrichedSong.artist} album=${enrichedSong.album}")

                    val lyricsResult = lyricsFetcher.fetchLyrics(enrichedSong.artist, enrichedSong.title)
                    val finalSong = if (lyricsResult.isSuccess) {
                        val result = lyricsResult.getOrThrow()
                        val l = result.syncedLrc ?: result.plainText
                        Log.e(TAG, "Letras: ${l.take(50)}...")
                        enrichedSong.copy(lyrics = l)
                    } else {
                        Log.e(TAG, "Sin letras: ${lyricsResult.exceptionOrNull()?.message}")
                        enrichedSong
                    }

                    updateState(downloadId, DownloadStatus.DOWNLOADING, 0,
                        "Descargando ${finalSong.title}... (${index + 1}/${songs.size})")
                    updateSong(downloadId, finalSong)

                    val audioResult = extractor.getBestAudioStream(finalSong.youtubeUrl)
                    Log.e(TAG, "Audio stream result: ${audioResult.isSuccess}")
                    if (audioResult.isFailure) {
                        updateState(downloadId, DownloadStatus.ERROR, 0,
                            "No se pudo obtener audio: ${audioResult.exceptionOrNull()?.localizedMessage}")
                        continue
                    }

                    val audioStream = audioResult.getOrThrow()
                    val pattern = currentFolderPattern()
                    val (_, fileName) = FolderPatternParser.resolvePattern(pattern, finalSong)
                    val downloadDir = getDownloadDirectory(finalSong)

                    // Try proxy download (loader.to) for reliable delivery
                    Log.e(TAG, "Obteniendo URL de proxy...")
                    updateState(downloadId, DownloadStatus.DOWNLOADING, 0,
                        "Obteniendo descarga via proxy...")

                    val proxyResult = proxyDownloader.getDownloadUrl(finalSong.youtubeUrl)
                    var fileResult: Result<File>
                    if (proxyResult.isSuccess) {
                        val proxyUrl = proxyResult.getOrThrow()
                        Log.e(TAG, "Proxy URL: ${proxyUrl.url.take(80)}...")
                        fileResult = audioDownloader.downloadAudio(
                            audioUrl = proxyUrl.url,
                            mimeType = "audio/mpeg",
                            song = finalSong,
                            outputDir = downloadDir,
                            outputFileName = fileName,
                            onProgress = { progress ->
                                updateState(downloadId, DownloadStatus.DOWNLOADING, progress)
                            }
                        )
                    } else {
                        Log.e(TAG, "Proxy fallo: ${proxyResult.exceptionOrNull()?.message}, intentando directo...")
                        fileResult = audioDownloader.downloadAudio(
                            audioUrl = audioStream.url,
                            mimeType = audioStream.mimeType,
                            song = finalSong,
                            outputDir = downloadDir,
                            outputFileName = fileName,
                            onProgress = { progress ->
                                updateState(downloadId, DownloadStatus.DOWNLOADING, progress)
                            }
                        )
                    }

                    Log.e(TAG, "Download result isSuccess=${fileResult.isSuccess}")
                    if (fileResult.isSuccess) {
                        val file = fileResult.getOrThrow()
                        Log.e(TAG, "Archivo: ${file.name} (${file.length()} bytes)")

                        val inserted = LocalSong(
                            id = file.absolutePath,
                            title = finalSong.title,
                            artist = finalSong.artist,
                            album = finalSong.album,
                            genre = finalSong.genre,
                            year = finalSong.year,
                            trackNumber = finalSong.trackNumber,
                            duration = finalSong.duration * 1000,
                            filePath = file.absolutePath,
                            lyrics = finalSong.lyrics
                        )

                        musicRepository.insertSong(inserted)

                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                musicRepository.enrichSong(inserted, skipTagWrite = true, fetchLyrics = true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Background enrich failed for '${inserted.title}': ${e.message}")
                            }
                        }

                        updateState(downloadId, DownloadStatus.TAGGING, 100,
                            "OK ${file.name} (${file.length() / 1024} KB)", file.absolutePath)
                        successCount++
                    } else {
                        val err = fileResult.exceptionOrNull()
                        Log.e(TAG, "Error descarga: ${err?.message}")
                        updateState(downloadId, DownloadStatus.ERROR, 0,
                            "Error: ${err?.localizedMessage ?: "desconocido"}")
                    }
                }

                val msg = if (successCount == songs.size) "Completado: $successCount canciones"
                    else "Descargadas $successCount de ${songs.size}"
                updateState(downloadId, DownloadStatus.COMPLETED, 100, msg)

            } catch (e: Exception) {
                updateState(downloadId, DownloadStatus.ERROR, 0,
                    "Error: ${e.localizedMessage ?: "Desconocido"}")
            }
        }
    }

    private fun currentFolderPattern(): String {
        val prefs = getApplication<Application>().getSharedPreferences(
            FolderPatternParser.PREFS_NAME, Context.MODE_PRIVATE
        )
        return prefs.getString(FolderPatternParser.KEY_FOLDER_PATTERN, FolderPatternParser.DEFAULT_PATTERN)
            ?: FolderPatternParser.DEFAULT_PATTERN
    }

    private fun getDownloadDirectory(song: com.beatohm.model.Song? = null): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val baseDir = File(dir, DeviceUtils.MUSIC_FOLDER_NAME)
        if (song == null) {
            if (!baseDir.exists()) baseDir.mkdirs()
            Log.e(TAG, "Dir: ${baseDir.absolutePath}")
            return baseDir
        }
        val (subDir, _) = FolderPatternParser.resolvePattern(currentFolderPattern(), song)
        val targetDir = File(baseDir, subDir)
        if (!targetDir.exists()) targetDir.mkdirs()
        Log.e(TAG, "Dir: ${targetDir.absolutePath}")
        return targetDir
    }

    fun searchSongs(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            Log.e(TAG, "searchSongs iniciado: query=$query")
            _isSearching.value = true
            _searchError.value = null
            _searchResults.value = emptyList()

            val result = extractor.searchSongs(query)
            if (result.isSuccess) {
                val results = result.getOrThrow()
                _searchResults.value = results
                Log.e(TAG, "searchSongs OK: ${results.size} resultados")
                if (results.isEmpty()) {
                    _searchError.value = "No se encontraron resultados"
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Error de búsqueda"
                Log.e(TAG, "searchSongs ERROR: $err", result.exceptionOrNull())
                _searchError.value = err
            }
            _isSearching.value = false
        }
    }

    fun downloadFromSearch(result: SearchResult) {
        startDownload(result.youtubeUrl)
    }

    /**
     * Add an imported track to the downloads list UI.
     * Called by ImportPlaylistService when a track finishes importing.
     */
    fun addImportedTrack(title: String, artist: String, filePath: String) {
        val song = com.beatohm.model.Song(
            title = title,
            artist = artist,
            filePath = filePath
        )
        val state = DownloadState(
            id = "import_${System.currentTimeMillis()}_${title.hashCode()}",
            url = "",
            song = song,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            filePath = filePath
        )
        addDownload(state)
    }

    private fun addDownload(state: DownloadState) {
        val list = _downloads.value?.toMutableList() ?: mutableListOf()
        list.add(0, state)
        _downloads.value = list
        _isDownloading.value = true
    }

    private fun updateState(id: String, status: DownloadStatus, progress: Int = 0, message: String? = null, filePath: String = "") {
        val list = _downloads.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(
            status = status,
            progress = progress,
            errorMessage = message ?: list[idx].errorMessage,
            filePath = filePath.ifBlank { list[idx].filePath }
        )
        _downloads.value = list
        _isDownloading.value = list.any { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.FETCHING_METADATA || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.TAGGING }
    }

    private fun updateSong(id: String, song: com.beatohm.model.Song) {
        val list = _downloads.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(song = song)
        _downloads.value = list
    }

    /**
     * Aplica un candidato de metadata sobre la canción original (los campos que el
     * candidato no provee se conservan). Helper temporal de la adaptación por
     * compilación (T4); T5 lo refina con el bottom sheet de elección.
     */
    private fun applyCandidateMetadata(
        song: com.beatohm.model.Song,
        candidate: MetadataCandidate
    ): com.beatohm.model.Song = song.copy(
        title = candidate.title.ifBlank { song.title },
        artist = candidate.artist.ifBlank { song.artist },
        album = candidate.album,
        genre = candidate.genre,
        year = candidate.year,
        thumbnailUrl = candidate.artworkUrl.ifBlank { song.thumbnailUrl }
    )

    companion object {
        private const val TAG = "BeatOhm"

        /** Shared instance set by the Activity so the import service can update the downloads list. */
        var instance: MainViewModel? = null
            private set
    }

    init {
        instance = this
    }
}
