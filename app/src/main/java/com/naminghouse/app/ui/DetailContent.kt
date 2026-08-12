package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.eval.summarize
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.saju.SajuSummary
import com.naminghouse.engine.suri.SuriMeaning

/** 이름 하나의 종합 감명 패널 — 추천 상세·감명 결과 공용 */
@Composable
fun EvaluationDetail(eval: NameEvaluation, saju: SajuSummary?, stat: NameStat? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── 헤더
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    eval.surname + eval.givenName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "종합 ${eval.score}점 · ${eval.grade}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "뜻  ${meaningLine(eval)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── 총평 — 축별 결과를 읽어 만든 문장
        val summary = summarize(eval)
        SectionCard("총평") {
            Text(summary.verdict, style = MaterialTheme.typography.bodyMedium)
            summary.strengths.forEach { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
            summary.cautions.forEach { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Text(c, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── 이름 통계 (대법원 출생신고)
        stat?.let { NameStatCard(it) }

        // ── 띠·별자리 — 이름이 아니라 태어난 때의 정보
        saju?.let { BirthSignCard(it) }

        // ── 수리사격
        SectionCard("수리사격 (원형이정)") {
            SuriRow("원격(초년)", eval.suri.won)
            SuriRow("형격(청년)", eval.suri.hyeong)
            SuriRow("이격(중년)", eval.suri.i)
            SuriRow("정격(총운)", eval.suri.jeong)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("판정", style = MaterialTheme.typography.bodyMedium)
                VerdictBadge(eval.suriVerdict)
            }
        }

        // ── 발음오행
        SectionCard("발음오행") {
            val baleum = eval.baleum
            if (baleum == null) {
                Text("판정 불가(한글 이름 아님)", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val full = eval.surname + eval.givenName
                    full.forEachIndexed { i, ch ->
                        ElementBall(ch.toString(), baleum.elements.getOrNull(i))
                        if (i < baleum.relations.size) RelationArrow(baleum.relations[i])
                    }
                    Spacer(Modifier.weight(1f))
                    VerdictBadge(eval.baleumVerdict)
                }
            }
        }

        // ── 수리오행
        SectionCard("수리오행") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val strokes = (eval.surnameHanja + eval.givenHanja).map { it.wonhoek }
                eval.suriOheng.elements.forEachIndexed { i, el ->
                    ElementBall("${strokes.getOrNull(i) ?: ""}", el)
                    if (i < eval.suriOheng.relations.size) {
                        RelationArrow(eval.suriOheng.relations[i])
                    }
                }
                Spacer(Modifier.weight(1f))
                VerdictBadge(eval.suriOhengVerdict)
            }
            Text(
                "글자 획수의 끝자리를 오행으로 환산해 배열을 봅니다 (1·2 木, 3·4 火, 5·6 土, 7·8 金, 9·0 水).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── 음양
        SectionCard("음양 배열") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "수리음양  ${eval.strokeEumyang.display}" +
                            if (eval.strokeEumyang.isBalanced) "  (조화)" else "  (순음/순양)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    eval.soundEumyang?.let {
                        Text(
                            "발음음양  ${it.display}" + if (it.isBalanced) "  (조화)" else "  (편중)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                VerdictBadge(eval.eumyangVerdict)
            }
        }

        // ── 자원오행 · 사주보완
        SectionCard(if (saju != null) "자원오행 · 사주 보완" else "자원오행") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                eval.givenHanja.forEach { h ->
                    ElementBall(h.char.toString(), h.element)
                }
            }
            // 사주 분포 위에 이름의 자원오행을 얹어 어느 칸이 채워지는지 보인다
            if (saju != null) {
                val nameCounts = eval.jawonElements.filterNotNull()
                    .groupingBy { it }.eachCount()
                OhengBarChart(sajuCounts = saju.simpleCounts, nameCounts = nameCounts)
            }
            val fit = eval.sajuFit
            if (fit != null) {
                Text(
                    "보완 대상 오행: " + fit.targets.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (fit.matched.isNotEmpty()) {
                    Text(
                        "이름이 채워주는 오행: " + fit.matched.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (fit.gisinUsed.isNotEmpty()) {
                    Text(
                        "주의: 기신 오행 사용 " + fit.gisinUsed.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("판정", style = MaterialTheme.typography.bodyMedium)
                VerdictBadge(eval.jawonVerdict)
            }
        }

        // ── 불용한자 경고
        if (eval.bulyongWarnings.isNotEmpty()) {
            SectionCard("불용한자 참고") {
                eval.bulyongWarnings.forEach { (ch, info) ->
                    Text(
                        "$ch [${info.category}] ${info.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "불용한자는 전통 속설로, 학파에 따라 이견이 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── 글자 풀이
        SectionCard("글자 풀이") {
            (eval.surnameHanja + eval.givenHanja).forEachIndexed { i, h ->
                val role = if (i < eval.surnameHanja.size) "성" else "이름"
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.char.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.width(44.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(h.meaning.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$role · 원획 ${h.wonhoek} · 필획 ${h.pilhoek}" +
                                (h.element?.let { " · 자원오행 ${it.hanja}(${it.ko})" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 오행 구슬 사이의 상생·비화·상극 표시 */
@Composable
private fun RelationArrow(relation: OhengRelation) {
    Text(
        when (relation) {
            OhengRelation.SANGSAENG -> "→생"
            OhengRelation.BIHWA -> "=비"
            OhengRelation.SANGGEUK -> "×극"
        },
        style = MaterialTheme.typography.labelMedium,
        color = when (relation) {
            OhengRelation.SANGSAENG -> MaterialTheme.colorScheme.primary
            OhengRelation.BIHWA -> MaterialTheme.colorScheme.onSurfaceVariant
            OhengRelation.SANGGEUK -> MaterialTheme.colorScheme.error
        },
    )
}

@Composable
private fun SuriRow(label: String, meaning: SuriMeaning) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
        Text(
            "${meaning.number}수 ${meaning.title}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            meaning.grade.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (meaning.grade.isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
}
