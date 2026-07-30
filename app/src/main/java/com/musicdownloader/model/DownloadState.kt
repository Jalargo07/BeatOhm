package com.musicdownloader.model

enum class DownloadStatus {
    QUEUED,
    EXTRACTING,
    FETCHING_METADATA,
    DOWNLOADING,
    TAGGING,
    COMPLETED,
    ERROR
}

data class DownloadState(
    val id: String = "",
    val url: String = "",
    val song: Song = Song(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val errorMessage: String = "",
    val filePath: String = ""
)
