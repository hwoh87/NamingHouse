package com.naminghouse.app.ads

import android.content.Context
import java.time.LocalDate

/** 광고 이벤트 횟수만 로컬 저장. 이름·출생정보·광고 식별자는 수집하지 않는다. */
class AdFrequency(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ad_frequency", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()
    private fun count(kind: String): Int = if (prefs.getString("${kind}_day", "") == today())
        prefs.getInt("${kind}_count", 0) else 0
    fun rewardAllowed() = AdPolicy.rewardAllowed(count("reward"), System.currentTimeMillis(), prefs.getLong("reward_last", 0))
    fun rewardMessage() = if (count("reward") >= AdPolicy.REWARD_DAILY_LIMIT)
        "오늘의 광고 열람 5회를 모두 사용했어요. 내일 다시 이용해 주세요." else "광고 사이에는 1분의 간격이 있어요. 잠시 후 다시 이용해 주세요."
    fun record(kind: String) {
        prefs.edit().putString("${kind}_day", today()).putInt("${kind}_count", count(kind) + 1)
            .putLong("${kind}_last", System.currentTimeMillis())
            .putLong("full_last", System.currentTimeMillis()).apply()
    }
    fun sessionStarted() { prefs.edit().putInt("sessions", prefs.getInt("sessions", 0) + 1).apply() }
    fun interstitialAllowed(adFree: Boolean, completed: Int, elapsed: Long) =
        AdPolicy.interstitialAllowed(adFree, prefs.getInt("sessions", 0), completed, elapsed,
            count("interstitial"), System.currentTimeMillis(), prefs.getLong("full_last", 0))
    fun suppressFullScreen() { prefs.edit().putLong("full_last", System.currentTimeMillis()).apply() }
}
