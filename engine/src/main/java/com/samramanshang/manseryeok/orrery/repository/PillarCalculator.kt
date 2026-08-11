package com.samramanshang.manseryeok.orrery.repository

import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.HGANJI
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.YANGGAN
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.RELATIONS
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.METEORS_12
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.SPIRITS_12
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.SKY
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.EARTH
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.SKY_KR
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.EARTH_KR
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.STEM_INFO
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.JIJANGGAN
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.METEOR_LOOKUP
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.PILLAR_NAMES
import com.samramanshang.manseryeok.orrery.constants.SajuConstants.GONGMANG_TABLE
import com.samramanshang.manseryeok.orrery.model.*
import com.samramanshang.manseryeok.orrery.util.DateUtils
import kotlin.math.abs
import kotlin.math.truncate

/**
 * 사주 계산 엔진 - pillars.ts 포팅
 */
object PillarCalculator {

    // 절기 상수
    private val MONTH = intArrayOf(
        0, 21355, 42843, 64498, 86335, 108366, 130578, 152958,
        175471, 198077, 220728, 243370, 265955, 288432, 310767,
        332928, 354903, 376685, 398290, 419736, 441060, 462295,
        483493, 504693, 525949
    )

    // 기준점 (1996-02-04 22:08 병자년 입춘)
    private const val UNIT_YEAR = 1996
    private const val UNIT_MONTH = 2
    private const val UNIT_DAY = 4
    private const val UNIT_HOUR = 22
    private const val UNIT_MIN = 8

    // 세차/월건/일진/시주 기준값
    private const val UNIT_YGAN = 2
    private const val UNIT_YJI = 0
    private const val UNIT_MGAN = 6
    private const val UNIT_MJI = 2
    private const val UNIT_MSU = 26
    private const val UNIT_DGAN = 7
    private const val UNIT_DJI = 7
    private const val UNIT_DSU = 7
    private const val UNIT_HGAN = 5
    private const val UNIT_HJI = 11
    private const val UNIT_HSU = 35

    private fun div(a: Int, b: Int): Int = truncate(a.toDouble() / b).toInt()

    /**
     * 그레고리력 → 60갑자 인덱스
     * @return [so24, so24year, so24month, so24day, so24hour]
     */
    fun calcPillarIndices(
        year: Int, month: Int, day: Int, hour: Int, min: Int,
        jasiMethod: JasiMethod = JasiMethod.UNIFIED
    ): IntArray {
        val displ2min = DateUtils.minutesBetween(
            UNIT_YEAR, UNIT_MONTH, UNIT_DAY, UNIT_HOUR, UNIT_MIN,
            year, month, day, hour, min
        )
        val displ2day = DateUtils.daysBetween(
            UNIT_YEAR, UNIT_MONTH, UNIT_DAY, year, month, day
        )

        var so24 = div(displ2min, 525949)
        if (displ2min >= 0) so24 += 1

        // 년주
        var so24year = (so24 % 60) * -1 + 12
        if (so24year < 0) so24year += 60
        else if (so24year > 59) so24year -= 60

        // 월주
        var monthmin100 = displ2min % 525949
        monthmin100 = 525949 - monthmin100
        if (monthmin100 < 0) monthmin100 += 525949
        else if (monthmin100 >= 525949) monthmin100 -= 525949

        var so24monthIdx = 0
        for (i in 0 until 12) {
            val j = i * 2
            if (MONTH[j] <= monthmin100 && monthmin100 < MONTH[j + 2]) {
                so24monthIdx = i
            }
        }

        var t = so24year % 10
        t = t % 5
        t = t * 12 + 2 + so24monthIdx
        var so24month = t
        if (so24month > 59) so24month -= 60

        // 일주
        var so24day = displ2day % 60
        so24day = so24day * -1 + 7
        if (so24day < 0) so24day += 60
        else if (so24day > 59) so24day -= 60

        // 시주
        val i: Int
        when {
            hour == 0 || (hour == 1 && min < 30) -> i = 0
            (hour == 1 && min >= 30) || hour == 2 || (hour == 3 && min < 30) -> i = 1
            (hour == 3 && min >= 30) || hour == 4 || (hour == 5 && min < 30) -> i = 2
            (hour == 5 && min >= 30) || hour == 6 || (hour == 7 && min < 30) -> i = 3
            (hour == 7 && min >= 30) || hour == 8 || (hour == 9 && min < 30) -> i = 4
            (hour == 9 && min >= 30) || hour == 10 || (hour == 11 && min < 30) -> i = 5
            (hour == 11 && min >= 30) || hour == 12 || (hour == 13 && min < 30) -> i = 6
            (hour == 13 && min >= 30) || hour == 14 || (hour == 15 && min < 30) -> i = 7
            (hour == 15 && min >= 30) || hour == 16 || (hour == 17 && min < 30) -> i = 8
            (hour == 17 && min >= 30) || hour == 18 || (hour == 19 && min < 30) -> i = 9
            (hour == 19 && min >= 30) || hour == 20 || (hour == 21 && min < 30) -> i = 10
            (hour == 21 && min >= 30) || hour == 22 || (hour == 23 && min < 30) -> i = 11
            else -> {
                // 야자시: hour == 23 && min >= 30
                i = 0
                if (jasiMethod == JasiMethod.UNIFIED) {
                    so24day += 1
                    if (so24day == 60) so24day = 0
                }
            }
        }

        val isYajasi = i == 0 && hour == 23 && jasiMethod == JasiMethod.SPLIT
        val dayForHour = if (isYajasi) (so24day + 1) % 60 else so24day
        t = dayForHour % 10
        t = t % 5
        t = t * 12 + i
        val so24hour = t

        return intArrayOf(so24, so24year, so24month, so24day, so24hour)
    }

