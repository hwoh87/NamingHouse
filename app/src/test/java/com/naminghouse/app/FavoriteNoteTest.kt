package com.naminghouse.app

import org.junit.Assert.*
import org.junit.Test

class FavoriteNoteTest {
    @Test fun oldFavoritesRemainReadable() {
        val old = FavoriteName.parse("김\t도윤\t金度侖\t94\t대길\t이름의 뜻")!!
        assertEquals("", old.familyNote)
        assertFalse(old.isFinalist)
        assertEquals("김도윤", old.fullHangul)
    }
    @Test fun familyNoteRoundTripsAndAppearsInSharing() {
        val note = "따뜻하게\n자라길\t바라 + % & 🌷"
        val item = FavoriteName("김", "도윤", "金度侖", 94, "대길", "이름의 뜻", note, true)
        assertEquals(item, FavoriteName.parse(item.serialize()))
        assertFalse(shareText(listOf(item)).contains(note))
        assertTrue(shareText(listOf(item), includeNotes = true).contains(note))
        assertTrue(shareText(listOf(item)).contains("최종 후보"))
        assertEquals(9, item.serialize().split('\t').size)
    }

    @Test fun sevenFieldFavoritesKeepTheirNoteWithoutBecomingFinalists() {
        val old = FavoriteName.parse("김\t도윤\t金度侖\t94\t대길\t이름의 뜻\t%EA%B0%80%EC%A1%B1+%EB%A9%94%EB%AA%A8")!!
        assertEquals("가족 메모", old.familyNote)
        assertFalse(old.isFinalist)
        assertEquals(old, FavoriteName.parse(old.serialize()))
        assertFalse(shareText(listOf(old)).contains("최종 후보"))
    }

    @Test fun invalidOptionalFieldsDoNotHideAnOtherwiseValidName() {
        val item = FavoriteName.parse("김\t도윤\t金度侖\t94\t대길\t이름의 뜻\t%invalid\tunknown")!!
        assertEquals("", item.familyNote)
        assertFalse(item.isFinalist)
        assertNull(FavoriteName.parse("김\t도윤"))
    }

    @Test fun eightFieldFavoritesKeepFinalistStatusWithoutInventingAnEvaluationRecord() {
        val old = FavoriteName.parse("김\t도윤\t金度侖\t94\t대길\t이름의 뜻\t\ttrue")!!
        assertTrue(old.isFinalist)
        assertNull(old.evaluationRecord)
        assertEquals(old, FavoriteName.parse(old.serialize()))
    }

    @Test fun keyPreservesNameBoundariesAndExcludesMutableFields() {
        val first = FavoriteName("남궁", "민", "南宮珉", 94, "대길", "뜻")
        val sameFullName = first.copy(surname = "남", givenName = "궁민")
        assertEquals(first.fullHangul, sameFullName.fullHangul)
        assertNotEquals(first.key, sameFullName.key)
        assertNotEquals(first.key, first.copy(hanja = "南宮旼").key)
        assertEquals(first.key, first.copy(score = 80, grade = "길", meaning = "다른 뜻", familyNote = "메모", isFinalist = true).key)
    }

    @Test fun searchIncludesNamesMeaningsAndNotesAndKeepsInputOrder() {
        val first = FavoriteName("김", "도윤", "金度侖", 85, "길", "너그러운 사람", "봄에 만날 아이", true)
        val second = FavoriteName("이", "서윤", "李瑞潤", 97, "대길", "빛나는 사람", "Spring baby")
        val items = listOf(first, second)
        assertEquals(listOf(first), favoritesVisible(items, " 김도 ", false))
        assertEquals(listOf(second), favoritesVisible(items, "瑞", false))
        assertEquals(items, favoritesVisible(items, "사람", false))
        assertEquals(listOf(first), favoritesVisible(items, "봄", false))
        assertEquals(listOf(second), favoritesVisible(items, "spring", false))
        assertEquals(items, favoritesVisible(items, "  ", false))
        assertEquals(listOf(first), favoritesVisible(items, "사람", true))
        assertTrue(favoritesVisible(items, "빛나는", true).isEmpty())
        assertTrue(favoritesVisible(emptyList(), "", true).isEmpty())
    }
}
