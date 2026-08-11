package com.naminghouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ui.theme.WuxingColors
import com.naminghouse.engine.gen.NameCandidate
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.Element

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(vm: NamingViewModel) {
    var selected by remember { mutableStateOf<NameCandidate?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (vm.mode == AppMode.RECOMMEND) "추천 결과" else "감명 결과") },
            navigationIcon = {
                IconButton(onClick = vm::backToInput) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            vm.saju?.let { saju ->
                item { SajuCard(saju) }
            }

            if (vm.mode == AppMode.RECOMMEND) {
                item {
                    Text(
                        "추천 이름 ${vm.candidates.size}개 — 수리사격·발음오행·자원오행 기준",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(vm.candidates) { i, cand ->
                    CandidateRow(rank = i + 1, cand = cand, onClick = { selected = cand })
                }
                item { Spacer(Modifier.height(28.dp)) }
            } else {
                vm.evaluation?.let { eval ->
                    item { EvaluationDetail(eval, vm.saju) }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }

    selected?.let { cand ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                EvaluationDetail(cand.evaluation, vm.saju)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CandidateRow(rank: Int, cand: NameCandidate, onClick: () -> Unit) {
    val eval = cand.evaluation
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        eval.surname + cand.givenName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cand.hanja.joinToString("") { it.char.toString() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    AxisChip("수리", eval.suriVerdict.label)
                    AxisChip("발음", eval.baleumVerdict.label)
                    AxisChip("자원", eval.jawonVerdict.label)
                    AxisChip("음양", eval.eumyangVerdict.label)
                    if (cand.tier == 1) AxisChip("인기", "TOP")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${eval.score}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(eval.grade, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AxisChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun SajuCard(saju: SajuSummary) {
    SectionCard("사주 요약") {
        // 4주 표 — [년, 월, 일, 시]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val labels = listOf("년주", "월주", "일주", "시주")
            saju.ganzis.forEachIndexed { i, ganzi ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(labels[i], style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (i == 3 && saju.unknownTime) "모름" else ganzi,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 오행 분포 (단순 글자 수)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Element.entries.forEach { el ->
                val count = saju.simpleCounts[el] ?: 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WuxingColors.of(el).copy(alpha = if (count == 0) 0.25f else 1f),
                ) {
                    Text(
                        "${el.hanja} $count",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
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
        )
        Text(
            "이름으로 보완하면 좋은 오행: " +
                saju.targetElements.joinToString(" · ") { "${it.hanja}(${it.ko})" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (saju.unknownTime) {
            Text(
                "출생 시간 미상 — 시주를 제외하고 분석했습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
