package com.naminghouse.engine

import com.naminghouse.engine.gen.NameStats
import com.naminghouse.engine.gen.NameStatsMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class NameStatsValidationTest {
    private val header = "name\tdominant\tmaleCount\tfemaleCount\tranks\n"
    private val valid = header + "도윤\tM\t10\t1\t2026:1\n"

    private fun rejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Invalid statistics must fail as a whole")
        } catch (_: IllegalArgumentException) {
            // Expected: no silently repaired or partially loaded data.
        }
    }

    private fun metadata(data: String = valid, extra: String = ""): NameStatsMetadata {
        val hash = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return NameStatsMetadata.parse(("""
            schemaVersion=1
            datasetSha256=$hash
            sourceScope=regional_top_names
            queryYearStart=2008
            queryYearEnd=2026
            collectedFrom=unknown
            collectedThrough=unknown
            sourcePeriodStart=unknown
            sourcePeriodEnd=unknown
            coverageStatus=legacy_unverified
            nameCount=1
        """.trimIndent() + "\n" + extra).lineSequence())
    }

    @Test
    fun `malformed records do not quietly become zero counts or partial data`() {
        val invalid = listOf(
            "도윤\tM\tbad\t0\t2026:1", "도윤\tM\t-1\t0\t2026:1",
            "도윤\tM\t1.5\t0\t2026:1", "도윤\tM\t0\t0\t2026:1",
            "도윤\tM\t2147483648\t0\t2026:1", "도윤\tM\t2147483647\t1\t2026:1",
            "도윤\tX\t1\t0\t2026:1", "도윤\tF\t1\t0\t2026:1",
            "도윤\tM\t1\t0\t2026:0", "도윤\tM\t1\t0\t2026:-1",
            "도윤\tM\t1\t0\t2026:1,2026:2", "도윤\tM\t1\t0\t2026:1:2",
            "도윤\tM\t1\t0\twrong", "도윤\tM\t1\t0", "도윤\tM\t1\t0\t\textra",
            "도윤\tM\t1\t0\t2026:1,", " John\tM\t1\t0\t2026:1",
        )
        invalid.forEach { row -> rejects { NameStats.parse((header + row).lineSequence()) } }
    }

    @Test
    fun `duplicate name or missing header or empty data is rejected`() {
        rejects { NameStats.parse((valid + "도윤\tM\t2\t0\t2026:2").lineSequence()) }
        rejects { NameStats.parse(sequenceOf("wrong header", "도윤\tM\t1\t0\t")) }
        rejects { NameStats.parse(emptySequence()) }
        rejects { NameStats.parse(header.lineSequence()) }
    }

    @Test
    fun `largest valid total remains positive and has a bounded percent`() {
        val stat = NameStats.parse((header + "도윤\tM\t2147483646\t1\t").lineSequence())["도윤"]!!
        assertEquals(Int.MAX_VALUE, stat.total)
        assertEquals(100, stat.malePercent)
    }

    @Test
    fun `metadata is shared with each name and unknown dates stay explicit`() {
        val metadata = metadata()
        val stats = NameStats.parse(valid.toByteArray(), metadata)
        assertSame(metadata, stats.metadata)
        assertSame(metadata, stats["도윤"]!!.metadata)
        assertEquals("수집일 미기록", metadata.collectionLabel)
        assertEquals("조회 연도 2008~2026년 · 원자료 집계 종료일 미기록", metadata.periodLabel)
        assertEquals("legacy_unverified", metadata.coverageStatus)
        assertFalse(metadata.periodLabel.contains("최신"))
        assertEquals(stats.size, NameStats.parse(valid.lineSequence(), metadata).size)
    }

    @Test
    fun `mismatched data hash or row count prevents loading`() {
        rejects { NameStats.parse(valid.replace("10", "11").toByteArray(), metadata()) }
        rejects { NameStats.parse(valid.toByteArray(), metadata().copy(nameCount = 2)) }
        rejects { NameStats.parse(valid.toByteArray(), metadata().copy(queryYearEnd = 2025)) }
        // The byte API verifies actual line endings rather than normalizing a modified asset.
        rejects { NameStats.parse(valid.replace("\n", "\r\n").toByteArray(), metadata()) }
    }

    @Test
    fun `malformed UTF8 is rejected even without metadata`() {
        rejects { NameStats.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
    }

    @Test
    fun `metadata rejects duplicate keys unsupported versions and invalid dates`() {
        rejects { metadata(extra = "schemaVersion=2") }
        rejects { NameStatsMetadata.parse(sequenceOf("schemaVersion=2")) }
        rejects { NameStatsMetadata.parse(sequenceOf("schemaVersion=1", "sourceScope=regional_top_names")) }
        val properties = """
            schemaVersion=1
            sourceScope=regional_top_names
            datasetSha256=${"a".repeat(64)}
            queryYearStart=2008
            queryYearEnd=2026
        """.trimIndent()
        listOf(
            "collectedFrom=2026-01-01T00:00:00Z",
            "collectedFrom=2026-02-01T00:00:00Z\ncollectedThrough=2026-01-01T00:00:00Z",
            "sourcePeriodEnd=2026-02-30", "coverageStatus=nationwide_verified", "nameCount=-1",
        ).forEach { suffix -> rejects { NameStatsMetadata.parse((properties + "\n" + suffix).lineSequence()) } }
    }

    @Test
    fun `real recorded collection range stays distinct from source reporting cutoff`() {
        val metadata = metadata().copy(collectedFrom = "2026-01-01T00:00:00Z", collectedThrough = "2026-01-02T00:00:00Z")
        assertEquals("수집 기간 2026-01-01~2026-01-02", metadata.collectionLabel)
        assertTrue(metadata.periodLabel.endsWith("원자료 집계 종료일 미기록"))
    }

    @Test
    fun `bundled metadata matches unchanged dataset and declares uncertain provenance`() {
        val asset = listOf(File("src/main/assets/name-stats.tsv"), File("engine/src/main/assets/name-stats.tsv"))
            .first(File::exists)
        val metadata = asset.resolveSibling("name-stats-metadata.properties").bufferedReader().useLines(NameStatsMetadata::parse)
        val stats = NameStats.parse(asset.readBytes(), metadata)
        assertEquals(213, stats.size)
        assertEquals(2008, metadata.queryYearStart)
        assertEquals(2026, metadata.queryYearEnd)
        assertEquals("legacy_unverified", metadata.coverageStatus)
        assertEquals("수집일 미기록", metadata.collectionLabel)
    }
}
