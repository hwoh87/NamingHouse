package com.naminghouse.app

import com.naminghouse.app.ui.hasComparableGenderCounts
import com.naminghouse.app.ui.sampleShareLabel
import com.naminghouse.engine.gen.NameStat
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.*
import org.junit.Test

class NameStatPresentationTest {
    @Test fun aMissingGenderSampleDoesNotProduceAnExclusiveGenderClaim() {
        assertFalse(hasComparableGenderCounts(NameStat("가온", Gender.M, 10, 0, emptyList())))
        assertFalse(hasComparableGenderCounts(NameStat("가온", Gender.F, 0, 10, emptyList())))
        assertFalse(hasComparableGenderCounts(NameStat("가온", Gender.M, 0, 0, emptyList())))
        assertTrue(hasComparableGenderCounts(NameStat("가온", Gender.M, 10, 2, emptyList())))
    }

    @Test fun roundingNeverHidesAPositiveMinorityShare() {
        assertEquals("1% 미만", sampleShareLabel(1, 1000))
        assertEquals("99% 초과", sampleShareLabel(999, 1000))
        assertEquals("약 50%", sampleShareLabel(5, 10))
        assertEquals("자료 없음", sampleShareLabel(0, 0))
    }
}
