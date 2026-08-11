package com.samramanshang.manseryeok.orrery.analyzer

import com.samramanshang.manseryeok.orrery.constants.SajuConstants
import com.samramanshang.manseryeok.orrery.model.PillarDetail

/**
 * V3.01 — 정통 격국 단일 산식 (천간 투출 반영) + G-07 Wave 2 위임 API.
 *
 * 메인 사주 화면 (`SajuScreen.determineGeokcuk`), 그룹 궁합의 두 산식
 * (`MemberGyeokAnalyzer.geokCategoryOf`, `GroupCompatibilityAnalyzer.orthodoxCategoryLocal`),
 * 1:1 궁합 (`CompatibilityAnalyzer.orthodoxCategory`) 가 각각 별개로 산출하던 격국 로직을
 * 본 객체로 통합. 어떤 화면에서 보든 동일한 사주는 동일한 격국 라벨을 반환한다.
 *
 * 격국 결정 우선순위 (자평진전 정설):
 *  1. 월지 12운성이 乾祿 → 建祿格 (건록은 투출 무관)
 *  2. 월지 12운성이 帝旺 → 羊刃格 (양인은 투출 무관)
 *  3. 월지 지장간 중 사주 천간(연·월·시간)에 **투출(透出)**한 글자가 있으면 그 십신이 격을 결정.
 *     복수 투출 시 우선순위: 정기(正氣) > 중기(中氣) > 여기(餘氣).
 *     동일 위치 우선순위가 같으면 앞(여기) 쪽보다 뒤(정기) 쪽이 강하므로 마지막으로 발견된 것 채택.
 *  4. 투출자가 없으면 월지 지지 십신(branchSipsin) — 정기 기준 약식 폴백.
 *  5. 빈 / "?" / "本元" → 雜格
 *
 * 폴백은 항상 [GeokgukLabel.JAPGYEOK] — 이전에 그룹 산식이 비겁격으로 잘못 폴백하던 버그 해소.
 *
 * Wave 2 G-07 위임용 공개 API:
 *   [resolveWithDetail] — 격국 라벨 + 투출 상세(투간 천간·위치)를 함께 반환.
 *   `MemberGyeokAnalyzer.geokCategoryOf()` 는 이 함수에 위임하면 됨.
 */
object GeokgukResolver {

    /**
     * G-07 Wave 2 위임 API — 격국 판정 결과 + 투출 상세.
     *
     * @param label         격국 라벨 (GeokgukLabel)
     * @param transparentStem 투출된 천간 글자 (투출 없으면 null — 건록/양인/폴백 포함)
     * @param transparentPosition 투출된 기둥 위치 ("시간", "월간", "년간") — 없으면 null
     * @param resolvedBy    격국 결정 근거 — "건록", "양인", "투출", "폴백" 중 하나
     */
    data class GeokgukDetail(
        val label: GeokgukLabel,
        val transparentStem: String?,
        val transparentPosition: String?,
        val resolvedBy: String
    )

    /**
     * G-07 Wave 2 위임 함수 — 4기둥 전체에서 격국 라벨 + 투출 상세 함께 반환.
     *
     * 시그니처:
     *   GeokgukResolver.resolveWithDetail(pillars: List<PillarDetail>): GeokgukDetail
     *
     * [fullLabelFromPillars]와 동일한 우선순위 로직이지만 투출 글자·위치를 페이로드에 포함.
     * MemberGyeokAnalyzer(G-07)는 이 함수에 위임해 중복 구현 제거.
     */
    fun resolveWithDetail(pillars: List<PillarDetail>): GeokgukDetail {
        if (pillars.size < 3) return GeokgukDetail(GeokgukLabel.JAPGYEOK, null, null, "폴백")
        val monthPillar = pillars[2]

        // 1·2: 건록/양인
        when (monthPillar.unseong) {
            "乾祿" -> return GeokgukDetail(GeokgukLabel.GEONROK, null, null, "건록")
            "帝旺" -> return GeokgukDetail(GeokgukLabel.YANGIN, null, null, "양인")
        }

        // 3: 천간 투출
        val allStems = pillars.map { it.pillar.stem }
        val transparentStem = transparentSipsin(monthPillar, allStems)
        if (transparentStem != null) {
            val label = labelFromTransparent(transparentStem, pillars)
            if (label != null && label != GeokgukLabel.JAPGYEOK) {
                // 투출 기둥 위치 확인 (일간 인덱스 1 제외, pillars 순: 시0·일1·월2·년3)
                val positionLabels = mapOf(0 to "시간", 2 to "월간", 3 to "년간")
                var foundIdx = -1
                pillars.forEachIndexed { idx, pd ->
                    if (idx != 1 && pd.pillar.stem == transparentStem && foundIdx == -1) {
                        foundIdx = idx
                    }
                }
                val pos = positionLabels[foundIdx]
                return GeokgukDetail(label, transparentStem, pos, "투출")
            }
        }

        // 4: 폴백
        val fallbackLabel = sipsinToLabel(monthPillar.branchSipsin.trim())
        return GeokgukDetail(fallbackLabel, null, null, "폴백")
    }

