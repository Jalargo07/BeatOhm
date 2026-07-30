package com.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musicdownloader.model.Song

@Entity(tableName = "songs")
data class LocalSong(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: Int = 0,
    val duration: Long = 0,
    val filePath: String = "",
    val thumbnailUrl: String = "",
    val lyrics: String = ""
)

fun LocalSong.toSong(): Song = Song(
    title = title,
    artist = artist,
    album = album,
    genre = genre,
    year = year,
    trackNumber = trackNumber,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    youtubeUrl = filePath,
    youtubeId = "",
    lyrics = lyrics
)
