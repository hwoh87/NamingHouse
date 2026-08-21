package com.naminghouse.engine.oheng

import com.samramanshang.manseryeok.orrery.model.Element

/**
 * 발음오행 학파 — 초성 ㅇ·ㅎ(후음)과 ㅁ·ㅂ·ㅍ(순음)의 오행 배속이 갈린다.
 * 다수 작명 실무는 운해본(ㅇㅎ=土, ㅁㅂㅍ=水)을 쓴다.
 */
enum class BaleumSchool(val label: String) {
    /** 훈민정음운해 계열(다수설): 후음=土, 순음=水 */
    UNHAE("운해본(다수설)"),

    /** 훈민정음해례 계열(소수설·원전): 후음=水, 순음=土 */
    HAERYE("해례본"),
}

/** 인접 두 글자 소리 오행의 관계 */
enum class OhengRelation(val label: String) {
    SANGSAENG("상생"),
    BIHWA("비화"),   // 같은 오행
    SANGGEUK("상극"),
}

/**
 * 발음오행 상생이 인정된 경로.
 *
 * 실무 다수 유파는 초성 연쇄 하나만 보지 않는다 — 성씨의 **받침(종성)** 을 시작점으로
 * 삼는 경로까지 셋 중 하나만 만족하면 상생으로 본다(미소 한국작명원·사주포럼 등).
 * 김(ㄱ=木·받침 ㅁ=水)처럼 받침이 있는 성씨는 초성으로 상극이어도 받침으로 풀린다.
 */
enum class BaleumPath(val label: String) {
    /** ① 성 초성 → 이름 초성들 — 가장 좁고 널리 쓰이는 경로 */
    CHOSEONG("초성"),

    /** ② 성 종성 → 이름 초성들 */
    SURNAME_JONG("성 받침"),

    /** ③ 성 종성 → 이름1 초성 → 이름1 종성 → 이름2 초성 */
    JONG_CHAIN("받침 연쇄"),

    /** 어느 경로로도 상극을 피하지 못함 */
    NONE("상극"),
}

data class BaleumResult(
    /** 글자별 소리 오행 (성 포함, 앞에서부터) — 초성 기준, 화면 표시용 */
    val elements: List<Element>,
    /** 초성 인접 쌍 관계 */
    val relations: List<OhengRelation>,
    /** 초성 연쇄에 상극이 있는가 (표시·설명용) */
    val hasSanggeuk: Boolean,
    val allSangsaeng: Boolean,
    /** 상생이 인정된 경로 — [BaleumPath.NONE] 이면 어느 경로로도 안 풀린 것 */
    val path: BaleumPath = BaleumPath.CHOSEONG,
) {
    /** 받침 경로로 구제된 배열인가 */
    val rescuedByJongseong: Boolean
        get() = path == BaleumPath.SURNAME_JONG || path == BaleumPath.JONG_CHAIN

    /** 감점 대상인 상극 개수 — 어느 경로로도 안 풀렸을 때만 센다 */
    val effectiveSanggeuk: Int
        get() = if (path == BaleumPath.NONE) relations.count { it == OhengRelation.SANGGEUK } else 0
}

object BaleumOheng {

    // 상생 순환: 木→火→土→金→水→木
    private val NEXT: Map<Element, Element> = mapOf(
        Element.TREE to Element.FIRE,
        Element.FIRE to Element.EARTH,
        Element.EARTH to Element.METAL,
        Element.METAL to Element.WATER,
        Element.WATER to Element.TREE,
    )

    private val CHOSEONG = listOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /** 받침 자모 — 인덱스 0 은 받침 없음. 겹받침은 첫 자음으로 본다(통용 관행). */
    private val JONGSEONG = listOf(
        "", "ㄱ", "ㄲ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ", "ㄹㅁ", "ㄹㅂ",
        "ㄹㅅ", "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ",
        "ㅋ", "ㅌ", "ㅍ", "ㅎ",
    )

    fun choseongOf(syllable: Char): Char? {
        val code = syllable.code - 0xAC00
        if (code < 0 || code >= 11172) return null
        return CHOSEONG[code / 588]
    }

