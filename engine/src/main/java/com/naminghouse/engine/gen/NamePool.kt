package com.naminghouse.engine.gen

import com.samramanshang.manseryeok.orrery.model.Gender

/** 후보 한글 이름 풀 항목 */
data class PoolName(
    val name: String,
    /** M/F/U(중성) */
    val gender: Char,
    /** 1=최근 인기 상위권, 2=흔히 쓰이는 이름, 3=고전·한자 친화적 */
    val tier: Int,
) {
    fun matches(target: Gender): Boolean = when (gender) {
        'U' -> true
        'M' -> target == Gender.M
        'F' -> target == Gender.F
        else -> true
    }
}

/**
 * names.tsv(한글 이름 후보 풀) 로더.
 * 형식: name \t gender(M|F|U) \t tier(1|2|3)
 */
class NamePool private constructor(val names: List<PoolName>) {

    fun forGender(gender: Gender, maxTier: Int = 3): List<PoolName> =
        names.filter { it.matches(gender) && it.tier <= maxTier }

    companion object {
        fun parse(lines: Sequence<String>): NamePool {
            val list = ArrayList<PoolName>(1200)
            var header = true
            for (line in lines) {
                if (header) { header = false; continue }
                if (line.isBlank()) continue
                val cols = line.split('\t')
                if (cols.size < 3) continue
                val name = cols[0].trim()
                if (name.isEmpty()) continue
                list.add(
                    PoolName(
                        name = name,
                        gender = cols[1].trim().firstOrNull() ?: 'U',
                        tier = cols[2].trim().toIntOrNull() ?: 3,
                    )
                )
            }
            return NamePool(list)
        }
    }
}
