package com.beatohm.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room de candidatos de metadata ambiguos (tabla `metadata_candidates`).
 *
 * Cuando el enriquecimiento encuentra varias coincidencias plausibles
 * (MetadataResult.AmbiguousMatches), los candidatos se serializan a JSON
 * (Gson) en [candidatesJson] y quedan PENDING hasta que el usuario elige
 * cuál aplicar (APPLIED) o descarta el lote (SKIPPED).
 *
 * Invariante: por canción puede haber **como máximo un** registro PENDING
 * a la vez (MetadataCandidateRepository.saveCandidates reemplaza el
 * existente). Los registros resueltos (APPLIED/SKIPPED) se conservan como
 * historial de elecciones previas.
 *
 * La FK a `songs(id)` usa ON DELETE CASCADE (mismo patrón que
 * [PlaybackEvent]): al borrar una canción, sus candidatos se limpian solos.
 */
@Entity(
    tableName = "metadata_candidates",
    foreignKeys = [ForeignKey(
        entity = LocalSong::class,
        parentColumns = ["id"],
        childColumns = ["songId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("songId"), Index("status")]
)
data class MetadataCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val candidatesJson: String,
    val status: String = STATUS_PENDING,
    val createdAt: Long = 0,
    val appliedAt: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPLIED = "APPLIED"
        const val STATUS_SKIPPED = "SKIPPED"
    }
}
