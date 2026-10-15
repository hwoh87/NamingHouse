package com.naminghouse.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CertificateExportState(path: String? = null, uri: String? = null) {
    var pendingPath by mutableStateOf(path)
    var destinationUri by mutableStateOf(uri)
    var preparing by mutableStateOf(false)
    val busy: Boolean get() = preparing || pendingPath != null || destinationUri != null

    companion object {
        val Saver = listSaver<CertificateExportState, String>(
            save = { listOf(it.pendingPath.orEmpty(), it.destinationUri.orEmpty()) },
            restore = { CertificateExportState(it[0].ifEmpty { null }, it[1].ifEmpty { null }) },
        )
    }
}

internal class CertificateExport(
    val state: CertificateExportState,
    val begin: (fileName: String, write: suspend (File) -> Unit) -> Unit,
)

// A recreated composition may retry the same copy after cancelling its predecessor.
private val pdfCopyMutex = Mutex()

/** Register unconditionally, before looking at the selected evaluation (which can be lost on restore). */
@Composable
internal fun rememberCertificateExport(): CertificateExport {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = CertificateExportState.Saver) { CertificateExportState() }
    // Unlike cacheDir, this directory is not automatically evicted while the document picker is open.
    // It is also excluded from OS backup. Completed/cancelled requests are removed immediately.
    val files = remember(appContext) { PendingPdfFiles(File(appContext.noBackupFilesDir, "pending-certificates")) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) {
            val path = state.pendingPath
            state.pendingPath = null
            state.destinationUri = null
            scope.launch(NonCancellable + Dispatchers.IO) { files.discard(path) }
        } else {
            // Keep both values until the copy finishes. A recreated Activity can restart it.
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            state.destinationUri = uri.toString()
        }
    }

    LaunchedEffect(files) {
        withContext(Dispatchers.IO) { files.cleanOld(state.pendingPath) }
    }

    val pendingPath = state.pendingPath
    val destination = state.destinationUri
    LaunchedEffect(pendingPath, destination) {
        if (destination == null) return@LaunchedEffect
        val uri = Uri.parse(destination)
        try {
            withContext(Dispatchers.IO) {
                pdfCopyMutex.withLock {
                    val path = pendingPath ?: throw IOException("저장할 감명서를 찾지 못했습니다")
                    files.completed(path)
                    val jobContext = currentCoroutineContext()
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use {
                        files.copyTo(path, it) { jobContext.ensureActive() }
                    } ?: throw IOException("저장 위치를 열지 못했습니다")
                }
            }
            Toast.makeText(context, "감명서 PDF를 저장했습니다", Toast.LENGTH_SHORT).show()
        } catch (cancelled: CancellationException) {
            // Preserve the staged document and destination for the next Activity instance.
            throw cancelled
        } catch (_: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
            }
            Toast.makeText(context, "PDF를 저장하지 못했습니다. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
        }
        state.pendingPath = null
        state.destinationUri = null
        withContext(NonCancellable + Dispatchers.IO) {
            files.discard(pendingPath)
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }

    return CertificateExport(state) { fileName, write ->
        if (!state.busy) {
            state.preparing = true // Set before launching: repeated taps cannot open two pickers.
            scope.launch {
                var staged: File? = null
                var launched = false
                try {
                    val file = files.create()
                    staged = file
                    write(file)
                    withContext(Dispatchers.IO) { files.completed(file.absolutePath) }
                    state.pendingPath = file.absolutePath
                    launcher.launch(fileName)
                    launched = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, "감명서를 만들지 못했습니다. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
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
