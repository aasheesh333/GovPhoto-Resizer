package com.dhanuk.govphoto.data.ads

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterstitialControllerTest {

    @Test fun `first save can't show`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 1)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `second save without cooldown can show`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 2)
        assertTrue(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `cooldown gate`() {
        val rl = RateLimiter(now = { 10_000 }, saveCount = 2, lastShowMs = 5_000, shownInSession = 1)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `per-session cap of 3`() {
        val rl = RateLimiter(now = { 999_999 }, saveCount = 2, lastShowMs = 0L, shownInSession = 3)
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }

    @Test fun `markShown advances state`() {
        val rl = RateLimiter(now = { 1_000 }, saveCount = 2)
        rl.markShown()
        // After a show, cooldown applies.
        assertFalse(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
        // 60s later, cap-2 still allows one more.
        rl.now = { 61_000 }; assertTrue(rl.canShow(minIntervalMs = 60_000, perSessionCap = 3, minSaveCount = 2))
    }
}
