package com.musicdownloader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC")
    fun getAllSongs(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongsByTitle(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC")
    fun getAllSongsByArtist(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs ORDER BY album COLLATE NOCASE ASC, trackNumber ASC")
    fun getAllSongsByAlbum(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs ORDER BY duration ASC")
    fun getAllSongsByDuration(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsNow(): List<LocalSong>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC")
    fun getSongsByAlbum(album: String): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY artist ASC")
    fun getSongsByGenre(genre: String): Flow<List<LocalSong>>

    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT year FROM songs WHERE year != '' ORDER BY year ASC")
    fun getAllYears(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: LocalSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<LocalSong>)

    @Delete
    suspend fun deleteSong(song: LocalSong)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: String)

    @Query("DELETE FROM songs WHERE id NOT IN (SELECT MIN(id) FROM songs GROUP BY title, artist)")
    suspend fun deleteDuplicateSongs()

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): LocalSong?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY artist ASC")
    fun getFavoriteSongs(): Flow<List<LocalSong>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: String, isFavorite: Boolean)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.songId 
        WHERE ps.playlistId = :playlistId 
        ORDER BY ps.position ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<LocalSong>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT * FROM songs ORDER BY playCount DESC, title COLLATE NOCASE ASC")
    fun getMostPlayedSongs(): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs WHERE year = :year ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, trackNumber ASC")
    fun getSongsByYear(year: String): Flow<List<LocalSong>>

    @Query("SELECT * FROM songs WHERE filePath LIKE :folderPath || '/%' ORDER BY title COLLATE NOCASE ASC")
    fun getSongsInFolder(folderPath: String): Flow<List<LocalSong>>

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)
}
