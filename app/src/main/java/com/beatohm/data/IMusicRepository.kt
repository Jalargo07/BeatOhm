package com.beatohm.data

import com.beatohm.metadata.MetadataCandidate
import kotlinx.coroutines.flow.Flow

interface IMusicRepository {

    // Songs
    suspend fun getAllSongsNow(): List<LocalSong>
    fun getAllSongs(): Flow<List<LocalSong>>
    fun getAllSongsByTitle(): Flow<List<LocalSong>>
    fun getAllSongsByArtist(): Flow<List<LocalSong>>
    fun getAllSongsByAlbum(): Flow<List<LocalSong>>
    fun getAllSongsByDuration(): Flow<List<LocalSong>>
    fun getAllAlbums(): Flow<List<String>>
    fun getAllArtists(): Flow<List<String>>
    fun getAllGenres(): Flow<List<String>>
    fun getAllYears(): Flow<List<String>>
    fun getAllAlbumsWithCover(): Flow<List<AlbumWithCover>>
    fun getAllArtistsWithCover(): Flow<List<ArtistWithCover>>
    fun getSongsByAlbum(album: String): Flow<List<LocalSong>>
    fun getSongsByArtist(artist: String): Flow<List<LocalSong>>
    fun getSongsByGenre(genre: String): Flow<List<LocalSong>>
    fun getSongsByYear(year: String): Flow<List<LocalSong>>
    fun getMostPlayedSongs(): Flow<List<LocalSong>>
    fun getSongCount(): Flow<Int>
    fun getFavoriteSongs(): Flow<List<LocalSong>>
    suspend fun setFavorite(songId: String, isFavorite: Boolean)
    suspend fun getSongById(id: String): LocalSong?
    fun isIncomplete(song: LocalSong): Boolean
    suspend fun insertSong(song: LocalSong)
    suspend fun deleteSong(song: LocalSong)
    suspend fun incrementPlayCount(songId: String)

    // Playback scoring
    suspend fun recordPlaybackEvent(songId: String, timestamp: Long, score: Int)
    fun getTopPlayedSongs(sinceTimestamp: Long, limit: Int = 100): Flow<List<LocalSong>>
    suspend fun getSongIdByPath(path: String): String?

    // Enrichment
    suspend fun enrichMetadataGradually(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int, title: String) -> Unit)? = null
    )
    suspend fun enrichSong(
        song: LocalSong,
        skipTagWrite: Boolean = false,
        fetchLyrics: Boolean = false
    ): LocalSong
    suspend fun fetchMetadata(song: LocalSong): LocalSong
    suspend fun downloadArtworkForSong(song: LocalSong): LocalSong
    suspend fun extractDominantColor(song: LocalSong): LocalSong
    suspend fun fetchLyricsForSong(song: LocalSong): LocalSong
    suspend fun writeArtworkToFile(song: LocalSong): LocalSong
    suspend fun writeLyricsToFile(song: LocalSong): LocalSong
    fun renameSongFile(song: LocalSong): LocalSong
    suspend fun saveSong(song: LocalSong)

    // T2: Aplica ClearMatch + finalizeMetadataUpdate (rename + tags + DB migration)
    suspend fun applyClearMatch(song: LocalSong, candidate: MetadataCandidate): LocalSong

    // Metadata candidates (T9): canciones que tienen candidatos ambiguos PENDING
    // sin resolver (para la UI de pendientes)
    suspend fun getSongsWithPendingCandidates(): List<LocalSong>

    // T12: Re-enriquecimiento de canciones con metadata sospechosa
    suspend fun reEnrichSuspiciousSongs(): Pair<Int, Int>

    // Data integrity: migra referencias de filas huérfanas duplicadas y las elimina
    suspend fun cleanOrphanDuplicateSongs()

    // Callback para cuando se alcanza el límite de escritura de tags
    fun setLimitReachedCallback(callback: (() -> Unit)?)
}
