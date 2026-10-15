package com.naminghouse.app

import com.naminghouse.engine.oheng.BaleumSchool
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import com.samramanshang.manseryeok.orrery.model.JasiMethod
import org.junit.Assert.*
import org.junit.Test

class EvaluationRecordTest {
    private val birth = BirthInput(2026, 8, 11, 23, 40, Gender.F,
        jasiMethod = JasiMethod.SPLIT, latitude = 35.1595, longitude = 126.8526,
        timezone = "Asia/Seoul", useTrueSolarTime = true)
    private val record = EvaluationRecord(BaleumSchool.HAERYE, 1792090800000,
        EvaluationVersions.RULES, "024d4f2c1fabc8e0", birth, "광주 + 전남 %")

    @Test fun recordRetainsEveryCalculationCondition() {
        assertEquals(record, EvaluationRecord.parse(record.serialize()))
        assertEquals(birth, EvaluationRecord.parse(record.serialize())!!.birth)
        assertTrue(record.conditionLabel.contains("23시 40분"))
        assertTrue(record.provenanceLabel.contains(BaleumSchool.HAERYE.label))
    }

    @Test fun exportRedactsAllBirthFieldsWithoutPretendingTheEvaluationHadNoBirthData() {
        val redacted = record.withoutBirthDetails()
        assertNull(redacted.birth)
        assertEquals("", redacted.birthPlace)
        assertTrue(redacted.birthInfoOmitted)
        assertTrue(redacted.conditionLabel.contains("출생 정보 미포함"))
        assertFalse(redacted.serialize().contains("광주"))
        assertFalse(redacted.serialize().contains("126.8526"))
        assertEquals(record.provenanceLabel, redacted.provenanceLabel)
        assertEquals(redacted, EvaluationRecord.parse(redacted.serialize()))
        assertEquals(redacted, redacted.withoutBirthDetails())
    }

    @Test fun noBirthEvaluationAndRedactedBirthEvaluationStayDifferent() {
        val noBirth = record.copy(birth = null, birthPlace = "")
        assertFalse(noBirth.withoutBirthDetails().birthInfoOmitted)
        assertTrue(noBirth.conditionLabel.contains("사주 없이"))
        assertNotEquals(noBirth, record.withoutBirthDetails())
    }

    @Test fun unknownVersionInvalidDatesAndInjectedFieldsFailClosed() {
        val encoded = record.serialize()
        assertNull(EvaluationRecord.parse(encoded.replaceFirst("1\t", "2\t")))
        assertNull(EvaluationRecord.parse(encoded + "\textra"))
        assertNull(EvaluationRecord.parse("%not-a-valid-escape"))
        val fields = encoded.split('\t').toMutableList()
        fields[7] = "2"; fields[8] = "31"
        assertNull(EvaluationRecord.parse(fields.joinToString("\t")))
        fields[7] = "8"; fields[8] = "11"; fields[14] = "NaN"
        assertNull(EvaluationRecord.parse(fields.joinToString("\t")))
        assertNull(EvaluationRecord.parse("a".repeat(4097)))
    }
}
