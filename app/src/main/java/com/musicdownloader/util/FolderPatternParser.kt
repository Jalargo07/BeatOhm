package com.musicdownloader.util

import com.musicdownloader.model.Song
import java.io.File

object FolderPatternParser {

    private val ILLEGAL_CHARS = Regex("[/\\\\:*?\"<>|]")

    const val DEFAULT_PATTERN = "{Artist}/{Album}/{Track} - {Title}"
    const val PREFS_NAME = "settings"
    const val KEY_FOLDER_PATTERN = "folder_pattern"
    private const val MAX_SEGMENT_LENGTH = 200

    fun sanitize(input: String): String {
        return input.replace(ILLEGAL_CHARS, "_").trim().take(MAX_SEGMENT_LENGTH).ifBlank { "Unknown" }
    }

    /**
     * Resuelve un patrón de carpetas para una canción.
     * Retorna Pair(subDirectorio, nombreArchivo) donde:
     *   - subDirectorio: path relativo de carpetas (ej "Pink Floyd/The Wall")
     *   - nombreArchivo: nombre del archivo sin extensión (ej "01 - In The Flesh")
     */
    fun resolvePattern(pattern: String, song: Song): Pair<String, String> {
        val segments = pattern.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            return Pair("", sanitize(song.fileName))
        }

        val dirSegments = segments.dropLast(1)
        val filePattern = segments.last()

        val dirPath = dirSegments.joinToString(File.separator) { replacePlaceholders(it, song) }
        val fileName = replacePlaceholders(filePattern, song)

        return Pair(dirPath, fileName)
    }

    private fun replacePlaceholders(template: String, song: Song): String {
        return template
            .replace("{Artist}", sanitize(song.artist.ifBlank { "Unknown" }))
            .replace("{Album}", sanitize(song.album.ifBlank { "Unknown" }))
            .replace("{Title}", sanitize(song.title.ifBlank { "Unknown" }))
            .replace("{Track}", String.format("%02d", song.trackNumber.coerceAtLeast(0)))
            .replace("{Year}", sanitize(song.year.ifBlank { "Unknown" }))
            .replace("{Genre}", sanitize(song.genre.ifBlank { "Unknown" }))
            .replace("{ArtistInitial}", sanitize(
                song.artist.firstOrNull()?.toString()?.uppercase() ?: "U"
            ))
    }
}