    /** 4주를 60갑자 문자열로 반환 [년주, 월주, 일주, 시주] */
    fun getFourPillars(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        jasiMethod: JasiMethod = JasiMethod.UNIFIED
    ): Array<String> {
        val indices = calcPillarIndices(year, month, day, hour, minute, jasiMethod)
        return arrayOf(
            HGANJI[indices[1]], HGANJI[indices[2]],
            HGANJI[indices[3]], HGANJI[indices[4]]
        )
    }

    /**
     * 절기 시간 구하기
     */
    data class SolarTerms(
        val ingiName: Int, val ingiYear: Int, val ingiMonth: Int,
        val ingiDay: Int, val ingiHour: Int, val ingiMin: Int,
        val midName: Int, val midYear: Int, val midMonth: Int,
        val midDay: Int, val midHour: Int, val midMin: Int,
        val outgiName: Int, val outgiYear: Int, val outgiMonth: Int,
        val outgiDay: Int, val outgiHour: Int, val outgiMin: Int
    )

    fun calcSolarTerms(year: Int, month: Int, day: Int, hour: Int, min: Int): SolarTerms {
        val indices = calcPillarIndices(year, month, day, hour, min)
        val so24month = indices[2]

        val displ2min = DateUtils.minutesBetween(
            UNIT_YEAR, UNIT_MONTH, UNIT_DAY, UNIT_HOUR, UNIT_MIN,
            year, month, day, hour, min
        )

        var monthmin100 = (displ2min % 525949) * -1
        if (monthmin100 < 0) monthmin100 += 525949
        else if (monthmin100 >= 525949) monthmin100 -= 525949

        var ii = so24month % 12 - 2
        if (ii == -2) ii = 10
        else if (ii == -1) ii = 11

        val j = ii * 2
        // V3.06 (audit R3): 절기 복원 부호 — displ2min 과거=양수 규약 정합, 외부 절입시각 15건 대조
        var tmin = displ2min + (monthmin100 - MONTH[j])
        val ingi = DateUtils.dateFromMinutes(-tmin, UNIT_YEAR, UNIT_MONTH, UNIT_DAY, UNIT_HOUR, UNIT_MIN)

        // V3.06 (audit R3): 절기 복원 부호 — displ2min 과거=양수 규약 정합, 외부 절입시각 15건 대조
        tmin = displ2min + (monthmin100 - MONTH[j + 1])
        val mid = DateUtils.dateFromMinutes(-tmin, UNIT_YEAR, UNIT_MONTH, UNIT_DAY, UNIT_HOUR, UNIT_MIN)

        // V3.06 (audit R3): 절기 복원 부호 — displ2min 과거=양수 규약 정합, 외부 절입시각 15건 대조
        tmin = displ2min + (monthmin100 - MONTH[j + 2])
        val outgi = DateUtils.dateFromMinutes(-tmin, UNIT_YEAR, UNIT_MONTH, UNIT_DAY, UNIT_HOUR, UNIT_MIN)

        return SolarTerms(
            ingiName = ii * 2, ingiYear = ingi[0], ingiMonth = ingi[1],
            ingiDay = ingi[2], ingiHour = ingi[3], ingiMin = ingi[4],
            midName = ii * 2 + 1, midYear = mid[0], midMonth = mid[1],
            midDay = mid[2], midHour = mid[3], midMin = mid[4],
            outgiName = ii * 2 + 2, outgiYear = outgi[0], outgiMonth = outgi[1],
            outgiDay = outgi[2], outgiHour = outgi[3], outgiMin = outgi[4]
        )
    }

