package com.naminghouse.app.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.naminghouse.app.ui.theme.WuxingColors
import com.naminghouse.engine.eval.AxisVerdict
import com.naminghouse.engine.hanja.HanjaEntry
import com.samramanshang.manseryeok.orrery.model.Element

val Element.ko: String
    get() = when (this) {
        Element.TREE -> "목"
        Element.FIRE -> "화"
        Element.EARTH -> "토"
        Element.METAL -> "금"
        Element.WATER -> "수"
    }

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun VerdictBadge(verdict: AxisVerdict) {
    val (color, text) = when (verdict) {
        AxisVerdict.GIL -> Color(0xFF2E7D4F) to "길"
        AxisVerdict.BOTONG -> Color(0xFF8A6D1E) to "보통"
        AxisVerdict.HYUNG -> Color(0xFFA83C32) to "흉"
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 오행 원형 칩: 글자(음절·한자)와 오행색 */
@Composable
fun ElementBall(label: String, element: Element?, sub: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = element?.let { WuxingColors.of(it) } ?: MaterialTheme.colorScheme.outline,
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(10.dp),
            )
        }
        Text(
            sub ?: element?.let { "${it.hanja}(${it.ko})" } ?: "미상",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
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

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        Element.entries.forEach { el ->
            val base = sajuCounts[el] ?: 0
            val added = nameCounts[el] ?: 0
            val color = WuxingColors.of(el)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (added > 0) "$base+$added" else "$base",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (added > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.height(maxBarHeight).width(34.dp),
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
                                        .width(34.dp)
                                        .height(maxBarHeight * added / maxTotal)
                                        .background(color.copy(alpha = 0.4f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }
                            if (base > 0) {
                                Box(
                                    Modifier
                                        .width(34.dp)
                                        .height(maxBarHeight * base / maxTotal)
                                        .background(
                                            color,
                                            if (added > 0) RoundedCornerShape(0.dp)
                                            else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
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
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "'$syllable' 인명용 한자 (${candidates.size}자)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text("해당 음의 인명용 한자가 없습니다", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(candidates) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(entry) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    entry.char.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.width(44.dp),
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
                                        color = if (entry.usableForNaming) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                                entry.element?.let { el ->
                                    Surface(shape = CircleShape, color = WuxingColors.of(el)) {
                                        Text(
                                            el.hanja,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(6.dp),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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
    OutlinedButton(onClick = { open.value = true }) {
        if (selected == null) {
            Text("$syllable · 한자 선택")
        } else {
            Text("$syllable ${selected.char} (${selected.wonhoek}획)")
            selected.element?.let { el ->
                Spacer(Modifier.width(6.dp))
                Surface(shape = CircleShape, color = WuxingColors.of(el)) {
                    Text(
                        el.hanja,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(4.dp),
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
