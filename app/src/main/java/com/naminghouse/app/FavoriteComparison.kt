package com.naminghouse.app

import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.saju.SajuNamingService
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.util.KoreanLunarCalendar
import java.time.LocalDate

data class ComparedFavorite(val favorite: FavoriteName, val evaluation: NameEvaluation)

/** One comparison has exactly one captured birth/school context. Stored scores are never reused. */
data class FavoriteComparison(
    val entries: List<ComparedFavorite>,
    val saju: SajuSummary?,
    val school: BaleumSchool,
    val conditionLabel: String,
    val birthLine: String? = null,
    val evaluationRecord: EvaluationRecord? = null,
)

/** Validate outside the imported engine, which intentionally normalizes invalid lunar input. */
fun isValidBirthDate(year: Int, month: Int, day: Int, isLunar: Boolean, isLeapMonth: Boolean): Boolean {
    if (year !in 1900..2050 || month !in 1..12 || day !in 1..31) return false
    return runCatching {
        if (!isLunar) {
            LocalDate.of(year, month, day)
            true
        } else {
            val solar = KoreanLunarCalendar.lunarToSolar(year, month, day, isLeapMonth)
            val roundTrip = KoreanLunarCalendar.solarToLunar(solar.year, solar.month, solar.day)
            roundTrip.year == year && roundTrip.month == month && roundTrip.day == day &&
                roundTrip.isLeap == isLeapMonth
        }
    }.getOrDefault(false)
}

internal fun evaluateFavorite(
    favorite: FavoriteName,
    db: HanjaDb,
    saju: SajuSummary?,
    school: BaleumSchool,
): NameEvaluation {
    require(favorite.surname.isNotEmpty() && favorite.givenName.isNotEmpty() &&
        favorite.hanja.length == favorite.fullHangul.length) { "저장된 이름의 한자 정보를 확인해 주세요" }
    val entries = favorite.hanja.mapIndexed { index, char ->
        val entry = requireNotNull(db.byChar[char]) { "저장된 이름의 한자 자료를 찾지 못했습니다" }
        require(favorite.fullHangul[index].toString() in entry.readings) {
            "저장된 이름과 한자 독음이 맞지 않습니다"
        }
        entry
    }
    return NameEvaluator.evaluate(
        favorite.surname, favorite.givenName,
        entries.take(favorite.surname.length), entries.drop(favorite.surname.length), saju, school,
    )
}

internal fun compareFavoriteNames(
    items: List<FavoriteName>,
    db: HanjaDb,
    birth: BirthInput?,
    school: BaleumSchool,
    conditionLabel: String,
    birthLine: String?,
): FavoriteComparison {
    require(items.size in 2..3) { "비교할 이름을 2~3개 선택해 주세요" }
    require(items.map { Triple(it.surname, it.givenName, it.hanja) }.distinct().size == items.size) {
        "서로 다른 이름을 선택해 주세요"
    }
    birth?.let {
        require(isValidBirthDate(it.year, it.month, it.day, it.isLunar, it.isLeapMonth)) {
            "생년월일을 확인해 주세요. 실제 달력에 있는 날짜와 윤달을 선택해 주세요"
        }
        require(it.unknownTime || (it.hour in 0..23 && it.minute in 0..59)) { "출생 시간을 확인해 주세요" }
    }
    val saju = birth?.let(SajuNamingService::analyze)
    return FavoriteComparison(
        items.map { ComparedFavorite(it, evaluateFavorite(it, db, saju, school)) },
        saju, school, conditionLabel, birthLine,
    )
}
