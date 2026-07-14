package com.dhanuk.govphoto_resizer.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhanuk.govphoto_resizer.data.local.GovPhotoDatabase
import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentPresetRepositoryTest {

    private lateinit var db: GovPhotoDatabase
    private lateinit var dao: RecentPresetDao
    private lateinit var repo: RecentPresetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GovPhotoDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.recentPresetDao()
        repo = RecentPresetRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordUse_inserts_then_bumps() = runTest {
        // First recordUse: row does not exist → upsert IGNORE inserts → useCount = 1.
        repo.recordUse("aadhaar", "Aadhaar Card", "IDENTITY_CARDS")

        var rows = repo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].useCount)
        assertEquals("Aadhaar Card", rows[0].examName)
        assertEquals("IDENTITY_CARDS", rows[0].category)

        // Second recordUse: row exists → upsert is ignored (returns -1) → bumpUse increments.
        repo.recordUse("aadhaar", "Aadhaar Card", "IDENTITY_CARDS")

        rows = repo.observeRecent(50).first()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].useCount)
    }
}
