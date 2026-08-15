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

    /** 번들 name-stats.tsv — 모듈/루트 어느 쪽에서 실행해도 찾는다. */
    private fun loadBundled(): NameStats {
        val f = listOf("src/main/assets/name-stats.tsv", "engine/src/main/assets/name-stats.tsv")
            .map(::File).firstOrNull(File::exists)
            ?: error("name-stats.tsv 없음 — tools/name-stats 파이프라인을 먼저 실행해야 함")
        return f.bufferedReader().useLines { NameStats.parse(it) }
    }

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
    fun `차트 - 성별과 연도로 거르고 순위순으로 선다`() {
        val stats = parse(
            "도윤\tM\t100\t0\t2026:2,2025:1",
            "서준\tM\t90\t0\t2026:1",
            "서연\tF\t0\t100\t2026:1",
            "지호\tM\t80\t0\t2025:3",
            "하준\tM\t70\t0\t2026:40",
        )
        assertEquals(
            listOf(1 to "서준", 2 to "도윤"),
            stats.chart(Gender.M, 2026).map { (rank, s) -> rank to s.name },
        )
        assertEquals(
            listOf(1 to "서연"),
            stats.chart(Gender.F, 2026).map { (rank, s) -> rank to s.name },
        )
        assertTrue(stats.chart(Gender.F, 2024).isEmpty())
        // 40위(하준)는 기본 30위 컷에 잘리고, 상한을 올리면 들어온다
        assertEquals(3, stats.chart(Gender.M, 2026, maxRank = 40).size)
    }

    @Test
    fun `차트 연도 목록과 특정 해 순위 조회`() {
        val stats = parse(
            "도윤\tM\t100\t0\t2026:2,2024:1",
            "서연\tF\t0\t100\t2025:1",
        )
        assertEquals(listOf(2026, 2025, 2024), stats.chartYears())
        assertEquals(2, stats["도윤"]!!.rankIn(2026))
        assertNull(stats["도윤"]!!.rankIn(2025))
    }

    @Test
    fun `번들 데이터 - 최신 해 남아·여아 차트가 30위 가까이 선다`() {
        val stats = loadBundled()
        val year = stats.chartYears().first()
        assertTrue("차트 연도가 2025년 이후여야 함 (현재 $year)", year >= 2025)
        listOf(Gender.M, Gender.F).forEach { g ->
            val chart = stats.chart(g, year)
            assertTrue("$g $year 차트가 25개 이상이어야 함 (현재 ${chart.size})", chart.size >= 25)
            assertEquals("$g 차트는 1위부터 시작해야 함", 1, chart.first().first)
            assertEquals("$g 차트는 순위 오름차순이어야 함", chart.map { it.first }.sorted(), chart.map { it.first })
        }
    }

    @Test
    fun `번들 데이터 - 실제 통계가 실려 있고 인기 이름이 잡힌다`() {
        val stats = loadBundled()

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
