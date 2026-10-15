package com.naminghouse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.naminghouse.app.ui.theme.*

/** 네이밍하우스의 주요 행동. 상태와 이동을 외부에서 받아 다른 화면에도 그대로 붙인다. */
@Composable
fun NamingHero(hasSavedInput: Boolean, onStart: () -> Unit, modifier: Modifier = Modifier,
    resumeLabel: String = "이어서 이름 찾기  →") {
    Surface(modifier.fillMaxWidth(), shape = InkShape.large,
        color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(Modifier.padding(InkSpace.s24), verticalArrangement = Arrangement.spacedBy(InkSpace.s16)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("우리 가족의 첫 선물", style = MaterialTheme.typography.labelLarge)
                Text("名", fontFamily = HanjaFamily, style = MaterialTheme.typography.headlineMedium)
            }
            GentleArrival {
                Text("너를 부르는 말에,\n사랑을 담는 일", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("아직 만나지 못했어도, 마음은 먼저 닿으니까.\n오래도록 다정하게 부를 이름을 찾아보세요.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = InkShape.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(if (hasSavedInput) resumeLabel else "무료로 이름 찾기  →",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("이름 추천·점수 무료 · 감명서 한 편 무료 공개",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun NamingToolTile(glyph: String, title: String, description: String,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    InkCard(modifier, onClick = onClick, spacing = InkSpace.s12) {
        Text(glyph, fontFamily = HanjaFamily, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NamingTrustNote() {
    Text("점수는 전통 성명학 기준의 참고 정보예요.\n이름의 뜻과 부르기 좋은 소리, 가족의 마음도 함께 살펴보세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = InkSpace.s8))
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun NamingHeroPreview() {
    NamingHouseTheme { NamingHero(false, {}) }
}

/** 전체 흐름에 쓰는 제목: 작은 위치 표시, 큰 질문, 한 문장의 안내. */
@Composable
fun SceneHeading(eyebrow: String, title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = InkSpace.s16), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 상세 씬의 공통 앱바. 화면마다 달랐던 인셋과 제목 크기를 한 곳에서 관리한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
fun JourneySteps(current: Int, labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                LinearProgressIndicator(progress = { if (index <= current) 1f else 0f },
                    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant, drawStopIndicator = {})
                Text("${index + 1}  $label", style = MaterialTheme.typography.labelMedium,
                    color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun EmptyNames(title: String, description: String, action: String, onAction: () -> Unit) {
    InkCard(contentPadding = PaddingValues(InkSpace.s24), spacing = InkSpace.s20) {
        Text("名", fontFamily = HanjaFamily, style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction, shape = InkShape.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(action)
        }
    }
}

/** 한 번의 짧은 등장. 시스템의 애니메이션 배율을 따르고 내용·터치는 즉시 제공한다. */
@Composable
fun GentleArrival(content: @Composable () -> Unit) {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        played = true
        progress.animateTo(1f, tween(600))
    }
    Box(Modifier.graphicsLayer {
        alpha = 0.65f + progress.value * 0.35f
        translationY = (1f - progress.value) * InkSpace.s8.toPx()
    }) { content() }
}

/** 뜻과 점수 사이에서 잠시, 실제로 아이를 부르는 소리를 상상한다. 음성 재생은 없다. */
@Composable
fun FirstGreeting(givenName: String) {
    var open by rememberSaveable(givenName) { mutableStateOf(false) }
    val last = givenName.lastOrNull()
    val vocative = if (last != null && last in '가'..'힣') {
        givenName + if ((last.code - '가'.code) % 28 == 0) "야" else "아"
    } else givenName
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp).semantics { stateDescription = if (open) "펼쳐짐" else "접힘" }) {
            Text(if (open) "첫 인사 접기" else "이 이름으로 첫 인사 건네보기",
                style = MaterialTheme.typography.labelLarge)
        }
        AnimatedVisibility(open, enter = fadeIn(tween(350)) + expandVertically(tween(350)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))) {
            InkCard(spacing = InkSpace.s12) {
                Text("우리 아이에게 건네는 첫 인사", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("“$vocative, 만나서 반가워.”", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text("소리 내어 천천히 불러보세요.\n그 이름을 부를 우리의 하루를 떠올려보세요.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


@Composable
fun NameReasons(eval: com.naminghouse.engine.eval.NameEvaluation) {
    val fit = eval.sajuFit
    InkCard(spacing = InkSpace.s12) {
        SectionTitle("이 이름을 살펴볼 이유")
        Text("뜻 · ${com.naminghouse.engine.eval.meaningLine(eval)}", style = MaterialTheme.typography.bodyMedium)
        Text("조화 · 전통 성명학 기준 수리 ${eval.suriVerdict.label}, 발음오행 ${eval.baleumVerdict.label}",
            style = MaterialTheme.typography.bodyMedium)
        Text(if (fit == null) "기준 · 출생 사주를 포함하지 않은 이름 풀이예요."
            else "기준 · 입력한 출생 사주를 포함해 계산했어요. 사주보완 판정은 ${fit.verdict.label}이에요.",
            style = MaterialTheme.typography.bodyMedium)
        Text("판정은 전통 성명학의 참고 정보예요. 뜻과 실제로 부르는 느낌도 함께 살펴보세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
