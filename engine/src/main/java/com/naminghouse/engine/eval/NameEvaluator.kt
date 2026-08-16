package com.naminghouse.engine.eval

import com.naminghouse.engine.data.BulyongHanja
import com.naminghouse.engine.data.BulyongInfo
import com.naminghouse.engine.data.BulyongSeverity
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.oheng.BaleumOheng
import com.naminghouse.engine.oheng.BaleumResult
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.oheng.ArrangementQuality
import com.naminghouse.engine.oheng.arrangementQualityOf
import com.naminghouse.engine.oheng.EumYang
import com.naminghouse.engine.oheng.EumYangResult
import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.oheng.SuriOheng
import com.naminghouse.engine.oheng.SuriOhengResult
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
    /** 보완 대상 중 **이름 한자**가 직접 채워 주는 오행 */
    val matched: List<Element>,
    /** 보완 대상 중 **성씨 한자**가 이미 갖고 있는 오행 (이름이 또 채울 필요가 없는 몫) */
    val surnameCovered: List<Element>,
    val gisinUsed: List<Element>,
    val verdict: AxisVerdict,
) {
    /** 성+이름 석 자를 합쳐 실제로 보완된 오행 */
    val covered: List<Element> get() = (matched + surnameCovered).distinct()
}

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
    val suriOheng: SuriOhengResult,
    val suriOhengVerdict: AxisVerdict,
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
 * 배점: 수리사격 30 + 발음오행 18 + 수리오행 12 + 자원오행·사주보완 25 + 음양 10
 *       + 불용한자 무결 5 = 100.
 *
 * 축 구성은 작명왕의 6축(사주오행·수리사격·발음오행·발음음양·수리오행·수리음양)을 따랐고,
 * 발음음양·수리음양은 하나의 '음양' 축으로 합쳤다. 가중치 자체는 자체 판단이다 —
 * 작명왕은 숫자 점수 없이 축별 서술 등급만 낸다.
 *
 * ## 배점 곡선의 근거 (2026-08 조정)
 *
 * 실제 출생신고 상위 200개 이름에 그럴듯한 한자를 붙여 성씨별 3,108건을 채점해 보니
 * 평균 45~54점, 70점 이상이 4~12%뿐이었다. 세상에 실제로 쓰이는 이름의 90% 안팎이
 * "보통" 이하로 떨어지면 변별이 아니라 고장이다. 원인 둘을 다음과 같이 고쳤다.
 *
 * 1. **사주보완** — 보완 대상 상위 2개를 이름 두 글자가 **모두** 채워야 만점이었다(달성률 4%).
 *    실무 작명은 용신 하나만 보강해도 충분하다고 보므로 하나만 맞아도 상당 점수를 준다.
 *    성씨 한자의 자원오행이 계산에서 통째로 빠져 있던 것도 함께 고쳤다 — 성이 이미
 *    용신을 갖고 있으면 이름은 다른 몫을 해도 된다.
 *    이어서 기신 감점을 글자 수 비례에서 **여부**로 바꿨다 — 신약 사주는 기신이 3개,
 *    신강은 2개라 개수로 곱해 깎으면 신약으로 태어난 것 자체가 감점이 됐다.
 * 2. **발음오행** — 상극이 하나라도 있으면 18점 중 4점(78% 감점)이었다. 성씨 초성만으로
 *    결과가 갈리는데(김씨는 실제 인기 이름의 79%가 상극, 이씨는 13%) 사용자가 바꿀 수
 *    없는 값이다. 게다가 작명왕이 상극 배열에 어떤 등급을 주는지는 실측하지 못했다
 *    (tools/jakmyeongwang-report.md '미해결'). 검증 못 한 구간에 최대 페널티를 두는 대신
 *    상극 **개수**에 비례해 깎는다.
 *
 * 3. **수리사격** — 흉수 가중치가 0.05라 사실상 0점이었다. 81수리는 40개가 흉수여서
 *    무작위 이름이면 각 격이 50% 확률로 흉이고 4격 전길은 6%뿐이다. 4격 전길을 이상으로
 *    두는 것은 맞지만, 사주·자원오행과 상충할 때 일부를 양보하는 것도 실무다.
 *    흉수도 최소한의 점수는 남겨 다른 축이 만회할 수 있게 했다.
 * 4. **불용한자** — 경고 하나에 -6, 무결 가점 +5까지 잃어 실질 -11점이었다. 그런데 목록
 *    238자의 대부분이 '길흉역설'(뜻이 너무 좋으면 흉하다) 같은 속설이라 英·榮·珍·明·秀·敏
 *    처럼 실제 출생신고 최다 빈출 글자를 덮는다. 뜻이 명백히 부정적인 34자
 *    ([BulyongSeverity.GIPI])만 감점하고 나머지는 참고 표시로 남긴다 — 이 파일이 원래
 *    주석에 써 둔 "'경고' 용도로만 사용한다"는 방침과 코드를 일치시킨 것이다.
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
        val baleumQuality = baleum?.let { arrangementQualityOf(it.relations) }
        val baleumVerdict = baleumQuality?.let(::verdictOf) ?: AxisVerdict.BOTONG

        // ── 수리오행 (글자별 획수 → 오행 배열)
        val suriOheng = SuriOheng.evaluate(surnameStrokes + givenStrokes)
        val suriOhengQuality = arrangementQualityOf(suriOheng.relations)
        val suriOhengVerdict = verdictOf(suriOhengQuality)

        // ── 음양 (수리음양 + 발음음양)
        val strokeEumyang = EumYang.ofStrokes(surnameStrokes + givenStrokes)
        val soundEumyang = EumYang.ofSound(fullName)
        val balancedCount = listOf(strokeEumyang.isBalanced, soundEumyang?.isBalanced ?: true).count { it }
        val eumyangVerdict = when (balancedCount) {
            2 -> AxisVerdict.GIL
            1 -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }

        // ── 자원오행 · 사주보완 (성씨 자원오행도 보완 몫으로 센다)
        val jawonElements = givenHanja.map { it.element }
        val sajuFit = saju?.let { sajuFit(it, jawonElements, surnameHanja.map { h -> h.element }) }
        val jawonVerdict = sajuFit?.verdict ?: jawonHarmonyVerdict(jawonElements)

        // ── 불용한자 — 성은 선택의 여지가 없으므로 이름 글자만 검사
        val bulyong = givenHanja.mapNotNull { e -> BulyongHanja.map[e.char]?.let { e.char to it } }

        val score = score(
            suri = suri,
            baleumQuality = baleumQuality,
            baleumSanggeuk = baleum?.relations?.count { it == OhengRelation.SANGGEUK } ?: 0,
            suriOhengQuality = suriOhengQuality,
            strokeEumyang = strokeEumyang,
            soundEumyang = soundEumyang,
            jawonElements = jawonElements,
            sajuFit = sajuFit,
            bulyongGipiCount = bulyong.count { it.second.severity == BulyongSeverity.GIPI },
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
            suriOheng = suriOheng,
            suriOhengVerdict = suriOhengVerdict,
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

    private fun verdictOf(quality: ArrangementQuality): AxisVerdict = when (quality) {
        ArrangementQuality.SANGSAENG -> AxisVerdict.GIL
        ArrangementQuality.BIHWA_ONLY -> AxisVerdict.BOTONG
        ArrangementQuality.SANGGEUK -> AxisVerdict.HYUNG
    }

    /**
     * @param jawonElements 이름 글자의 자원오행
     * @param surnameElements 성씨 글자의 자원오행 — 사용자가 바꿀 수 없지만 사주 보완에는
     *   똑같이 기여한다. 성이 이미 용신을 갖고 있으면 이름이 또 채울 필요가 없다.
     */
    private fun sajuFit(
        saju: SajuSummary,
        jawonElements: List<Element?>,
        surnameElements: List<Element?>,
    ): SajuFitResult {
        val targets = saju.targetElements
        val present = jawonElements.filterNotNull()
        val surnamePresent = surnameElements.filterNotNull()
        val matched = targets.filter { it in present }
        val surnameCovered = targets.filter { it in surnamePresent && it !in matched }
        val gisinUsed = present.filter { it in saju.gisin && it !in targets }
        // 용신 하나만 보강해도 '길' — 상위 2개를 모두 채우라는 기존 기준은 실측 달성률이 4%였다.
        val verdict = when {
            gisinUsed.isNotEmpty() && matched.isEmpty() -> AxisVerdict.HYUNG
            matched.isNotEmpty() -> AxisVerdict.GIL
            surnameCovered.isNotEmpty() -> AxisVerdict.BOTONG
            else -> AxisVerdict.HYUNG
        }
        return SajuFitResult(targets, matched, surnameCovered, gisinUsed, verdict)
    }

    /** 사주 없이(출생 전 모드) 자원오행끼리의 조화만 판정 */
    private fun jawonHarmonyVerdict(jawonElements: List<Element?>): AxisVerdict {
        val present = jawonElements.filterNotNull()
        if (present.size < jawonElements.size) return AxisVerdict.BOTONG
        if (present.size < 2) return AxisVerdict.BOTONG
        val relations = present.zipWithNext { a, b -> BaleumOheng.relationOf(a, b) }
        return verdictOf(arrangementQualityOf(relations))
    }

    private fun score(
        suri: SuriGyeok,
        baleumQuality: ArrangementQuality?,
        baleumSanggeuk: Int,
        suriOhengQuality: ArrangementQuality,
        strokeEumyang: EumYangResult,
        soundEumyang: EumYangResult?,
        jawonElements: List<Element?>,
        sajuFit: SajuFitResult?,
        bulyongGipiCount: Int,
    ): Int {
        // 수리사격 30 — 흉수도 바닥을 남긴다.
        // 81수리는 절반이 흉수라 4격 전길은 6%뿐이다. 예전 흉수 0.05는 사실상 0점이라
        // 한 격만 흉해도 -7.1, 두 격이면 -14.6이 빠져 다른 축이 만회할 수가 없었다.
        val gradeVal = { g: SuriGrade ->
            when (g) {
                SuriGrade.DAEGIL -> 1.0
                SuriGrade.GIL -> 0.9
                SuriGrade.PYEONG -> 0.6
                SuriGrade.HYUNG -> 0.35
                SuriGrade.DAEHYUNG -> 0.2
            }
        }
        val suriScore = 30.0 * suri.all.sumOf { gradeVal(it.grade) } / 4.0

        // 발음오행 18 — 상극은 '개수'에 비례해 깎는다.
        // 세 글자 이름의 인접 쌍은 둘뿐이라 하나 상극(12)과 둘 다 상극(6)은 전혀 다른 상태인데
        // 예전에는 똑같이 4점이었다. 성씨 초성만으로 결정되는 몫이 크므로 바닥도 5점 남긴다.
        val baleumScore = when {
            baleumQuality == null -> 9.0 // 한글 이름이 아니라 판정 불가
            baleumSanggeuk > 0 -> (18.0 - 6.0 * baleumSanggeuk).coerceAtLeast(5.0)
            baleumQuality == ArrangementQuality.SANGSAENG -> 18.0
            else -> 13.0 // 전부 비화 — 유파 이견이 커 중간
        }

        // 수리오행 12
        val suriOhengScore = when (suriOhengQuality) {
            ArrangementQuality.SANGSAENG -> 12.0
            ArrangementQuality.BIHWA_ONLY -> 8.0
            ArrangementQuality.SANGGEUK -> 2.0
        }

        // 자원오행·사주보완 25 — 이름이 직접 채운 몫(direct)을 성씨가 이미 채운 몫보다 높게 본다.
        val jawonScore: Double = if (sajuFit != null) {
            val direct = sajuFit.matched.size
            val base = when {
                direct >= 2 -> 25.0                          // 이름 두 글자가 용신 둘을 보강
                direct == 1 && sajuFit.covered.size >= 2 -> 25.0 // 이름 + 성씨로 용신 둘이 채워짐
                direct == 1 -> 22.0                          // 이름이 용신 하나 보강 — 실무 기준 '충분'
                sajuFit.covered.isNotEmpty() -> 17.0         // 성씨가 이미 보완, 이름은 중립
                sajuFit.targets.isEmpty() -> 15.0
                else -> 9.0                                  // 보완 대상을 아무도 채우지 못함
            }
            // 기신은 '몇 글자에 들었나'가 아니라 '들었나'로만 본다.
            //
            // deriveYongGiShared 는 신약 사주에 기신 3개, 신강에 2개를 준다. 다섯 오행 중
            // 셋이 기신이면 이름 글자가 걸릴 확률이 구조적으로 높아, 글자 수만큼 곱해 깎으면
            // **신약으로 태어난 것 자체가 감점**이 된다. 실측에서 기신 사용률이 사주별로
            // 11%~88%로 벌어졌고, 총점 편차 10.6점이 거의 그대로 이 항에서 나왔다.
            // 삼라 engine-core 의 용신·기신 산출은 골든 테스트로 고정돼 있어 손대지 않고,
            // 작명 쪽 배점에서 상한을 둬 흡수한다.
            val gisinPenalty = if (sajuFit.gisinUsed.isEmpty()) 0.0 else 4.0
            (base - gisinPenalty).coerceIn(0.0, 25.0)
        } else {
            when (jawonHarmonyVerdict(jawonElements)) {
                AxisVerdict.GIL -> 22.0
                AxisVerdict.BOTONG -> 15.0
                AxisVerdict.HYUNG -> 7.0
            }
        }

        // 음양 10 (수리 6 + 발음 4)
        val eumyangScore =
            (if (strokeEumyang.isBalanced) 6.0 else 0.0) + (if (soundEumyang?.isBalanced != false) 4.0 else 1.0)

        // 불용한자 5 — '기피'(뜻이 명백히 부정적인 34자)만 감점하고 '속설'은 감점하지 않는다.
        val bulyongScore = 5.0 - 5.0 * bulyongGipiCount

        return (suriScore + baleumScore + suriOhengScore + jawonScore + eumyangScore + bulyongScore)
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
