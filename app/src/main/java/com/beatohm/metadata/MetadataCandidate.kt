package com.beatohm.metadata

/**
 * Candidato de metadata devuelto por un [MetadataProvider].
 *
 * Los providers devuelven candidatos CRUDOS: `score` arranca en `0f` y el
 * scoring/ranking (comparación de title/artist/album/year contra la canción
 * buscada) lo realiza MetadataFetcher. `source` indica qué fuente produjo el
 * candidato y queda seteado por el provider al construirlo.
 *
 * Los campos vacíos se usan cuando la fuente no provee ese dato (ej. MusicBrainz
 * no trae artwork, Last.fm track.search no trae año/género).
 */
data class MetadataCandidate(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val artworkUrl: String = "",
    val duration: Long = 0,
    val source: MetadataSource,
    val score: Float = 0f
)