    /** 대운 10개 계산 */
    fun getDaewoon(
        isMale: Boolean,
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        jasiMethod: JasiMethod = JasiMethod.UNIFIED
    ): List<Pair<String, WallDateTime>> {
        val indices = calcPillarIndices(year, month, day, hour, minute, jasiMethod)
        val sy = indices[1]
        val sm = indices[2]

        val yearStem = HGANJI[sy][0].toString()
        val isYangGan = yearStem in YANGGAN
        val order = (isMale && isYangGan) || (!isMale && !isYangGan)

        val terms = calcSolarTerms(year, month, day, hour, minute)

        // P1a: Calendar epoch 산술 → DateUtils 분(minute) 산술.
        //   출생·절기 시각 모두 초=0 인 벽시계라 분 단위가 손실 없는 최소 정밀도이며,
        //   절입 계산(calcSolarTerms)과 같은 달력 규칙(DateUtils)을 쓰므로 내부 정합도 좋아진다.
        val d0 = if (order) {
            intArrayOf(terms.outgiYear, terms.outgiMonth, terms.outgiDay, terms.outgiHour, terms.outgiMin)
        } else {
            intArrayOf(terms.ingiYear, terms.ingiMonth, terms.ingiDay, terms.ingiHour, terms.ingiMin)
        }

        val diffMin = DateUtils.minutesBetween(
            year, month, day, hour, minute,
            d0[0], d0[1], d0[2], d0[3], d0[4]
        )
        // V2.85: 단위 명시화 — 명리 공식 "3일 = 1년" 환산.
        //   |절기-출생| 시각 × (365.2422 / 3) = 첫 대운까지의 대기 시간 (분 단위)
        //   사후 sanity 는 SajuRepository 가 startDate 기반으로 검증.
        val waitMinDouble = abs(diffMin.toDouble()) * 365.242196 / 3.0
        val waitMin = if (waitMinDouble.isFinite() && waitMinDouble < Int.MAX_VALUE.toDouble() / 2) {
            waitMinDouble.toInt()
        } else {
            0  // overflow 시 birth 시점 그대로 — SajuRepository sanity 가 input.year+i*10 fallback 적용
        }

        val first = DateUtils.dateFromMinutes(waitMin, year, month, day, hour, minute)
        var cur = WallDateTime(first[0], first[1], first[2], first[3], first[4])

        val flow = if (order) 1 else -1
        var mIdx = sm

        val ret = mutableListOf<Pair<String, WallDateTime>>()
        for (idx in 0 until 10) {
            mIdx += flow
            if (mIdx >= HGANJI.size) mIdx = 0
            if (mIdx < 0) mIdx = HGANJI.size - 1
            ret.add(HGANJI[mIdx] to cur)

            // +10년 벽시계 — Calendar.add(YEAR, 10)과 동일하게 2/29 는 평년에서 2/28 로 클램프.
            val ny = cur.year + 10
            val nd = if (cur.month == 2 && cur.day == 29 && DateUtils.dayOfYear(ny, 12, 31) == 365) 28 else cur.day
            cur = WallDateTime(ny, cur.month, nd, cur.hour, cur.minute)
        }

        return ret
    }

    // =============================================
    // 십신 계산
    // =============================================

    private enum class InteractionType { SAME, OUTPUT, INPUT, SHIELD, SWORD }

    private fun getInteraction(e0: Element, e1: Element): InteractionType? {
        if (e0 == e1) return InteractionType.SAME

        return when {
            // 상생 (output)
            (e0 == Element.WATER && e1 == Element.TREE) ||
            (e0 == Element.TREE && e1 == Element.FIRE) ||
            (e0 == Element.FIRE && e1 == Element.EARTH) ||
            (e0 == Element.EARTH && e1 == Element.METAL) ||
            (e0 == Element.METAL && e1 == Element.WATER) -> InteractionType.OUTPUT

            // 상생 역방향 (input)
            (e0 == Element.WATER && e1 == Element.METAL) ||
            (e0 == Element.TREE && e1 == Element.WATER) ||
            (e0 == Element.FIRE && e1 == Element.TREE) ||
            (e0 == Element.EARTH && e1 == Element.FIRE) ||
            (e0 == Element.METAL && e1 == Element.EARTH) -> InteractionType.INPUT

            // 상극 (shield)
            (e0 == Element.WATER && e1 == Element.EARTH) ||
            (e0 == Element.TREE && e1 == Element.METAL) ||
            (e0 == Element.FIRE && e1 == Element.WATER) ||
            (e0 == Element.EARTH && e1 == Element.TREE) ||
            (e0 == Element.METAL && e1 == Element.FIRE) -> InteractionType.SHIELD

            // 상극 (sword)
            (e0 == Element.WATER && e1 == Element.FIRE) ||
            (e0 == Element.TREE && e1 == Element.EARTH) ||
            (e0 == Element.FIRE && e1 == Element.METAL) ||
            (e0 == Element.EARTH && e1 == Element.WATER) ||
            (e0 == Element.METAL && e1 == Element.TREE) -> InteractionType.SWORD

            else -> null
        }
    }

    /** 일간 기준 십신 계산 */
    fun getRelation(dayStem: String, targetStem: String): Relation? {
        val day = STEM_INFO[dayStem] ?: return null
        val target = STEM_INFO[targetStem] ?: return null

        val interaction = getInteraction(day.element, target.element) ?: return null
        val sameYY = day.yinyang == target.yinyang

        return when (interaction) {
            InteractionType.SAME -> if (sameYY) RELATIONS[0] else RELATIONS[1]
            InteractionType.OUTPUT -> if (sameYY) RELATIONS[2] else RELATIONS[3]
            InteractionType.SWORD -> if (sameYY) RELATIONS[4] else RELATIONS[5]
            InteractionType.SHIELD -> if (sameYY) RELATIONS[6] else RELATIONS[7]
            InteractionType.INPUT -> if (sameYY) RELATIONS[8] else RELATIONS[9]
        }
    }

