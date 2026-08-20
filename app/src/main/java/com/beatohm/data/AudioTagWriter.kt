package com.beatohm.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

object AudioTagWriter {
    private const val TAG = "AudioTagWriter"
    private val SUPPORTED = setOf("mp3", "m4a", "flac", "ogg", "opus", "webm")

    fun writeArtwork(file: File, song: LocalSong): Result<Boolean> {
        Log.d(TAG, "writeArtwork INICIO: ext=${file.extension} name=${file.name}")
        if (file.extension.lowercase() !in SUPPORTED) {
            Log.w(TAG, "writeArtwork: Formato no soportado: ${file.extension}")
            return Result.success(false)
        }
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "writeArtwork: Archivo no existe o vacío: ${file.name}")
            return Result.success(false)
        }

        val thumbUrl = song.thumbnailUrl
        val artFile = if (thumbUrl.isNotBlank() && !thumbUrl.startsWith("http")) {
            File(thumbUrl)
        } else null

        if (artFile == null || !artFile.exists() || artFile.length() == 0L) {
            Log.w(TAG, "writeArtwork: Sin artwork válida: thumbnailUrl='$thumbUrl'")
            return Result.success(false)
        }

        val artworkBytes = try { artFile.readBytes() } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            when (file.extension.lowercase()) {
                "opus", "webm" -> OpusTagWriter.writeArtwork(file, song)
                "mp3" -> Result.success(writeMp3Artwork(file, song, artworkBytes))
                else -> Result.success(writeJaudiotaggerArtwork(file, song, artworkBytes))
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeArtwork: Error en ${file.name}: ${e.message}")
            Result.failure(e)
        }
    }

    fun writeLyrics(file: File, song: LocalSong): Result<Boolean> {
        Log.d(TAG, "writeLyrics INICIO: ext=${file.extension} name=${file.name}")
        if (file.extension.lowercase() !in SUPPORTED) {
            Log.w(TAG, "writeLyrics: Formato no soportado: ${file.extension}")
            return Result.success(false)
        }
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "writeLyrics: Archivo no existe o vacío: ${file.name}")
            return Result.success(false)
        }
        if (song.lyrics.isBlank()) {
            Log.w(TAG, "writeLyrics: Sin lyrics para escribir: ${file.name}")
            return Result.success(false)
        }

        return try {
            when (file.extension.lowercase()) {
                "opus", "webm" -> OpusTagWriter.writeLyrics(file, song)
                "mp3" -> Result.success(writeMp3Lyrics(file, song))
                else -> Result.success(writeJaudiotaggerLyrics(file, song))
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeLyrics: Error en ${file.name}: ${e.message}")
            Result.failure(e)
        }
    }

    fun writeTags(file: File, song: LocalSong): Result<Boolean> {
        Log.d(TAG, "writeTags INICIO: ext=${file.extension} name=${file.name}")
        if (file.extension.lowercase() !in SUPPORTED) {
            Log.w(TAG, "Formato no soportado: ${file.extension} (${file.name})")
            return Result.success(false)
        }
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Archivo no existe o vacío: ${file.name}")
            return Result.success(false)
        }
        if (file.extension.lowercase() == "opus" || file.extension.lowercase() == "webm") {
            Log.d(TAG, "Delegando a OpusTagWriter: ${file.name}")
            return OpusTagWriter.writeTags(file, song)
        }

        val title = MusicRepository.fixMojibake(song.title)
        val artist = MusicRepository.fixMojibake(song.artist)
        val album = MusicRepository.fixMojibake(song.album)

        return try {
            when (file.extension.lowercase()) {
                "mp3" -> writeMp3Tags(file, title, artist, album, song)
                else -> writeWithJaudiotagger(file, title, artist, album, song)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeTags: Error en ${file.name}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Write ID3v2.3 tags manually — preserves existing frames (APIC, USLT, unknown)
     * and only replaces text frames (TIT2, TPE1, TALB, TCON, TDRC, TRCK).
     * Uses atomic temp file write to avoid partial corruption.
     */
    private fun writeMp3Tags(
        file: File, title: String, artist: String, album: String, song: LocalSong
    ): Result<Boolean> {
        val bytes = file.readBytes()
        val (audioStart, existingFrames) = parseId3v2Frames(bytes)
        val audioData = bytes.copyOfRange(audioStart, stripId3v1(bytes, audioStart))

        val replacementIds = setOf("TIT2", "TPE1", "TALB", "TCON", "TDRC", "TRCK")
        val newFrames = mutableListOf<ByteArray>()
        newFrames.add(createTextFrame("TIT2", title))
        newFrames.add(createTextFrame("TPE1", artist))
        newFrames.add(createTextFrame("TALB", album))
        if (song.genre.isNotBlank()) newFrames.add(createTextFrame("TCON", song.genre))
        if (song.year.isNotBlank()) newFrames.add(createTextFrame("TDRC", song.year))
        if (song.trackNumber > 0) newFrames.add(createTextFrame("TRCK", song.trackNumber.toString()))

        val preserved = existingFrames.filter { (id, _) -> id !in replacementIds }
        val allFrames = preserved.map { it.second } + newFrames

        return try {
            val ok = atomicWriteId3v2(file, allFrames, audioData)
            Log.d(TAG, "Tags escritos OK (manual ID3v2.3, preserved ${preserved.size} frames): ${file.name}")
            Result.success(ok)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing MP3 tags: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * P2.2: Uses existing tag instead of createDefaultTag() which destroys fields.
     */
    private fun writeWithJaudiotagger(
        file: File, title: String, artist: String, album: String, song: LocalSong
    ): Result<Boolean> {
        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
        // P2.2: Use existing tag, only create if null
        val tag = audioFile.tag ?: audioFile.createDefaultTag()
        audioFile.tag = tag
        tag.setEncoding(StandardCharsets.UTF_16)
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
        tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, song.genre)
        tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, song.year)
        if (song.trackNumber > 0) tag.setField(org.jaudiotagger.tag.FieldKey.TRACK, song.trackNumber.toString())
        if (song.lyrics.isNotBlank()) tag.setField(org.jaudiotagger.tag.FieldKey.LYRICS, song.lyrics)
        org.jaudiotagger.audio.AudioFileIO.write(audioFile)
        Log.d(TAG, "Tags escritos OK (jaudiotagger, tag preserved): ${file.name}")
        return Result.success(true)
    }

    private fun writeMp3Artwork(file: File, song: LocalSong, artworkBytes: ByteArray): Boolean {
        try {
            val bytes = file.readBytes()
            val (audioStart, existingFrames) = parseId3v2Frames(bytes)
            val audioData = bytes.copyOfRange(audioStart, stripId3v1(bytes, audioStart))

            val preserved = existingFrames.filter { (id, _) -> id != "APIC" }.map { it.second }
            val frames = preserved.toMutableList()

            val mimeType = "image/jpeg"
            val mimeBytes = mimeType.toByteArray(StandardCharsets.US_ASCII)
            val apicData = ByteArray(1 + 1 + mimeBytes.size + 1 + 1 + artworkBytes.size)
            var offset = 0
            apicData[offset++] = 0 // encoding: ISO-8859-1
            System.arraycopy(mimeBytes, 0, apicData, offset, mimeBytes.size)
            offset += mimeBytes.size
            apicData[offset++] = 0 // null terminator for MIME
            apicData[offset++] = 3 // picture type: front cover
            apicData[offset++] = 0 // description null terminator
            System.arraycopy(artworkBytes, 0, apicData, offset, artworkBytes.size)

            frames.add(createApicFrame(apicData))

            val ok = atomicWriteId3v2(file, frames, audioData)
            Log.d(TAG, "Artwork escrito OK (manual ID3v2.3, preserved ${preserved.size} frames): ${file.name}")
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Error writing MP3 artwork: ${e.message}")
            return false
        }
    }

    private fun writeMp3Lyrics(file: File, song: LocalSong): Boolean {
        try {
            val bytes = file.readBytes()
            val (audioStart, existingFrames) = parseId3v2Frames(bytes)
            val audioData = bytes.copyOfRange(audioStart, stripId3v1(bytes, audioStart))

            val preserved = existingFrames.filter { (id, _) -> id != "USLT" }.map { it.second }
            val frames = preserved.toMutableList()
            frames.add(createUnsynchronizedLyrics(song.lyrics))

            val ok = atomicWriteId3v2(file, frames, audioData)
            Log.d(TAG, "Lyrics escritos OK (manual ID3v2.3, preserved ${preserved.size} frames): ${file.name}")
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Error writing MP3 lyrics: ${e.message}")
            return false
        }
    }

    private fun writeJaudiotaggerLyrics(file: File, song: LocalSong): Boolean {
        try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            audioFile.tag = tag
            tag.setEncoding(StandardCharsets.UTF_16)
            tag.setField(org.jaudiotagger.tag.FieldKey.LYRICS, song.lyrics)
            org.jaudiotagger.audio.AudioFileIO.write(audioFile)
            Log.d(TAG, "Lyrics escritos OK (jaudiotagger): ${file.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing lyrics (jaudiotagger): ${e.message}")
            return false
        }
    }

    private fun createApicFrame(data: ByteArray): ByteArray {
        val header = ByteArray(10)
        header[0] = 'A'.code.toByte()
        header[1] = 'P'.code.toByte()
        header[2] = 'I'.code.toByte()
        header[3] = 'C'.code.toByte()
        val size = encodeSyncSafe(data.size)
        header[4] = size[0]
        header[5] = size[1]
        header[6] = size[2]
        header[7] = size[3]
        header[8] = 0
        header[9] = 0

        return header + data
    }

    private fun writeJaudiotaggerArtwork(file: File, song: LocalSong, artworkBytes: ByteArray): Boolean {
        try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()
            audioFile.tag = tag
            tag.setEncoding(StandardCharsets.UTF_16)

            val artwork = org.jaudiotagger.tag.images.StandardArtwork()
            artwork.setBinaryData(artworkBytes)
            artwork.setMimeType("image/jpeg")
            artwork.setPictureType(3) // front cover
            artwork.setDescription("")
            tag.setField(artwork)

            org.jaudiotagger.audio.AudioFileIO.write(audioFile)
            Log.d(TAG, "Artwork escrito OK (jaudiotagger): ${file.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing artwork (jaudiotagger): ${e.message}")
            return false
        }
    }

    // ── Frame parsing helpers (shared by artwork, lyrics, and writeTags) ──

    /**
     * Parses ID3v2 frames from a byte array. Returns the audio start offset
     * and a list of (frameId, frameBytes) pairs for all frames in the tag.
     */
    private fun parseId3v2Frames(bytes: ByteArray): Pair<Int, List<Pair<String, ByteArray>>> {
        val frames = mutableListOf<Pair<String, ByteArray>>()
        var audioStart = 0

        if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val size = ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)
            audioStart = 10 + size

            // Parse individual frames
            var pos = 10
            while (pos < audioStart) {
                if (pos + 10 > audioStart) break
                val frameId = String(bytes, pos, 4, Charsets.US_ASCII)
                val frameSize = ((bytes[pos + 4].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 5].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 6].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 7].toInt() and 0xFF)
                if (frameSize <= 0 || pos + 10 + frameSize > audioStart) break
                val frameBytes = bytes.copyOfRange(pos, pos + 10 + frameSize)
                frames.add(frameId to frameBytes)
                pos += 10 + frameSize
            }
        }

        return audioStart to frames
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
    private fun createUnsynchronizedLyrics(lyrics: String): ByteArray {
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

    // ── Shared ID3v2 helpers ──

    /**
     * Strip ID3v1 tag (128 bytes at end) if present. Returns the audio end offset.
     */
    private fun stripId3v1(bytes: ByteArray, audioStart: Int): Int {
        var audioEnd = bytes.size
        if (audioEnd - audioStart > 128) {
            val tagStart = audioEnd - 128
            if (bytes[tagStart] == 'T'.code.toByte() &&
                bytes[tagStart + 1] == 'A'.code.toByte() &&
                bytes[tagStart + 2] == 'G'.code.toByte()) {
                audioEnd = tagStart
            }
        }
        return audioEnd
    }

    /**
     * Build ID3v2.3 header for a given total frame payload size.
     */
    private fun buildId3v2Header(totalFrameSize: Int): ByteArray {
        val tagSize = encodeSyncSafe(totalFrameSize)
        return byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, // version 2.3.0
            tagSize[0], tagSize[1], tagSize[2], tagSize[3]
        )
    }

    /**
     * Atomic write of an ID3v2.3 tag: header + frames + audio data → temp file → rename.
     */
    private fun atomicWriteId3v2(file: File, frames: List<ByteArray>, audioData: ByteArray): Boolean {
        val totalFrameSize = frames.sumOf { it.size }
        val header = buildId3v2Header(totalFrameSize)
        val tmpFile = File(file.parentFile, "${file.name}.id3.tmp")
        try {
            FileOutputStream(tmpFile).use { out ->
                out.write(header)
                for (frame in frames) {
                    out.write(frame)
                }
                out.write(audioData)
            }
            return atomicReplace(tmpFile, file)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
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

    /**
     * Atomic replace: write to tmp, then delete original and rename tmp to original.
     * Fallback to copyTo if rename fails (cross-filesystem on Android).
     */
    private fun atomicReplace(tmp: File, original: File): Boolean {
        return try {
            if (original.exists()) {
                original.delete()
            }
            val renamed = tmp.renameTo(original)
            if (!renamed) {
                tmp.copyTo(original, overwrite = true)
                tmp.delete()
            }
            original.exists() && original.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "atomicReplace error: ${e.message}")
            false
        }
    }
}
