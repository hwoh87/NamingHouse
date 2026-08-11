package com.naminghouse.engine

import com.naminghouse.engine.oheng.BaleumOheng
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.oheng.EumYang
import com.naminghouse.engine.oheng.OhengRelation
import com.samramanshang.manseryeok.orrery.model.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OhengTest {

    @Test
    fun `초성 분해`() {
        assertEquals('ㄱ', BaleumOheng.choseongOf('김'))
        assertEquals('ㅁ', BaleumOheng.choseongOf('민'))
        assertEquals('ㅈ', BaleumOheng.choseongOf('준'))
        assertEquals('ㅎ', BaleumOheng.choseongOf('하'))
    }

    @Test
    fun `운해본 매핑 - ㅇㅎ은 토, ㅁㅂㅍ은 수`() {
        assertEquals(Element.EARTH, BaleumOheng.elementOf('ㅇ', BaleumSchool.UNHAE))
        assertEquals(Element.EARTH, BaleumOheng.elementOf('ㅎ', BaleumSchool.UNHAE))
        assertEquals(Element.WATER, BaleumOheng.elementOf('ㅁ', BaleumSchool.UNHAE))
        assertEquals(Element.TREE, BaleumOheng.elementOf('ㄱ', BaleumSchool.UNHAE))
        assertEquals(Element.FIRE, BaleumOheng.elementOf('ㄴ', BaleumSchool.UNHAE))
        assertEquals(Element.METAL, BaleumOheng.elementOf('ㅅ', BaleumSchool.UNHAE))
    }

    @Test
    fun `해례본 매핑 - ㅇㅎ은 수, ㅁㅂㅍ은 토`() {
        assertEquals(Element.WATER, BaleumOheng.elementOf('ㅇ', BaleumSchool.HAERYE))
        assertEquals(Element.EARTH, BaleumOheng.elementOf('ㅂ', BaleumSchool.HAERYE))
    }

    @Test
    fun `김민준 - 목수금 전부 상생`() {
        val r = BaleumOheng.evaluate("김민준", BaleumSchool.UNHAE)!!
        assertEquals(listOf(Element.TREE, Element.WATER, Element.METAL), r.elements)
        assertTrue(r.allSangsaeng)
        assertFalse(r.hasSanggeuk)
    }

    @Test
    fun `상극 배열 감지 - 김선우(목금토 상극 포함)`() {
        // 김(木)-선(金): 金克木 상극
        val r = BaleumOheng.evaluate("김선우", BaleumSchool.UNHAE)!!
        assertTrue(r.hasSanggeuk)
        assertEquals(OhengRelation.SANGGEUK, r.relations[0])
    }

    @Test
    fun `비화 - 같은 오행 연속`() {
        assertEquals(OhengRelation.BIHWA, BaleumOheng.relationOf(Element.TREE, Element.TREE))
    }

    @Test
    fun `수리음양 - 순음이나 순양만 흉`() {
        assertTrue(EumYang.ofStrokes(listOf(6, 14, 7)).isBalanced)
        assertFalse(EumYang.ofStrokes(listOf(4, 4, 8)).isBalanced)
        assertFalse(EumYang.ofStrokes(listOf(1, 3, 5)).isBalanced)
        assertEquals("●●○", EumYang.ofStrokes(listOf(6, 14, 7)).display)
    }

    @Test
    fun `발음음양 - 양성과 음성 모음이 섞이면 조화`() {
        val r = EumYang.ofSound("하준")!! // ㅏ(양) + ㅜ(음)
        assertTrue(r.isBalanced)
        assertNotNull(EumYang.ofSound("이서"))
    }
}
