package com.dhanuk.govphoto.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhanuk.govphoto.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto.data.local.entity.PhotoHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoHistoryDaoTest {

    private lateinit var db: GovPhotoDatabase
    private lateinit var dao: PhotoHistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GovPhotoDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.photoHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        presetId: String,
        examName: String = "Exam",
        createdAt: Long
    ) = PhotoHistoryEntity(
        presetId = presetId,
        examName = examName,
        originalImagePath = "/orig/$presetId.jpg",
        processedImagePath = "/proc/$presetId.jpg",
        fileSizeKb = 100,
        widthPx = 354,
        heightPx = 472,
        createdAt = createdAt
    )

    @Test
    fun insert_persists_and_observesRecent_returns_desc() = runTest {
        dao.insert(entity("p1", createdAt = 1000L))
        dao.insert(entity("p2", createdAt = 2000L))
        dao.insert(entity("p3", createdAt = 3000L))

        val rows = dao.observeRecent(50).first()
        assertEquals(3, rows.size)
        assertEquals("p3", rows[0].presetId)
        assertEquals("p2", rows[1].presetId)
        assertEquals("p1", rows[2].presetId)
    }

    @Test
    fun search_finds_by_presetId_substring() = runTest {
        dao.insert(entity("Aadhaar Card", examName = "UIDAI", createdAt = 1000L))
        dao.insert(entity("Passport", examName = "MEA", createdAt = 2000L))

        val results = dao.search("aadhaar").first()
        assertEquals(1, results.size)
        assertTrue(results[0].presetId.contains("Aadhaar Card"))
    }

    @Test
    fun delete_removes_row() = runTest {
        val item = entity("p1", createdAt = 1000L)
        dao.insert(item)
        assertEquals(1, dao.count())

        val inserted = dao.observeRecent(50).first()[0]
        dao.delete(inserted)
        assertEquals(0, dao.count())
        assertNull(dao.getById(inserted.id))
    }
}
