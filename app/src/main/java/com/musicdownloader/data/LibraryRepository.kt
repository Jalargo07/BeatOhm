package com.musicdownloader.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LibraryRepository(private val context: Context) : ILibraryRepository {

    data class ScanResult(val songs: List<LocalSong>, val incompleteSongs: List<LocalSong>)

    private val dao = AppDatabase.getInstance(context).songDao()

    override fun getLibraryFolders(): List<String> {
        val defaultDir = getMusicDir().absolutePath
        val set = foldersPrefs().getStringSet(KEY_FOLDERS, null)
        val folders = if (set.isNullOrEmpty()) {
            listOf(defaultDir)
        } else {
            val merged = HashSet(set)
            merged.add(defaultDir)
            merged.toList()
        }
        return folders.map { it.trimEnd('/') }
    }

    override fun getSongsInFolder(folderPath: String): Flow<List<LocalSong>> = dao.getSongsInFolder(folderPath)

    override fun addLibraryFolder(path: String) {
        val normalized = path.trimEnd('/')
        val current = foldersPrefs().getStringSet(KEY_FOLDERS, null) ?: mutableSetOf()
        val updated = HashSet(current)
        updated.add(normalized)
        foldersPrefs().edit().putStringSet(KEY_FOLDERS, updated).apply()
    }

    override fun removeLibraryFolder(path: String) {
        val normalized = path.trimEnd('/')
        val current = foldersPrefs().getStringSet(KEY_FOLDERS, null) ?: return
        val updated = HashSet(current)
        updated.remove(normalized)
        foldersPrefs().edit().putStringSet(KEY_FOLDERS, updated).apply()
    }

    override fun getAlbumArtCacheDir(): File = File(getMusicDir(), ".albumart")

    suspend fun scanMusicFolder(): List<LocalSong> = scanLibrary().songs

    override suspend fun scanLibrary(): ScanResult {
        val prefs = context.getSharedPreferences("waveform_migration", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("v2_cleared", false)) {
            dao.clearAllWaveforms()
            prefs.edit().putBoolean("v2_cleared", true).apply()
        }
        return fastScan()
    }

    override suspend fun fastScan(): ScanResult = withContext(Dispatchers.IO) {
        cleanupDuplicates()
        val existing = dao.getAllSongsNow().associate { it.id to it }
        val newSongs = discoverNewFiles(existing)
        if (newSongs.isNotEmpty()) dao.insertSongs(newSongs)
        val allSongs = dao.getAllSongsNow()
        ScanResult(allSongs, allSongs.filter { isIncomplete(it) })
    }

    private fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || song.genre.isBlank()
            || song.thumbnailUrl.isBlank() || song.lyrics.isBlank() || song.year.isBlank()
    }

    private fun discoverNewFiles(existing: Map<String, LocalSong>): List<LocalSong> {
        val newSongs = mutableListOf<LocalSong>()
        val processed = mutableSetOf<String>()
        for (folderPath in getLibraryFolders()) {
            val dir = File(folderPath)
            if (!dir.isDirectory) continue
            try {
                for (file in audioFilesIn(dir, MAX_SCAN_DEPTH)) {
                    val path = file.absolutePath
                    if (processed.add(path) && existing[path] == null) {
                        newSongs.add(LocalSong(id = path, title = file.nameWithoutExtension, duration = 0L, filePath = path))
                    }
                }
            } catch (_: Exception) {}
        }
        return newSongs
    }

    private fun audioFilesIn(root: File, maxDepth: Int): List<File> {
        val visited = HashSet<String>()
        val result = mutableListOf<File>()
        fun visit(dir: File, depth: Int) {
            if (depth > maxDepth) return
            val canonical = try { dir.canonicalPath } catch (_: Exception) { return }
            if (!visited.add(canonical)) return
            val children = try { dir.listFiles() ?: return } catch (_: Exception) { return }
            for (child in children) {
                if (child.name.startsWith(".") || child.name == "Android") continue
                if (child.isDirectory) {
                    visit(child, depth + 1)
                } else if (child.extension.lowercase() in AUDIO_EXTENSIONS) {
                    result.add(child)
                }
            }
        }
        visit(root, 0)
        return result
    }

    override suspend fun deleteSongsInFolder(folderPath: String) {
        val songs = getSongsExclusiveToFolder(folderPath)
        for (song in songs) dao.deleteSong(song)
    }

    private suspend fun getSongsExclusiveToFolder(folderPath: String): List<LocalSong> {
        val normalized = folderPath.trimEnd('/')
        val keepFolders = getLibraryFolders().filter { it != normalized }
        val prefix = "$normalized${File.separator}"
        return dao.getAllSongsNow().filter { song ->
            song.filePath.startsWith(prefix) &&
                keepFolders.none { song.filePath.startsWith("$it${File.separator}") }
        }
    }

    override fun getAlbumCoverOverride(album: String): String? = albumCoverPrefs().getString(album, null)

    override fun setAlbumCoverOverride(album: String, coverPath: String) {
        albumCoverPrefs().edit().putString(album, coverPath).apply()
    }

    override fun downloadArtwork(url: String, dest: File) {
        try {
            if (dest.exists()) return
            val request = Request.Builder().url(url).get().build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { it.write(bytes) }
                }
            }
        } catch (_: Exception) {}
    }

    override fun getMusicDir(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val target = File(musicDir, "MusicDownloader")
        if (!target.exists()) target.mkdirs()
        return target
    }

    private suspend fun cleanupDuplicates() {
        try {
            val allSongs = dao.getAllSongsNow()
            val orphaned = allSongs.filter { !File(it.id).exists() }
            orphaned.forEach { dao.deleteSongById(it.id) }
            if (orphaned.isNotEmpty()) {
                Log.e(TAG, "Eliminados ${orphaned.size} registros huérfanos")
            }
            val groups = allSongs.filter { File(it.id).exists() }
                .groupBy { "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}" }
            var dedupCount = 0
            for ((_, songs) in groups) {
                if (songs.size > 1) {
                    for (dupe in songs.drop(1)) {
                        dao.deleteSongById(dupe.id)
                        dedupCount++
                    }
                }
            }
            if (dedupCount > 0) {
                Log.e(TAG, "Eliminados $dedupCount duplicados")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanupDuplicates error: ${e.message}")
        }
    }

    private fun albumCoverPrefs() =
        context.getSharedPreferences(ALBUM_COVERS_PREFS, Context.MODE_PRIVATE)

    private fun foldersPrefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "LibraryRepository"
        private const val PREFS_NAME = "library_prefs"
        private const val KEY_FOLDERS = "library_folders"
        private const val ALBUM_COVERS_PREFS = "album_covers"
        private const val MAX_SCAN_DEPTH = 4
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "webm")
    }
}
