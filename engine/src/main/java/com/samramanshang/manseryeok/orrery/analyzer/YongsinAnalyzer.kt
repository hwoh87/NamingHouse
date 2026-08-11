package com.samramanshang.manseryeok.orrery.analyzer

import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.model.Element
import com.samramanshang.manseryeok.orrery.model.PillarDetail

// HwahaAnalyzer 함수 — 같은 패키지이므로 import 불필요 (패키지 레벨 함수 자동 접근)
// applyHwahaAdjustment, detectHwaha, HwahaResult 는 HwahaAnalyzer.kt 에 정의됨.

// perf: 두 용신 함수가 각자 재할당하던 동일 오행극(剋) 인덱스 맵을 파일 1회 생성으로 공유(동작 불변).
private val YS_OHAENG_CLASH = mapOf(0 to 2, 2 to 4, 4 to 1, 1 to 3, 3 to 0)

/** 한글 조사 — 받침 유무로 "이/가" 결정. 한자·영문은 false 처리. (PalaceFlowAnalyzer.kt josaIGa와 동일 로직) */
private fun josaIGa(word: String): String {
    val last = word.lastOrNull() ?: return "가"
    if (last !in '가'..'힣') return "가"  // 한글 아니면 기본
    val finalCons = (last.code - 0xAC00) % 28
    return if (finalCons == 0) "가" else "이"
}

/** 조사 "을/를" */
private fun josaEulReul(word: String): String {
    val last = word.lastOrNull() ?: return "를"
    if (last !in '가'..'힣') return "를"
    val finalCons = (last.code - 0xAC00) % 28
    return if (finalCons == 0) "를" else "을"
}

/**
 * 다관점 용신 분석기 — 억부 외에 통관·조후 관점을 추가로 산출.
 *
 * 학파별 차이:
 *   - 억부(抑扶): 강약 균형 (UI에서 isStrong 기반으로 별도 처리, 여기선 통관·조후만)
 *   - 통관(通關): 데드락(일간 vs 적대 오행)이면 사이를 잇는 오행
 *   - 조후(調候): 월령 한열 극단이면 균형 오행
 *   - 병약(病藥): 격국 섹션에서 이미 다루므로 스킵
 *
 * 통관 룰은 격국에 따라 우선순위가 달라짐:
 *   - 정관격/편관격: 살인상생 우선 (인성)
 *   - 정재격/편재격: 군겁쟁재 풀이 우선 (식상)
 *   - 인성격/식상격: 효신탈식 풀이 (비겁)
 *   - 비겁격: 식상 통관 우선
 *
 * 회귀 테스트는 [computeTongguan]/[computeJohu] 직접 호출로 검증.
 */

/** 한 관점에서 본 용신 1개 + 그 이유 */
data class YongsinPerspective(
    val school: String,    // "억부" / "통관" / "조후"
    val element: Element,
    val reason: String,    // 한 줄 설명
    val isEmergency: Boolean = false  // 응급 처방(통관)이면 true
)

/** 통관용신과 짝이 되는 상황별 처방 텍스트 */
data class TongguanAdvice(
    val headline: String,    // "윤활유로 쓰는 금(金)"
    val context: String,     // 데드락 컨텍스트 ("관계의 데드락 (土↔水)")
    val howTo: String,       // 행동 지침
    val warning: String      // 오해 방지
)

/** 오행 → 한글 (extension property) */
val Element.hangul: String
    get() = when (this) {
        Element.TREE -> "목"
        Element.FIRE -> "화"
        Element.EARTH -> "토"
        Element.METAL -> "금"
        Element.WATER -> "수"
    }

/* ─ 신강·신약 판정 (단일 출처) ─────────────────────── */

/**
 * 억부(抑扶)법 신강·신약 판정 결과.
 *
 * @param isStrong true=신강, false=신약
 * @param helpScore  일간 생조 점수 합 (비겁+인성, 월주 ×2, 일주 +1, 월지 득령 보너스 포함)
 * @param weakenScore 일간 설기·제압 점수 합 (식상+재성+관성, 월주 ×2)
 */
data class StrengthResult(val isStrong: Boolean, val helpScore: Int, val weakenScore: Int)

/**
 * 억부법 신강·신약 판정 — 앱 전역 단일 출처.
 *
 * 알고리즘 (자평진전 정설):
 *   1. 8셀(천간4 + 지지정기4) 순회 — 월주 ×2 가중치
 *   2. 일간 자체: 비겁으로 +1 (일지 본기와 구분, 일주 고유 가중)
 *   3. 월령 득령(月令得令) 보정:
 *      - 월지 정기 십신이 比肩·劫財(비겁) → +2 (강력 득령)
 *      - 正印·偏印(인성)              → +1 (보조적 득령)
 *   4. helpScore ≥ weakenScore → 신강, 미만 → 신약
 *
 * 중복 구현 제거 대상 (이 함수로 교체):
 *   - SajuScreen.analyzeStrength (internal, UI 전용 — StrengthResult 재사용)
 *   - LuckFlowAnalyzer.deriveYongGi (인라인 복제)
 *   - SajuAiRepository.buildStrengthFromSaju / computeStrengthPayload / strengthAndYongGi / fetchTagline
 *     (모두 동일 help/weaken 카운팅 인라인)
 *
 * @param pillars [hour, day, month, year] 순 — SajuRepository 표준 순서
 */
fun analyzeStrengthShared(pillars: List<PillarDetail>): StrengthResult {
    val helping  = setOf("比肩", "劫財", "偏印", "正印")
    val weakening = setOf("食神", "傷官", "偏財", "正財", "偏官", "正官")
    var help = 0; var weaken = 0
    pillars.forEachIndexed { i, pd ->
        val w = if (i == 2) 2 else 1  // 月柱 가중치 2배
        val sipsinList = if (pd.stemSipsin == "本元")
            listOf(pd.branchSipsin) else listOf(pd.stemSipsin, pd.branchSipsin)
        sipsinList.forEach { s ->
            when (s) {
                in helping  -> help   += w
                in weakening -> weaken += w
            }
        }
        if (i == 1) help += 1  // 일간 자체는 비겁으로 추가 카운트
    }
    // 월령 득령(月令得令) 추가 가중치 — 정설 (자평진전):
    //   월지 정기가 일간 비겁(같은 오행)이면 +2, 인성(생해주는 오행)이면 +1
    val monthBranchSipsin = pillars.getOrNull(2)?.branchSipsin
    when (monthBranchSipsin) {
        "比肩", "劫財" -> help += 2  // 강력한 득령
        "正印", "偏印" -> help += 1  // 보조적 득령
    }
    return StrengthResult(help >= weaken, help, weaken)
}

/**
 * S-06: 억부 중화(中和) 여부 판정.
 *
 * 자평진전 정설: help ≈ weaken 인 사주는 강약 이진 분류 대신 "중화"로 처리한다.
 * 중화 사주는 억부 처방이 불명확해 통관·조후를 먼저 참고하거나
 * 격국에 맞는 십신을 유지하는 방향이 길하다.
 *
 * 판정 임계: |help - weaken| ≤ [helpScore + weakenScore] × 0.12 (±12% 이내)
 * — 절대차가 아닌 비율 기준을 쓰는 이유: 사주 규모(총점)에 따라 같은 차이의 의미가 다름.
 *
 * @param sr [analyzeStrengthShared] 결과
 * @return true이면 중화 사주, false이면 신강/신약 이진 처방 적용
 */
fun isNeutralBalance(sr: StrengthResult): Boolean {
    val total = (sr.helpScore + sr.weakenScore).coerceAtLeast(1)
    return kotlin.math.abs(sr.helpScore - sr.weakenScore) <= total * 0.12
}

/**
 * 일간 오행 + 신강여부 → 용신·기신 오행 목록 도출.
 *
 * 정통 자평 3계열:
 *   신강 → 설기·억제: 식상(genTo) + 재성(ctrlTo) + 관살(ctrlBy) — 일간 에너지 풀기
 *   신약 → 생조:       비겁(dayElement) + 인성(genBy)              — 일간 에너지 보태기
 *
 * 중화 사주([isNeutralBalance] == true)는 UI에서 별도 안내를 우선 표시하고
 * 이 함수를 보조 결과로만 사용한다 (S-06).
 */
