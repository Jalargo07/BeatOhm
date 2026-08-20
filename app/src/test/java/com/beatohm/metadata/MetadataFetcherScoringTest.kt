package com.beatohm.metadata

import android.util.Log
import com.beatohm.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class MetadataFetcherScoringTest {

    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockedLog = Mockito.mockStatic(Log::class.java)
        mockedLog.`when`<Int> { Log.d(Mockito.anyString(), Mockito.anyString()) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    private val fetcher = MetadataFetcher()

    private fun song(
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "",
        year: String = "",
        duration: Long = 0L
    ) = Song(
        title = title,
        artist = artist,
        album = album,
        year = year,
        duration = duration,
        filePath = "/test/file.mp3"
    )

    private fun candidate(
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "",
        year: String = "",
        duration: Long = 0L,
        score: Float = 0f,
        source: MetadataSource = MetadataSource.ITUNES
    ) = MetadataCandidate(
        title = title,
        artist = artist,
        album = album,
        year = year,
        duration = duration,
        score = score,
        source = source
    )

    // ── isGoodMatch ─────────────────────────────────────────────────────────

    @Test
    fun `isGoodMatch returns true for exact match`() {
        assertTrue(fetcher.isGoodMatch("Hello", "Hello"))
    }

    @Test
    fun `isGoodMatch returns true when one contains the other`() {
        assertTrue(fetcher.isGoodMatch("Hello", "Hello World"))
    }

    @Test
    fun `isGoodMatch is case insensitive`() {
        assertTrue(fetcher.isGoodMatch("hello", "HELLO"))
    }

    @Test
    fun `isGoodMatch returns false for completely different strings`() {
        assertFalse(fetcher.isGoodMatch("Hello", "Goodbye"))
    }

    @Test
    fun `isGoodMatch returns false when expected is blank`() {
        assertFalse(fetcher.isGoodMatch("", "Hello"))
    }

    @Test
    fun `isGoodMatch returns false when actual is blank`() {
        assertFalse(fetcher.isGoodMatch("Hello", ""))
    }

    @Test
    fun `isGoodMatch handles accented characters`() {
        assertTrue(fetcher.isGoodMatch("Canción", "Cancion"))
    }

    // ── scoreCandidate ──────────────────────────────────────────────────────

    @Test
    fun `scoreCandidate high score for exact match with all fields`() {
        val s = song(title = "Song", artist = "Artist", album = "Album", year = "2024", duration = 200_000)
        val c = candidate(title = "Song", artist = "Artist", album = "Album", year = "2024", duration = 200_000)
        val score = fetcher.scoreCandidate(c, s)
        // title(0.35) + artist(0.30) + album(0.10) + year(0.05) + duration(0.20) = 1.0
        assertTrue("Expected score >= 0.9, got $score", score >= 0.9f)
    }

    @Test
    fun `scoreCandidate penalizes mismatched duration`() {
        val s = song(title = "Song", artist = "Artist", duration = 200_000)
        val c = candidate(title = "Song", artist = "Artist", duration = 300_000) // 100s diff
        val score = fetcher.scoreCandidate(c, s)
        // Duration >60s diff: score *= 0.3, so total should be low
        assertTrue("Expected score < 0.5, got $score", score < 0.5f)
    }

    @Test
    fun `scoreCandidate gives neutral score when artist is blank`() {
        val s = song(title = "Song", artist = "", duration = 200_000)
        val c = candidate(title = "Song", artist = "Some Artist", duration = 200_000)
        val score = fetcher.scoreCandidate(c, s)
        // title(0.35) + artist neutral(0.15) + album neutral(0.05) + year neutral(0.025) + duration(0.20) = 0.775
        assertTrue("Expected score >= 0.6, got $score", score >= 0.6f)
    }

    @Test
    fun `scoreCandidate duration bonus for close match`() {
        val s = song(title = "Song", artist = "Artist", duration = 200_000)
        val c = candidate(title = "Song", artist = "Artist", duration = 210_000) // 10s diff
        val score = fetcher.scoreCandidate(c, s)
        // title(0.35) + artist(0.30) + album neutral(0.05) + year neutral(0.025) + duration bonus(0.20) = 0.925
        assertTrue("Expected score >= 0.9, got $score", score >= 0.9f)
    }

    // ── deduplicateCandidates ───────────────────────────────────────────────

    @Test
    fun `deduplicateCandidates keeps higher score from duplicates`() {
        val c1 = candidate(title = "Song", artist = "Artist", score = 0.3f)
        val c2 = candidate(title = "Song", artist = "Artist", score = 0.8f)
        val result = fetcher.deduplicateCandidates(listOf(c1, c2))
        assertEquals(1, result.size)
        assertEquals(0.8f, result[0].score, 0.01f)
    }

    @Test
    fun `deduplicateCandidates keeps distinct candidates`() {
        val c1 = candidate(title = "Song A", artist = "Artist X", score = 0.5f)
        val c2 = candidate(title = "Song B", artist = "Artist Y", score = 0.5f)
        val result = fetcher.deduplicateCandidates(listOf(c1, c2))
        assertEquals(2, result.size)
    }

    @Test
    fun `deduplicateCandidates deduplicates case-insensitive`() {
        val c1 = candidate(title = "hello", artist = "world", score = 0.4f)
        val c2 = candidate(title = "HELLO", artist = "WORLD", score = 0.6f)
        val result = fetcher.deduplicateCandidates(listOf(c1, c2))
        assertEquals(1, result.size)
        assertEquals(0.6f, result[0].score, 0.01f)
    }

    // ── decideMatch ─────────────────────────────────────────────────────────

    @Test
    fun `decideMatch returns NoMatch for empty list`() {
        val result = fetcher.decideMatch(emptyList())
        assertTrue(result is MetadataResult.NoMatch)
    }

    @Test
    fun `decideMatch returns ClearMatch for high score`() {
        val c = candidate(score = 0.85f)
        val result = fetcher.decideMatch(listOf(c))
        assertTrue(result is MetadataResult.ClearMatch)
    }

    @Test
    fun `decideMatch returns AmbiguousMatches for medium score`() {
        val c = candidate(score = 0.50f)
        val result = fetcher.decideMatch(listOf(c))
        assertTrue(result is MetadataResult.AmbiguousMatches)
    }

    @Test
    fun `decideMatch returns NoMatch for low score`() {
        val c = candidate(score = 0.20f)
        val result = fetcher.decideMatch(listOf(c))
        assertTrue(result is MetadataResult.NoMatch)
    }

    // ── Threshold constants ─────────────────────────────────────────────────

    @Test
    fun `thresholds are correctly defined`() {
        assertEquals(0.85f, MetadataFetcher.THRESHOLD_EARLY_EXIT, 0.001f)
        assertEquals(0.60f, MetadataFetcher.THRESHOLD_CLEAR, 0.001f)
        assertEquals(0.40f, MetadataFetcher.THRESHOLD_CANDIDATE, 0.001f)
    }
}
