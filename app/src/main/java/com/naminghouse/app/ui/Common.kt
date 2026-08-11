package com.naminghouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
