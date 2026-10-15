package com.naminghouse.app

import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.oheng.BaleumSchool
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FavoriteComparisonTest {
    private val db by lazy {
        listOf(File("../engine/src/main/assets/hanja.tsv"), File("engine/src/main/assets/hanja.tsv"))
            .first(File::exists).bufferedReader().useLines(HanjaDb::parse)
    }

    private fun favorite(name: String, storedScore: Int = 1): FavoriteName {
        val hanja = "金" + name.map { db.candidatesFor(it.toString()).first { entry -> entry.usableForNaming }.char }.joinToString("")
        return FavoriteName("김", name, hanja, storedScore, "이전 등급", "저장 당시 뜻", "가족 메모")
    }

    @Test fun `different stored conditions are recalculated with one shared context`() {
        val names = listOf(favorite("하윤", 1), favorite("서준", 99))
        val birth = BirthInput(2026, 8, 11, 12, 0, Gender.M, useTrueSolarTime = true)
        val result = compareFavoriteNames(names, db, birth, BaleumSchool.HAERYE, "같은 조건", "출생 정보")
        assertNotNull(result.saju)
        assertEquals(birth, result.saju!!.input)
        assertEquals(names, result.entries.map { it.favorite })
        result.entries.forEach { entry ->
            val chars = entry.favorite.hanja.map { db.byChar.getValue(it) }
            val expected = NameEvaluator.evaluate("김", entry.favorite.givenName, chars.take(1), chars.drop(1),
                result.saju, BaleumSchool.HAERYE)
            assertEquals(expected, entry.evaluation)
        }
        assertNotEquals(names.first().score, result.entries.first().evaluation.score)
        assertEquals("출생 정보", result.birthLine)
    }

    @Test fun `explicit no saju comparison works without a birth date`() {
        val result = compareFavoriteNames(listOf(favorite("하윤"), favorite("서준")), db, null,
            BaleumSchool.UNHAE, "사주 없이", null)
        assertNull(result.saju)
        assertNull(result.birthLine)
        assertEquals(2, result.entries.size)
    }

    @Test fun `invalid solar and lunar dates never silently become another date`() {
        assertFalse(isValidBirthDate(2026, 2, 31, false, false))
        assertFalse(isValidBirthDate(2026, 2, 29, false, false))
        assertTrue(isValidBirthDate(2024, 2, 29, false, false))
        assertFalse(isValidBirthDate(2026, 1, 1, true, true)) // No leap month that year.
        assertFalse(isValidBirthDate(2025, 6, 31, true, true))
        assertTrue(isValidBirthDate(2025, 6, 1, true, true))
        assertFalse(isValidBirthDate(1899, 12, 31, false, false))
    }

    @Test fun `invalid comparison birth is rejected instead of dropping saju`() {
        val names = listOf(favorite("하윤"), favorite("서준"))
        assertThrows(IllegalArgumentException::class.java) {
            compareFavoriteNames(names, db, BirthInput(2026, 2, 31, 12, 0, Gender.M),
                BaleumSchool.UNHAE, "", null)
        }
    }

    @Test fun `duplicate and out of range selections are rejected`() {
        val first = favorite("하윤")
        for (items in listOf(listOf(first), listOf(first, first), List(4) { favorite("서준") })) {
            assertThrows(IllegalArgumentException::class.java) {
                compareFavoriteNames(items, db, null, BaleumSchool.UNHAE, "", null)
            }
        }
    }

    @Test fun `corrupt saved hanja cannot be evaluated under a different reading`() {
        val name = favorite("하윤")
        assertThrows(IllegalArgumentException::class.java) {
            evaluateFavorite(name.copy(hanja = "金金金"), db, null, BaleumSchool.UNHAE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            evaluateFavorite(name.copy(hanja = "金"), db, null, BaleumSchool.UNHAE)
        }
    }
}
