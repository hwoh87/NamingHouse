package com.naminghouse.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.naminghouse.app.FavoriteName
import com.naminghouse.app.ui.theme.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyNameSheet(name: FavoriteName, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable(name.fullHangul, name.hanja) { mutableStateOf(name.familyNote) }
    var busy by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = InkSpace.gutter)
            .padding(bottom = InkSpace.s24), verticalArrangement = Arrangement.spacedBy(InkSpace.s16)) {
            SceneHeading("가족과 함께 고르는 이름", "${name.fullHangul}의 이름 카드", "고른 이유를 적고, 가족에게 보여주세요.")
            OutlinedTextField(value = note, onValueChange = { note = it.take(160) },
                label = { Text("우리가 고른 이유") }, minLines = 2, maxLines = 4,
                supportingText = { Text("${note.length}/160 · 이 기기에 저장돼요. 공유하면 카드에 포함돼요.") },
                modifier = Modifier.fillMaxWidth(), shape = InkShape.medium)
            TextButton(onClick = { focus.clearFocus(); keyboard?.hide() }) { Text("카드 미리보기") }
            Surface(shape = InkShape.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().testTag("family-name-card").drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) {
                Column(Modifier.padding(InkSpace.s24), verticalArrangement = Arrangement.spacedBy(InkSpace.s16)) {
                    Text("우리 가족의 이름 이야기", style = MaterialTheme.typography.labelLarge)
                    Text(name.fullHangul, style = MaterialTheme.typography.displaySmall)
                    Text(name.hanja, fontFamily = HanjaFamily, style = MaterialTheme.typography.headlineSmall)
                    Text(name.meaning, style = MaterialTheme.typography.bodyLarge)
                    if (note.isNotBlank()) {
                        HorizontalDivider()
                        Text("이 이름에 담은 우리 마음", style = MaterialTheme.typography.labelLarge)
                        Text(note, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("네이밍하우스 · 오래 부를 이름을 함께", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s12)) {
                OutlinedButton(onClick = { onSave(note); onDismiss() }, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("이유 저장") }
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            val uri = withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                                val file = File.createTempFile("family_", ".png", dir)
                                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            }
                            onSave(note)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "가족 이름 카드 공유"))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, "카드를 준비하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                        } finally { busy = false }
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("저장하고 공유") }
            }
        }
    }
}
