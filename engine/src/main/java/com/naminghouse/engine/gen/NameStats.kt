package com.naminghouse.engine.gen

import com.samramanshang.manseryeok.orrery.model.Gender
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** 수집 범위와 실제 확인한 날짜를 분리한다. unknown은 추정 날짜로 보완하지 않는다. */
data class NameStatsMetadata(
    val datasetSha256: String? = null,
    val queryYearStart: Int? = null,
    val queryYearEnd: Int? = null,
    val collectedFrom: String? = null,
    val collectedThrough: String? = null,
    val sourcePeriodStart: String? = null,
    val sourcePeriodEnd: String? = null,
    val coverageStatus: String = "unknown",
    val nameCount: Int? = null,
) {
    val sourceScope: String get() = "지역별 상위 이름 표본"
    val scopeDescription: String get() = SCOPE_DESCRIPTION
    val collectionLabel: String
        get() = if (collectedFrom == null || collectedThrough == null) "수집일 미기록"
        else if (collectedFrom.take(10) == collectedThrough.take(10)) "수집일 ${collectedThrough.take(10)}"
        else "수집 기간 ${collectedFrom.take(10)}~${collectedThrough.take(10)}"
    val periodLabel: String
        get() {
            val query = if (queryYearStart != null && queryYearEnd != null)
                "조회 연도 ${queryYearStart}~${queryYearEnd}년" else "조회 연도 미기록"
            val end = sourcePeriodEnd?.let { "원자료 집계 종료일 $it" } ?: "원자료 집계 종료일 미기록"
            return "$query · $end"
        }

    internal fun verify(bytes: ByteArray) {
        datasetSha256?.let { expected ->
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(actual == expected) { "이름 통계와 메타데이터의 SHA-256이 다릅니다" }
        }
    }

    companion object {
        const val SCOPE_DESCRIPTION = "지역별 상위 이름을 합산한 참고 자료예요. 전국 전체 신고 건수나 정확한 성별 사용 비율을 뜻하지 않아요."
        val UNKNOWN = NameStatsMetadata()

        /** 생성기의 단순 key=value 형식. 중복 키/지원하지 않는 버전을 조용히 덮어쓰지 않는다. */
        fun parse(lines: Sequence<String>): NameStatsMetadata {
            val fields = linkedMapOf<String, String>()
            lines.forEach { line ->
                if (line.isBlank() || line.trimStart().startsWith('#')) return@forEach
                val split = line.indexOf('=')
                require(split > 0) { "잘못된 이름 통계 메타데이터" }
                val key = line.substring(0, split).trim()
                val value = line.substring(split + 1).trim()
                require(fields.put(key, value) == null) { "중복 메타데이터: $key" }
            }
            require(fields["schemaVersion"] == "1") { "지원하지 않는 이름 통계 메타데이터 버전" }
            require(fields["sourceScope"] == "regional_top_names") { "지원하지 않는 이름 통계 수집 범위" }
            fun optional(key: String): String? = fields[key]?.takeUnless { it == "unknown" || it.isEmpty() }
            fun number(key: String): Int? = optional(key)?.let {
                require(it.matches(Regex("[0-9]+"))) { "잘못된 메타데이터 숫자: $key" }
                it.toIntOrNull() ?: throw IllegalArgumentException("메타데이터 숫자 범위 초과: $key")
            }
            val hash = optional("datasetSha256")
            require(hash != null && hash.matches(Regex("[0-9a-f]{64}"))) { "메타데이터 SHA-256 누락 또는 오류" }
            val start = number("queryYearStart")
            val end = number("queryYearEnd")
            require(start != null && end != null && start in 2008..9999 && end in start..9999) { "잘못된 조회 연도 범위" }
            val from = optional("collectedFrom")
            val through = optional("collectedThrough")
            require((from == null) == (through == null)) { "불완전한 수집 기간" }
            try {
                if (from != null && through != null) require(!Instant.parse(from).isAfter(Instant.parse(through)))
            } catch (e: Exception) { throw IllegalArgumentException("잘못된 수집 기간", e) }
            val periodStart = optional("sourcePeriodStart")
            val periodEnd = optional("sourcePeriodEnd")
            try {
                periodStart?.let { LocalDate.parse(it) }
                periodEnd?.let { LocalDate.parse(it) }
                if (periodStart != null && periodEnd != null) require(periodStart <= periodEnd)
            } catch (e: Exception) { throw IllegalArgumentException("잘못된 원자료 기간", e) }
            val coverage = fields["coverageStatus"] ?: "unknown"
            require(coverage in setOf("unknown", "legacy_unverified", "complete_query_set")) { "잘못된 수집 상태" }
            val count = number("nameCount")
            require(count == null || count > 0) { "빈 통계 데이터" }
            return NameStatsMetadata(hash, start, end, from, through, periodStart, periodEnd, coverage, count)
        }
    }
}

