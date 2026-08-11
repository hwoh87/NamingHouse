package com.naminghouse.engine.gen

import com.samramanshang.manseryeok.orrery.model.Gender

/** 한 이름의 출생신고 통계 */
data class NameStat(
    val name: String,
    /** 남녀 중 더 많이 쓰인 쪽 */
    val dominant: Gender,
    val maleCount: Int,
    val femaleCount: Int,
    /** 연도 → 순위 (최근 연도부터) */
    val ranks: List<Pair<Int, Int>>,
) {
    val total: Int get() = maleCount + femaleCount

    /** 남아 비율 0~100 (반올림). 여아 비율은 100 - 이 값. */
    val malePercent: Int
        get() = if (total == 0) 50 else Math.round(maleCount * 100f / total)

    /** 가장 최근 연도의 순위 (없으면 null) */
    val latestRank: Pair<Int, Int>? get() = ranks.firstOrNull()

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
class NameStats private constructor(private val byName: Map<String, NameStat>) {

    operator fun get(name: String): NameStat? = byName[name]

    val size: Int get() = byName.size

    companion object {
        val EMPTY = NameStats(emptyMap())

        fun parse(lines: Sequence<String>): NameStats {
            val map = HashMap<String, NameStat>(4000)
            var header = true
            for (line in lines) {
                if (header) { header = false; continue }
                if (line.isBlank()) continue
                val c = line.split('\t')
                if (c.size < 5) continue
                val name = c[0].trim()
                if (name.isEmpty()) continue
                val ranks = c[4].split(',').mapNotNull { entry ->
                    val parts = entry.split(':')
                    val year = parts.getOrNull(0)?.trim()?.toIntOrNull()
                    val rank = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    if (year != null && rank != null) year to rank else null
                }
                map[name] = NameStat(
                    name = name,
                    dominant = if (c[1].trim() == "F") Gender.F else Gender.M,
                    maleCount = c[2].trim().toIntOrNull() ?: 0,
                    femaleCount = c[3].trim().toIntOrNull() ?: 0,
                    ranks = ranks.sortedByDescending { it.first },
                )
            }
            return NameStats(map)
        }
    }
}
