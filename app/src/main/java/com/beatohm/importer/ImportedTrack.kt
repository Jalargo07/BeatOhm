package com.beatohm.importer

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int
) {
    /**
     * Search query for YouTube: "Artist - Title Audio" or just "Title Audio" if no artist
     */
    val searchQuery: String get() = if (artist.isNotBlank()) "$artist - $title Audio" else "$title Audio"
}
