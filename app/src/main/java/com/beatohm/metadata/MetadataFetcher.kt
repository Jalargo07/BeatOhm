package com.beatohm.metadata

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.beatohm.model.Song
import com.beatohm.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Orquesta la búsqueda de metadata sobre las 5 fuentes (Last.fm → iTunes → Spotify →
 * Deezer → MusicBrainz, en ese orden) y decide el resultado con un scoring de confianza.
 *
 * ## Flujo de búsqueda en 3 fases
 *
 * 1. **Fase 1 — artista + título**: consulta los 5 providers con artist+title y ACUMULA
 *    todos los candidatos. Si el mejor candidato supera [THRESHOLD_EARLY_EXIT] (0.85),
 *    se hace early exit → [MetadataResult.ClearMatch]. No hay early exit intermedio:
 *    se acumulan todos los candidatos de las 5 fuentes antes de decidir.
 *
 * 2. **Fase 2 — título + album + duration** (sin artista): solo se ejecuta si
 *    `song.album` no está en blanco O `song.duration > 0`. Consulta con `artist = ""`
 *    y `ExtraTags(song.album)` para que los providers incluyan el album en la query.
 *
 * 3. **Fase 3 — solo título**: SIEMPRE se ejecuta. Consulta con `artist = ""` y el
 *    título. Providers que traen duration (iTunes, Deezer, Spotify) contribuyen al
 *    scoring; los que no (Last.fm, MusicBrainz) dan duration=0 → neutro en el score.
 *
 * ## Re-rank final
 * Se combinan TODOS los candidatos de las 3 fases, se deduplican por title+artist
 * normalizados, se scorean con [scoreCandidate] (que incluye duration scoring con
 * tiers), y se decide con [decideMatch].
 *
 * ## Umbrales
 * - [THRESHOLD_EARLY_EXIT] (0.85): early exit en Fase 1.
 * - [THRESHOLD_CLEAR] (0.60): mejor candidato → [MetadataResult.ClearMatch].
 * - [THRESHOLD_CANDIDATE] (0.40): candidatos viables → [MetadataResult.AmbiguousMatches].
 * - < 0.40 → [MetadataResult.NoMatch].
 */
class MetadataFetcher {

    companion object {
        private const val TAG = "MetadataFetcher"

        /** Score para early exit en Fase 1: mejor candidato ≥ 85% → ClearMatch inmediato. */
        const val THRESHOLD_EARLY_EXIT = 0.85f

        /** Score mínimo para considerar un match "claro" (enriquecer automático). */
        const val THRESHOLD_CLEAR = 0.60f

        /** Score mínimo para que un candidato sea válido (AmbiguousMatches). */
        const val THRESHOLD_CANDIDATE = 0.40f

        /** Orden de consulta de fuentes (PLAN.md decisión #1). */
        private val PROVIDERS = listOf(
            LastFmProvider,
            ITunesProvider,
            SpotifyProvider,
            DeezerProvider,
            MusicBrainzProvider
        )
    }

    /**
     * Busca metadata completa para [song] sobre las 5 fuentes y devuelve un
     * [MetadataResult]:
     * - [MetadataResult.ClearMatch]: mejor candidato superó [THRESHOLD_CLEAR] (o
     *   [THRESHOLD_EARLY_EXIT] en Fase 1).
     * - [MetadataResult.AmbiguousMatches]: varios candidatos plausibles (score
     *   >= [THRESHOLD_CANDIDATE] pero < [THRESHOLD_CLEAR]).
     * - [MetadataResult.NoMatch]: ningún candidato alcanzó [THRESHOLD_CANDIDATE].
     *
     * El scoring usa [scoreCandidate] con duration tiers: ±20s bonus máximo,
     * ±30s bonus medio, ±60s neutro, >60s penalty severo (×0.3). Providers
     * sin duration (Last.fm, MusicBrainz) dan duration=0 → neutro (no penaliza).
     */
    suspend fun fetchFullMetadata(song: Song): MetadataResult = withContext(Dispatchers.IO) {
        try {
            AppLogger.d(TAG, "fetchFullMetadata start: artist=${song.artist.length}ch, title=${song.title.length}ch")

            val usableArtist = cleanChannelName(song.artist).isNotBlank()
            val allCandidates = mutableListOf<MetadataCandidate>()

            // ── FASE 1: artista + título en las 5 fuentes ──────────────────
            if (usableArtist) {
                val extraTags = ExtraTags(song.album, song.genre)
                val phase1 = queryProviders(song.artist, song.title, extraTags, "Fase 1")
                allCandidates += phase1

                // Early exit: si el MEJOR candidato de Fase 1 ≥ 85%, devolver ya
                val scored1 = phase1.map { it.copy(score = scoreCandidate(it, song)) }
                val bestPhase1 = scored1.maxByOrNull { it.score }
                if (bestPhase1 != null && bestPhase1.score >= THRESHOLD_EARLY_EXIT) {
                    AppLogger.d(TAG, "Fase 1 early exit: score=${bestPhase1.score}")
                    return@withContext MetadataResult.ClearMatch(bestPhase1)
                }
                AppLogger.d(TAG, "Fase 1 done: ${phase1.size} candidates, best=${bestPhase1?.score ?: 0f}")
            } else {
                Log.d(TAG, "Fase 1 SKIP: artist not usable after cleanChannelName")
            }

            // ── FASE 2: título + album + duration (sin artista) ────────────
            if (song.album.isNotBlank() || song.duration > 0) {
                Log.d(TAG, "Fase 2: no artist, album=${song.album.length}ch, duration=${song.duration}ms")
                val extraTags2 = ExtraTags(song.album)
                val phase2 = queryProviders("", song.title, extraTags2, "Fase 2")
                allCandidates += phase2
                Log.d(TAG, "Fase 2 done: ${phase2.size} candidates accumulated")
            } else {
                Log.d(TAG, "Fase 2 SKIP: no album or duration")
            }

            // ── FASE 3: solo título ────────────────────────────────────────
            Log.d(TAG, "Fase 3: title only (${song.title.length}ch)")
            val phase3 = queryProviders("", song.title, ExtraTags(), "Fase 3")
            allCandidates += phase3
            Log.d(TAG, "Fase 3 done: ${phase3.size} candidates, total=${allCandidates.size}")

            // ── RE-RANK FINAL ──────────────────────────────────────────────
            val deduplicated = deduplicateCandidates(allCandidates)
            Log.d(TAG, "Re-rank: ${allCandidates.size} total → ${deduplicated.size} after dedup")

            val scored = deduplicated
                .map { it.copy(score = scoreCandidate(it, song)) }
                .sortedByDescending { it.score }

            if (scored.isNotEmpty()) {
                Log.d(TAG, "Top candidate: score=${scored.first().score} [${scored.first().source}]")
            }

            decideMatch(scored)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFullMetadata ERROR: ${e.message}", e)
            MetadataResult.NoMatch
        }
    }

