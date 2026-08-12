package com.beatohm.importer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTrackStatusDao {
    @Insert
    suspend fun insertAll(tracks: List<ImportTrackStatus>)

    @Insert
    suspend fun insert(track: ImportTrackStatus): Long

    @Update
    suspend fun update(track: ImportTrackStatus)

    @Query("UPDATE import_track_status SET status = :status, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun updateStatus(trackId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE import_track_status SET status = 'DOWNLOADING', updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun markDownloading(trackId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE import_track_status SET status = 'COMPLETED', localPath = :path, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun markCompleted(trackId: Long, path: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE import_track_status SET status = 'FAILED', errorMessage = :error, updatedAt = :updatedAt WHERE id = :trackId")
    suspend fun markFailed(trackId: Long, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM import_track_status WHERE sessionId = :sessionId AND status = 'PENDING' ORDER BY id ASC LIMIT :limit")
    suspend fun getPendingTracks(sessionId: Long, limit: Int = 20): List<ImportTrackStatus>

    @Query("SELECT * FROM import_track_status WHERE sessionId = :sessionId AND status = 'DOWNLOADING'")
    suspend fun getDownloadingTracks(sessionId: Long): List<ImportTrackStatus>

    @Query("SELECT COUNT(*) FROM import_track_status WHERE sessionId = :sessionId AND status = 'PENDING'")
    suspend fun getPendingCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM import_track_status WHERE sessionId = :sessionId AND status = 'COMPLETED'")
    suspend fun getCompletedCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM import_track_status WHERE sessionId = :sessionId AND status = 'FAILED'")
    suspend fun getFailedCount(sessionId: Long): Int

    @Query("SELECT * FROM import_track_status WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getAllTracks(sessionId: Long): Flow<List<ImportTrackStatus>>

    @Query("DELETE FROM import_track_status WHERE sessionId = :sessionId")
    suspend fun deleteAll(sessionId: Long)
}
