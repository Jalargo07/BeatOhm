package com.beatohm.data

import android.util.Log
import com.beatohm.ads.TagWriteCounter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * P2.3: Coordinador único de escritura de tags — punto de entrada para TODOS los
 * flujos que escriben metadata/artwork/lyrics al archivo de audio.
 *
 * Responsabilidades:
 * - Verifica el límite de escrituras gratuitas antes de cada operación (lanza
 *   [TagWriteLimitReachedException] si se alcanzó).
 * - Protege [TagWriteCounter] con [Mutex] para que la secuencia check→increment
 *   sea atómica entre coroutines concurrentes.
 * - Solo incrementa el counter tras éxito confirmado (Result.success(true)).
 * - Delega la escritura real a [AudioTagWriter] / [OpusTagWriter].
 *
 * Consumidores: MusicRepository (finalizeMetadataUpdate, writeArtworkToFile,
 * writeLyricsToFile, enrichSong), AudioDownloader.writeMetadata,
 * PlaylistImportManager.writeTags, MetadataRegenService.
 */
class TagWriteCoordinator {

    private val mutex = Mutex()

    /**
     * Escribe tags de metadata (title, artist, album, genre, year, trackNumber)
     * al archivo de audio.
     *
     * @return [Result.success] con `true` si se escribió, `false` si fue no-op
     *   (formato no soportado, archivo vacío, etc.), o [Result.failure] si hubo
     *   un error al escribir.
     * @throws TagWriteLimitReachedException si se alcanzó el límite de escrituras
     *   gratuitas.
     */
    suspend fun writeMetadata(file: File, song: LocalSong): Result<Boolean> {
        return doWrite { AudioTagWriter.writeTags(file, song) }
    }

    /**
     * Escribe SOLO artwork (carátula) al archivo de audio.
     *
     * @return [Result.success] con `true` si se escribió, `false` si fue no-op,
     *   o [Result.failure] si hubo error.
     * @throws TagWriteLimitReachedException si se alcanzó el límite.
     */
    suspend fun writeArtwork(file: File, song: LocalSong): Result<Boolean> {
        return doWrite { AudioTagWriter.writeArtwork(file, song) }
    }

    /**
     * Escribe SOLO lyrics (letras) al archivo de audio.
     *
     * @return [Result.success] con `true` si se escribió, `false` si fue no-op,
     *   o [Result.failure] si hubo error.
     * @throws TagWriteLimitReachedException si se alcanzó el límite.
     */
    suspend fun writeLyrics(file: File, song: LocalSong): Result<Boolean> {
        return doWrite { AudioTagWriter.writeLyrics(file, song) }
    }

    /**
     * Verifica el límite de escritura. Lanza [TagWriteLimitReachedException] si
     * se alcanzó. Thread-safe vía [Mutex].
     */
    suspend fun checkLimit() {
        mutex.withLock {
            if (TagWriteCounter.hasReachedLimit()) {
                throw TagWriteLimitReachedException()
            }
        }
    }

    /**
     * Verifica el límite de forma síncrona (para callers que no están en coroutine).
     * [checkLimit] es preferido en contexto suspend.
     */
    fun checkLimitSync() {
        if (TagWriteCounter.hasReachedLimit()) {
            throw TagWriteLimitReachedException()
        }
    }

    /**
     * Orquesta una operación de escritura: verifica límite → ejecuta → incrementa
     * counter solo si tuvo éxito (Result.success(true)).
     *
     * El [Mutex] protege la secuencia check → increment entre coroutines
     * concurrentes. La operación de escritura I/O corre fuera del lock.
     */
    private suspend fun doWrite(action: () -> Result<Boolean>): Result<Boolean> {
        checkLimit()
        return try {
            val result = action()
            if (result.isSuccess && result.getOrDefault(false)) {
                mutex.withLock {
                    TagWriteCounter.increment()
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tag write failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "TagWriteCoordinator"
    }
}
