package com.naminghouse.engine.saju

import com.naminghouse.engine.fortune.BirthSigns
import com.naminghouse.engine.fortune.StarSign
import com.naminghouse.engine.fortune.Ttii
import com.samramanshang.manseryeok.orrery.analyzer.analyzeStrengthShared
import com.samramanshang.manseryeok.orrery.analyzer.applyHwahaToCountsForStrength
import com.samramanshang.manseryeok.orrery.analyzer.countElements
import com.samramanshang.manseryeok.orrery.analyzer.deriveYongGiShared
import com.samramanshang.manseryeok.orrery.analyzer.detectHwaha
import com.samramanshang.manseryeok.orrery.analyzer.isNeutralBalance
import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Element
import com.samramanshang.manseryeok.orrery.model.Pillar
import com.samramanshang.manseryeok.orrery.model.PillarDetail
import com.samramanshang.manseryeok.orrery.repository.PillarCalculator
import com.samramanshang.manseryeok.orrery.util.BirthInputNormalizer
import com.samramanshang.manseryeok.orrery.util.KoreanLunarCalendar

/**
 * 작명 관점의 사주 요약.
 *
 * [pillars] 는 삼라 엔진 분석기 계약 순서인 [시, 일, 월, 년],
 * [ganzis] 는 화면 표기용 [년, 월, 일, 시] 순서다. 혼동 금지.
 */
data class SajuSummary(
    val input: BirthInput,
    val pillars: List<PillarDetail>,
    val ganzis: List<String>,
    val unknownTime: Boolean,
    val dayStem: String,
    val dayElement: Element,
    /** 지장간 가중(10x 스케일)·합화 보정 후 오행 분포 */
    val weightedCounts: Map<Element, Int>,
    /** 여덟 글자(시간 모름 시 여섯 글자) 단순 오행 개수 */
    val simpleCounts: Map<Element, Int>,
    val isStrong: Boolean,
    val isNeutral: Boolean,
    val yongsin: List<Element>,
    val gisin: List<Element>,
    /** 단순 집계 기준 사주에 아예 없는 오행 */
    val lacking: List<Element>,
    /** 이름의 자원오행으로 보완할 오행 — 우선순위순 */
    val targetElements: List<Element>,
    /** 년지 기준 띠 (입춘 경계 반영) */
    val ttii: Ttii?,
    /** 양력 생일 기준 별자리 */
    val starSign: StarSign?,
)

/**
 * 삼라 engine-core 사주 경로를 작명용으로 감싼 파사드.
 * 파이프라인은 삼라 앱 SajuRepository.computeSaju 와 동일하게 유지한다
 * (음력→양력 변환 → 시각 정규화 → 사주 → PillarDetail 조립 → 오행/용신 분석).
 */
object SajuNamingService {

    fun analyze(rawInput: BirthInput): SajuSummary {
        // 시간 모름은 삼라 UI 계약대로 정오 대입 후 계산하고, 표시는 unknownTime 으로 분기.
        val input = if (rawInput.unknownTime) rawInput.copy(hour = 12, minute = 0) else rawInput

        val solarInput = if (input.isLunar) {
            val s = KoreanLunarCalendar.lunarToSolar(input.year, input.month, input.day, input.isLeapMonth)
            input.copy(year = s.year, month = s.month, day = s.day, isLunar = false)
        } else input

        val adjusted = BirthInputNormalizer.normalizeForOrientalCalendar(solarInput)

        val fourPillars = PillarCalculator.getFourPillars(
            adjusted.year, adjusted.month, adjusted.day, adjusted.hour, adjusted.minute, input.jasiMethod
        )
        val yp = fourPillars[0]; val mp = fourPillars[1]
        val dp = fourPillars[2]; val hp = fourPillars[3]
        val dayStem = dp[0].toString()

        // 분석기 계약 순서: [시, 일, 월, 년]. 일주 십신은 "本元" 리터럴 계약.
        val ganzisForDetail = listOf(hp, dp, mp, yp)
        val pillars = ganzisForDetail.mapIndexed { i, ganzi ->
            val stem = ganzi[0].toString()
            val branch = ganzi[1].toString()
            val stemSipsin =
                if (i == 1) "本元" else PillarCalculator.getRelation(dayStem, stem)?.hanja ?: "?"
            val branchSipsin =
                PillarCalculator.getRelation(dayStem, PillarCalculator.getJeonggi(branch))?.hanja ?: "?"
            PillarDetail(
                pillar = Pillar(ganzi, stem, branch),
                stemSipsin = stemSipsin,
                branchSipsin = branchSipsin,
                unseong = PillarCalculator.getTwelveMeteor(dayStem, branch),
                sinsal = PillarCalculator.getTwelveSpirit(yp[1].toString(), branch),
                jigang = PillarCalculator.getHiddenStems(branch),
            )
        }

        val dayElement = SajuConstants.STEM_INFO.getValue(dayStem).element

        val rawCounts = countElements(pillars)
        val hwaha = detectHwaha(pillars, rawCounts)
        val weighted = applyHwahaToCountsForStrength(rawCounts, hwaha, dayStem)

        val strength = analyzeStrengthShared(pillars)
        val neutral = isNeutralBalance(strength)
        val (yong, gi) = deriveYongGiShared(dayElement, strength.isStrong)

        val simple = simpleCounts(ganzisForDetail, rawInput.unknownTime)
        val lacking = Element.entries.filter { (simple[it] ?: 0) == 0 }

        return SajuSummary(
            input = rawInput,
            pillars = pillars,
            ganzis = listOf(yp, mp, dp, hp),
            unknownTime = rawInput.unknownTime,
            dayStem = dayStem,
            dayElement = dayElement,
            weightedCounts = weighted,
            simpleCounts = simple,
            isStrong = strength.isStrong,
            isNeutral = neutral,
            yongsin = yong,
            gisin = gi,
            lacking = lacking,
            targetElements = deriveTargets(yong, gi, lacking),
            ttii = BirthSigns.ttiiOf(yp[1].toString()),
            // 별자리는 달력상 양력 날짜 기준 — 절기·시각 보정 전 값을 쓴다
            starSign = BirthSigns.starSignOf(solarInput.month, solarInput.day),
        )
    }

    /**
     * 이름으로 보완할 오행 우선순위.
     * 1순위 용신(그중에서도 사주에 없는 오행 먼저), 이어서 기신이 아닌 결핍 오행.
     * 상용 작명 관행(용신 보강 + 무존재 오행 채움)의 절충이다.
     */
    private fun deriveTargets(
        yong: List<Element>,
        gi: List<Element>,
        lacking: List<Element>,
    ): List<Element> {
        val ordered = yong.sortedByDescending { it in lacking } + lacking.filter { it !in gi }
        return ordered.distinct().take(3)
    }

    /** 여덟 글자(시간 모름 시 시주 제외 여섯 글자)의 단순 오행 개수. */
    private fun simpleCounts(ganzisHourFirst: List<String>, unknownTime: Boolean): Map<Element, Int> {
        val counted = if (unknownTime) ganzisHourFirst.drop(1) else ganzisHourFirst
        val result = Element.entries.associateWith { 0 }.toMutableMap()
        for (ganzi in counted) {
            SajuConstants.STEM_INFO[ganzi[0].toString()]?.let { result[it.element] = result.getValue(it.element) + 1 }
            SajuConstants.BRANCH_ELEMENT[ganzi[1].toString()]?.let { result[it] = result.getValue(it) + 1 }
        }
        return result
    }
}
