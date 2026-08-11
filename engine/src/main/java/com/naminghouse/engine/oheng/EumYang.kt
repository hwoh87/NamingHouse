package com.naminghouse.engine.oheng

/** 음양 배열 평가 결과 */
data class EumYangResult(
    /** 앞에서부터 각 글자: true=양(홀수 획), false=음(짝수 획) */
    val pattern: List<Boolean>,
    /** 순양·순음이 아니면 조화로 본다 */
    val isBalanced: Boolean,
) {
    val display: String get() = pattern.joinToString("") { if (it) "○" else "●" }
}

object EumYang {

    /** 수리음양: 원획 홀수=양, 짝수=음. 전 글자 동일 음양(순양·순음)만 흉. */
    fun ofStrokes(strokes: List<Int>): EumYangResult {
        val pattern = strokes.map { it % 2 == 1 }
        return EumYangResult(pattern, pattern.distinct().size > 1)
    }

    private val YANG_VOWELS = setOf('ㅏ', 'ㅑ', 'ㅗ', 'ㅛ', 'ㅐ', 'ㅒ', 'ㅘ', 'ㅙ', 'ㅚ')
    private val NEUTRAL_VOWELS = setOf('ㅣ')

    private val JUNGSEONG = listOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
        'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ',
    )

    /**
     * 발음음양: 모음의 양성(ㅏㅗ 계열)·음성(ㅓㅜㅡ 계열) 기준.
     * 중성모음 ㅣ는 판정에서 제외하고, 나머지가 한쪽으로만 쏠리면 흉으로 본다.
     */
    fun ofSound(fullName: String): EumYangResult? {
        val pattern = fullName.map { ch ->
            val code = ch.code - 0xAC00
            if (code < 0 || code >= 11172) return null
            JUNGSEONG[(code % 588) / 28]
        }.map { vowel -> if (vowel in NEUTRAL_VOWELS) null else vowel in YANG_VOWELS }

        val decided = pattern.filterNotNull()
        val balanced = decided.isEmpty() || decided.distinct().size > 1
        // 중성(ㅣ)은 표시 편의상 양으로 취급하되, 판정은 위 decided 만 사용
        return EumYangResult(pattern.map { it ?: true }, balanced)
    }
}
