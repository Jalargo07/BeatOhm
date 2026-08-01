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

    private val _isShuffle = MutableLiveData(false)
    val isShuffle: LiveData<Boolean> = _isShuffle

    private val _repeatMode = MutableLiveData(RepeatMode.ALL)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private var currentIndex = 0
    private var shuffleIndex = 0
    private val shuffleOrder = mutableListOf<Int>()

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
    }

    private fun buildShuffleOrder() {
        val list = _playlist.value
        if (list.isNullOrEmpty()) {
            shuffleOrder.clear()
            shuffleIndex = 0
            return
        }
        val n = list.size
        if (n == 1) {
            shuffleOrder.clear()
            shuffleOrder.add(0)
            shuffleIndex = 0
            return
        }
        shuffleOrder.clear()
        val remaining = (0 until n).toMutableList()
        remaining.remove(currentIndex)
        remaining.shuffle()
        shuffleOrder.add(currentIndex)
        shuffleOrder.addAll(remaining)
        shuffleIndex = 0
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
            else -> RepeatMode.ALL
        }
    }

    fun nextSong(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            val song = list[currentIndex.coerceIn(0, list.size - 1)]
            setSong(song)
            return song
        }

        val isShuffled = _isShuffle.value == true && shuffleOrder.size > 1
        if (_repeatMode.value == RepeatMode.OFF && !isShuffled && currentIndex >= list.size - 1) {
            return null
        }

        if (isShuffled) {
            if (shuffleIndex >= shuffleOrder.size - 1) {
                buildShuffleOrder()
                shuffleIndex = if (shuffleOrder.size > 1) 1 else 0
            } else {
                shuffleIndex++
            }
            currentIndex = shuffleOrder[shuffleIndex]
        } else {
            currentIndex = (currentIndex + 1) % list.size
        }
        val song = list[currentIndex]
        setSong(song)
        return song
    }

    fun playAt(index: Int) {
        val list = _playlist.value ?: return
        if (list.isEmpty()) return
        if (index < 0 || index >= list.size) return
        currentIndex = index
        if (_isShuffle.value == true) buildShuffleOrder()
        setSong(list[index])
    }

    fun removeFromQueue(index: Int) {
        val list = _playlist.value?.toMutableList() ?: return
        if (index < 0 || index >= list.size) return
        val wasCurrent = index == currentIndex
        list.removeAt(index)
        _playlist.value = list
        if (wasCurrent) {
            if (list.isEmpty()) {
                currentIndex = 0
                _currentSong.value = null
            } else {
                currentIndex = currentIndex.coerceAtMost(list.size - 1)
                setSong(list[currentIndex])
            }
        } else if (index < currentIndex) {
            currentIndex--
        }
        if (_isShuffle.value == true && list.isNotEmpty()) buildShuffleOrder()
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

    fun notifySongEnded(): Song? = nextSong()

    enum class RepeatMode { ALL, ONE, OFF }

    fun scanMusicFiles(): List<Song> {
        val repo = MusicRepository(getApplication())
        val localSongs = runBlocking { repo.scanMusicFolder() }
        val songs = localSongs.map { it.toSong() }
        setPlaylist(songs, 0)
        return songs
    }
}
