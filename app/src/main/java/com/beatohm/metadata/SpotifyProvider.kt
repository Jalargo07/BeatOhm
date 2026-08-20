package com.beatohm.metadata

import android.util.Log

/**
 * Provider de metadata de la Spotify Web API.
 *
 * **ESTADO: DESHABILITADO** — requiere Spotify Premium del owner para funcionar.
 * Acordado mantener deshabilitado hasta que se decida reactivar.
 *
 * Para reactivar en el futuro:
 * 1. Restaurar el código de búsqueda (ver git history de este archivo)
 * 2. Asegurar que SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET existan en secrets.properties
 * 3. Los BuildConfig fields se eliminan de build.gradle.kts; restaurarlos si se reactiva
 *
 * Contrato: `search()` retorna lista vacía. El provider participa en la cadena de
 * MetadataFetcher pero no produce candidatos.
 */
object SpotifyProvider : MetadataProvider {

    override val source = MetadataSource.SPOTIFY

    override suspend fun search(
        artist: String,
        title: String,
        extraTags: ExtraTags
    ): List<MetadataCandidate> {
        Log.d(TAG, "search SKIP: Spotify disabled (requires Premium)")
        return emptyList()
    }

    private const val TAG = "SpotifyProvider"
}
