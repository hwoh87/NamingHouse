package com.naminghouse.engine

import com.naminghouse.engine.suri.Suri81
import com.naminghouse.engine.suri.SuriCalculator
import com.naminghouse.engine.suri.SuriGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuriTest {

    @Test
    fun `박영수 검증 예시 - 성6 이름14_7`() {
        // irum.com/TAROT 카페 공통 예시: 원21 형20 이13 정27
        val g = SuriCalculator.calculate(listOf(6), listOf(14, 7))
        assertEquals(21, g.won.number)
        assertEquals(20, g.hyeong.number)
        assertEquals(13, g.i.number)
        assertEquals(27, g.jeong.number)
    }

    @Test
    fun `외자 이름 - 원격은 이름 단독, 이격은 성 단독`() {
        val g = SuriCalculator.calculate(listOf(7), listOf(9))
        assertEquals(9, g.won.number)
        assertEquals(16, g.hyeong.number)
        assertEquals(7, g.i.number)
        assertEquals(16, g.jeong.number)
    }

    @Test
    fun `복성 - 성 두 글자 합산`() {
        // 남궁(南宮): 南9 + 宮10
        val g = SuriCalculator.calculate(listOf(9, 10), listOf(8, 5))
        assertEquals(13, g.won.number)
        assertEquals(27, g.hyeong.number)
        assertEquals(24, g.i.number)
        assertEquals(32, g.jeong.number)
    }

    /**
     * 작명왕 실측 리포트(tools/jakmyeongwang-report.md)와 4격이 일치하는지 고정한다.
     * 성 金 8획 + 이름 10획·21획 → 초년 31 / 청년 18 / 장년 29 / 전체 39.
     * 우리 공식(허수 없음)이 작명왕과 같다는 증거.
     */
    @Test
    fun `작명왕 실측 사례 - 4격이 31_18_29_39 로 일치`() {
        val g = SuriCalculator.calculate(listOf(8), listOf(10, 21))
        assertEquals(31, g.won.number)
        assertEquals(18, g.hyeong.number)
        assertEquals(29, g.i.number)
        assertEquals(39, g.jeong.number)
        // 작명왕은 네 격 모두 "매우 좋아요"로 표시했다
        assertTrue(g.allGood)
        // 명칭도 두 개는 그대로 일치한다(18 발전격, 29 성공격)
        assertEquals("발전격", g.hyeong.title)
        assertEquals("성공격", g.i.title)
    }

    @Test
    fun `81 초과 수는 81을 빼고 환원`() {
        assertEquals(Suri81.of(1).title, Suri81.of(82).title)
        assertEquals(Suri81.of(3).title, Suri81.of(84).title)
    }

    @Test
    fun `81수리 표 - 길흉 분포가 표준(길40·흉40·반길1)`() {
        val all = Suri81.all
        assertEquals(81, all.size)
        assertEquals(40, all.count { it.grade.isGood })
        assertEquals(40, all.count { it.grade == SuriGrade.HYUNG || it.grade == SuriGrade.DAEHYUNG })
        assertEquals(1, all.count { it.grade == SuriGrade.PYEONG })
        assertEquals(77, all.first { it.grade == SuriGrade.PYEONG }.number)
    }

    @Test
    fun `81수리 표 - 공인 길수 목록과 일치`() {
        val expectedGood = setOf(
            1, 3, 5, 6, 7, 8, 11, 13, 15, 16, 17, 18, 21, 23, 24, 25, 29,
            31, 32, 33, 35, 37, 38, 39, 41, 45, 47, 48, 51, 52, 57, 58,
            61, 63, 65, 67, 68, 73, 75, 81,
        )
        val actualGood = Suri81.all.filter { it.grade.isGood }.map { it.number }.toSet()
        assertEquals(expectedGood, actualGood)
    }

    /**
     * 풀이문은 화면에 그대로 나가는 콘텐츠다 — 빠지거나(빈 문자열),
     * 등급과 어긋난 논조("길수입니다"라며 흉 등급)가 섞이지 않게 고정한다.
     */
    @Test
    fun `81수리 표 - 풀이문이 전부 있고 등급 논조와 어긋나지 않는다`() {
        Suri81.all.forEach { m ->
            assertTrue("${m.number}수 풀이문 없음", m.description.isNotBlank())
            assertTrue("${m.number}수 풀이문이 너무 짧음", m.description.length >= 30)
            assertTrue("${m.number}수 풀이문은 문장으로 끝나야 함", m.description.endsWith("."))
            if (!m.grade.isGood) {
                assertTrue(
                    "${m.number}수(흉)의 풀이문이 길수 표현을 씀",
                    !m.description.contains("길수입니다"),
                )
            }
        }
    }

    @Test
    fun `대표 수리 명칭`() {
        assertEquals("태초격", Suri81.of(1).title)
        assertEquals("두령격", Suri81.of(21).title)
        assertEquals("파멸격", Suri81.of(34).title)
        assertEquals(SuriGrade.DAEHYUNG, Suri81.of(34).grade)
        assertEquals("환원격", Suri81.of(81).title)
    }
}
