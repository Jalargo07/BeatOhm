package com.beatohm.metadata

/**
 * Fuentes de metadata consultables para enriquecer canciones.
 *
 * Orden de consulta planificado (MetadataFetcher, T4):
 * LASTFM → ITUNES → SPOTIFY → DEEZER → MUSICBRAINZ
 */
enum class MetadataSource {
    LASTFM,
    ITUNES,
    SPOTIFY,
    DEEZER,
    MUSICBRAINZ
}
