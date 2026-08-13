package com.naminghouse.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naminghouse.app.R
import com.naminghouse.app.Routes
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkTheme
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// 수묵 조형 요소
//
// 그림 에셋은 전부 **흰 종이 위 검은 먹** 한 장으로만 두고, 라이트에서는 곱하기로
// 얹어 종이를 물들이고 다크에서는 반전 후 스크린으로 얹어 검은 종이에 흰 먹으로
// 쓴 것처럼 만든다. 이렇게 하면 모드별 에셋을 두 벌 들고 다니지 않아도 된다.
// ─────────────────────────────────────────────────────────────────────────────

/** 흑백 반전 — 다크 모드에서 먹 그림을 흰 획으로 뒤집는다. */
private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** 한지 질감용 — 반전과 동시에 탈색해서 다크 배경에 남색 기가 돌지 않게 한다. */
private val InvertGrayFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.299f, -0.587f, -0.114f, 0f, 255f,
            -0.299f, -0.587f, -0.114f, 0f, 255f,
            -0.299f, -0.587f, -0.114f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** 화면 모드 설정으로 다크가 고정될 수 있어, 시스템 플래그가 아니라 실제 배경 명도로 판정한다. */
private val isDarkSurface: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * 한지 결만 얹는 모디파이어 — 공유용 감명서처럼 배경을 스스로 그려야 하는 면에 쓴다.
 */
@Composable
fun Modifier.hanjiGrain(alpha: Float = 0.5f): Modifier {
    val dark = isDarkSurface
    return paint(
        painterResource(R.drawable.hanji_paper),
        sizeToIntrinsics = false,
        contentScale = ContentScale.Crop,
        alpha = if (dark) alpha * 0.2f else alpha,
        colorFilter = if (dark) InvertGrayFilter else null,
    )
}

/**
 * 앱 전체의 바탕 — 한지 결 위에, 상태바 뒤까지 이어지는 원산(遠山) 한 자락.
 *
 * 화면마다 따로 깔지 않고 [com.naminghouse.app.MainActivity] 에서 한 번만 감싼다.
 * 입력 화면의 제목도, 결과 화면의 앱바도 같은 안개 위에 놓여 화면 전환이 이어져 보인다.
 */
@Composable
fun HanjiBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isDarkSurface
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paint(
                painterResource(R.drawable.hanji_paper),
                sizeToIntrinsics = false,
                contentScale = ContentScale.Crop,
                alpha = if (dark) 0.09f else 0.5f,
                colorFilter = if (dark) InvertGrayFilter else null,
            )
    ) {
        InkArt(
            res = R.drawable.ink_ridge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(300.dp),
            // 제목과 앱바 아이콘이 이 위에 앉는다 — 먹이 진하면 글씨를 잡아먹으므로
            // 봉우리 꼭대기까지 안개로 남을 만큼만 얹는다.
            alpha = if (dark) 0.26f else 0.17f,
        )
        // Surface 가 아니라 Box 로 배경을 깔기 때문에 기본 콘텐츠 색(검정)이 그대로 내려간다.
        // 다크 모드에서 색을 지정하지 않은 글씨가 검게 나오지 않도록 여기서 다시 잡아 준다.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/**
 * 먹 그림 한 장. 종이(흰색)는 지워지고 먹만 남도록 블렌드해서 얹는다.
 *
 * 원본 비율과 자리 비율이 거의 같은 곳에만 쓰기 때문에 늘려 채우고 크롭 계산은 하지 않는다.
 */
@Composable
fun InkArt(@DrawableRes res: Int, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val image = ImageBitmap.imageResource(res)
    val dark = isDarkSurface
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = alpha,
            colorFilter = if (dark) InvertFilter else null,
            blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply,
        )
    }
}

/**
 * 붓으로 한 번 그은 획. 구분선·탭 밑줄로 쓴다.
 *
 * 양 끝이 뾰족하고 가운데가 굵으며 축이 미세하게 흔들려서, 곧은 1dp 선과 달리
 * 손으로 그은 자국으로 읽힌다. [seed] 를 달리 주면 획마다 흔들림이 달라진다.
 */
