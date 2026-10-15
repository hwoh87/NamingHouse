package com.naminghouse.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.ComparedFavorite
import com.naminghouse.app.FavoriteComparison
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine

/** All columns use the comparison's captured context, including the transition to detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteComparisonSheet(comparison: FavoriteComparison, onDismiss: () -> Unit, onOpen: (ComparedFavorite) -> Unit) {
    var differencesOnly by rememberSaveable { mutableStateOf(false) }
    val entries = comparison.entries
    val axes: List<Pair<String, (NameEvaluation) -> String>> = listOf(
        "수리사격" to { "${it.suriVerdict.label} · 네 격 중 ${it.suri.goodCount}격 길" },
        "발음오행" to { it.baleumVerdict.label },
        "수리오행" to { it.suriOhengVerdict.label },
        (if (comparison.saju == null) "자원오행 조화" else "사주 보완") to { it.jawonVerdict.label },
        "음양 조화" to { it.eumyangVerdict.label },
    )
    val visibleAxes = axes.filter { (_, value) -> !differencesOnly || entries.map { value(it.evaluation) }.distinct().size > 1 }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = InkSpace.s32).testTag("favorite-comparison"),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
            Column(Modifier.padding(horizontal = InkSpace.gutter), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                SceneHeading("나란히 놓고 고르기", "우리 가족의 이름 후보", "같은 조건으로 다시 계산했어요. 뜻과 가족의 마음도 함께 살펴보세요.")
                Text("이번 비교 기준", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(comparison.conditionLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("comparison-condition"))
                Text("저장 당시 점수는 바꾸지 않았어요. 이전 입력 조건에 따라 점수가 달라질 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = differencesOnly, onClick = { differencesOnly = !differencesOnly }, label = { Text("판정이 다른 항목만") })
                Text("옆으로 밀어 ${entries.size}개 이름을 함께 살펴보세요 →", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // A single horizontal scroll keeps every section in the same column order.
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = InkSpace.gutter),
                verticalArrangement = Arrangement.spacedBy(InkSpace.s16)) {
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
                    entries.forEach { entry ->
                        Column(Modifier.width(224.dp), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                            Text(entry.favorite.fullHangul, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(entry.favorite.hanja, fontFamily = HanjaFamily, style = MaterialTheme.typography.titleLarge)
                            if (entry.favorite.isFinalist) Text("최종 후보", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { onOpen(entry) }, modifier = Modifier.testTag("comparison-open-${entry.favorite.key}")) { Text("이 기준으로 상세 보기 →") }
                        }
                    }
                }
                ComparisonValues("이번 비교 점수", entries, entries.map { "${it.evaluation.score}점 · ${it.evaluation.grade}\n저장 당시 ${it.favorite.score}점" })
                ComparisonValues("이름의 뜻", entries, entries.map { meaningLine(it.evaluation) })
                ComparisonValues("우리 가족의 마음", entries, entries.map { it.favorite.familyNote.ifBlank { "아직 적어둔 마음이 없어요" } })
                visibleAxes.forEach { (label, value) -> ComparisonValues(label, entries, entries.map { value(it.evaluation) }) }
                if (differencesOnly && visibleAxes.isEmpty()) {
                    Text("다섯 항목의 판정이 같아요. 한자 뜻과 가족의 마음을 함께 살펴보세요.",
                        modifier = Modifier.width(224.dp), style = MaterialTheme.typography.bodyMedium)
                }
                ComparisonValues("이름 한자 획수", entries, entries.map { entry ->
                    entry.evaluation.givenHanja.joinToString("\n") { "${it.char} · 원획 ${it.wonhoek}획 / 필획 ${it.pilhoek}획" }
                })
            }
            Text("성명학은 전통적인 해석의 한 방법이에요. 점수만으로 이름의 가치를 정하지 않아도 괜찮아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = InkSpace.gutter))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("비교 닫기") }
        }
    }
}

@Composable
private fun ComparisonValues(label: String, entries: List<ComparedFavorite>, values: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
        values.forEachIndexed { index, value ->
            Column(Modifier.width(224.dp).semantics(mergeDescendants = true) {
                contentDescription = "${entries[index].favorite.fullHangul}, $label"
            }, verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