fun deriveYongGiShared(
    dayElement: Element,
    isStrong: Boolean
): Pair<List<Element>, List<Element>> {
    val e = Element.entries
    val idx = e.indexOf(dayElement)
    val genBy  = e[(idx + 4) % 5]   // 인성
    val genTo  = e[(idx + 1) % 5]   // 식상
    val clashes = YS_OHAENG_CLASH
    val ctrlToE = e[clashes[idx]!!]                                       // 재성
    val ctrlByE = e[clashes.entries.first { it.value == idx }.key]        // 관살
    return if (isStrong) {
        listOf(genTo, ctrlToE, ctrlByE).distinct() to listOf(genBy, dayElement).distinct()
    } else {
        listOf(genBy, dayElement).distinct() to listOf(ctrlByE, ctrlToE, genTo).distinct()
    }
}

/* ─ 카운트 / 헬퍼 ──────────────────────────────────── */

/**
 * 사주 8자(천간4) + 지지 지장간(여기·중기·정기) 가중 카운트.
 *
 * 정통 명리(자평수언·명리신론) 기준 가중치를 정수 10× 스케일로 적용:
 *   - 천간:           10
 *   - 지지 정기:      10  (월지 정기는 추가 ×2 보너스 = 20)
 *   - 지지 중기:       5
 *   - 지지 여기:       3
 *
 * 정기만 보던 단순 카운트는 학생 수준이라 격하한다. 지장간까지 봐야
 * "관살이 강하다·약하다"가 사주 전체 맥락에서 판정 가능.
 *
 * 지장간 순서: JIJANGGAN[branch] = "여기·중기·정기" (예: "戊丙甲" = 寅).
 * 2자(공백 포함, 예: "壬 癸" = 子)는 여기·정기만 — 중기 없음.
 *
 * @param pillars [hour, day, month, year] 순 — SajuRepository 표준 순서
 * @return 오행별 가중 합 (정수, 10× 스케일)
 */
fun countElements(pillars: List<PillarDetail>): Map<Element, Int> {
    val counts = mutableMapOf<Element, Int>()
    pillars.forEachIndexed { i, pd ->
        val isMonth = (i == 2)
        // 천간 가중 10
        SajuConstants.STEM_INFO[pd.pillar.stem]?.element?.let {
            counts[it] = (counts[it] ?: 0) + 10
        }
        // 지장간 가중 (여기·중기·정기)
        val jijangRaw = SajuConstants.JIJANGGAN[pd.pillar.branch] ?: ""
        val cleaned = jijangRaw.replace(" ", "")
        val len = cleaned.length
        cleaned.forEachIndexed { j, ch ->
            val stem = ch.toString()
            val element = SajuConstants.STEM_INFO[stem]?.element ?: return@forEachIndexed
            // 위치 분류 — 정기는 항상 last
            val isJeonggi = (j == len - 1)
            val isYeogi = (j == 0 && len >= 2 && !isJeonggi) // 첫 자, 정기 아님
            val weight = when {
                isJeonggi -> if (isMonth) 20 else 10  // 월지 정기 ×2 보너스
                isYeogi -> 3
                else -> 5  // 중기
            }
            counts[element] = (counts[element] ?: 0) + weight
        }
    }
    return counts
}

/** 카운트 합. 비율 임계 판정용. */
private fun Map<Element, Int>.totalSum(): Int = values.sum().coerceAtLeast(1)

/* ─ 십신 카테고리 컨텍스트 (Stage 2 룰 매칭용) ─ */

/**
 * 사주의 십신 카테고리별 비율 + 특수 신살 플래그.
 * 십신 카드 변주(`SipsinVariation.condition`) 자동 매칭에 사용.
 *
 * 카테고리:
 *   - 비겁(比劫) = 비견(比肩) + 겁재(劫財)
 *   - 식상(食傷) = 식신(食神) + 상관(傷官)
 *   - 재성(財星) = 정재(正財) + 편재(偏財)
 *   - 관성(官星) = 정관(正官) + 편관(偏官)
 *   - 인성(印星) = 정인(正印) + 편인(偏印)
 */
data class SipsinContext(
    val bigeopRatio: Double,    // 비겁
    val sicksangRatio: Double,  // 식상
    val jaesungRatio: Double,   // 재성
    val gwansungRatio: Double,  // 관성
    val insungRatio: Double,    // 인성
    val hasYangin: Boolean,     // 양인살 존재 여부
    // 귀인 결합 — 식신·상관·정인 등의 변주에서 ★ 강조용
    val hasCheonul: Boolean = false,    // 천을귀인
    val hasMunchang: Boolean = false,   // 문창귀인
    val hasHakdang: Boolean = false,    // 학당귀인
    val hasCheonduk: Boolean = false,   // 천덕귀인
    val hasWolduk: Boolean = false,     // 월덕귀인
    val hasYeokma: Boolean = false,     // 역마
    val hasDohwa: Boolean = false,      // 도화살
    val hasHwagae: Boolean = false,     // 화개살
    // 셀 단위 십신 존재 여부 — 비율 임계 미달이라도 셀 단위 패턴(예: 식신제살) 활성용
    val hasBigeon: Boolean = false,     // 비견 셀 1+ (本元 외)
    val hasGeopjae: Boolean = false,    // 겁재 셀 1+
    val hasSiksin: Boolean = false,     // 식신 셀 1+
    val hasSanggwan: Boolean = false,   // 상관 셀 1+
    val hasJeongjae: Boolean = false,   // 정재 셀 1+
    val hasPyeonjae: Boolean = false,   // 편재 셀 1+
    val hasJeonggwan: Boolean = false,  // 정관 셀 1+
    val hasPyeongwan: Boolean = false,  // 편관 셀 1+
    val hasJeongin: Boolean = false,    // 정인 셀 1+
    val hasPyeonin: Boolean = false,    // 편인 셀 1+
    /**
     * S-05: 일간 무근(無根) 여부 — 지지 4자(지장간) 어디에도 일간 천간이 통근하지 않을 때 true.
     *
     * 정통 명리(자평진전) 종격(從格) 입격의 핵심 전제:
     *   일간이 지지에 뿌리를 두지 않아야(無根) 강한 흐름에 '순종(從)'할 수 있음.
     *   일간이 어느 지지에라도 통근하면 신강 가능성이 있어 종격으로 보지 않는다.
     *
     * 산출: 일간 천간 글자가 4지지 지장간(여기·중기·정기 포함) 중 1개라도 나타나면 false.
     * [computeSipsinContext] 에서 pillars를 받아 자동 계산.
     */
    val isDayRootless: Boolean = false
)

private val BIGEOP_SET = setOf("比肩", "劫財")
private val SICKSANG_SET = setOf("食神", "傷官")
private val JAESUNG_SET = setOf("正財", "偏財")
private val GWANSUNG_SET = setOf("正官", "偏官")
private val INSUNG_SET = setOf("正印", "偏印")

/**
 * 사주에서 십신 카테고리 비율 산출.
 *
 * S-10: analyzeStrengthShared 와 동일 가중치 체계 적용.
 *   - 8자(천간 4 + 지지 정기 4) 기반 카운트. 월주 ×2.
 *   - 일주 천간(本元)은 비겁 1 로 고정 카운트 (일간 자체 자리).
 *   - 월지 득령(月令得令) 보정: 월지 정기 비겁 → bigeop +2, 인성 → insung +1.
 *     (자평진전 정설. analyzeStrengthShared 의 help 득령 보정과 동일 계수.)
 *
 * S-05: isDayRootless 자동 계산 — pillars[1].pillar.stem(일간 천간)이
 *   4지지의 지장간(JIJANGGAN, 여기·중기·정기 전부) 중 어디에도 없으면 true.
 *   일간이 통근(通根) 한 곳도 없을 때만 종격 입격 가능(자평진전 정설).
 */
