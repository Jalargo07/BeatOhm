package com.beatohm.util

import android.util.Log
import com.beatohm.BuildConfig

/**
 * Logger centralizado de la app con soporte de niveles y redaccion de datos sensibles.
 *
 * En release, solo se emiten logs WARN y ERROR. Los logs DEBUG e INFO se descartan
 * para evitar exposicion de informacion sensible en produccion.
 *
 * Datos sensibles (URLs, tokens, queries, rutas de archivos, cuerpos de respuesta)
 * se redactan automaticamente o se sanitizan con longitudes/conteos.
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val minLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.WARN

    fun d(tag: String, message: String) {
        if (minLevel.ordinal <= Level.DEBUG.ordinal) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (minLevel.ordinal <= Level.INFO.ordinal) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (minLevel.ordinal <= Level.WARN.ordinal) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (minLevel.ordinal <= Level.ERROR.ordinal) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    fun redactToken(token: String?): String {
        if (token.isNullOrBlank()) return "[EMPTY]"
        if (token.length <= 6) return "***"
        return "${token.take(3)}***${token.takeLast(3)}"
    }

    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return "[EMPTY]"
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return "[invalid-url]"
            val scheme = uri.scheme ?: "https"
            "$scheme://$host/***"
        } catch (_: Exception) {
            "[url:${url.length}ch]"
        }
    }

    fun sanitizePath(path: String?): String {
        if (path.isNullOrBlank()) return "[EMPTY]"
        val name = path.substringAfterLast('/')
        return if (name.length > 40) "${name.take(37)}..." else name
    }

    fun bodyLength(body: String?): String {
        if (body.isNullOrBlank()) return "0"
        return "${body.length}"
    }
}
