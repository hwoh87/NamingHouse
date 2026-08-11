package com.naminghouse.engine

import com.naminghouse.engine.gen.NameStats
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NameStatsTest {

    private fun parse(vararg rows: String) =
        NameStats.parse((listOf("name\tdominant\tmaleCount\tfemaleCount\tranks") + rows).asSequence())

    @Test
    fun `파싱 - 순위 이력과 남녀비율`() {
        val stats = parse("도윤\tM\t9600\t400\t2026:3,2025:1,2024:2")
        val s = stats["도윤"]!!
        assertEquals(Gender.M, s.dominant)
        assertEquals(10000, s.total)
        assertEquals(96, s.malePercent)
        assertEquals(2026 to 3, s.latestRank)
        assertEquals(3, s.ranks.size)
        assertTrue(s.isPopular)
    }

    @Test
    fun `순위는 최신 연도부터 정렬된다`() {
        val s = parse("서연\tF\t100\t9900\t2024:5,2026:2,2025:1")["서연"]!!
        assertEquals(listOf(2026, 2025, 2024), s.ranks.map { it.first })
        assertEquals(1, s.malePercent)
    }

    @Test
    fun `순위 이력이 없어도 파싱된다`() {
        val s = parse("갑돌\tM\t3\t0\t")["갑돌"]!!
        assertTrue(s.ranks.isEmpty())
        assertNull(s.latestRank)
        assertTrue(!s.isPopular)
        assertEquals(100, s.malePercent)
    }

    @Test
    fun `없는 이름은 null`() {
        assertNull(parse("도윤\tM\t10\t0\t2026:3")["없는이름"])
    }

    @Test
    fun `번들 데이터 - 실제 통계가 실려 있고 인기 이름이 잡힌다`() {
        val f = listOf("src/main/assets/name-stats.tsv", "engine/src/main/assets/name-stats.tsv")
            .map(::File).firstOrNull(File::exists)
            ?: error("name-stats.tsv 없음 — tools/name-stats 파이프라인을 먼저 실행해야 함")
        val stats = f.bufferedReader().useLines { NameStats.parse(it) }

        assertTrue("이름이 150개 이상이어야 함 (현재 ${stats.size})", stats.size >= 150)

        // 최근 최상위권 이름은 반드시 들어 있어야 한다
        listOf("도윤", "서준", "시우", "서연", "하윤").forEach { name ->
            val s = stats[name]
            assertNotNull("$name 통계가 있어야 함", s)
            assertTrue("$name 은 인기 이름으로 잡혀야 함", s!!.isPopular)
        }

        // 남아·여아 이름의 성별 쏠림이 실제로 드러나야 한다
        val doyun = stats["도윤"]!!
        assertTrue("도윤은 남아 우세여야 함 (남 ${doyun.malePercent}%)", doyun.malePercent >= 70)
        val seoyeon = stats["서연"]!!
        assertTrue("서연은 여아 우세여야 함 (남 ${seoyeon.malePercent}%)", seoyeon.malePercent <= 30)
    }
}
