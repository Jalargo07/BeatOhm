package com.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "regen_status")
data class RegenStatus(
    @PrimaryKey val songId: String,
    val status: String  // "pending", "success", "failed"
)
