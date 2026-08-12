package com.beatohm.model

data class SearchResult(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationText: String,
    val durationSeconds: Long,
    val thumbnailUrl: String
) {
    val youtubeUrl: String
        get() = "https://www.youtube.com/watch?v=$videoId"
}