    /**
     * Consulta todos los providers en orden ([PROVIDERS]) con la misma firma y acumula
     * los candidatos crudos de cada uno. Cada candidato pasa por limpieza
     * ([MetadataCandidate.cleaned]) antes de sumarse al acumulado.
     *
     * La deduplicación NO se hace aquí: se acumulan todos los candidatos crudos de
     * esta fase y la dedup global ocurre al final de [fetchFullMetadata] para evitar
     * que candidatos de fuentes distintas se pierdan prematuramente.
     */
    private suspend fun queryProviders(
        artist: String,
        title: String,
        extraTags: ExtraTags,
        phase: String
    ): List<MetadataCandidate> {
        val candidates = mutableListOf<MetadataCandidate>()
        for (provider in PROVIDERS) {
            Log.d(TAG, "$phase: querying ${provider.source}...")
            val raw = provider.search(artist, title, extraTags)
            if (raw.isEmpty()) {
                Log.d(TAG, "$phase: ${provider.source} no results")
            } else {
                val cleaned = raw.map { it.cleaned() }
                Log.d(TAG, "$phase: ${provider.source} → ${cleaned.size} candidate(s)")
                candidates += cleaned
            }
        }
        return candidates
    }

    /**
     * Elimina candidatos duplicados que representan la misma canción llegada
     * de fuentes distintas (ej. Last.fm + Deezer + iTunes para "Besos En Guerra"
     * de Morat). Agrupa por clave normalizada `title|artist` y conserva el
     * candidato con mayor score de cada grupo.
     *
     * Se ejecuta UNA sola vez al final de [fetchFullMetadata], después de
     * acumular candidatos de las 3 fases, para que el scoring global (con
     * duration) determine qué candidato conservar de cada grupo.
     */
    @VisibleForTesting
    internal fun deduplicateCandidates(candidates: List<MetadataCandidate>): List<MetadataCandidate> {
        return candidates.groupBy {
            "${normalizeForMatch(it.title)}|${normalizeForMatch(it.artist)}"
        }.mapValues { (_, group) ->
            group.maxByOrNull { it.score }!!
        }.values.toList()
    }

