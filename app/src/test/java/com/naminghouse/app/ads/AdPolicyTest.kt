package com.naminghouse.app.ads

import org.junit.Assert.*
import org.junit.Test

class AdPolicyTest {
    @Test fun rewardsRespectDailyCapAndGap() {
        assertTrue(AdPolicy.rewardAllowed(0, 1000, 0))
        assertFalse(AdPolicy.rewardAllowed(5, 1000000, 0))
        assertFalse(AdPolicy.rewardAllowed(1, 60999, 1000))
        assertTrue(AdPolicy.rewardAllowed(4, 61000, 1000))
        assertFalse(AdPolicy.rewardAllowed(1, 100, 1000))
    }
    @Test fun interstitialRequiresEveryGuard() {
        fun allowed(adFree: Boolean = false, sessions: Int = 2, completed: Int = 3,
            elapsed: Long = 180000, count: Int = 0, now: Long = 1000000, last: Long = 0) =
            AdPolicy.interstitialAllowed(adFree, sessions, completed, elapsed, count, now, last)
        assertTrue(allowed())
        assertFalse(allowed(adFree = true))
        assertFalse(allowed(sessions = 1))
        assertFalse(allowed(completed = 2))
        assertFalse(allowed(elapsed = 179999))
        assertFalse(allowed(count = 2))
        assertFalse(allowed(last = 400001))
        assertTrue(allowed(last = 400000))
    }
}
