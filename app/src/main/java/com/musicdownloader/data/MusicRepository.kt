package com.musicdownloader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream

class MusicRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).songDao()

    fun getAllSongs(): Flow<List<LocalSong>> = dao.getAllSongs()
    fun getAllAlbums(): Flow<List<String>> = dao.getAllAlbums()
    fun getAllArtists(): Flow<List<String>> = dao.getAllArtists()
    fun getAllGenres(): Flow<List<String>> = dao.getAllGenres()
    fun getAllYears(): Flow<List<String>> = dao.getAllYears()
    fun getSongsByAlbum(album: String): Flow<List<LocalSong>> = dao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String): Flow<List<LocalSong>> = dao.getSongsByArtist(artist)
    fun getSongsByGenre(genre: String): Flow<List<LocalSong>> = dao.getSongsByGenre(genre)
    fun getSongCount(): Flow<Int> = dao.getSongCount()

    suspend fun insertSong(song: LocalSong) = dao.insertSong(song)
    suspend fun deleteSong(song: LocalSong) = dao.deleteSong(song)

    suspend fun scanMusicFolder(): List<LocalSong> {
        val dir = getMusicDir()
        if (!dir.exists()) return emptyList()

        val files = dir.listFiles { f -> f.extension in listOf("mp3", "m4a", "flac", "ogg", "opus", "wav") }
            ?: return emptyList()

        val artCacheDir = File(dir, ".albumart")
        if (!artCacheDir.exists()) artCacheDir.mkdirs()

        val songs = mutableListOf<LocalSong>()
        for (file in files) {
            try {
                val meta = MediaMetadataRetriever()
                meta.setDataSource(file.absolutePath)
                val title = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                val artist = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                val album = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                val genre = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
                val year = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
                val track = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0
                val duration = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                var thumbnailUrl = ""
                try {
                    val art = meta.embeddedPicture
                    if (art != null) {
                        val artFile = File(artCacheDir, "${file.nameWithoutExtension}.jpg")
                        if (!artFile.exists()) {
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            if (bitmap != null) {
                                FileOutputStream(artFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                            }
                        }
                        if (artFile.exists()) {
                            thumbnailUrl = artFile.absolutePath
                        }
                    }
                } catch (_: Exception) {}
                meta.release()

                songs.add(LocalSong(
                    id = file.absolutePath,
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    trackNumber = track,
                    duration = duration,
                    filePath = file.absolutePath,
                    thumbnailUrl = thumbnailUrl,
                    lyrics = ""
                ))
            } catch (_: Exception) {}
        }

        if (songs.isNotEmpty()) {
            dao.deleteAll()
            dao.insertSongs(songs)
        }
        return songs
    }

    fun getMusicDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val target = File(musicDir, "MusicDownloader")
        if (!target.exists()) target.mkdirs()
        return target
    }
}