    /** 받침 첫 자음 (받침이 없으면 null) */
    fun jongseongOf(syllable: Char): Char? {
        val code = syllable.code - 0xAC00
        if (code < 0 || code >= 11172) return null
        return JONGSEONG[code % 28].firstOrNull()
    }

    /** 초성 → 오행 (아음=木, 설음=火, 치음=金 공통; 후음·순음은 학파 분기) */
    fun elementOf(choseong: Char, school: BaleumSchool): Element? = when (choseong) {
        'ㄱ', 'ㄲ', 'ㅋ' -> Element.TREE
        'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅌ' -> Element.FIRE
        'ㅅ', 'ㅆ', 'ㅈ', 'ㅉ', 'ㅊ' -> Element.METAL
        'ㅇ', 'ㅎ' -> if (school == BaleumSchool.UNHAE) Element.EARTH else Element.WATER
        'ㅁ', 'ㅂ', 'ㅃ', 'ㅍ' -> if (school == BaleumSchool.UNHAE) Element.WATER else Element.EARTH
        else -> null
    }

    fun relationOf(a: Element, b: Element): OhengRelation = when {
        a == b -> OhengRelation.BIHWA
        NEXT[a] == b || NEXT[b] == a -> OhengRelation.SANGSAENG
        else -> OhengRelation.SANGGEUK
    }

    /**
     * 성명 전체(성+이름)의 소리 오행 배열 평가.
     *
     * 초성 연쇄가 상극이면 성씨 받침을 시작점으로 삼는 두 경로를 더 본다 —
     * 실무 다수 유파가 셋 중 하나만 만족하면 상생으로 보기 때문이다([BaleumPath]).
     * 실측: 실제 인기 이름 211개 기준 김씨 상극률이 79% -> 36%, 강씨는 79% -> 6% 로
     * 떨어진다. 초성만 보면 받침 있는 성씨가 구조적으로 손해를 본다.
     *
     * (상생 방향은 유파 이견이 있어 방향 무관 '인접 상극 여부'만 판정.)
     */
    fun evaluate(fullName: String, school: BaleumSchool = BaleumSchool.UNHAE): BaleumResult? {
        val elements = fullName.map { ch ->
            val cho = choseongOf(ch) ?: return null
            elementOf(cho, school) ?: return null
        }
        if (elements.size < 2) {
            return BaleumResult(elements, emptyList(), hasSanggeuk = false, allSangsaeng = false)
        }
        val relations = elements.zipWithNext { a, b -> relationOf(a, b) }
        val hasSanggeuk = relations.any { it == OhengRelation.SANGGEUK }

        val path = when {
            !hasSanggeuk -> BaleumPath.CHOSEONG
            else -> jongseongPath(fullName, elements, school)
        }

        return BaleumResult(
            elements = elements,
            relations = relations,
            hasSanggeuk = hasSanggeuk,
            allSangsaeng = relations.all { it == OhengRelation.SANGSAENG },
            path = path,
        )
    }

    /** 성씨 받침을 시작점으로 삼는 경로 ②③ — 먼저 통하는 것을 돌려준다. */
    private fun jongseongPath(
        fullName: String,
        choElements: List<Element>,
        school: BaleumSchool,
    ): BaleumPath {
        val surJong = jongseongOf(fullName[0])?.let { elementOf(it, school) }
            ?: return BaleumPath.NONE

        // ② 성 종성 → 이름 초성들
        val path2 = listOf(surJong) + choElements.drop(1)
        if (isChainClean(path2)) return BaleumPath.SURNAME_JONG

        // ③ 성 종성 → 이름1 초성 → 이름1 종성 → 이름2 초성
        if (fullName.length == 3) {
            val midJong = jongseongOf(fullName[1])?.let { elementOf(it, school) }
            if (midJong != null &&
                isChainClean(listOf(surJong, choElements[1], midJong, choElements[2]))
            ) return BaleumPath.JONG_CHAIN
        }
        return BaleumPath.NONE
    }

    private fun isChainClean(els: List<Element>): Boolean =
        els.zipWithNext { a, b -> relationOf(a, b) }.none { it == OhengRelation.SANGGEUK }
}
