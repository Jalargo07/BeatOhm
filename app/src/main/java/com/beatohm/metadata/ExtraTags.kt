package com.beatohm.metadata

/**
 * Tags extra ya conocidos de la canción (album/género) que pueden ayudar a los
 * providers a filtrar/validar candidatos, principalmente en búsquedas SIN artista
 * (cuando el artista es basura del canal de YouTube, por ejemplo).
 */
data class ExtraTags(
    val album: String = "",
    val genre: String = ""
)
