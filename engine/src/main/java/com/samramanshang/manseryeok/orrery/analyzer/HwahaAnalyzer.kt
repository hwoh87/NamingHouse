package com.samramanshang.manseryeok.orrery.analyzer

import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.model.Element
import com.samramanshang.manseryeok.orrery.model.PillarDetail

/**
 * 천간합화(化氣) 분석.
 *
 * 5개 천간합:
 *   - 甲己 → 土 (中正之合)
 *   - 乙庚 → 金 (仁義之合)
 *   - 丙辛 → 水 (威制之合)
 *   - 丁壬 → 木 (仁壽之合)
 *   - 戊癸 → 火 (無情之合)
 *
 * 진가(眞假) 판정 (정밀화 적용):
 *   - 인접 천간쌍에서만 발생 (시-일, 일-월, 월-년)
 *   - 정설 우선순위: 월령 통근 > 화기 오행 충분 > 임계 미만
 *     (1) 월지 정기가 화기 오행이면 → 眞合 (가장 강력 — 월령 통근)
 *     (2) 월지 본기·중기·여기 어느 하나라도 화기와 일치 → 眞合 보강
 *     (3) 화기 오행 카운트 ≥ 15% 이상 → 眞合 (분포 충분, S-01: 10%→15% 상향)
 *     (4) 미만 → 假合 (작용 약함, 표시만)
 *
 * S-08 (audit Phase 1) — 쟁합(爭合)·투합(妬合) 게이트:
 *   쟁합: 동일 양간 2개가 1개 음간을 두고 경쟁 → 합화 불성립
 *   투합: 동일 음간 2개가 1개 양간을 두고 경쟁 → 합화 불성립
 *   이론근거(자평진전·명리정종): 쟁합/투합이면 합의 뜻은 있으나 성화(成化)되지 않는다.
 *   처리: [detectHwaha] 가 4기둥 전 천간 배열을 쟁합/투합 검사 후 해당 쌍은 건너뜀.
 *
 * 합거(合去) — S-08 Phase 2 구현 완료:
 *   진합(眞合) 성화 시 합에 참여한 천간이 化氣로 변해 본래 십신 역할을 잃는 다운스트림 효과.
 *   일간은 합에 참여해도 化하지 않고 羈絆(기반) 상태에 머무므로 합거 대상에서 제외.
 *   일간 외 천간이 합거 대상 → [detectHagoElements] 로 합거 오행 목록 반환.
 *   합거 오행이 용신 오행이면 "용신 탈출(脫出)" — 흉.
 *   합거 오행이 기신 오행이면 "기신 포박(包縛)" — 길.
 *   [computeHageo] (YongsinAnalyzer.kt) 가 두 리스트(용신/기신)와 교차해 품질 신호를 반환.
 *   가합(假合)은 성화 불발이므로 합거 대상 아님 — 진합(眞合)만 합거 발동.
 */

/** 합화 1건 결과. */
data class HwahaResult(
    val stem1: String,            // 천간 1
    val stem2: String,            // 천간 2 (글자 순서는 STEM_COMBINES 정의대로)
    val position: String,         // "時·日" / "日·月" / "月·年"
    val hwagiElement: Element,    // 화기 오행
    val isJin: Boolean,           // 진합(眞合) 여부
    val combineName: String       // "中正之合" 등
)

private val COMBINE_NAMES = mapOf(
    setOf("甲", "己") to "中正之合",
    setOf("乙", "庚") to "仁義之合",
    setOf("丙", "辛") to "威制之合",
    setOf("丁", "壬") to "仁壽之合",
    setOf("戊", "癸") to "無情之合"
)

private fun elementFromKey(key: String): Element? = when (key) {
    "tree" -> Element.TREE
    "fire" -> Element.FIRE
    "earth" -> Element.EARTH
    "metal" -> Element.METAL
    "water" -> Element.WATER
    else -> null
}

/**
 * 사주에서 인접 천간합 검출.
 *
 * S-08: 쟁합·투합 게이트 적용 — 합화 불성립 쌍은 결과에서 제외.
 *
 * @param pillars [hour, day, month, year] 순
 * @param counts countElements 결과 — 진가 판정에 사용
 */