    /** 지장간 반환 */
    fun getHiddenStems(branch: String): String = JIJANGGAN[branch] ?: ""

    /** 지장간의 정기 (마지막 글자) */
    fun getJeonggi(branch: String): String {
        val jijang = getHiddenStems(branch).replace(" ", "")
        return if (jijang.isNotEmpty()) jijang.last().toString() else ""
    }

    /** 한자를 한글로 변환 */
    fun toHangul(hanja: String): String {
        val skyIdx = SKY.indexOf(hanja)
        if (skyIdx >= 0) return SKY_KR[skyIdx].toString()
        val earthIdx = EARTH.indexOf(hanja)
        if (earthIdx >= 0) return EARTH_KR[earthIdx].toString()
        return hanja
    }

    /**
     * 12운성 계산.
     *
     * 학파 정책: **음양 분리 (자평진전·궁통보감 방식)**
     *   - 양일간(甲丙戊庚壬): 12지지를 순행(子→丑→...) 으로 진행
     *   - 음일간(乙丁己辛癸): 12지지를 역행(子→亥→...) 으로 진행
     *
     * 다른 학파 (음양 동일 방식, "양생음사 X")은 이 앱에서 사용 안 함.
     * 음양 분리 방식이 한국 명리학 주류이며 자평진전 정설.
     */
    fun getTwelveMeteor(stem: String, branch: String): String {
        val stemKr = toHangul(stem)
        val branchKr = toHangul(branch)
        val key = stemKr + branchKr
        val idx = METEOR_LOOKUP[key]
        return if (idx != null) METEORS_12[idx].hanja else "?"
    }

    // 12신살 시작 지지
    private val SPIRIT_START = mapOf(
        "寅" to 11, "午" to 11, "戌" to 11,
        "巳" to 2, "酉" to 2, "丑" to 2,
        "申" to 5, "子" to 5, "辰" to 5,
        "亥" to 8, "卯" to 8, "未" to 8
    )

    /** 12신살 계산 (년지 기준) */
    fun getTwelveSpirit(yearBranch: String, targetBranch: String): String {
        val start = SPIRIT_START[yearBranch] ?: return "?"
        val targetIdx = EARTH.indexOf(targetBranch.first())
        if (targetIdx < 0) return "?"
        val offset = ((targetIdx - start) % 12 + 12) % 12
        return SPIRITS_12[offset].hanja
    }

    // =============================================
    // 좌법 · 인종법
    // =============================================

    private val YANG_STEM_OF = mapOf(
        Element.TREE to "甲", Element.FIRE to "丙", Element.EARTH to "戊",
        Element.METAL to "庚", Element.WATER to "壬"
    )

    private data class SipsinCategory(val name: String, val interactions: List<InteractionType>)

    private val SIPSIN_CATEGORIES = listOf(
        SipsinCategory("比劫", listOf(InteractionType.SAME)),
        SipsinCategory("食傷", listOf(InteractionType.OUTPUT)),
        SipsinCategory("財星", listOf(InteractionType.SWORD)),
        SipsinCategory("官星", listOf(InteractionType.SHIELD)),
        SipsinCategory("印星", listOf(InteractionType.INPUT))
    )

    fun calculateJwabeop(
        dayStem: String, branches: List<String>, dayBranch: String
    ): List<List<JwaEntry>> {
        return branches.map { branch ->
            val hidden = getHiddenStems(branch).replace(" ", "")
            hidden.map { stemChar ->
                val stem = stemChar.toString()
                val rel = getRelation(dayStem, stem)
                val sipsin = rel?.hanja ?: "?"
                val unseong = getTwelveMeteor(stem, dayBranch)
                JwaEntry(stem, sipsin, unseong)
            }
        }
    }

    fun calculateInjongbeop(dayStem: String, dayBranch: String): List<InjongEntry> {
        val dayInfo = STEM_INFO[dayStem] ?: return emptyList()

        val hidden = getHiddenStems(dayBranch).replace(" ", "")
        val presentInteractions = mutableSetOf<InteractionType>()
        for (stemChar in hidden) {
            val info = STEM_INFO[stemChar.toString()] ?: continue
            val interaction = getInteraction(dayInfo.element, info.element)
            if (interaction != null) presentInteractions.add(interaction)
        }

        val result = mutableListOf<InjongEntry>()
        for (cat in SIPSIN_CATEGORIES) {
            val missing = cat.interactions.all { it !in presentInteractions }
            if (!missing) continue

            var targetElement: Element? = null
            for ((_, info) in STEM_INFO) {
                if (info.yinyang != YinYang.YANG) continue
                val inter = getInteraction(dayInfo.element, info.element)
                if (inter != null && inter in cat.interactions) {
                    targetElement = info.element
                    break
                }
            }
            if (targetElement == null) continue

            val yangStem = YANG_STEM_OF[targetElement] ?: continue
            val unseong = getTwelveMeteor(yangStem, dayBranch)
            result.add(InjongEntry(cat.name, yangStem, unseong))
        }

        return result
    }

