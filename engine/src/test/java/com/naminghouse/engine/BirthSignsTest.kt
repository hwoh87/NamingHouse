package com.naminghouse.engine

import com.naminghouse.engine.fortune.BirthSigns
import com.naminghouse.engine.saju.SajuNamingService
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BirthSignsTest {

    @Test
    fun `12지지 전부 띠가 있다`() {
        "子丑寅卯辰巳午未申酉戌亥".forEach { b ->
            assertNotNull("$b 띠 없음", BirthSigns.ttiiOf(b.toString()))
        }
        assertEquals("말띠", BirthSigns.ttiiOf("午")!!.name)
        assertEquals("뱀띠", BirthSigns.ttiiOf("巳")!!.name)
        assertNull(BirthSigns.ttiiOf("甲"))
    }

    @Test
    fun `별자리 경계일`() {
        assertEquals("사자자리", BirthSigns.starSignOf(7, 23)!!.name)
        assertEquals("사자자리", BirthSigns.starSignOf(8, 22)!!.name)
        assertEquals("처녀자리", BirthSigns.starSignOf(8, 23)!!.name)
        assertEquals("게자리", BirthSigns.starSignOf(7, 22)!!.name)
    }

    @Test
    fun `염소자리는 해를 넘긴다`() {
        assertEquals("염소자리", BirthSigns.starSignOf(12, 22)!!.name)
        assertEquals("염소자리", BirthSigns.starSignOf(12, 31)!!.name)
        assertEquals("염소자리", BirthSigns.starSignOf(1, 1)!!.name)
        assertEquals("염소자리", BirthSigns.starSignOf(1, 19)!!.name)
        assertEquals("물병자리", BirthSigns.starSignOf(1, 20)!!.name)
    }

    @Test
    fun `1년 365일 모두 별자리가 나온다`() {
        val days = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (m in 1..12) for (d in 1..days[m - 1]) {
            assertNotNull("$m/$d 별자리 없음", BirthSigns.starSignOf(m, d))
        }
    }

    @Test
    fun `사주 요약에 띠와 별자리가 실린다`() {
        // 2026-08-12 → 병오년(말띠), 사자자리
        val s = SajuNamingService.analyze(BirthInput(2026, 8, 12, 12, 0, Gender.M))
        assertEquals("午", s.ganzis[0].last().toString())
        assertEquals("말띠", s.ttii!!.name)
        assertEquals("사자자리", s.starSign!!.name)
    }

    @Test
    fun `띠는 입춘 경계를 따른다`() {
        // 2026-01-20 은 달력상 2026년이지만 입춘 전이라 을사년 = 뱀띠
        val before = SajuNamingService.analyze(BirthInput(2026, 1, 20, 12, 0, Gender.M))
        assertEquals("뱀띠", before.ttii!!.name)
        // 같은 날의 별자리는 달력 기준이라 물병자리
        assertEquals("물병자리", before.starSign!!.name)

        val after = SajuNamingService.analyze(BirthInput(2026, 3, 1, 12, 0, Gender.M))
        assertEquals("말띠", after.ttii!!.name)
    }

    @Test
    fun `음력 입력도 별자리는 양력으로 환산해 판정한다`() {
        // 음력 2026-01-01 = 양력 2026-02-17 → 물병자리
        val s = SajuNamingService.analyze(
            BirthInput(2026, 1, 1, 12, 0, Gender.M, isLunar = true)
        )
        assertEquals("물병자리", s.starSign!!.name)
    }
}
