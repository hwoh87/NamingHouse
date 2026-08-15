package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.R
import com.naminghouse.app.Routes
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkTheme
import com.samramanshang.manseryeok.orrery.model.Gender

/**
 * 홈 — 세 모드의 문패, 지난 작명, 담아둔 이름 미리보기.
 *
 * 이전에는 입력 화면이 곧 첫 화면이라 긴 폼이 인사를 대신했다.
 * 재방문 사용자는 '이어서 이름 짓기' 두 번의 탭으로 결과까지 간다.
 */
@Composable
fun HomeScreen(vm: NamingViewModel, nav: NavHostController) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 머리 — 브랜드
        Row(
            Modifier.padding(top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "작명하우스",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "사주와 성명학으로 이름을 짓습니다",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 지난 작명 — 저장된 입력이 있을 때만
        if (vm.hasSavedInput) {
            InkCard {
                SectionTitle("지난 작명")
                Text(
                    lastInputSummary(vm),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        vm.mode = AppMode.RECOMMEND
                        nav.navigate(Routes.INPUT)
                    },
                    shape = InkShape.medium,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(
                        "이어서 이름 짓기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── 모드 문패 셋
        ModeCard(
            glyph = "薦", title = "이름 추천",
            desc = "사주에 맞는 이름을 점수순으로 찾아 드립니다",
        ) { vm.mode = AppMode.RECOMMEND; nav.navigate(Routes.INPUT) }
        ModeCard(
            glyph = "字", title = "한자 추천",
            desc = "정해 둔 한글 이름에 좋은 한자 조합을 붙입니다",
        ) { vm.mode = AppMode.HANJA; nav.navigate(Routes.INPUT) }
        ModeCard(
            glyph = "鑑", title = "이름 감명",
            desc = "지어 둔 이름의 풀이와 점수를 봅니다",
        ) { vm.mode = AppMode.EVALUATE; nav.navigate(Routes.INPUT) }
        ModeCard(
            glyph = "譜", title = "인기 이름 순위",
            desc = "요즘 아기들이 많이 받는 이름을 순위로 봅니다",
        ) { nav.navigate(Routes.RANKING) }

        // ── 담아둔 이름 미리보기
        InkCard(onClick = {
            nav.navigate(Routes.FAVORITES) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }) {
            SectionTitle("담아둔 이름") {
                Text(
                    if (vm.favorites.isEmpty()) "" else "${vm.favorites.size} ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.favorites.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkArt(R.drawable.ink_plum, Modifier.size(72.dp), alpha = 0.4f)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "추천 목록에서 별을 누르면\n여기 모입니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                vm.favorites.take(3).forEach { f ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            f.fullHangul,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            f.hanja,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = HanjaFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "★ ${f.score}",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkTheme.colors.gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── 형제 앱 — 사주를 더 깊이 보고 싶은 사람에게
        SamraBanner()

        Spacer(Modifier.height(8.dp))
    }
}

/** 모드 문패 — 한자 한 글자가 문패, 옆에 하는 일 한 줄. */
@Composable
private fun ModeCard(glyph: String, title: String, desc: String, onClick: () -> Unit) {
    InkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    glyph,
                    fontFamily = HanjaFamily,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 저장된 입력 요약 한 줄 — "김(金) · 남아 · 양력 2026년 8월 11일 12:00 · 서울" */
private fun lastInputSummary(vm: NamingViewModel): String {
    val hanja = vm.surnameHanja.value.filterNotNull().joinToString("") { it.char.toString() }
    val surname = vm.surnameSyllables + if (hanja.isNotEmpty()) "($hanja)" else ""
    val gender = if (vm.gender == Gender.M) "남아" else "여아"
    val birth = if (vm.preBirth) {
        "출생 전 작명"
    } else {
        val cal = if (vm.isLunar) "음력" else "양력"
        val time = if (vm.unknownTime) "시간 모름"
        else "${vm.hour}:${vm.minute.padStart(2, '0')}"
        "$cal ${vm.year}년 ${vm.month}월 ${vm.day}일 $time · ${vm.city.name}"
    }
    return "$surname · $gender · $birth"
}
