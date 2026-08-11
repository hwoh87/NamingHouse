package com.samramanshang.manseryeok.orrery.util

/**
 * 한국(중국) 음력 ↔ 양력 변환 (1900~2099)
 *
 * 테이블 출처: solarlunar / chinese-lunar NPM 라이브러리에서 널리 검증된 표준 17비트 포맷
 *  - 하위 4비트 (0-3): 윤달 번호 (0 = 윤달 없음)
 *  - bit 16        : 윤달 일수 플래그 (1 = 30일, 0 = 29일)
 *  - bits 15-4     : 평달 1~12월 일수 (bit 15 = 1월, bit 4 = 12월; 1=30, 0=29)
 */
object KoreanLunarCalendar {

    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeap: Boolean
    )

    data class SolarDate(
        val year: Int,
        val month: Int,
        val day: Int
    )

    /**
     * 음력 → 양력 변환 (1900~2099 범위 지원)
     * 입력 음력 날짜가 유효하지 않으면 가장 가까운 유효 일자로 보정.
     */
    fun lunarToSolar(year: Int, month: Int, day: Int, isLeap: Boolean = false): SolarDate {
        // 1900-01-01(음) → 1900-01-31(양) 기준 offset 계산
        var offset = 0
        for (y in 1900 until year) {
            offset += daysInLunarYear(y)
        }
        val leapMonth = leapMonthOf(year)
        for (m in 1 until month) {
            offset += daysInLunarMonth(year, m, false)
            if (leapMonth == m) offset += daysInLunarMonth(year, m, true)
        }
        // isLeap 인데 해당 연·월에 실제 윤달이 없으면(잘못된 윤달 선택) 본달로 폴백한다.
        // 옛 코드는 leapMonth 무관하게 본달 일수를 더해 결과가 ~1달 밀려 엉뚱한 사주가 나왔다.
        // 아래 maxDay(=daysInLunarMonth(..., isLeap && leapMonth == month)) 가드와 정합시킨다.
        if (isLeap && leapMonth == month) {
            // 윤달 도달을 위해 본달(평달) 일수를 더한다
            offset += daysInLunarMonth(year, month, false)
        }
        // 일수 검증
        val maxDay = daysInLunarMonth(year, month, isLeap && leapMonth == month)
        val safeDay = day.coerceIn(1, maxDay)
        offset += (safeDay - 1)

        // 1900-01-31(양) + offset → 양력
        return jdToSolar(solarToJD(1900, 1, 31) + offset)
    }

    private fun jdToSolar(jd: Double): SolarDate {
        // 그레고리력 역변환
        val a = jd.toLong() + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = (e - (153 * m + 2) / 5 + 1).toInt()
        val month = (m + 3 - 12 * (m / 10)).toInt()
        val year = (100 * b + d - 4800 + m / 10).toInt()
        return SolarDate(year, month, day)
    }

    /**
     * 양력 → 음력 변환 (1900-01-31 이후 지원, 음력 1900-01-01 기준)
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate {
        val baseJd = solarToJD(1900, 1, 31)
        val targetJd = solarToJD(year, month, day)
        var offset = (targetJd - baseJd).toInt()

        var lunarYear = 1900
        while (lunarYear < 1900 + LUNAR_INFO.size) {
            val daysInYear = daysInLunarYear(lunarYear)
            if (offset < daysInYear) break
            offset -= daysInYear
            lunarYear++
        }

        val leap = leapMonthOf(lunarYear)
        var isLeap = false
        var lunarMonth = 1
        var m = 1
        while (m <= 12) {
            // 평달 먼저
            val dim = daysInLunarMonth(lunarYear, m, false)
            if (offset < dim) {
                lunarMonth = m
                isLeap = false
                break
            }
            offset -= dim

            // 해당 월 뒤에 윤달이 있으면 그다음을 윤달로 처리
            if (leap == m) {
                val dimLeap = daysInLunarMonth(lunarYear, m, true)
                if (offset < dimLeap) {
                    lunarMonth = m
                    isLeap = true
                    break
                }
                offset -= dimLeap
            }
            m++
        }

        return LunarDate(lunarYear, lunarMonth, offset + 1, isLeap)
    }

    // Julian Day Number (그레고리력)
    private fun solarToJD(y: Int, m: Int, d: Int): Double {
        val a = (14 - m) / 12
        val yr = y + 4800 - a
        val mo = m + 12 * a - 3
        return d + (153 * mo + 2) / 5 + 365 * yr + yr / 4 - yr / 100 + yr / 400 - 32045.0
    }

    // 음력 연도의 윤달 번호 (0 = 윤달 없음, 1~12)
    private fun leapMonthOf(year: Int): Int {
        val idx = year - 1900
        if (idx < 0 || idx >= LUNAR_INFO.size) return 0
        return (LUNAR_INFO[idx] and 0xFL).toInt()
    }

    // 음력 연도별 총 일수 캐시
    private val yearDaysCache by lazy { IntArray(LUNAR_INFO.size) { -1 } }

    private fun daysInLunarYear(year: Int): Int {
        val idx = year - 1900
        if (idx < 0 || idx >= LUNAR_INFO.size) return 354
        val cached = yearDaysCache[idx]
        if (cached >= 0) return cached
        var days = 0
        val leap = leapMonthOf(year)
        for (m in 1..12) {
            days += daysInLunarMonth(year, m, false)
            if (leap == m) days += daysInLunarMonth(year, m, true)
        }
        yearDaysCache[idx] = days
        return days
    }

    private fun daysInLunarMonth(year: Int, month: Int, isLeap: Boolean): Int {
        val idx = year - 1900
        if (idx < 0 || idx >= LUNAR_INFO.size) return 29
        val info = LUNAR_INFO[idx]
        return if (isLeap) {
            if ((info and 0x10000L) != 0L) 30 else 29
        } else {
            // month 1 → bit 15, month 12 → bit 4
            val bit = 0x10000L shr month
            if ((info and bit) != 0L) 30 else 29
        }
    }

    /**
     * 표준 lunarInfo 테이블 (1900~2099, 200 entries)
     * 출처: https://github.com/yize/chineseLunar / npm solarlunar — 광범위하게 검증됨
     */
    private val LUNAR_INFO = longArrayOf(
        // 1900-1909
        0x04bd8L, 0x04ae0L, 0x0a570L, 0x054d5L, 0x0d260L, 0x0d950L, 0x16554L, 0x056a0L, 0x09ad0L, 0x055d2L,
        // 1910-1919
        0x04ae0L, 0x0a5b6L, 0x0a4d0L, 0x0d250L, 0x1d255L, 0x0b54fL, 0x0d6a0L, 0x0ada2L, 0x095b0L, 0x14977L,
        // 1920-1929
        0x04970L, 0x0a4b0L, 0x0b4b5L, 0x06a50L, 0x06d40L, 0x1ab54L, 0x02b60L, 0x09570L, 0x052f2L, 0x04970L,
        // 1930-1939
        0x06566L, 0x0d4a0L, 0x0ea50L, 0x06e95L, 0x05ad0L, 0x02b60L, 0x186e3L, 0x092e0L, 0x1c8d7L, 0x0c950L,
        // 1940-1949
        0x0d4a0L, 0x1d8a6L, 0x0b550L, 0x056a0L, 0x1a5b4L, 0x025d0L, 0x092d0L, 0x0d2b2L, 0x0a950L, 0x0b557L,
        // 1950-1959
        0x06ca0L, 0x0b550L, 0x15355L, 0x04da0L, 0x0a5b0L, 0x14573L, 0x052b0L, 0x0a9a8L, 0x0e950L, 0x06aa0L,
        // 1960-1969
        0x0aea6L, 0x0ab50L, 0x04b60L, 0x0aae4L, 0x0a570L, 0x05260L, 0x0f263L, 0x0d950L, 0x05b57L, 0x056a0L,
        // 1970-1979
        0x096d0L, 0x04dd5L, 0x04ad0L, 0x0a4d0L, 0x0d4d4L, 0x0d250L, 0x0d558L, 0x0b540L, 0x0b5a0L, 0x195a6L,
        // 1980-1989
        0x095b0L, 0x049b0L, 0x0a974L, 0x0a4b0L, 0x0b27aL, 0x06a50L, 0x06d40L, 0x0af46L, 0x0ab60L, 0x09570L,
        // 1990-1999
        0x04af5L, 0x04970L, 0x064b0L, 0x074a3L, 0x0ea50L, 0x06b58L, 0x055c0L, 0x0ab60L, 0x096d5L, 0x092e0L,
        // 2000-2009
        0x0c960L, 0x0d954L, 0x0d4a0L, 0x0da50L, 0x07552L, 0x056a0L, 0x0abb7L, 0x025d0L, 0x092d0L, 0x0cab5L,
        // 2010-2019
        0x0a950L, 0x0b4a0L, 0x0baa4L, 0x0ad50L, 0x055d9L, 0x04ba0L, 0x0a5b0L, 0x15176L, 0x052b0L, 0x0a930L,
        // 2020-2029
        0x07954L, 0x06aa0L, 0x0ad50L, 0x05b52L, 0x04b60L, 0x0a6e6L, 0x0a4e0L, 0x0d260L, 0x0ea65L, 0x0d530L,
        // 2030-2039
        0x05aa0L, 0x076a3L, 0x096d0L, 0x04afbL, 0x04ad0L, 0x0a4d0L, 0x1d0b6L, 0x0d250L, 0x0d520L, 0x0dd45L,
        // 2040-2049
        0x0b5a0L, 0x056d0L, 0x055b2L, 0x049b0L, 0x0a577L, 0x0a4b0L, 0x0aa50L, 0x1b255L, 0x06d20L, 0x0ada0L,
        // 2050-2059
        0x14b63L, 0x09370L, 0x049f8L, 0x04970L, 0x064b0L, 0x168a6L, 0x0ea50L, 0x06b20L, 0x1a6c4L, 0x0aae0L,
        // 2060-2069
        0x0a2e0L, 0x0d2e3L, 0x0c960L, 0x0d557L, 0x0d4a0L, 0x0da50L, 0x05d55L, 0x056a0L, 0x0a6d0L, 0x055d4L,
        // 2070-2079
        0x052d0L, 0x0a9b8L, 0x0a950L, 0x0b4a0L, 0x0b6a6L, 0x0ad50L, 0x055a0L, 0x0aba4L, 0x0a5b0L, 0x052b0L,
        // 2080-2089
        0x0b273L, 0x06930L, 0x07337L, 0x06aa0L, 0x0ad50L, 0x14b55L, 0x04b60L, 0x0a570L, 0x054e4L, 0x0d160L,
        // 2090-2099
        0x0e968L, 0x0d520L, 0x0daa0L, 0x16aa6L, 0x056d0L, 0x04ae0L, 0x0a9d4L, 0x0a2d0L, 0x0d150L, 0x0f252L
    )
}
