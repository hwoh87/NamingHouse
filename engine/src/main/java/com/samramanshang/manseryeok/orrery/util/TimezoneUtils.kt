package com.samramanshang.manseryeok.orrery.util

import com.samramanshang.manseryeok.orrery.model.BirthInput
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 타임존 · DST 유틸리티 (timezone.ts 포팅)
 *
 * P1a: java.util.Calendar → kotlinx-datetime. JVM 에선 java.time tzdb 위임이라
 * 역사적 Asia/Seoul 오프셋(1948-51 KDT, 1954-61 UTC+8:30, 1987-88 KDT)이 기존과 동일하고,
 * JS 에선 @js-joda/timezone 의 같은 IANA 데이터를 쓴다 — 플랫폼 간 동일 결과.
 */
object TimezoneUtils {

    const val DEFAULT_TIMEZONE = "Asia/Seoul"
    private const val DEFAULT_LONGITUDE = 127.0992
    private const val KST_FIXED_OFFSET_MINUTES = 9 * 60

    data class AdjustedTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int
    )

    fun getBirthTimezone(input: BirthInput): String {
        return input.timezone ?: DEFAULT_TIMEZONE
    }

    /** 로컬 벽시계(해당 타임존)를 UTC epoch 순간으로 해석 */
    private fun wallClockToInstant(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, timezone: String
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.of(timezone))

    private fun Instant.toUtcAdjusted(): AdjustedTime {
        val dt = toLocalDateTime(TimeZone.UTC)
        return AdjustedTime(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute)
    }

    /** 로컬 벽시계 → UTC 필드 (Natal 의 줄리안 데이 계산용) */
    fun birthInputToUtcTime(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, timezone: String
    ): AdjustedTime = wallClockToInstant(year, month, day, hour, minute, timezone).toUtcAdjusted()

    private fun getEquationOfTimeMinutes(year: Int, month: Int, day: Int): Double {
        val dayOfYear = LocalDate(year, month, day).dayOfYear
        val b = (2 * PI * (dayOfYear - 81)) / 364.0
        return 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
    }

    /**
     * 비-한국 출생 경로: 경도 × 4분 + 균시차(EoT) 보정
     */
    fun adjustBirthInputToSolarTime(input: BirthInput): AdjustedTime {
        val timezone = getBirthTimezone(input)
        val utcInstant = wallClockToInstant(
            input.year, input.month, input.day, input.hour, input.minute, timezone
        )
        val equationOfTime = getEquationOfTimeMinutes(input.year, input.month, input.day)
        val longitude = input.longitude
        val shiftMs = ((longitude * 4 + equationOfTime) * 60_000).toLong()
        return Instant.fromEpochMilliseconds(utcInstant.toEpochMilliseconds() + shiftMs)
            .toUtcAdjusted()
    }

    /**
     * 한국 출생 경로: KST(+9:00) 벽시계 등가 시각을 반환
     * 1948-1951 KDT, 1954-1961 UTC+8:30/+9:30, 1987-1988 KDT 자동 포함
     */
    fun adjustBirthInputToKstWallClock(input: BirthInput): AdjustedTime {
        // Asia/Seoul 로 해석해 UTC epoch 를 얻은 뒤(역사 오프셋 반영),
        // 고정 +9:00 을 더해 "고정 KST 벽시계" 로 환산한다.
        // (epoch 는 이미 UTC 이므로 추가로 offset 을 빼면 안 된다 — 이중 변환 버그 방지)
        val utcInstant = wallClockToInstant(
            input.year, input.month, input.day, input.hour, input.minute, DEFAULT_TIMEZONE
        )
        return Instant.fromEpochMilliseconds(
            utcInstant.toEpochMilliseconds() + KST_FIXED_OFFSET_MINUTES * 60_000L
        ).toUtcAdjusted()
    }

    /**
     * 진태양시(true solar time)와 한국 표준시(KST +9:00 = 동경 135°E) 사이의 분 단위 차이.
     * +면 표준시보다 늦고(동쪽), -면 빠르다(서쪽).
     * 서울(127°) 기준 약 -32분 ± EoT.
     *
     * UI 표시 전용 — 사주 계산은 별도로 입력의 useTrueSolarTime 플래그를 따른다.
     */
    fun trueSolarDeltaMinutes(year: Int, month: Int, day: Int, longitude: Double): Int {
        val eot = getEquationOfTimeMinutes(year, month, day)
        val delta = (longitude - 135.0) * 4.0 + eot
        return delta.toInt()
    }

    /**
     * 한국 역사적 시간 편차 감지
     */
    fun isKoreanHistoricalTimeAnomaly(year: Int, month: Int, day: Int): Boolean {
        // 원 구현과 동일 기준점: 그 날 12:00 UTC 시점의 Asia/Seoul 오프셋.
        val instant = LocalDateTime(year, month, day, 12, 0).toInstant(TimeZone.UTC)
        val offsetSeconds = TimeZone.of(DEFAULT_TIMEZONE).offsetAt(instant).totalSeconds
        return offsetSeconds != KST_FIXED_OFFSET_MINUTES * 60
    }
}
