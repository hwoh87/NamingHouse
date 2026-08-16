package com.naminghouse.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace

/**
 * 삼라만상 만세력 앱 안내 — 형제 앱으로 건너가는 다리.
 *
 * 이 앱의 사주 계산은 삼라 엔진을 그대로 이식한 것이라, 같은 생년월일시를 넣으면
 * 두 앱의 사주팔자가 어긋나지 않는다. 작명은 이름을 고르는 데까지만 보여 주므로
 * 대운·세운처럼 더 깊은 풀이를 원하는 사용자를 그쪽으로 보낸다.
 */

/** 만세력 - 삼라만상 (Google Play) */
const val SAMRA_PACKAGE = "com.samramanshang.manseryeok"

/** 설치돼 있으면 앱을 열고, 없으면 스토어로 보낸다. 스토어 앱조차 없으면 웹으로. */
fun openSamra(context: Context) {
    context.packageManager.getLaunchIntentForPackage(SAMRA_PACKAGE)?.let {
        context.startActivity(it)
        return
    }
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=$SAMRA_PACKAGE".toUri())
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$SAMRA_PACKAGE".toUri(),
        )
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "스토어를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 형제 앱 배너 — 카드 한 장.
 *
 * 광고처럼 보이지 않도록 화면의 다른 카드와 같은 [InkCard] 를 쓰고, 색은 삼라 쪽
 * 표식(감색 바탕 금색 글자)에만 남긴다. [subtitle] 로 놓이는 자리에 맞는 말을 준다.
 */
@Composable
fun SamraBanner(
    modifier: Modifier = Modifier,
    subtitle: String = "이 앱의 사주 계산은 삼라만상 만세력 엔진을 씁니다. " +
        "대운·세운·일진까지 더 깊이 보려면 그쪽에서 보세요.",
) {
    val context = LocalContext.current
    // 설치 여부는 첫 조립 때 한 번만 본다 — 배너를 보는 중에 앱이 설치될 일은 없다.
    val installed = remember {
        runCatching {
            context.packageManager.getLaunchIntentForPackage(SAMRA_PACKAGE) != null
        }.getOrDefault(false)
    }

    InkCard(modifier = modifier, onClick = { openSamra(context) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SamraMark()
            Spacer(Modifier.width(InkSpace.s12))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "만세력 · 삼라만상",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(InkSpace.s8))
                    Surface(
                        shape = InkShape.circle,
                        // 삼라 감색을 옅게 깔면 다크에서 배경과 붙어 사라진다 — 중립 면으로 둔다.
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    ) {
                        Text(
                            if (installed) "열기" else "무료",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = InkSpace.s8, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(InkSpace.s8))
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 삼라 런처 아이콘을 옮긴 표식 — 밤하늘색 바탕에 금색 萬. 모드와 무관하게 색을 고정한다. */
@Composable
private fun SamraMark(size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .background(SamraNavy, InkShape.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "萬",
            color = SamraGold,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = HanjaFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val SamraNavy = Color(0xFF0A1220)
private val SamraGold = Color(0xFFE8C770)
