package com.beatohm.metadata

/**
 * Resultado del flujo de búsqueda de metadata sobre las 5 fuentes.
 *
 * - [ClearMatch]: hay un candidato con confianza suficiente → aplicar automáticamente.
 * - [AmbiguousMatches]: hay varios candidatos plausibles → pedir elección al usuario.
 * - [NoMatch]: ninguna fuente dio un candidato válido → dejar la canción sin cambios.
 */
sealed class MetadataResult {

    data class ClearMatch(val candidate: MetadataCandidate) : MetadataResult()

    data class AmbiguousMatches(val candidates: List<MetadataCandidate>) : MetadataResult()

    object NoMatch : MetadataResult()
}
