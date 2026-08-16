package com.naminghouse.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme

/**
 * 프리미엄 감명서 구매 시트.
 *
 * 핵심 기능은 전부 무료라는 것이 이 앱의 자리다 — 여기서 파는 것은 기능이
 * 아니라 소장품(인쇄급 PDF·낙관 각인·표구)이다. 가격은 반드시 Play 가 준
 * 현지 표시 가격을 쓴다(콘솔 등록 전이나 오프라인이면 글자 없이 버튼만).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheet(vm: NamingViewModel, onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val price by vm.premium.priceText.collectAsState()

    // 결제가 끝나 소유가 켜지면 시트는 스스로 닫힌다.
    LaunchedEffect(vm.isPremium) {
        if (vm.isPremium) onDismiss()
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "프리미엄 감명서",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "지어 준 이름을 평생 소장품으로",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SealBadge(main = "藏", sub = "소장")
            }

            InkStroke(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                alpha = 0.25f,
            )

            FeatureLine("인쇄급 PDF 저장", "감명서 족자를 고해상도 PDF 로 — 인화·액자·출생 기록용")
            FeatureLine("낙관에 이름 각인", "족자 낙관 자리에 아이 이름이 전각 도장으로 찍힙니다")
            FeatureLine("표구 두 종 추가", "감지금니·다갈 — 족자의 종이와 먹빛을 바꿉니다")
            FeatureLine("한 번으로 평생", "재구매 없음 — 둘째, 셋째 이름도 그대로 씁니다")

            Spacer(Modifier.height(InkSpace.s2))

            Button(
                onClick = { activity?.let { vm.premium.launchPurchase(it) } },
                shape = InkShape.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    price?.let { "$it · 프리미엄 열기" } ?: "프리미엄 열기",
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

@Composable
private fun FeatureLine(title: String, desc: String) {
    Row {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(InkTheme.colors.gold, InkShape.circle)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
