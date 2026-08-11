package com.naminghouse.engine.oheng

import com.samramanshang.manseryeok.orrery.model.Element

/** 수리오행 배열 평가 결과 */
data class SuriOhengResult(
    /** 성·이름 각 글자의 획수에서 나온 오행 (앞에서부터) */
    val elements: List<Element>,
    /** 인접 쌍 관계 */
    val relations: List<OhengRelation>,
    val hasSanggeuk: Boolean,
    val allSangsaeng: Boolean,
)

/**
 * 수리오행(數理五行) — 글자마다 그 글자의 원획을 오행으로 환산해 배열을 본다.
 *
 * 발음오행이 소리(초성)를 오행으로 보는 축이라면, 이쪽은 획수를 오행으로 본다.
 * 원형이정 4격의 수가 아니라 **글자별 획수**를 쓴다 — 작명왕 실측 리포트에서
 * 성 金(8획)·이름 10획·21획인 이름의 수리오행이 정확히 金水木 으로 나왔다
 * (8→金, 10→水, 21→木). 4격을 썼다면 오행이 4개 나왔을 것이다.
 * 자세한 역산은 tools/jakmyeongwang-report.md 참조.
 */
object SuriOheng {

    /** 끝자리 → 오행. 1·2 木, 3·4 火, 5·6 土, 7·8 金, 9·0 水 */
    fun elementOf(strokes: Int): Element = when (strokes % 10) {
        1, 2 -> Element.TREE
        3, 4 -> Element.FIRE
        5, 6 -> Element.EARTH
        7, 8 -> Element.METAL
        else -> Element.WATER // 9, 0
    }

    /**
     * 성+이름 각 글자의 원획 배열을 받아 상생/상극을 판정한다.
     * 판정 방식은 발음오행과 같다 — 인접 쌍이 모두 상생이면 최길, 상극이 하나라도 있으면 흉.
     */
    fun evaluate(strokes: List<Int>): SuriOhengResult {
        val elements = strokes.map(::elementOf)
        if (elements.size < 2) {
            return SuriOhengResult(elements, emptyList(), hasSanggeuk = false, allSangsaeng = false)
        }
        val relations = elements.zipWithNext { a, b -> BaleumOheng.relationOf(a, b) }
        return SuriOhengResult(
            elements = elements,
            relations = relations,
            hasSanggeuk = relations.any { it == OhengRelation.SANGGEUK },
            allSangsaeng = relations.all { it == OhengRelation.SANGSAENG },
        )
    }
}
