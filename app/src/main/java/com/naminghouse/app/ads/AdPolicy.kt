package com.naminghouse.app.ads

/** 보수적인 출시 기본값. 날짜별 횟수는 앱 재시작 후에도 유지한다. */
object AdPolicy {
    const val REWARD_DAILY_LIMIT = 5
    const val REWARD_GAP_MS = 60_000L
    const val INTERSTITIAL_DAILY_LIMIT = 2
    const val INTERSTITIAL_GAP_MS = 600_000L
    const val FIRST_SESSION_GRACE_MS = 180_000L
    const val CACHE_TTL_MS = 3_300_000L

    fun rewardAllowed(count: Int, now: Long, last: Long): Boolean =
        count < REWARD_DAILY_LIMIT && (last == 0L || now - last >= REWARD_GAP_MS)

    fun interstitialAllowed(adFree: Boolean, sessions: Int, completed: Int,
        elapsed: Long, count: Int, now: Long, last: Long): Boolean =
        !adFree && sessions >= 2 && completed >= 3 && elapsed >= FIRST_SESSION_GRACE_MS &&
            count < INTERSTITIAL_DAILY_LIMIT && (last == 0L || now - last >= INTERSTITIAL_GAP_MS)
}
