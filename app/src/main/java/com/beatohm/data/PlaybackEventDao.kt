package com.beatohm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackEventDao {
    @Insert
    suspend fun insert(event: PlaybackEvent)

    @Query("""
        SELECT s.*, COALESCE(SUM(pe.score), 0) as totalScore
        FROM songs s
        INNER JOIN playback_events pe ON s.id = pe.songId
        WHERE pe.timestamp >= :sinceTimestamp
        GROUP BY s.id
        HAVING totalScore > 0
        ORDER BY totalScore DESC, s.isFavorite DESC, s.title COLLATE NOCASE ASC
        LIMIT :limit
    """)
    fun getTopSongsByScore(sinceTimestamp: Long, limit: Int = 100): Flow<List<LocalSong>>

    @Query("DELETE FROM playback_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
