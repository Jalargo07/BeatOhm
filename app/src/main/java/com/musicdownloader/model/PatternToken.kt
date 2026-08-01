package com.musicdownloader.model

data class PatternToken(
    val type: Type,
    val key: String?,
    val displayName: String
) {
    enum class Type { PLACEHOLDER, SEPARATOR }

    fun toPatternString(): String = when (type) {
        Type.PLACEHOLDER -> "{$key}"
        Type.SEPARATOR -> key ?: ""
    }

    companion object {
        val available: List<PatternToken> = listOf(
            PatternToken(Type.PLACEHOLDER, "Artist", "Artista"),
            PatternToken(Type.PLACEHOLDER, "Album", "Álbum"),
            PatternToken(Type.PLACEHOLDER, "Title", "Título"),
            PatternToken(Type.PLACEHOLDER, "Track", "Track"),
            PatternToken(Type.PLACEHOLDER, "Year", "Año"),
            PatternToken(Type.PLACEHOLDER, "Genre", "Género"),
            PatternToken(Type.PLACEHOLDER, "ArtistInitial", "Inicial Artista"),
            PatternToken(Type.SEPARATOR, "/", "/"),
            PatternToken(Type.SEPARATOR, "-", "-"),
            PatternToken(Type.SEPARATOR, " ", "Espacio")
        )
    }
}
