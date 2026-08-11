package com.naminghouse.engine

import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.oheng.SuriOheng
import com.samramanshang.manseryeok.orrery.model.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 수리오행 — 작명왕 실측 리포트(tools/jakmyeongwang-report.md)에서 역산한 규칙을 고정한다.
 */
class SuriOhengTest {

    @Test
    fun `끝자리 오행 환산`() {
        assertEquals(Element.TREE, SuriOheng.elementOf(1))
        assertEquals(Element.TREE, SuriOheng.elementOf(2))
        assertEquals(Element.FIRE, SuriOheng.elementOf(3))
        assertEquals(Element.FIRE, SuriOheng.elementOf(4))
        assertEquals(Element.EARTH, SuriOheng.elementOf(5))
        assertEquals(Element.EARTH, SuriOheng.elementOf(6))
        assertEquals(Element.METAL, SuriOheng.elementOf(7))
        assertEquals(Element.METAL, SuriOheng.elementOf(8))
        assertEquals(Element.WATER, SuriOheng.elementOf(9))
        assertEquals(Element.WATER, SuriOheng.elementOf(10))
    }

    @Test
    fun `10 이상은 끝자리로 판정`() {
        assertEquals(Element.TREE, SuriOheng.elementOf(21))
        assertEquals(Element.WATER, SuriOheng.elementOf(20))
        assertEquals(Element.METAL, SuriOheng.elementOf(18))
        assertEquals(Element.EARTH, SuriOheng.elementOf(35))
    }

    /**
     * 작명왕 실측: 성 金(8획) + 이름 10획·21획 → 수리오행 "金 水 木", 판정 "매우 좋아요".
     * (이름은 마스킹돼 있었으나 4격 31/18/29/39 에서 획수가 역산된다)
     */
    @Test
    fun `작명왕 실측 사례 - 8·10·21획은 金水木 연속 상생`() {
        val r = SuriOheng.evaluate(listOf(8, 10, 21))
        assertEquals(listOf(Element.METAL, Element.WATER, Element.TREE), r.elements)
        assertTrue("金生水生木 은 연속 상생", r.allSangsaeng)
        assertFalse(r.hasSanggeuk)
    }

    @Test
    fun `상극 배열 감지`() {
        // 8(金) - 4(火): 火剋金 상극
        val r = SuriOheng.evaluate(listOf(8, 4, 11))
        assertTrue(r.hasSanggeuk)
        assertEquals(OhengRelation.SANGGEUK, r.relations[0])
        assertFalse(r.allSangsaeng)
    }

    @Test
    fun `같은 오행 연속은 비화 - 상극도 상생도 아님`() {
        // 1(木) - 11(木) - 3(火)
        val r = SuriOheng.evaluate(listOf(1, 11, 3))
        assertEquals(OhengRelation.BIHWA, r.relations[0])
        assertEquals(OhengRelation.SANGSAENG, r.relations[1])
        assertFalse(r.hasSanggeuk)
        assertFalse("비화가 섞이면 전부 상생은 아님", r.allSangsaeng)
    }

    @Test
    fun `외자 이름 - 두 글자도 판정된다`() {
        val r = SuriOheng.evaluate(listOf(8, 10))
        assertEquals(1, r.relations.size)
        assertTrue(r.allSangsaeng)
    }

    @Test
    fun `한 글자만 있으면 관계가 없다`() {
        val r = SuriOheng.evaluate(listOf(8))
        assertTrue(r.relations.isEmpty())
        assertFalse(r.hasSanggeuk)
    }
}
