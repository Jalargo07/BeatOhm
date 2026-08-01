package com.musicdownloader.data

import java.io.File
import java.nio.charset.StandardCharsets
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import com.musicdownloader.data.MusicRepository

object AudioTagWriter {
    private val SUPPORTED = setOf("mp3", "m4a", "flac", "ogg")

    fun writeTags(file: File, song: LocalSong) {
        if (file.extension.lowercase() !in SUPPORTED) return
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setEncoding(StandardCharsets.UTF_16)
            tag.setField(FieldKey.TITLE, MusicRepository.fixMojibake(song.title))
            tag.setField(FieldKey.ARTIST, MusicRepository.fixMojibake(song.artist))
            tag.setField(FieldKey.ALBUM, MusicRepository.fixMojibake(song.album))
            tag.setField(FieldKey.GENRE, song.genre)
            tag.setField(FieldKey.YEAR, song.year)
            if (song.lyrics.isNotBlank()) {
                try {
                    tag.setField(FieldKey.LYRICS, song.lyrics)
                } catch (_: Exception) {}
            }
            if (song.trackNumber > 0) tag.setField(FieldKey.TRACK, song.trackNumber.toString())
            if (song.thumbnailUrl.isNotBlank()) {
                try {
                    tag.setField(ArtworkFactory.createArtworkFromFile(File(song.thumbnailUrl)))
                } catch (_: Exception) {}
            }
            AudioFileIO.write(audioFile)
        } catch (_: Exception) {}
    }
}
