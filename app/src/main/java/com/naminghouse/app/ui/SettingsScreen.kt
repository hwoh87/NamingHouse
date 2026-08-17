package com.naminghouse.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.Routes
import com.naminghouse.app.ThemeMode
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.oheng.BaleumSchool

/**
 * 설정 — 작명 기준과 앱 정보.
 *
 * 성명학 학파는 입력 화면에 묻혀 있던 것을 이리로 옮겼다(입력이 아니라 기준이다).
 * 프리미엄·약관 줄은 결제를 붙일 때 채워질 자리를 먼저 파 둔 것이다 —
 * 스토어 심사는 약관·개인정보 처리방침·구매 복원을 요구한다.
 */
@Composable
fun SettingsScreen(vm: NamingViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    val price by vm.premium.priceText.collectAsState()
    val adFreePrice by vm.premium.adFreePriceText.collectAsState()
    val activity = context as? Activity

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = InkSpace.gutter),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s12),
    ) {
        Text(
            "설정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = InkSpace.s16),
        )

        InkCard {
            SectionTitle("작명 기준")
            Text(
                "발음오행 학파",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InkSegmented(
                options = BaleumSchool.entries.toList(),
                selected = vm.school,
                label = { it.label },
                onSelect = { vm.onSchoolChanged(it) },
            )
            Text(
                "운해본: ㅇㅎ=토(작명 실무 다수설) · 해례본: ㅇㅎ=수(훈민정음 원전)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(InkSpace.s2))
            Text(
                "화면 모드",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InkSegmented(
                options = ThemeMode.entries.toList(),
                selected = vm.themeMode,
                label = { it.label },
                onSelect = { vm.onThemeModeChanged(it) },
            )
        }

        InkCard {
            SectionTitle("프리미엄") {
                if (vm.isPremium) {
                    Surface(
                        shape = InkShape.circle,
                        color = InkTheme.colors.goldSoft,
                    ) {
                        Text(
                            "이용 중",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTheme.colors.gold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = InkSpace.s8, vertical = InkSpace.s2),
                        )
                    }
                }
            }
            SettingsRow(
                "프리미엄 감명서 · PDF 저장",
                value = if (vm.isPremium) "이용 중" else price,
                onClick = {
                    if (vm.isPremium) {
                        Toast.makeText(
                            context,
                            "이름 상세 화면에서 PDF 저장·낙관 각인·표구를 쓸 수 있습니다",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        showPremium = true
                    }
                },
            )
            // 프리미엄이 광고 제거를 포함하므로, 이미 산 사람에게 이 줄은 파는 줄이 아니라
            // 상태를 알리는 줄이다. 눌러도 결제가 열리지 않는다.
            SettingsRow(
                "광고 제거",
                value = when {
                    vm.isAdFree -> "적용 중"
                    else -> adFreePrice
                },
                onClick = if (vm.isAdFree) {
                    null
                } else {
                    { activity?.let { vm.premium.launchAdFreePurchase(it) } }
                },
            )
            SettingsRow("구매 복원", onClick = { vm.premium.restore() })
        }

        InkCard {
            SectionTitle("정보")
            SettingsRow("이용약관", onClick = { nav.navigate(Routes.TERMS) })
            SettingsRow("개인정보 처리방침", onClick = { nav.navigate(Routes.PRIVACY) })
            SettingsRow("글꼴·오픈소스 라이선스", onClick = { showLicenses = true })
            SettingsRow("버전", value = appVersion(), onClick = null)
        }

        SamraBanner(
            subtitle = "같은 곳에서 만든 사주 만세력 앱입니다. " +
                "이 앱의 사주 계산이 여기서 왔습니다.",
        )

        Text(
            "만든 곳 · 삼라만상",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = InkSpace.s8),
        )
        Spacer(Modifier.height(InkSpace.s16))
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
    if (showPremium) {
        PremiumSheet(vm, onDismiss = { showPremium = false })
    }
}

@Composable
private fun SettingsRow(label: String, value: String? = null, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InkShape.medium)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = InkSpace.s8, horizontal = InkSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }
}

/** 번들 글꼴 고지 — 마루부리는 출처 표기를 권장하는 라이선스라 여기 명시한다. */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = InkShape.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier.padding(horizontal = InkSpace.gutter, vertical = InkSpace.s20),
                verticalArrangement = Arrangement.spacedBy(InkSpace.s12),
            ) {
                Text(
                    "글꼴·오픈소스 라이선스",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                InkStroke(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, alpha = 0.25f)
                LicenseItem(
                    "마루 부리",
                    "© 네이버문화재단. 이 앱에는 네이버에서 제공한 마루 부리 글꼴이 적용되어 있습니다. " +
                        "네이버 글꼴 라이선스에 따라 자유롭게 사용·수정·재배포할 수 있습니다.",
                )
                LicenseItem(
                    "Noto Serif KR",
                    "© Google · Adobe. SIL Open Font License 1.1. 한자 표기에 씁니다.",
                )
                LicenseItem(
                    "Pretendard",
                    "© 길형진 (orioncactus). SIL Open Font License 1.1. 본문 글꼴로 씁니다.",
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}

@Composable
private fun LicenseItem(name: String, text: String) {
    Column {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
