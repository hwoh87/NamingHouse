package com.naminghouse.app.ui

import com.naminghouse.engine.gen.NameStat

internal const val MISSING_GENDER_SAMPLE = "한쪽 성별의 집계 자료가 없어 비율을 표시하지 않아요. 실제 사용이 없다는 뜻은 아니에요."
internal const val NAME_STAT_SOURCE = "출처: 대법원 전자가족관계등록시스템 · 지역별 상위 이름 표본"

internal fun hasComparableGenderCounts(stat: NameStat): Boolean = stat.maleCount > 0 && stat.femaleCount > 0

internal fun sampleShareLabel(count: Int, total: Int): String {
    if (total <= 0) return "자료 없음"
    return when (val rounded = Math.round(count.toDouble() * 100 / total).toInt()) {
        0 -> "1% 미만"
        100 -> "99% 초과"
        else -> "약 ${rounded}%"
    }
}

internal fun sampleCountLabel(stat: NameStat): String = "수집된 지역 상위 자료 ${stat.total}건 · 전체 출생신고 건수가 아니에요"
