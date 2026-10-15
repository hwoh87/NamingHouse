package com.naminghouse.app

import com.naminghouse.engine.oheng.BaleumSchool
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import com.samramanshang.manseryeok.orrery.model.JasiMethod
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

/** First versioned baseline of the existing rules; changing scoring rules requires a new identifier. */
object EvaluationVersions {
    const val RULES = "rules-v1"
}

/** Immutable provenance of an actual calculation, never reconstructed from later edited fields. */
data class EvaluationRecord(
    val school: BaleumSchool,
    val evaluatedAtEpochMillis: Long,
    val rulesVersion: String,
    val hanjaDataVersion: String,
    val birth: BirthInput? = null,
    val birthPlace: String = "",
    /** Distinguishes a private export from an evaluation that deliberately did not use birth data. */
    val birthInfoOmitted: Boolean = false,
) {
    init {
        require(evaluatedAtEpochMillis in 0..253402300799999L)
        require(rulesVersion.matches(VERSION) && hanjaDataVersion.matches(VERSION))
        require(birthPlace.length <= 80 && birthPlace.none(Char::isISOControl))
        require(birth == null || !birthInfoOmitted)
        require(birth != null || birthPlace.isEmpty())
        birth?.let {
            require(isValidBirthDate(it.year, it.month, it.day, it.isLunar, it.isLeapMonth))
            require(it.isLunar || !it.isLeapMonth)
            require(it.hour in 0..23 && it.minute in 0..59)
            require(it.latitude.isFinite() && it.latitude in -90.0..90.0)
            require(it.longitude.isFinite() && it.longitude in -180.0..180.0)
            val timezone = it.timezone
            require(timezone == null || (timezone.length <= 80 && runCatching { ZoneId.of(timezone) }.isSuccess))
        }
    }

    val provenanceLabel: String
        get() = "${school.label} · 평가 기준 $rulesVersion · 한자 자료 ${hanjaDataVersion.take(12)}"

    val summary: String
        get() {
            val date = Instant.ofEpochMilli(evaluatedAtEpochMillis).atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
            return "$date · ${school.label}"
        }

    val birthLine: String?
        get() = birth?.let {
            val calendar = if (!it.isLunar) "양력" else if (it.isLeapMonth) "음력(윤달)" else "음력"
            val time = if (it.unknownTime) "시간 모름" else "${it.hour}시 ${it.minute.toString().padStart(2, '0')}분"
            val gender = if (it.gender == Gender.M) "남아" else "여아"
            "$gender · $calendar ${it.year}년 ${it.month}월 ${it.day}일 $time" +
                if (birthPlace.isBlank()) "" else " · $birthPlace"
        }

    val conditionLabel: String
        get() = "${birthLine ?: if (birthInfoOmitted) "출생 정보 미포함" else "사주 없이 · 성명학 기준"} · ${school.label}"

    fun withoutBirthDetails(): EvaluationRecord =
        if (birth == null) this else copy(birth = null, birthPlace = "", birthInfoOmitted = true)

    fun serialize(): String {
        val fields = mutableListOf("1", evaluatedAtEpochMillis.toString(), school.name,
            rulesVersion, hanjaDataVersion, birthInfoOmitted.toString())
        birth?.let {
            fields += listOf(it.year.toString(), it.month.toString(), it.day.toString(),
                it.hour.toString(), it.minute.toString(), it.gender.name, it.unknownTime.toString(),
                it.jasiMethod.name, it.latitude.toString(), it.longitude.toString(), it.timezone.orEmpty(),
                it.isLunar.toString(), it.isLeapMonth.toString(), it.useTrueSolarTime.toString(), birthPlace)
        }
        return fields.joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
    }

    companion object {
        private val VERSION = Regex("[a-zA-Z0-9._-]{1,80}")

        fun parse(serialized: String): EvaluationRecord? = runCatching {
            require(serialized.length <= 4096)
            val c = serialized.split('\t').map { URLDecoder.decode(it, "UTF-8") }
            require(c.size == 6 || c.size == 21)
            require(c[0] == "1")
            val birth = if (c.size == 6) null else BirthInput(
                year = c[6].toInt(), month = c[7].toInt(), day = c[8].toInt(),
                hour = c[9].toInt(), minute = c[10].toInt(), gender = Gender.valueOf(c[11]),
                unknownTime = c[12].toBooleanStrict(), jasiMethod = JasiMethod.valueOf(c[13]),
                latitude = c[14].toDouble(), longitude = c[15].toDouble(), timezone = c[16].ifEmpty { null },
                isLunar = c[17].toBooleanStrict(), isLeapMonth = c[18].toBooleanStrict(),
                useTrueSolarTime = c[19].toBooleanStrict(),
            )
            EvaluationRecord(BaleumSchool.valueOf(c[2]), c[1].toLong(), c[3], c[4], birth,
                c.getOrNull(20).orEmpty(), c[5].toBooleanStrict())
        }.getOrNull()
    }
}
