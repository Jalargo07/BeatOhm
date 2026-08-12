package com.beatohm.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object AudioTagWriter {
    private const val TAG = "AudioTagWriter"
    private val SUPPORTED = setOf("mp3", "m4a", "flac", "ogg", "opus", "webm")

    fun writeTags(file: File, song: LocalSong) {
        Log.d(TAG, "writeTags INICIO: ext=${file.extension} name=${file.name}")
        if (file.extension.lowercase() !in SUPPORTED) {
            Log.w(TAG, "Formato no soportado: ${file.extension} (${file.name})")
            return
        }
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Archivo no existe o vacío: ${file.name}")
            return
        }
        if (file.extension.lowercase() == "opus" || file.extension.lowercase() == "webm") {
            Log.d(TAG, "Delegando a OpusTagWriter: ${file.name}")
            OpusTagWriter.writeTags(file, song)
            return
        }

        val title = MusicRepository.fixMojibake(song.title)
        val artist = MusicRepository.fixMojibake(song.artist)
        val album = MusicRepository.fixMojibake(song.album)

        if (file.extension.lowercase() == "mp3") {
            writeMp3Tags(file, title, artist, album, song)
        } else {
            writeWithJaudiotagger(file, title, artist, album, song)
        }
    }

    /**
     * Write ID3v2.3 tags manually — bypasses jaudiotagger bugs with corrupted MP3s.
     */
    private fun writeMp3Tags(file: File, title: String, artist: String, album: String, song: LocalSong) {
        try {
            val bytes = file.readBytes()

            // Strip existing ID3 tags
            var audioStart = 0
            if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
                val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                    ((bytes[7].toInt() and 0x7F) shl 14) or
                    ((bytes[8].toInt() and 0x7F) shl 7) or
                    (bytes[9].toInt() and 0x7F)
                audioStart = 10 + size
            }

            // Strip ID3v1 at end
            var audioEnd = bytes.size
            if (audioEnd - audioStart > 128) {
                val tagStart = audioEnd - 128
                if (bytes[tagStart] == 'T'.code.toByte() &&
                    bytes[tagStart + 1] == 'A'.code.toByte() &&
                    bytes[tagStart + 2] == 'G'.code.toByte()) {
                    audioEnd = tagStart
                }
            }

            val audioData = bytes.copyOfRange(audioStart, audioEnd)

            // Build ID3v2.3 tag
            val frames = mutableListOf<ByteArray>()
            frames.add(createTextFrame("TIT2", title))
            frames.add(createTextFrame("TPE1", artist))
            frames.add(createTextFrame("TALB", album))
            if (song.genre.isNotBlank()) frames.add(createTextFrame("TCON", song.genre))
            if (song.year.isNotBlank()) frames.add(createTextFrame("TDRC", song.year))
            if (song.trackNumber > 0) frames.add(createTextFrame("TRCK", song.trackNumber.toString()))
            if (song.lyrics.isNotBlank()) {
                frames.add(createUnsynchronizedLyrics("USLT", song.lyrics))
            }

            val totalFrameSize = frames.sumOf { it.size }
            val tagSize = encodeSyncSafe(totalFrameSize + 10) // +10 for extended header

            // ID3v2.3 header
            val header = ByteArray(10)
            header[0] = 'I'.code.toByte()
            header[1] = 'D'.code.toByte()
            header[2] = '3'.code.toByte()
            header[3] = 3 // version 2.3.0
            header[4] = 0
            header[5] = 0 // flags
            header[6] = tagSize[0]
            header[7] = tagSize[1]
            header[8] = tagSize[2]
            header[9] = tagSize[3]

            // Write: header + frames + audio
            FileOutputStream(file).use { out ->
                out.write(header)
                for (frame in frames) {
                    out.write(frame)
                }
                out.write(audioData)
            }

            Log.d(TAG, "Tags escritos OK (manual ID3v2.3): ${file.name} '$title' - '$artist'")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing MP3 tags: ${e.message}")
        }
    }

    private fun writeWithJaudiotagger(file: File, title: String, artist: String, album: String, song: LocalSong) {
        try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.createDefaultTag()
            audioFile.tag = tag
            tag.setEncoding(StandardCharsets.UTF_16)
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
            tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, song.genre)
            tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, song.year)
            if (song.trackNumber > 0) tag.setField(org.jaudiotagger.tag.FieldKey.TRACK, song.trackNumber.toString())
            org.jaudiotagger.audio.AudioFileIO.write(audioFile)
            Log.d(TAG, "Tags escritos OK (jaudiotagger): ${file.name} '$title' - '$artist'")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing tags (jaudiotagger): ${e.message}")
        }
    }

    /**
     * Create an ID3v2.3 text frame.
     * Frame format: [4-byte ID] [4-byte size] [2-byte flags] [1-byte encoding] [text]
     */
    private fun createTextFrame(id: String, text: String): ByteArray {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val frameData = ByteArray(1 + textBytes.size)
        frameData[0] = 0 // encoding: ISO-8859-1 (safe for most text)
        System.arraycopy(textBytes, 0, frameData, 1, textBytes.size)

        val header = ByteArray(10)
        header[0] = id[0].code.toByte()
        header[1] = id[1].code.toByte()
        header[2] = id[2].code.toByte()
        header[3] = id[3].code.toByte()
        val size = encodeSyncSafe(frameData.size)
        header[4] = size[0]
        header[5] = size[1]
        header[6] = size[2]
        header[7] = size[3]
        header[8] = 0 // flags high
        header[9] = 0 // flags low

        return header + frameData
    }

    /**
     * Create an ID3v2.3 unsynchronized lyrics frame (USLT).
     */
    private fun createUnsynchronizedLyrics(id: String, lyrics: String): ByteArray {
        val descBytes = ByteArray(0) // empty description
        val lyricsBytes = lyrics.toByteArray(StandardCharsets.UTF_8)
        // encoding(1) + lang(3) + desc(1+0) + lyrics
        val frameData = ByteArray(1 + 3 + 1 + descBytes.size + lyricsBytes.size)
        frameData[0] = 0 // ISO-8859-1
        frameData[1] = 'e'.code.toByte() // lang
        frameData[2] = 'n'.code.toByte()
        frameData[3] = 'g'.code.toByte()
        frameData[4] = 0 // null terminator for description
        System.arraycopy(lyricsBytes, 0, frameData, 5, lyricsBytes.size)

        val header = ByteArray(10)
        header[0] = 'U'.code.toByte()
        header[1] = 'S'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'T'.code.toByte()
        val size = encodeSyncSafe(frameData.size)
        header[4] = size[0]
        header[5] = size[1]
        header[6] = size[2]
        header[7] = size[3]
        header[8] = 0
        header[9] = 0

        return header + frameData
    }

    /**
     * Encode an integer as a syncsafe 4-byte value (each byte uses only 7 bits).
     */
    private fun encodeSyncSafe(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
        )
    }
}
