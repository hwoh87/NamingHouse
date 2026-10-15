package com.naminghouse.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.naminghouse.app.JokjaStyle
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ads.RewardedAds
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import com.samramanshang.manseryeok.orrery.model.Gender
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 이름 상세 — 족자(簇子) 한 폭.
 *
 * 이름 하나를 정하는 순간이 이 앱의 감정적 정점이라, 바텀시트가 아니라
 * 화면 전체를 준다. 머리의 족자는 그대로 감명서 이미지가 되어 공유된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: NamingViewModel, nav: NavHostController) {
    // ActivityResult must remain registered even when a process restart loses vm.selected.
    val pdfExport = rememberCertificateExport()
    val sel = vm.selected
    if (sel == null) {
        LaunchedEffect(pdfExport.state.busy) {
            if (!pdfExport.state.busy) nav.popBackStack()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("PDF 저장을 마무리하고 있어요", modifier = Modifier.padding(InkSpace.gutter))
        }
        return
    }
    val (eval, stat) = sel
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()
    val exportLayer = rememberGraphicsLayer()
    var showPremium by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    // 감명서 풀이를 다 볼 수 있는가 — 프리미엄이거나, 이 이름을 따로 열어 두었거나.
    val unlocked = vm.isUnlocked(eval)

    // 잠긴 감명서에서만 보상형 광고가 쓰인다 — 필요할 때 당겨 두어야 버튼이 즉시 뜬다.
    val adsReady by com.naminghouse.app.ads.AdsInit.adsReady.collectAsState()
    LaunchedEffect(unlocked, adsReady) { if (!unlocked) RewardedAds.preload(context) }

    // 표구는 프리미엄에서만 산다 — 소유가 꺼져 있으면(환불 등) 백지로 되돌린다.
    val style = if (vm.isPremium) vm.jokjaStyle else JokjaStyle.BAEKJI

    // 표지 신원 칸 — 사주가 있을 때만 생년월일 줄을 싣는다.
    val birthLine = vm.selectedBirthLine
    val issued = remember {
        val d = LocalDate.now()
        "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    }


    Column(Modifier.fillMaxSize()) {
        SceneTopBar("이름 이야기", { nav.popBackStack() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = InkSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s20),
        ) {
            JokjaCard(
                eval = eval,
                style = style,
                sealName = if (vm.isPremium) {
                    eval.givenHanja.joinToString("") { it.char.toString() }
                } else {
                    null
                },
                modifier = Modifier.testTag("name-cover").drawWithContent {
                    shareLayer.record { this@drawWithContent.drawContent() }
                    // PDF 용 3배 캡처 — 명령만 담아 두고 래스터화는 저장할 때 한다.
                    exportLayer.record(
                        size = IntSize(
                            (size.width * ExportScale).roundToInt(),
                            (size.height * ExportScale).roundToInt(),
                        ),
                    ) {
                        scale(ExportScale, pivot = Offset.Zero) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    drawLayer(shareLayer)
                },
            )

            JokjaStyleRow(
                selected = style,
                onSelect = { s ->
                    if (vm.isPremium) {
                        vm.onJokjaStyleChanged(s)
                    } else if (s != JokjaStyle.BAEKJI) {
                        showPremium = true
                    }
                },
            )

            NameReasons(eval)
            FirstGreeting(eval.givenName)

            EvaluationDetail(
                eval = eval,
                saju = vm.selectedSaju,
                stat = stat,
                showHero = false,
                locked = !unlocked,
                onUnlock = { showUnlock = true },
            )

            Spacer(Modifier.height(InkSpace.s4))
        }

        // 하단 고정 행동 줄 — 담아두기 · 감명서 공유
        Column(Modifier.fillMaxWidth()) {
            InkStroke(
                Modifier.fillMaxWidth().padding(horizontal = InkSpace.gutter),
                color = MaterialTheme.colorScheme.onSurface,
                alpha = 0.16f,
            )
            Row(
                Modifier.padding(horizontal = InkSpace.gutter, vertical = InkSpace.s12),
                horizontalArrangement = Arrangement.spacedBy(InkSpace.s8),
            ) {
                val isFav = vm.isFavorite(eval)
                OutlinedButton(
                    onClick = { vm.toggleFavorite(eval) },
                    shape = InkShape.medium,
                    contentPadding = PaddingValues(horizontal = InkSpace.s8),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(
                        if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isFav) InkTheme.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(InkSpace.s8))
                    Text(if (isFav) "담아둠" else "담아두기", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        if (!sharing) {
                            sharing = true
                            scope.launch {
                                try {
                                    // Compose owns the capture; only our independent copy is recycled.
                                    val bitmap = checkNotNull(shareLayer.toImageBitmap().asAndroidBitmap()
                                        .copy(Bitmap.Config.ARGB_8888, false))
                                    try { shareBitmap(context, bitmap) } finally { bitmap.recycle() }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast.makeText(context, "이미지를 만들지 못했습니다", Toast.LENGTH_SHORT).show()
                                } finally {
                                    sharing = false
                                }
                            }
                        }
                    },
                    enabled = !sharing && !pdfExport.state.busy,
                    shape = InkShape.medium,
                    modifier = Modifier.weight(1.4f).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(InkSpace.s8))
                    Text(
                        "공유",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // 프리미엄 감명서 — 인쇄급 PDF. 금색 아이콘이 유일한 프리미엄 표식이다.
                OutlinedButton(
                    onClick = {
                        if (vm.isPremium) {
                            // Freeze the complete evaluation before any suspension or picker launch.
                            val exportSaju = vm.selectedSaju
                            val provenance = vm.selectedEvaluationRecord?.provenanceLabel
                            pdfExport.begin("네이밍하우스_감명서_${eval.surname}${eval.givenName}.pdf") { file ->
                                val bitmap = checkNotNull(exportLayer.toImageBitmap().asAndroidBitmap()
                                    .copy(Bitmap.Config.ARGB_8888, false))
                                try {
                                    withContext(Dispatchers.IO) {
                                        file.outputStream().use { output ->
                                            writeCertificatePdf(context, output, bitmap, eval, exportSaju, stat,
                                                birthLine, issued, provenance)
                                        }
                                    }
                                } finally { bitmap.recycle() }
                            }
                        } else {
                            showPremium = true
                        }
                    },
                    shape = InkShape.medium,
                    contentPadding = PaddingValues(0.dp),
                    enabled = !pdfExport.state.busy && !sharing,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = "PDF 저장",
                        modifier = Modifier.size(20.dp),
                        tint = if (vm.isPremium) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            InkTheme.colors.gold
                        },
                    )
                }
            }
            if (vm.isPremium) {
                Text(
                    when {
                        pdfExport.state.preparing -> "감명서 PDF를 준비하고 있어요"
                        pdfExport.state.destinationUri != null -> "선택한 위치에 PDF를 저장하고 있어요"
                        birthLine != null -> "PDF에는 이름·한자·출생정보·평가 기준이 담겨요"
                        else -> "PDF에는 이름·한자·평가 기준이 담겨요"
                    },
                    modifier = Modifier.padding(horizontal = InkSpace.gutter).padding(bottom = InkSpace.s12),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showPremium) {
        PremiumSheet(vm, onDismiss = { showPremium = false })
    }
    if (showUnlock) {
        UnlockSheet(vm, eval, onDismiss = { showUnlock = false })
    }
}

/**
 * 족자 한 폭 — 세로로 쓴 한자 이름, 독음, 낙관.
 *
 * 위아래 표구 막대와 모서리 장식은 그림 에셋 없이 그린다. 공유 이미지가
 * 이 카드 그대로라, 배경은 반투명이 아니라 불투명한 종이색으로 깐다.
 * [sealName] 이 오면(프리미엄) 이름 아래에 성명인을 함께 찍는다.
 */
@Composable
private fun JokjaCard(
    eval: NameEvaluation,
    style: JokjaStyle,
    sealName: String?,
    modifier: Modifier = Modifier,
) {
    val hanjaChars = (eval.surnameHanja + eval.givenHanja).map { it.char }
    val hangul = eval.surname + eval.givenName
    val colors = jokjaColors(style)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = InkShape.large,
        color = colors.paper,
        contentColor = colors.ink,
    ) {
        Box {
            Box(Modifier.matchParentSize().hanjiGrain(if (style == JokjaStyle.BAEKJI) 0.4f else 0.18f))
            // 모서리 장식 — 표구의 귀. 금니 두 줄로 긋는다.
            CornerMarks(
                color = colors.corner,
                modifier = Modifier.matchParentSize().padding(InkSpace.s12),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = InkSpace.s24, vertical = InkSpace.s16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NAMING HOUSE  /  이름 이야기", style = MaterialTheme.typography.labelSmall, color = colors.subInk)
                Spacer(Modifier.height(InkSpace.s16))
                Text(hangul, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(InkSpace.s8))
                Text(hanjaChars.joinToString("  "), fontFamily = HanjaFamily,
                    style = MaterialTheme.typography.headlineMedium, color = colors.subInk)
                Spacer(Modifier.height(InkSpace.s12))
                Text(meaningLine(eval), style = MaterialTheme.typography.bodyMedium,
                    color = colors.subInk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(InkSpace.s12))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
                    Text("성명학 ${eval.score}점 · ${eval.grade}", style = MaterialTheme.typography.labelLarge, color = colors.subInk)
                    if (sealName != null) NameSeal(sealName)
                }

            }
        }
    }
}

/** 표구 막대 — 족자의 위아래 가로대 */
@Composable
private fun JokjaRod(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(color, RoundedCornerShape(3.dp))
    )
}

