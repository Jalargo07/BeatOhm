package com.beatohm.importer

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int
) {
    /**
     * Search query for YouTube: "Artist - Title Audio"
     */
    val searchQuery: String get() = "$artist - $title Audio"
}
