package com.dhanuk.govphoto.data.ads

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rate-limiter policy for RewardedAdController mirrors InterstitialController:
 * every save eligible, 2-minute cooldown, per-session cap of 5.
 */
class RewardedAdControllerTest {

    private val policy = Triple(120_000L, 5, 1)

    private fun RateLimiter.canShowPolicy() = canShow(policy.first, policy.second, policy.third)

    @Test fun `first save is eligible`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 1)
        assertTrue(rl.canShowPolicy())
    }

    @Test fun `no saves blocked`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 0)
        assertFalse(rl.canShowPolicy())
    }

    @Test fun `cooldown gate within 2 min`() {
        val rl = RateLimiter(now = { 10_000 }, saveCount = 1, lastShowMs = 5_000, shownInSession = 1)
        assertFalse(rl.canShowPolicy())
    }

    @Test fun `cooldown opens after 2 min`() {
        val rl = RateLimiter(now = { 125_000 }, saveCount = 1, lastShowMs = 5_000, shownInSession = 1)
        assertTrue(rl.canShowPolicy())
    }

    @Test fun `per-session cap of 5`() {
        val rl = RateLimiter(now = { 999_999 }, saveCount = 1, lastShowMs = 0L, shownInSession = 5)
        assertFalse(rl.canShowPolicy())
    }

    @Test fun `markShown advances state`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 1)
        rl.markShown()
        assertFalse(rl.canShowPolicy())
        rl.now = { 121_000 }; assertTrue(rl.canShowPolicy())
    }
}