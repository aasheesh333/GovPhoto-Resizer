package com.dhanuk.govphoto_resizer.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import com.dhanuk.govphoto_resizer.data.local.entity.RecentPresetEntity
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
class RecentPresetDaoTest {

    private lateinit var db: GovPhotoDatabase
    private lateinit var dao: RecentPresetDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GovPhotoDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.recentPresetDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun preset(
        presetId: String,
        usedAt: Long = 0L,
        useCount: Int = 1
    ) = RecentPresetEntity(
        presetId = presetId,
        examName = "Exam",
        category = "doc",
        usedAt = usedAt,
        useCount = useCount
    )

    @Test
    fun upsert_then_bump_increments_useCount() = runTest {
        dao.upsert(preset("aadhaar-2x2", useCount = 1))
        dao.bumpUse("aadhaar-2x2", now = 5000L)

        val rows = dao.observeRecent(10).first()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].useCount)
        assertEquals(5000L, rows[0].usedAt)
    }

    @Test
    fun observeRecent_orders_by_usedAt_desc() = runTest {
        dao.upsert(preset("old", usedAt = 1000L))
        dao.upsert(preset("newest", usedAt = 3000L))
        dao.upsert(preset("mid", usedAt = 2000L))

        val rows = dao.observeRecent(10).first()
        assertEquals(3, rows.size)
        assertEquals("newest", rows[0].presetId)
        assertEquals("mid", rows[1].presetId)
        assertEquals("old", rows[2].presetId)
    }
}
