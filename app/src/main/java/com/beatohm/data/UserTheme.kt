package com.beatohm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_themes")
data class UserTheme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val accentColor: Int,
    val backgroundColor: Int,
    val surfaceColor: Int,
    val textColor: Int,
    val iconPackId: String = "default",
    val playerLayoutId: String = "classic",
    val fontStyle: String = "default",
    val isPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)