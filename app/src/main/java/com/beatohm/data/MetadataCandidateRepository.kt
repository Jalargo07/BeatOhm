package com.beatohm.data

import android.util.Log
import com.beatohm.metadata.MetadataCandidate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de candidatos de metadata ambiguos (tabla `metadata_candidates`).
 *
 * Encapsula [MetadataCandidateDao] y la serialización Gson de
 * [MetadataCandidate] (la lista completa va en `candidatesJson`, con el
 * enum [com.beatohm.metadata.MetadataSource] serializado como string).
 *
 * Invariante: **como máximo un registro PENDING por canción**.
 * [saveCandidates] reemplaza el PENDING existente (lo borra con
 * `deletePendingBySongId` e inserta el nuevo); los registros resueltos
 * (APPLIED/SKIPPED) se conservan como historial.
 *
 * Es single-writer de la tabla: solo este repositorio inserta/borra
 * registros PENDING, por lo que la secuencia delete→insert es segura sin
 * transacción explícita (T9 lo integra en MusicRepository como único
 * caller de escritura).
 */
class MetadataCandidateRepository(private val dao: MetadataCandidateDao) {

    private val gson = Gson()

    /**
     * Persiste los candidatos ambiguos de una canción como registro PENDING.
     *
     * Si la canción ya tiene un PENDING (por un fetch anterior), se reemplaza:
     * se borra el PENDING viejo y se inserta el nuevo (invariante "máximo un
     * PENDING por canción"). Devuelve el id del registro insertado.
     */
    suspend fun saveCandidates(songId: String, candidates: List<MetadataCandidate>): Long {
        val now = System.currentTimeMillis()
        dao.deletePendingBySongId(songId)
        return dao.insert(
            MetadataCandidateEntity(
                songId = songId,
                candidatesJson = candidatesToJson(candidates),
                status = MetadataCandidateEntity.STATUS_PENDING,
                createdAt = now,
                appliedAt = null
            )
        )
    }

    /**
     * Flow reactivo de TODOS los registros PENDING (para la UI de pendientes),
     * ordenados por antigüedad (más viejos primero).
     */
    fun getPendingCandidates(): Flow<List<MetadataCandidateEntity>> = dao.getPending()

    /**
     * Flow reactivo con el conteo de registros PENDING (para badge en Settings).
     */
    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    /**
     * Aplica el candidato en [selectedIndex] de `candidatesJson` del registro
     * [candidateId] a su canción y marca el registro como APPLIED.
     *
     * Solo sobre-escribe campos del candidato que NO estén en blanco
     * (title, artist, album, year, genre y artworkUrl → thumbnailUrl).
     * Usa el patrón update-vs-insert de MusicRepository: update si la fila ya
     * existe (preserva ranking/playlists), insert solo para filas nuevas.
     *
     * @return la LocalSong actualizada, o null si el registro/candidato/canción
     * no existen (canción borrada, índice fuera de rango, JSON corrupto).
     */
    suspend fun applyCandidate(candidateId: Long, selectedIndex: Int, songDao: SongDao): LocalSong? {
        val entity = dao.getById(candidateId) ?: return null
        val candidates = jsonToCandidates(entity.candidatesJson)
        if (selectedIndex !in candidates.indices) {
            Log.w(TAG, "applyCandidate: índice $selectedIndex fuera de rango (${candidates.size} candidatos) para id $candidateId")
            return null
        }
        val candidate = candidates[selectedIndex]
        val song = songDao.getSongById(entity.songId) ?: return null

        val updated = song.copy(
            title = candidate.title.ifBlank { song.title },
            artist = candidate.artist.ifBlank { song.artist },
            album = candidate.album.ifBlank { song.album },
            year = candidate.year.ifBlank { song.year },
            genre = candidate.genre.ifBlank { song.genre },
            thumbnailUrl = candidate.artworkUrl.ifBlank { song.thumbnailUrl }
        )

        // update-vs-insert (patrón MusicRepository.saveSong): @Update por PK NO
        // borra la fila ni dispara el CASCADE de playback_events; insertSong
        // (REPLACE) solo para filas realmente nuevas.
        if (songDao.getSongById(updated.id) != null) {
            songDao.updateSong(updated)
        } else {
            songDao.insertSong(updated)
        }

        dao.markApplied(candidateId, System.currentTimeMillis())
        Log.d(TAG, "applyCandidate: metadata aplicada a '${updated.title}' (id $candidateId, índice $selectedIndex, fuente ${candidate.source})")
        return updated
    }

    /**
     * Marca el registro [candidateId] como SKIPPED (el usuario descartó el lote).
     */
    suspend fun skipCandidate(candidateId: Long) {
        dao.markSkipped(candidateId)
    }

    /**
     * Devuelve los registros PENDING de una canción (0 o 1 por el invariante).
     */
    suspend fun getPendingCandidatesBySongId(songId: String): List<MetadataCandidateEntity> =
        dao.getBySongId(songId).filter { it.status == MetadataCandidateEntity.STATUS_PENDING }

    /**
     * songIds con al menos un registro PENDING (una sola query, no N por canción).
     * Lo usan MusicRepository (reordenar la cola de enriquecimiento:
     * pendientes al final) y getSongsWithPendingCandidates (UI de pendientes).
     */
    suspend fun getPendingSongIds(): List<String> = dao.getPendingSongIds()

    /**
     * Deserializa el [candidatesJson] de un [MetadataCandidateEntity] a
     * [List]<[MetadataCandidate]> para la UI de pendientes (T10).
     *
     * Expone el parser Gson privado (el mismo que usa [applyCandidate]) para
     * no duplicar la lógica de serialización en el fragment/adapter.
     * JSON corrupto → try/catch interno → lista vacía.
     */
    fun deserializeCandidates(json: String): List<MetadataCandidate> = jsonToCandidates(json)

    /**
     * Limpieza de TODOS los registros PENDING (ej: antes de re-enriquecer
     * para descartar pendientes viejos sin dedup/duración).
     */
    suspend fun deleteAllPending() {
        dao.deleteAllPending()
    }

    /**
     * P2.4: Obtiene una entidad por su ID. Usado por MusicRepository.applyCandidateWithFinalize.
     */
    suspend fun getById(id: Long): MetadataCandidateEntity? = dao.getById(id)

    /**
     * P2.4: Marca un registro como APPLIED. Usado por MusicRepository.applyCandidateWithFinalize.
     */
    suspend fun markApplied(candidateId: Long, appliedAt: Long) {
        dao.markApplied(candidateId, appliedAt)
    }

    /**
     * Devuelve el conteo de registros PENDING de forma síncrona.
     * Lo usa MetadataRegenService al final de la regeneración.
     */
    suspend fun getPendingCountSync(): Int {
        return dao.getPendingCountSync()
    }

    /**
     * Limpieza opcional de registros resueltos (APPLIED/SKIPPED) de la tabla.
     */
    suspend fun deleteResolved() {
        dao.deleteResolved()
    }

    private fun candidatesToJson(candidates: List<MetadataCandidate>): String {
        return try {
            gson.toJson(candidates)
        } catch (e: Exception) {
            Log.e(TAG, "candidatesToJson: fallo serializando ${candidates.size} candidatos: ${e.message}")
            "[]"
        }
    }

    private fun jsonToCandidates(json: String): List<MetadataCandidate> {
        return try {
            val type = object : TypeToken<List<MetadataCandidate>>() {}.type
            gson.fromJson<List<MetadataCandidate>>(json, type)?.filterNotNull() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "jsonToCandidates: JSON corrupto, se ignora: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "MetadataCandidateRepo"
    }
}
