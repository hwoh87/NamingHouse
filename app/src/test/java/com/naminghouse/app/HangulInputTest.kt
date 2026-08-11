package com.naminghouse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 한글 IME 조합 입력 처리.
 *
 * 회귀 방지: 완성형(가~힣)만 통과시키면 초성을 누르는 순간 빈 문자열이 되어
 * TextField 값이 되돌아가고 IME 조합이 깨진다 — 성씨·이름을 아예 못 치게 된다.
 */
class HangulInputTest {

    @Test
    fun `조합 중 낱자를 통과시킨다`() {
        // '이'를 치는 과정: ㅇ → 이
        assertEquals("ㅇ", acceptHangul("ㅇ", 2))
        assertEquals("이", acceptHangul("이", 2))
        // 두 글자 성씨 '남궁': 남 → 남ㄱ → 남구 → 남궁
        assertEquals("남", acceptHangul("남", 2))
        assertEquals("남ㄱ", acceptHangul("남ㄱ", 2))
        assertEquals("남구", acceptHangul("남구", 2))
        assertEquals("남궁", acceptHangul("남궁", 2))
    }

    @Test
    fun `조합용 자모도 통과시킨다`() {
        // 일부 IME 는 호환자모(U+3131) 대신 조합용 자모(U+1100)를 보낸다
        val conjoining = "ᄀ" // ᄀ
        assertEquals(conjoining, acceptHangul(conjoining, 2))
    }

    @Test
    fun `한글이 아닌 문자는 버린다`() {
        assertEquals("", acceptHangul("abc", 2))
        assertEquals("", acceptHangul("123", 2))
        assertEquals("김", acceptHangul("kim김", 2))
        assertEquals("", acceptHangul("🙂", 2))
    }

    @Test
    fun `완성 음절 기준으로 글자 수를 제한한다`() {
        assertEquals("남궁", acceptHangul("남궁수", 2))
        assertEquals("김", acceptHangul("김", 1))
        assertEquals("김", acceptHangul("김수", 1))
        // 정원을 채웠으면 조합 중 낱자도 더 받지 않는다
        assertEquals("남궁", acceptHangul("남궁ㅅ", 2))
    }

    @Test
    fun `이름은 세 글자까지`() {
        assertEquals("다현우", acceptHangul("다현우", 3))
        assertEquals("다현우", acceptHangul("다현우진", 3))
        assertEquals("다현ㅇ", acceptHangul("다현ㅇ", 3))
    }

    @Test
    fun `빈 입력과 전체 삭제`() {
        assertEquals("", acceptHangul("", 2))
    }

    @Test
    fun `음절과 낱자 판별`() {
        assertTrue(isHangulSyllable('김'))
        assertTrue(isHangulSyllable('가'))
        assertTrue(isHangulSyllable('힣'))
        assertFalse(isHangulSyllable('ㄱ'))
        assertFalse(isHangulSyllable('a'))

        assertTrue(isHangulJamo('ㄱ'))
        assertTrue(isHangulJamo('ㅏ'))
        assertTrue(isHangulJamo('ᄀ'))
        assertFalse(isHangulJamo('김'))
    }
}