fun computeSipsinContext(
    pillars: List<PillarDetail>,
    hasYangin: Boolean = false,
    hasCheonul: Boolean = false,
    hasMunchang: Boolean = false,
    hasHakdang: Boolean = false,
    hasCheonduk: Boolean = false,
    hasWolduk: Boolean = false,
    hasYeokma: Boolean = false,
    hasDohwa: Boolean = false,
    hasHwagae: Boolean = false
): SipsinContext {
    var bigeop = 0; var sicksang = 0; var jaesung = 0
    var gwansung = 0; var insung = 0
    val cellSipsins = mutableSetOf<String>()
    pillars.forEachIndexed { i, pd ->
        val w = if (i == 2) 2 else 1  // 월주 ×2
        // S-10: 일주 천간(本元)은 비겁 1로 고정 (analyzeStrengthShared i==1 help+1 동일)
        if (pd.stemSipsin == "本元") {
            bigeop += 1  // 일간 자체 비겁 고정 (weight=1, 월주 아님)
            cellSipsins += "比肩"
        } else {
            when (pd.stemSipsin) {
                in BIGEOP_SET -> { bigeop += w; cellSipsins += pd.stemSipsin }
                in SICKSANG_SET -> { sicksang += w; cellSipsins += pd.stemSipsin }
                in JAESUNG_SET -> { jaesung += w; cellSipsins += pd.stemSipsin }
                in GWANSUNG_SET -> { gwansung += w; cellSipsins += pd.stemSipsin }
                in INSUNG_SET -> { insung += w; cellSipsins += pd.stemSipsin }
            }
        }
        // 지지 정기 항상 카운트 (미매핑 값도 cellSipsins 에 보존)
        cellSipsins += pd.branchSipsin
        when (pd.branchSipsin) {
            in BIGEOP_SET -> bigeop += w
            in SICKSANG_SET -> sicksang += w
            in JAESUNG_SET -> jaesung += w
            in GWANSUNG_SET -> gwansung += w
            in INSUNG_SET -> insung += w
        }
    }
    // S-10: 월지 득령(月令得令) 보정 — analyzeStrengthShared 와 동일 계수 적용
    //   월지 정기 비겁 → bigeop +2 (강력 득령), 인성 → insung +1 (보조 득령)
    when (pillars.getOrNull(2)?.branchSipsin) {
        "比肩", "劫財" -> bigeop += 2
        "正印", "偏印" -> insung += 1
    }
    val total = (bigeop + sicksang + jaesung + gwansung + insung).coerceAtLeast(1)

    // S-05: 일간 무근(無根) 판정 — 일간 천간이 4지지 지장간 어디에도 없으면 true
    val dayRootless = computeIsDayRootless(pillars)

    return SipsinContext(
        bigeopRatio = bigeop.toDouble() / total,
        sicksangRatio = sicksang.toDouble() / total,
        jaesungRatio = jaesung.toDouble() / total,
        gwansungRatio = gwansung.toDouble() / total,
        insungRatio = insung.toDouble() / total,
        hasYangin = hasYangin,
        hasCheonul = hasCheonul,
        hasMunchang = hasMunchang,
        hasHakdang = hasHakdang,
        hasCheonduk = hasCheonduk,
        hasWolduk = hasWolduk,
        hasYeokma = hasYeokma,
        hasDohwa = hasDohwa,
        hasHwagae = hasHwagae,
        hasBigeon = "比肩" in cellSipsins,
        hasGeopjae = "劫財" in cellSipsins,
        hasSiksin = "食神" in cellSipsins,
        hasSanggwan = "傷官" in cellSipsins,
        hasJeongjae = "正財" in cellSipsins,
        hasPyeonjae = "偏財" in cellSipsins,
        hasJeonggwan = "正官" in cellSipsins,
        hasPyeongwan = "偏官" in cellSipsins,
        hasJeongin = "正印" in cellSipsins,
        hasPyeonin = "偏印" in cellSipsins,
        isDayRootless = dayRootless
    )
}

/**
 * S-05: 일간 무근(無根) 판정 헬퍼.
 *
 * 정통 자평진전: 일간이 사주 4지지 지장간에 전혀 통근하지 않을 때
 * "무근" 상태로 보고 종격 입격 가능 조건이 충족됨.
 * 통근 = 일간 천간 글자가 지장간(여기·중기·정기) 중 하나라도 같을 때.
 *
 * @param pillars [hour, day, month, year] 순 (SajuRepository 표준)
 * @return 일간이 4지지 지장간 중 어디에도 통근하지 않으면 true
 */
fun computeIsDayRootless(pillars: List<PillarDetail>): Boolean {
    if (pillars.size < 2) return false
    val dayStem = pillars[1].pillar.stem  // 일간 천간
    // 4지지 모두에서 지장간 확인 (공백 제거 후 글자 단위 분해)
    return pillars.none { pd ->
        val jijangRaw = SajuConstants.JIJANGGAN[pd.pillar.branch] ?: ""
        dayStem in jijangRaw.replace(" ", "")
    }
}

/**
 * 변주 condition 텍스트를 SipsinContext에 대해 평가.
 *
 * condition 키워드(자연어) → 비율 임계 매칭. 부분 문자열 검색이라 유연함.
 * 임계: 동반/강 = 15% 이상, 약 = 10% 미만, "강함" = 30% 이상.
 */
fun matchesVariation(ctx: SipsinContext, condition: String): Boolean = when {
    // 군겁쟁재 — 비겁 + 재성 모두 충족
    condition.contains("군겁쟁재") ->
        ctx.bigeopRatio >= 0.30 && ctx.jaesungRatio >= 0.15
    // 재극인 — 재성 강 + 인성 동반
    condition.contains("재극인") ->
        ctx.jaesungRatio >= 0.30 && ctx.insungRatio >= 0.15

    // 양인 + 귀인 결합 (보유 여부)
    condition.contains("羊刃") || condition.contains("양인") -> ctx.hasYangin
    condition.contains("천을") -> ctx.hasCheonul
    condition.contains("문창") -> ctx.hasMunchang
    condition.contains("학당") -> ctx.hasHakdang
    condition.contains("천덕") -> ctx.hasCheonduk
    condition.contains("월덕") -> ctx.hasWolduk
    condition.contains("역마") -> ctx.hasYeokma
    condition.contains("도화") -> ctx.hasDohwa
    condition.contains("화개") -> ctx.hasHwagae

    // 강함(30%+)
    condition.contains("비겁") && condition.contains("강") -> ctx.bigeopRatio >= 0.30
    condition.contains("식상") && condition.contains("강") -> ctx.sicksangRatio >= 0.30
    condition.contains("재성") && condition.contains("강") -> ctx.jaesungRatio >= 0.30
    condition.contains("관성") && condition.contains("강") -> ctx.gwansungRatio >= 0.30
    condition.contains("정관") && condition.contains("강") -> ctx.gwansungRatio >= 0.30
    condition.contains("편관") && condition.contains("강") -> ctx.gwansungRatio >= 0.30
    condition.contains("정인") && condition.contains("강") -> ctx.insungRatio >= 0.30
    condition.contains("편인") && condition.contains("강") -> ctx.insungRatio >= 0.30

    // 적당(15%~30% — 동반은 있되 과중하지 않음)
    condition.contains("비겁") && condition.contains("적당") -> ctx.bigeopRatio in 0.15..0.30
    condition.contains("식상") && condition.contains("적당") -> ctx.sicksangRatio in 0.15..0.30
    condition.contains("재성") && condition.contains("적당") -> ctx.jaesungRatio in 0.15..0.30
    condition.contains("관성") && condition.contains("적당") -> ctx.gwansungRatio in 0.15..0.30
    condition.contains("인성") && condition.contains("적당") -> ctx.insungRatio in 0.15..0.30

    // 약함(10% 미만)
    condition.contains("비겁") && condition.contains("약") -> ctx.bigeopRatio < 0.10
    condition.contains("식상") && condition.contains("약") -> ctx.sicksangRatio < 0.10
    condition.contains("재성") && condition.contains("약") -> ctx.jaesungRatio < 0.10
    condition.contains("관성") && condition.contains("약") -> ctx.gwansungRatio < 0.10
    condition.contains("정관") && condition.contains("약") -> ctx.gwansungRatio < 0.10
    condition.contains("정인") && condition.contains("약") -> ctx.insungRatio < 0.10
    condition.contains("편인") && condition.contains("약") -> ctx.insungRatio < 0.10
    condition.contains("인성") && condition.contains("약") -> ctx.insungRatio < 0.10

    // 동반(15%+) — 카테고리 키워드와 개별 십신명 모두 포함 처리
    condition.contains("비겁") || condition.contains("비견") || condition.contains("겁재") ->
        ctx.bigeopRatio >= 0.15
    condition.contains("식상") || condition.contains("식신") || condition.contains("상관") ->
        ctx.sicksangRatio >= 0.15
    condition.contains("재성") || condition.contains("정재") || condition.contains("편재") ->
        ctx.jaesungRatio >= 0.15
    condition.contains("관성") || condition.contains("정관") || condition.contains("편관") ->
        ctx.gwansungRatio >= 0.15
    condition.contains("인성") || condition.contains("정인") || condition.contains("편인") ->
        ctx.insungRatio >= 0.15

    else -> false
}

