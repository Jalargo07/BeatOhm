package com.musicdownloader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.musicdownloader.model.Song
import java.io.File

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

    private var currentIndex = 0

    fun setSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = false
        _currentPosition.value = 0L
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
        currentIndex = startIndex
        if (songs.isNotEmpty()) setSong(songs[startIndex])
    }

    fun nextSong(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null
        currentIndex = (currentIndex + 1) % list.size
        val song = list[currentIndex]
        setSong(song)
        return song
    }

    fun prevSong(): Song? {
        val list = _playlist.value ?: return null
        if (list.isEmpty()) return null
        currentIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        val song = list[currentIndex]
        setSong(song)
        return song
    }

    fun scanMusicFiles(): List<Song> {
        val dir = File(getApplication<Application>().getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile,
            "Music/MusicDownloader")
        val songs = mutableListOf<Song>()
        if (!dir.exists()) return songs
        dir.listFiles { f -> f.extension == "mp3" || f.extension == "m4a" || f.extension == "webm" || f.extension == "opus" }
            ?.sortedBy { it.name }?.forEach { file ->
                songs.add(Song(
                    title = file.nameWithoutExtension,
                    artist = "",
                    youtubeUrl = file.absolutePath
                ))
            }
        _playlist.value = songs
        return songs
    }
}
