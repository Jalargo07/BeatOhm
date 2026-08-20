package com.beatohm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de candidatos de metadata (tabla `metadata_candidates`).
 *
 * Queries no-obvias:
 * - [getBySongId] devuelve TODOS los registros de la canción (historial
 *   incluido); el filtro por status PENDING lo hace el repository.
 * - [deletePendingBySongId] solo borra el PENDING de la canción (no el
 *   historial resuelto) — lo usa saveCandidates para mantener el
 *   invariante "máximo un PENDING por canción".
 * - [deleteResolved] limpieza opcional de registros APPLIED/SKIPPED.
 */
@Dao
interface MetadataCandidateDao {

    @Insert
    suspend fun insert(entity: MetadataCandidateEntity): Long

    @Insert
    suspend fun insertAll(entities: List<MetadataCandidateEntity>)

    @Query("SELECT * FROM metadata_candidates WHERE songId = :songId ORDER BY createdAt DESC")
    suspend fun getBySongId(songId: String): List<MetadataCandidateEntity>

    @Query("SELECT * FROM metadata_candidates WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPending(): Flow<List<MetadataCandidateEntity>>

    @Query("SELECT COUNT(*) FROM metadata_candidates WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM metadata_candidates WHERE status = 'PENDING'")
    suspend fun getPendingCountSync(): Int

    @Query("SELECT DISTINCT songId FROM metadata_candidates WHERE status = 'PENDING'")
    suspend fun getPendingSongIds(): List<String>

    @Query("SELECT * FROM metadata_candidates WHERE id = :id")
    suspend fun getById(id: Long): MetadataCandidateEntity?

    @Query("UPDATE metadata_candidates SET status = 'APPLIED', appliedAt = :appliedAt WHERE id = :id")
    suspend fun markApplied(id: Long, appliedAt: Long)

    @Query("UPDATE metadata_candidates SET status = 'SKIPPED' WHERE id = :id")
    suspend fun markSkipped(id: Long)

    @Query("DELETE FROM metadata_candidates WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM metadata_candidates WHERE songId = :songId AND status = 'PENDING'")
    suspend fun deletePendingBySongId(songId: String)

    @Query("DELETE FROM metadata_candidates WHERE status = 'PENDING'")
    suspend fun deleteAllPending()

    @Query("DELETE FROM metadata_candidates WHERE status IN ('APPLIED','SKIPPED')")
    suspend fun deleteResolved()
}