    /**
     * 월지 지장간에서 사주 천간(연·월·시간)에 투출한 글자를 찾아 십신 라벨 반환.
     *
     * 정통 자평진전 투출 규칙:
     *  - 지장간 순서: "여기중기정기" (JIJANGGAN 값 기준, 2자면 여기·정기만)
     *  - 일간(pillars[1].pillar.stem)은 本元이므로 투출 비교에서 제외.
     *  - 우선순위: 정기(마지막 자) > 중기 > 여기.
     *    복수 투출 시 가장 강한 것(정기 투출 우선)을 반환.
     *
     * @param monthPillar 월주 상세
     * @param allStems 4천간 리스트 [hour, day, month, year] — day(index 1)는 비교 제외
     * @return 투출자의 십신 한자 문자열 ("正官", "偏官" 등), 없으면 null
     */
    private fun transparentSipsin(monthPillar: PillarDetail, allStems: List<String>): String? {
        val branch = monthPillar.pillar.branch
        val jijangRaw = SajuConstants.JIJANGGAN[branch] ?: return null
        val cleaned = jijangRaw.replace(" ", "")
        if (cleaned.isEmpty()) return null

        // 투출 비교 대상 천간: 일간(index 1)은 本元이므로 제외
        val compareStems = allStems.filterIndexed { idx, _ -> idx != 1 }.toSet()

        // 지장간 위치별 우선순위 — 정기(마지막)를 가장 높게
        // 발견된 투출자 중 정기를 우선 채택: 위치가 높을수록(정기에 가까울수록) 덮어씀
        var result: String? = null
        var resultPriority = -1  // 낮을수록 우선순위 낮음 (정기 = len-1)

        cleaned.forEachIndexed { j, ch ->
            val stemChar = ch.toString()
            if (stemChar in compareStems) {
                // 지장간 인덱스가 클수록(정기에 가까울수록) 우선순위 높음
                if (j >= resultPriority) {
                    // 투출된 천간의 십신 = 월주 천간 기준으로 stemSipsin 재산출은
                    // 이미 PillarDetail.stemSipsin/branchSipsin 로 계산돼 있지 않으므로,
                    // 해당 천간이 등장하는 기둥의 stemSipsin 을 찾아 사용한다.
                    result = stemChar
                    resultPriority = j
                }
            }
        }
        return result
    }

    /**
     * 투출된 천간 글자 → GeokgukLabel.
     *
     * 천간 글자 자체는 십신이 아니므로, 투출 천간이 속한 기둥의 stemSipsin 을
     * allPillars에서 역참조해 라벨을 결정한다.
     */
    private fun labelFromTransparent(
        transparentStem: String,
        allPillars: List<PillarDetail>
    ): GeokgukLabel? {
        // 해당 천간 글자를 가진 기둥(일주 제외, index != 1)의 stemSipsin 을 찾는다
        allPillars.forEachIndexed { idx, pd ->
            if (idx == 1) return@forEachIndexed  // 일간 本元 제외
            if (pd.pillar.stem == transparentStem) {
                return sipsinToLabel(pd.stemSipsin.trim())
            }
        }
        return null
    }

    /** 십신 한자 문자열 → GeokgukLabel 변환. */
    private fun sipsinToLabel(sipsin: String): GeokgukLabel = when (sipsin) {
        "比肩" -> GeokgukLabel.BIGYEON
        "劫財" -> GeokgukLabel.GEOPJAE
        "食神" -> GeokgukLabel.SIKSHIN
        "傷官" -> GeokgukLabel.SANGGWAN
        "正財" -> GeokgukLabel.JEONGJAE
        "偏財" -> GeokgukLabel.PYEONJAE
        "正官" -> GeokgukLabel.JEONGGWAN
        "偏官" -> GeokgukLabel.PYEONGGWAN
        "正印" -> GeokgukLabel.JEONGIN
        "偏印" -> GeokgukLabel.PYEONIN
        else -> GeokgukLabel.JAPGYEOK
    }

