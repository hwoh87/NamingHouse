package com.naminghouse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.Routes
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.gen.NameStat
import com.samramanshang.manseryeok.orrery.model.Gender
import java.time.LocalDate

/**
 * 인기 이름 순위 — 대법원 출생신고 통계로 해·성별 상위 이름을 훑는 구경 화면.
 *
 * 시도별 상위 20위 집계를 전국 합산한 근사치라 연속성이 보장되는 30위까지만 싣고
 * 아래에 출처와 한계를 밝힌다. 이름을 누르면 그 이름 그대로 한자 추천으로 이어진다 —
 * 구경이 곧 작명의 입구가 되게.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(vm: NamingViewModel, nav: NavHostController) {
    val stats = vm.nameStats
    val years = remember(stats) { stats.chartYears() }
    var gender by remember { mutableStateOf(vm.gender) }
    var year by remember(years) { mutableIntStateOf(years.firstOrNull() ?: 0) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "인기 이름 순위",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            // 앱바 뒤로 배경의 원산이 그대로 이어져야 해서 면을 깔지 않는다.
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            // 상태바 인셋은 Scaffold 가 이미 콘텐츠에 주었다 — 기본값대로 두면 이중 공백.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (years.isEmpty()) {
            Text(
                vm.loadError ?: "통계를 불러오는 중…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            return
        }

        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkSegmented(
                options = listOf(Gender.M, Gender.F),
                selected = gender,
                label = { if (it == Gender.M) "남아" else "여아" },
                onSelect = { gender = it },
            )
            InkSegmented(
                options = years,
                selected = year,
                label = { "$it" },
                onSelect = { year = it },
            )
            Text(
                "이름을 누르면 그 이름으로 한자 추천을 이어 갑니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val chart = remember(stats, gender, year) { stats.chart(gender, year) }
        val thisYear = remember { LocalDate.now().year }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            if (chart.isEmpty()) {
                item {
                    Text(
                        "이 해의 순위 자료가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    InkCard(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
                        spacing = 0.dp,
                    ) {
                        chart.forEachIndexed { i, (rank, stat) ->
                            RankRow(rank, stat, prevRank = stat.rankIn(year - 1)) {
                                vm.mode = AppMode.HANJA
                                vm.gender = gender
                                vm.givenName = stat.name
                                vm.givenHanja.value = emptyList()
                                nav.navigate(Routes.INPUT)
                            }
                            if (i < chart.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    buildString {
                        append(
                            "대법원 전자가족관계등록시스템 출생신고 통계 · 시도별 상위 이름을 " +
                                "전국 합산한 참고용 순위입니다. 남녀가 함께 쓰는 이름은 " +
                                "더 많이 쓰인 성별의 순위에만 나타납니다."
                        )
                        if (year == thisYear) append("\n${year}년은 연중 집계 중인 잠정치입니다.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 순위 한 줄 — 순위·이름·전년 대비. 1~3위만 금니로 살짝 올린다. */
@Composable
private fun RankRow(rank: Int, stat: NameStat, prevRank: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) InkTheme.colors.gold
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.width(36.dp),
        )
        Text(
            stat.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // 전년 순위가 있을 때만 증감을 단다 — 통계에 없던 해를 '신규'로 단정하지 않는다.
        when {
            prevRank == null -> {}
            prevRank > rank -> DeltaMark("▲${prevRank - rank}", InkTheme.colors.gil)
            prevRank < rank -> DeltaMark("▼${rank - prevRank}", InkTheme.colors.hyung)
            else -> DeltaMark("—", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DeltaMark(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color,
    )
}
