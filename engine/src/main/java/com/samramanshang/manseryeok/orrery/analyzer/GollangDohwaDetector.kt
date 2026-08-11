package com.samramanshang.manseryeok.orrery.analyzer

import com.samramanshang.manseryeok.orrery.constants.SajuConstants

/**
 * 곤랑도화살(滾浪桃花殺) 검출기 — 정밀 분류 버전.
 *
 * 정의: 천간이 합(干合)을 이루면서 지지가 子卯 형(刑)을 짜는 구조 — 정통명에서는
 * '간합지형(干合支刑)'이라고도 부른다. 일반 도화살·홍염살보다 작용력이 훨씬 강하다.
 *
 * 정통명리 진성 판정 규칙:
 *   1. 일주(日柱) 참여 필수 — 일간이 결합 천간이거나 일지가 子/卯여야 본인 사생활 영역에 작용.
 *      월-년·시-월·시-년 결합은 본인 사생활보다는 '환경·배경' 차원이라 진성 곤랑도화로 보지 않음.
 *   2. 충(沖) 깨트림 해소 — 일간이 결합 두 천간 중 하나와 충(沖)을 이루면 합이 무너져 곤랑도화 무효.
 *      예: 月壬 + 年丁 = 丁壬合 시도 + 日癸가 年丁을 丁癸沖 → 합 해소 → 곤랑도화 무효.
 *
 * 3단계 tier 분류:
 *   - TRUE_GOLLANG: 일주 참여 + 일간 충 깨트림 없음 → ⚠️곤랑도화 표시
 *   - NULLIFIED:    일간이 결합 천간과 충 → 완전 침묵 (사용자에게 흔들림 방지)
 *   - CONTEXTUAL:   일주 비참여 → 완전 침묵 (배경 차원, 본인 사생활 영향 미미)
 *
 * 강도 구분(TRUE_GOLLANG 내부):
 *   - JEOPSHIN(첩신): 인접 두 기둥 — 작용력 최대
 *   - REMOTE(원격):   한 칸 떨어진 두 기둥 — 작용력 중간
 *   - FAR(원거리):    양 끝 기둥(시-년) — 작용력 약함
 */

/** 인력 강도 — 첩신일수록 강함. (TRUE_GOLLANG tier에서만 의미 있음) */
enum class GollangIntensity {
    JEOPSHIN,
    REMOTE,
    FAR
}

/** 곤랑도화 진위 분류 */
enum class GollangTier {
    /** 정통 곤랑도화 — 일주 참여 + 충 깨트림 없음 */
    TRUE_GOLLANG,
    /** 일간 충으로 합이 깨져 무효 — UI 침묵 */
    NULLIFIED,
    /** 일주 비참여 — 배경 차원, UI 침묵 */
    CONTEXTUAL
}

/** 곤랑도화 1건 결과. */
data class GollangDohwaResult(
    /** 子가 위치한 기둥 인덱스 (0=시, 1=일, 2=월, 3=년) */
    val ziPillarIndex: Int,
    /** 卯가 위치한 기둥 인덱스 */
    val myoPillarIndex: Int,
    /** 子 기둥의 천간 */
    val ziStem: String,
    /** 卯 기둥의 천간 */
    val myoStem: String,
    /** 천간합 이름 (예: "戊癸合", "丙辛合") */
    val stemCombineName: String,
    /** 천간합 화기 오행 키 (예: "fire", "water") */
    val hwagiElementKey: String,
    /** 첩신·원격 강도 */
    val intensity: GollangIntensity,
    /** 두 기둥 위치 라벨 (예: "日·時", "月·日") */
    val positionLabel: String,
    /** 진성/무효/배경 차원 분류 */
    val tier: GollangTier,
    /** 일주가 결합에 참여하는지 (일간이 결합 천간 OR 일지가 子/卯) */
    val isDayPillarInvolved: Boolean,
    /** 일간이 결합 천간 하나와 충을 이루어 합을 깬 경우의 충 이름 (예: "丁癸沖"). 없으면 null. */
    val disruptingClash: String?
)

private fun pillarLabel(idx: Int): String = when (idx) {
    0 -> "時"
    1 -> "日"
    2 -> "月"
    3 -> "年"
    else -> "?"
}

private fun intensityOf(i: Int, j: Int): GollangIntensity {
    val distance = kotlin.math.abs(i - j)
    return when (distance) {
        1 -> GollangIntensity.JEOPSHIN
        2 -> GollangIntensity.REMOTE
        else -> GollangIntensity.FAR
    }
}

