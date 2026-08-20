package com.beatohm.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.beatohm.data.AppDatabase
import com.beatohm.data.LocalSong
import com.beatohm.data.ILibraryRepository
import com.beatohm.data.IMusicRepository
import com.beatohm.data.IWaveformRepository
import com.beatohm.data.LibraryRepository
import com.beatohm.data.MetadataCandidateRepository
import com.beatohm.data.MusicRepository
import com.beatohm.data.WaveformRepository
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

    private val repo: IMusicRepository = MusicRepository(
        application,
        metadataCandidateRepo = MetadataCandidateRepository(AppDatabase.getInstance(application).metadataCandidateDao())
    )
    private val libraryRepo: ILibraryRepository = LibraryRepository(application)
    private val waveformRepo: IWaveformRepository = WaveformRepository(application)

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
    val pendingIncompleteSongs: List<LocalSong> get() = pendingIncomplete
    private var enrichmentJob: Job? = null

    fun refreshLibrary() {
        if (_isScanning.value == true) return
        _folders.postValue(libraryRepo.getLibraryFolders())
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.postValue(true)
            try {
                // Phase 1: Show existing DB songs immediately
                _allSongs.postValue(repo.getAllSongsNow())

                // Data integrity: limpia duplicados huérfanos ANTES de fastScan(), porque
                // cleanupDuplicates() (dentro de fastScan) borra las filas huérfanas, lo que
                // dispara ON DELETE CASCADE en playback_events (FK real) perdiendo su ranking,
                // y deja referencias colgantes en playlist_songs/regen_status (sin FK, no hay
                // cascade). Acá se migran primero esas referencias a la fila real gemela.
                repo.cleanOrphanDuplicateSongs()

                // Phase 2: Fast scan — find new files, insert to DB with minimal metadata
                val result = libraryRepo.fastScan()
                _allSongs.postValue(result.songs)

                // Phase 3: Background enrichment (gradual, non-blocking)
                // This runs AFTER songs are already visible in UI
                launch(Dispatchers.IO) {
                    repo.enrichMetadataGradually(result.songs) { done, total, title ->
                        Log.d(TAG, "Enriching: $done/$total - $title")
                    }
                    // After metadata enrichment, extract waveforms
                    waveformRepo.extractMissingWaveforms(repo.getAllSongsNow()) { done, total ->
                        Log.d(TAG, "Waveform: $done/$total")
                    }
                }

                // Offer enrichment for incomplete songs
                val offered = offeredIds()
                val pending = result.incompleteSongs.filter { it.id !in offered }
                pendingIncomplete = pending
                _incompleteCount.postValue(result.incompleteSongs.size)
                if (pending.isNotEmpty()) {
                    _offerEnrichment.postValue(pending.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning library", e)
            } finally {
                _isScanning.postValue(false)
                _folders.postValue(libraryRepo.getLibraryFolders())
            }
        }
    }

    fun refreshFolders() {
        _folders.postValue(libraryRepo.getLibraryFolders())
    }

    fun addFolder(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepo.addLibraryFolder(path)
            refreshLibrary()
        }
    }

    fun removeFolder(path: String, deleteSongs: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepo.removeLibraryFolder(path)
            if (deleteSongs) libraryRepo.deleteSongsInFolder(path)
            _folders.postValue(libraryRepo.getLibraryFolders())
            refreshLibrary()
        }
    }

    fun startEnrichment(selectedSongs: List<LocalSong>, fetchLyrics: Boolean = false) {
        if (selectedSongs.isEmpty() || enrichmentJob?.isActive == true) return
        val playingPath = PlayerViewModel.getInstance(getApplication()).currentSong.value?.filePath.orEmpty()
        enrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            var done = 0
            val total = selectedSongs.size
            for (song in selectedSongs) {
                try {
                    repo.enrichSong(song, skipTagWrite = song.filePath == playingPath, fetchLyrics = fetchLyrics)
                } catch (_: Exception) {}
                done++
                _enrichmentProgress.postValue(EnrichmentProgress(done, total, song.title, fetchLyrics))
                addOfferedId(song.id)
            }
            _enrichmentProgress.postValue(null)
            _offerEnrichment.postValue(null)
            pendingIncomplete = emptyList()
            val freshSongs = repo.getAllSongsNow()
            _incompleteCount.postValue(freshSongs.count { isIncomplete(it) })
            _allSongs.postValue(freshSongs)
        }
    }

    fun startEnrichment(fetchLyrics: Boolean = false) {
        if (pendingIncomplete.isEmpty() || enrichmentJob?.isActive == true) return
        startEnrichment(pendingIncomplete, fetchLyrics)
    }

    fun isIncomplete(song: LocalSong): Boolean {
        return repo.isIncomplete(song)
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
        private const val KEY_ENRICHED_IDS = "enriched_ids"
    }
}
