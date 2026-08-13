package com.naminghouse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.app.ui.theme.hanjaAwareFamily
import com.naminghouse.engine.eval.AxisVerdict
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.City
import com.samramanshang.manseryeok.orrery.model.Element
import com.samramanshang.manseryeok.orrery.repository.CityRepository

val Element.ko: String
    get() = when (this) {
        Element.TREE -> "목"
        Element.FIRE -> "화"
        Element.EARTH -> "토"
        Element.METAL -> "금"
        Element.WATER -> "수"
    }

/** 판정 색은 라이트/다크에서 명도가 달라야 해서 테마에서 꺼내 쓴다. */
@Composable
fun verdictColor(verdict: AxisVerdict): Color = when (verdict) {
    AxisVerdict.GIL -> InkTheme.colors.gil
    AxisVerdict.BOTONG -> InkTheme.colors.botong
    AxisVerdict.HYUNG -> InkTheme.colors.hyung
}

/**
 * 축 카드. [help] 를 주면 제목 옆에 ? 가 붙고, 눌러 그 축이 무엇을 보는지 펼쳐 읽을 수 있다.
 * 성명학 용어를 처음 보는 부모를 위한 것 — 항상 펼쳐 두면 판정보다 설명이 화면을 먹는다.
 */
@Composable
fun SectionCard(title: String, help: String? = null, content: @Composable () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    InkCard {
        SectionTitle(title) {
            if (help != null) {
                Surface(
                    shape = CircleShape,
                    color = if (showHelp) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clip(CircleShape).clickable { showHelp = !showHelp },
                ) {
                    Text(
                        "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (help != null) {
            AnimatedVisibility(showHelp) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
fun VerdictBadge(verdict: AxisVerdict) {
    val color = verdictColor(verdict)
    Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            verdict.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/**
 * 오행 구슬 — 글자(음절·획수·한자)와 오행색.
 *
 * 단색 원이 아니라 가운데가 옅고 가장자리에 먹이 고인 방울로 그린다.
 */
@Composable
fun ElementBall(label: String, element: Element?, sub: String? = null) {
    val base = element?.let { InkTheme.colors.of(it) } ?: MaterialTheme.colorScheme.outline
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .background(
                    Brush.radialGradient(
                        listOf(lerp(base, Color.White, 0.22f), base, lerp(base, Color.Black, 0.14f))
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = hanjaAwareFamily(label),
                maxLines = 1,
            )
        }
        Text(
            sub ?: element?.let { "${it.hanja}(${it.ko})" } ?: "미상",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * 오행 분포 막대 차트.
 *
 * 사주 여덟 글자를 아래쪽 진한 막대로, 이름 한자의 자원오행을 그 위에 옅은 막대로 쌓아
 * "이름이 어느 칸을 채우는지"를 한 눈에 보인다. 사주에도 없고 이름도 못 채운 오행은
 * 바닥에 납작한 줄만 남겨 결핍이 드러나게 한다.
 *
 * @param nameCounts 이름이 더하는 오행 개수. 비어 있으면 사주 분포만 그린다.
 */
@Composable
fun OhengBarChart(
    sajuCounts: Map<Element, Int>,
    nameCounts: Map<Element, Int> = emptyMap(),
) {
    val maxTotal = Element.entries.maxOf { (sajuCounts[it] ?: 0) + (nameCounts[it] ?: 0) }.coerceAtLeast(1)
    val maxBarHeight = 84.dp
    val cap = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        Element.entries.forEach { el ->
            val base = sajuCounts[el] ?: 0
            val added = nameCounts[el] ?: 0
            val color = InkTheme.colors.of(el)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (added > 0) "$base+$added" else "$base",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (added > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.height(maxBarHeight).width(32.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (base == 0 && added == 0) {
                        // 사주에도 없고 이름도 못 채운 오행 — 빈 자리로 남겨 결핍을 보인다
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (added > 0) {
                                Box(
                                    Modifier
                                        .width(32.dp)
                                        .height(maxBarHeight * added / maxTotal)
                                        .background(color.copy(alpha = 0.38f), cap)
                                )
                            }
                            if (base > 0) {
                                Box(
                                    Modifier
                                        .width(32.dp)
                                        .height(maxBarHeight * base / maxTotal)
                                        .background(color, if (added > 0) RoundedCornerShape(0.dp) else cap)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "${el.hanja}(${el.ko})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (nameCounts.isNotEmpty()) {
        Text(
            "진한 칸이 사주, 옅은 칸이 이름 한자의 자원오행입니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 이름 순위·남녀비율 카드 — 대법원 출생신고 통계.
 * 통계에 없는 이름이면 아무것도 그리지 않는다.
 */
@Composable
fun NameStatCard(stat: NameStat) {
    SectionCard("이름 통계") {
        val ranks = stat.ranks
        if (ranks.isEmpty()) {
            Text(
                "최근 상위권에 든 기록이 없는 이름입니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                "출생신고 이름 순위",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ranks.take(6).forEach { (year, rank) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$rank",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${year}년",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 남녀비율 — 한 줄 막대
        val malePct = stat.malePercent
        Text(
            "남녀 비율",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (malePct > 0) {
                Box(
                    Modifier
                        .weight(malePct.coerceAtLeast(1).toFloat())
                        .height(24.dp)
                        .background(
                            InkTheme.colors.male,
                            RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (malePct >= 15) {
                        Text("남 $malePct%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            val femalePct = 100 - malePct
            if (femalePct > 0) {
                Box(
                    Modifier
                        .weight(femalePct.coerceAtLeast(1).toFloat())
                        .height(24.dp)
                        .background(
                            InkTheme.colors.female,
                            RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (femalePct >= 15) {
                        Text("여 $femalePct%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Text(
            "2008년 이후 출생신고 ${stat.total}건 기준 · 대법원 전자가족관계등록시스템 통계",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 띠·별자리 카드 — 이름이 아니라 태어난 때에 딸린 정보 */
@Composable
fun BirthSignCard(saju: SajuSummary) {
    val ttii = saju.ttii
    val star = saju.starSign
    if (ttii == null && star == null) return

    SectionCard("띠 · 별자리") {
        ttii?.let {
            Text(
                "${it.name}  ${it.branchKo}(${it.branch})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(it.traits, style = MaterialTheme.typography.bodyMedium)
        }
        star?.let {
            Text(
                "${it.name}  ${it.period}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(it.traits, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "띠는 양력 1월 1일이 아니라 입춘을 해의 경계로 봅니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 한자 선택 다이얼로그 — 음 하나에 대한 인명용 한자 목록 */
@Composable
fun HanjaPickerDialog(
    syllable: String,
    candidates: List<HanjaEntry>,
    onSelect: (HanjaEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "'$syllable' 인명용 한자",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${candidates.size}자 · 이름에 흔히 쓰는 글자부터 보여 드립니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                InkStroke(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, alpha = 0.25f)
                Spacer(Modifier.height(6.dp))

                if (candidates.isEmpty()) {
                    Text("해당 음의 인명용 한자가 없습니다", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(candidates) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(entry) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    entry.char.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = HanjaFamily,
                                    modifier = Modifier.width(46.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.meaning.ifEmpty { "-" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "원획 ${entry.wonhoek} · 필획 ${entry.pilhoek}" +
                                            if (entry.usableForNaming) "" else " · 이름에 드물게 쓰는 글자",
                                        style = MaterialTheme.typography.labelSmall,
                                        // 못 쓰는 글자가 아니라 드문 글자다 — 빨간 오류색은 과하다.
                                        color = if (entry.usableForNaming) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            InkTheme.colors.botong
                                        },
                                    )
                                }
                                entry.element?.let { el ->
                                    Surface(shape = CircleShape, color = InkTheme.colors.of(el)) {
                                        Text(
                                            el.hanja,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(7.dp),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}

/** '글자 → 선택된 한자' 버튼. 누르면 픽커 다이얼로그. */
@Composable
fun HanjaSlotButton(
    syllable: Char,
    selected: HanjaEntry?,
    candidates: List<HanjaEntry>,
    onSelect: (HanjaEntry) -> Unit,
) {
    var open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    OutlinedButton(
        onClick = { open.value = true },
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (selected == null) {
            Text("$syllable · 한자 고르기", style = MaterialTheme.typography.labelLarge)
        } else {
            Text(
                selected.char.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = HanjaFamily,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$syllable · ${selected.wonhoek}획",
                style = MaterialTheme.typography.labelMedium,
            )
            selected.element?.let { el ->
                Spacer(Modifier.width(6.dp))
                Surface(shape = CircleShape, color = InkTheme.colors.of(el)) {
                    Text(
                        el.hanja,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(5.dp),
                    )
                }
            }
        }
    }
    if (open.value) {
        HanjaPickerDialog(
            syllable = syllable.toString(),
            candidates = candidates,
            onSelect = { open.value = false; onSelect(it) },
            onDismiss = { open.value = false },
        )
    }
}

/**
 * 출생 지역 선택 다이얼로그 — 국내 도시만.
 * 해외 출생은 도시의 시간대 정보가 없어 진태양시 환산이 틀어지므로 넣지 않는다.
 */
@Composable
fun CityPickerDialog(
    selected: City,
    onSelect: (City) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "출생 지역",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "출생지 경도로 진태양시를 보정합니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                InkStroke(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, alpha = 0.25f)
                Spacer(Modifier.height(6.dp))

                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(CityRepository.KOREAN_CITIES) { c ->
                        val on = c == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(c) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                c.region ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("닫기")
                }
            }
        }
    }
}
