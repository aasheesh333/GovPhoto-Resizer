package com.dhanuk.govphoto.data.subscription

import com.dhanuk.govphoto.data.datastore.CachedIsProStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriptionRepositoryTest {

    class FakeCachedStore : CachedIsProStore {
        var stored = false
        override suspend fun getCachedIsPro(): Boolean = stored
        override suspend fun setCachedIsPro(value: Boolean) { stored = value }
    }

    @Test fun `bind without Purchases configured keeps isPro false`() = runTest {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val repo = SubscriptionRepository(ctx, FakeCachedStore())
        repo.bind()  // no-op: Purchases not configured
        assertFalse(repo.isPro.value)
    }

    @Test fun `cached store round-trips`() = runTest {
        val fake = FakeCachedStore()
        fake.setCachedIsPro(true)
        assertEquals(true, fake.getCachedIsPro())
        fake.setCachedIsPro(false)
        assertEquals(false, fake.getCachedIsPro())
    }
}
