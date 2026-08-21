package com.naminghouse.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ads.RewardedAds
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.eval.NameEvaluation

/**
 * 잠긴 대목 — 가려진 줄 수와 '무엇이 가려졌는지'만 보여 준다.
 *
 * 진짜 값을 그려 놓고 흐리기만 하면 minSdk 24 에서는 [blur] 가 통째로 무시돼 그대로 읽히고,
 * API 31 이상에서도 화면낭독기에는 원문이 그대로 나간다. 그래서 여기서 그리는 건 **내용이 없는
 * 뼈대 줄**이고, 블러는 그 뼈대를 종이 뒤로 밀어 넣는 장식일 뿐이다.
 *
 * [teaser] 는 지어낸 미리보기가 아니라 실제로 센 값이어야 한다("주의할 글자 1자") —
 * 없는 것을 있는 척하면 결제 후에 배신감이 남는다.
 */
@Composable
fun LockedBlock(
    teaser: String,
    lines: Int = 3,
    onUnlock: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            // 뼈대가 한 줄뿐이면 상자가 안내문보다 낮아져 글자가 잘린다 — 바닥을 깔아 둔다.
            .heightIn(min = 44.dp)
            .clip(InkShape.medium)
            .clickable(onClick = onUnlock)
    ) {
        SkeletonLines(
            count = lines.coerceAtLeast(2),
            modifier = Modifier.blur(6.dp).padding(vertical = InkSpace.s8),
        )
        Row(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = InkSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = InkTheme.colors.gold,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(InkSpace.s8))
            Text(
                teaser,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(InkSpace.s8))
            Text(
                "열기",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 글이 있던 자리 — 폭을 들쭉날쭉하게 두어야 문단으로 읽힌다. */
@Composable
private fun SkeletonLines(count: Int, modifier: Modifier) {
    val widths = listOf(0.92f, 0.74f, 0.85f, 0.62f, 0.8f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .fillMaxWidth(widths[i % widths.size])
                    .height(11.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f),
                        InkShape.small,
                    )
            )
        }
    }
}

/**
 * 잠긴 감명서를 여는 세 갈래 — 무료 1회 · 광고 · 프리미엄.
 *
 * 무료 1회를 자동으로 태우지 않고 여기서 고르게 두는 게 핵심이다. 사용자가 마음에 든
 * 이름에 쓰게 해야 '한 편은 정말 다 보여 준다'는 약속이 지켜진 걸로 읽힌다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockSheet(vm: NamingViewModel, eval: NameEvaluation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val price by vm.premium.priceText.collectAsState()
    val adReady by RewardedAds.ready.collectAsState()
    val adUsable = activity != null && RewardedAds.available(context)
    val name = eval.surname + eval.givenName

    // 열리는 순간 시트는 스스로 닫힌다 — 광고를 다 본 뒤 손이 한 번 덜 간다.
    LaunchedEffect(vm.isUnlocked(eval)) {
        if (vm.isUnlocked(eval)) onDismiss()
    }
    LaunchedEffect(Unit) { RewardedAds.preload(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpace.s24)
                .padding(bottom = InkSpace.s28),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s16),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$name 감명서 전부 보기",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "총평 풀이 · 수리사격 4격 · 사주 보완 · 글자 풀이 · 불용한자",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SealBadge(main = "開", sub = "열람")
            }

            InkStroke(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                alpha = 0.25f,
            )

            if (!vm.freeUnlockUsed) {
                Button(
                    onClick = { vm.claimFreeUnlock(eval) },
                    shape = InkShape.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        "이 이름 하나 무료로 열기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "무료 공개는 한 이름에만 씁니다 — 마음에 드는 이름에 쓰세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (adUsable) {
                OutlinedButton(
                    onClick = {
                        RewardedAds.show(
                            activity = activity,
                            onReward = { vm.grantAdUnlock(eval) },
                            onUnavailable = {
                                vm.premium.message.value = "광고를 준비하는 중입니다 — 잠시 후 다시 시도해 주세요"
                            },
                        )
                    },
                    shape = InkShape.medium,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(InkSpace.s8))
                    Text(
                        if (adReady) "광고 보고 이 이름 열기" else "광고 준비 중",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            InkStroke(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                alpha = 0.12f,
            )

            Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s4)) {
                Text(
                    "프리미엄 감명서",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "한 번 사면 이름 수 제한 없이 전부 열립니다 — 인쇄급 PDF·낙관 각인·표구 두 종·광고 제거까지.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { activity?.let { vm.premium.launchPurchase(it) } },
                shape = InkShape.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    price?.let { "$it · 모든 이름 열기" } ?: "모든 이름 열기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.premium.restore() }) {
                    Text("구매 복원", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "결제는 Google Play 가 처리합니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
