package com.naminghouse.engine.eval

import com.naminghouse.engine.data.BulyongHanja
import com.naminghouse.engine.data.BulyongInfo
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.oheng.BaleumOheng
import com.naminghouse.engine.oheng.BaleumResult
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.oheng.EumYang
import com.naminghouse.engine.oheng.EumYangResult
import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.saju.SajuSummary
import com.naminghouse.engine.suri.SuriCalculator
import com.naminghouse.engine.suri.SuriGrade
import com.naminghouse.engine.suri.SuriGyeok
import com.samramanshang.manseryeok.orrery.model.Element

/** 평가축 판정 */
enum class AxisVerdict(val label: String) {
    GIL("길"),
    BOTONG("보통"),
    HYUNG("흉"),
}

/** 사주보완(자원오행) 평가 */
data class SajuFitResult(
    val targets: List<Element>,
    val matched: List<Element>,
    val gisinUsed: List<Element>,
    val verdict: AxisVerdict,
)

/** 이름 하나에 대한 종합 감명 결과 */
data class NameEvaluation(
    val surname: String,
    val givenName: String,
    val surnameHanja: List<HanjaEntry>,
    val givenHanja: List<HanjaEntry>,
    val suri: SuriGyeok,
    val suriVerdict: AxisVerdict,
    val baleum: BaleumResult?,
    val baleumVerdict: AxisVerdict,
    val strokeEumyang: EumYangResult,
    val soundEumyang: EumYangResult?,
    val eumyangVerdict: AxisVerdict,
    /** 이름 글자별 자원오행 (미상은 null) */
    val jawonElements: List<Element?>,
    val jawonVerdict: AxisVerdict,
    val sajuFit: SajuFitResult?,
    val bulyongWarnings: List<Pair<Char, BulyongInfo>>,
    val score: Int,
    val grade: String,
)

/**
 * 성명학 종합 평가기.
 *
 * 배점: 수리사격 35 + 발음오행 20 + 자원오행·사주보완 30 + 음양 10 = 95점 만점을
 * 100점으로 환산하지 않고, 나머지 5점은 불용한자 무결(경고 없음) 가점으로 채운다.
 */
object NameEvaluator {

    fun evaluate(
        surname: String,
        givenName: String,
        surnameHanja: List<HanjaEntry>,
        givenHanja: List<HanjaEntry>,
        saju: SajuSummary?,
        school: BaleumSchool = BaleumSchool.UNHAE,
    ): NameEvaluation {
        require(surnameHanja.isNotEmpty() && givenHanja.isNotEmpty()) { "한자 선택이 필요함" }

        val surnameStrokes = surnameHanja.map { it.wonhoek }
        val givenStrokes = givenHanja.map { it.wonhoek }

        // ── 수리사격(원형이정)
        val suri = SuriCalculator.calculate(surnameStrokes, givenStrokes)
        val suriVerdict = when {
            suri.allGood -> AxisVerdict.GIL
            suri.goodCount >= 3 -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }

        // ── 발음오행
        val fullName = surname + givenName
        val baleum = BaleumOheng.evaluate(fullName, school)
        val baleumVerdict = when {
            baleum == null -> AxisVerdict.BOTONG
            baleum.allSangsaeng -> AxisVerdict.GIL
            !baleum.hasSanggeuk -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }

        // ── 음양 (수리음양 + 발음음양)
        val strokeEumyang = EumYang.ofStrokes(surnameStrokes + givenStrokes)
        val soundEumyang = EumYang.ofSound(fullName)
        val balancedCount = listOf(strokeEumyang.isBalanced, soundEumyang?.isBalanced ?: true).count { it }
        val eumyangVerdict = when (balancedCount) {
            2 -> AxisVerdict.GIL
            1 -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }

        // ── 자원오행 · 사주보완
        val jawonElements = givenHanja.map { it.element }
        val sajuFit = saju?.let { sajuFit(it, jawonElements) }
        val jawonVerdict = sajuFit?.verdict ?: jawonHarmonyVerdict(jawonElements)

        // ── 불용한자 — 성은 선택의 여지가 없으므로 이름 글자만 검사
        val bulyong = givenHanja.mapNotNull { e -> BulyongHanja.map[e.char]?.let { e.char to it } }

        val score = score(
            suri = suri,
            baleum = baleum,
            strokeEumyang = strokeEumyang,
            soundEumyang = soundEumyang,
            jawonElements = jawonElements,
            sajuFit = sajuFit,
            bulyongCount = bulyong.size,
        )

        return NameEvaluation(
            surname = surname,
            givenName = givenName,
            surnameHanja = surnameHanja,
            givenHanja = givenHanja,
            suri = suri,
            suriVerdict = suriVerdict,
            baleum = baleum,
            baleumVerdict = baleumVerdict,
            strokeEumyang = strokeEumyang,
            soundEumyang = soundEumyang,
            eumyangVerdict = eumyangVerdict,
            jawonElements = jawonElements,
            jawonVerdict = jawonVerdict,
            sajuFit = sajuFit,
            bulyongWarnings = bulyong,
            score = score,
            grade = gradeOf(score),
        )
    }

