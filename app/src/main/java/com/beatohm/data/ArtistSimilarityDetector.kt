package com.beatohm.data

import android.util.Log
import com.beatohm.metadata.normalizeForMatch

/**
 * Detector de artistas duplicados basado en similitud textual (Levenshtein).
 *
 * Compara pares de artistas únicos presentes en la tabla `songs` y calcula un
 * score de similitud (0-100). Solo propone pares con score >= [MIN_SCORE].
 *
 * El algoritmo:
 * 1. Obtiene artistas únicos con conteo de canciones.
 * 2. Para cada par, optimiza: solo compara si empiezan con la misma letra O
 *    la diferencia de longitud es <= 3.
 * 3. Calcula score = 100 - (levenshtein / maxLen * 100).
 * 4. Filtra score >= 65 y ordena por score descendente.
 *
 * NO merge automático — SIEMPRE el usuario confirma.
 */
object ArtistSimilarityDetector {

    private const val TAG = "ArtistSimilarity"
    private const val MIN_SCORE = 65
    private const val MAX_LENGTH_DIFF = 3

    data class DuplicatePair(
        val artist1: String,
        val artist2: String,
        val score: Int,
        val songCount1: Int,
        val songCount2: Int
    )

    /**
     * Distancia de Levenshtein entre dos strings.
     */
    fun levenshtein(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Normaliza un texto para comparación reutilizando [normalizeForMatch] de
     * MetadataCleaning (DRY).
     */
    fun normalize(text: String): String = normalizeForMatch(text)

    /**
     * Encuentra pares de artistas similares en la base de datos.
     *
     * @param db Instancia de [AppDatabase] para acceder al SongDao.
     * @return Lista de [DuplicatePair] ordenada por score descendente.
     */
    suspend fun findDuplicatePairs(db: AppDatabase): List<DuplicatePair> {
        val dao = db.songDao()
        val allSongs = dao.getAllSongsNow()

        // Agrupar por artista exacto (original, no normalizado)
        val artistCounts = mutableMapOf<String, Int>()
        for (song in allSongs) {
            val artist = song.artist.takeIf { it.isNotBlank() } ?: continue
            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
        }

        val artists = artistCounts.keys.toList()
        Log.d(TAG, "Artistas únicos: ${artists.size}")

        // Precalcular normalizados
        val normalizedMap = artists.associateWith { normalize(it) }

        val pairs = mutableListOf<DuplicatePair>()

        for (i in artists.indices) {
            for (j in i + 1 until artists.size) {
                val a = artists[i]
                val b = artists[j]
                val normA = normalizedMap[a]!!
                val normB = normalizedMap[b]!!

                // Si ya normalizan igual → score 100
                if (normA == normB) {
                    pairs.add(
                        DuplicatePair(
                            artist1 = a,
                            artist2 = b,
                            score = 100,
                            songCount1 = artistCounts[a]!!,
                            songCount2 = artistCounts[b]!!
                        )
                    )
                    continue
                }

                // Optimización: solo comparar si empiezan con la misma letra
                // O la diferencia de longitud es <= MAX_LENGTH_DIFF
                val firstCharA = normA.firstOrNull()
                val firstCharB = normB.firstOrNull()
                val lengthDiff = kotlin.math.abs(normA.length - normB.length)

                if (firstCharA != firstCharB && lengthDiff > MAX_LENGTH_DIFF) {
                    continue
                }

                val maxLen = maxOf(normA.length, normB.length, 1)
                val dist = levenshtein(normA, normB)
                val score = 100 - (dist * 100 / maxLen)

                if (score >= MIN_SCORE) {
                    pairs.add(
                        DuplicatePair(
                            artist1 = a,
                            artist2 = b,
                            score = score,
                            songCount1 = artistCounts[a]!!,
                            songCount2 = artistCounts[b]!!
                        )
                    )
                }
            }
        }

        Log.d(TAG, "Pares encontrados: ${pairs.size}")
        return pairs.sortedByDescending { it.score }
    }

    /**
     * Une dos artistas: actualiza TODAS las filas de `songs` donde
     * `artist == from` a `artist = to`.
     *
     * NOTA: NO renombra archivos, NO toca metadata_candidates.
     *
     * @param db Instancia de [AppDatabase].
     * @param from Nombre del artista viejo.
     * @param to Nombre del artista nuevo (destino del merge).
     */
    suspend fun mergeArtists(db: AppDatabase, from: String, to: String) {
        val dao = db.songDao()
        val count = dao.updateArtist(from, to)
        Log.d(TAG, "Merge artistas: '$from' → '$to' ($count canciones actualizadas)")
    }
}
