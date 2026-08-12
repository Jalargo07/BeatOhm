package com.beatohm.data

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory

object AudioTagWriter {
    private const val TAG = "AudioTagWriter"
    private val SUPPORTED = setOf("mp3", "m4a", "flac", "ogg", "opus", "webm")

    fun writeTags(file: File, song: LocalSong) {
        Log.e(TAG, "writeTags INICIO: ext=${file.extension} name=${file.name}")
        if (file.extension.lowercase() !in SUPPORTED) {
            Log.e(TAG, "Formato no soportado: ${file.extension} (${file.name})")
            return
        }
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Archivo no existe o vacío: ${file.name}")
            return
        }
        if (file.extension.lowercase() == "opus" || file.extension.lowercase() == "webm") {
            Log.e(TAG, "Delegando a OpusTagWriter: ${file.name}")
            OpusTagWriter.writeTags(file, song)
            return
        }
        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setEncoding(StandardCharsets.UTF_16)

            val title = MusicRepository.fixMojibake(song.title)
            val artist = MusicRepository.fixMojibake(song.artist)
            val album = MusicRepository.fixMojibake(song.album)

            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM, album)
            tag.setField(FieldKey.GENRE, song.genre)
            tag.setField(FieldKey.YEAR, song.year)
            if (song.trackNumber > 0) tag.setField(FieldKey.TRACK, song.trackNumber.toString())

            if (song.lyrics.isNotBlank()) {
                try {
                    tag.setField(FieldKey.LYRICS, song.lyrics)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing lyrics: ${e.message}")
                }
            }

            if (song.thumbnailUrl.isNotBlank()) {
                try {
                    val artFile = File(song.thumbnailUrl)
                    if (artFile.exists()) {
                        tag.setField(ArtworkFactory.createArtworkFromFile(artFile))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing artwork: ${e.message}")
                }
            }

            AudioFileIO.write(audioFile)
            Log.e(TAG, "Tags escritos OK: ${file.name} [${file.extension.uppercase()}] '$title' - '$artist'")
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo tags en ${file.name}: ${e.message}")
        }
    }
}