    /**
     * Calcula el score de confianza de [candidate] contra [song] (0.0 – 1.0).
     *
     * ## Pesos
     * | Componente | Peso  | Match              | Neutral (dato ausente) |
     * |------------|-------|--------------------|------------------------|
     * | TITLE      | 0.35  | 0.35               | —                      |
     * | ARTIST     | 0.30  | 0.30               | 0.15 (artista blank)   |
     * | ALBUM      | 0.10  | 0.10               | 0.05 (album blank)     |
     * | YEAR       | 0.05  | 0.05               | 0.025 (año blank)      |
     * | DURATION   | 0.20  | tiers (ver abajo)  | 0.10 (duration=0)      |
     *
     * ## Duration tiers (el factor más potente)
     * - ≤ 20 s de diferencia → +0.20 (bonus máximo)
     * - ≤ 30 s → +0.10 (bonus medio)
     * - ≤ 60 s → +0.0 (neutro)
     * - > 60 s → score × 0.3 (penalty severo: evita matchear canciones distintas
     *   con el mismo título, ej. "La Tierra" de Ekhymosis vs Ivete Sangalo)
     * - duration = 0 en candidato o canción → +0.10 (neutro: no gana ni pierde)
     *
     * ## Penalty de artista
     * Si `song.artist` es usable Y el artista del candidato NO contiene al
     * esperado (normalizado) → score × 0.5.
     *
     * @return Score en rango [0.0, 1.0].
     */
    @VisibleForTesting
    internal fun scoreCandidate(candidate: MetadataCandidate, song: Song): Float {
        val usableArtist = cleanChannelName(song.artist).isNotBlank()

        var score = 0f

        // ── TITLE (0.35) ──────────────────────────────────────────────────
        if (isGoodMatch(song.title, candidate.title)) score += 0.35f

        // ── ARTIST (0.30) ─────────────────────────────────────────────────
        if (usableArtist) {
            if (isGoodMatch(song.artist, candidate.artist)) {
                score += 0.30f
            }
            // Penalty: artista del candidato no contiene al de la canción
            val songNorm = normalizeForMatch(song.artist)
            val candidateNorm = normalizeForMatch(candidate.artist)
            if (songNorm.isNotBlank() && candidateNorm.isNotBlank() &&
                !candidateNorm.contains(songNorm)) {
                score *= 0.5f
            }
        } else {
            score += 0.15f  // neutral: 0.30 × 0.5
        }

        // ── ALBUM (0.10) ──────────────────────────────────────────────────
        if (song.album.isNotBlank()) {
            if (isGoodMatch(song.album, candidate.album)) score += 0.10f
        } else {
            score += 0.05f  // neutral
        }

        // ── YEAR (0.05) ───────────────────────────────────────────────────
        if (song.year.isNotBlank() && candidate.year.isNotBlank()) {
            if (song.year == candidate.year) score += 0.05f
        } else {
            score += 0.025f  // neutral
        }

        // ── DURATION (0.20) — el factor más potente ───────────────────────
        if (candidate.duration > 0 && song.duration > 0) {
            val diff = abs(candidate.duration - song.duration)
            when {
                diff <= 20_000L -> score += 0.20f  // ±20s: bonus máximo
                diff <= 30_000L -> score += 0.10f  // ±30s: bonus medio
                diff <= 60_000L -> { /* neutro: +0 */ }
                else -> score *= 0.3f               // >60s: penalty severo
            }
        } else {
            score += 0.10f  // duration=0: neutro (no gana ni pierde)
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Decide el [MetadataResult] a partir de los candidatos ya scored y ordenados:
     * - Mejor score >= [THRESHOLD_CLEAR] (0.60) → [MetadataResult.ClearMatch].
     * - Mejor score >= [THRESHOLD_CANDIDATE] (0.40) → [MetadataResult.AmbiguousMatches]
     *   con todos los candidatos que alcancen el umbral.
     * - Ningún candidato alcanza 0.40 → [MetadataResult.NoMatch].
     *
     * **Importante**: los candidatos que llegan aquí ya pasaron por
     * [deduplicateCandidates], así que si quedan 2+ con score >= 0.60 son
     * CANCIONES DISTINTAS (ej. "El Huracán" de 3 artistas), no la misma
     * canción de 3 fuentes.
     */
    @VisibleForTesting
    internal fun decideMatch(candidates: List<MetadataCandidate>): MetadataResult {
        if (candidates.isEmpty()) return MetadataResult.NoMatch

        val bestScore = candidates.first().score

        if (bestScore >= THRESHOLD_CLEAR) {
            return MetadataResult.ClearMatch(candidates.first())
        }

        val viable = candidates.filter { it.score >= THRESHOLD_CANDIDATE }
        return if (viable.isNotEmpty()) {
            MetadataResult.AmbiguousMatches(viable)
        } else {
            MetadataResult.NoMatch
        }
    }

    /**
     * Aplica limpieza a los campos de un candidato crudo antes del scoring:
     * [cleanTitle] sobre el título y `cleanArtist(cleanChannelName())` sobre el artista
     * (mismo comportamiento que el código viejo al construir el Song enriquecido).
     */
    @VisibleForTesting
    internal fun MetadataCandidate.cleaned(): MetadataCandidate = copy(
        title = cleanTitle(title),
        artist = cleanArtist(cleanChannelName(artist))
    )

    /**
     * Compara dos strings normalizados (NFD + lowercase + letterOrDigit) y devuelve
     * true si uno contiene al otro. `false` si alguno queda vacío tras normalizar
     * (evita matches triviales contra strings vacíos).
     */
    @VisibleForTesting
    internal fun isGoodMatch(expected: String, actual: String): Boolean {
        val e = normalizeForMatch(expected)
        val a = normalizeForMatch(actual)
        return e.isNotBlank() && a.isNotBlank() && (a.contains(e) || e.contains(a))
    }
}