/** 지역별 상위 이름 표본에서 확인된 건수와 순위. 전국 전체 건수/성별 비율이 아니다. */
data class NameStat(
    val name: String,
    /** 남녀 중 더 많이 쓰인 쪽 */
    val dominant: Gender,
    val maleCount: Int,
    val femaleCount: Int,
    /** 연도 → 순위 (최근 연도부터) */
    val ranks: List<Pair<Int, Int>>,
    val metadata: NameStatsMetadata = NameStatsMetadata.UNKNOWN,
) {
    val total: Int get() = maleCount + femaleCount

    /** 수집 표본 내 남아 비율 0~100 (반올림). 전국 성별 사용 비율이 아니다. */
    val malePercent: Int
        get() = if (total == 0) 50 else (maleCount.toDouble() * 100 / total).let { Math.round(it).toInt() }

    /** 가장 최근 연도의 순위 (없으면 null) */
    val latestRank: Pair<Int, Int>? get() = ranks.firstOrNull()

    /** [year]년 순위 — 그 해 기록이 없으면 null */
    fun rankIn(year: Int): Int? = ranks.firstOrNull { it.first == year }?.second

    /** 최근 몇 해 안에 100위 안에 든 적이 있는가 — '인기 이름' 판정 */
    val isPopular: Boolean get() = ranks.any { (_, rank) -> rank <= 100 }
}

/**
 * name-stats.tsv 로더. 생성기는 tools/name-stats/build_name_stats.py.
 * 형식: name \t dominant(M|F) \t maleCount \t femaleCount \t ranks("2026:107,2025:103")
 *
 * 출처는 대법원 전자가족관계등록시스템 통계(시도별 상위 20위를 전국 합산)라
 * 하위권 이름은 과소집계된다. 순위는 인기 이름 표시용 참고값으로만 쓴다.
 */