    // =============================================
    // 합충형파해 관계
    // =============================================

    private fun lookupPair(table: Map<String, *>, a: String, b: String): Any? {
        return table["$a,$b"] ?: table["$b,$a"]
    }

    fun getStemRelation(stem1: String, stem2: String): List<RelationResult> {
        val results = mutableListOf<RelationResult>()

        val combine = lookupPair(SajuConstants.STEM_COMBINES, stem1, stem2) as? Pair<*, *>
        if (combine != null) results.add(RelationResult(combine.first as String, combine.second as String))

        val clash = lookupPair(SajuConstants.STEM_CLASHES, stem1, stem2) as? String
        if (clash != null) results.add(RelationResult(clash, null))

        return results
    }

    fun getBranchRelation(branch1: String, branch2: String): List<RelationResult> {
        val results = mutableListOf<RelationResult>()

        val combine = lookupPair(SajuConstants.BRANCH_COMBINES_6, branch1, branch2) as? Pair<*, *>
        if (combine != null) results.add(RelationResult(combine.first as String, combine.second as String))

        val half = lookupPair(SajuConstants.HALF_COMPOSES, branch1, branch2) as? Pair<*, *>
        if (half != null) results.add(RelationResult(half.first as String, half.second as String))

        val clash = lookupPair(SajuConstants.BRANCH_CLASHES, branch1, branch2) as? String
        if (clash != null) results.add(RelationResult(clash, null))

        val brk = lookupPair(SajuConstants.BRANCH_BREAKS, branch1, branch2) as? String
        if (brk != null) results.add(RelationResult(brk, null))

        val harm = lookupPair(SajuConstants.BRANCH_HARMS, branch1, branch2) as? String
        if (harm != null) results.add(RelationResult(harm, null))

        // 형
        val pKey1 = "$branch1,$branch2"
        val pKey2 = "$branch2,$branch1"
        val punishment = SajuConstants.BRANCH_PUNISHMENTS[pKey1] ?: SajuConstants.BRANCH_PUNISHMENTS[pKey2]
        if (punishment != null) {
            results.add(RelationResult(punishment.first, punishment.second))
        }

        // 자형
        if (branch1 == branch2 && branch1 in SajuConstants.BRANCH_SELF_PUNISHMENTS) {
            results.add(RelationResult("刑", "自刑"))
        }

        // 원진
        val wonjin = lookupPair(SajuConstants.BRANCH_WONJIN, branch1, branch2) as? String
        if (wonjin != null) results.add(RelationResult(wonjin, null))

        // 귀문관살
        val gwimun = lookupPair(SajuConstants.BRANCH_GWIMUN, branch1, branch2) as? String
        if (gwimun != null) results.add(RelationResult(gwimun, null))

        return results
    }

    fun analyzePillarRelations(pillar1: String, pillar2: String): PairRelation {
        return PairRelation(
            stem = getStemRelation(pillar1[0].toString(), pillar2[0].toString()),
            branch = getBranchRelation(pillar1[1].toString(), pillar2[1].toString())
        )
    }

    fun checkTripleCompose(branches: List<String>): List<RelationResult> {
        val results = mutableListOf<RelationResult>()
        val branchSet = branches.toSet()

        for (triple in SajuConstants.TRIPLE_COMPOSES) {
            if (triple.first in branchSet && triple.second in branchSet && triple.third in branchSet) {
                val key = "${triple.first},${triple.second},${triple.third}"
                results.add(RelationResult("三合", SajuConstants.TRIPLE_COMPOSE_ELEMENTS[key]))
            }
        }
        return results
    }

    fun checkDirectionalCompose(branches: List<String>): List<RelationResult> {
        val results = mutableListOf<RelationResult>()
        val branchSet = branches.toSet()

        for (dir in SajuConstants.DIRECTIONAL_COMPOSES) {
            if (dir.first in branchSet && dir.second in branchSet && dir.third in branchSet) {
                val key = "${dir.first},${dir.second},${dir.third}"
                results.add(RelationResult("方合", SajuConstants.DIRECTIONAL_COMPOSE_ELEMENTS[key]))
            }
        }
        return results
    }

    fun analyzeAllRelations(pillars: List<String>): AllRelations {
        val pairs = mutableMapOf<String, PairRelation>()

        // pillars 순서: [時(0), 日(1), 月(2), 年(3)]
        val names = listOf("시", "일", "월", "년")
        for (i in pillars.indices) {
            for (j in i + 1 until pillars.size) {
                val rel = analyzePillarRelations(pillars[i], pillars[j])
                if (rel.stem.isNotEmpty() || rel.branch.isNotEmpty()) {
                    pairs["${names[i]}-${names[j]}"] = rel
                }
            }
        }

        val branches = pillars.map { it[1].toString() }

        return AllRelations(
            pairs = pairs,
            triple = checkTripleCompose(branches),
            directional = checkDirectionalCompose(branches)
        )
    }

    // =============================================
    // 신살
    // =============================================