/**
 * 변주의 발현 전제(SipsinVariation.requires)가 현재 사주에서 충족되는지.
 *
 * "신강"/"신약"은 강약 분석 결과(isStrong)로, 그 외 토큰은 matchesVariation 규칙으로 평가.
 * 모두 충족해야 true. requires가 비어 있으면(전제 없음) true.
 */
fun requiresSatisfied(ctx: SipsinContext, isStrong: Boolean, requires: List<String>): Boolean =
    requires.all { req ->
        when {
            req.contains("신강") -> isStrong
            req.contains("신약") -> !isStrong
            else -> matchesVariation(ctx, req)
        }
    }

/**
 * 사주 8셀(천간 4 + 지지 4)에 등장하는 십신×기둥 카드에서, 현재 사주에 매칭되는 변주만 추려
 * AI 페이로드용으로 직렬화. condition/effect에 tone·domains·발현전제 충족 여부(requires_met)를 부가.
 *
 * 일간 본원(本元)·미매핑 셀은 건너뛰고, (십신, 조건, 효과) 기준으로 중복 제거.
 */
fun matchedChartVariations(
    pillars: List<PillarDetail>,
    ctx: SipsinContext,
    isStrong: Boolean
): List<Map<String, Any>> {
    val seen = HashSet<String>()
    val out = ArrayList<Map<String, Any>>()
    pillars.forEachIndexed { idx, pd ->
        listOf(pd.stemSipsin, pd.branchSipsin).forEach { s ->
            if (s == "本元") return@forEach
            val card = com.samramanshang.manseryeok.orrery.constants.SIPSIN_BY_PILLAR[s to idx]
                ?: return@forEach
            card.variations.forEach { v ->
                if (!matchesVariation(ctx, v.condition)) return@forEach
                if (!seen.add("${card.sipsinHanja}|${v.condition}|${v.effect}")) return@forEach
                out += mapOf(
                    "sipsin" to card.sipsinKr,
                    "pillar" to card.pillarLabel,
                    "condition" to v.condition,
                    "effect" to v.effect,
                    "tone" to v.tone,
                    "domains" to v.domains,
                    "requires_met" to requiresSatisfied(ctx, isStrong, v.requires)
                )
            }
        }
    }
    return out
}

/* ─ 통관용신 (격국 결합) ─────────────────────────── */

/** 통관 룰 식별자 — 격국별 우선순위 정렬에 사용 */
private enum class TongguanRule {
    /** 일간 강 + 관살 강 → 인성 통관 */
    StrongDay_StrongCtrlBy_To_Insung,
    /** 일간 강 + 재성 강 → 식상 통관 */
    StrongDay_StrongCtrlTo_To_Sicksang,
    /** 일간 약 + 관살 강 → 살인상생 (인성 통관) */
    WeakDay_StrongCtrlBy_To_Insung,
    /** 재성 강 + 인성 강 → 비겁 통관 (재극인 풀이) */
    StrongCtrlTo_StrongInsung_To_Bigeop,
    /** 인성 강 + 식상 강 → 비겁 통관 (효신탈식 풀이) */
    StrongInsung_StrongSicksang_To_Bigeop
}

/**
 * 격국별 통관 룰 우선순위.
 * 첫 매칭 룰이 채택되므로 순서가 의미 있음.
 *
 * 명리 정설 기반:
 *   - 정관격/편관격: 일간이 약하면 살인상생 최우선, 강하면 식상 제압
 *   - 재성격: 비겁이 재성을 군겁쟁재 하면 식상으로 풀기, 재극인이면 비겁
 *   - 인성격: 식상이 인성을 깨면 비겁으로 보호 (효신탈식)
 *   - 식상격: 인성이 식상을 압박하면 비겁
 *   - 비겁격: 군겁쟁재 풀이 식상 우선
 */
// 격국별 통관용신 룰 매트릭스 — 자평진전 정설 기반.
//
// 12 정격(正格)을 5 그룹으로 통합:
//   1. 관성격(정관/편관) — 살인상생·식신제살 우선
//   2. 재성격(정재/편재) — 재극인·군겁쟁재 풀이
//   3. 인성격(정인/편인) — 효신탈식 보호
//   4. 식상격(식신/상관) — 효신탈식 → 살인상생 보강
//   5. 비겁격(건록/양인/비견/겁재) — 군겁쟁재 우선·살인상생 보조
//
// 미커버 영역 (별도 시스템에서 처리):
//   - 외격(從格): jongwang/jongsal/jongjae/jongah 등 — `SipsinCombinationPatterns.kt`
//   - 화기격(化氣格): 천간합화 분석 — `detectHwaha` (hwaha analyzer)
private fun rulesFor(geokcuk: String?): List<TongguanRule> = when (geokcuk) {
    "正官格", "偏官格" -> listOf(
        TongguanRule.WeakDay_StrongCtrlBy_To_Insung,      // 살인상생
        TongguanRule.StrongDay_StrongCtrlBy_To_Insung,
        TongguanRule.StrongDay_StrongCtrlTo_To_Sicksang
    )
    "正財格", "偏財格" -> listOf(
        TongguanRule.StrongCtrlTo_StrongInsung_To_Bigeop, // 재극인
        TongguanRule.StrongDay_StrongCtrlTo_To_Sicksang   // 군겁쟁재 풀이
    )
    "正印格", "偏印格" -> listOf(
        TongguanRule.StrongInsung_StrongSicksang_To_Bigeop // 효신탈식
    )
    "食神格", "傷官格" -> listOf(
        TongguanRule.StrongInsung_StrongSicksang_To_Bigeop, // 효신탈식 우선
        TongguanRule.StrongDay_StrongCtrlBy_To_Insung
    )
    "建祿格", "羊刃格", "比肩格", "劫財格" -> listOf(
        TongguanRule.StrongDay_StrongCtrlTo_To_Sicksang,   // 군겁쟁재
        TongguanRule.StrongDay_StrongCtrlBy_To_Insung
    )
    // 격국 미상이면 기본 3룰 (B-lite 기존 순서 유지)
    else -> listOf(
        TongguanRule.StrongDay_StrongCtrlBy_To_Insung,
        TongguanRule.StrongDay_StrongCtrlTo_To_Sicksang,
        TongguanRule.WeakDay_StrongCtrlBy_To_Insung
    )
}

/**
 * S-01 (audit Phase 1): 화기격 보정 배선 헬퍼.
 *
 * [countElements] → [applyHwahaAdjustment] → [computeTongguan] / [analyzeStrengthShared] 경로를
 * 단일 진입점으로 제공. 쟁합/투합 게이트(S-08)가 이미 적용된 [detectHwaha] 결과를 인자로 받는다.
 *
 * 사용법 (SajuScreen/YongsinAnalyzer 통합 흐름):
 *   val rawCounts  = countElements(pillars)
 *   val hwaha      = detectHwaha(pillars, rawCounts)   // S-08 게이트 적용
 *   val adjCounts  = applyHwahaToCountsForStrength(rawCounts, hwaha)
 *   val tongguan   = computeTongguan(dayElement, adjCounts, geokcuk)
 *   val sr         = analyzeStrengthShared(pillars)    // 십신 기반 — 별도 경로
 *
 * 주의: analyzeStrengthShared 는 오행 카운트가 아닌 십신 문자열 기반이므로
 *       화기격 보정이 강약 판정 자체에는 직접 반영되지 않는다(억부법 정통 원칙상 합화는
 *       오행 분포 보정이지 십신 카운팅 변경이 아님). 보정은 통관(오행 데드락) 계산에 반영.
 */
