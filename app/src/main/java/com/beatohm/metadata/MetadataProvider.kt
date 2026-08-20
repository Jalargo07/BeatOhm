package com.beatohm.metadata

/**
 * Contrato de una fuente de metadata (Last.fm, iTunes, Spotify, Deezer, MusicBrainz).
 *
 * `search()` consulta la API de la fuente con artista/título (y tags extra opcionales)
 * y devuelve candidatos CRUDOS: campos tal como vienen de la API (con limpieza básica
 * de títulos/artistas cuando corresponde) y `score = 0f`. El scoring y la decisión
 * ClearMatch/Ambiguous/NoMatch los realiza MetadataFetcher.
 *
 * Los errores de red/parseo NO se propagan: cada provider los captura y devuelve
 * lista vacía (la búsqueda nunca debe romper el flujo de enriquecimiento).
 */
interface MetadataProvider {

    /** Fuente que produce este provider. */
    val source: MetadataSource

    /**
     * Busca candidatos de metadata para [artist]/[title].
     *
     * @param artist artista esperado (puede estar vacío si solo se conoce el título).
     * @param title  título esperado.
     * @param extraTags album/género ya conocidos (opcionales, útiles si artist está vacío).
     * @return hasta N candidatos con `source` seteado y `score = 0f`; lista vacía si
     *         no hay resultados, la key no está configurada o hubo un error.
     */
    suspend fun search(
        artist: String,
        title: String,
        extraTags: ExtraTags
    ): List<MetadataCandidate>
}
