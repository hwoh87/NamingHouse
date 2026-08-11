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

data class BaleumResult(
    /** 글자별 소리 오행 (성 포함, 앞에서부터) */
    val elements: List<Element>,
    /** 인접 쌍 관계 */
    val relations: List<OhengRelation>,
    val hasSanggeuk: Boolean,
    val allSangsaeng: Boolean,
)

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

    fun choseongOf(syllable: Char): Char? {
        val code = syllable.code - 0xAC00
        if (code < 0 || code >= 11172) return null
        return CHOSEONG[code / 588]
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
     * 인접 쌍이 모두 상생이면 최길, 상극이 하나라도 있으면 흉 배열로 본다.
     * (상생 방향은 유파 이견이 있어 방향 무관 '인접 상생 여부'만 판정.)
     */
    fun evaluate(fullName: String, school: BaleumSchool = BaleumSchool.UNHAE): BaleumResult? {
        val elements = fullName.map { ch ->
            val cho = choseongOf(ch) ?: return null
            elementOf(cho, school) ?: return null
        }
        if (elements.size < 2) return BaleumResult(elements, emptyList(), hasSanggeuk = false, allSangsaeng = false)
        val relations = elements.zipWithNext { a, b -> relationOf(a, b) }
        return BaleumResult(
            elements = elements,
            relations = relations,
            hasSanggeuk = relations.any { it == OhengRelation.SANGGEUK },
            allSangsaeng = relations.all { it == OhengRelation.SANGSAENG },
        )
    }
}
