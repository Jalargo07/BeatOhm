package com.beatohm.importer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_sessions")
data class ImportSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val playlistUrl: String,
    val platform: String,
    val playlistName: String = "",
    val totalTracks: Int = 0,
    val completedTracks: Int = 0,
    val failedTracks: Int = 0,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
