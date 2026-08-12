package com.musicdownloader.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(context: Context) {
    private val playlistDao = AppDatabase.getInstance(context).playlistDao()

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun createPlaylist(playlist: Playlist): Long = playlistDao.createPlaylist(playlist)

    suspend fun updatePlaylist(playlist: Playlist) = playlistDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.deletePlaylist(playlist)

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        playlistDao.addSongToPlaylist(PlaylistSong(playlistId, songId, 0))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeSongFromPlaylist(PlaylistSong(playlistId, songId, 0))
    }

    suspend fun clearPlaylist(playlistId: Long) = playlistDao.clearPlaylist(playlistId)

    fun getSongCount(playlistId: Long): Flow<Int> = playlistDao.getSongCount(playlistId)

    suspend fun updateSongCount(playlistId: Long, count: Int) = playlistDao.updateSongCount(playlistId, count)
}
