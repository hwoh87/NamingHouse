package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.naminghouse.app.DiscoveredName
import com.naminghouse.app.DiscoveryGender
import com.naminghouse.app.DiscoveryMode
import com.naminghouse.app.DiscoveryPosition
import com.naminghouse.app.NameDiscoveryIndex
import com.naminghouse.app.NameDiscoveryQuery
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.Routes
import com.naminghouse.app.normalizedDiscoveryName
import com.naminghouse.app.ui.theme.InkShape
import com.naminghouse.app.ui.theme.InkSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explore the source name pool independently of saved favorites and scoring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameExplorerScreen(vm: NamingViewModel, nav: NavHostController) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var mode by rememberSaveable { mutableStateOf(DiscoveryMode.SOUND) }
    var position by rememberSaveable { mutableStateOf(DiscoveryPosition.ANY) }
    var gender by rememberSaveable { mutableStateOf(DiscoveryGender.ALL) }
    var length by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDirect by rememberSaveable { mutableStateOf(false) }
    var directInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var directError by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val finishTyping = { focus.clearFocus(); keyboard?.hide(); Unit }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pool = vm.namePool
    val db = vm.hanjaDb
    var index by remember(pool, db) { mutableStateOf<NameDiscoveryIndex?>(null) }
    var indexError by remember(pool, db) { mutableStateOf<String?>(null) }
    LaunchedEffect(pool, db) {
        if (pool == null || db == null) return@LaunchedEffect
        try {
            index = withContext(Dispatchers.Default) {
                val jobContext = currentCoroutineContext()
                NameDiscoveryIndex(pool, db) { jobContext.ensureActive() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { indexError = "이름 자료를 준비하지 못했어요. 화면을 다시 열어 주세요." }
    }
    val query = NameDiscoveryQuery(input.text, mode, position, gender, length)
    // A new query has no results until its own job completes; old names never masquerade as new hits.
    var results by remember(index, query) { mutableStateOf<List<DiscoveredName>?>(null) }
    var searchError by remember(index, query) { mutableStateOf<String?>(null) }
    LaunchedEffect(index, query) {
        val ready = index ?: return@LaunchedEffect
        try {
            results = withContext(Dispatchers.Default) {
                val jobContext = currentCoroutineContext()
                ready.search(query) { jobContext.ensureActive() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { searchError = "검색을 마치지 못했어요. 검색어를 다시 입력해 주세요." }
    }
    val openName: (String) -> Unit = { name ->
        finishTyping()
        if (vm.startHanjaFor(name)) {
            showDirect = false
            nav.navigate(Routes.INPUT) { launchSingleTop = true }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SceneTopBar("이름 둘러보기", { nav.popBackStack() })
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("name-explorer").semantics {
                stateDescription = if (results == null) "검색 중" else "검색 완료"
            },
            contentPadding = PaddingValues(horizontal = InkSpace.gutter, vertical = InkSpace.s16),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s16),
        ) {
            item {
                SceneHeading("소리와 뜻", "마음에 드는 이름부터",
                    "이름 후보 목록을 둘러보세요. 담아둔 이름 검색과는 별개이며, 성명학 평가는 한자를 찾은 뒤에 확인해요.")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
                    InkSegmented(DiscoveryMode.entries, mode, { it.label }, { mode = it })
                    OutlinedTextField(
                        value = input,
                        // Preserve composing characters and selection: filtering here can break Korean IMEs.
                        onValueChange = { input = it },
                        label = { Text(if (mode == DiscoveryMode.SOUND) "이름이나 초성" else "한자의 뜻이나 글자") },
                        placeholder = { Text(if (mode == DiscoveryMode.SOUND) "예: ㅅㅇ, 서ㅇ, 윤" else "예: 빛, 밝을, 明") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("explorer-query"),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (input.text.isNotEmpty()) IconButton(onClick = { input = TextFieldValue() }) {
                                Icon(Icons.Default.Close, "검색어 지우기")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { finishTyping() }),
                    )
                    if (mode == DiscoveryMode.SOUND) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                            DiscoveryPosition.entries.forEach { choice ->
                                FilterChip(selected = position == choice, onClick = { finishTyping(); position = choice },
                                    label = { Text(choice.label) })
                            }
                        }
                    } else {
                        Text("추천에 쓰는 한자 훈음에서 찾아요. 표시한 글자는 이름 일부에 쓸 수 있는 한자이며, 이름 전체의 뜻은 아니에요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InkSegmented(DiscoveryGender.entries, gender, { it.label }, { finishTyping(); gender = it })
                    Text("성별은 후보 목록의 분류예요. 실제 사용 비율을 뜻하지 않아요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                        listOf<Int?>(null, 1, 2, 3).forEach { choice ->
                            FilterChip(selected = length == choice, onClick = { finishTyping(); length = choice },
                                label = { Text(when (choice) { 1 -> "한 글자"; 2 -> "두 글자"; 3 -> "세 글자"; else -> "전체 길이" }) })
                        }
                    }
                    OutlinedButton(onClick = { finishTyping(); directError = null; showDirect = true },
                        modifier = Modifier.fillMaxWidth().testTag("explorer-direct"), shape = InkShape.medium) {
                        Text("목록 밖의 이름 직접 입력")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                    Text("최근 본 이름", style = MaterialTheme.typography.titleSmall)
                    if (!vm.recentNamesLoaded) {
                        Text("최근 이름을 불러오고 있어요", style = MaterialTheme.typography.bodySmall)
                    } else if (vm.recentNames.isEmpty()) {
                        Text("한자 찾기로 열어 본 이름이 여기에 남아요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                            vm.recentNames.forEach { name ->
                                FilterChip(selected = false, onClick = { openName(name) }, label = { Text(name) })
                            }
                        }
                        TextButton(onClick = { finishTyping(); vm.clearRecentNames() }) { Text("최근 본 이름 전체 지우기") }
                    }
                    vm.explorationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val error = vm.loadError ?: indexError ?: searchError
            when {
                error != null -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                index == null -> item { Text("이름 자료를 준비하고 있어요", style = MaterialTheme.typography.bodyMedium) }
                mode == DiscoveryMode.MEANING && input.text.isBlank() -> item {
                    Text("찾고 싶은 뜻이나 한자를 입력해 주세요", style = MaterialTheme.typography.bodyMedium)
                }
                results == null -> item { Text("조건에 맞는 이름을 찾고 있어요", style = MaterialTheme.typography.bodyMedium) }
                results!!.isEmpty() -> item {
                    EmptyNames("일치하는 이름이 없어요", "검색어를 줄이거나 성별·글자 수 조건을 바꿔 보세요. 목록 밖의 이름도 직접 입력할 수 있어요.", "조건 모두 지우기") {
                        finishTyping(); input = TextFieldValue(); mode = DiscoveryMode.SOUND
                        gender = DiscoveryGender.ALL; length = null; position = DiscoveryPosition.ANY
                    }
                }
                else -> {
                    item { Text("후보 목록 ${pool?.names?.size ?: 0}개 중 ${results!!.size}개 · 평가 전 이름",
                        modifier = Modifier.testTag("explorer-result-count"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(results!!, key = { it.poolName.name }) { found ->
                        DiscoveryNameRow(found, onOpen = { openName(found.poolName.name) }, onRelated = {
                            finishTyping()
                            input = TextFieldValue(found.poolName.name.takeLast(1))
                            mode = DiscoveryMode.SOUND
                            position = DiscoveryPosition.END
                            length = null
                            scope.launch { listState.animateScrollToItem(1) }
                        })
                    }
                }
            }
        }
    }
    if (showDirect) {
        val submit = {
            val name = normalizedDiscoveryName(directInput.text)
            if (name == null) directError = "한글이 완성된 이름을 1~3글자로 입력해 주세요"
            else openName(name)
        }
        AlertDialog(
            modifier = Modifier.imePadding(),
            properties = DialogProperties(decorFitsSystemWindows = false),
            onDismissRequest = { showDirect = false; directError = null },
            title = { Text("직접 정한 이름") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                    Text("성을 빼고 이름만 입력해 주세요. 이 이름의 한자 후보를 찾아볼게요.")
                    OutlinedTextField(value = directInput, onValueChange = { directInput = it; directError = null },
                        label = { Text("이름 1~3글자") }, singleLine = true, isError = directError != null,
                        modifier = Modifier.fillMaxWidth().testTag("explorer-direct-input"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }))
                    (directError ?: vm.explorationError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = { submit() }) { Text("한자 찾기") } },
            dismissButton = { TextButton(onClick = { showDirect = false; directError = null }) { Text("닫기") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryNameRow(found: DiscoveredName, onOpen: () -> Unit, onRelated: () -> Unit) {
    val name = found.poolName.name
    InkCard(modifier = Modifier.testTag("discovered-$name"), contentPadding = PaddingValues(InkSpace.s16)) {
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (found.matchingHanja.isNotEmpty()) {
            Text("뜻이 맞는 한자 후보", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            found.matchingHanja.take(3).forEach { entry ->
                val reading = entry.readings.filter { name.contains(it) }.joinToString("·")
                Text("${entry.char} · ${entry.meaning} · 독음 $reading", style = MaterialTheme.typography.bodyMedium)
            }
            if (found.matchingHanja.size > 3) Text("이름에 쓸 수 있는 일치 한자 ${found.matchingHanja.size}자",
                style = MaterialTheme.typography.labelSmall)
            Text("다음 단계의 추천 조합은 이 한자와 다를 수 있어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
            OutlinedButton(onClick = onOpen, shape = InkShape.medium) { Text("이 이름의 한자 찾기") }
            TextButton(onClick = onRelated) { Text("끝 글자 ‘${name.last()}’ 더 찾기") }
        }
    }
}