    /**
     * 4기둥 전체를 받아 천간 투출을 반영한 격국 라벨 반환 (정통 자평진전).
     *
     * pillars 순서: [hour(0), day(1), month(2), year(3)] — SajuRepository 표준.
     */
    fun fullLabelFromPillars(pillars: List<PillarDetail>): GeokgukLabel {
        if (pillars.size < 3) return GeokgukLabel.JAPGYEOK
        val monthPillar = pillars[2]

        // 1·2: 건록/양인은 투출 무관
        when (monthPillar.unseong) {
            "乾祿" -> return GeokgukLabel.GEONROK
            "帝旺" -> return GeokgukLabel.YANGIN
        }

        // 3: 천간 투출 체크 (연·월·시간 vs 월지 지장간)
        val allStems = pillars.map { it.pillar.stem }  // [hour, day, month, year]
        val transparentStem = transparentSipsin(monthPillar, allStems)
        if (transparentStem != null) {
            val label = labelFromTransparent(transparentStem, pillars)
            if (label != null && label != GeokgukLabel.JAPGYEOK) return label
        }

        // 4: 투출자 없음 — 월지 정기(branchSipsin) 약식 폴백
        return sipsinToLabel(monthPillar.branchSipsin.trim())
    }

    /**
     * 메인 사주 화면과 동일한 풀 라벨 (한자 + 한글 + 카테고리 + 대분류) 반환.
     *
     * 단일 월주만 전달 시 천간 투출 비교를 할 수 없으므로 정기 기준 약식 산출.
     * 4기둥 전체가 있으면 [fullLabelFromPillars] 사용 권장.
     */
    fun fullLabel(monthPillar: PillarDetail?): GeokgukLabel {
        monthPillar ?: return GeokgukLabel.JAPGYEOK
        when (monthPillar.unseong) {
            "乾祿" -> return GeokgukLabel.GEONROK
            "帝旺" -> return GeokgukLabel.YANGIN
        }
        return sipsinToLabel(monthPillar.branchSipsin.trim())
    }

    /** "비겁격/식상격/재성격/관성격/인성격/잡격" 6종 카테고리 — 그룹 `MemberGyeokScore.geokLabel` 형식. */
    fun category(monthPillar: PillarDetail?): String =
        fullLabel(monthPillar).category

    /** "비겁/식상/재성/관성/인성" 5종 대분류 또는 null (잡격) — 그룹 `MemberArchetype.classicalCategory` 형식. */
    fun majorCategory(monthPillar: PillarDetail?): String? =
        fullLabel(monthPillar).majorCategory

    /**
     * pillars 리스트에서 격국 결정 — 천간 투출 반영 (자평진전 정설).
     *
     * 4기둥 전체를 활용해 투출자를 판정한다. 이전 단일-월주 약식보다 정통에 가까움.
     * 1:1 궁합 시그니처 호환 유지.
     */
    fun majorCategoryFromPillars(pillars: List<PillarDetail>): String? {
        if (pillars.size < 3) return null
        return fullLabelFromPillars(pillars).majorCategory
    }