@Composable
fun InkStroke(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    alpha: Float = 0.3f,
    thickness: Dp = 5.dp,
    seed: Float = 0f,
) {
    Canvas(modifier.height(thickness)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val n = 44
        // 붓을 대고 떼는 자리는 가늘고 가운데가 굵다.
        fun half(t: Float) = h * 0.5f * (0.10f + 0.90f * sin(PI * t).toFloat().pow(0.5f))
        fun mid(t: Float) = h * 0.5f + h * 0.16f * sin(t * 6.3f + seed)

        val path = Path()
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = t * w
            val y = mid(t) - half(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in n downTo 0) {
            val t = i / n.toFloat()
            path.lineTo(t * w, mid(t) + half(t))
        }
        path.close()
        drawPath(
            path,
            Brush.horizontalGradient(
                0f to color.copy(alpha = 0f),
                0.1f to color.copy(alpha = alpha),
                0.8f to color.copy(alpha = alpha * 0.85f),
                1f to color.copy(alpha = 0f),
            ),
        )
    }
}

/**
 * 종이 위에 놓인 한 장 — 그림자를 쓰지 않고 얇은 테두리와 반투명 면으로만 띄운다.
 * 반투명이라 아래의 한지 결이 비쳐 카드가 종이에서 떠오르지 않는다.
 */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = InkShape.large
    // 반투명 면이라 Surface 가 대응하는 onColor 를 찾지 못한다 — 직접 지정한다.
    val color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
    val onColor = MaterialTheme.colorScheme.onSurface
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
    if (onClick == null) {
        Surface(
            modifier.fillMaxWidth(),
            shape = shape, color = color, contentColor = onColor, border = border,
        ) { inner() }
    } else {
        Surface(
            onClick, modifier.fillMaxWidth(),
            shape = shape, color = color, contentColor = onColor, border = border,
        ) { inner() }
    }
}

/** 카드 제목 — 왼쪽에 짧은 먹 획을 세워 제목 줄을 잡아 준다. */
@Composable
fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 15.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * 낙관(落款) — 점수를 찍는 붉은 도장.
 *
 * 목록에서는 쓰지 않는다. 이름 하나를 펼쳐 볼 때만 찍혀야 도장으로 읽힌다.
 */
@Composable
fun SealBadge(main: String, sub: String, modifier: Modifier = Modifier) {
    val ink = InkTheme.colors
    Box(
        modifier
            .size(width = 58.dp, height = 62.dp)
            .background(ink.seal, InkShape.small),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .border(1.dp, ink.onSeal.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                main,
                color = ink.onSeal,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(sub, color = ink.onSeal.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 런처 아이콘과 같은 표식 — 감색 바탕에 금색 名. 모드와 무관하게 색을 고정한다. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Box(
        modifier
            .size(size)
            .background(Color(0xFF25313C), InkShape.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "名",
            color = Color(0xFFC9A44E),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = HanjaFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 하단 내비게이션 — 홈·보관함·설정.
 *
 * Material NavigationBar 대신 먹 획 표시선을 쓰는 자체 부품. 탭마다 한자 문패
 * 한 글자를 달아 세 화면이 같은 세계의 방(房)처럼 읽히게 한다.
 */
@Composable
fun InkNavBar(current: String?, onNavigate: (String) -> Unit) {
    data class Tab(val route: String, val glyph: String, val label: String)
    val tabs = listOf(
        Tab(Routes.HOME, "家", "홈"),
        Tab(Routes.FAVORITES, "藏", "보관함"),
        Tab(Routes.SETTINGS, "設", "설정"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(top = 7.dp, bottom = 9.dp)
        ) {
            tabs.forEachIndexed { i, tab ->
                val on = tab.route == current
                val color = if (on) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .clip(InkShape.medium)
                        .clickable { onNavigate(tab.route) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tab.glyph,
                        fontFamily = HanjaFamily,
                        fontSize = 17.sp,
                        color = color.copy(alpha = if (on) 1f else 0.75f),
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                    )
                    if (on) {
                        InkStroke(
                            Modifier.width(26.dp).padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            alpha = 0.85f,
                            thickness = 4.dp,
                            seed = i * 2.3f,
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

/** 먹으로 칠한 선택 막대 — Material 세그먼티드 버튼 자리를 대신한다. */
@Composable
fun <T> InkSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = InkShape.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(3.dp)) {
            options.forEach { option ->
                val on = option == selected
                val fill = if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                val labelColor =
                    if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .weight(1f)
                        .clip(InkShape.medium)
                        .background(fill)
                        .clickable { onSelect(option) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
