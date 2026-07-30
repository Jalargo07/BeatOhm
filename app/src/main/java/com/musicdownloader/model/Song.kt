package com.musicdownloader.model

data class Song(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: Int = 0,
    val duration: Long = 0,
    val thumbnailUrl: String = "",
    val youtubeUrl: String = "",
    val youtubeId: String = ""
) {
    val fileName: String
        get() = "${artist} - ${title}".replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
