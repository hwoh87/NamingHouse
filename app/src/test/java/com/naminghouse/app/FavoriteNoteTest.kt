package com.naminghouse.app

import org.junit.Assert.*
import org.junit.Test

class FavoriteNoteTest {
    @Test fun oldFavoritesRemainReadable() {
        val old = FavoriteName.parse("김\t도윤\t金度侖\t94\t대길\t이름의 뜻")!!
        assertEquals("", old.familyNote)
        assertEquals("김도윤", old.fullHangul)
    }
    @Test fun familyNoteRoundTripsAndAppearsInSharing() {
        val note = "따뜻하게\n자라길\t바라 + % & 🌷"
        val item = FavoriteName("김", "도윤", "金度侖", 94, "대길", "이름의 뜻", note)
        assertEquals(item, FavoriteName.parse(item.serialize()))
        assertTrue(shareText(listOf(item)).contains(note))
        assertEquals(7, item.serialize().split('\t').size)
    }
}
