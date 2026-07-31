package com.musicdownloader.data

import androidx.room.Relation

data class PlaylistWithSongs(
    val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "songId",
        associateBy = androidx.room.Junction(
            PlaylistSong::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<LocalSong>
)
