package com.naminghouse.app.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.R
import com.naminghouse.app.shareText
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme

/** 담아둔 이름을 여는 별 — 결과 화면 앱바에서 쓴다. */
@Composable
fun FavoritesAction(count: Int, onClick: () -> Unit) {
    val empty = count == 0
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (!empty) {
                    Badge(
                        containerColor = InkTheme.colors.seal,
                        contentColor = InkTheme.colors.onSeal,
                    ) { Text("$count") }
                }
            }
        ) {
            Icon(
                if (empty) Icons.Outlined.StarBorder else Icons.Filled.Star,
                contentDescription = "담아둔 이름",
                tint = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else InkTheme.colors.gold,
            )
        }
    }
}

/**
 * 담아둔 이름 — 하단 탭의 한 화면.
 *
 * 추천 60개를 훑고 서너 개를 추려 배우자·부모님과 상의하는 게 실제 흐름이라,
 * 담아두기와 내보내기가 같이 있어야 쓸모가 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: NamingViewModel, nav: NavHostController) {
    val context = LocalContext.current
    // 담아둔 이름을 다시 평가하면 뷰모델이 목적지를 적어 둔다 — 여기서 소비하고 이동한다.
    LaunchedEffect(vm.pendingRoute) {
        vm.pendingRoute?.let { route ->
            vm.pendingRoute = null
            nav.navigate(route)
        }
    }
    var notReady by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var familyName by remember { mutableStateOf<com.naminghouse.app.FavoriteName?>(null) }
    familyName?.let { name ->
        FamilyNameSheet(name, onSave = { vm.updateFavoriteNote(name, it) }, onDismiss = { familyName = null })
    }
    var comparing by rememberSaveable { mutableStateOf(false) }
    val compared = vm.favorites.filter { it.fullHangul + it.hanja in selected }
    if (comparing) {
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { comparing = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = InkSpace.s32)) {
                SceneHeading("나란히 놓고 고르기", "우리 가족의 이름 후보", "저장 당시의 점수와 뜻을 비교해요.", Modifier.padding(horizontal = InkSpace.gutter))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = InkSpace.gutter),
                    horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
                    compared.forEach { name ->
                        InkCard(modifier = Modifier.width(220.dp), spacing = InkSpace.s16) {
                            Text(name.fullHangul, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(name.hanja, fontFamily = HanjaFamily, style = MaterialTheme.typography.titleLarge)
                            SectionTitle("이름의 뜻")
                            Text(name.meaning, style = MaterialTheme.typography.bodyMedium)
                            SectionTitle("성명학 점수")
                            Text("${name.score}점 · ${name.grade}", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { comparing = false; notReady = !vm.openFavorite(name) }) { Text("상세 풀이 보기 →") }
                        }
                    }
                }
                Text("점수는 저장 당시 기준이에요. 출생 정보와 학파가 다르면 달라질 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(InkSpace.gutter))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = InkSpace.gutter),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s12),
    ) {
        SceneHeading("03  ·  가족의 이름책", "담아둔 이름", "마음에 남은 ${vm.favorites.size}개의 이름. 함께 비교하고 이야기해 보세요.")
        if (vm.favorites.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                Button(onClick = { comparing = true }, enabled = compared.size >= 2,
                    shape = InkShape.medium, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text("${compared.size}/3 이름 비교")
                }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText(vm.favorites)) }
                    context.startActivity(Intent.createChooser(send, "이름 공유"))
                }, shape = InkShape.medium, modifier = Modifier.heightIn(min = 52.dp)) { Text("목록 공유") }
            }
            Text("비교할 이름을 2~3개 선택해 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (vm.favorites.isEmpty()) {
            EmptyNames("첫 번째 이름을 담아보세요", "추천 목록의 별을 누르면 이곳에 모여요. 가족과 나란히 비교하며 골라보세요.", "이름 찾으러 가기") {
                vm.mode = com.naminghouse.app.AppMode.RECOMMEND
                nav.navigate(com.naminghouse.app.Routes.INPUT)
            }
        } else {
            if (notReady) {
                Text(
                    "한자 자료를 불러오는 중이에요. 잠시 후 다시 눌러 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            vm.favorites.forEach { f ->
                InkCard(
                    onClick = { notReady = !vm.openFavorite(f) },
                    contentPadding = PaddingValues(InkSpace.s16),
                    spacing = InkSpace.s12,
                ) {
                    TextButton(onClick = { familyName = f }) { Text("가족 이름 카드") }
                    if (f.familyNote.isNotBlank()) Text(f.familyNote, style = MaterialTheme.typography.bodyMedium)
                    val key = f.fullHangul + f.hanja
                    FilterChip(selected = key in selected,
                        enabled = key in selected || compared.size < 3,
                        onClick = { selected = if (key in selected) selected - key else selected + key },
                        label = { Text(if (key in selected) "비교 선택됨" else "비교에 추가") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                            Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s4)) {
                                Text(
                                    f.fullHangul,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(InkSpace.s8))
                                Text(
                                    f.hanja,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = HanjaFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                f.meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "종합 ${f.score}점 · ${f.grade}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(onClick = { vm.removeFavorite(f) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "빼기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(InkSpace.s24))
    }
}
