package com.beatohm.importer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_track_status",
    foreignKeys = [
        ForeignKey(
            entity = ImportSession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "status"])
    ]
)
data class ImportTrackStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val status: String = "PENDING",
    val youtubeUrl: String? = null,
    val localPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
