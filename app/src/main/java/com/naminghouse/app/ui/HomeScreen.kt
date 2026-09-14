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
import com.naminghouse.app.ui.theme.InkSpace
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
            .padding(horizontal = InkSpace.gutter),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s20),
    ) {
        // ── 머리 — 브랜드
        Row(
            Modifier.padding(top = InkSpace.s20, bottom = InkSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(InkSpace.s12))
            Column {
                Text(
                    "네이밍하우스",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "NAMING HOUSE  ·  이름에 담는 마음",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        NamingHero(vm.hasSavedInput, onStart = {
            vm.mode = AppMode.RECOMMEND
            nav.navigate(Routes.INPUT)
        })
        if (vm.hasSavedInput) {
            Text(lastInputSummary(vm), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionTitle("이름을 만나는 다른 방법")
        Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
            NamingToolTile("字", "한자 찾기", "정해둔 이름에\n좋은 뜻을 더해요", {
                vm.mode = AppMode.HANJA; nav.navigate(Routes.INPUT)
            }, Modifier.weight(1f))
            NamingToolTile("鑑", "이름 풀이", "이름에 담긴 뜻과\n조화를 살펴봐요", {
                vm.mode = AppMode.EVALUATE; nav.navigate(Routes.INPUT)
            }, Modifier.weight(1f))
        }
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
                    Spacer(Modifier.width(InkSpace.s12))
                    Text(
                        "자꾸 불러보고 싶은 이름을\n별을 눌러 간직해보세요",
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
                        Spacer(Modifier.width(InkSpace.s8))
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

        NamingTrustNote()

        // ── 형제 앱 — 사주를 더 깊이 보고 싶은 사람에게
        SamraBanner()

        Spacer(Modifier.height(InkSpace.s8))
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
            Spacer(Modifier.width(InkSpace.s12))
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
