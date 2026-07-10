package com.dhanuk.govphoto_resizer.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhanuk.govphoto_resizer.data.local.GovPhotoDatabase
import com.dhanuk.govphoto_resizer.data.local.dao.PhotoHistoryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var db: GovPhotoDatabase
    private lateinit var dao: PhotoHistoryDao
    private lateinit var repo: HistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GovPhotoDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.photoHistoryDao()
        repo = HistoryRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordSave_inserts_and_observeRecent_returns_desc() = runTest {
        // recordSave stamps createdAt = System.currentTimeMillis().
        // Guarantee distinct (ascending) ms by sleeping between inserts.
        repo.recordSave(save(presetId = "p1"))
        Thread.sleep(5)
        repo.recordSave(save(presetId = "p2"))
        Thread.sleep(5)
        repo.recordSave(save(presetId = "p3"))

        val rows = repo.observeRecent(50).first()
        assertEquals(3, rows.size)
        assertEquals("p3", rows[0].presetId)
        assertEquals("p2", rows[1].presetId)
        assertEquals("p1", rows[2].presetId)
    }

    @Test
    fun search_finds_by_substring() = runTest {
        repo.recordSave(save(presetId = "aadhaar", examName = "Aadhaar Card"))
        repo.recordSave(save(presetId = "passport", examName = "Passport"))

        val results = repo.search("aadhaar").first()
        assertEquals(1, results.size)
        assertTrue(results[0].presetId.contains("aadhaar"))
    }

    private fun save(
        presetId: String,
        examName: String = "Exam"
    ) = HistoryRepository.HistorySave(
        presetId = presetId,
        examName = examName,
        originalImagePath = "/orig/$presetId.jpg",
        processedImagePath = "/proc/$presetId.jpg",
        fileSizeKb = 100,
        widthPx = 354,
        heightPx = 472
    )
}
