package com.beatohm.importer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportSessionDao {
    @Insert
    suspend fun insert(session: ImportSession): Long

    @Update
    suspend fun update(session: ImportSession)

    @Query("SELECT * FROM import_sessions WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveSession(): ImportSession?

    @Query("SELECT * FROM import_sessions WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<ImportSession?>

    @Query("SELECT * FROM import_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ImportSession>>

    @Query("UPDATE import_sessions SET status = :status, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE import_sessions SET completedTracks = :completed, failedTracks = :failed, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateProgress(sessionId: Long, completed: Int, failed: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM import_sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: Long)
}
