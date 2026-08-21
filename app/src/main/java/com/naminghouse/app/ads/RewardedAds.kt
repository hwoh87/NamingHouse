package com.naminghouse.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 감명서 한 편을 여는 보상형 광고.
 *
 * 한 편에 광고 하나 — 다 본 사람에게만 [show] 의 onReward 가 온다. 미리 받아 두지 않으면
 * 버튼을 누르고 몇 초를 기다리게 되므로 상세 화면에 들어올 때 [preload] 로 당겨 둔다.
 * 광고는 일회용이라 노출 뒤 곧바로 다음 편을 받아 둔다.
 *
 * 운영 단위가 아직 없으면([AdIds.rewardedUnitId] 가 null) 통째로 잠자며 [available] 이 false 다.
 */
object RewardedAds {

    private var ad: RewardedAd? = null
    private var loading = false

    private val _ready = MutableStateFlow(false)

    /** 지금 즉시 보여 줄 광고가 손에 있는가. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 이 빌드에서 보상형 광고를 쓸 수 있는가 — 운영 단위 미등록이면 false. */
    fun available(context: Context): Boolean = AdIds.rewardedUnitId(context) != null

    fun preload(context: Context) {
        if (loading || ad != null) return
        val unitId = AdIds.rewardedUnitId(context) ?: return
        if (!AdsInit.adsReady.value) return // 동의 전에는 요청하지 않는다
        loading = true
        RewardedAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loading = false
                    _ready.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // 조용히 접는다 — 다음 preload 가 다시 시도한다.
                    ad = null
                    loading = false
                    _ready.value = false
                }
            },
        )
    }

    /**
     * @param onReward 광고를 끝까지 본 경우에만 불린다(닫아 버리면 오지 않는다).
     * @param onUnavailable 아직 받아 둔 광고가 없을 때. 부르는 쪽이 안내하고 다시 받아 둔다.
     */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val loaded = ad
        if (loaded == null) {
            preload(activity)
            onUnavailable()
            return
        }
        // 다음 요청을 막지 않도록 손에서 먼저 놓는다 — 같은 광고를 두 번 보여 줄 수 없다.
        ad = null
        _ready.value = false
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preload(activity)
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                preload(activity)
                onUnavailable()
            }
        }
        loaded.show(activity) { onReward() }
    }
}