    fun getSpecialSals(
        stems: List<String>, branches: List<String>, dayPillar: String
    ): SpecialSals {
        val dayStem = stems[1]
        val dayBranch = branches[1]
        val monthBranch = branches[2]

        val yanginBranch = SajuConstants.YANGIN_MAP[dayStem]
        val yangin = if (yanginBranch != null) {
            branches.indices.filter { branches[it] == yanginBranch }
        } else emptyList()

        val dohwaBranch = SajuConstants.DOHWA_MAP[dayBranch]
        val dohwa = if (dohwaBranch != null) {
            branches.indices.filter { it != 1 && branches[it] == dohwaBranch }
        } else emptyList()

        // 진도화 판정 — 일지가 속한 삼합 그룹의 다른 두 글자 중 하나라도 사주에 있으면 진도화.
        // 예: 일지 子(申子辰 그룹) + 사주에 申 또는 辰 등장 + 도화 글자 酉 등장 → 진도화.
        val isTrueDohwa = if (dohwa.isNotEmpty()) {
            val samhapGroup = SajuConstants.TRIPLE_COMPOSES.firstOrNull { tri ->
                dayBranch in setOf(tri.first, tri.second, tri.third)
            }
            if (samhapGroup != null) {
                val others = setOf(samhapGroup.first, samhapGroup.second, samhapGroup.third) - dayBranch
                branches.any { it in others }
            } else false
        } else false

        // 곤랑도화 — detector 단일 호출. 진성 곤랑도화(TRUE_GOLLANG)만 기둥 인덱스 수집.
        // NULLIFIED(일간 충으로 합 깨짐) · CONTEXTUAL(일주 비참여)은 UI 침묵을 위해 제외.
        val gollangPillars = com.samramanshang.manseryeok.orrery.analyzer
            .detectGollangDohwa(stems, branches)
            .filter { it.tier == com.samramanshang.manseryeok.orrery.analyzer.GollangTier.TRUE_GOLLANG }
            .flatMap { listOf(it.ziPillarIndex, it.myoPillarIndex) }
            .toSet() // KMP: toSortedSet 은 JVM 전용 — 소비처(707행)가 .sorted() 하므로 순서 불요

        val cheonulBranches = SajuConstants.CHEONUL_MAP[dayStem] ?: emptyList()
        val cheonul = branches.indices.filter { branches[it] in cheonulBranches }

        val cheondukChar = SajuConstants.CHEONDUK_MAP[monthBranch]
        val cheonduk = if (cheondukChar != null) {
            val all = stems + branches
            all.indices.filter { all[it] == cheondukChar }.map { it % 4 }.distinct()
        } else emptyList()

        val woldukChar = SajuConstants.WOLDUK_MAP[monthBranch]
        val wolduk = if (woldukChar != null) {
            stems.indices.filter { stems[it] == woldukChar }
        } else emptyList()

        val munchangBranch = SajuConstants.MUNCHANG_MAP[dayStem]
        val munchang = if (munchangBranch != null) {
            branches.indices.filter { branches[it] == munchangBranch }
        } else emptyList()

        val geumyeoBranch = SajuConstants.GEUMYEO_MAP[dayStem]
        val geumyeo = if (geumyeoBranch != null) {
            branches.indices.filter { branches[it] == geumyeoBranch }
        } else emptyList()

        // 화개살 — 연지 기준 12신살에서 華蓋에 해당하는 기둥
        val yearBranch = branches[3]
        val hwagae = branches.indices.filter { getTwelveSpirit(yearBranch, branches[it]) == "華蓋" }

        // 탕화살 — 일지 기준 특정 지지 등장
        val tanghwaTarget = SajuConstants.TANGHWA_MAP[dayBranch]
        val tanghwa = if (tanghwaTarget != null) {
            branches.indices.filter { it != 1 && branches[it] == tanghwaTarget }
        } else emptyList()

        // 현침살 — 甲·辛(천간) 또는 卯·午·申(지지)이 있는 기둥
        val hyeonchim = (0..3).filter { i ->
            stems[i] in SajuConstants.HYEONCHIM_STEMS || branches[i] in SajuConstants.HYEONCHIM_BRANCHES
        }

        // 학당귀인 — 일간 장생 지지가 등장한 기둥
        val hakdangBranch = SajuConstants.HAKDANG_MAP[dayStem]
        val hakdang = if (hakdangBranch != null) {
            branches.indices.filter { branches[it] == hakdangBranch }
        } else emptyList()

        // 복성귀인 — 일간 길성(복수 가능)이 등장한 기둥
        val bokseongBranches = SajuConstants.BOKSEONG_MAP[dayStem] ?: emptyList()
        val bokseong = branches.indices.filter { branches[it] in bokseongBranches }

        // 천주귀인 — 의식주 풍족 길성
        val cheonjuBranch = SajuConstants.CHEONJU_MAP[dayStem]
        val cheonju = if (cheonjuBranch != null) {
            branches.indices.filter { branches[it] == cheonjuBranch }
        } else emptyList()

        // 암록 — 일간 건록의 6合 지지
        val amrokBranch = SajuConstants.AMROK_MAP[dayStem]
        val amrok = if (amrokBranch != null) {
            branches.indices.filter { branches[it] == amrokBranch }
        } else emptyList()

        // 월덕합 — 월덕귀인의 합 천간이 천간에 등장한 기둥
        val woldukhapStem = SajuConstants.WOLDUKHAP_MAP[monthBranch]
        val woldukhap = if (woldukhapStem != null) {
            stems.indices.filter { stems[it] == woldukhapStem }
        } else emptyList()

        // 장성 — 12신살 將星(연지 기준 三合 中位)에 해당하는 기둥
        val jangseong = branches.indices.filter {
            getTwelveSpirit(yearBranch, branches[it]) == "將星"
        }

        // 역마 — 12신살 驛馬(연지 기준 三合 다음 글자, 이동·변화·해외)에 해당하는 기둥
        val yeokma = branches.indices.filter {
            getTwelveSpirit(yearBranch, branches[it]) == "驛馬"
        }

        return SpecialSals(
            yangin = yangin,
            baekho = dayPillar in SajuConstants.BAEKHO_PILLARS,
            goegang = dayPillar in SajuConstants.GOEGANG_PILLARS,
            dohwa = dohwa,
            cheonul = cheonul,
            cheonduk = cheonduk,
            wolduk = wolduk,
            munchang = munchang,
            hongyeom = dayPillar in SajuConstants.HONGYEOM_PILLARS,
            geumyeo = geumyeo,
            hwagae = hwagae,
            tanghwa = tanghwa,
            hyeonchim = hyeonchim,
            hakdang = hakdang,
            bokseong = bokseong,
            cheonju = cheonju,
            amrok = amrok,
            woldukhap = woldukhap,
            jangseong = jangseong,
            yeokma = yeokma,
            isTrueDohwa = isTrueDohwa,
            gollangDohwa = gollangPillars.sorted()
        )
    }