/** 족자 표구별 색 — 표구는 UI 모드가 아니라 '종이'라서 백지 외에는 값을 고정한다. */
private data class JokjaColors(
    val paper: Color,
    val ink: Color,
    val subInk: Color,
    val rod: Color,
    val corner: Color,
)

@Composable
private fun jokjaColors(style: JokjaStyle): JokjaColors = when (style) {
    JokjaStyle.BAEKJI -> JokjaColors(
        paper = MaterialTheme.colorScheme.surfaceContainerLowest,
        ink = MaterialTheme.colorScheme.onSurface,
        subInk = MaterialTheme.colorScheme.onSurfaceVariant,
        rod = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        corner = InkTheme.colors.gold.copy(alpha = 0.55f),
    )
    // 감지금니(紺紙金泥) — 쪽빛 종이에 금물 글씨. 고려 사경의 표구다.
    JokjaStyle.GAMJI -> JokjaColors(
        paper = Color(0xFF222B36),
        ink = Color(0xFFD8B96A),
        subInk = Color(0xFF97A1AD),
        rod = Color(0xFFC9A44E).copy(alpha = 0.9f),
        corner = Color(0xFFC9A44E).copy(alpha = 0.65f),
    )
    // 다갈(茶褐) — 바랜 옛 종이에 짙은 먹.
    JokjaStyle.DAGAL -> JokjaColors(
        paper = Color(0xFFE9DCC4),
        ink = Color(0xFF41352A),
        subInk = Color(0xFF7C6C57),
        rod = Color(0xFF6B4F3A).copy(alpha = 0.9f),
        corner = Color(0xFFA8823E).copy(alpha = 0.7f),
    )
}

