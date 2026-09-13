package com.naminghouse.app.ads

import android.app.Activity
import android.os.SystemClock
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*

/** 결과를 읽고 명시적으로 '다른 조건으로 찾기'를 선택할 때만. 운영 ID 없으면 비활성. */
object InterstitialAds {
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var showing = false
    private var started = 0L
    private var completed = 0
    fun startSession(activity: Activity) {
        if (started != 0L) return
        started = SystemClock.elapsedRealtime()
        AdFrequency(activity).sessionStarted()
    }
    fun preload(activity: Activity, adFree: Boolean) {
        val id = AdIds.interstitialUnitId(activity) ?: return
        if (adFree || !AdsInit.adsReady.value || loading || showing) return
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt < AdPolicy.CACHE_TTL_MS) return
        ad = null
        loading = true
        InterstitialAd.load(activity.applicationContext, id, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(value: InterstitialAd) {
                loading = false; ad = value; loadedAt = SystemClock.elapsedRealtime()
            }
            override fun onAdFailedToLoad(error: LoadAdError) { loading = false; ad = null }
        })
    }
    fun finishResults(activity: Activity, adFree: Boolean, onContinue: () -> Unit) {
        if (showing) return
        completed++
        val frequency = AdFrequency(activity)
        val loaded = ad
        if (loaded == null || !AdsInit.adsReady.value || activity.isFinishing || activity.isDestroyed ||
            SystemClock.elapsedRealtime() - loadedAt >= AdPolicy.CACHE_TTL_MS ||
            !frequency.interstitialAllowed(adFree, completed, SystemClock.elapsedRealtime() - started)) {
            onContinue(); return
        }
        ad = null
        showing = true
        var continued = false
        fun finish() { if (!continued) { continued = true; showing = false; onContinue() } }
        loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { frequency.record("interstitial"); completed = 0 }
            override fun onAdDismissedFullScreenContent() = finish()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = finish()
        }
        loaded.show(activity)
    }
}