fun detectHwaha(
    pillars: List<PillarDetail>,
    counts: Map<Element, Int>
): List<HwahaResult> {
    if (pillars.size < 4) return emptyList()
    val results = mutableListOf<HwahaResult>()
    val total = counts.values.sum().coerceAtLeast(1).toDouble()

    // 4기둥 전체 천간 목록 — 쟁합·투합 감지용
    val allStems = pillars.map { it.pillar.stem }

    // 인접 페어 — pillars 순서: [hour=0, day=1, month=2, year=3]
    val adjacent = listOf(
        Triple(0, 1, "時·日"),
        Triple(1, 2, "日·月"),
        Triple(2, 3, "月·年")
    )
    // 월지 — 정설상 가장 강한 통근 자리. 월지 오행이 화기와 일치하면 무조건 眞合.
    val monthBranch = pillars[2].pillar.branch
    val monthBranchElement = SajuConstants.BRANCH_ELEMENT[monthBranch]
    // 월지 지장간 정기·중기 — 화기 오행 통근 보강 판정용.
    // V3.04 (audit): [4·valid](a) 여기(餘氣)는 제외 — 자평진전 원칙상 여기 하나만으로는 성화(成化)를
    //   보강할 수 없다(정기·중기만 인정). JIJANGGAN 문자열은 "여기중기정기" 순(2자면 여기·정기만)이므로
    //   첫 글자(여기)를 drop 하면 나머지가 정기·중기(또는 정기만)가 된다.
    val monthCoreElements = com.samramanshang.manseryeok.orrery.repository.PillarCalculator
        .getHiddenStems(monthBranch).replace(" ", "").drop(1).mapNotNull { ch ->
            SajuConstants.STEM_INFO[ch.toString()]?.element
        }

    adjacent.forEach { (i, j, label) ->
        val s1 = pillars[i].pillar.stem
        val s2 = pillars[j].pillar.stem
        val key1 = "$s1,$s2"
        val key2 = "$s2,$s1"
        val combine = SajuConstants.STEM_COMBINES[key1] ?: SajuConstants.STEM_COMBINES[key2]
        if (combine != null) {
            // ── S-08: 쟁합·투합 게이트 ──────────────────────────────────
            // 자평진전 이론: 쟁합(爭合) = 같은 양간 2개가 음간 1개를 두고 쟁탈,
            //              투합(妬合) = 같은 음간 2개가 양간 1개를 두고 투기.
            // 이 경우 합의 뜻은 있으나 성화(成化)되지 않아 합화 불발로 처리.
            //
            // 양간: 甲丙戊庚壬 / 음간: 乙丁己辛癸
            val yangStems = setOf("甲", "丙", "戊", "庚", "壬")
            val s1Yang = s1 in yangStems
            val s2Yang = s2 in yangStems
            // 나머지 천간에서 s1 또는 s2와 동일 글자가 있으면 쟁합/투합 성립
            val otherStems = allStems.filterIndexed { k, _ -> k != i && k != j }
            val isJaengtuhap = when {
                // 쟁합: s1이 양간이고 s2가 음간인 합쌍 → 다른 위치에 s1과 동일 양간 존재
                s1Yang && !s2Yang && otherStems.contains(s1) -> true
                // 쟁합: s2가 양간이고 s1이 음간인 합쌍 → 다른 위치에 s2와 동일 양간 존재
                s2Yang && !s1Yang && otherStems.contains(s2) -> true
                // 투합: s1이 음간이고 s2가 양간인 합쌍 → 다른 위치에 s1과 동일 음간 존재
                !s1Yang && s2Yang && otherStems.contains(s1) -> true
                // 투합: s2가 음간이고 s1이 양간인 합쌍 → 다른 위치에 s2와 동일 음간 존재
                !s2Yang && s1Yang && otherStems.contains(s2) -> true
                else -> false
            }
            if (isJaengtuhap) return@forEach  // 쟁합/투합 → 합화 불발, 건너뜀
            // ────────────────────────────────────────────────────────────

            val hwagi = elementFromKey(combine.second) ?: return@forEach
            val hwagiPct = (counts[hwagi] ?: 0) / total
            // V3.04 (audit): [4·valid](b) 참여 천간이 월지 정기와 같은 오행이면(=자기 오행으로 월령에
            //   강하게 통근) 그 천간은 化하지 않고 본래 정체성을 고수한다 — 合而不化, 假合 강등.
            //   단, 그 천간의 오행이 이미 화기(hwagi)와 같으면 "저항"이 아니라 오히려 화기를 거드는
            //   쪽이므로 게이트 대상에서 제외.
            val s1Element = SajuConstants.STEM_INFO[s1]?.element
            val s2Element = SajuConstants.STEM_INFO[s2]?.element
            val stemRootsMonth =
                (s1Element != null && s1Element != hwagi && s1Element == monthBranchElement) ||
                (s2Element != null && s2Element != hwagi && s2Element == monthBranchElement)
            // 진가 판정 — 정설 우선순위:
            //   (1) 월지 본기 = 화기 → 眞合 (가장 강력, 월령 통근)
            //   (2) 월지 지장간 정기·중기 어딘가 화기 → 眞合 (월령 약통근, 여기 제외)
            //   (3) 화기 오행 카운트 ≥ 15% → 眞合 (분포 충분, S-01: 10%→15% 상향)
            //   (4) 그 외 → 假合
            //   단, 참여 천간이 월지 정기에 자기 오행으로 통근하면(stemRootsMonth) 위 조건과 무관하게 假合.
            val isJin = !stemRootsMonth && (
                monthBranchElement == hwagi ||
                hwagi in monthCoreElements ||
                hwagiPct >= 0.15
            )
            val name = COMBINE_NAMES[setOf(s1, s2)] ?: "合"
            results += HwahaResult(
                stem1 = s1,
                stem2 = s2,
                position = label,
                hwagiElement = hwagi,
                isJin = isJin,
                combineName = name
            )
        }
    }
    return results
}

