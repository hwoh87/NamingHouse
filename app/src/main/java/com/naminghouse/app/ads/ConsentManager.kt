package com.naminghouse.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * GDPR/UMP 동의 흐름 래퍼.
 *
 * EEA·영국 사용자에게만 동의 폼이 뜨고 그 밖(한국 등)에선 사실상 no-op 이라,
 * 한국 단독 출시에도 안전하면서 해외까지 함께 커버한다.
 *
 * **순서 규약: 동의 수집(또는 canRequestAds() 충족) 전에 첫 광고 요청 금지.**
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    /**
     * 동의 정보를 갱신하고 필요하면 폼을 띄운다.
     *
     * @param onCanRequestAds 광고 요청이 허용된 시점에 호출(동의 완료 또는 비대상 지역).
     *   멱등 가드가 있는 [AdsInit.ensureInitialized] 를 넘기므로 중복 호출은 무해하다.
     */
    fun gatherConsent(activity: Activity, onCanRequestAds: () -> Unit) {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "consent form error: ${formError.message}")
                    if (consentInfo.canRequestAds()) onCanRequestAds()
                }
            },
            { requestError ->
                // 갱신 실패 — 이미 동의했거나 비대상이면 canRequestAds() 가 참일 수 있다.
                Log.w(TAG, "consent info update failed: ${requestError.message}")
                if (consentInfo.canRequestAds()) onCanRequestAds()
            },
        )
        // 재방문 빠른 경로 — 네트워크 갱신을 기다리지 않는다. 첫 실행엔 false 라 조기 발화하지 않는다.
        if (consentInfo.canRequestAds()) onCanRequestAds()
    }
}
