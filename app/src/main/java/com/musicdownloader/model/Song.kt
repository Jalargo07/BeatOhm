package com.musicdownloader.model

data class Song(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: Int = 0,
    val duration: Long = 0,
    val thumbnailUrl: String = "",
    val filePath: String = "",
    val youtubeUrl: String = "",
    val youtubeId: String = "",
    val lyrics: String = "",
    val dominantColor: Int = 0
) {
    val fileName: String
        get() = "${fixMojibake(artist)} - ${fixMojibake(title)}".replace(Regex("[/\\\\:*?\"<>|]"), "_")

    companion object {
        fun fixMojibake(text: String): String {
            if (!text.contains("Â") && !text.contains("Ã")) return text
            try {
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded != text && !decoded.contains("\uFFFD")) return decoded
            } catch (_: Exception) {}
            return text
        }
    }
}
