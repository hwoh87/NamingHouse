package com.naminghouse.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.naminghouse.app.FavoriteBackup
import com.naminghouse.app.FavoriteBackupException
import com.naminghouse.app.FavoriteName
import com.naminghouse.app.PendingFavoriteBackupFiles
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class FavoriteBackupExportState(path: String? = null, uri: String? = null) {
    var pendingPath by mutableStateOf(path)
    var destinationUri by mutableStateOf(uri)
    var preparing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    val busy: Boolean get() = preparing || pendingPath != null || destinationUri != null

    companion object {
        val Saver = listSaver<FavoriteBackupExportState, String>(
            save = { listOf(it.pendingPath.orEmpty(), it.destinationUri.orEmpty()) },
            restore = { FavoriteBackupExportState(it[0].ifEmpty { null }, it[1].ifEmpty { null }) },
        )
    }
}

internal class FavoriteBackupExport(
    val state: FavoriteBackupExportState,
    val begin: (items: List<FavoriteName>, includeNotes: Boolean, includeBirthDetails: Boolean) -> Unit,
)

private val backupCopyMutex = Mutex()

/** URI와 단계 파일을 완료 때까지 유지해 화면 재생성 시 같은 파일에 처음부터 다시 쓴다. */
@Composable
internal fun rememberFavoriteBackupExport(copyOverride: (suspend (File, Uri) -> Unit)? = null): FavoriteBackupExport {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = FavoriteBackupExportState.Saver) { FavoriteBackupExportState() }
    val files = remember(context) { PendingFavoriteBackupFiles(File(context.noBackupFilesDir, "pending-namebooks")) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            val path = state.pendingPath
            state.pendingPath = null
            state.destinationUri = null
            scope.launch(NonCancellable + Dispatchers.IO) { files.discard(path) }
        } else {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            state.destinationUri = uri.toString()
        }
    }

    LaunchedEffect(files) { withContext(Dispatchers.IO) { files.cleanOld(state.pendingPath) } }

    val pendingPath = state.pendingPath
    val destination = state.destinationUri
    LaunchedEffect(pendingPath, destination) {
        if (destination == null) return@LaunchedEffect
        val uri = Uri.parse(destination)
        try {
            withContext(Dispatchers.IO) {
                backupCopyMutex.withLock {
                    val path = pendingPath ?: throw IOException("Missing staged backup")
                    val file = files.completed(path)
                    if (copyOverride != null) copyOverride(file, uri) else {
                        val job = currentCoroutineContext()
                        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            files.copyTo(path, output) { job.ensureActive() }
                        } ?: throw IOException("Cannot open selected output")
                    }
                }
            }
            state.notice = "이름책 파일을 저장했어요. 이 앱의 ‘파일 가져오기’에서 다시 불러올 수 있어요."
        } catch (cancelled: CancellationException) {
            // 재생성된 Activity가 다시 복사할 수 있도록 단계 파일과 목적지를 남긴다.
            throw cancelled
        } catch (_: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                backupCopyMutex.withLock {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    files.discard(pendingPath)
                }
            }
            state.error = "파일을 저장하지 못했어요. 저장 위치를 확인하고 다시 시도해 주세요."
        }
        state.pendingPath = null
        state.destinationUri = null
        withContext(NonCancellable + Dispatchers.IO) {
            files.discard(pendingPath)
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
    }

    return FavoriteBackupExport(state) { items, includeNotes, includeBirthDetails ->
        if (!state.busy) {
            state.preparing = true
            state.error = null
            state.notice = null
            val snapshot = items.toList()
            scope.launch {
                var staged: File? = null
                var launched = false
                try {
                    val file = withContext(Dispatchers.IO) {
                        files.create(FavoriteBackup.encode(snapshot, includeNotes, includeBirthDetails)).also { staged = it }
                    }
                    state.pendingPath = file.absolutePath
                    launcher.launch("네이밍하우스-이름책-${LocalDate.now()}.${FavoriteBackup.EXTENSION}")
                    launched = true
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (invalid: FavoriteBackupException) { state.error = invalid.userMessage
                } catch (_: Exception) { state.error = "보관할 파일을 준비하지 못했어요. 다시 시도해 주세요."
                } finally {
                    if (!launched) {
                        state.pendingPath = null
                        withContext(NonCancellable + Dispatchers.IO) { files.discard(staged?.absolutePath) }
                    }
                    state.preparing = false
                }
            }
        }
    }
}