    // =============================================
    // 태원(胎元) · 명궁(命宮) — WS-4 정밀도 심화 (additive, 기존 필드/함수 미변경)
    // =============================================

    /**
     * 태원(胎元) — 월주에서 干進一位·支進三位(천간 순행 +1칸, 지지 순행 +3칸)로 산출하는 자평 표준 태원법.
     *
     * 예: 월주 甲子 → 甲(+1)=乙, 子(+3)=卯 → 태원 乙卯.
     *
     * 검증: 60갑자는 (천간idx - 지지idx) mod 2 의 홀짝 패리티가 항상 일치해야 유효한 간지 쌍이 되는데,
     * +1(천간)과 +3(지지)은 둘 다 패리티를 뒤집으므로(홀수만큼 이동) 산출된 쌍은 항상 유효한 60갑자 조합이 된다.
     *
     * 순수 함수 — SajuResult/PillarDetail 구조에 영향 없음.
     */
    fun getTaewon(monthGanji: String): String {
        if (monthGanji.length < 2) return ""
        val stemIdx = SKY.indexOf(monthGanji[0])
        val branchIdx = EARTH.indexOf(monthGanji[1])
        if (stemIdx < 0 || branchIdx < 0) return ""
        val newStemIdx = (stemIdx + 1) % 10
        val newBranchIdx = (branchIdx + 3) % 12
        return "${SKY[newStemIdx]}${EARTH[newBranchIdx]}"
    }

    /**
     * 오호둔(五虎遁) — 연간(年干) 기준 寅월의 시작 천간.
     * 이 앱의 calcPillarIndices 월주 산출식(UNIT_MGAN 기반)과 교차검증 완료:
     *   甲子년(so24year=0) 寅월 → 丙寅, 乙丑년(so24year=1) 寅월 → 戊寅,
     *   丙寅년(so24year=2) 寅월 → 庚寅, 丁卯년(so24year=3) 寅월 → 壬寅,
     *   戊辰년(so24year=4) 寅월 → 甲寅  (모두 t = so24year%5*12+2+0 공식으로 직접 재계산해 일치 확인)
     * → 甲/己년 丙寅, 乙/庚년 戊寅, 丙/辛년 庚寅, 丁/壬년 壬寅, 戊/癸년 甲寅.
     */
    private val FIVE_TIGER_START = mapOf(
        "甲" to "丙", "己" to "丙",
        "乙" to "戊", "庚" to "戊",
        "丙" to "庚", "辛" to "庚",
        "丁" to "壬", "壬" to "壬",
        "戊" to "甲", "癸" to "甲"
    )

