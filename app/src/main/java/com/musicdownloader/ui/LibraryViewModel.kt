package com.musicdownloader.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.musicdownloader.data.LocalSong
import com.musicdownloader.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    data class EnrichmentProgress(
        val done: Int,
        val total: Int,
        val currentTitle: String,
        val withLyrics: Boolean = false
    )

    private val repo = MusicRepository(application)

    private val _folders = MutableLiveData<List<String>>(emptyList())
    val folders: LiveData<List<String>> = _folders

    private val _allSongs = MutableLiveData<List<LocalSong>>(emptyList())
    val allSongs: LiveData<List<LocalSong>> = _allSongs

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _offerEnrichment = MutableLiveData<Int?>(null)
    val offerEnrichment: LiveData<Int?> = _offerEnrichment

    private val _enrichmentProgress = MutableLiveData<EnrichmentProgress?>(null)
    val enrichmentProgress: LiveData<EnrichmentProgress?> = _enrichmentProgress

    private val _incompleteCount = MutableLiveData(0)
    val incompleteCount: LiveData<Int> = _incompleteCount

    private var pendingIncomplete: List<LocalSong> = emptyList()
    private var enrichmentJob: Job? = null

    fun refreshLibrary() {
        if (_isScanning.value == true) return
        _folders.postValue(repo.getLibraryFolders())
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.postValue(true)
            try {
                _allSongs.postValue(repo.getAllSongsNow())
                val result = repo.fastScan()
                _allSongs.postValue(result.songs)

                launch(Dispatchers.IO) {
                    repo.enrichMetadataGradually(result.songs) { done, total, title ->
                        Log.d(TAG, "Enriching: $done/$total - $title")
                    }
                    repo.extractMissingWaveforms(repo.getAllSongsNow()) { done, total ->
                        Log.d(TAG, "Waveform: $done/$total")
                    }
                }

                val offered = offeredIds()
                val pending = result.incompleteSongs.filter { it.id !in offered }
                pendingIncomplete = pending
                _incompleteCount.postValue(result.incompleteSongs.size)
                if (enrichmentJob?.isActive != true && pending.isNotEmpty()) {
                    _offerEnrichment.postValue(pending.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning library", e)
            } finally {
                _isScanning.postValue(false)
                _folders.postValue(repo.getLibraryFolders())
            }
        }
    }

    fun refreshFolders() {
        _folders.postValue(repo.getLibraryFolders())
    }

    fun addFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.addLibraryFolder(path)
            refreshLibrary()
        }
    }

    fun removeFolder(path: String, deleteSongs: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeLibraryFolder(path)
            if (deleteSongs) repo.deleteSongsInFolder(path)
            _folders.postValue(repo.getLibraryFolders())
            refreshLibrary()
        }
    }

    fun startEnrichment(selectedSongs: List<LocalSong>, options: com.musicdownloader.data.MusicRepository.EnrichOptions) {
        if (selectedSongs.isEmpty() || enrichmentJob?.isActive == true) return
        _offerEnrichment.postValue(null)
        val playingPath = PlayerViewModel.getInstance(getApplication()).currentSong.value?.filePath.orEmpty()
        enrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            var done = 0
            val total = selectedSongs.size
            for (song in selectedSongs) {
                try {
                    val updated = repo.enrichSong(
                        song,
                        options,
                        skipTagWrite = song.filePath == playingPath
                    )
                    if (!isIncomplete(updated)) {
                        addOfferedId(song.id)
                    }
                } catch (_: Exception) {}
                done++
                _enrichmentProgress.postValue(
                    EnrichmentProgress(done, total, song.title, options.fetchLyrics)
                )
            }
            _enrichmentProgress.postValue(null)
            _offerEnrichment.postValue(null)
            pendingIncomplete = emptyList()
            try {
                repo.consolidateArtists(skipPath = playingPath)
            } catch (_: Exception) {}
            val freshSongs = repo.getAllSongsNow()
            _incompleteCount.postValue(freshSongs.count { isIncomplete(it) })
            _allSongs.postValue(freshSongs)
        }
    }

    fun startEnrichment(options: com.musicdownloader.data.MusicRepository.EnrichOptions = com.musicdownloader.data.MusicRepository.EnrichOptions()) {
        if (pendingIncomplete.isEmpty() || enrichmentJob?.isActive == true) return
        startEnrichment(pendingIncomplete, options)
    }

    fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || isGenericGenre(song.genre)
            || song.thumbnailUrl.isBlank() || song.year.isBlank()
    }

    private fun isGenericGenre(genre: String): Boolean {
        if (genre.isBlank()) return true
        return genre.lowercase().trim() in setOf(
            "music", "musica", "música", "unknown", "unknow", "other", "none", "n/a", "audio"
        )
    }

    fun dismissEnrichmentOffer() {
        addOfferedIds(pendingIncomplete.map { it.id })
        pendingIncomplete = emptyList()
        _offerEnrichment.value = null
        _incompleteCount.value = 0
    }

    private fun enrichmentPrefs() =
        getApplication<Application>().getSharedPreferences(ENRICHMENT_PREFS, Context.MODE_PRIVATE)

    private fun offeredIds(): Set<String> =
        enrichmentPrefs().getStringSet(KEY_ENRICHED_IDS, null) ?: emptySet()

    private fun addOfferedId(id: String) {
        val current = HashSet(offeredIds())
        current.add(id)
        enrichmentPrefs().edit().putStringSet(KEY_ENRICHED_IDS, current).apply()
    }

    private fun addOfferedIds(ids: List<String>) {
        val current = HashSet(offeredIds())
        current.addAll(ids)
        enrichmentPrefs().edit().putStringSet(KEY_ENRICHED_IDS, current).apply()
    }

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val ENRICHMENT_PREFS = "enrichment_prefs"
        private const val KEY_ENRICHED_IDS = "enriched_ids_v2"
    }
}
