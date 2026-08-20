package com.beatohm.data

import android.content.SharedPreferences
import com.beatohm.ads.TagWriteCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TagWriteCounterLogicTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(mockEditor)

        // Inject mock prefs via reflection (TagWriteCounter is a singleton object)
        val prefsField = TagWriteCounter::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(TagWriteCounter, mockPrefs)
    }

    @Test
    fun `hasReachedLimit returns false when count is below MAX_FREE_WRITES`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(50)
        assertFalse(TagWriteCounter.hasReachedLimit())
    }

    @Test
    fun `hasReachedLimit returns true when count equals MAX_FREE_WRITES`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(100)
        assertTrue(TagWriteCounter.hasReachedLimit())
    }

    @Test
    fun `hasReachedLimit returns true when count exceeds MAX_FREE_WRITES`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(150)
        assertTrue(TagWriteCounter.hasReachedLimit())
    }

    @Test
    fun `increment returns new count`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(5)
        val result = TagWriteCounter.increment()
        assertEquals(6, result)
    }

    @Test
    fun `increment calls putInt on editor`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(5)
        TagWriteCounter.increment()
        verify(mockEditor).putInt("songs_written_count", 6)
        verify(mockEditor).apply()
    }

    @Test
    fun `getCount returns current count`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(42)
        assertEquals(42, TagWriteCounter.getCount())
    }

    @Test
    fun `getCount returns 0 when no count stored`() {
        `when`(mockPrefs.getInt("songs_written_count", 0)).thenReturn(0)
        assertEquals(0, TagWriteCounter.getCount())
    }

    @Test
    fun `MAX_FREE_WRITES is 100`() {
        assertEquals(100, TagWriteCounter.MAX_FREE_WRITES)
    }
}
