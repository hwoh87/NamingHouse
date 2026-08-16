package com.naminghouse.app

import androidx.compose.ui.graphics.toArgb
import com.naminghouse.app.ui.BOTONG
import com.naminghouse.app.ui.FEMALE
import com.naminghouse.app.ui.GIL
import com.naminghouse.app.ui.GOLD
import com.naminghouse.app.ui.HYUNG
import com.naminghouse.app.ui.INK
import com.naminghouse.app.ui.MALE
import com.naminghouse.app.ui.MUTED
import com.naminghouse.app.ui.ON_SEAL
import com.naminghouse.app.ui.PAPER
import com.naminghouse.app.ui.RULE
import com.naminghouse.app.ui.SEAL
import com.naminghouse.app.ui.theme.LightInk
import com.samramanshang.manseryeok.orrery.model.Element
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 감명서 PDF 의 인쇄용 색을 정확한 ARGB 로 못 박는다.
 *
 * 색 상수를 손으로 베낀 리터럴에서 팔레트 참조로 바꿀 때, 산출물이 한 픽셀도
 * 달라지지 않았음을 보이려고 넣었다. 아래 숫자는 **바꾸기 전 파일에 있던 값
 * 그대로**다 — 팔레트를 고쳐서 이 테스트가 깨지면, 그건 실패가 아니라
 * "유료 감명서 PDF 의 색도 같이 바뀐다"는 알림이다. 의도한 변경이면 숫자를
 * 갱신하고, 인쇄 대비를 한 번 확인하고 넘어갈 것.
 */
class CertificatePdfColorTest {

    @Test
    fun `인쇄용 색은 라이트 팔레트 값과 정확히 일치한다`() {
        assertEquals(0xFF241F19.toInt(), INK)        // BrandLight.onSurface
        assertEquals(0xFF67604F.toInt(), MUTED)      // BrandLight.onSurfaceVariant
        assertEquals(0xFFD5CAB2.toInt(), RULE)       // BrandLight.outlineVariant
        assertEquals(0xFFA5873F.toInt(), GOLD)       // LightInk.gold
        assertEquals(0xFF9C3A2E.toInt(), SEAL)       // LightInk.seal
        assertEquals(0xFFFBF3E7.toInt(), ON_SEAL)    // LightInk.onSeal
        assertEquals(0xFF2F6B4A.toInt(), GIL)        // LightInk.gil
        assertEquals(0xFF876A22.toInt(), BOTONG)     // LightInk.botong
        assertEquals(0xFF9E3A2F.toInt(), HYUNG)      // LightInk.hyung
        assertEquals(0xFF3D6CB0.toInt(), MALE)       // LightInk.male
        assertEquals(0xFFB1567A.toInt(), FEMALE)     // LightInk.female
    }

    @Test
    fun `본문 종이색은 팔레트에 대응이 없어 고정값이다`() {
        assertEquals(0xFFFDFAF2.toInt(), PAPER)
    }

    @Test
    fun `오행 다섯 색도 팔레트에서 온다`() {
        val expected = mapOf(
            Element.TREE to 0xFF3F7A55.toInt(),
            Element.FIRE to 0xFFB0463A.toInt(),
            Element.EARTH to 0xFF9E7C33.toInt(),
            Element.METAL to 0xFF6F7681.toInt(),
            Element.WATER to 0xFF3A5F8A.toInt(),
        )
        expected.forEach { (element, argb) ->
            assertEquals("$element", argb, LightInk.of(element).toArgb())
        }
    }
}
