package com.naminghouse.engine

import com.naminghouse.engine.oheng.ArrangementQuality
import com.naminghouse.engine.oheng.BaleumOheng
import com.naminghouse.engine.oheng.SuriOheng
import com.naminghouse.engine.oheng.arrangementQualityOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 오행 배열 판정 규칙 — 발음오행·수리오행 공용.
 *
 * 작명왕 실측 두 건이 "비화가 섞여도 상극만 없으면 최고 등급"임을 보여준다.
 * 그 기준을 고정한다.
 */
class OhengArrangementTest {

    private fun suriQuality(vararg strokes: Int) =
        arrangementQualityOf(SuriOheng.evaluate(strokes.toList()).relations)

    private fun baleumQuality(name: String) =
        arrangementQualityOf(BaleumOheng.evaluate(name)!!.relations)

    @Test
    fun `연속 상생은 길`() {
        // 작명왕 실측: 金(8) 水(10) 木(21) → "매우 좋아요"
        assertEquals(ArrangementQuality.SANGSAENG, suriQuality(8, 10, 21))
    }

    @Test
    fun `작명왕 실측 - 木火火(생+비화)도 최고 등급`() {
        // 최시윤 崔11(木) ○14(火) 允4(火). 작명왕은 "매우 좋아요" + "상생 관계" 로 서술했다.
        assertEquals(ArrangementQuality.SANGSAENG, suriQuality(11, 14, 4))
    }

    @Test
    fun `작명왕 실측 - 金金土(비화+역생)도 최고 등급`() {
        // 최시윤 발음오행 金(최ㅊ) 金(시ㅅ) 土(윤ㅇ). 역생이라도 상극이 아니면 길.
        assertEquals(ArrangementQuality.SANGSAENG, baleumQuality("최시윤"))
    }

    @Test
    fun `상극이 하나라도 있으면 흉`() {
        // 8(金) - 4(火): 火剋金
        assertEquals(ArrangementQuality.SANGGEUK, suriQuality(8, 4, 11))
        // 김(木) - 선(金): 金剋木
        assertEquals(ArrangementQuality.SANGGEUK, baleumQuality("김선우"))
    }

    @Test
    fun `순수 비화는 보통 - 유파별 이견이 커 길로 올리지 않는다`() {
        // 8(金) 18(金) 28(金) — 전부 같은 오행
        assertEquals(ArrangementQuality.BIHWA_ONLY, suriQuality(8, 18, 28))
    }

    @Test
    fun `관계가 없으면(한 글자) 비화로 본다`() {
        assertEquals(ArrangementQuality.BIHWA_ONLY, arrangementQualityOf(emptyList()))
    }
}
