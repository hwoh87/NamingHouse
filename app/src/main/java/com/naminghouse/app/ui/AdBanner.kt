package com.naminghouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ads.AdIds
import com.naminghouse.app.ads.AdsInit
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace

/**
 * 결과 목록 아래에 놓는 인라인 적응형 배너.
 *
 * **두 게이트를 모두 통과해야 뜬다:** 광고 제거를 산 사람이 아니어야 하고([NamingViewModel.isAdFree]),
 * 동의·SDK 초기화가 끝나 있어야 한다([AdsInit.adsReady]). 둘 중 하나라도 아니면 자리도 차지하지 않는다
 * — 빈 상자가 잠깐 떴다 사라지면 목록이 튄다.
 *
 * 광고를 목록 '사이'가 아니라 맨 끝에만 두는 건 의도다. 이름을 고르는 흐름을 끊지 않는다.
 */
@Composable
fun ResultListAd(vm: NamingViewModel, modifier: Modifier = Modifier) {
    val adsReady by AdsInit.adsReady.collectAsState()
    if (vm.isAdFree || !adsReady) return

    Column(modifier.fillMaxWidth().padding(top = InkSpace.s8)) {
        // 광고임을 먼저 밝힌다 — 이름 카드와 같은 종이 위에 있어 오인 여지가 있다.
        Text(
            "광고",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(InkSpace.s4))
        AdViewHost(
            Modifier
                .fillMaxWidth()
                .clip(InkShape.medium)
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}

/**
 * 실제 가용 폭으로 앵커 적응형 크기를 잡는다. 폭이 바뀌면(회전·폴드 펼침·폰트 스케일)
 * [key] 로 AdView 를 새로 만든다 — setAdSize 는 생성 후 갈아끼울 수 없다.
 */
@Composable
private fun AdViewHost(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        key(widthDp) {
            BannerAdView(widthDp, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BannerAdView(widthDp: Int, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val adView = remember {
        AdView(context).apply {
            adUnitId = AdIds.bannerUnitId(context)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    LaunchedEffect(adView) { adView.loadAd(AdRequest.Builder().build()) }

    AndroidView(factory = { adView }, modifier = modifier)
}
