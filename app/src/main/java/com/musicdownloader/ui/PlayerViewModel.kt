package com.musicdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.musicdownloader.data.MusicRepository
import com.musicdownloader.data.toSong
import com.musicdownloader.model.Song
import kotlinx.coroutines.runBlocking

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private var instance: PlayerViewModel? = null
        fun getInstance(app: Application): PlayerViewModel {
            if (instance == null) instance = PlayerViewModel(app)
            return instance!!
        }
    }

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _duration = MutableLiveData(0L)
    val duration: LiveData<Long> = _duration

    private val _playlist = MutableLiveData<List<Song>>(emptyList())
    val playlist: LiveData<List<Song>> = _playlist

    private val _displayPlaylist = MutableLiveData<List<Song>>(emptyList())
    val displayPlaylist: LiveData<List<Song>> = _displayPlaylist

    private val _isShuffle = MutableLiveData(false)
    val isShuffle: LiveData<Boolean> = _isShuffle

    private val _repeatMode = MutableLiveData(RepeatMode.ALL)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private var currentIndex = 0
    private var shuffleIndex = 0
    private val shuffleOrder = mutableListOf<Int>()

    private fun updateDisplayPlaylist() {
        val base = _playlist.value ?: emptyList()
        if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            _displayPlaylist.value = shuffleOrder.mapNotNull { base.getOrNull(it) }
        } else {
            _displayPlaylist.value = base
        }
    }

    fun setSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setPosition(pos: Long) {
        _currentPosition.value = pos
    }

    fun setDuration(dur: Long) {
        _duration.value = dur
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        _playlist.value = songs
        currentIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        if (songs.isNotEmpty()) {
            shuffleOrder.clear()
            shuffleIndex = 0
            if (_isShuffle.value == true) buildShuffleOrder()
            setSong(songs[currentIndex])
        }
        updateDisplayPlaylist()
    }

    fun toggleShuffle() {
        val list = _playlist.value ?: return
        if (list.isEmpty()) return
        if (_isShuffle.value == true) {
            _isShuffle.value = false
            currentIndex = shuffleOrder.getOrElse(shuffleIndex) { currentIndex }
            shuffleOrder.clear()
            shuffleIndex = 0
        } else {
            buildShuffleOrder()
            _isShuffle.value = true
        }
        updateDisplayPlaylist()
    }

    private fun buildShuffleOrder() {
        val list = _playlist.value
        if (list.isNullOrEmpty()) {
            shuffleOrder.clear()
            shuffleIndex = 0
            updateDisplayPlaylist()
            return
        }
        val n = list.size
        if (n == 1) {
            shuffleOrder.clear()
            shuffleOrder.add(0)
            shuffleIndex = 0
            updateDisplayPlaylist()
            return
        }
        shuffleOrder.clear()
        val remaining = (0 until n).toMutableList()
        remaining.remove(currentIndex)
        remaining.shuffle()
        shuffleOrder.add(currentIndex)
        shuffleOrder.addAll(remaining)
        shuffleIndex = 0
        updateDisplayPlaylist()
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
            else -> RepeatMode.ALL
        }
    }

    fun nextSong(): Song? = onSongFinished()

    fun playAt(index: Int) {
        val list = _playlist.value ?: return
        if (list.isEmpty()) return
        if (index < 0 || index >= list.size) return
        currentIndex = index
        if (_isShuffle.value == true) buildShuffleOrder()
        setSong(list[index])
    }

    fun playAtDisplay(index: Int) {
        val list = _playlist.value ?: return
        if (list.isEmpty()) return
        if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            val baseIndex = shuffleOrder.getOrElse(index) { return }
            playAt(baseIndex)
        } else {
            playAt(index)
        }
    }

    fun removeFromQueue(index: Int) {
        val list = _playlist.value ?: return
        if (index < 0 || index >= list.size) return

        if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            val baseIndex = shuffleOrder.getOrElse(index) { return }
            val wasCurrent = baseIndex == currentIndex

            val newList = list.toMutableList()
            newList.removeAt(baseIndex)
            _playlist.value = newList

            val newShuffleOrder = mutableListOf<Int>()
            for (i in shuffleOrder) {
                if (i == baseIndex) continue
                newShuffleOrder.add(if (i > baseIndex) i - 1 else i)
            }
            shuffleOrder.clear()
            shuffleOrder.addAll(newShuffleOrder)

            if (wasCurrent) {
                if (newList.isEmpty()) {
                    currentIndex = 0
                    _currentSong.value = null
                } else {
                    shuffleIndex = index.coerceIn(0, shuffleOrder.size - 1)
                    currentIndex = shuffleOrder[shuffleIndex]
                    setSong(newList[currentIndex])
                }
            } else if (baseIndex < currentIndex) {
                currentIndex--
            }
            updateDisplayPlaylist()
            return
        }

        val newList = list.toMutableList()
        val wasCurrent = index == currentIndex
        newList.removeAt(index)
        _playlist.value = newList
        if (wasCurrent) {
            if (newList.isEmpty()) {
                currentIndex = 0
                _currentSong.value = null
            } else {
                currentIndex = currentIndex.coerceAtMost(newList.size - 1)
                setSong(newList[currentIndex])
            }
        } else if (index < currentIndex) {
            currentIndex--
        }
        if (_isShuffle.value == true && newList.isNotEmpty()) buildShuffleOrder()
        updateDisplayPlaylist()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _playlist.value ?: return
        if (fromIndex < 0 || fromIndex >= list.size || toIndex < 0 || toIndex >= list.size) return
        if (fromIndex == toIndex) return

        if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            val currentDisplayIndex = shuffleOrder.indexOf(currentIndex)
            val movedBase = shuffleOrder.removeAt(fromIndex)
            shuffleOrder.add(toIndex, movedBase)
            shuffleIndex = when {
                currentDisplayIndex == fromIndex -> toIndex
                fromIndex < currentDisplayIndex && toIndex >= currentDisplayIndex -> shuffleIndex - 1
                fromIndex > currentDisplayIndex && toIndex <= currentDisplayIndex -> shuffleIndex + 1
                else -> shuffleIndex
            }
            updateDisplayPlaylist()
            return
        }

        val newList = list.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)

        currentIndex = when (currentIndex) {
            fromIndex -> toIndex
            in (fromIndex + 1)..toIndex -> currentIndex - 1
            in toIndex until fromIndex -> currentIndex + 1
            else -> currentIndex
        }

        _playlist.value = newList
        if (_isShuffle.value == true && newList.isNotEmpty()) buildShuffleOrder()
        updateDisplayPlaylist()
    }

    fun prevSong(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null
        if (_repeatMode.value == RepeatMode.ONE) {
            val song = list[currentIndex.coerceIn(0, list.size - 1)]
            setSong(song)
            return song
        }
        if (_isShuffle.value == true && shuffleOrder.size > 1) {
            shuffleIndex = if (shuffleIndex > 0) shuffleIndex - 1 else shuffleOrder.size - 1
            currentIndex = shuffleOrder[shuffleIndex]
        } else {
            currentIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        }
        val song = list[currentIndex]
        setSong(song)
        return song
    }

    fun onSongFinished(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            val song = list[currentIndex.coerceIn(0, list.size - 1)]
            setSong(song)
            return song
        }

        return if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            advanceAfterFinishedShuffled()
        } else {
            advanceAfterFinishedSequential()
        }
    }

    private fun advanceAfterFinishedSequential(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null

        val finishedIndex = currentIndex
        val isLast = finishedIndex >= list.size - 1

        if (isLast && _repeatMode.value == RepeatMode.OFF) {
            removeFinishedSong(finishedIndex)
            return null
        }

        val nextInOldList = if (isLast) 0 else finishedIndex + 1
        val newList = removeFinishedSong(finishedIndex)
        if (newList.isEmpty()) return null

        var nextIndex = if (nextInOldList > finishedIndex) nextInOldList - 1 else nextInOldList
        nextIndex = nextIndex.coerceIn(0, newList.size - 1)
        currentIndex = nextIndex
        val song = newList[currentIndex]
        setSong(song)
        return song
    }

    private fun advanceAfterFinishedShuffled(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null

        val finishedBase = shuffleOrder[shuffleIndex]
        val isLastDisplay = shuffleIndex >= shuffleOrder.size - 1

        if (isLastDisplay && _repeatMode.value == RepeatMode.OFF) {
            removeFinishedSong(finishedBase)
            return null
        }

        val newList = removeFinishedSong(finishedBase)
        if (newList.isEmpty()) return null

        if (isLastDisplay) {
            currentIndex = shuffleOrder.getOrElse(shuffleOrder.lastIndex) { 0 }
            buildShuffleOrder()
        } else {
            currentIndex = shuffleOrder[shuffleIndex]
        }
        val song = newList[currentIndex]
        setSong(song)
        return song
    }

    private fun removeFinishedSong(finishedBase: Int): List<Song> {
        val list = _playlist.value ?: return emptyList()
        val newList = list.toMutableList()
        newList.removeAt(finishedBase)
        _playlist.value = newList

        if (_isShuffle.value == true && shuffleOrder.isNotEmpty()) {
            val newOrder = mutableListOf<Int>()
            for (baseIndex in shuffleOrder) {
                if (baseIndex == finishedBase) continue
                newOrder.add(if (baseIndex > finishedBase) baseIndex - 1 else baseIndex)
            }
            shuffleOrder.clear()
            shuffleOrder.addAll(newOrder)
        }
        updateDisplayPlaylist()
        return newList
    }

    fun notifySongEnded(): Song? = onSongFinished()

    enum class RepeatMode { ALL, ONE, OFF }

    fun scanMusicFiles(): List<Song> {
        val repo = MusicRepository(getApplication())
        val localSongs = runBlocking { repo.scanMusicFolder() }
        val songs = localSongs.map { it.toSong() }
        setPlaylist(songs, 0)
        return songs
    }
}