fun applyHwahaToCountsForStrength(
    rawCounts: Map<Element, Int>,
    hwaha: List<HwahaResult>,
    // V3.04 (audit): [5·valid] 일간은 化하지 않으므로(羈絆) 차감 제외 — 호출부가 일간 천간을 넘기도록 노출.
    dayStem: String = ""
): Map<Element, Int> = applyHwahaAdjustment(rawCounts, hwaha, dayStem)

/**
 * 통관 임계 — 사주마다 베이스라인이 다르므로 절대 카운트 대신 비율로 판정.
 *
 * 정통 명리 표준에 가까운 값:
 *   - 일간 강(強): 30% 이상 (사주 절반 가까이 일간 오행)
 *   - 일간 약(弱): 18% 이하 (생조 매우 부족)
 *   - 상대 오행 강: 15% 이상 (관살·재성·인성·식상 등이 의미 있게 형성)
 */
private const val PCT_DAY_STRONG = 0.30
private const val PCT_DAY_WEAK = 0.18
private const val PCT_OPPONENT_STRONG = 0.15

/**
 * 통관용신: 격국 + 데드락 카운트 결합. 임계는 사주 전체 합 대비 비율.
 *
 * 비율 방식 채택 이유: 지장간 가중 카운트는 사주마다 베이스라인이 90~130 범위로
 * 다르므로 절대 카운트 임계는 사주마다 의미가 달라짐. 비율로 정규화하면 안정.
 *
 * @param dayElement 일간 오행
 * @param counts 사주 오행 가중 카운트(천간+지장간, 10× 스케일)
 * @param geokcuk 주격명 한자 (예: "正官格"). null이면 기본 룰
 * @return 데드락 없으면 null
 */
fun computeTongguan(
    dayElement: Element,
    counts: Map<Element, Int>,
    geokcuk: String? = null
): YongsinPerspective? {
    val e = Element.entries
    val idx = e.indexOf(dayElement)
    val genBy = e[(idx + 4) % 5]   // 인성
    val genTo = e[(idx + 1) % 5]   // 식상
    val clashes = YS_OHAENG_CLASH
    val ctrlToE = e[clashes[idx]!!]                                     // 재성
    val ctrlByE = e[clashes.entries.first { it.value == idx }.key]      // 관살

    val total = counts.totalSum().toDouble()
    val dayPct = (counts[dayElement] ?: 0) / total
    val ctrlByPct = (counts[ctrlByE] ?: 0) / total
    val ctrlToPct = (counts[ctrlToE] ?: 0) / total
    val genByPct = (counts[genBy] ?: 0) / total
    val genToPct = (counts[genTo] ?: 0) / total

    val isDayStrong = dayPct >= PCT_DAY_STRONG
    val isDayWeak = dayPct <= PCT_DAY_WEAK
    val isCtrlByStrong = ctrlByPct >= PCT_OPPONENT_STRONG
    val isCtrlToStrong = ctrlToPct >= PCT_OPPONENT_STRONG
    val isGenByStrong = genByPct >= PCT_OPPONENT_STRONG
    val isGenToStrong = genToPct >= PCT_OPPONENT_STRONG

    // 룰별 매칭 시도. 격국 우선순위대로 첫 매칭 반환.
    for (rule in rulesFor(geokcuk)) {
        when (rule) {
            TongguanRule.StrongDay_StrongCtrlBy_To_Insung ->
                if (isDayStrong && isCtrlByStrong) return YongsinPerspective(
                    school = "통관",
                    element = genBy,
                    reason = "일간(${dayElement.hangul})과 관살(${ctrlByE.hangul})이 충돌 — " +
                        "${genBy.hangul}${josaIGa(genBy.hangul)} 사이를 이어 ${ctrlByE.hangul}→${genBy.hangul}→${dayElement.hangul} 흐름을 만듦",
                    isEmergency = true
                )
            TongguanRule.StrongDay_StrongCtrlTo_To_Sicksang ->
                if (isDayStrong && isCtrlToStrong) return YongsinPerspective(
                    school = "통관",
                    element = genTo,
                    reason = "일간(${dayElement.hangul})이 재성(${ctrlToE.hangul})을 직접 극함 — " +
                        "${genTo.hangul}${josaIGa(genTo.hangul)} 사이를 이어 ${dayElement.hangul}→${genTo.hangul}→${ctrlToE.hangul} 흐름으로 풀어줌",
                    isEmergency = true
                )
            TongguanRule.WeakDay_StrongCtrlBy_To_Insung ->
                if (isDayWeak && isCtrlByStrong) return YongsinPerspective(
                    school = "통관",
                    element = genBy,
                    reason = "관살(${ctrlByE.hangul})이 약한 일간을 압박 — " +
                        "${genBy.hangul}${josaIGa(genBy.hangul)} 살인상생(殺印相生)으로 일간을 보호",
                    isEmergency = true
                )
            TongguanRule.StrongCtrlTo_StrongInsung_To_Bigeop ->
                if (isCtrlToStrong && isGenByStrong) return YongsinPerspective(
                    school = "통관",
                    element = dayElement,
                    reason = "재성(${ctrlToE.hangul})이 인성(${genBy.hangul})을 깨려 함(재극인) — " +
                        "비겁(${dayElement.hangul})이 재성을 분담해 인성을 보호",
                    isEmergency = true
                )
            TongguanRule.StrongInsung_StrongSicksang_To_Bigeop ->
                if (isGenByStrong && isGenToStrong) return YongsinPerspective(
                    school = "통관",
                    element = dayElement,
                    reason = "인성(${genBy.hangul})이 식상(${genTo.hangul})을 깨려 함(효신탈식) — " +
                        "비겁(${dayElement.hangul})이 다리를 놓아 풀어줌",
                    isEmergency = true
                )
        }
    }
    return null
}

/* ─ 조후용신 ───────────────────────────────────────── */

/**
 * 조후용신 — 월지 기준 1차 판정 (일간 무관).
 *
 * 하위 함수 [computeJohu] 에서 월지×일간 세분화 결과가 없으면 이 값이 사용됨.
 *
 * 정설(궁통보감) 대분류:
 *   亥·子·丑(겨울) + 寅(초봄 잔한)   → 火 필요 (따뜻함)
 *   巳·午·未(여름) + 申(초가을 잔열)  → 水 필요 (시원함)
 *   卯·辰·酉·戌                       → 기본 조후 불필요 (세분화에서 일간별 예외 처리)
 */
private fun baseJohu(monthBranch: String): YongsinPerspective? {
    val coldMonths = setOf("亥", "子", "丑", "寅")
    val hotMonths  = setOf("巳", "午", "未", "申")
    return when (monthBranch) {
        in coldMonths -> YongsinPerspective(
            school = "조후",
            element = Element.FIRE,
            reason = "한랭한 ${monthBranch}월 — 火(따뜻함)가 사주를 데워줌"
        )
        in hotMonths -> YongsinPerspective(
            school = "조후",
            element = Element.WATER,
            reason = "무더운 ${monthBranch}월 — 水(서늘함)가 사주를 식혀줌"
        )
        else -> null
    }
}