    private fun sajuFit(saju: SajuSummary, jawonElements: List<Element?>): SajuFitResult {
        val targets = saju.targetElements
        val present = jawonElements.filterNotNull()
        val matched = targets.filter { it in present }
        val gisinUsed = present.filter { it in saju.gisin && it !in targets }
        val topTargets = targets.take(2)
        val verdict = when {
            gisinUsed.isNotEmpty() && matched.isEmpty() -> AxisVerdict.HYUNG
            topTargets.isNotEmpty() && topTargets.all { it in present } -> AxisVerdict.GIL
            matched.isNotEmpty() -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }
        return SajuFitResult(targets, matched, gisinUsed, verdict)
    }

    /** 사주 없이(출생 전 모드) 자원오행끼리의 조화만 판정 */
    private fun jawonHarmonyVerdict(jawonElements: List<Element?>): AxisVerdict {
        val present = jawonElements.filterNotNull()
        if (present.size < jawonElements.size) return AxisVerdict.BOTONG
        if (present.size < 2) return AxisVerdict.BOTONG
        val relations = present.zipWithNext { a, b -> BaleumOheng.relationOf(a, b) }
        return when {
            relations.all { it == OhengRelation.SANGSAENG } -> AxisVerdict.GIL
            relations.none { it == OhengRelation.SANGGEUK } -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }
    }

    private fun score(
        suri: SuriGyeok,
        baleum: BaleumResult?,
        strokeEumyang: EumYangResult,
        soundEumyang: EumYangResult?,
        jawonElements: List<Element?>,
        sajuFit: SajuFitResult?,
        bulyongCount: Int,
    ): Int {
        // 수리사격 35
        val gradeVal = { g: SuriGrade ->
            when (g) {
                SuriGrade.DAEGIL -> 1.0
                SuriGrade.GIL -> 0.85
                SuriGrade.PYEONG -> 0.4
                SuriGrade.HYUNG -> 0.05
                SuriGrade.DAEHYUNG -> 0.0
            }
        }
        val suriScore = 35.0 * suri.all.sumOf { gradeVal(it.grade) } / 4.0

        // 발음오행 20
        val baleumScore = when {
            baleum == null -> 10.0
            baleum.allSangsaeng -> 20.0
            !baleum.hasSanggeuk -> 14.0
            else -> 4.0
        }

        // 자원오행·사주보완 30
        val jawonScore: Double = if (sajuFit != null) {
            val top = sajuFit.targets.take(2)
            val coverage = if (top.isEmpty()) 0.5 else sajuFit.matched.count { it in top }.toDouble() / top.size
            (30.0 * coverage - 5.0 * sajuFit.gisinUsed.size).coerceIn(0.0, 30.0)
        } else {
            when (jawonHarmonyVerdict(jawonElements)) {
                AxisVerdict.GIL -> 27.0
                AxisVerdict.BOTONG -> 18.0
                AxisVerdict.HYUNG -> 8.0
            }
        }

        // 음양 10 (수리 6 + 발음 4)
        val eumyangScore =
            (if (strokeEumyang.isBalanced) 6.0 else 0.0) + (if (soundEumyang?.isBalanced != false) 4.0 else 1.0)

        // 불용한자 무결 가점 5, 경고당 -6
        val bulyongScore = if (bulyongCount == 0) 5.0 else -6.0 * bulyongCount

        return (suriScore + baleumScore + jawonScore + eumyangScore + bulyongScore)
            .coerceIn(0.0, 100.0)
            .toInt()
    }

    fun gradeOf(score: Int): String = when {
        score >= 85 -> "대길"
        score >= 70 -> "길"
        score >= 50 -> "보통"
        else -> "불길"
    }
}
