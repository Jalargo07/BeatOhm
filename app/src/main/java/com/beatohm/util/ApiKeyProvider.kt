package com.beatohm.util

import android.util.Log
import com.beatohm.BuildConfig

/**
 * Wrapper centralizado para acceder a API keys (Last.fm, Genius, futuras).
 *
 * Patrón: cada key se lee desde BuildConfig (generado desde secrets.properties).
 * Si la key está vacía o no configurada, se retorna null y se loguea un skip
 * SANITIZADO (sin exponer el valor del token).
 *
 * Diseño preparado para futura migración a backend proxy: solo cambiar la fuente
 * de datos aquí, sin tocar providers individuales.
 */
object ApiKeyProvider {

    private const val TAG = "ApiKeyProvider"

    /**
     * Devuelve la API key de Last.fm o null si no está configurada.
     * Log sanitizado: solo indica presencia, nunca expone el valor.
     */
    fun lastFmKey(): String? {
        val key = BuildConfig.LASTFM_API_KEY
        return if (key.isNotBlank()) {
            key
        } else {
            Log.d(TAG, "LASTFM_API_KEY not configured, skipping")
            null
        }
    }

    /**
     * Devuelve el access token de Genius o null si no está configurado.
     * Log sanitizado: solo indica presencia, nunca expone el valor.
     */
    fun geniusToken(): String? {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        return if (token.isNotBlank()) {
            token
        } else {
            Log.d(TAG, "GENIUS_ACCESS_TOKEN not configured, skipping")
            null
        }
    }

    /**
     * Devuelve el client ID de Spotify o null si no está configurado.
     * Spotify está deshabilitado (requiere Premium). Preparado para reactivación.
     */
    fun spotifyClientId(): String? {
        Log.d(TAG, "Spotify disabled (requires Premium)")
        return null
    }

    /**
     * Devuelve el client secret de Spotify o null si no está configurado.
     * Spotify está deshabilitado (requiere Premium). Preparado para reactivación.
     */
    fun spotifyClientSecret(): String? {
        Log.d(TAG, "Spotify disabled (requires Premium)")
        return null
    }
}
