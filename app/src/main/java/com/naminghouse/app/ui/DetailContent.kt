package com.naminghouse.app.ui

import com.naminghouse.engine.data.BulyongSeverity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.R
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.eval.summarize
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.saju.SajuSummary
import com.naminghouse.engine.suri.SuriMeaning

/**
 * 이름 하나의 종합 감명 패널 — 상세 화면 공용.
 * 상세 화면은 머리에 족자를 따로 그리므로 [showHero] 를 끈다.
 *
 * [locked] 가 참이면 **결론은 남기고 풀이만 가린다** — 점수·등급·총평 한 줄·이름 통계·
 * 발음오행·수리오행·음양은 그대로 두고, 근거에 해당하는 대목(총평 풀이·수리사격 4격·
 * 사주 보완·글자 풀이·불용한자)만 [LockedBlock] 으로 덮는다. 가린 자리에는 진짜 값을
 * 아예 그리지 않고 몇 줄이 있었는지만 센다.
 */
@Composable
fun EvaluationDetail(
    eval: NameEvaluation,
    saju: SajuSummary?,
    stat: NameStat? = null,
    showHero: Boolean = true,
    locked: Boolean = false,
    onUnlock: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {

        if (showHero) DetailHero(eval)

        // ── 총평 — 축별 결과를 읽어 만든 문장
        val summary = summarize(eval)
        SectionCard("총평") {
            Text(summary.verdict, style = MaterialTheme.typography.bodyLarge)
            val noteCount =
                summary.strengths.size + summary.cautions.size + summary.suggestions.size
            if (locked && noteCount > 0) {
                LockedBlock(
                    teaser = "강점 ${summary.strengths.size} · 주의 ${summary.cautions.size} · " +
                        "제안 ${summary.suggestions.size}",
                    lines = noteCount.coerceIn(2, 4),
                    onUnlock = onUnlock,
                )
            } else {
                summary.strengths.forEach { NoteLine("✓", it, InkTheme.colors.gil) }
                summary.cautions.forEach { NoteLine("!", it, InkTheme.colors.hyung) }
                // 주의점을 어떻게 풀지 — 경고만 쌓지 말고 다음 행동을 알려 준다
                summary.suggestions.forEach { NoteLine("→", it, InkTheme.colors.botong) }
            }
        }

        // ── 이름 통계 (대법원 출생신고)
        stat?.let { NameStatCard(it) }

        // ── 띠·별자리 — 이름이 아니라 태어난 때의 정보
        saju?.let { BirthSignCard(it) }

        // ── 수리사격
        SectionCard(
            "수리사격 (원형이정)",
            help = "성과 이름의 획수를 네 가지로 조합해(원격·형격·이격·정격) 초년·청년·중년·" +
                "총운을 보는 이론입니다. 각 격의 수를 81수리 길흉표에 대조해 판정합니다.",
        ) {
            // 잠겼을 때도 원격 한 격은 진짜로 보여 준다 — 무엇을 사는지 알려면 표본이 있어야 한다.
            SuriRow("원격 (초년)", eval.suri.won)
            if (locked) {
                LockedBlock(
                    teaser = "형격(청년) · 이격(중년) · 정격(총운) 과 종합 판정",
                    lines = 4,
                    onUnlock = onUnlock,
                )
            } else {
                SuriRow("형격 (청년)", eval.suri.hyeong)
                SuriRow("이격 (중년)", eval.suri.i)
                SuriRow("정격 (총운)", eval.suri.jeong)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpace.s8),
                ) {
                    Text(
                        "판정",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerdictBadge(eval.suriVerdict)
                }
            }
        }

        // ── 발음오행
        SectionCard(
            "발음오행",
            help = "이름 소리의 첫소리(자음)를 오행에 배속해 이웃 글자끼리 살리는 관계(상생)인지 " +
                "누르는 관계(상극)인지 봅니다. 한자가 아니라 소리에서 나오는 값이라, " +
                "바꾸려면 이름 소리 자체를 바꿔야 합니다.",
        ) {
            val baleum = eval.baleum
            if (baleum == null) {
                Text("판정 불가(한글 이름 아님)", style = MaterialTheme.typography.bodyMedium)
            } else {
                BallRow(verdictTrailing = { VerdictBadge(eval.baleumVerdict) }) {
                    val full = eval.surname + eval.givenName
                    full.forEachIndexed { i, ch ->
                        ElementBall(ch.toString(), baleum.elements.getOrNull(i))
                        if (i < baleum.relations.size) RelationArrow(baleum.relations[i])
                    }
                }
            }
        }

        // ── 수리오행
        SectionCard("수리오행") {
            BallRow(verdictTrailing = { VerdictBadge(eval.suriOhengVerdict) }) {
                val strokes = (eval.surnameHanja + eval.givenHanja).map { it.wonhoek }
                eval.suriOheng.elements.forEachIndexed { i, el ->
                    ElementBall("${strokes.getOrNull(i) ?: ""}", el)
                    if (i < eval.suriOheng.relations.size) {
                        RelationArrow(eval.suriOheng.relations[i])
                    }
                }
            }
            Text(
                "글자 획수의 끝자리를 오행으로 환산해 배열을 봅니다 (1·2 木, 3·4 火, 5·6 土, 7·8 金, 9·0 水).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── 음양
        SectionCard(
            "음양 배열",
            help = "획수의 홀짝(수리음양)과 모음의 양성·음성(발음음양)이 한쪽으로 쏠리지 않고 " +
                "섞였는지 봅니다. 전부 양이거나 전부 음이면 편중으로 봅니다.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
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
        SectionCard(
            if (saju != null) "자원오행 · 사주 보완" else "자원오행",
            help = "한자마다 부수와 뜻에서 오는 고유 오행(자원오행)이 있습니다. 사주에 부족하거나 " +
                "필요한 기운(용신)을 이름 한자가 채워 주는지를 봅니다 — 사주 기반 작명의 핵심 축입니다.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                eval.givenHanja.forEach { h ->
                    ElementBall(h.char.toString(), h.element)
                }
            }
            if (locked) {
                LockedBlock(
                    teaser = if (saju != null) "이 이름이 사주에서 부족한 기운을 채우는지 · 보완 판정"
                        else "자원오행 종합 판정",
                    lines = if (saju != null) 4 else 2,
                    onUnlock = onUnlock,
                )
            } else {
                // 사주 분포 위에 이름의 자원오행을 얹어 어느 칸이 채워지는지 보인다
                if (saju != null) {
                    val nameCounts = eval.jawonElements.filterNotNull()
                        .groupingBy { it }.eachCount()
                    OhengBarChart(sajuCounts = saju.simpleCounts, nameCounts = nameCounts)
                }
                val fit = eval.sajuFit
                if (fit != null) {
                    Text(
                        "보완 대상 오행  " + fit.targets.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (fit.matched.isNotEmpty()) {
                        Text(
                            "이름이 채워주는 오행  " + fit.matched.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = InkTheme.colors.gil,
                        )
                    }
                    if (fit.surnameCovered.isNotEmpty()) {
                        Text(
                            "성씨가 이미 갖춘 오행  " +
                                fit.surnameCovered.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (fit.gisinUsed.isNotEmpty()) {
                        Text(
                            "주의 · 기신 오행 사용  " + fit.gisinUsed.joinToString(" · ") { "${it.hanja}(${it.ko})" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpace.s8),
                ) {
                    Text(
                        "판정",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VerdictBadge(eval.jawonVerdict)
                }
            }
        }

        // ── 불용한자 경고
        if (eval.bulyongWarnings.isNotEmpty()) {
            SectionCard("불용한자 참고") {
                if (locked) {
                    // 몇 자가 걸렸는지는 알려 준다 — 이 카드는 '있다'는 사실 자체가 값이다.
                    val gipiCount = eval.bulyongWarnings.count {
                        it.second.severity == BulyongSeverity.GIPI
                    }
                    LockedBlock(
                        teaser = "주의할 글자 ${eval.bulyongWarnings.size}자" +
                            if (gipiCount > 0) " · 그중 기피 ${gipiCount}자" else "",
                        lines = eval.bulyongWarnings.size.coerceIn(1, 3),
                        onUnlock = onUnlock,
                    )
                } else {
                    eval.bulyongWarnings.forEach { (ch, info) ->
                        val gipi = info.severity == BulyongSeverity.GIPI
                        Text(
                            "$ch [${info.category}·${info.severity.label}] ${info.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (gipi) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "'속설'은 학파에 따라 이견이 커 점수에 반영하지 않고 참고로만 보여 줍니다. " +
                            "뜻이 명백히 좋지 않은 '기피' 글자만 감점합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 글자 풀이
        SectionCard("글자 풀이") {
            // 잠겼을 때는 성씨 글자까지만 — 이름 글자의 뜻·획수·자원오행이 이 카드의 값이다.
            val shown = if (locked) eval.surnameHanja else eval.surnameHanja + eval.givenHanja
            shown.forEachIndexed { i, h ->
                val role = if (i < eval.surnameHanja.size) "성" else "이름"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = InkSpace.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        h.char.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = HanjaFamily,
                        modifier = Modifier.width(46.dp),
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
            if (locked) {
                LockedBlock(
                    // 자원오행은 바로 위 카드의 구슬에 이미 나와 있다 — 없는 걸 판다고 하지 않는다.
                    teaser = "이름 글자 ${eval.givenHanja.size}자의 뜻 · 원획 · 필획",
                    lines = eval.givenHanja.size.coerceIn(1, 3),
                    onUnlock = onUnlock,
                )
            }
        }
    }
}

/**
 * 이름 머리 — 큰 명조 이름 옆에 점수를 낙관으로 찍는다.
 *
 * 목록에서는 점수를 숫자로만 두고 여기서만 도장을 찍는다. 60줄이 전부 붉으면
 * 도장이 아니라 배경이 되기 때문이다.
 */
@Composable
private fun DetailHero(eval: NameEvaluation) {
    Column {
        Box(Modifier.fillMaxWidth()) {
            InkArt(
                res = R.drawable.ink_plum,
                modifier = Modifier.align(Alignment.CenterEnd).size(170.dp),
                alpha = 0.13f,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = InkSpace.s8, bottom = InkSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        eval.surname + eval.givenName,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() },
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = HanjaFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(InkSpace.s12))
                SealBadge(main = "${eval.score}", sub = eval.grade)
            }
        }
        InkStroke(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            alpha = 0.3f,
        )
        Text(
            "뜻  ${meaningLine(eval)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = InkSpace.s8),
        )
    }
}

/** 총평의 강점·주의 한 줄 */
@Composable
private fun NoteLine(mark: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
        Text(
            mark,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 오행 구슬 줄 — 이름이 길면 판정 배지가 밀려 잘리므로 흘려서 감싼다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BallRow(
    verdictTrailing: @Composable () -> Unit,
    balls: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s8),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s8),
        ) { balls() }
        Spacer(Modifier.width(InkSpace.s8))
        verdictTrailing()
    }
}

/** 오행 구슬 사이의 상생·비화·상극 표시 */
@Composable
private fun RelationArrow(relation: OhengRelation) {
    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
        Text(
            when (relation) {
                OhengRelation.SANGSAENG -> "→생"
                OhengRelation.BIHWA -> "=비"
                OhengRelation.SANGGEUK -> "×극"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when (relation) {
                OhengRelation.SANGSAENG -> InkTheme.colors.gil
                OhengRelation.BIHWA -> MaterialTheme.colorScheme.onSurfaceVariant
                OhengRelation.SANGGEUK -> InkTheme.colors.hyung
            },
        )
    }
}

@Composable
private fun SuriRow(label: String, meaning: SuriMeaning) {
    Column(Modifier.padding(bottom = InkSpace.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(
                "${meaning.number}수 ${meaning.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                meaning.grade.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (meaning.grade.isGood) InkTheme.colors.gil else InkTheme.colors.hyung,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            meaning.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = InkSpace.s2),
        )
    }
}
