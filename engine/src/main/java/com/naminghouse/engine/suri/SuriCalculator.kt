package com.naminghouse.engine.suri

/** 원형이정 4격 — 각 격의 수리와 길흉 */
data class SuriGyeok(
    val won: SuriMeaning,    // 원격(元) — 초년운
    val hyeong: SuriMeaning, // 형격(亨) — 청년운(인격)
    val i: SuriMeaning,      // 이격(利) — 중년운(외격)
    val jeong: SuriMeaning,  // 정격(貞) — 총운
) {
    val all: List<SuriMeaning> get() = listOf(won, hyeong, i, jeong)
    val goodCount: Int get() = all.count { it.grade.isGood }
    val allGood: Boolean get() = goodCount == 4
}

/**
 * 수리성명학 원형이정(元亨利貞) 4격 계산 — 한국식(허수 없음), 원획 기준.
 *
 * 기본형(성1+이름2): 원=이름1+이름2, 형=성+이름1, 이=성+이름2, 정=총획.
 * 외자·복성·이름3자는 통용 변형을 따르며, 유파 차가 있어 참고 표기를 권장한다.
 */
object SuriCalculator {

    /**
     * @param surnameStrokes 성 각 글자의 원획 (1~2자)
     * @param givenStrokes 이름 각 글자의 원획 (1~3자)
     */
    fun calculate(surnameStrokes: List<Int>, givenStrokes: List<Int>): SuriGyeok {
        require(surnameStrokes.isNotEmpty() && surnameStrokes.size <= 2) { "성은 1~2자" }
        require(givenStrokes.isNotEmpty() && givenStrokes.size <= 3) { "이름은 1~3자" }
        val s = surnameStrokes.sum()
        val total = s + givenStrokes.sum()

        val (won, hyeong, i, jeong) = when (givenStrokes.size) {
            1 -> {
                // 외자: 원=이름 단독, 이=성 단독, 정=총획(=형격과 동일값)
                val n = givenStrokes[0]
                listOf(n, s + n, s, total)
            }
            2 -> {
                val (n1, n2) = givenStrokes
                listOf(n1 + n2, s + n1, s + n2, total)
            }
            else -> {
                // 이름 3자: 원=이름 전체 합, 형=성+첫자, 이=성+끝자
                val n1 = givenStrokes.first()
                val n3 = givenStrokes.last()
                listOf(givenStrokes.sum(), s + n1, s + n3, total)
            }
        }

        return SuriGyeok(
            won = Suri81.of(won),
            hyeong = Suri81.of(hyeong),
            i = Suri81.of(i),
            jeong = Suri81.of(jeong),
        )
    }
}
