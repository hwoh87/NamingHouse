package com.naminghouse.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.naminghouse.app.R

// ─────────────────────────────────────────────────────────────────────────────
// 번들 활자
//
// 시스템 serif 는 기기마다 다른 글꼴로 떨어진다(픽셀은 명조, 삼성은 고딕 폴백).
// 이름이 상품인 앱이라 활자를 운에 맡기지 않고 세 벌을 직접 싣는다.
//  · 마루부리 — 한글 명조(이름·제목). KS 완성형 2,350자 + 앱 등장 음절로 서브셋.
//  · 한자 명조 — Noto Serif KR 600 고정. 인명용 한자 전체로 서브셋한 큰 글자용.
//  · 프리텐다드 — 본문·라벨 고딕. 같은 한글 범위로 서브셋.
// 서브셋 밖의 드문 글자는 글리프 단위로 시스템 글꼴에 떨어질 뿐, 빈 네모는 없다.
// ─────────────────────────────────────────────────────────────────────────────

val MaruBuri = FontFamily(
    Font(R.font.maruburi_regular, FontWeight.Normal),
    Font(R.font.maruburi_bold, FontWeight.Bold),
)

/**
 * 한자 큰 글자용 명조. 한 웨이트(600)를 모든 굵기에 물려서 합성 볼드가 끼어들지
 * 않게 한다 — 한자는 획이 많아 600 하나가 한글 Regular/Bold 어느 쪽 옆에서도 어울린다.
 */
val HanjaFamily = FontFamily(
    Font(R.font.hanja_serif, FontWeight.Normal),
    Font(R.font.hanja_serif, FontWeight.Medium),
    Font(R.font.hanja_serif, FontWeight.SemiBold),
    Font(R.font.hanja_serif, FontWeight.Bold),
)

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** 한자가 섞인 짧은 라벨(오행 구슬 등)에 한자 명조를 골라 주는 도우미. */
fun hanjaAwareFamily(text: String): FontFamily? =
    if (text.any { it.code in 0x3400..0x9FFF || it.code in 0xF900..0xFAFF }) HanjaFamily else null

val InkTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = MaruBuri, letterSpacing = (-0.5).sp),
        displayMedium = displayMedium.copy(fontFamily = MaruBuri, letterSpacing = (-0.5).sp),
        displaySmall = displaySmall.copy(fontFamily = MaruBuri, letterSpacing = (-0.4).sp),
        headlineLarge = headlineLarge.copy(fontFamily = MaruBuri, letterSpacing = (-0.4).sp),
        headlineMedium = headlineMedium.copy(fontFamily = MaruBuri, letterSpacing = (-0.3).sp),
        headlineSmall = headlineSmall.copy(fontFamily = MaruBuri, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontFamily = MaruBuri, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontFamily = MaruBuri),
        titleSmall = titleSmall.copy(fontFamily = MaruBuri),
        bodyLarge = bodyLarge.copy(fontFamily = Pretendard, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Pretendard, lineHeight = 21.sp),
        bodySmall = bodySmall.copy(fontFamily = Pretendard, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontFamily = Pretendard),
        labelMedium = labelMedium.copy(fontFamily = Pretendard),
        labelSmall = labelSmall.copy(fontFamily = Pretendard),
    )
}