    /**
     * 명궁(命宮) — 자평 표준 정입명궁결(定入命宮訣) 공식.
     *
     * 근거(정입명궁결 원문): "正月起寅順數，數至生月止；即以生月支上起子時，逆數至生時，即為命宮。"
     *
     * 적용:
     *   1) "正月起寅順數，數至生月止" — 이미 절기 기준으로 계산된 월지(월주 지지, 寅=1월…丑=12월 순환)를
     *      그대로 기준점(temp)으로 삼는다. (사용자 지시: "절기 기준 생월지 순번" 그대로 사용)
     *   2) "生月支上起子時，逆數至生時" — temp 지지에 生時=子 를 대응시키고, 실제 시지의 순번(子=0…亥=11)만큼
     *      "역행"(EARTH 인덱스 감소 방향, wrap)하여 도착한 지지가 명궁 지지.
     *      → 명궁지지idx = (월지idx - 시지idx + 12) % 12   (EARTH 인덱스: 子=0…亥=11)
     *   3) 명궁 천간은 오호둔법(연간 기준 寅월 천간 시작)을 그대로 적용 — 월간 산출에 쓰이는 것과 동일한
     *      고전 규칙을 명궁지지 위치에 적용한다.
     *
     * 시주 미상(unknownTime=true)이면 시지를 알 수 없어 계산 불가 — 호출부(SajuRepository)가 null 처리.
     *
     * @param yearStem 년간 한자 1글자
     * @param monthBranch 월지 한자 1글자 (절기 기준 월주 지지)
     * @param hourBranch 시지 한자 1글자
     * @return 명궁 간지(예: "丙寅") 또는 산출 불가 시 null
     */
    fun getMyeonggung(yearStem: String, monthBranch: String, hourBranch: String): String? {
        val monthIdx = EARTH.indexOf(monthBranch)
        val hourIdx = EARTH.indexOf(hourBranch)
        val startStem = FIVE_TIGER_START[yearStem] ?: return null
        val startIdx = SKY.indexOf(startStem)
        if (monthIdx < 0 || hourIdx < 0 || startIdx < 0) return null

        val palaceBranchIdx = ((monthIdx - hourIdx) % 12 + 12) % 12
        val yinIdx = EARTH.indexOf('寅')
        val offsetFromYin = ((palaceBranchIdx - yinIdx) % 12 + 12) % 12
        val palaceStemIdx = (startIdx + offsetFromYin) % 10

        return "${SKY[palaceStemIdx]}${EARTH[palaceBranchIdx]}"
    }

    /** 연도 → 년주 간지 */
    fun getYearGanzi(year: Int): String {
        return HGANJI[((12 + (year - 1996)) % 60 + 60) % 60]
    }

    /** 일주 기준 공망 지지 2개 */
    fun getGongmang(dayGanzi: String): Pair<String, String> {
        val idx = HGANJI.indexOf(dayGanzi)
        if (idx < 0) return "" to ""
        return GONGMANG_TABLE[idx / 10]
    }

    /** 삼재 매핑: 생년 지지 그룹 → 삼재 3년 지지 (들음·눌음·날음 순) */
    private val SAMJAE_MAP: List<Pair<Set<String>, List<String>>> = listOf(
        setOf("申", "子", "辰") to listOf("寅", "卯", "辰"),
        setOf("亥", "卯", "未") to listOf("巳", "午", "未"),
        setOf("寅", "午", "戌") to listOf("申", "酉", "戌"),
        setOf("巳", "酉", "丑") to listOf("亥", "子", "丑")
    )

    /**
     * 생년 지지 + 기준 연도 → 삼재 3년 정보.
     *
     * 동작:
     *   1) 생년 지지가 속한 4그룹 매핑으로 삼재 3지지 산출
     *   2) `fromYear` 부터 12년 뒤까지 첫 번째 지지(들음년)가 등장하는 양력 연도 탐색
     *   3) 그 연도부터 3년치 [들음, 눌음, 날음] + 각 해 60갑자 채움
     *
     * 현재가 이미 삼재 사이클 안이면 자동으로 그 사이클을 잡음
     * (3지지 중 어느 것이라도 fromYear 와 매칭되면 그 시점부터 들음을 역산해 시작점을 잡지 않고,
     *  단순히 "들음년"이 가장 먼저 매칭되는 가까운 미래"로 통일 — 다음 사이클 시작을 알리는 UX).
     */
    fun computeSamjae(yearBranch: String, fromYear: Int): com.samramanshang.manseryeok.orrery.model.Samjae {
        val triple = SAMJAE_MAP.firstOrNull { yearBranch in it.first }?.second
            ?: return com.samramanshang.manseryeok.orrery.model.Samjae(emptyList(), emptyList())
        val phases = listOf("들음", "눌음", "날음")

        // 현재 진행중인 삼재 사이클 우선 — fromYear 의 연지가 triple 에 속하면 시작년 역산
        val curBranch = getYearGanzi(fromYear).getOrNull(1)?.toString()
        val curIdx = curBranch?.let { triple.indexOf(it) } ?: -1
        val startYear = if (curIdx >= 0) {
            fromYear - curIdx
        } else {
            // 다음 도래: triple[0] 지지가 처음 등장하는 연도
            (fromYear..fromYear + 12).firstOrNull {
                getYearGanzi(it).getOrNull(1)?.toString() == triple[0]
            } ?: fromYear
        }

        val years = (0..2).map { i ->
            val yr = startYear + i
            com.samramanshang.manseryeok.orrery.model.SamjaeYear(
                year = yr,
                ganji = getYearGanzi(yr),
                phase = phases[i]
            )
        }
        return com.samramanshang.manseryeok.orrery.model.Samjae(triple, years)
    }
}
