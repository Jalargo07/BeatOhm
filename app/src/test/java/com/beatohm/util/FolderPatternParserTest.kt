package com.beatohm.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPatternParserTest {

    @Test
    fun `sanitize replaces illegal characters with underscore`() {
        assertEquals("Hello_ World", FolderPatternParser.sanitize("Hello/ World"))
    }

    @Test
    fun `sanitize replaces all illegal characters`() {
        assertEquals("a_b_c_d_e_f", FolderPatternParser.sanitize("a/b\\c:d*e?f"))
    }

    @Test
    fun `sanitize trims whitespace`() {
        assertEquals("Song", FolderPatternParser.sanitize("  Song  "))
    }

    @Test
    fun `sanitize returns Unknown for blank input`() {
        assertEquals("Unknown", FolderPatternParser.sanitize(""))
    }

    @Test
    fun `sanitize returns Unknown for whitespace-only input`() {
        assertEquals("Unknown", FolderPatternParser.sanitize("   "))
    }

    @Test
    fun `sanitize truncates long input`() {
        val longInput = "A".repeat(300)
        val result = FolderPatternParser.sanitize(longInput)
        assertEquals(200, result.length)
    }

    @Test
    fun `sanitize preserves valid characters`() {
        assertEquals("My Song (Remix)", FolderPatternParser.sanitize("My Song (Remix)"))
    }

    @Test
    fun `sanitize handles angle brackets`() {
        assertEquals("Song_", FolderPatternParser.sanitize("Song<"))
        assertEquals("_Song", FolderPatternParser.sanitize(">Song"))
    }
}
