package com.naminghouse.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.samramanshang.manseryeok.orrery.model.Element

// 한지 느낌의 웜 라이트 + 차분한 다크. 삼라 패턴(브랜드 고정 스킴, 다이나믹 컬러 미사용)을 따른다.
private val Navy = Color(0xFF1F3A5F)
private val Gold = Color(0xFFB98A2F)

private val BrandLight = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2F1),
    onPrimaryContainer = Color(0xFF0E2340),
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3C2),
    onSecondaryContainer = Color(0xFF4A3608),
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF241F18),
    surface = Color(0xFFFBF7EF),
    onSurface = Color(0xFF241F18),
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF54503F),
    outline = Color(0xFF9A917D),
    surfaceContainer = Color(0xFFF4EEE1),
    surfaceContainerHigh = Color(0xFFEFE7D7),
    surfaceContainerHighest = Color(0xFFE9E0CE),
    error = Color(0xFFA83C32),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFAAC3E8),
    onPrimary = Color(0xFF102540),
    primaryContainer = Color(0xFF2D4A73),
    onPrimaryContainer = Color(0xFFD9E2F1),
    secondary = Color(0xFFE0BE72),
    onSecondary = Color(0xFF3C2E06),
    secondaryContainer = Color(0xFF57431A),
    onSecondaryContainer = Color(0xFFF3E3C2),
    background = Color(0xFF171613),
    onBackground = Color(0xFFE8E2D6),
    surface = Color(0xFF171613),
    onSurface = Color(0xFFE8E2D6),
    surfaceVariant = Color(0xFF2A2822),
    onSurfaceVariant = Color(0xFFCBC4B2),
    outline = Color(0xFF8F8875),
    surfaceContainer = Color(0xFF1F1E1A),
    surfaceContainerHigh = Color(0xFF262420),
    surfaceContainerHighest = Color(0xFF2D2B25),
    error = Color(0xFFE8998F),
)

/** 오행 도메인 컬러 (라이트/다크 공용으로 무난한 톤) */
object WuxingColors {
    fun of(element: Element): Color = when (element) {
        Element.TREE -> Color(0xFF3E8E5A)
        Element.FIRE -> Color(0xFFC64B3E)
        Element.EARTH -> Color(0xFFB9862F)
        Element.METAL -> Color(0xFF8A8D93)
        Element.WATER -> Color(0xFF3D6CB0)
    }
}

@Composable
fun NamingHouseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) BrandDark else BrandLight

    // 시스템 다크모드 플래그가 아니라 **앱 배경의 밝기**로 시스템 바 아이콘 대비를 정한다.
    // 이걸 안 하면 다크 모드에서 흰 아이콘이 밝은 배경 위에 그려져 시계가 안 보인다.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val light = colorScheme.background.luminance() > 0.5f
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