/** 성명인(姓名印) — 프리미엄에서 이름이 새겨지는 전각 도장. 인주색은 표구와 무관하다. */
@Composable
private fun NameSeal(name: String) {
    val ink = InkTheme.colors
    Box(Modifier.background(ink.seal, InkShape.small)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(InkSpace.s4)
                .border(1.dp, ink.onSeal.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Column(
            Modifier.padding(horizontal = InkSpace.s8, vertical = InkSpace.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            name.forEach { ch ->
                Text(
                    ch.toString(),
                    color = ink.onSeal,
                    fontFamily = HanjaFamily,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 표구 고르기 — 백지·감지금니·다갈. 프리미엄이 아니면 백지 밖 선택이 구매 시트를 연다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JokjaStyleRow(selected: JokjaStyle, onSelect: (JokjaStyle) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpace.s8),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s4)) {
        JokjaStyle.entries.forEach { style ->
            FilterChip(selected = style == selected, onClick = { onSelect(style) },
                label = { Text(style.label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Box(Modifier.size(12.dp).background(jokjaColors(style).paper, InkShape.circle)
                        .border(1.dp, MaterialTheme.colorScheme.outline, InkShape.circle))
                })
        }
    }
}

/** 낙관 — 비스듬히 찍힌 점수 도장. */
@Composable
private fun StampedSeal(score: Int, grade: String) {
    SealBadge(
        main = "$score",
        sub = grade,
        modifier = Modifier.graphicsLayer { rotationZ = -3f },
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

/** PDF 캡처 배율 — 화면 폭의 3배면 A4 기준 대략 300dpi 급 인쇄 품질이 된다. */
private const val ExportScale = 3f

/** Each completed image gets a unique URI. Keep it available for delayed receivers. */
private suspend fun shareBitmap(context: android.content.Context, bitmap: Bitmap) {
    var file: java.io.File? = null
    var launched = false
    try {
        withContext(Dispatchers.IO) {
            file = createSharedImageFile(context.cacheDir)
            file!!.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "이미지를 저장하지 못했습니다" }
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", checkNotNull(file))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("감명서", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "감명서 공유"))
        launched = true
    } finally {
        if (!launched) file?.delete()
    }
}
