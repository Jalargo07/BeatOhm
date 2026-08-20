package com.beatohm.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.room.withTransaction
import com.beatohm.DeviceUtils
import com.beatohm.metadata.LyricsFetcher
import com.beatohm.metadata.MetadataCandidate
import com.beatohm.metadata.MetadataFetcher
import com.beatohm.metadata.MetadataResult
import com.beatohm.metadata.normalizeForMatch
import com.beatohm.network.NetworkModule
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

class MusicRepository(
    private val context: Context,
    private val libraryRepo: LibraryRepository = LibraryRepository(context),
    private val metadataCandidateRepo: MetadataCandidateRepository
) : IMusicRepository {

    private var limitReachedCallback: (() -> Unit)? = null

    override fun setLimitReachedCallback(callback: (() -> Unit)?) {
        limitReachedCallback = callback
        onLimitReachedGlobal = callback  // Also set the global one
    }

    fun onLimitReached() {
        Log.w(TAG, "onLimitReached called, local=${limitReachedCallback != null}, global=${onLimitReachedGlobal != null}")
        val callback = limitReachedCallback ?: onLimitReachedGlobal
        if (callback != null) {
            // Must run on main thread for UI operations (dialogs, toasts)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback.invoke()
            }
        }
    }

    /** Exposición pública del repo de candidatos (lo usa MetadataRegenService). */
    val metadataCandidateRepository: MetadataCandidateRepository get() = metadataCandidateRepo

    private val db = AppDatabase.getInstance(context)
    private val dao = db.songDao()
    private val playbackEventDao = db.playbackEventDao()
    private val metadataFetcher = MetadataFetcher()
    private val lyricsFetcher = LyricsFetcher()
    private val tagWriteCoordinator = TagWriteCoordinator()

    override suspend fun getAllSongsNow(): List<LocalSong> = dao.getAllSongsNow()

    override fun getAllSongs(): Flow<List<LocalSong>> = dao.getAllSongs()
    override fun getAllSongsByTitle(): Flow<List<LocalSong>> = dao.getAllSongsByTitle()
    override fun getAllSongsByArtist(): Flow<List<LocalSong>> = dao.getAllSongsByArtist()
    override fun getAllSongsByAlbum(): Flow<List<LocalSong>> = dao.getAllSongsByAlbum()
    override fun getAllSongsByDuration(): Flow<List<LocalSong>> = dao.getAllSongsByDuration()
    override fun getAllAlbums(): Flow<List<String>> = dao.getAllAlbums()
    override fun getAllArtists(): Flow<List<String>> = dao.getAllArtists()
    override fun getAllGenres(): Flow<List<String>> = dao.getAllGenres()
    override fun getAllYears(): Flow<List<String>> = dao.getAllYears()
    override fun getAllAlbumsWithCover(): Flow<List<AlbumWithCover>> = dao.getAlbumsWithCover()
    override fun getAllArtistsWithCover(): Flow<List<ArtistWithCover>> = dao.getArtistsWithCover()
    override fun getSongsByAlbum(album: String): Flow<List<LocalSong>> = dao.getSongsByAlbum(album)
    override fun getSongsByArtist(artist: String): Flow<List<LocalSong>> = dao.getSongsByArtist(artist)
    override fun getSongsByGenre(genre: String): Flow<List<LocalSong>> = dao.getSongsByGenre(genre)
    override fun getSongsByYear(year: String): Flow<List<LocalSong>> = dao.getSongsByYear(year)
    override fun getMostPlayedSongs(): Flow<List<LocalSong>> = dao.getMostPlayedSongs()
    override fun getSongCount(): Flow<Int> = dao.getSongCount()
    override fun getFavoriteSongs(): Flow<List<LocalSong>> = dao.getFavoriteSongs()
    override suspend fun setFavorite(songId: String, isFavorite: Boolean) = dao.setFavorite(songId, isFavorite)
    override suspend fun getSongById(id: String): LocalSong? = dao.getSongById(id)

    override fun isIncomplete(song: LocalSong): Boolean {
        return song.artist.isBlank() || song.album.isBlank() || song.genre.isBlank()
            || song.thumbnailUrl.isBlank() || song.lyrics.isBlank() || song.year.isBlank()
    }

    override suspend fun insertSong(song: LocalSong) = dao.insertSong(song)
    override suspend fun deleteSong(song: LocalSong) = dao.deleteSong(song)
    override suspend fun incrementPlayCount(songId: String) = dao.incrementPlayCount(songId)

    override suspend fun recordPlaybackEvent(songId: String, timestamp: Long, score: Int) {
        playbackEventDao.insert(PlaybackEvent(songId = songId, timestamp = timestamp, score = score))
    }

    override fun getTopPlayedSongs(sinceTimestamp: Long, limit: Int): Flow<List<LocalSong>> {
        return playbackEventDao.getTopSongsByScore(sinceTimestamp, limit)
    }

    override suspend fun getSongIdByPath(path: String): String? = dao.getIdByPath(path)

    /**
     * Gradually enriches songs missing metadata (artist, album, duration) in the background.
     * Collects all updated songs and does a single batch insert for performance.
     * Call this AFTER fastScan() so songs are already visible in the UI.
     */
    override suspend fun enrichMetadataGradually(
        songs: List<LocalSong>,
        onProgress: ((done: Int, total: Int, title: String) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            val artCacheDir = libraryRepo.getAlbumArtCacheDir()
            if (!artCacheDir.exists()) artCacheDir.mkdirs()

            val songsNeedingMetadata = songs.filter {
                it.artist.isBlank() || it.album.isBlank() || it.duration == 0L
            }
            val total = songsNeedingMetadata.size
            val done = AtomicInteger(0)
            val threads = DeviceUtils.getOptimalThreadCount(context)
            val semaphore = Semaphore(threads)

            // T9: reorden de cola — las canciones que ya tienen candidatos PENDING (de un
            // fetch previo ambiguo sin resolver) se procesan al FINAL, después de las que no
            // tienen pendientes, para no pisar su elección en curso con un re-fetch temprano.
            // Una sola query del DAO, no N por canción.
            val pendingSongIds = metadataCandidateRepo.getPendingSongIds().toSet()
            val (withPending, withoutPending) = songsNeedingMetadata.partition { it.id in pendingSongIds }
            val ordered = withoutPending + withPending

            Log.d(TAG, "Enriching $total songs with $threads threads (batched by 10, ${withPending.size} con candidatos pendientes al final)")

            val batches = ordered.chunked(10)

            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        val batchResults = mutableListOf<LocalSong>()
                        for (song in batch) {
                            try {
                                val file = File(song.filePath)
                                if (file.exists()) {
                                    val local = extractSong(file, artCacheDir, song)
                                    // Clasificación con el flujo remoto SOLO si el archivo no
                                    // aportó artist+album (los candidatos no proveen duration).
                                    // ClearMatch → se actualiza en songs; Ambiguous → los
                                    // candidatos quedan en metadata_candidates y la canción NO
                                    // se actualiza (queda con su metadata vieja); NoMatch → se
                                    // guarda lo local, que es lo mejor disponible.
                                    val enriched: LocalSong? = if (local.artist.isBlank() || local.album.isBlank()) {
                                        when (val result = metadataFetcher.fetchFullMetadata(local.toSong())) {
                                            is MetadataResult.ClearMatch -> {
                                                // T3: applyClearMatch maneja rename + tags + DB migration
                                                // (individual, NO batch). El batch al final solo recibe
                                                // canciones que NO pasaron por applyClearMatch.
                                                val cleared = applyClearMatch(local, result.candidate)
                                                // Fetch art if needed (after ClearMatch since thumbnailUrl may be blank)
                                                if (cleared.thumbnailUrl.isBlank()) {
                                                    val artUrl = fetchArtFromITunes(cleared, artCacheDir)
                                                    if (artUrl.isNotBlank()) {
                                                        val withArt = cleared.copy(thumbnailUrl = artUrl)
                                                        dao.updateSong(withArt)
                                                    }
                                                }
                                                null  // Ya manejado por applyClearMatch
                                            }
                                            is MetadataResult.AmbiguousMatches -> {
                                                // NO persistir candidatos en el batch normal de enrichMetadataGradually.
                                                // Solo reEnrichSuspiciousSongs (re-detección explícita del usuario) debe
                                                // crear entradas PENDING. Aquí dejamos la canción sin cambios (como NoMatch)
                                                // para romper el loop infinito: deleteAllPending → enrichMetadata las recrea.
                                                Log.d(TAG, "enrichMetadataGradually: ambiguo '${song.title}' → ignorado (pendiente para re-detección)")
                                                local
                                            }
                                            MetadataResult.NoMatch -> local
                                        }
                                    } else {
                                        local
                                    }
                                    if (enriched != null) {
                                        if (enriched.thumbnailUrl.isBlank()) {
                                            val artUrl = fetchArtFromITunes(enriched, artCacheDir)
                                            batchResults.add(if (artUrl.isNotBlank()) enriched.copy(thumbnailUrl = artUrl) else enriched)
                                        } else {
                                            batchResults.add(enriched)
                                        }
                                    }
                                }
                            } catch (e: CancellationException) { throw e }
                              catch (_: Exception) {}
                            val current = done.incrementAndGet()
                            onProgress?.invoke(current, total, song.title)
                        }
                        if (batchResults.isNotEmpty()) {
                            // update-vs-insert: las filas que YA existen se actualizan con
                            // @Update (no borra la fila ni dispara el CASCADE de
                            // playback_events → conserva ranking y referencias); solo las
                            // realmente nuevas van a insertSongs (REPLACE es destructivo
                            // si la fila ya existe).
                            val (existing, fresh) = batchResults.partition { dao.getSongById(it.id) != null }
                            if (existing.isNotEmpty()) dao.updateSongs(existing)
                            if (fresh.isNotEmpty()) dao.insertSongs(fresh)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun extractSong(file: File, artCacheDir: File, existing: LocalSong?): LocalSong {
        val path = file.absolutePath
        return try {
            val meta = MediaMetadataRetriever()
            meta.setDataSource(path)
            val raw = readMetadataFromRetriever(meta, file)
            val artPath = extractEmbeddedArt(meta, artCacheDir, file)
            meta.release()
            buildSongFromRaw(path, raw, artPath, existing)
        } catch (_: Exception) {
            LocalSong(id = path, title = file.nameWithoutExtension, filePath = path,
                lyrics = existing?.lyrics ?: "", isFavorite = existing?.isFavorite ?: false,
                playCount = existing?.playCount ?: 0, waveformData = existing?.waveformData ?: "")
        }
    }

    private data class RawMetadata(val title: String, val artist: String, val album: String,
        val genre: String, val year: String, val track: Int, val duration: Long)

    private fun readMetadataFromRetriever(meta: MediaMetadataRetriever, file: File): RawMetadata {
        return RawMetadata(
            title = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension),
            artist = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""),
            album = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""),
            genre = fixMojibake(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""),
            year = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: "",
            track = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull() ?: 0,
            duration = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        )
    }

    private fun extractEmbeddedArt(meta: MediaMetadataRetriever, artCacheDir: File, file: File): String {
        if (file.extension.lowercase() == "opus") return ""
        return try {
            val art = meta.embeddedPicture ?: return ""
            val artFile = File(artCacheDir, "${file.nameWithoutExtension}.jpg")
            if (!artFile.exists()) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return ""
                java.io.FileOutputStream(artFile).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out) }
            }
            if (artFile.exists()) artFile.absolutePath else ""
        } catch (_: Exception) { "" }
    }

    private fun buildSongFromRaw(path: String, raw: RawMetadata, artPath: String, existing: LocalSong?): LocalSong {
        return LocalSong(
            id = path, title = raw.title,
            artist = raw.artist.ifBlank { existing?.artist.orEmpty() },
            album = raw.album.ifBlank { existing?.album.orEmpty() },
            genre = raw.genre.ifBlank { existing?.genre.orEmpty() },
            year = raw.year.ifBlank { existing?.year.orEmpty() },
            trackNumber = if (raw.track > 0) raw.track else (existing?.trackNumber ?: 0),
            duration = raw.duration, filePath = path,
            thumbnailUrl = artPath.ifBlank { existing?.thumbnailUrl.orEmpty() },
            lyrics = existing?.lyrics ?: "", isFavorite = existing?.isFavorite ?: false,
            playCount = existing?.playCount ?: 0, waveformData = existing?.waveformData ?: ""
        )
    }

    private suspend fun fetchArtFromITunes(song: LocalSong, artCacheDir: File): String {
        try {
            val query = "${song.artist} ${song.album}".ifBlank { song.title }
            val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&limit=1"
            val request = Request.Builder().url(url).get().build()
            NetworkModule.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return@use
                if (results.length() > 0) {
                    val artworkUrl = results.getJSONObject(0).optString("artworkUrl100", "")
                    if (artworkUrl.isNotBlank()) {
                        val hdUrl = artworkUrl.replace("100x100bb", "600x600bb")
                        val artFile = File(artCacheDir, "${song.title.hashCode()}.jpg")
                        libraryRepo.downloadArtwork(hdUrl, artFile)
                        if (artFile.exists()) return artFile.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "iTunes art fetch failed for ${song.title}: ${e.message}")
        }
        return ""
    }

    // === Funciones con responsabilidad única ===

    override suspend fun fetchMetadata(song: LocalSong): LocalSong {
        val existing = dao.getSongById(song.id) ?: return song
        return when (val result = metadataFetcher.fetchFullMetadata(song.toSong())) {
            // Match claro → aplicar la metadata del candidato (bestValue conserva lo ya bueno).
            is MetadataResult.ClearMatch -> {
                val candidate = result.candidate
                Log.d(TAG, "fetchMetadata: match claro '${candidate.artist}' - '${candidate.title}'")
                applyCandidate(existing, candidate)
            }
            // Varias coincidencias plausibles → NO aplicar: los candidatos se persisten en
            // metadata_candidates para que la UI de pendientes los muestre y la canción
            // queda SIN cambios (con su metadata vieja, sin renombrar ni validar).
            is MetadataResult.AmbiguousMatches -> {
                metadataCandidateRepo.saveCandidates(song.id, result.candidates)
                Log.d(TAG, "fetchMetadata: ambiguo '${song.title}' → candidatos persistidos (${result.candidates.size})")
                song
            }
            // Sin coincidencias → la canción queda sin cambios.
            MetadataResult.NoMatch -> song
        }
    }

    /**
     * Canciones con candidatos PENDING sin resolver (para la UI de pendientes).
     * Si una canción fue renombrada (su id cambió), el songId colgado de
     * metadata_candidates no resuelve a ninguna fila y simplemente se omite;
     * la FK CASCADE limpia los candidatos cuando una canción se borra.
     */
    override suspend fun getSongsWithPendingCandidates(): List<LocalSong> {
        val pendingSongIds = metadataCandidateRepo.getPendingSongIds()
        if (pendingSongIds.isEmpty()) return emptyList()
        return pendingSongIds.mapNotNull { dao.getSongById(it) }
    }

    /**
     * T12: Detecta si la metadata de una canción no coincide con su nombre de archivo.
     *
     * El criterio pragmático: los archivos descargados por el pipeline tienen formato
     * `"artista - título"`. Si la metadata actual no contiene al artista/título del
     * archivo, la metadata es sospechosa y debería re-procesarse.
     *
     * Retorna `false` si el nombre de archivo no tiene separador `" - "` (no podemos juzgar).
     */
    private fun isSuspiciousMetadata(song: LocalSong): Boolean {
        val fileName = File(song.filePath).nameWithoutExtension
        val parts = fileName.split(" - ", limit = 2)
        if (parts.size < 2) return false  // sin separador " - ", no podemos juzgar
        val fileArtist = parts[0].trim()
        val fileTitle = parts[1].trim()

        val normFileArtist = normalizeForMatch(fileArtist)
        val normFileTitle = normalizeForMatch(fileTitle)
        val normMetaArtist = normalizeForMatch(song.artist)
        val normMetaTitle = normalizeForMatch(song.title)

        // Si artist del archivo tiene contenido Y artist de metadata NO contiene artist del archivo → sospechosa
        if (normFileArtist.isNotBlank() && !normMetaArtist.contains(normFileArtist) && !normFileArtist.contains(normMetaArtist)) return true

        // Si title del archivo tiene contenido Y title de metadata NO contiene title del archivo → sospechosa
        if (normFileTitle.isNotBlank() && !normMetaTitle.contains(normFileTitle) && !normFileTitle.contains(normMetaTitle)) return true

        return false
    }

    /**
     * T12: Re-procesa canciones con metadata sospechosa (que no coincide con el nombre
     * del archivo) usando el flujo nuevo de fetchMetadata.
     *
     * 1. Obtiene todas las canciones
     * 2. Filtra las sospechosas (isSuspiciousMetadata)
     * 3. Para cada sospechosa: fetchMetadata → ClearMatch se aplica, AmbiguousMatches
     *    persiste candidatos, NoMatch deja sin cambios
     * 4. Retorna Pair<reEnriched, ambiguous> — cuántas se re-enriquecieron y cuántas
     *    quedaron ambiguas
     */
    override suspend fun reEnrichSuspiciousSongs(): Pair<Int, Int> {
        // Limpiar pendientes viejos (código anterior sin dedup/duración)
        metadataCandidateRepo.deleteAllPending()

        val allSongs = dao.getAllSongsNow()
        val suspicious = allSongs.filter { isSuspiciousMetadata(it) }
        Log.d(TAG, "reEnrichSuspiciousSongs: ${allSongs.size} canciones totales, ${suspicious.size} sospechosas")

        var reEnriched = 0
        var ambiguous = 0

        for (song in suspicious) {
            try {
                val updated = fetchMetadata(song)
                if (updated != song) {
                    // ClearMatch → se aplicó metadata nueva
                    reEnriched++
                    Log.d(TAG, "reEnrichSuspiciousSongs: re-enriquecida '${song.title}' → '${updated.title}'")
                } else {
                    // fetchMetadata devolvió la misma canción → AmbiguousMatches (candidatos persistidos)
                    // o NoMatch (sin cambios). En ambos casos queda pendiente.
                    // Verificamos si hay candidatos pendientes para saber si es ambiguo
                    val pendingCandidates = metadataCandidateRepo.getPendingCandidatesBySongId(song.id)
                    if (pendingCandidates.isNotEmpty()) {
                        ambiguous++
                        Log.d(TAG, "reEnrichSuspiciousSongs: ambigua '${song.title}' → candidatos pendientes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "reEnrichSuspiciousSongs: error procesando '${song.title}': ${e.message}")
            }
        }

        Log.d(TAG, "reEnrichSuspiciousSongs resultado: re-enriquecidas=$reEnriched, ambiguas=$ambiguous")
        return Pair(reEnriched, ambiguous)
    }

    override suspend fun downloadArtworkForSong(song: LocalSong): LocalSong {
        val thumbnailUrl = song.thumbnailUrl
        if (thumbnailUrl.isBlank() || !thumbnailUrl.startsWith("http")) return song
        val dest = File(libraryRepo.getAlbumArtCacheDir(), "${song.id.hashCode()}.jpg")
        libraryRepo.downloadArtwork(thumbnailUrl, dest)
        return if (dest.exists()) song.copy(thumbnailUrl = dest.absolutePath) else song
    }

    override suspend fun extractDominantColor(song: LocalSong): LocalSong {
        val thumbnailUrl = song.thumbnailUrl
        if (thumbnailUrl.isBlank()) {
            Log.w(TAG, "extractDominantColor: skipped '${song.title}' - thumbnailUrl is blank")
            return song
        }
        val file = File(thumbnailUrl)
        if (!file.exists()) {
            Log.w(TAG, "extractDominantColor: skipped '${song.title}' - file not found: $thumbnailUrl")
            return song
        }
        try {
            val bitmap = BitmapFactory.decodeFile(thumbnailUrl)
            if (bitmap != null) {
                val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                val dominant = palette.getDominantColor(0)
                Log.d(TAG, "extractDominantColor: #${Integer.toHexString(dominant)} for '${song.title}'")
                bitmap.recycle()
                if (dominant != 0) return song.copy(dominantColor = dominant)
                Log.w(TAG, "extractDominantColor: palette returned 0 for '${song.title}'")
            } else {
                Log.w(TAG, "extractDominantColor: BitmapFactory returned null for '${song.title}' ($thumbnailUrl)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractDominantColor: FAILED for '${song.title}': ${e.message}")
        }
        return song
    }

    override suspend fun fetchLyricsForSong(song: LocalSong): LocalSong {
        if (song.lyrics.isNotBlank()) return song
        try {
            val result = lyricsFetcher.fetchLyrics(song.artist, song.title)
            if (result.isSuccess) {
                val lyrics = (result.getOrNull()?.syncedLrc ?: result.getOrNull()?.plainText).orEmpty()
                if (lyrics.isNotBlank()) {
                    Log.d(TAG, "fetchLyricsForSong: found lyrics for '${song.title}'")
                    return song.copy(lyrics = lyrics)
                }
            }
        } catch (_: Exception) {}
        return song
    }

    override suspend fun writeArtworkToFile(song: LocalSong): LocalSong {
        val file = File(song.filePath)
        if (!file.exists()) return song
        tagWriteCoordinator.writeArtwork(file, song).onSuccess {
            if (it) Log.d(TAG, "Artwork written to file: ${song.title}")
        }
        return song
    }

    override suspend fun writeLyricsToFile(song: LocalSong): LocalSong {
        val file = File(song.filePath)
        if (!file.exists()) return song
        tagWriteCoordinator.writeLyrics(file, song).onSuccess {
            if (it) Log.d(TAG, "Lyrics written to file: ${song.title}")
        }
        return song
    }

    override fun renameSongFile(song: LocalSong): LocalSong {
        val oldFile = File(song.filePath)
        if (!oldFile.exists()) return song
        val newFileName = "${fixMojibake(song.artist)} - ${fixMojibake(song.title)}".replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val newFile = File(oldFile.parent, "${newFileName}.${oldFile.extension}")
        if (newFile.absolutePath == oldFile.absolutePath || newFile.exists()) {
            if (newFile.exists()) {
                Log.w(TAG, "renameSongFile: target already exists '${newFile.name}', skipping")
            }
            return song
        }
        return if (oldFile.renameTo(newFile)) {
            val updated = song.copy(id = newFile.absolutePath, filePath = newFile.absolutePath)
            Log.d(TAG, "renameSongFile: '${oldFile.name}' → '${newFile.name}'")
            updated
        } else song
    }

    override suspend fun saveSong(song: LocalSong) {
        // update si la fila ya existe (preserva playback_events/ranking y referencias de
        // playlists); insertSong (REPLACE) solo para filas nuevas — sobre una fila existente
        // REPLACE la borra y el ON DELETE CASCADE de playback_events pierde el ranking
        // (MetadataRegenService re-guarda canciones que ya están en la DB).
        if (dao.getSongById(song.id) != null) {
            dao.updateSong(song)
        } else {
            dao.insertSong(song)
        }
    }

    // === Composición: enriquecer canción completa ===

    override suspend fun enrichSong(
        song: LocalSong,
        skipTagWrite: Boolean,
        fetchLyrics: Boolean
    ): LocalSong {
        var updated = song
        updated = fetchMetadata(updated)
        // T9: fetchMetadata devuelve la canción SIN cambios cuando no hubo match claro
        // (NoMatch) o cuando el resultado fue ambiguo (los candidatos quedaron en
        // metadata_candidates para elección manual). En esos casos NO se renombra el
        // archivo: no se renombra basándose en metadata no validada.
        val metadataApplied = updated != song
        updated = downloadArtworkForSong(updated)
        updated = extractDominantColor(updated)
        if (fetchLyrics) updated = fetchLyricsForSong(updated)

        if (metadataApplied) {
            updated = finalizeMetadataUpdate(updated)
        } else if (!skipTagWrite) {
            // Sin rename pero con tag write: persistir y escribir tags
            if (dao.getSongById(updated.id) != null) {
                dao.updateSong(updated)
            } else {
                dao.insertSong(updated)
            }
            tagWriteCoordinator.writeMetadata(File(updated.filePath), updated)
        }
        return updated
    }

    /**
     * Finaliza la actualización de metadata de una canción después de un ClearMatch:
     * renombra el archivo, migra referencias de DB si el path cambió, escribe tags
     * ID3/Vorbis al archivo, y actualiza la DB con el nuevo path.
     *
     * Centraliza la lógica de persistencia que antes estaba inline en enrichSong()
     * para que otros flujos (MetadataRegenService, re-detección) puedan reutilizarla
     * sin duplicar código (DRY).
     */
    private suspend fun finalizeMetadataUpdate(song: LocalSong): LocalSong {
        // P2.3: Limit check now handled by TagWriteCoordinator.writeMetadata()

        var updated = song

        // 1. Renombrar archivo con nombre "Artista - Título.ext"
        updated = renameSongFile(updated)

        // 2. Migrar referencias de DB si el path cambió
        if (updated.filePath != song.filePath) {
            try {
                db.withTransaction {
                    val oldId = song.filePath
                    val newId = updated.filePath
                    if (dao.getSongById(newId) != null) {
                        dao.updateSong(updated)
                    } else {
                        dao.insertSong(updated)
                    }
                    dao.movePlaybackEvents(oldId, newId)
                    dao.movePlaylistSongs(oldId, newId)
                    dao.copyRegenStatus(oldId, newId)
                    dao.deleteRegenStatus(oldId)
                    dao.deleteSongById(oldId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "finalizeMetadataUpdate: DB transaction failed, reverting rename '${updated.title}': ${e.message}")
                File(updated.filePath).renameTo(File(song.filePath))
                throw e
            }
        } else {
            if (dao.getSongById(updated.id) != null) {
                dao.updateSong(updated)
            } else {
                dao.insertSong(updated)
            }
        }

        // 3. Escribir tags ID3/Vorbis al archivo via TagWriteCoordinator (P2.3)
        tagWriteCoordinator.writeMetadata(File(updated.filePath), updated).onFailure {
            Log.e(TAG, "finalizeMetadataUpdate: error escribiendo tags '${updated.title}': ${it.message}")
        }

        return updated
    }

    /**
     * Limpieza de integridad de datos: canciones duplicadas en `songs` donde una fila
     * quedó huérfana (archivo físico inexistente) y otra real comparte la misma clave
     * normalizada (title|artist|album). Migra las referencias (playback_events,
     * playlist_songs, regen_status) de cada huérfana a la primera fila real del grupo
     * y elimina la fila huérfana. NO toca grupos donde todas las filas existen (pueden
     * ser canciones legítimamente distintas) ni grupos donde ninguna existe.
     */
    override suspend fun cleanOrphanDuplicateSongs() {
        try {
            val allSongs = dao.getAllSongsNow()
            val groups = allSongs.groupBy {
                "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}|${it.album.lowercase().trim()}"
            }
            var removed = 0
            for (songs in groups.values) {
                if (songs.size < 2) continue
                val real = songs.filter { File(it.filePath).exists() }
                val orphaned = songs.filter { !File(it.filePath).exists() }
                if (real.isEmpty() || orphaned.isEmpty()) continue
                val target = real.first()
                for (orphan in orphaned) {
                    try {
                        // Transacción por huérfana: la secuencia move×3 + delete es atómica.
                        // Si falla una huérfana (p.ej. conflicto inesperado), el try/catch
                        // POR huérfana la registra y continúa con el resto del lote.
                        db.withTransaction {
                            dao.movePlaybackEvents(orphan.id, target.id)
                            dao.movePlaylistSongs(orphan.id, target.id)
                            dao.copyRegenStatus(orphan.id, target.id)
                            dao.deleteRegenStatus(orphan.id)
                            dao.deleteSongById(orphan.id)
                        }
                        removed++
                    } catch (e: Exception) {
                        Log.e(TAG, "cleanOrphanDuplicateSongs: fallo migrando huérfana '${orphan.id}' → '${target.id}': ${e.message}")
                    }
                }
            }
            if (removed > 0) {
                Log.d(TAG, "cleanOrphanDuplicateSongs: $removed huérfanas eliminadas")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanOrphanDuplicateSongs error: ${e.message}")
        }
    }

    private fun bestValue(existing: String, newValue: String): String =
        if (isUsableMetadata(newValue)) newValue else existing

    /**
     * Aplica los campos no-blank de un candidato de metadata sobre [base] (patrón
     * bestValue: solo reemplaza metadata usable). No toca thumbnailUrl/artwork: el
     * arte se resuelve aparte (downloadArtworkForSong / fetchArtFromITunes).
     */
    private fun applyCandidate(base: LocalSong, candidate: MetadataCandidate): LocalSong =
        base.copy(
            title = bestValue(base.title, candidate.title),
            artist = bestValue(base.artist, candidate.artist),
            album = bestValue(base.album, candidate.album),
            genre = bestValue(base.genre, candidate.genre),
            year = bestValue(base.year, candidate.year)
        )

    /** T13: Wrapper público para MetadataRegenService (ClearMatch). */
    fun applyCandidatePublic(base: LocalSong, candidate: MetadataCandidate): LocalSong =
        applyCandidate(base, candidate)

    /** T13: Persiste candidatos ambiguos para MetadataRegenService. */
    suspend fun persistAmbiguousCandidates(songId: String, candidates: List<MetadataCandidate>) {
        metadataCandidateRepo.saveCandidates(songId, candidates)
    }

    /**
     * T2: Aplica un ClearMatch y finaliza la actualización (rename + tags + DB migration).
     * Wrapper público que encapsula applyCandidate + finalizeMetadataUpdate para que
     * MetadataRegenService pueda ejecutar el flujo completo sin duplicar lógica.
     */
    override suspend fun applyClearMatch(song: LocalSong, candidate: MetadataCandidate): LocalSong {
        val applied = applyCandidate(song, candidate)
        return finalizeMetadataUpdate(applied)
    }

    /**
     * P2.4: Aplica un candidato seleccionado por el usuario desde la UI de pendientes
     * y finaliza la actualización (write tags + rename + DB migration + mark APPLIED).
     *
     * Garantiza que no quede candidato APPLIED con archivo/ruta antiguos: el rename
     * y los tags se escriben ANTES de marcar el registro como APPLIED. Si falla
     * la escritura de tags o el rename, el candidato NO se marca APPLIED.
     *
     * @return la LocalSong actualizada, o null si el registro/candidato no existe.
     */
    suspend fun applyCandidateWithFinalize(candidateId: Long, selectedIndex: Int): LocalSong? {
        val entity = metadataCandidateRepo.getById(candidateId) ?: return null
        val candidates = metadataCandidateRepo.deserializeCandidates(entity.candidatesJson)
        if (selectedIndex !in candidates.indices) {
            Log.w(TAG, "applyCandidateWithFinalize: index $selectedIndex out of range (${candidates.size} candidates)")
            return null
        }
        val candidate = candidates[selectedIndex]
        val song = dao.getSongById(entity.songId) ?: return null

        // Apply metadata fields
        val applied = applyCandidate(song, candidate)

        // Finalize: write tags + rename + DB migration
        val finalized = finalizeMetadataUpdate(applied)

        // Mark APPLIED only after finalize succeeds
        metadataCandidateRepo.markApplied(candidateId, System.currentTimeMillis())
        Log.d(TAG, "applyCandidateWithFinalize: applied '${finalized.title}' (id $candidateId, idx $selectedIndex)")

        return finalized
    }

    private fun isUsableMetadata(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return trimmed.lowercase() !in setOf("unknown", "various artists", "desconocido")
    }

    companion object {
        private const val TAG = "MusicRepository"

        // Shared callback across all MusicRepository instances (service + activity)
        @Volatile
        var onLimitReachedGlobal: (() -> Unit)? = null

        /**
         * Detecta y repara doble encoding UTF-8 (mojibake).
         * Ejemplo: "Â¿QuÃ©" → "¿Qué"
         */
        fun fixMojibake(text: String): String {
            if (!text.contains("Â") && !text.contains("Ã")) return text
            try {
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded != text && !decoded.contains("�")) return decoded
            } catch (_: Exception) {}
            return text
        }
    }
}
