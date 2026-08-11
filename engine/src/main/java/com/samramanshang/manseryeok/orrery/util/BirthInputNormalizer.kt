package com.samramanshang.manseryeok.orrery.util

import com.samramanshang.manseryeok.orrery.model.BirthInput

/**
 * V2.95: 출생 입력값 시간 보정 단일점.
 *
 * 이전: SajuRepository / ZiweiRepository / NatalRepository 가 각각
 *   `TimezoneUtils.adjustBirthInputToSolarTime` / `adjustBirthInputToKstWallClock` /
 *   `birthInputToUtcDate` 를 직접 호출 — 3곳에서 보정 분기를 중복 표현.
 *
 * 이후: 본 노멀라이저가 (1) 동양 만세력 (사주/자미두수) 용 AdjustedTime, (2) 서양 점성술 용
 *   UTC Calendar 두 가지 표준 보정을 제공. 3엔진 모두 이 노멀라이저에 위임.
 *
 * 향후 시간 보정 룰 변경 (예: 새 DST 구간, 새 표준시 변경) 시 이 파일만 수정하면 됨.
 */
object BirthInputNormalizer {

    /**
     * 동양 만세력 (사주·자미두수) 용 시간 보정.
     *
     * 분기 규칙:
     *   - `input.useTrueSolarTime == true` 또는 timezone 이 한국 외 → 진태양시 보정
     *     (경도 × 4분 + 균시차 EoT)
     *   - 그 외 → KST 벽시계 등가 (역사 KDT/UTC+8:30 자동 보정 포함)
     *
     * @return 보정된 양력 (year, month, day, hour, minute)
     */
    fun normalizeForOrientalCalendar(input: BirthInput): TimezoneUtils.AdjustedTime {
        val useSolarTime = input.useTrueSolarTime ||
            (input.timezone != null && input.timezone != TimezoneUtils.DEFAULT_TIMEZONE)
        return if (useSolarTime) {
            TimezoneUtils.adjustBirthInputToSolarTime(input)
        } else {
            TimezoneUtils.adjustBirthInputToKstWallClock(input)
        }
    }

    /**
     * 서양 점성술 (Natal) 용 UTC 시각 필드.
     *
     * 줄리안 데이트(JD) 계산을 위한 UTC 변환. timezone 이 null 이면 Asia/Seoul.
     * `unknownTime` 일 때는 호출 측이 hour=12, minute=0 으로 전달하는 것을 권장.
     * (P1a: Calendar 반환 → 순수 AdjustedTime — 소비처는 전부 Y/M/D/H/M 필드만 읽는다)
     */
    fun toUtcTime(input: BirthInput, hour: Int, minute: Int): TimezoneUtils.AdjustedTime {
        val timezone = TimezoneUtils.getBirthTimezone(input)
        return TimezoneUtils.birthInputToUtcTime(
            input.year, input.month, input.day, hour, minute, timezone
        )
    }
}
