package com.dhanuk.govphoto.data.push

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PushRepositoryTest {

    class FakeStore : PushCategoryStore {
        private val map = PushCategory.entries.associateBy { it }.mapValues { it.value.defaultEnabled }.toMutableMap()
        override suspend fun isEnabled(category: PushCategory): Boolean = map[category] ?: category.defaultEnabled
        override suspend fun setEnabled(category: PushCategory, enabled: Boolean) { map[category] = enabled }
    }

    @Test fun `defaults match spec`() {
        assertEquals(true, PushCategory.RELEASE_NOTES.defaultEnabled)
        assertEquals(false, PushCategory.EXAM_DEADLINES.defaultEnabled)
        assertEquals(true, PushCategory.SUPPORT_REPLIES.defaultEnabled)
    }

    @Test fun `toggle persists to fake store`() = runTest {
        val fake = FakeStore()
        assertTrue(fake.isEnabled(PushCategory.RELEASE_NOTES))
        fake.setEnabled(PushCategory.EXAM_DEADLINES, true)
        assertTrue(fake.isEnabled(PushCategory.EXAM_DEADLINES))
        fake.setEnabled(PushCategory.RELEASE_NOTES, false)
        assertFalse(fake.isEnabled(PushCategory.RELEASE_NOTES))
    }
}
