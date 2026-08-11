package com.naminghouse.engine

import com.naminghouse.engine.suri.Suri81
import com.naminghouse.engine.suri.SuriCalculator
import com.naminghouse.engine.suri.SuriGrade
import org.junit.Assert.assertEquals
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

    @Test
    fun `대표 수리 명칭`() {
        assertEquals("태초격", Suri81.of(1).title)
        assertEquals("두령격", Suri81.of(21).title)
        assertEquals("파멸격", Suri81.of(34).title)
        assertEquals(SuriGrade.DAEHYUNG, Suri81.of(34).grade)
        assertEquals("환원격", Suri81.of(81).title)
    }
}
