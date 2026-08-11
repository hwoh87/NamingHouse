package com.samramanshang.manseryeok.orrery.util

import kotlin.math.truncate

/**
 * 날짜 연산 유틸리티
 * pillars.ts의 날짜 계산 로직 포팅
 */
object DateUtils {

    private const val MINUTES_PER_DAY = 24 * 60

    /** Python의 int(a/b) — 0 방향 절사 */
    fun div(a: Int, b: Int): Int = truncate(a.toDouble() / b).toInt()

    /** Python 호환 모듈로 (항상 양수) */
    fun pymod(a: Int, b: Int): Int = ((a % b) + b) % b

    /** year의 1월 1일부터 month/day까지 일수 */
    fun dayOfYear(year: Int, month: Int, day: Int): Int {
        var e = 0
        for (i in 1 until month) {
            e += 31
            if (i == 2 || i == 4 || i == 6 || i == 9 || i == 11) {
                e -= 1
            }
            if (i == 2) {
                e -= 2
                if (year % 4 == 0) e += 1
                if (year % 100 == 0) e -= 1
                if (year % 400 == 0) e += 1
                if (year % 4000 == 0) e -= 1
            }
        }
        e += day
        return e
    }

    private fun daysInYear(year: Int): Int = dayOfYear(year, 12, 31)

    private fun daysInMonth(year: Int, month: Int): Int {
        require(month in 1..12) { "month must be in 1..12: $month" }
        val start = dayOfYear(year, month, 1)
        val end = if (month == 12) {
            daysInYear(year) + 1
        } else {
            dayOfYear(year, month + 1, 1)
        }
        return end - start
    }

    /** 두 날짜 사이의 일수 */
    fun daysBetween(
        y1: Int, m1: Int, d1: Int,
        y2: Int, m2: Int, d2: Int
    ): Int {
        val p1: Int; val p1n: Int; val p2: Int
        val pp1: Int; val pp2: Int; val pr: Int

        if (y2 > y1) {
            val _p1 = dayOfYear(y1, m1, d1)
            val _p1n = dayOfYear(y1, 12, 31)
            val _p2 = dayOfYear(y2, m2, d2)
            p1 = _p1; p1n = _p1n; p2 = _p2
            pp1 = y1; pp2 = y2; pr = -1
        } else if (y2 < y1) {
            val _p1 = dayOfYear(y2, m2, d2)
            val _p1n = dayOfYear(y2, 12, 31)
            val _p2 = dayOfYear(y1, m1, d1)
            p1 = _p1; p1n = _p1n; p2 = _p2
            pp1 = y2; pp2 = y1; pr = 1
        } else {
            // V3.07 (audit R4): 동년 분기 부호 — 전 분기 '과거=양수' 규약 정합 (1996년생 거울상 수정)
            return dayOfYear(y1, m1, d1) - dayOfYear(y2, m2, d2)
        }

        var dis = p1n - p1
        var k = pp1 + 1
        val ppp2 = pp2 - 1

        while (k <= ppp2) {
            when {
                k == -2000 && ppp2 > 1990 -> { dis += 1457682; k = 1991 }
                k == -1750 && ppp2 > 1990 -> { dis += 1366371; k = 1991 }
                k == -1500 && ppp2 > 1990 -> { dis += 1275060; k = 1991 }
                k == -1250 && ppp2 > 1990 -> { dis += 1183750; k = 1991 }
                k == -1000 && ppp2 > 1990 -> { dis += 1092439; k = 1991 }
                k == -750 && ppp2 > 1990 -> { dis += 1001128; k = 1991 }
                k == -500 && ppp2 > 1990 -> { dis += 909818; k = 1991 }
                k == -250 && ppp2 > 1990 -> { dis += 818507; k = 1991 }
                k == 0 && ppp2 > 1990 -> { dis += 727197; k = 1991 }
                k == 250 && ppp2 > 1990 -> { dis += 635887; k = 1991 }
                k == 500 && ppp2 > 1990 -> { dis += 544576; k = 1991 }
                k == 750 && ppp2 > 1990 -> { dis += 453266; k = 1991 }
                k == 1000 && ppp2 > 1990 -> { dis += 361955; k = 1991 }
                k == 1250 && ppp2 > 1990 -> { dis += 270644; k = 1991 }
                k == 1500 && ppp2 > 1990 -> { dis += 179334; k = 1991 }
                k == 1750 && ppp2 > 1990 -> { dis += 88023; k = 1991 }
            }
            dis += dayOfYear(k, 12, 31)
            k += 1
        }
        dis += p2
        dis *= pr

        return dis
    }

    /** 두 시점 사이의 분(minutes) 계산 */
    fun minutesBetween(
        uy: Int, umm: Int, ud: Int, uh: Int, umin: Int,
        y1: Int, mo1: Int, d1: Int, h1: Int, mm1: Int
    ): Int {
        val dispday = daysBetween(uy, umm, ud, y1, mo1, d1)
        return dispday * 24 * 60 + (uh - h1) * 60 + (umin - mm1)
    }

    /** 분(tmin)으로부터 날짜 역산 */
    fun dateFromMinutes(
        tmin: Int, uyear: Int, umonth: Int, uday: Int,
        uhour: Int, umin: Int
    ): IntArray {
        val totalMinutes = uhour.toLong() * 60L + umin.toLong() + tmin.toLong()
        val dayOffset = totalMinutes.floorDiv(MINUTES_PER_DAY.toLong())
        val minuteOfDay = totalMinutes.mod(MINUTES_PER_DAY.toLong()).toInt()

        var year = uyear
        var month = umonth
        var day = uday.toLong() + dayOffset

        while (day > daysInMonth(year, month)) {
            day -= daysInMonth(year, month)
            month += 1
            if (month > 12) {
                month = 1
                year += 1
            }
        }

        while (day <= 0) {
            month -= 1
            if (month < 1) {
                month = 12
                year -= 1
            }
            day += daysInMonth(year, month)
        }

        return intArrayOf(
            year,
            month,
            day.toInt(),
            minuteOfDay / 60,
            minuteOfDay % 60
        )
    }
}
