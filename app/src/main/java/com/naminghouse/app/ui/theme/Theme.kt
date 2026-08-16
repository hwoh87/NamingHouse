package com.naminghouse.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.naminghouse.app.ThemeMode
import com.samramanshang.manseryeok.orrery.model.Element

// ─────────────────────────────────────────────────────────────────────────────
// 수묵(水墨) 팔레트
//
// 한지 위에 먹으로 쓴 화면을 기준으로 삼는다. 강조는 원색이 아니라 먹의 농담으로 주고,
// 붉은색은 낙관(落款) 자리에만 쓴다 — 목록 60줄이 전부 붉으면 낙관이 아니라 소음이 된다.
// 삼라 패턴대로 브랜드 고정 스킴을 쓰고 다이나믹 컬러는 쓰지 않는다.
// ─────────────────────────────────────────────────────────────────────────────

private val CheongMuk = Color(0xFF2C3640)   // 청묵(靑墨) — 주된 먹빛
private val Hanji = Color(0xFFF5EFE2)       // 한지 바탕
private val JuSa = Color(0xFF9C3A2E)        // 주사(朱砂) — 낙관
private val GeumNi = Color(0xFFA5873F)      // 금니(金泥)

private val BrandLight = lightColorScheme(
    primary = CheongMuk,
    onPrimary = Color(0xFFF8F3E8),
    primaryContainer = Color(0xFFD5DCE2),
    onPrimaryContainer = Color(0xFF16202A),
    secondary = JuSa,
    onSecondary = Color(0xFFFDF4EE),
    secondaryContainer = Color(0xFFF0D9D2),
    onSecondaryContainer = Color(0xFF561B12),
    tertiary = GeumNi,
    onTertiary = Color(0xFFFFFBF2),
    background = Hanji,
    onBackground = Color(0xFF241F19),
    surface = Hanji,
    onSurface = Color(0xFF241F19),
    surfaceVariant = Color(0xFFE7DECB),
    onSurfaceVariant = Color(0xFF67604F),
    outline = Color(0xFFB2A78E),
    outlineVariant = Color(0xFFD5CAB2),
    surfaceContainerLowest = Color(0xFFFEFBF4),
    surfaceContainerLow = Color(0xFFFAF5EA),
    surfaceContainer = Color(0xFFF7F1E4),
    surfaceContainerHigh = Color(0xFFF1EADB),
    surfaceContainerHighest = Color(0xFFEAE1CE),
    error = Color(0xFFA03A2E),
    onError = Color(0xFFFFF5F2),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFA9BCC9),
    onPrimary = Color(0xFF15222B),
    primaryContainer = Color(0xFF2E3E49),
    onPrimaryContainer = Color(0xFFD3E1EA),
    secondary = Color(0xFFD9796A),
    onSecondary = Color(0xFF47160E),
    secondaryContainer = Color(0xFF6B291F),
    onSecondaryContainer = Color(0xFFF5D6CE),
    tertiary = Color(0xFFD8B665),
    onTertiary = Color(0xFF3B2C07),
    background = Color(0xFF141311),
    onBackground = Color(0xFFE9E2D4),
    surface = Color(0xFF141311),
    onSurface = Color(0xFFE9E2D4),
    surfaceVariant = Color(0xFF2B2925),
    onSurfaceVariant = Color(0xFFBAB2A1),
    outline = Color(0xFF8A8271),
    outlineVariant = Color(0xFF3A3733),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF191815),
    surfaceContainer = Color(0xFF1D1C19),
    surfaceContainerHigh = Color(0xFF25231F),
    surfaceContainerHighest = Color(0xFF2D2B26),
    error = Color(0xFFE0958A),
    onError = Color(0xFF44100A),
)

/**
 * 기하 규율 — 모서리는 네 단계뿐.
 *
 * 화면마다 7·9·11dp 가 섞이면 눈이 그 불규칙을 읽고 정돈되지 않았다고 판단한다.
 * 면(카드·입력·버튼·칩)은 반드시 이 넷 중 하나를 쓴다.
 *
 * 획은 여기 해당하지 않는다 — 구분선·족자 막대·낙관 테두리처럼 두께가 몇 dp뿐인
 * 그래픽 요소는 반경이 두께에 비례해야 해서 별도로 둔다.
 */
object InkShape {
    /** 칩·배지 — 작은 표식 */
    val small = RoundedCornerShape(4.dp)

    /** 기본 — 카드·입력·버튼 */
    val medium = RoundedCornerShape(10.dp)

    /** 큰 면 — 족자·바텀시트·다이얼로그 */
    val large = RoundedCornerShape(16.dp)

    /** 아바타·토글처럼 완전히 둥근 것 */
    val circle = CircleShape
}

