package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.naminghouse.app.AppMode
import com.naminghouse.app.FavoriteName
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.Routes
import com.naminghouse.app.favoritesVisible
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import com.naminghouse.app.ui.theme.InkTheme

@Composable
fun FavoritesAction(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = {
            if (count > 0) Badge(containerColor = InkTheme.colors.seal, contentColor = InkTheme.colors.onSeal) { Text("$count") }
        }) {
            Icon(if (count == 0) Icons.Outlined.StarBorder else Icons.Filled.Star,
                contentDescription = "담아둔 이름",
                tint = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else InkTheme.colors.gold)
        }
    }
}

/** 이름을 모으는 단계에서 최종 후보를 고르는 단계까지 이어지는 로컬 이름책. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(vm: NamingViewModel, nav: NavHostController) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val finishSearch = { focus.clearFocus(); keyboard?.hide(); Unit }
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var finalistsOnly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var familyName by remember { mutableStateOf<FavoriteName?>(null) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<List<FavoriteName>?>(null) }
    val compared = selected.mapNotNull { key -> vm.favorites.find { it.key == key } }
    val visible = favoritesVisible(vm.favorites, query, finalistsOnly)

    LaunchedEffect(vm.pendingRoute) {
        vm.pendingRoute?.let { route -> vm.pendingRoute = null; nav.navigate(route) }
    }
    DisposableEffect(vm) { onDispose { vm.clearComparison() } }
    LaunchedEffect(vm.recentlyRemoved) {
        vm.recentlyRemoved?.let { removed ->
            val result = snackbar.showSnackbar("${removed.fullHangul} 이름을 뺐어요", "되돌리기",
                withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.restoreFavorite(removed)
            else vm.dismissRemovedFavorite(removed)
        }
    }
    familyName?.let { name ->
        FamilyNameSheet(name, onSave = { vm.updateFavoriteNote(name, it) }, onDismiss = { familyName = null })
    }
    vm.comparison?.let { comparison ->
        FavoriteComparisonSheet(comparison, onDismiss = vm::clearComparison,
            onOpen = { vm.openComparedFavorite(it) })
    }
    if (backupOpen) FavoriteBackupSheet(vm.favorites, onImport = vm::importFavorites, onDismiss = { backupOpen = false })
    sharing?.let { items -> FavoriteShareSheet(items, onDismiss = { sharing = null }) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = InkSpace.gutter).testTag("favorites-list"),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
            SceneHeading("03  ·  가족의 이름책", "담아둔 이름", "마음에 남은 이름을 모으고, 우리 가족의 최종 후보를 골라보세요.")
            OutlinedButton(onClick = { finishSearch(); backupOpen = true }, enabled = vm.favoritesLoaded,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("favorites-backup")) { Text("이름책 백업·가져오기") }
            if (!vm.favoritesLoaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("담아둔 이름을 불러오고 있어요", style = MaterialTheme.typography.bodyMedium)
            } else if (vm.favorites.isEmpty()) {
                EmptyNames("첫 번째 이름을 담아보세요", "추천 목록의 별을 누르면 이곳에 모여요. 가족과 뜻과 소리를 함께 살펴보세요.", "이름 찾으러 가기") {
                    vm.mode = AppMode.RECOMMEND
                    nav.navigate(Routes.INPUT)
                }
            } else {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { finishSearch() }),
                    label = { Text("이름·한자·뜻·메모 검색") }, shape = InkShape.medium,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "검색 지우기") } },
                    modifier = Modifier.fillMaxWidth().testTag("favorites-search"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                    FilterChip(selected = !finalistsOnly, onClick = { finishSearch(); finalistsOnly = false }, label = { Text("전체 ${vm.favorites.size}") })
                    FilterChip(selected = finalistsOnly, onClick = { finishSearch(); finalistsOnly = true },
                        label = { Text("최종 후보 ${vm.favorites.count { it.isFinalist }}") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                    Button(onClick = { finishSearch(); vm.compareFavorites(compared) }, enabled = compared.size >= 2 && !vm.comparisonBusy && !vm.busy,
                        shape = InkShape.medium, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Text(if (vm.comparisonBusy) "비교 준비 중" else "${compared.size}/3 이름 비교")
                    }
                    OutlinedButton(onClick = {
                        finishSearch()
                        sharing = visible.toList()
                    }, enabled = visible.isNotEmpty(), shape = InkShape.medium, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(if (finalistsOnly || query.isNotBlank()) "결과 공유" else "목록 공유")
                    }
                }
                Text("2~3개를 선택하면 현재 출생 정보와 학파로 다시 계산해 비교해요. 저장 점수는 그대로 남아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (compared.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("선택: ${compared.joinToString(" · ") { it.fullHangul }}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { selected = emptyList() }) { Text("선택 해제") }
                    }
                }
                if (vm.comparisonBusy || vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                (vm.comparisonError ?: vm.errorMessage)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { nav.navigate(Routes.INPUT) }) { Text("입력 조건 확인") }
                }
                if (visible.isEmpty()) {
                    EmptyNames(if (query.isNotBlank()) "찾는 이름이 없어요" else "최종 후보를 골라보세요",
                        if (query.isNotBlank()) "검색어를 바꾸거나 전체 이름을 확인해 보세요." else "이름 카드에서 ‘최종 후보’를 누르면 이곳에 모여요.", "전체 이름 보기") {
                        finishSearch(); query = ""; finalistsOnly = false
                    }
                }
                visible.forEach { name ->
                    FavoriteCard(name, selected = name.key in selected,
                        selectionEnabled = name.key in selected || compared.size < 3,
                        onSelect = { selected = if (name.key in selected) selected - name.key else compared.map { it.key } + name.key },
                        onFinalist = { vm.setFavoriteFinalist(name, !name.isFinalist) },
                        onOpen = { vm.openFavorite(name) }, openEnabled = !vm.busy && !vm.comparisonBusy,
                        onFamilyCard = { familyName = name },
                        onRemove = { selected = selected - name.key; vm.removeFavorite(name) })
                }
            }
            vm.favoriteError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                vm.recentlyRemoved?.let { removed ->
                    TextButton(onClick = { vm.restoreFavorite(removed) }) { Text("삭제한 이름 복원 다시 시도") }
                }
            }
            Spacer(Modifier.height(InkSpace.s32))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(InkSpace.s12))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteCard(name: FavoriteName, selected: Boolean, selectionEnabled: Boolean,
    onSelect: () -> Unit, onFinalist: () -> Unit, onOpen: () -> Unit, openEnabled: Boolean,
    onFamilyCard: () -> Unit, onRemove: () -> Unit) {
    var showRecord by rememberSaveable(name.key) { mutableStateOf(false) }
    InkCard(contentPadding = PaddingValues(InkSpace.s16), spacing = InkSpace.s12,
        modifier = Modifier.testTag("favorite-${name.key}")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.s4)) {
                Text(name.fullHangul, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(name.hanja, style = MaterialTheme.typography.titleMedium, fontFamily = HanjaFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "${name.fullHangul} 이름책에서 빼기") }
        }
        Text(name.meaning, style = MaterialTheme.typography.bodyMedium)
        Text("저장 당시 ${name.score}점 · ${name.grade}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val record = name.evaluationRecord
        if (record == null) {
            Text("저장 당시 평가 기준 미상", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(record.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showRecord = !showRecord }, modifier = Modifier.testTag("favorite-record-${name.key}")) {
                Text(if (showRecord) "저장한 평가 조건 접기" else "저장한 평가 조건 보기")
            }
            if (showRecord) {
                Text(record.conditionLabel, style = MaterialTheme.typography.bodySmall)
                Text(record.provenanceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (name.familyNote.isNotBlank()) Text("우리 가족의 마음\n${name.familyNote}", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
            FilterChip(selected = name.isFinalist, onClick = onFinalist,
                leadingIcon = { if (name.isFinalist) Icon(Icons.Filled.Check, null) }, label = { Text("최종 후보") },
                modifier = Modifier.testTag("finalist-${name.key}"))
            FilterChip(selected = selected, enabled = selectionEnabled, onClick = onSelect,
                label = { Text(if (selected) "비교 선택됨" else "비교에 추가") }, modifier = Modifier.testTag("compare-${name.key}"))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
            TextButton(onClick = onFamilyCard) { Text("가족 이름 카드") }
            TextButton(onClick = onOpen, enabled = openEnabled) { Text("현재 조건으로 풀이 →") }
        }
    }
}