class NameStats private constructor(
    private val byName: Map<String, NameStat>,
    val metadata: NameStatsMetadata = NameStatsMetadata.UNKNOWN,
) {

    operator fun get(name: String): NameStat? = byName[name]

    val size: Int get() = byName.size

    /** 순위 이력이 존재하는 연도들 — 최신 해부터 */
    fun chartYears(): List<Int> =
        byName.values.asSequence()
            .flatMap { it.ranks.asSequence() }
            .map { it.first }
            .distinct()
            .sortedDescending()
            .toList()

    /**
     * [year]년 [gender] 인기 차트 — (순위, 통계)를 순위 오름차순으로.
     *
     * 순위는 우세 성별 기준으로만 저장되므로(빌더 참조) 남녀 겸용 이름은 우세 쪽 차트에만 선다.
     * 시도별 상위 20위 합산 데이터라 30위 밖은 연속성이 없다 — [maxRank] 기본값이 30인 이유.
     */
    fun chart(gender: Gender, year: Int, maxRank: Int = 30): List<Pair<Int, NameStat>> =
        byName.values
            .mapNotNull { s ->
                val rank = s.rankIn(year) ?: return@mapNotNull null
                if (s.dominant == gender && rank <= maxRank) rank to s else null
            }
            .sortedBy { it.first }

    companion object {
        val EMPTY = NameStats(emptyMap())

        private const val HEADER = "name\tdominant\tmaleCount\tfemaleCount\tranks"

        /** 원본 바이트를 검증한다. assets는 이 오버로드로 읽어 줄바꿈까지 해시 검증한다. */
        fun parse(bytes: ByteArray, metadata: NameStatsMetadata = NameStatsMetadata.UNKNOWN): NameStats {
            metadata.verify(bytes)
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            catch (e: Exception) { throw IllegalArgumentException("이름 통계 UTF-8 오류", e) }
            return parseRows(text.lineSequence(), metadata)
        }

        /** 생성기 표준 UTF-8/LF + 마지막 개행 기준. 원본 검증에는 bytes 오버로드를 권장한다. */
        fun parse(lines: Sequence<String>, metadata: NameStatsMetadata = NameStatsMetadata.UNKNOWN): NameStats {
            val rows = lines.toList().dropLastWhile { it.isEmpty() }
            metadata.verify((rows.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
            return parseRows(rows.asSequence(), metadata)
        }

        private fun parseRows(lines: Sequence<String>, metadata: NameStatsMetadata): NameStats {
            val iterator = lines.iterator()
            require(iterator.hasNext() && iterator.next() == HEADER) { "이름 통계 헤더 오류" }
            val map = LinkedHashMap<String, NameStat>()
            var lineNumber = 1
            while (iterator.hasNext()) {
                val line = iterator.next()
                lineNumber++
                if (line.isBlank()) continue
                val c = line.split('\t')
                require(c.size == 5) { "이름 통계 $lineNumber 행의 열 수 오류" }
                val name = c[0]
                require(name.length in 1..3 && name.all { it in '가'..'힣' }) { "잘못된 이름: $name" }
                require(name !in map) { "중복 이름 통계: $name" }
                require(c[1] == "M" || c[1] == "F") { "잘못된 성별: $name" }
                fun count(value: String): Int {
                    require(value.matches(Regex("[0-9]+"))) { "잘못된 통계 숫자: $name" }
                    return value.toIntOrNull() ?: throw IllegalArgumentException("통계 숫자 범위 초과: $name")
                }
                val male = count(c[2])
                val female = count(c[3])
                require(male.toLong() + female in 1..Int.MAX_VALUE.toLong()) { "잘못된 건수 합계: $name" }
                require(c[1] == if (male >= female) "M" else "F") { "건수와 우세 성별 불일치: $name" }
                val seenYears = HashSet<Int>()
                val ranks = if (c[4].isEmpty()) emptyList() else c[4].split(',').map { entry ->
                    val parts = entry.split(':')
                    require(parts.size == 2) { "잘못된 순위 이력: $name" }
                    val year = count(parts[0])
                    val rank = count(parts[1])
                    require(year in 2008..9999 && rank > 0 && seenYears.add(year)) { "잘못되거나 중복된 순위 연도: $name" }
                    metadata.queryYearStart?.let { require(year >= it) { "조회 범위 밖 순위: $name" } }
                    metadata.queryYearEnd?.let { require(year <= it) { "조회 범위 밖 순위: $name" } }
                    year to rank
                }
                map[name] = NameStat(name, if (c[1] == "F") Gender.F else Gender.M,
                    male, female, ranks.sortedByDescending { it.first }, metadata)
            }
            require(map.isNotEmpty()) { "이름 통계가 비어 있습니다" }
            metadata.nameCount?.let { require(map.size == it) { "메타데이터 이름 수 불일치" } }
            return NameStats(map, metadata)
        }
    }
}
