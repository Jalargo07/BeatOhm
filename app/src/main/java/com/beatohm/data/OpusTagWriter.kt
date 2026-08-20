package com.beatohm.data

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusTags
import org.gagravarr.vorbis.VorbisStyleComments
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpusTagWriter {
    private const val TAG = "OpusTagWriter"
    private const val KEY_TRACKNUMBER = "tracknumber"
    private const val KEY_LYRICS = "lyrics"
    private const val KEY_METADATA_BLOCK_PICTURE = "metadata_block_picture"

    fun writeArtwork(file: File, song: LocalSong): Result<Boolean> {
        Log.d(TAG, "writeArtwork INICIO: ${file.name}")
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "writeArtwork: Archivo no existe o vacío: ${file.name}")
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

        val tmpFile = File(file.parentFile, "${file.name}.opus.tmp")
        var opusIn: OpusFile? = null
        var output: FileOutputStream? = null
        return try {
            val opus = OpusFile(file)
            opusIn = opus
            val tags = opus.tags

            val encoded = Base64.encodeToString(buildPictureBlock(artFile), Base64.NO_WRAP)
            setComment(tags, KEY_METADATA_BLOCK_PICTURE, encoded)

            output = FileOutputStream(tmpFile)
            val opusOut = OpusFile(output, opus.sid.takeIf { it > 0 } ?: -1, opus.info, tags)
            var audio = opus.getNextAudioPacket()
            while (audio != null) {
                opusOut.writeAudioData(audio)
                audio = opus.getNextAudioPacket()
            }
            opusOut.close()

            if (tmpFile.length() == 0L) {
                Log.e(TAG, "writeArtwork: Archivo de salida vacío")
                return Result.success(false)
            }

            val ok = replaceFile(tmpFile, file)
            Log.d(TAG, "writeArtwork OK: ${file.name} replace=$ok")
            Result.success(ok)
        } catch (e: Exception) {
            Log.e(TAG, "writeArtwork: Error en ${file.name}: ${e.message}", e)
            Result.failure(e)
        } catch (e: Throwable) {
            Log.e(TAG, "writeArtwork: Throwable en ${file.name}: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            try { opusIn?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    fun writeTags(file: File, song: LocalSong): Result<Boolean> {
        Log.e(TAG, "writeTags INICIO: ${file.name} (${file.length()} bytes)")
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Archivo no existe o vacío: ${file.name}")
            return Result.success(false)
        }
        val tmpFile = File(file.parentFile, "${file.name}.opus.tmp")
        var opusIn: OpusFile? = null
        var output: FileOutputStream? = null
        return try {
            Log.e(TAG, "Abriendo OpusFile: ${file.name}")
            val opus = OpusFile(file)
            opusIn = opus
            val tags = opus.tags
            applyTags(tags, song)
            Log.e(TAG, "Tags aplicados, escribiendo archivo temporal")

            output = FileOutputStream(tmpFile)
            val opusOut = OpusFile(output, opus.sid.takeIf { it > 0 } ?: -1, opus.info, tags)
            var audio = opus.getNextAudioPacket()
            while (audio != null) {
                opusOut.writeAudioData(audio)
                audio = opus.getNextAudioPacket()
            }
            opusOut.close()

            if (tmpFile.length() == 0L) {
                Log.e(TAG, "Archivo de salida vacío: ${tmpFile.name}")
                return Result.success(false)
            }

            val ok = replaceFile(tmpFile, file)
            Log.e(TAG, "writeTags OK: ${file.name} (tmp=${tmpFile.length()} → result=${file.length()}) replace=$ok")
            Result.success(ok)
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo tags Opus en ${file.name}: ${e.message}", e)
            Result.failure(e)
        } catch (e: Throwable) {
            Log.e(TAG, "Throwable escribiendo tags Opus en ${file.name}: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            try { opusIn?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    fun writeLyrics(file: File, song: LocalSong): Result<Boolean> {
        Log.d(TAG, "writeLyrics INICIO: ${file.name}")
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "writeLyrics: Archivo no existe o vacío: ${file.name}")
            return Result.success(false)
        }
        val tmpFile = File(file.parentFile, "${file.name}.opus.tmp")
        var opusIn: OpusFile? = null
        var output: FileOutputStream? = null
        return try {
            val opus = OpusFile(file)
            opusIn = opus
            val tags = opus.tags

            if (song.lyrics.isNotBlank()) {
                setComment(tags, KEY_LYRICS, song.lyrics)
            } else {
                tags.removeComments(KEY_LYRICS)
            }

            output = FileOutputStream(tmpFile)
            val opusOut = OpusFile(output, opus.sid.takeIf { it > 0 } ?: -1, opus.info, tags)
            var audio = opus.getNextAudioPacket()
            while (audio != null) {
                opusOut.writeAudioData(audio)
                audio = opus.getNextAudioPacket()
            }
            opusOut.close()

            if (tmpFile.length() == 0L) {
                Log.e(TAG, "writeLyrics: Archivo de salida vacío")
                return Result.success(false)
            }

            val ok = replaceFile(tmpFile, file)
            Log.d(TAG, "writeLyrics OK: ${file.name} replace=$ok")
            Result.success(ok)
        } catch (e: Exception) {
            Log.e(TAG, "writeLyrics: Error en ${file.name}: ${e.message}", e)
            Result.failure(e)
        } catch (e: Throwable) {
            Log.e(TAG, "writeLyrics: Throwable en ${file.name}: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            try { opusIn?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    private fun applyTags(tags: OpusTags, song: LocalSong) {
        val title = MusicRepository.fixMojibake(song.title)
        val artist = MusicRepository.fixMojibake(song.artist)
        val album = MusicRepository.fixMojibake(song.album)

        setComment(tags, VorbisStyleComments.KEY_TITLE, title)
        setComment(tags, VorbisStyleComments.KEY_ARTIST, artist)
        setComment(tags, VorbisStyleComments.KEY_ALBUM, album)
        setComment(tags, VorbisStyleComments.KEY_GENRE, song.genre)
        setComment(tags, VorbisStyleComments.KEY_DATE, song.year)
        if (song.trackNumber > 0) {
            setComment(tags, KEY_TRACKNUMBER, song.trackNumber.toString())
        } else {
            tags.removeComments(KEY_TRACKNUMBER)
        }
        if (song.lyrics.isNotBlank()) {
            setComment(tags, KEY_LYRICS, song.lyrics)
        } else {
            tags.removeComments(KEY_LYRICS)
        }
        if (song.thumbnailUrl.isNotBlank()) {
            try {
                val artFile = File(song.thumbnailUrl)
                if (artFile.exists() && artFile.length() > 0) {
                    val encoded = Base64.encodeToString(buildPictureBlock(artFile), Base64.NO_WRAP)
                    setComment(tags, KEY_METADATA_BLOCK_PICTURE, encoded)
                } else {
                    tags.removeComments(KEY_METADATA_BLOCK_PICTURE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error escribiendo artwork: ${e.message}")
            }
        } else {
            tags.removeComments(KEY_METADATA_BLOCK_PICTURE)
        }
    }

    private fun setComment(tags: OpusTags, key: String, value: String) {
        tags.setComments(key, listOf(value))
    }

    private fun buildPictureBlock(artFile: File): ByteArray {
        val data = artFile.readBytes()
        val mime = when (artFile.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(artFile.absolutePath, bounds)
        val width = bounds.outWidth.coerceAtLeast(0)
        val height = bounds.outHeight.coerceAtLeast(0)
        return ByteBuffer.allocate(32 + mimeBytes.size + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(3)
            .putInt(mimeBytes.size)
            .put(mimeBytes)
            .putInt(0)
            .putInt(width)
            .putInt(height)
            .putInt(0)
            .putInt(0)
            .putInt(data.size)
            .put(data)
            .array()
    }

    private fun replaceFile(tmp: File, original: File): Boolean {
        return try {
            val renamed = if (original.exists()) {
                original.delete() && tmp.renameTo(original)
            } else {
                tmp.renameTo(original)
            }
            if (!renamed) {
                tmp.copyTo(original, overwrite = true)
                tmp.delete()
            }
            original.exists() && original.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error reemplazando archivo: ${e.message}")
            false
        }
    }
}
