package com.naminghouse.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * 이름 상세 — 족자(簇子) 한 폭.
 *
 * 이름 하나를 정하는 순간이 이 앱의 감정적 정점이라, 바텀시트가 아니라
 * 화면 전체를 준다. 머리의 족자는 그대로 감명서 이미지가 되어 공유된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: NamingViewModel, nav: NavHostController) {
    val sel = vm.selected
    if (sel == null) {
        // 프로세스 복원 등으로 선택이 사라진 채 들어오면 조용히 물러난다.
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val (eval, stat) = sel
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "이름 상세",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            // 상태바 인셋은 Scaffold 가 이미 콘텐츠에 주었다 — 기본값대로 두면 이중 공백.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JokjaCard(
                eval = eval,
                modifier = Modifier.drawWithContent {
                    shareLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(shareLayer)
                },
            )

            vm.saju?.let { SajuCard(it) }

            EvaluationDetail(eval, vm.saju, stat, showHero = false)

            Spacer(Modifier.height(4.dp))
        }

        // 하단 고정 행동 줄 — 담아두기 · 감명서 공유
        Column(Modifier.fillMaxWidth()) {
            InkStroke(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurface,
                alpha = 0.16f,
            )
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isFav = vm.isFavorite(eval)
                OutlinedButton(
                    onClick = { vm.toggleFavorite(eval) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Icon(
                        if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isFav) InkTheme.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFav) "담아둠" else "담아두기", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val bitmap = shareLayer.toImageBitmap().asAndroidBitmap()
                                shareBitmap(context, bitmap, eval)
                            }.onFailure {
                                Toast.makeText(context, "이미지를 만들지 못했습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f).height(50.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "감명서 공유",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 족자 한 폭 — 세로로 쓴 한자 이름, 독음, 낙관.
 *
 * 위아래 표구 막대와 모서리 장식은 그림 에셋 없이 그린다. 공유 이미지가
 * 이 카드 그대로라, 배경은 반투명이 아니라 불투명한 종이색으로 깐다.
 */
@Composable
private fun JokjaCard(eval: NameEvaluation, modifier: Modifier = Modifier) {
    val hanjaChars = (eval.surnameHanja + eval.givenHanja).map { it.char }
    val hangul = eval.surname + eval.givenName

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.hanjiGrain(0.4f)) {
            // 모서리 장식 — 표구의 귀. 금니 두 줄로 긋는다.
            CornerMarks(
                color = InkTheme.colors.gold.copy(alpha = 0.55f),
                modifier = Modifier.matchParentSize().padding(10.dp),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                JokjaRod()
                Spacer(Modifier.height(18.dp))

                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                    // 이름 — 세로 한 줄
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        hanjaChars.forEach { ch ->
                            Text(
                                ch.toString(),
                                fontFamily = HanjaFamily,
                                fontSize = 44.sp,
                                lineHeight = 54.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    // 독음과 낙관 — 족자의 방서(傍書) 자리
                    Column(
                        Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        hangul.forEach { ch ->
                            Text(
                                ch.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        StampedSeal(score = eval.score, grade = eval.grade, key = eval)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    meaningLine(eval),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                JokjaRod()
                Spacer(Modifier.height(8.dp))
                Text(
                    "작명하우스 · 사주와 성명학으로 지은 이름",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 표구 막대 — 족자의 위아래 가로대 */
@Composable
private fun JokjaRod() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
    )
}

/** 낙관 — 도장이 찍히듯 떨어지는 점수. */
@Composable
private fun StampedSeal(score: Int, grade: String, key: Any) {
    val scale = remember(key) { Animatable(1.55f) }
    val alpha = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        launch { alpha.animateTo(1f, tween(130)) }
        scale.animateTo(
            1f,
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        )
    }
    SealBadge(
        main = "$score",
        sub = grade,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
            rotationZ = -3f
        },
    )
}

/** 표구 모서리 귀 — 네 귀에 짧은 두 줄. */
@Composable
private fun CornerMarks(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val len = 14.dp.toPx()
        val gap = 3.dp.toPx()
        val stroke = Stroke(width = 1.2.dp.toPx())
        val w = size.width
        val h = size.height
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            // 바깥 줄
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x, y), end = androidx.compose.ui.geometry.Offset(x + dx * len, y), strokeWidth = stroke.width)
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x, y), end = androidx.compose.ui.geometry.Offset(x, y + dy * len), strokeWidth = stroke.width)
            // 안 줄
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x + dx * gap, y + dy * gap), end = androidx.compose.ui.geometry.Offset(x + dx * (len * 0.62f), y + dy * gap), strokeWidth = stroke.width)
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x + dx * gap, y + dy * gap), end = androidx.compose.ui.geometry.Offset(x + dx * gap, y + dy * (len * 0.62f)), strokeWidth = stroke.width)
        }
        corner(0f, 0f, 1f, 1f)
        corner(w, 0f, -1f, 1f)
        corner(0f, h, 1f, -1f)
        corner(w, h, -1f, -1f)
    }
}

/** 캡처한 감명서를 캐시에 쓰고 공유 시트를 연다. */
private fun shareBitmap(context: android.content.Context, bitmap: Bitmap, eval: NameEvaluation) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "gammyeong_${eval.surname}${eval.givenName}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "감명서 공유"))
}
