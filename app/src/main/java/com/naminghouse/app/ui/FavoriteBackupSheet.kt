package com.naminghouse.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.FavoriteBackup
import com.naminghouse.app.FavoriteBackupException
import com.naminghouse.app.FavoriteImportPreview
import com.naminghouse.app.FavoriteImportResult
import com.naminghouse.app.FavoriteName
import com.naminghouse.app.shareText
import com.naminghouse.app.ui.theme.InkSpace
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 파일 선택 권한은 시스템 문서 선택기에 맡기고, 가져오기는 확인 버튼 이후에만 저장한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteBackupSheet(items: List<FavoriteName>, onImport: suspend (List<FavoriteName>) -> FavoriteImportResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by rememberSaveable { mutableStateOf(items.isEmpty()) }
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var includeBirthDetails by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<List<FavoriteName>?>(null) }
    val export = rememberFavoriteBackupExport()
    val working = busy || export.state.busy
    val operationInProgress by rememberUpdatedState(working)
    val preview = imported?.let { FavoriteBackup.preview(it, items) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null; notice = null; imported = null
            try {
                imported = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(FavoriteBackup::read)
                        ?: throw IOException("Cannot open selected input")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (invalid: FavoriteBackupException) { error = invalid.userMessage
            } catch (_: Exception) { error = "파일을 읽지 못했어요. 파일 접근 권한과 저장 위치를 확인해 주세요."
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!working) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden || !operationInProgress }),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = InkSpace.gutter)
            .padding(bottom = InkSpace.s24).testTag("favorite-backup"), verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
            SceneHeading("우리 가족의 이름책 보관", "백업·가져오기", "이름책을 파일로 보관하거나, 전에 보관한 이름을 다시 담아요.")
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s8)) {
                FilterChip(selected = !importing, enabled = !working, onClick = { importing = false; error = null; notice = null }, label = { Text("파일로 보관") })
                FilterChip(selected = importing, enabled = !working, onClick = { importing = true; error = null; notice = null }, label = { Text("파일 가져오기") })
            }
            if (!importing) {
                Text("전체 이름 ${items.size}개 · 최종 후보 ${items.count { it.isFinalist }}개", style = MaterialTheme.typography.titleMedium)
                Text("이름·한자·뜻·최종 후보와 저장 점수를 보관해요. 평가일과 학파 등 기록의 출처도 함께 남아요.", style = MaterialTheme.typography.bodyMedium)
                BackupPrivacyOption(includeNotes, { includeNotes = it }, "가족 메모 포함", !working, "backup-include-notes")
                BackupPrivacyOption(includeBirthDetails, { includeBirthDetails = it }, "출생정보 포함", !working, "backup-include-birth")
                Text(if (includeBirthDetails) "저장된 출생일시·성별·지역 정보가 파일에 포함돼요. 파일을 건네면 이 정보도 함께 전달돼요."
                    else "출생일시·성별·지역 정보는 제외해요. 나중에 같은 출생 조건으로 다시 계산하려면 정보를 다시 입력해야 해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("암호화되지 않은 복원 전용 파일(.${FavoriteBackup.EXTENSION})이에요. 네이밍하우스에서 가져와야 이름을 다시 담을 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled = items.isNotEmpty() && !working, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("backup-export"), onClick = {
                    error = null; notice = null
                    export.begin(items, includeNotes, includeBirthDetails)
                }) { Text("저장 위치 선택") }
                if (items.isEmpty()) Text("아직 담아둔 이름이 없어요. 파일 가져오기는 지금도 사용할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("네이밍하우스에서 보관한 .${FavoriteBackup.EXTENSION} 파일을 선택해 주세요. 최대 1MB, ${FavoriteBackup.MAX_NAMES}개 이름까지 읽을 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("backup-import-file"),
                    onClick = {
                        error = null; notice = null
                        try { importLauncher.launch(arrayOf("*/*")) }
                        catch (_: Exception) { error = "파일 선택기를 열지 못했어요. 다시 시도해 주세요." }
                    }) { Text("가져올 파일 선택") }
                if (preview != null) {
                    FavoriteImportConfirmation(preview, busy = working, onConfirm = {
                        val confirmed = imported ?: return@FavoriteImportConfirmation
                        busy = true; error = null; notice = null
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { onImport(confirmed) }
                                notice = "${result.added}개 이름을 담았어요. 이미 있던 ${result.skipped}개 이름의 메모와 기록은 그대로예요."
                                imported = null
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (invalid: FavoriteBackupException) { error = invalid.userMessage
                            } catch (_: Exception) { error = "이름책에 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
                            } finally { busy = false }
                        }
                    })
                }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup-error")) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("backup-notice")) }
            if (!importing) {
                export.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup-export-error")) }
                export.state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("backup-export-notice")) }
            }
            TextButton(enabled = !working, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        }
    }
}

