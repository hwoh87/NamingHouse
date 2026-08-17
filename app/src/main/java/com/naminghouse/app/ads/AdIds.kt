package com.naminghouse.app.ads

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 광고 단위 ID 중앙 제공.
 *
 * 디버그 빌드는 **Google 공식 테스트 단위 ID** 를 돌려준다 — 개발 중 실 단위로 노출·클릭하면
 * AdMob 정책 위반(계정 정지)이라 여기서 갈라 둔다. 릴리즈만 운영 단위를 쓴다.
 *
 * 앱 ID 는 코드가 아니라 AndroidManifest 의 `com.google.android.gms.ads.APPLICATION_ID` 에 있다.
 */
object AdIds {
    // https://developers.google.com/admob/android/test-ads
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"

    /** AdMob 콘솔의 '결과목록 하단 배너' (pub-1799447209671601). */
    private const val PROD_BANNER = "ca-app-pub-1799447209671601/7459204066"

    fun bannerUnitId(context: Context): String =
        if (isDebuggable(context)) TEST_BANNER else PROD_BANNER

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