/**
 * 합거(合去) 오행 식별 — 진합(眞合) 성화에서 일간을 제외한 합 참여 천간의 오행.
 *
 * 정통 자평진전(子平眞詮) 합거 원칙:
 *   - 성화(成化) = 진합(眞合, isJin=true)일 때만 합거가 발동한다.
 *     가합(假合)은 작용만 있고 실제 化하지 않으므로 합거 아님.
 *   - 일간(日干)이 합에 참여하면 일간은 化하지 않고 羈絆(기반)에 머문다.
 *     즉, 일간은 묶이되 합거(역할 소실)는 아님 — 합거 대상 제외.
 *   - 일간 외 천간(시·월·년간)이 합에 참여하고 眞合이면 해당 천간의 본래 오행이 합거됨.
 *
 * @param hwaha   [detectHwaha] 결과 (쟁합/투합 게이트 통과 후 성화된 합만 포함)
 * @param dayStem 일간 천간 한자 (합거 대상 제외 판별용)
 * @return 합거된 오행 목록 (중복 포함 — 동일 오행이 복수 합에서 합거될 수 있음)
 */
fun detectHagoElements(
    hwaha: List<HwahaResult>,
    dayStem: String
): List<Element> {
    val result = mutableListOf<Element>()
    hwaha.forEach { h ->
        if (!h.isJin) return@forEach  // 가합 — 합거 발동 안 함
        // stem1, stem2 중 일간이 아닌 쪽이 합거 대상
        val nonDayStems = buildList {
            if (h.stem1 != dayStem) add(h.stem1)
            if (h.stem2 != dayStem) add(h.stem2)
        }
        nonDayStems.forEach { stem ->
            SajuConstants.STEM_INFO[stem]?.element?.let { result += it }
        }
    }
    return result
}

/**
 * 합화에 따른 오행 분포 보정.
 *
 * 진합(眞合): 두 천간 카운트의 일부를 화기 오행으로 이동
 *   - 두 천간 각 5점씩 차감 (천간 기본 카운트 10이라 절반)
 *   - 화기 오행 +10
 * 가합(假合): 화기 오행 +5만 추가 (원본 유지, 작용은 표시)
 *
 * S-01 (audit Phase 1): production 배선 완료.
 *   - 임계 10%→15% 상향(S-08과 동시 적용)으로 중화 사주의 허위 화격 진입 위험 완화.
 *   - 쟁합/투합 게이트(S-08)가 선행 적용된 결과를 입력으로 받으므로 과대평가 억제.
 *   - YongsinAnalyzer.computeTongguan / analyzeStrengthShared 호출 직전에
 *     [countElements] 결과를 이 함수로 통과시켜 억부·통관 계산에 반영.
 *   - 배선 위치: [applyHwahaToCountsForStrength] 헬퍼 참고.
 *
 * @param dayStem 일간 천간 한자 — 진합거에서 일간 자신의 오행은 차감하지 않기 위한 식별자(기본값 "" =
 *   일간 제외 없음, 기존 호출부 호환).
 * @return 보정된 카운트 맵 — 합거 downstream은 [detectHagoElements]·[computeHageo] 참고.
 */
fun applyHwahaAdjustment(
    counts: Map<Element, Int>,
    hwaha: List<HwahaResult>,
    dayStem: String = ""
): Map<Element, Int> {
    if (hwaha.isEmpty()) return counts
    val out = counts.toMutableMap()
    hwaha.forEach { h ->
        val e1 = SajuConstants.STEM_INFO[h.stem1]?.element ?: return@forEach
        val e2 = SajuConstants.STEM_INFO[h.stem2]?.element ?: return@forEach
        if (h.isJin) {
            // V3.04 (audit): [5·valid] 일간은 합에 참여해도 化하지 않고 羈絆(기반)에 머문다(위 :163~166
            //   원칙과 동일). 일간 오행은 차감 대상에서 제외 — 상대 천간만 -5, 화기 +10은 그대로.
            if (h.stem1 != dayStem) out[e1] = ((out[e1] ?: 0) - 5).coerceAtLeast(0)
            if (h.stem2 != dayStem) out[e2] = ((out[e2] ?: 0) - 5).coerceAtLeast(0)
            out[h.hwagiElement] = (out[h.hwagiElement] ?: 0) + 10
        } else {
            out[h.hwagiElement] = (out[h.hwagiElement] ?: 0) + 5
        }
    }
    return out
}
