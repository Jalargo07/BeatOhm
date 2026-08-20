package com.beatohm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beatohm.metadata.MetadataCandidateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for Room DB: metadata_candidates entity and DAO operations.
 *
 * Verifies:
 * - Insert and retrieve MetadataCandidateEntity
 * - Status transitions (PENDING → APPLIED / SKIPPED)
 * - Invariant: max one PENDING per song (deletePendingBySongId + insert)
 * - deleteAllPending clears all PENDING records
 *
 * Requires Android instrumented test runner (androidTest).
 * Run with: connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MetadataCandidateRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MetadataCandidateDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.metadataCandidateDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveBySongId() = runBlocking {
        val entity = MetadataCandidateEntity(
            songId = "song_1",
            candidatesJson = "[]",
            status = MetadataCandidateEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis(),
            appliedAt = null
        )
        dao.insert(entity)

        val result = dao.getBySongId("song_1")
        assertEquals(1, result.size)
        assertEquals("song_1", result[0].songId)
        assertEquals(MetadataCandidateEntity.STATUS_PENDING, result[0].status)
    }

    @Test
    fun markApplied_setsAppliedAt() = runBlocking {
        val entity = MetadataCandidateEntity(
            songId = "song_2",
            candidatesJson = "[]",
            status = MetadataCandidateEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis(),
            appliedAt = null
        )
        val id = dao.insert(entity)
        val now = System.currentTimeMillis()
        dao.markApplied(id, now)

        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals(MetadataCandidateEntity.STATUS_APPLIED, result!!.status)
        assertEquals(now, result.appliedAt)
    }

    @Test
    fun markSkipped_setsStatusToSkipped() = runBlocking {
        val entity = MetadataCandidateEntity(
            songId = "song_3",
            candidatesJson = "[]",
            status = MetadataCandidateEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis(),
            appliedAt = null
        )
        val id = dao.insert(entity)
        dao.markSkipped(id)

        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals(MetadataCandidateEntity.STATUS_SKIPPED, result!!.status)
    }

    @Test
    fun deletePendingBySongId_removesOnlyPending() = runBlocking {
        // Insert APPLIED record
        val applied = MetadataCandidateEntity(
            songId = "song_4",
            candidatesJson = "[]",
            status = MetadataCandidateEntity.STATUS_APPLIED,
            createdAt = System.currentTimeMillis(),
            appliedAt = System.currentTimeMillis()
        )
        dao.insert(applied)

        // Insert PENDING record
        val pending = MetadataCandidateEntity(
            songId = "song_4",
            candidatesJson = "[]",
            status = MetadataCandidateEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis(),
            appliedAt = null
        )
        dao.insert(pending)

        // Delete PENDING
        dao.deletePendingBySongId("song_4")

        // APPLIED should remain
        val result = dao.getBySongId("song_4")
        assertEquals(1, result.size)
        assertEquals(MetadataCandidateEntity.STATUS_APPLIED, result[0].status)
    }

    @Test
    fun deleteAllPending_clearsAllPendingRecords() = runBlocking {
        dao.insert(MetadataCandidateEntity(songId = "s1", candidatesJson = "[]", status = MetadataCandidateEntity.STATUS_PENDING, createdAt = 1L, appliedAt = null))
        dao.insert(MetadataCandidateEntity(songId = "s2", candidatesJson = "[]", status = MetadataCandidateEntity.STATUS_PENDING, createdAt = 2L, appliedAt = null))
        dao.insert(MetadataCandidateEntity(songId = "s3", candidatesJson = "[]", status = MetadataCandidateEntity.STATUS_APPLIED, createdAt = 3L, appliedAt = 3L))

        dao.deleteAllPending()

        val pending = dao.getPendingSongIds()
        assertEquals(0, pending.size)
    }

    @Test
    fun getById_returnsNullForNonExistent() = runBlocking {
        assertNull(dao.getById(9999L))
    }
}