    /**
     * 보조격(補助格) / 상신(相神) 후보 — 전체 8자 십신 빈도(월주 ×2)에서 주격 계열을 제외한 상위 2개(빈도 ≥2).
     *
     * 정통 자평진전: 건록격·양인격은 월령이 일간과 동기(비겁)라 월령에서 격(用)을 못 잡으므로,
     * 투출·우세한 식상·재·관을 **상신(相神)**으로 삼아 격을 운용한다("건록용사"). 이 함수가 그
     * 상신 후보(=보조격)를 빈도 기준으로 산출한다.
     *
     * SajuScreen.determineAuxGeokcuk 와 **동일 산식** — 메인 사주 탭과 융합 화면의 격국 보조 표시가
     * 어긋나지 않도록(건록격에 "비겁격" 동어반복이 아닌 "식신격" 등 실제 상신이 보이도록) 단일화.
     *
     * @param main 주격 라벨. 건록/양인 → 比肩·劫財 제외, 그 외 → 주격 자신의 십신 제외.
     */
    fun auxGeokgukLabels(pillars: List<PillarDetail>, main: GeokgukLabel): List<GeokgukLabel> {
        if (pillars.size < 4) return emptyList()
        val mainSipsin: Set<String> = when (main) {
            GeokgukLabel.GEONROK, GeokgukLabel.YANGIN -> setOf("比肩", "劫財")
            else -> setOf(main.hanja.dropLast(1))  // "正官格" → "正官"
        }
        val counts = mutableMapOf<String, Int>()
        pillars.forEachIndexed { i, pd ->
            val w = if (i == 2) 2 else 1  // 월주 가중치 2배
            buildList {
                if (pd.stemSipsin != "本元") add(pd.stemSipsin)
                if (pd.branchSipsin.isNotBlank() && pd.branchSipsin != "?") add(pd.branchSipsin)
            }.forEach { s -> counts[s] = (counts[s] ?: 0) + w }
        }
        return counts.entries
            .filter { (k, v) -> k !in mainSipsin && v >= 2 }
            .sortedByDescending { it.value }
            .take(2)
            .map { sipsinToLabel(it.key) }
            .filter { it != GeokgukLabel.JAPGYEOK }
    }

    /**
     * 저장된 격국 라벨을 메인 사주 화면과 동일한 "한글(한자)" 표시 형식으로 변환.
     *
     * - 한자 풀 라벨("建祿格") → "건록격(建祿格)" — [GeokgukLabel] 역매핑
     * - 한글 카테고리만("비겁격") → "비겁격" 그대로 (옛 snapshot 호환, 한자 매핑 불가)
     * - null / blank → null
     *
     * 호출 시점은 UI 표시 위치마다 — 저장 데이터는 손대지 않는다.
     */
    fun toDisplayLabel(rawLabel: String?): String? {
        val r = rawLabel?.takeIf { it.isNotBlank() } ?: return null
        return GeokgukLabel.fromHanja(r)?.displayLabel ?: r
    }
}

/**
 * 격국 한 항목 — 한자/한글/카테고리/대분류를 모두 한 곳에 묶는다.
 *
 * snapshot JSON 호환을 위해 enum 이름 변경 금지. 새 항목은 끝에 추가.
 */
enum class GeokgukLabel(
    /** 한자 풀 라벨 — 메인 사주 표시용 (예: "建祿格"). */
    val hanja: String,
    /** 한글 풀 라벨 — 메인 사주 표시용 (예: "건록격"). */
    val hangul: String,
    /** 그룹 산식 카테고리 — 비겁격/식상격/재성격/관성격/인성격/잡격. */
    val category: String,
    /** 1:1·MemberArchetype 대분류 — 비겁/식상/재성/관성/인성 또는 null(잡격). */
    val majorCategory: String?,
) {
    GEONROK("建祿格", "건록격", "비겁격", "비겁"),
    YANGIN("羊刃格", "양인격", "비겁격", "비겁"),
    BIGYEON("比肩格", "비견격", "비겁격", "비겁"),
    GEOPJAE("劫財格", "겁재격", "비겁격", "비겁"),
    SIKSHIN("食神格", "식신격", "식상격", "식상"),
    SANGGWAN("傷官格", "상관격", "식상격", "식상"),
    JEONGJAE("正財格", "정재격", "재성격", "재성"),
    PYEONJAE("偏財格", "편재격", "재성격", "재성"),
    JEONGGWAN("正官格", "정관격", "관성격", "관성"),
    PYEONGGWAN("偏官格", "편관격", "관성격", "관성"),
    JEONGIN("正印格", "정인격", "인성격", "인성"),
    PYEONIN("偏印格", "편인격", "인성격", "인성"),
    JAPGYEOK("雜格", "잡격", "잡격", null);

    /** 메인 사주 화면과 동일한 표시 형식 — "건록격(建祿格)". */
    val displayLabel: String get() = "$hangul($hanja)"

    companion object {
        /** 한자 풀 라벨에서 enum 역매핑 — snapshot 에 저장된 raw 한자를 다시 enum 으로 해석. */
        fun fromHanja(hanja: String): GeokgukLabel? =
            entries.firstOrNull { it.hanja == hanja }
    }
}
