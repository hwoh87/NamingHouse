package com.naminghouse.app

import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.hanja.HanjaDb
import java.text.Normalizer
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class NameDiscoveryTest {
    private val pool = NamePool.parse(sequenceOf(
        "name\tgender\ttier", "서윤\tF\t1", "서연\tF\t1", "선우\tU\t2", "성원\tM\t2",
        "서\tU\t3", "민서\tF\t1", "다서윤\tU\t3", "명수\tM\t3", "예원\tF\t2",
    ))
    private val db = HanjaDb.parse(sequenceOf(
        "hanja\treadings\twonhoek\tpilhoek\telement\tmeaning\tnamefit\tavoid",
        "瑞\t서,예\t14\t13\t金\t상서로울 서\t4\t0",
        "潤\t윤\t16\t15\t水\t윤택할 윤\t4\t0",
        "明\t명\t8\t8\t火\t밝을 명 LIGHT\t4\t0",
        "暑\t서\t13\t12\t火\t더울 서\t4\t1",
        "敍\t서\t11\t11\t金\t펼 서\t1\t0",
    ))
    private val index = NameDiscoveryIndex(pool, db)
    private fun names(query: NameDiscoveryQuery) = index.search(query).map { it.poolName.name }

    @Test fun initialsAndMixedSyllablesMatchAtKoreanSyllableBoundaries() {
        assertEquals(listOf("다서윤", "서연", "서윤", "선우", "성원"), names(NameDiscoveryQuery("ㅅㅇ")))
        assertEquals(listOf("다서윤", "서연", "서윤"), names(NameDiscoveryQuery("서ㅇ")))
        assertTrue(matchesNameSound("서윤", "ᄉᄋ"))
        assertTrue(matchesNameSound("서윤", Normalizer.normalize("서윤", Normalizer.Form.NFD)))
        assertTrue(matchesNameSound("가힣", "ㄱㅎ"))
        assertFalse(matchesNameSound("서윤", "ㅏ"))
        assertFalse(matchesNameSound("서윤", ".*"))
        assertFalse(matchesNameSound("서윤", "서윤아"))
    }

    @Test fun startsEndsAndPartialNamesHaveDifferentBoundaries() {
        assertEquals(listOf("서", "서연", "서윤"), names(NameDiscoveryQuery("서", position = DiscoveryPosition.START)))
        assertEquals(listOf("민서", "서"), names(NameDiscoveryQuery("서", position = DiscoveryPosition.END)))
        assertEquals(listOf("다서윤", "서윤"), names(NameDiscoveryQuery("윤", position = DiscoveryPosition.END)))
        assertTrue(matchesNameSound("민서", "ㅅ", DiscoveryPosition.END))
        assertFalse(matchesNameSound("민서", "ㅅ", DiscoveryPosition.START))
    }

    @Test fun genderAndLengthUseOnlyTheCandidatePoolAndKeepUnisexNames() {
        assertEquals(listOf("서"), names(NameDiscoveryQuery(gender = DiscoveryGender.MALE, length = 1)))
        assertEquals(listOf("서"), names(NameDiscoveryQuery(gender = DiscoveryGender.FEMALE, length = 1)))
        assertEquals(listOf("다서윤"), names(NameDiscoveryQuery(length = 3)))
        assertFalse(names(NameDiscoveryQuery(gender = DiscoveryGender.MALE)).contains("서윤"))
        assertTrue(names(NameDiscoveryQuery(gender = DiscoveryGender.MALE)).contains("선우"))
    }

    @Test fun meaningsExposeTheActualMatchingLetterAndItsReadingIncludingAlternativeReadings() {
        val matches = index.search(NameDiscoveryQuery("상서", mode = DiscoveryMode.MEANING))
        assertTrue(matches.any { it.poolName.name == "예원" })
        assertTrue(matches.any { it.poolName.name == "민서" })
        assertFalse(matches.any { it.poolName.name == "성원" })
        assertTrue(matches.all { it.matchingHanja.map { h -> h.char } == listOf('瑞') })
        assertEquals("상서로울 서", matches.first().matchingHanja.single().meaning)
        assertEquals(listOf("명수"), names(NameDiscoveryQuery("明", mode = DiscoveryMode.MEANING)))
        assertEquals(listOf("명수"), names(NameDiscoveryQuery("light", mode = DiscoveryMode.MEANING)))
    }

    @Test fun noInventedMeaningOrExcludedHanjaIsUsed() {
        assertTrue(names(NameDiscoveryQuery("희망찬 미래", mode = DiscoveryMode.MEANING)).isEmpty())
        assertTrue(names(NameDiscoveryQuery("더울", mode = DiscoveryMode.MEANING)).isEmpty())
        assertTrue(names(NameDiscoveryQuery("펼", mode = DiscoveryMode.MEANING)).isEmpty())
        assertTrue(names(NameDiscoveryQuery("", mode = DiscoveryMode.MEANING)).isEmpty())
    }

    @Test fun directNamesAreNormalizedOnlyAfterCompositionIsComplete() {
        assertEquals("서윤", normalizedDiscoveryName(" 서윤 "))
        assertEquals("서윤", normalizedDiscoveryName(Normalizer.normalize("서윤", Normalizer.Form.NFD)))
        assertEquals("서", normalizedDiscoveryName("ㅅㅓ"))
        listOf("ㅅ", "서ㅇ", "A", "", "서 윤", "하늘가람").forEach { assertNull(it, normalizedDiscoveryName(it)) }
    }

    @Test fun cancelledSearchDoesNotReturnPartialResults() {
        var checks = 0
        try {
            index.search(NameDiscoveryQuery("서")) {
                if (++checks == 3) throw CancellationException("new query")
            }
            fail("Cancelled search published results")
        } catch (_: CancellationException) { assertEquals(3, checks) }
    }
}
