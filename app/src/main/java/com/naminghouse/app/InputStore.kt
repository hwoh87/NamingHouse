package com.naminghouse.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * 마지막으로 계산에 쓴 입력.
 *
 * 임신 기간 내내 같은 아기 정보로 앱을 다시 여는 게 실제 사용 흐름인데,
 * 프로세스가 죽을 때마다 성씨·생년월일·지역을 처음부터 다시 넣게 하면
 * 그때마다 이탈 지점이 된다. 제출에 성공한 입력만 저장한다 —
 * 타이핑 중간값을 저장하면 반쯤 지운 상태가 복원되는 수가 있다.
 */
data class SavedInput(
    val surname: String,
    /** 성씨 한자 나열 — 복원 시 그 음의 후보에서 같은 글자를 찾아 되살린다 */
    val surnameHanja: String,
    val gender: String,
    val preBirth: Boolean,
    val popularOnly: Boolean,
    val year: String,
    val month: String,
    val day: String,
    val hour: String,
    val minute: String,
    val isLunar: Boolean,
    val isLeapMonth: Boolean,
    val unknownTime: Boolean,
    /** 광주(광역시/경기도)처럼 이름이 겹치는 도시가 있어 지역명까지 저장한다 */
    val cityName: String,
    val cityRegion: String,
    val school: String,
    val singleName: Boolean = false,
    val dolimja: String = "",
    val dolimjaLast: Boolean = false,
    val excludedSyllables: String = "",
    val diverseHanja: Boolean = true,
    val mode: String = AppMode.RECOMMEND.name,
    val givenName: String = "",
    /** Empty slots remain empty, rather than shifting the following Hanja into the wrong syllable. */
    val givenHanja: String = "",
    val savedAtEpochMillis: Long = 0,
)

private val Context.inputStore by preferencesDataStore(name = "last-input")

private val SURNAME = stringPreferencesKey("surname")
private val SURNAME_HANJA = stringPreferencesKey("surnameHanja")
private val GENDER = stringPreferencesKey("gender")
private val PRE_BIRTH = booleanPreferencesKey("preBirth")
private val POPULAR_ONLY = booleanPreferencesKey("popularOnly")
private val YEAR = stringPreferencesKey("year")
private val MONTH = stringPreferencesKey("month")
private val DAY = stringPreferencesKey("day")
private val HOUR = stringPreferencesKey("hour")
private val MINUTE = stringPreferencesKey("minute")
private val IS_LUNAR = booleanPreferencesKey("isLunar")
private val IS_LEAP = booleanPreferencesKey("isLeapMonth")
private val UNKNOWN_TIME = booleanPreferencesKey("unknownTime")
private val CITY_NAME = stringPreferencesKey("cityName")
private val CITY_REGION = stringPreferencesKey("cityRegion")
private val SCHOOL = stringPreferencesKey("school")
private val SINGLE_NAME = booleanPreferencesKey("singleName")
private val DOLIMJA = stringPreferencesKey("dolimja")
private val EXCLUDED = stringPreferencesKey("excludedSyllables")
private val DIVERSE = booleanPreferencesKey("diverseHanja")
private val DOLIMJA_LAST = booleanPreferencesKey("dolimjaLast")
private val FORMAT_VERSION = intPreferencesKey("formatVersion")
private val MODE = stringPreferencesKey("mode")
private val GIVEN_NAME = stringPreferencesKey("givenName")
private val GIVEN_HANJA = stringPreferencesKey("givenHanja")
private val SAVED_AT = longPreferencesKey("savedAtEpochMillis")

class InputStore internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.inputStore)

    /** 저장된 입력. 한 번도 제출한 적 없으면 null. */
    suspend fun load(): SavedInput? {
        val p = dataStore.data.first()
        val surname = p[SURNAME] ?: return null
        require((p[FORMAT_VERSION] ?: 1) in 1..2) { "지원하지 않는 입력 기록 형식입니다" }
        return SavedInput(
            surname = surname,
            surnameHanja = p[SURNAME_HANJA] ?: "",
            gender = p[GENDER] ?: "M",
            preBirth = p[PRE_BIRTH] ?: false,
            popularOnly = p[POPULAR_ONLY] ?: false,
            year = p[YEAR] ?: "",
            month = p[MONTH] ?: "",
            day = p[DAY] ?: "",
            hour = p[HOUR] ?: "",
            minute = p[MINUTE] ?: "",
            isLunar = p[IS_LUNAR] ?: false,
            isLeapMonth = p[IS_LEAP] ?: false,
            unknownTime = p[UNKNOWN_TIME] ?: false,
            cityName = p[CITY_NAME] ?: "",
            cityRegion = p[CITY_REGION] ?: "",
            school = p[SCHOOL] ?: "",
            singleName = p[SINGLE_NAME] ?: false,
            dolimja = p[DOLIMJA] ?: "",
            dolimjaLast = p[DOLIMJA_LAST] ?: false,
            excludedSyllables = p[EXCLUDED] ?: "",
            diverseHanja = p[DIVERSE] ?: true,
            mode = p[MODE] ?: AppMode.RECOMMEND.name,
            givenName = p[GIVEN_NAME] ?: "",
            givenHanja = p[GIVEN_HANJA] ?: "",
            savedAtEpochMillis = p[SAVED_AT] ?: 0,
        )
    }

    suspend fun save(s: SavedInput) {
        dataStore.edit { p ->
            p[FORMAT_VERSION] = 2
            p[SURNAME] = s.surname
            p[SURNAME_HANJA] = s.surnameHanja
            p[GENDER] = s.gender
            p[PRE_BIRTH] = s.preBirth
            p[POPULAR_ONLY] = s.popularOnly
            p[YEAR] = s.year
            p[MONTH] = s.month
            p[DAY] = s.day
            p[HOUR] = s.hour
            p[MINUTE] = s.minute
            p[IS_LUNAR] = s.isLunar
            p[IS_LEAP] = s.isLeapMonth
            p[UNKNOWN_TIME] = s.unknownTime
            p[CITY_NAME] = s.cityName
            p[CITY_REGION] = s.cityRegion
            p[SCHOOL] = s.school
            p[SINGLE_NAME] = s.singleName
            p[DOLIMJA] = s.dolimja
            p[DOLIMJA_LAST] = s.dolimjaLast
            p[EXCLUDED] = s.excludedSyllables
            p[DIVERSE] = s.diverseHanja
            p[MODE] = s.mode
            p[GIVEN_NAME] = s.givenName
            p[GIVEN_HANJA] = s.givenHanja
            p[SAVED_AT] = s.savedAtEpochMillis
        }
    }
}
