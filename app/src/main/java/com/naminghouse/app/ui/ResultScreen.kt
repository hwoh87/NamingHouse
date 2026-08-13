package com.naminghouse.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.Routes
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.eval.AxisVerdict
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.util.TimezoneUtils
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 간지 한자 → 한글 독음 (丙午 → 병오). 삼라 상수의 천간·지지 순서를 그대로 쓴다. */
private fun ganziHangul(ganzi: String): String = buildString {
    ganzi.forEach { ch ->
        val si = SajuConstants.SKY.indexOf(ch)
        val ei = SajuConstants.EARTH.indexOf(ch)
        append(
            when {
                si >= 0 -> SajuConstants.SKY_KR[si]
                ei >= 0 -> SajuConstants.EARTH_KR[ei]
                else -> ch
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: NamingViewModel, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when (vm.mode) {
                        AppMode.RECOMMEND -> "추천 결과"
                        AppMode.HANJA -> "한자 조합"
                        AppMode.EVALUATE -> "감명 결과"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                FavoritesAction(count = vm.favorites.size) {
                    nav.navigate(Routes.FAVORITES) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            // 앱바 뒤로 배경의 원산이 그대로 이어져야 해서 면을 깔지 않는다.
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            vm.saju?.let { saju ->
                item { SajuCard(saju) }
            }

            when (vm.mode) {
                AppMode.RECOMMEND -> {
                    item {
                        ListCaption(
                            "추천 이름 ${vm.candidates.size}개",
                            "수리사격 · 발음오행 · 수리오행 · 자원오행 · 음양을 종합해 점수순으로 놓았습니다",
                        )
                    }
                    itemsIndexed(vm.candidates) { i, cand ->
                        CandidateRow(
                            rank = i + 1,
                            eval = cand.evaluation,
                            stat = cand.stat,
                            isFavorite = vm.isFavorite(cand.evaluation),
                            onToggleFavorite = { vm.toggleFavorite(cand.evaluation) },
                            onClick = {
                                vm.selected = cand.evaluation to cand.stat
                                nav.navigate(Routes.DETAIL)
                            },
                            modifier = Modifier.staggerIn(vm.resultsShownAt, i),
                        )
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }

                AppMode.HANJA -> {
                    item {
                        ListCaption(
                            "'${vm.surnameSyllables}${vm.givenNameSyllables}' 한자 조합 ${vm.hanjaCombos.size}개",
                            "같은 소리에 붙일 수 있는 인명용 한자를 점수순으로 놓았습니다",
                        )
                    }
                    itemsIndexed(vm.hanjaCombos) { i, eval ->
                        CandidateRow(
                            rank = i + 1,
                            eval = eval,
                            stat = vm.nameStats[eval.givenName],
                            emphasizeHanja = true,
                            isFavorite = vm.isFavorite(eval),
                            onToggleFavorite = { vm.toggleFavorite(eval) },
                            onClick = {
                                vm.selected = eval to vm.nameStats[eval.givenName]
                                nav.navigate(Routes.DETAIL)
                            },
                            modifier = Modifier.staggerIn(vm.resultsShownAt, i),
                        )
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }

                // 감명은 목록 없이 상세로 바로 간다 — 이 화면에 올 일이 없다.
                AppMode.EVALUATE -> Unit
            }
        }
    }
}

/**
 * 결과가 갓 나온 직후에만 위에서 아래로 스며들 듯 등장시킨다.
 * 스크롤로 다시 올라올 때는 애니메이션 없이 그려진다 — 목록이 출렁이면 비교를 방해한다.
 */
@Composable
private fun Modifier.staggerIn(resultsShownAt: Long, index: Int): Modifier {
    val animate = remember { SystemClock.uptimeMillis() - resultsShownAt < 900L }
    if (!animate) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 45L)
        progress.animateTo(1f, tween(300))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

@Composable
private fun ListCaption(title: String, hint: String) {
    Column(Modifier.padding(top = 2.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 결과 목록의 한 줄. 한자 추천 모드에선 한글 이름이 모두 같으므로
 * [emphasizeHanja] 로 한자를 크게 보여 조합끼리 구분되게 한다.
 */
@Composable
private fun CandidateRow(
    rank: Int,
    eval: NameEvaluation,
    stat: NameStat?,
    emphasizeHanja: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkCard(
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        spacing = 0.dp,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.width(24.dp),
            )
            Column(Modifier.weight(1f)) {
                val hangul = eval.surname + eval.givenName
                val hanja = eval.givenHanja.joinToString("") { it.char.toString() }
                Row(verticalAlignment = Alignment.Bottom) {
                    if (emphasizeHanja) {
                        Text(
                            hanja,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = HanjaFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(hangul, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            hanja,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = HanjaFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 뜻이 없으면 후보끼리 비교가 안 된다 — 목록에서도 바로 보이게
                Text(
                    meaningLine(eval),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                AxisMarks(eval, stat)
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${eval.score}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    eval.grade,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "담아둔 이름에서 빼기" else "이름 담아두기",
                    tint = if (isFavorite) InkTheme.colors.gold else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * 다섯 축의 길흉을 색으로만 압축한 줄.
 *
 * "수리 길 / 발음 보통 …" 을 다 적으면 칩 여섯 개가 두 줄을 먹어 이름이 묻힌다.
 * 축 이름만 남기고 길흉은 글자색으로 옮겨 한 줄에 담았다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AxisMarks(eval: NameEvaluation, stat: NameStat?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AxisMark("수리", eval.suriVerdict)
        AxisMark("발음", eval.baleumVerdict)
        AxisMark("오행", eval.suriOhengVerdict)
        AxisMark("자원", eval.jawonVerdict)
        AxisMark("음양", eval.eumyangVerdict)
        stat?.latestRank?.let { (_, rank) ->
            if (rank <= 200) {
                Box(
                    Modifier
                        .background(InkTheme.colors.gold.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "인기 ${rank}위",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = InkTheme.colors.gold,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisMark(label: String, verdict: AxisVerdict) {
    val color = verdictColor(verdict)
    Box(
        Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 사주 요약.
 *
 * 이름 목록이 주인공이라 사주 카드가 첫 화면을 다 먹으면 안 된다. 사주팔자와
 * 보완할 오행만 접힌 채로 두고, 분포 막대는 눌러서 펼치게 한다.
 */
@Composable
fun SajuCard(saju: SajuSummary) {
    var open by remember { mutableStateOf(false) }

    InkCard(onClick = { open = !open }) {
        SectionTitle("사주") {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "접기" else "오행 분포 펼치기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 4주 표 — [년, 월, 일, 시]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val labels = listOf("년주", "월주", "일주", "시주")
            saju.ganzis.forEachIndexed { i, ganzi ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        labels[i],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val unknown = i == 3 && saju.unknownTime
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        ) {
                            Text(
                                if (unknown) "모름" else ganzi,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = if (unknown) null else HanjaFamily,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            // 한자를 못 읽는 부모가 태반이다 — 독음을 병기한다. 모름 칸은
                            // 공백으로 줄만 맞춘다(네 칸의 높이가 어긋나면 표가 아니라 계단이 된다).
                            Text(
                                if (unknown) " " else ganziHangul(ganzi),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // 표준시 만세력과 시주가 달라 보일 수 있는 이유를 남긴다. 음력 입력이면 양력
        // 환산 전 날짜로 균시차를 어림하지만, 오차는 경도항에 비해 몇 분 수준이라 '약'으로 표기.
        fun solarNote(s: SajuSummary): String? {
            val input = s.input
            if (!input.useTrueSolarTime) return null
            val delta = runCatching {
                TimezoneUtils.trueSolarDeltaMinutes(input.year, input.month, input.day, input.longitude)
            }.getOrNull() ?: return null
            val sign = if (delta >= 0) "+" else "−"
            return "출생지 경도 기준 진태양시 보정(약 $sign${abs(delta)}분)을 적용했습니다. " +
                "표준시 만세력과 시주가 다를 수 있습니다."
        }

        val strengthLabel = when {
            saju.isNeutral -> "중화"
            saju.isStrong -> "신강"
            else -> "신약"
        }
        Text(
            "일간 ${saju.dayStem}(${saju.dayElement.hanja}) · $strengthLabel · " +
                "용신 " + saju.yongsin.joinToString("·") { it.hanja },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "이름으로 보완하면 좋은 오행  " +
                saju.targetElements.joinToString(" · ") { "${it.hanja}(${it.ko})" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(2.dp))
                OhengBarChart(sajuCounts = saju.simpleCounts)
                if (saju.unknownTime) {
                    Text(
                        "출생 시간 미상 — 시주를 제외하고 분석했습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                solarNote(saju)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
