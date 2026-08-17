package com.naminghouse.app.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mobile Ads SDK 초기화를 1회만 보장하고 '광고 요청해도 되는가' 신호를 연다.
 *
 * **동의 수집 뒤에 불러야 한다**(UMP/GDPR: 동의 전 광고 요청 금지).
 * 실제 호출 지점은 [ConsentManager.gatherConsent] 의 콜백이다.
 */
object AdsInit {
    private val started = AtomicBoolean(false)
    private val _adsReady = MutableStateFlow(false)

    /** 초기화 완료 여부 — 배너가 이걸 보고 로드 시점을 잡는다. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext) {
            _adsReady.value = true
        }
    }
}