private fun combineName(s1: String, s2: String): String {
    val ordered = listOf(s1, s2).sorted()
    return "${ordered[0]}${ordered[1]}合"
}

/** 두 천간 사이 충(沖) 관계 키 — STEM_CLASHES 룩업용. 충이면 "甲庚沖" 같은 라벨 반환. */
private fun stemClashLabel(a: String, b: String): String? {
    // STEM_CLASHES 정의 순서를 보존 — 甲庚·乙辛·丙壬·丁癸
    if (SajuConstants.STEM_CLASHES.containsKey("$a,$b")) return "${a}${b}沖"
    if (SajuConstants.STEM_CLASHES.containsKey("$b,$a")) return "${b}${a}沖"
    return null
}

/**
 * 사주에서 곤랑도화 검출 — 진위 분류 포함.
 *
 * @param stems   천간 4자 — [hour, day, month, year] 순 (PillarCalculator 컨벤션)
 * @param branches 지지 4자 — 동일 순서
 * @return 검출된 곤랑도화 결과 리스트. UI에서는 tier == TRUE_GOLLANG 만 노출 권장.
 */
fun detectGollangDohwa(stems: List<String>, branches: List<String>): List<GollangDohwaResult> {
    if (stems.size < 4 || branches.size < 4) return emptyList()
    val results = mutableListOf<GollangDohwaResult>()
    val dayStem = stems[1]

    for (i in 0..3) {
        for (j in 0..3) {
            if (i == j) continue
            if (branches[i] != "子" || branches[j] != "卯") continue
            val si = stems[i]
            val sj = stems[j]
            val combineKey1 = "$si,$sj"
            val combineKey2 = "$sj,$si"
            val combine = SajuConstants.STEM_COMBINES[combineKey1]
                ?: SajuConstants.STEM_COMBINES[combineKey2]
                ?: continue

            val isDayInvolved = (i == 1 || j == 1) ||
                dayStem == si || dayStem == sj

            // 일간이 결합 두 천간 중 하나와 충인지 — 단, 일간이 결합 천간 자체이면 검사 안 함
            val disrupting: String? = if (dayStem != si && dayStem != sj) {
                stemClashLabel(dayStem, si) ?: stemClashLabel(dayStem, sj)
            } else null

            val tier = when {
                disrupting != null -> GollangTier.NULLIFIED
                isDayInvolved -> GollangTier.TRUE_GOLLANG
                else -> GollangTier.CONTEXTUAL
            }

            results += GollangDohwaResult(
                ziPillarIndex = i,
                myoPillarIndex = j,
                ziStem = si,
                myoStem = sj,
                stemCombineName = combineName(si, sj),
                hwagiElementKey = combine.second,
                intensity = intensityOf(i, j),
                positionLabel = "${pillarLabel(i)}·${pillarLabel(j)}",
                tier = tier,
                isDayPillarInvolved = isDayInvolved,
                disruptingClash = disrupting
            )
        }
    }
    return results
}

/**
 * 곤랑도화 결과의 한 줄 풀이 — tier별로 톤 차등.
 */
fun describeGollangDohwa(result: GollangDohwaResult): String = when (result.tier) {
    GollangTier.TRUE_GOLLANG -> {
        val intensityNote = when (result.intensity) {
            GollangIntensity.JEOPSHIN -> "첩신(貼身) 결합 — 작용력 최대. "
            GollangIntensity.REMOTE -> "원격 결합 — 작용력 중간. "
            GollangIntensity.FAR -> "원거리 결합 — 작용력 약함. "
        }
        "곤랑도화(滾浪桃花) — ${result.stemCombineName} + 子卯刑 (${result.positionLabel}). " +
            intensityNote +
            "대중적 매력·예술 감각이 강한 구조이지만, 관계 패턴에 주의가 필요하고 건강 전반에 관심을 기울이기 쉬운 흐름이에요."
    }
    GollangTier.NULLIFIED ->
        "곤랑도화 조건 일부 성립했으나 일간 ${result.disruptingClash}으로 합이 깨져 해소 — 작용 미미."
    GollangTier.CONTEXTUAL ->
        "${result.stemCombineName} + 子卯刑 (${result.positionLabel}) — 일주 비참여 결합. " +
            "본인 사생활보다는 배경·환경 차원의 영향에 가까움."
}
