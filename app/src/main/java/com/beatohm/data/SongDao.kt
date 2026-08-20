package com.beatohm.data

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

    @Query("SELECT album AS name, thumbnailUrl AS coverPath FROM songs WHERE thumbnailUrl IS NOT NULL AND thumbnailUrl != '' AND album IS NOT NULL AND album != '' GROUP BY album ORDER BY album COLLATE NOCASE ASC")
    fun getAlbumsWithCover(): Flow<List<AlbumWithCover>>

    @Query("SELECT artist AS name, thumbnailUrl AS coverPath FROM songs WHERE thumbnailUrl IS NOT NULL AND thumbnailUrl != '' AND artist IS NOT NULL AND artist != '' GROUP BY artist ORDER BY artist COLLATE NOCASE ASC")
    fun getArtistsWithCover(): Flow<List<ArtistWithCover>>

    // Inserta con REPLACE (destructivo): si la fila ya existe la borra y la reinserta.
    // OJO: eso dispara ON DELETE CASCADE en playback_events (FK real hacia songs).
    // Por eso las rutas de re-enriquecimiento (enrichSong, saveSong, enrichMetadataGradually)
    // SOLO deben llamar insertSong/insertSongs con filas que NO existen todavía; si la fila
    // existe, usar updateSong/updateSongs. playlist_songs y regen_status no tienen FK: su
    // problema son referencias colgantes / status huérfano, no cascade.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: LocalSong)

    // UPDATE por PK (id): NO borra la fila ni dispara cascade. Preserva playback_events
    // (ranking) y las referencias de playlist_songs/regen_status. Usarlo siempre que la
    // fila ya exista.
    @Update
    suspend fun updateSong(song: LocalSong)

    // Igual que updateSong pero en lote (re-enriquecer canciones existentes sin borrar
    // su ranking ni sus referencias).
    @Update
    suspend fun updateSongs(songs: List<LocalSong>)

    // REPLACE destructivo en lote: SOLO para filas nuevas (ver comentario de insertSong).
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

    @Query("UPDATE songs SET waveformData = :data WHERE id = :songId")
    suspend fun updateWaveform(songId: String, data: String)

    @Query("UPDATE songs SET waveformData = '' WHERE id = :songId")
    suspend fun clearWaveform(songId: String)

    @Query("UPDATE songs SET waveformData = ''")
    suspend fun clearAllWaveforms()

    @Query("UPDATE songs SET dominantColor = :color WHERE id = :songId")
    suspend fun updateDominantColor(songId: String, color: Int)

    @Query("SELECT id FROM songs WHERE filePath = :path LIMIT 1")
    suspend fun getIdByPath(path: String): String?

    // === Migración de referencias al renombrar/limpiar canciones (id == filePath) ===

    @Query("UPDATE playback_events SET songId = :newId WHERE songId = :oldId")
    suspend fun movePlaybackEvents(oldId: String, newId: String)

    // UPDATE OR IGNORE: si la playlist ya contiene a newId (PK compuesta playlistId+songId),
    // la membresía de oldId se descarta (IGNORE) en vez de abortar con violación UNIQUE.
    @Query("UPDATE OR IGNORE playlist_songs SET songId = :newId WHERE songId = :oldId")
    suspend fun movePlaylistSongs(oldId: String, newId: String)

    // Migración de regen_status (songId es PK → un UPDATE directo puede violar UNIQUE si
    // newId ya tiene fila). Estrategia copiar-y-borrar, llamada dentro de la transacción
    // del caller:
    // 1) copyRegenStatus: INSERT OR IGNORE copia el status de oldId a newId; si newId ya
    //    tiene fila, el INSERT se ignora y el status de oldId se pierde (aceptable:
    //    regen_status es efímero) mientras el del target se conserva. No deja filas
    //    colgantes (a diferencia de UPDATE OR IGNORE, que dejaría la fila de oldId suelta).
    // 2) deleteRegenStatus: elimina la fila de oldId.
    @Query("INSERT OR IGNORE INTO regen_status (songId, status) SELECT :newId, status FROM regen_status WHERE songId = :oldId")
    suspend fun copyRegenStatus(oldId: String, newId: String)

    @Query("DELETE FROM regen_status WHERE songId = :oldId")
    suspend fun deleteRegenStatus(oldId: String)

    // === Artistas duplicados ===

    @Query("SELECT COUNT(*) FROM songs WHERE artist = :artist")
    suspend fun getSongCountByArtist(artist: String): Int

    @Query("UPDATE songs SET artist = :newArtist WHERE artist = :oldArtist")
    suspend fun updateArtist(oldArtist: String, newArtist: String): Int
}

data class AlbumWithCover(val name: String, val coverPath: String)
data class ArtistWithCover(val name: String, val coverPath: String)