/**
 * 조후용신: 월지 × 일간 조합 세분화 (S-03, audit Phase 1).
 *
 * 궁통보감(窮通寶鑑) 기준 환절기 월(辰戌丑未·卯·酉)의 일간별 조후 필요 케이스:
 *
 * ▸ 辰월(3월 토기·水창고):
 *   - 壬·癸 일간: 辰은 水의 입묘(入墓) — 水가 약해져 木·火 조후 필요
 *   - 甲·乙 일간: 辰의 목기 약화로 뿌리 흔들 → 火 필요
 *   - 그 외: 조후 불필요 (적당한 습토)
 *
 * ▸ 戌월(9월 토기·火창고):
 *   - 丙·丁 일간: 戌은 火의 입묘 — 火가 약해져 木 조후 필요
 *   - 壬·癸 일간: 戌월 건조 → 水 보완 필요
 *   - 그 외: 조후 불필요
 *
 * ▸ 丑월(1월 겨울 토기):
 *   - 기본 겨울 한랭 → 火 (baseJohu 와 동일, 단 壬·癸는 특히 강조)
 *
 * ▸ 未월(7월 여름 토기):
 *   - 기본 여름 더위 → 水 (baseJohu 와 동일, 단 丙·丁는 특히 강조)
 *
 * ▸ 卯월(2월 목기):
 *   - 庚·辛 일간: 金이 木에 쇠약 → 土·火 보강 필요
 *   - 그 외: 조후 불필요
 *
 * ▸ 酉월(8월 금기):
 *   - 甲·乙 일간: 木이 金에 쇠약 → 水·火 보강 필요
 *   - 丙·丁 일간: 酉가 火를 극 → 木 필요
 *   - 그 외: 조후 불필요
 *
 * @param monthBranch 월지 한자
 * @param dayElement  일간 오행 (null이면 1차 기준만 사용)
 */
fun computeJohu(monthBranch: String, dayElement: Element? = null): YongsinPerspective? {
    // 환절기 월 세분화 — 일간이 있을 때만 적용
    if (dayElement != null) {
        when (monthBranch) {
            "辰" -> when (dayElement) {
                // 壬·癸: 辰은 水의 입묘 — 수기가 창고에 갇혀 약해짐 → 木(식상)·火 보완
                Element.WATER -> return YongsinPerspective(
                    school = "조후",
                    element = Element.TREE,
                    reason = "辰월은 水의 입묘지 — ${dayElement.hangul} 일간의 수기가 갇혀 木으로 설기하고 방향을 틔워야 함"
                )
                // 甲·乙: 辰의 목기(木氣)가 쇠잔 → 水(인성)·火(식상) 보완
                Element.TREE -> return YongsinPerspective(
                    school = "조후",
                    element = Element.FIRE,
                    reason = "辰월 목기 쇠잔 — ${dayElement.hangul} 일간의 기운을 火로 설기해 활력 보완"
                )
                else -> return null  // 그 외 → 辰월 조후 불필요
            }
            "戌" -> when (dayElement) {
                // 丙·丁: 戌은 火의 입묘 — 화기 약해짐 → 木(인성) 보완
                Element.FIRE -> return YongsinPerspective(
                    school = "조후",
                    element = Element.TREE,
                    reason = "戌월은 火의 입묘지 — ${dayElement.hangul} 일간의 화기가 갇혀 木(인성)으로 생조 필요"
                )
                // 壬·癸: 戌월 건조 토왕 → 水 일간 뿌리 흔들림 → 金(인성) 보완
                Element.WATER -> return YongsinPerspective(
                    school = "조후",
                    element = Element.METAL,
                    reason = "戌월 건조·토왕 — ${dayElement.hangul} 일간이 마르지 않도록 金(인성)으로 수원 보충"
                )
                else -> return null
            }
            "卯" -> when (dayElement) {
                // 庚·辛: 卯(木)에 金 일간이 극을 받아 쇠약 → 土(인성) 보완
                Element.METAL -> return YongsinPerspective(
                    school = "조후",
                    element = Element.EARTH,
                    reason = "卯월 목왕 — ${dayElement.hangul} 일간이 목기에 극을 받아 土(인성)로 강화 필요"
                )
                else -> return null
            }
            "酉" -> when (dayElement) {
                // 甲·乙: 酉(金)에 木 일간이 극을 받아 쇠약 → 水(인성) 보완
                Element.TREE -> return YongsinPerspective(
                    school = "조후",
                    element = Element.WATER,
                    reason = "酉월 금왕 — ${dayElement.hangul} 일간이 금기에 극을 받아 水(인성)로 강화 필요"
                )
                // 丙·丁: 酉가 火를 극 → 木(인성) 보완
                Element.FIRE -> return YongsinPerspective(
                    school = "조후",
                    element = Element.TREE,
                    reason = "酉월 금왕 — ${dayElement.hangul} 일간이 금기에 극을 받아 木(인성)으로 뿌리 보강"
                )
                else -> return null
            }
            // 丑·未는 baseJohu 와 방향 동일 — 강도 강조 메시지만 추가
            "丑" -> when (dayElement) {
                Element.WATER -> return YongsinPerspective(
                    school = "조후",
                    element = Element.FIRE,
                    reason = "丑월 혹한 — ${dayElement.hangul} 일간은 특히 차가워 火(식상)로 생동감 필요"
                )
                else -> return baseJohu(monthBranch)
            }
            "未" -> when (dayElement) {
                Element.FIRE -> return YongsinPerspective(
                    school = "조후",
                    element = Element.WATER,
                    reason = "未월 혹서 — ${dayElement.hangul} 일간은 특히 뜨거워 水(관성)로 냉각 필요"
                )
                else -> return baseJohu(monthBranch)
            }
        }
    }
    // 환절기 외 또는 일간 null → 1차 기준
    return baseJohu(monthBranch)
}

/* ─ 조후 보좌신(補佐神) — 궁통보감 일간×월지 주/보좌 2단 (WS-4 정밀도 심화, additive) ────── */

/**
 * 조후 주(主)/보좌(補佐) 2단 결과.
 *
 * ⚠️ [computeJohu]/[baseJohu] 의 기존 반환 시그니처와 1차 조후 판정 문자열은 이 함수와 완전히
 *    독립이다 — 기존 호출부(YongsinAnalyzerTest·SajuToneRegressionTest 등)에 영향 없음.
 */
data class JohuBojwaResult(
    /** 主(주) 조후용신 오행 — computeJohu 의 판정과 사상은 같으나 실제 일간(10干) 기준 세분화 */
    val primary: Element,
    /** 佐(보좌) 조후용신 오행 */
    val secondary: Element,
    val reason: String
)

/**
 * 甲木 총론(궁통보감) 중 확실히 검증 가능한 4개월만 등재 — "劈甲引丁"(子) 및 그 인접/대칭 케이스.
 *
 * 근거(궁통보감 甲木 월령 총론, 여러 명리 문헌에 공통 재인용되는 정통 구절):
 *   子月: "天寒地凍，木性極寒 — 先取庚金劈甲，方能引丁，庚丁二者不可缺一" → 主 丁火(온기) / 佐 庚金(甲을 쪼개 丁을 引火)
 *   丑月: 子月과 동일한 혹한 연장선(궁통보감 표에서도 丑월은 子월과 동일 방향으로 취급) → 主 丁火 / 佐 庚金
 *   午月: "木性洩焦 — 先取癸水潤之，次取庚金以佐" → 主 癸水(조열 해소) / 佐 庚金(水源 보강)
 *   未月: 午月과 대칭적인 늦여름 혹서 연장 → 主 癸水 / 佐 庚金
 *
 * 나머지 8개월(寅卯辰巳申酉戌亥)과 甲 이외 9干은 확신 부족 — 의도적으로 미등재(null 반환).
 * "전체 120셀 강제 아님" 지침에 따라 확실한 대표 케이스만 채움.
 */
private val JOHU_BOJWA_GAP_MOK: Map<String, JohuBojwaResult> = mapOf(
    "子" to JohuBojwaResult(
        primary = Element.FIRE,
        secondary = Element.METAL,
        reason = "궁통보감 甲木 子月: 天寒地凍 — 庚金(佐)으로 甲木을 쪼개야 丁火(主)를 引火할 수 있음(劈甲引丁)"
    ),
    "丑" to JohuBojwaResult(
        primary = Element.FIRE,
        secondary = Element.METAL,
        reason = "궁통보감 甲木 丑月: 혹한이 子月에서 이어짐 — 丁火(主)로 데우고 庚金(佐)으로 引火를 도움"
    ),
    "午" to JohuBojwaResult(
        primary = Element.WATER,
        secondary = Element.METAL,
        reason = "궁통보감 甲木 午月: 木性洩焦 — 癸水(主)로 조열을 식히고 庚金(佐)으로 水源을 보강"
    ),
    "未" to JohuBojwaResult(
        primary = Element.WATER,
        secondary = Element.METAL,
        reason = "궁통보감 甲木 未月: 午月의 혹서가 이어짐 — 癸水(主)로 식히고 庚金(佐)으로 보조"
    )
)