@Composable
internal fun FavoriteImportConfirmation(preview: FavoriteImportPreview, busy: Boolean, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s12), modifier = Modifier.testTag("backup-import-preview")) {
        HorizontalDivider()
        Text("추가 ${preview.additions.size}개 · 이미 저장됨 ${preview.duplicates.size}개", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("같은 이름과 한자가 이미 있으면 건너뛰어요. 기존 메모·최종 후보·평가 기록은 바꾸지 않아요.", style = MaterialTheme.typography.bodyMedium)
        if (preview.additions.isNotEmpty()) {
            Text("추가할 이름에 가족 메모 ${preview.additions.count { it.familyNote.isNotBlank() }}개 · 출생정보 ${preview.additions.count { it.evaluationRecord?.birth != null }}개가 포함돼요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("점수는 파일에 저장된 기록이에요. 현재 입력 조건의 풀이는 이름을 담은 뒤 다시 확인할 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        preview.additions.take(20).forEach { name ->
            Text("${name.fullHangul} · ${name.hanja}${if (name.isFinalist) " · 최종 후보" else ""}", style = MaterialTheme.typography.bodyMedium)
        }
        if (preview.additions.size > 20) Text("그 외 ${preview.additions.size - 20}개 이름", style = MaterialTheme.typography.bodySmall)
        if (preview.additions.isEmpty()) Text(if (preview.duplicates.isEmpty()) "파일에 담긴 이름이 없어요." else "모든 이름이 이미 이름책에 있어요.", style = MaterialTheme.typography.bodyMedium)
        Button(enabled = preview.additions.isNotEmpty() && !busy, onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("backup-import-confirm")) { Text("${preview.additions.size}개 이름 추가") }
    }
}

@Composable
private fun BackupPrivacyOption(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, enabled: Boolean, tag: String) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 공유 앱으로 보내는 문자열과 사용자가 보는 미리보기가 정확히 같다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteShareSheet(items: List<FavoriteName>, onDismiss: () -> Unit, onShare: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val text = remember(items, includeNotes) { shareText(items, includeNotes) }
    val canShare = text.toByteArray(Charsets.UTF_8).size <= 200_000
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = InkSpace.gutter).padding(bottom = InkSpace.s24),
            verticalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
            SceneHeading("가족에게 보여주기 전에", "이름 공유 미리보기", "지금 보이는 ${items.size}개 이름을 아래 내용으로 공유해요. 출생정보는 포함하지 않아요.")
            BackupPrivacyOption(includeNotes, { includeNotes = it }, "가족 메모 포함", true, "share-include-notes")
            if (canShare) {
                InkCard { Text(text, modifier = Modifier.testTag("favorite-share-preview"), style = MaterialTheme.typography.bodyMedium) }
            } else Text("공유할 내용이 너무 많아요. 이름책에서 검색하거나 최종 후보만 골라 다시 공유해 주세요.", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = items.isNotEmpty() && canShare, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("favorite-share-send"), onClick = {
                try {
                    if (onShare != null) onShare(text) else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    }, "이름 공유"))
                } catch (_: Exception) { error = "공유할 앱을 열지 못했어요. 다시 시도해 주세요." }
            }) { Text("이 내용으로 공유") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        }
    }
}
