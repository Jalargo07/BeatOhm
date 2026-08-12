package com.beatohm.importer

/**
 * Interface for playlist importers (Dependency Inversion).
 * Both DeezerImporter and SpotifyImporter implement this.
 */
interface IPlaylistImporter {
    /**
     * Fetch all tracks from a playlist.
     * Must handle pagination internally.
     * @param playlistId The ID extracted from the URL
     * @return List of all tracks in the playlist
     */
    suspend fun fetchTracks(playlistId: String): List<ImportedTrack>
    
    /**
     * Check if this importer can handle the given URL.
     */
    fun canHandle(url: String): Boolean
}
