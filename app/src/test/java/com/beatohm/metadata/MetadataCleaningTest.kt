package com.beatohm.metadata

import android.util.Log
import com.google.gson.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito

class MetadataCleaningTest {

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

    // ── cleanTitle ──────────────────────────────────────────────────────────

    @Test
    fun `cleanTitle removes parenthetical suffixes`() {
        assertEquals("Bohemian Rhapsody", cleanTitle("Bohemian Rhapsody (Official Video)"))
    }

    @Test
    fun `cleanTitle removes bracket suffixes`() {
        assertEquals("Shape of You", cleanTitle("Shape of You [Lyrics]"))
    }

    @Test
    fun `cleanTitle removes ft suffix`() {
        assertEquals("Havana", cleanTitle("Havana ft. Young Thug"))
    }

    @Test
    fun `cleanTitle removes dash Official Audio Live suffixes`() {
        assertEquals("Blinding Lights", cleanTitle("Blinding Lights - Official Video"))
    }

    @Test
    fun `cleanTitle removes MV suffix`() {
        assertEquals("Dynamite", cleanTitle("Dynamite MV"))
    }

    @Test
    fun `cleanTitle removes year in parentheses at end`() {
        assertEquals("Song Title", cleanTitle("Song Title (2024)"))
    }

    @Test
    fun `cleanTitle removes pipe channel name`() {
        assertEquals("My Song", cleanTitle("My Song | The Cypher Effect"))
    }

    @Test
    fun `cleanTitle handles multiple suffixes at once`() {
        assertEquals("Song", cleanTitle("Song (Official Video) [4K] - Live"))
    }

    @Test
    fun `cleanTitle preserves clean titles`() {
        assertEquals("Bohemian Rhapsody", cleanTitle("Bohemian Rhapsody"))
    }

    // ── cleanArtist ─────────────────────────────────────────────────────────

    @Test
    fun `cleanArtist removes Topic suffix`() {
        assertEquals("Pink Floyd", cleanArtist("Pink Floyd - Topic"))
    }

    @Test
    fun `cleanArtist removes VEVO suffix`() {
        assertEquals("Bad Bunny", cleanArtist("Bad BunnyVEVO"))
    }

    @Test
    fun `cleanArtist handles VEVO with space`() {
        assertEquals("Adele", cleanArtist("Adele VEVO"))
    }

    // ── cleanChannelName ────────────────────────────────────────────────────

    @Test
    fun `cleanChannelName removes Oficial suffix`() {
        assertEquals("La Mosca", cleanChannelName("La Mosca Oficial"))
    }

    @Test
    fun `cleanChannelName removes TV suffix`() {
        assertEquals("Bersuit", cleanChannelName("BersuitTV"))
    }

    @Test
    fun `cleanChannelName strips el prefix`() {
        assertEquals("Vecindariocalle13", cleanChannelName("elvecindariocalle13"))
    }

    @Test
    fun `cleanChannelName normalizes prepositions to lowercase`() {
        assertEquals("De La Ghetto", cleanChannelName("De La Ghetto"))
    }

    @Test
    fun `cleanChannelName removes Official from end`() {
        assertEquals("Nicky Jam", cleanChannelName("Nicky Jam Official"))
    }

    // ── extractYear ─────────────────────────────────────────────────────────

    @Test
    fun `extractYear extracts from full date`() {
        assertEquals("2024", extractYear("2024-03-15"))
    }

    @Test
    fun `extractYear extracts from year-only string`() {
        assertEquals("1999", extractYear("1999"))
    }

    @Test
    fun `extractYear returns empty for non-numeric`() {
        assertEquals("", extractYear("abcd"))
    }

    // ── normalizeForMatch ───────────────────────────────────────────────────

    @Test
    fun `normalizeForMatch lowercases`() {
        assertEquals("hello", normalizeForMatch("Hello"))
    }

    @Test
    fun `normalizeForMatch strips diacritics`() {
        assertEquals("jose", normalizeForMatch("José"))
    }

    @Test
    fun `normalizeForMatch strips non-alphanumeric`() {
        assertEquals("hello", normalizeForMatch("Hello! @#$"))
    }

    @Test
    fun `normalizeForMatch normalizes accented characters`() {
        assertEquals("cancion", normalizeForMatch("Canción"))
    }

    // ── jsonString ──────────────────────────────────────────────────────────

    @Test
    fun `jsonString returns value for existing string field`() {
        val obj = JsonObject().apply { addProperty("title", "Test") }
        assertEquals("Test", jsonString(obj, "title"))
    }

    @Test
    fun `jsonString returns null for missing field`() {
        val obj = JsonObject()
        assertNull(jsonString(obj, "title"))
    }

    @Test
    fun `jsonString returns null for JsonNull value`() {
        val obj = JsonObject().apply { add("title", com.google.gson.JsonNull.INSTANCE) }
        assertNull(jsonString(obj, "title"))
    }

    @Test
    fun `jsonString returns null for non-string primitive`() {
        val obj = JsonObject().apply { addProperty("count", 42) }
        assertNull(jsonString(obj, "count"))
    }
}
