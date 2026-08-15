package com.beatohm.data

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
    fun renameSongFile(song: LocalSong): LocalSong
    suspend fun saveSong(song: LocalSong)
}
