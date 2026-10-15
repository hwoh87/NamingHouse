package com.naminghouse.app.ui

import android.app.Activity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
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
 * 이름을 짓고 점수를 보는 데까지는 무료다 — 파는 것은 **풀이의 전량 공개**와
 * 소장품(인쇄급 PDF·낙관 각인·표구)이다. 이름 하나만 열고 싶은 사람에게는
 * [UnlockSheet] 쪽에 무료 1회와 광고 해제가 따로 있다.
 *
 * 가격은 반드시 Play 가 준 현지 표시 가격을 쓴다(콘솔 등록 전이나 오프라인이면
 * 글자 없이 버튼만).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheet(vm: NamingViewModel, onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val price by vm.premium.priceText.collectAsState()
    val busy by vm.premium.purchaseBusy.collectAsState()
    LaunchedEffect(Unit) { vm.premium.refreshProducts(reportFailure = false) }

    // 결제가 끝나 소유가 켜지면 시트는 스스로 닫힌다.
    LaunchedEffect(vm.isPremium) {
        if (vm.isPremium) onDismiss()
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = InkSpace.s24)
                .padding(bottom = InkSpace.s28),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s16),
        ) {
            SceneHeading("NAMING HOUSE  ·  PREMIUM", "이름에 담은 마음을,\n오래 간직하세요", "모든 이름의 깊은 풀이와 소장용 감명서. 한 번 결제로 이용해요.")
            InkCard(spacing = InkSpace.s20) {
                FeatureLine("모든 이름의 전체 풀이", "총평 · 수리사격 · 사주 보완 · 글자 풀이를 이름 수 제한 없이")
                FeatureLine("나만의 감명서 소장", "고해상도 PDF 저장 · 이름 낙관 각인 · 감지금니와 다갈 표구")
                FeatureLine("광고 없이, 다음 이름까지", "자동 광고 제거 포함 · 자동 갱신이나 재구매 없이")
            }
            Spacer(Modifier.height(InkSpace.s2))

            Button(
                onClick = { activity?.let { vm.premium.launchPurchase(it) } },
                enabled = price != null && !busy && activity != null,
                shape = InkShape.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    price?.let { "$it · 프리미엄 열기" } ?: "가격을 확인하고 있습니다",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (price == null) {
                TextButton(onClick = { vm.premium.refreshProducts() }) { Text("가격 다시 불러오기") }
            }
            Text("1회 결제 · 자동 갱신 없음", style = MaterialTheme.typography.labelMedium)
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
                .padding(top = InkSpace.s8)
                .size(6.dp)
                .background(InkTheme.colors.gold, InkShape.circle)
        )
        Spacer(Modifier.width(InkSpace.s12))
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
