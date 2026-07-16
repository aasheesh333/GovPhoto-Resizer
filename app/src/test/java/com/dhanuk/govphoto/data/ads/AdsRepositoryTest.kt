package com.dhanuk.govphoto.data.ads

import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AdsRepositoryTest {

    private fun provider(isPro: Boolean, adFreeUntilMs: Long, forceNoAds: Boolean) =
        object : AdStateProvider {
            override val isPro = isPro
            override val adFreeUntilMs = adFreeUntilMs
            override val forceNoAds = forceNoAds
        }

    @Test fun `free user with no reward shows ads`() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=0L, forceNoAds=false))
        assertFalse(repo.isAdFree.value)
    }

    @Test fun `pro user is ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = AdsRepository(ctx, provider(isPro=true, adFreeUntilMs=0L, forceNoAds=false))
        assertTrue(repo.isAdFree.value)
    }

    @Test fun `reward timestamp in future is ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val future = System.currentTimeMillis() + 60_000L
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=future, forceNoAds=false))
        assertTrue(repo.isAdFree.value)
    }

    @Test fun `expired reward is not ad-free`() {
        val ctx = RuntimeEnvironment.getApplication()
        val past = System.currentTimeMillis() - 1_000L
        val repo = AdsRepository(ctx, provider(isPro=false, adFreeUntilMs=past, forceNoAds=false))
        assertFalse(repo.isAdFree.value)
    }
}