/**
 * 조후 보좌신(補佐神) — 일간×월지 궁통보감 2단(主/佐) 조회.
 *
 * [computeJohu]/[baseJohu] 는 그대로 유지(기존 반환값·문자열 불변) — 이 함수는 순수 additive 조회다.
 * 확실히 검증 가능한 甲木 4개월(子丑午未)만 등재. 그 외 조합은 null(추측 채움 금지).
 *
 * @param dayStem 일간 한자 1글자
 * @param monthBranch 월지 한자 1글자
 */
fun computeJohuBojwa(dayStem: String, monthBranch: String): JohuBojwaResult? {
    if (dayStem != "甲") return null
    return JOHU_BOJWA_GAP_MOK[monthBranch]
}

/* ─ 상황별 처방 텍스트 (A) ────────────────────────── */

/**
 * 일간 × 통관 오행 조합 → 상황별 처방 텍스트.
 *
 * 명리에서 통관용신은 단순 "필요 오행"이 아니라 **그 오행의 자질을 행동으로 빌려오는 것**.
 * 예) 水일간이 金 통관 = 금을 늘리라는 게 아니라 "냉철한 이성·수용"을 의식적으로 쓰라는 의미.
 *
 * 5×5=25 케이스 중 의미 있는 자기-극 데드락 통관·재극인 통관 16개를 정적 매핑.
 * 빠진 키는 fallback 으로 일반 안내문 사용.
 */
private val ADVICE_TABLE: Map<Pair<Element, Element>, TongguanAdvice> = mapOf(
    // ── 일간 강 + 관살 강 → 인성 통관 (Y극X 케이스, X=일간, Y=관살) ──
    (Element.WATER to Element.METAL) to TongguanAdvice(
        headline = "윤활유로 쓰는 금(金)",
        context = "관계·책임의 데드락 (土→水)",
        howTo = "평소 부족한 '냉철한 이성과 수용하는 자세'를 외부에서 빌려와 대화. " +
            "상대의 날카로운 말을 필터 없이 받지 말고 데이터로만 수용.",
        warning = "금을 늘리라는 뜻이 아니에요. 평소 약한 인성(받아들임) 자질을 의식적으로 쓰라는 의미."
    ),
    (Element.TREE to Element.WATER) to TongguanAdvice(
        headline = "윤활유로 쓰는 수(水)",
        context = "강한 추진력이 압박과 충돌 (金→木)",
        howTo = "직진 대신 '학습·정보 수집·관조'로 우회. 결정 전 한 박자 늦춰 자료 한 번 더.",
        warning = "수를 키우라는 게 아니라 평소 약한 인내·관찰 습관을 의식적으로."
    ),
    (Element.FIRE to Element.TREE) to TongguanAdvice(
        headline = "윤활유로 쓰는 목(木)",
        context = "감정·표현이 한계와 충돌 (水→火)",
        howTo = "충동적 발화 대신 '계획·구조화'를 의식적으로. 큰 그림을 그린 뒤 행동.",
        warning = "목을 키우라는 게 아니라 평소 약한 계획·체계 습관을 빌려오라는 의미."
    ),
    (Element.EARTH to Element.FIRE) to TongguanAdvice(
        headline = "윤활유로 쓰는 화(火)",
        context = "고집·정체가 변화 압력과 충돌 (木→土)",
        howTo = "묵묵히 버티는 모드 대신 '열정·표현·소통'을 의식적으로 쓰기. 감정을 말로 풀기.",
        warning = "화를 키우라는 게 아니라 평소 약한 표현·전달 습관을 의식적으로."
    ),
    (Element.METAL to Element.EARTH) to TongguanAdvice(
        headline = "윤활유로 쓰는 토(土)",
        context = "예리함이 자기 한계와 충돌 (火→金)",
        howTo = "날 선 분석·비판 대신 '포용·중립·공감'으로 받아들이기. 결과보다 과정 인정.",
        warning = "토를 키우라는 게 아니라 평소 약한 포용·신뢰 습관을 빌려오라는 의미."
    ),
    // ── 일간 강 + 재성 강 → 식상 통관 (X극Y 케이스, X=일간, Y=재성) ──
    (Element.WATER to Element.TREE) to TongguanAdvice(
        headline = "윤활유로 쓰는 목(木)",
        context = "고집과 재물·이성 충돌 (水→火)",
        howTo = "직접 부딪히는 대신 '학습·기획'으로 우회 처리. 협상 전 자료·근거 준비.",
        warning = "목을 키우라는 게 아니라 식상(표현·계획)을 의식적으로 끼워 넣으라는 의미."
    ),
    (Element.TREE to Element.FIRE) to TongguanAdvice(
        headline = "윤활유로 쓰는 화(火)",
        context = "추진력과 결과·재물 충돌 (木→土)",
        howTo = "혼자 밀어붙이지 말고 '표현·소통·열정'으로 사람 끌어모으기. 비전 공유.",
        warning = "화를 키우라는 게 아니라 평소 약한 표현 습관을 의식적으로 활성화."
    ),
    (Element.FIRE to Element.EARTH) to TongguanAdvice(
        headline = "윤활유로 쓰는 토(土)",
        context = "감정 폭발과 자원·금전 충돌 (火→金)",
        howTo = "감정 우선 대응 대신 '안정·인내·관리'로 한 박자 늦추기. 감정 → 시스템화.",
        warning = "토를 키우라는 게 아니라 평소 약한 절제·관리 습관을 빌려오라는 의미."
    ),
    (Element.EARTH to Element.METAL) to TongguanAdvice(
        headline = "윤활유로 쓰는 금(金)",
        context = "고집·책임감과 사람·인연 충돌 (土→水)",
        howTo = "묵묵히 짊어지는 대신 '결단·정리·필터링'으로 정리. 우선순위 칼같이.",
        warning = "금을 키우라는 게 아니라 평소 약한 결단·정리 습관을 의식적으로."
    ),
    (Element.METAL to Element.WATER) to TongguanAdvice(
        headline = "윤활유로 쓰는 수(水)",
        context = "예리함과 학습·관계 충돌 (金→木)",
        howTo = "결론부터 말하지 말고 '관조·경청·데이터'로 듣기 우선. 즉답 대신 시간 두기.",
        warning = "수를 키우라는 게 아니라 평소 약한 관조·수용 습관을 의식적으로."
    ),
    // ── 재극인 통관 (X=일간, 비겁으로 인성 보호) ──
    (Element.WATER to Element.WATER) to TongguanAdvice(
        headline = "윤활유로 쓰는 수(水) — 비겁",
        context = "재극인(財剋印) — 자원 압박이 받아들임을 깨뜨림",
        howTo = "혼자 짊어지지 말고 '동료·동맹'을 의식적으로 활용. 부담을 분담.",
        warning = "비겁(자기 의지)을 키우라는 게 아니라 협력 자원을 빌려오라는 의미."
    ),
    (Element.TREE to Element.TREE) to TongguanAdvice(
        headline = "윤활유로 쓰는 목(木) — 비겁",
        context = "재극인(財剋印) — 결과 압박이 학습을 깨뜨림",
        howTo = "혼자 처리 대신 '동료·스터디'로 학습 환경 보호. 자존심 내려놓기.",
        warning = "비겁을 키우라는 게 아니라 의지·연대 습관을 의식적으로."
    ),
    (Element.FIRE to Element.FIRE) to TongguanAdvice(
        headline = "윤활유로 쓰는 화(火) — 비겁",
        context = "재극인(財剋印) — 자원 압박이 직관을 깨뜨림",
        howTo = "혼자 분투 대신 '팀워크·열정 공유'. 비전 동지 만들기.",
        warning = "비겁(자기 동력)을 키우라는 게 아니라 협력·비전 공유 습관을 의식적으로."
    ),
    (Element.EARTH to Element.EARTH) to TongguanAdvice(
        headline = "윤활유로 쓰는 토(土) — 비겁",
        context = "재극인(財剋印) — 자원 압박이 안정감을 깨뜨림",
        howTo = "혼자 책임 대신 '동료·가족 분담'. 신뢰망 활용.",
        warning = "비겁을 키우라는 게 아니라 분담·신뢰 습관을 의식적으로."
    ),
    (Element.METAL to Element.METAL) to TongguanAdvice(
        headline = "윤활유로 쓰는 금(金) — 비겁",
        context = "재극인(財剋印) — 자원 압박이 원칙을 깨뜨림",
        howTo = "혼자 칼 휘두르기 대신 '동맹·기준 공유'. 원칙 동지 만들기.",
        warning = "비겁을 키우라는 게 아니라 협력·기준 공유 습관을 의식적으로."
    )
)

