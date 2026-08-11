package com.naminghouse.app

/** 완성형 한글 음절(가~힣) */
fun isHangulSyllable(ch: Char): Boolean = ch.code in 0xAC00..0xD7A3

/**
 * 조합 중인 낱자.
 * 한글 IME 는 음절이 완성되기 전에 낱자를 먼저 보낸다 — 호환 자모(ㄱ·ㅏ, U+3130~318F)와
 * 조합용 자모(U+1100~11FF) 두 가지가 온다.
 */
fun isHangulJamo(ch: Char): Boolean = ch.code in 0x3130..0x318F || ch.code in 0x1100..0x11FF

/**
 * 이름 입력란용 필터.
 *
 * **조합 중인 낱자를 반드시 통과시켜야 한다.** 완성형만 남기면 사용자가 초성을 누르는 순간
 * 빈 문자열이 되어 TextField 값이 되돌아가고, IME 조합 세션이 깨져 한글을 아예 못 친다.
 * (이 앱에서 성씨·이름 입력이 먹통이던 원인이 정확히 그것이었다)
 *
 * 그래서 여기서는 한글이 아닌 문자만 걸러내고, 글자 수는 **완성된 음절** 기준으로 센다.
 * 정원이 남아 있을 때만 조합 중 낱자를 받아들여 초과 입력을 막는다.
 */
fun acceptHangul(raw: String, maxSyllables: Int): String {
    val sb = StringBuilder(raw.length)
    var syllables = 0
    for (ch in raw) {
        when {
            isHangulSyllable(ch) -> {
                if (syllables >= maxSyllables) break
                syllables++
                sb.append(ch)
            }
            isHangulJamo(ch) -> {
                // 마지막 음절을 조합하는 중 — 정원을 넘겼으면 더 받지 않는다
                if (syllables >= maxSyllables) break
                sb.append(ch)
            }
            // 영문·숫자·기호는 버린다
        }
    }
    return sb.toString()
}
