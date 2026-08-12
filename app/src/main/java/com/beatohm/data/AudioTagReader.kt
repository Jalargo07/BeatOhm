package com.beatohm.data

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.gagravarr.opus.OpusFile
import java.io.File

object AudioTagReader {
    private const val TAG = "AudioTagReader"

    fun readLyrics(filePath: String): String {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return ""

        return try {
            when (file.extension.lowercase()) {
                "opus" -> readOpusLyrics(file)
                "mp3", "m4a", "flac", "ogg" -> readJAudioTaggerLyrics(file)
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading lyrics from $filePath: ${e.message}")
            ""
        }
    }

    private fun readJAudioTaggerLyrics(file: File): String {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return ""
            tag.getFirst(FieldKey.LYRICS).orEmpty().trim()
        } catch (e: Exception) {
            Log.e(TAG, "JAudioTagger read error: ${e.message}")
            ""
        }
    }

    private fun readOpusLyrics(file: File): String {
        return try {
            val opus = OpusFile(file)
            val tags = opus.tags ?: return ""
            val comments = tags.allComments
            val lyrics = comments?.get("lyrics")?.firstOrNull().orEmpty()
            opus.close()
            lyrics.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Opus read error: ${e.message}")
            ""
        }
    }
}
