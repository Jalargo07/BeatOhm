package com.musicdownloader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RegenStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: RegenStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<RegenStatus>)

    @Query("DELETE FROM regen_status WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("DELETE FROM regen_status WHERE songId IN (:songIds)")
    suspend fun deleteAll(songIds: List<String>)

    @Query("SELECT * FROM regen_status WHERE status = 'failed'")
    fun getFailed(): Flow<List<RegenStatus>>

    @Query("SELECT * FROM regen_status WHERE status = 'failed'")
    suspend fun getFailedNow(): List<RegenStatus>

    @Query("SELECT * FROM regen_status WHERE status IN ('pending', 'failed')")
    fun getPendingAndFailed(): Flow<List<RegenStatus>>

    @Query("SELECT COUNT(*) FROM regen_status WHERE status = 'failed'")
    suspend fun getFailedCount(): Int

    @Query("SELECT COUNT(*) FROM regen_status WHERE status IN ('pending', 'failed')")
    suspend fun getPendingAndFailedCount(): Int

    @Query("SELECT * FROM regen_status WHERE status IN ('pending', 'failed')")
    suspend fun getPendingAndFailedNow(): List<RegenStatus>

    @Query("DELETE FROM regen_status")
    suspend fun clearAll()

    @Query("SELECT EXISTS(SELECT 1 FROM regen_status WHERE songId = :songId)")
    suspend fun exists(songId: String): Boolean
}
