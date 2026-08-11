package com.naminghouse.engine

import com.naminghouse.engine.saju.SajuNamingService
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 삼라 engine-core 골든 덤프(ios/SamraTests/golden_saju.json)에서 추출한 기대값과의 패리티.
 * 복사해 온 사주 경로가 원본과 동일하게 동작하는지 보증한다.
 */
class SajuGoldenTest {

    private fun ganzis(y: Int, mo: Int, d: Int, h: Int, mi: Int, g: Gender = Gender.M): List<String> =
        SajuNamingService.analyze(BirthInput(y, mo, d, h, mi, g)).ganzis

    @Test
    fun `1954년 - 역사적 KST +08_30 시대`() {
        assertEquals(listOf("甲午", "丁卯", "丙子", "戊子"), ganzis(1954, 3, 21, 0, 30))
    }

    @Test
    fun `1988년 - 서머타임 구간`() {
        assertEquals(listOf("戊辰", "丁巳", "癸亥", "癸丑"), ganzis(1988, 5, 8, 2, 30))
    }

    @Test
    fun `2024년 입춘 경계`() {
        assertEquals(listOf("癸卯", "乙丑", "戊戌", "庚申"), ganzis(2024, 2, 4, 16, 30, Gender.F))
    }

    @Test
    fun `2000년 자시 - 통자시 일주 이월`() {
        assertEquals(listOf("己卯", "丙子", "己未", "甲子"), ganzis(2000, 1, 1, 23, 30))
    }

    @Test
    fun `시간 모름 - 일주까지 일치, 시주는 표시 제외`() {
        val summary = SajuNamingService.analyze(
            BirthInput(1995, 7, 15, 0, 0, Gender.F, unknownTime = true)
        )
        assertEquals("乙亥", summary.ganzis[0])
        assertEquals("癸未", summary.ganzis[1])
        assertEquals("丁未", summary.ganzis[2])
        assertTrue(summary.unknownTime)
        // 시간 모름 시 단순 오행 집계는 여섯 글자만
        assertEquals(6, summary.simpleCounts.values.sum())
    }

    @Test
    fun `음력 입력 변환 경로`() {
        // 음력 1995-06-18 = 양력 1995-07-15
        val summary = SajuNamingService.analyze(
            BirthInput(1995, 6, 18, 12, 0, Gender.F, isLunar = true)
        )
        assertEquals("乙亥", summary.ganzis[0])
        assertEquals("丁未", summary.ganzis[2])
    }

    @Test
    fun `용신과 보완 오행이 도출된다`() {
        val summary = SajuNamingService.analyze(BirthInput(2024, 2, 4, 16, 30, Gender.M))
        assertTrue(summary.yongsin.isNotEmpty())
        assertTrue(summary.targetElements.isNotEmpty())
        assertTrue(summary.targetElements.size <= 3)
        // 보완 오행에 기신이 섞이면 안 됨(용신 자체가 기신일 수는 없음)
        summary.targetElements.forEach { t ->
            assertTrue(t in summary.yongsin || t !in summary.gisin)
        }
    }
}