/**
 * 간격 규율 — 4dp 격자 위의 한 벌.
 *
 * 숫자 이름(`s8`)이 정본이다. `xs/sm/md` 식으로 부르면 반단계가 필요해질 때마다
 * 이름이 꼬이고, 결국 다시 원시값을 타이핑하게 된다. 값이 곧 이름이면 그럴 일이 없다.
 *
 * 쓰는 자리는 **간격뿐**이다 — `padding` · `spacedBy` · `Spacer`.
 * `size()` · 모서리 반경 · 획 두께는 여기 해당하지 않는다. 그쪽은 [InkShape] 와
 * 각 부품의 사정을 따른다.
 *
 * 2026-08-16 실측 기준 4dp 격자를 벗어난 값이 44% 였다. 그 값들(6·7·10·14·18…)은
 * 일부러 토큰으로 만들지 않았다 — 토큰을 주면 정당한 값으로 굳는다. 원시값으로
 * 남겨 두면 `tools/design-guard.py` 가 세어 주고, 그 목록이 곧 다음 라운드의 작업표다.
 */
object InkSpace {
    /** 광학 보정용 반 칸 — 글줄 정렬을 눈으로 맞출 때만. */
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s28 = 28.dp

    // 큰 호흡. 실측에서 24dp 이상이 3.3% 뿐이라(레퍼런스 7.2%) 화면에 여백을 줄
    // 단 자체가 없었다. 자리를 먼저 만들어 둔다.
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp

    /** 화면 좌우 거터. 11곳이 이미 이 값으로 서 있었다 — 규칙이었는데 이름이 없었다. */
    val gutter = s20

    /** 카드 안쪽 여백 */
    val card = s16

    /** 세로로 쌓을 때의 기본 간격 */
    val stack = s8
}

/**
 * Material 스킴이 자리를 마련해 주지 않는 도메인 색.
 *
 * 오행 다섯 색과 길·흉 판정색은 라이트/다크에서 각각 다른 명도가 필요해서
 * (다크에서 진한 녹색 글씨는 읽히지 않는다) 스킴처럼 두 벌을 두고 갈아 끼운다.
 */
@Immutable
data class InkPalette(
    val seal: Color,
    val onSeal: Color,
    val gold: Color,
    /** 금니 옅은 면 — 배지·강조 칸의 바탕 */
    val goldSoft: Color,
    val gil: Color,
    val botong: Color,
    val hyung: Color,
    val male: Color,
    val female: Color,
    val tree: Color,
    val fire: Color,
    val earth: Color,
    val metal: Color,
    val water: Color,
) {
    fun of(element: Element): Color = when (element) {
        Element.TREE -> tree
        Element.FIRE -> fire
        Element.EARTH -> earth
        Element.METAL -> metal
        Element.WATER -> water
    }
}

private val LightInk = InkPalette(
    seal = JuSa,
    onSeal = Color(0xFFFBF3E7),
    gold = GeumNi,
    // JP+UI Kit 의 금색 램프 200. 알파를 씌워 만들던 방식은 한지 바탕이 비쳐
    // 색상이 틀어졌다 — 값을 직접 둔다.
    goldSoft = Color(0xFFFFEFAA),
    gil = Color(0xFF2F6B4A),
    botong = Color(0xFF876A22),
    hyung = Color(0xFF9E3A2F),
    male = Color(0xFF3D6CB0),
    female = Color(0xFFB1567A),
    tree = Color(0xFF3F7A55),
    fire = Color(0xFFB0463A),
    earth = Color(0xFF9E7C33),
    metal = Color(0xFF6F7681),
    water = Color(0xFF3A5F8A),
)

private val DarkInk = InkPalette(
    seal = Color(0xFFC1584A),
    onSeal = Color(0xFFFBF3E7),
    gold = Color(0xFFD8B665),
    // 다크는 킷에 대응하는 값이 없다. 이 배지는 판정 배지 다섯 개와 한 줄에 서기 때문에
    // 무게가 맞아야 한다 — 그쪽이 쓰는 알파 14%가 어두운 면 위에 만들어 내는 명도를
    // 그대로 고정값으로 옮겼다. 옅은 노랑을 쓰면 혼자 튄다.
    goldSoft = Color(0xFF342E20),
    gil = Color(0xFF74B792),
    botong = Color(0xFFD3AE5F),
    hyung = Color(0xFFE0958A),
    male = Color(0xFF7FA6DC),
    female = Color(0xFFD98BA9),
    tree = Color(0xFF6FAE84),
    fire = Color(0xFFD9786B),
    earth = Color(0xFFCFA95C),
    metal = Color(0xFF9AA3AF),
    water = Color(0xFF7098CC),
)

private val LocalInk = staticCompositionLocalOf { LightInk }

object InkTheme {
    val colors: InkPalette
        @Composable @ReadOnlyComposable get() = LocalInk.current
}

// 활자는 Type.kt — 마루부리(한글 명조)·한자 명조·프리텐다드 세 벌을 번들한다.

@Composable
fun NamingHouseTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
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

    CompositionLocalProvider(LocalInk provides if (dark) DarkInk else LightInk) {
        MaterialTheme(colorScheme = colorScheme, typography = InkTypography, content = content)
    }
}