/**
 * 일간 + 통관 오행 → 처방 텍스트. 키 없으면 일반 안내문 fallback.
 */
fun lookupTongguanAdvice(dayElement: Element, tongguanElement: Element): TongguanAdvice {
    return ADVICE_TABLE[dayElement to tongguanElement] ?: TongguanAdvice(
        headline = "윤활유로 쓰는 ${tongguanElement.hangul}(${tongguanElement.hanja})",
        context = "일간 ${dayElement.hangul} 데드락 풀이",
        howTo = "${tongguanElement.hangul}의 자질(이성·수용·결단·연대 등)을 평소 약한 영역으로 의식적으로 끌어쓰기.",
        warning = "${tongguanElement.hangul}${josaEulReul(tongguanElement.hangul)} 늘리라는 뜻이 아니에요. 평소 부족한 자질을 행동으로 빌려오라는 의미."
    )
}

/* ─ 병약(病藥) 용신 ──────────────────────────────────── */

/**
 * S-11: 병약(病藥) 용신 관점 결과.
 *
 * @param diseaseElement 病 오행 — 사주에서 과다하거나 격국을 깨는 흉신
 * @param cureElement    藥 오행 — 病을 제거·억제하는 오행 (병을 극하는 오행)
 * @param diseaseReason  병 오행이 흉신인 이유
 * @param cureReason     약 오행이 처방이 되는 이유
 */
data class ByeongyakResult(
    val diseaseElement: Element,
    val cureElement: Element,
    val diseaseReason: String,
    val cureReason: String
)

/**
 * S-11: 병약(病藥) 용신 분석.
 *
 * 정설(자평진전·명리신론):
 *   사주에 병(흉신·기신)이 뚜렷할 때 그 病을 극하는 오행이 藥(약신·용신)이 된다.
 *   병약 용신은 억부·통관 용신과 별개로 "지금 당장 제거해야 할 걸림돌"에 초점.
 *
 * 병 판정 기준 (자평진전):
 *   - 사주에서 일간을 과하게 제압하는 오행이 도처에 있을 때 (비중 35%+)
 *   - 격국을 파괴하는 오행(忌神·仇神)이 강하게 존재할 때
 *
 * 이 함수는 **억부 기신(gi) 중 가장 비중이 큰 단일 오행**을 病으로 특정하고,
 * 그것을 극하는 오행을 藥으로 명명한다. 병이 뚜렷하지 않으면(비중 35% 미만) null 반환.
 *
 * @param counts   [countElements] 결과 (화기격 보정 적용 후 adjCounts 권장)
 * @param gi       억부 기신 오행 목록 ([deriveYongGiShared] 결과)
 * @param dayElement 일간 오행
 * @return null이면 병이 뚜렷하지 않음(억부·통관·조후로 충분)
 */
fun computeByeongyak(
    counts: Map<Element, Int>,
    gi: List<Element>,
    dayElement: Element
): ByeongyakResult? {
    if (gi.isEmpty()) return null

    val total = counts.values.sum().coerceAtLeast(1).toDouble()

    // 기신 중 비중이 가장 큰 오행 = 病
    val diseaseElement = gi.maxByOrNull { (counts[it] ?: 0) } ?: return null
    val diseasePct = (counts[diseaseElement] ?: 0) / total

    // 병이 뚜렷해야(35%+) 병약 관점 적용 (명리신론 병약 입격 기준 참고)
    if (diseasePct < 0.35) return null

    // 藥 = 病을 극하는 오행
    val e = Element.entries
    val diseaseIdx = e.indexOf(diseaseElement)
    val clashes = YS_OHAENG_CLASH
    val cureElement = e[clashes.entries.first { it.value == diseaseIdx }.key]

    val diseaseReason = "${diseaseElement.hangul}(病) 기운이 사주의 ${(diseasePct * 100).toInt()}%를 차지해 " +
        "일간 ${dayElement.hangul}${josaEulReul(dayElement.hangul)} 과하게 압박하거나 格을 깨는 구조예요."
    val cureReason = "${cureElement.hangul}(藥)이 ${diseaseElement.hangul}${josaEulReul(diseaseElement.hangul)} 직접 제압(극)해 " +
        "사주의 균형을 회복시키는 역할을 해요."

    return ByeongyakResult(
        diseaseElement = diseaseElement,
        cureElement = cureElement,
        diseaseReason = diseaseReason,
        cureReason = cureReason
    )
}

/* ─ 합거(合去) 품질 신호 ────────────────────────────────── */

/**
 * 합거 1건의 품질 신호.
 *
 * @param hagoElement  합거된 오행
 * @param isYongsinHit true = 용신 탈출(흉), false = 기신 포박(길)
 * @param narrative    사용자 노출 한 줄 안내 (완화체 -어요)
 */
data class HageoSignal(
    val hagoElement: Element,
    val isYongsinHit: Boolean,
    val narrative: String
)

/**
 * 합거(合去) 용신·기신 교차 분석 — 품질 신호 반환.
 *
 * 정통 명리(자평진전) 합거 해석 원칙:
 *   - 용신 오행의 천간이 합거 → 용신 탈출(脫出) — 용신 효력 약화, 흉 신호
 *   - 기신 오행의 천간이 합거 → 기신 포박(包縛) — 기신 역할 소실, 길 신호
 *   - 합거 오행이 용신도 기신도 아니면 중간 이해관계 → 신호 없음(null)
 *
 * 가합(假合)은 [detectHagoElements] 단계에서 이미 제외되었으므로
 * 이 함수로 들어오는 [hagoElements]는 모두 진합(眞合) 합거 오행.
 *
 * @param hagoElements [detectHagoElements] 결과
 * @param yong         억부 용신 오행 목록 ([deriveYongGiShared] 결과 yong)
 * @param gi           억부 기신 오행 목록 ([deriveYongGiShared] 결과 gi)
 * @return 용신 탈출·기신 포박 신호 목록. 없으면 빈 리스트.
 */
fun computeHageo(
    hagoElements: List<Element>,
    yong: List<Element>,
    gi: List<Element>
): List<HageoSignal> {
    if (hagoElements.isEmpty()) return emptyList()
    val seen = mutableSetOf<Element>()
    val out = mutableListOf<HageoSignal>()
    hagoElements.forEach { el ->
        if (!seen.add(el)) return@forEach  // 동일 오행 중복 신호 1회만
        when {
            el in yong -> out += HageoSignal(
                hagoElement = el,
                isYongsinHit = true,
                narrative = "${el.hangul}(${el.hanja}) 용신이 천간합으로 묶여 탈출(脫出)할 가능성이 있어요. " +
                    "대운·세운에서 이 오행이 들어올 때 효과가 예상보다 약할 수 있는 구조예요."
            )
            el in gi -> out += HageoSignal(
                hagoElement = el,
                isYongsinHit = false,
                narrative = "${el.hangul}(${el.hanja}) 기신이 천간합으로 포박(包縛)돼 역할이 줄어들어요. " +
                    "기신의 부담이 합으로 완화되는 구조라 오히려 안정감으로 작용할 수 있어요."
            )
            // 용신도 기신도 아닌 중간 오행 — 신호 생략
        }
    }
    return out
}
