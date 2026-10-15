package com.naminghouse.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.naminghouse.app.ui.FavoriteBackupExport
import com.naminghouse.app.ui.rememberFavoriteBackupExport
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoriteBackupExportTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var export: FavoriteBackupExport
    private val name = FavoriteName("김", "도윤", "金度侖", 94, "대길", "너그러운 사람", "선택해 포함한 메모", true)

    private class PickerRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var launches = 0
        var requestCode = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches++
            this.requestCode = requestCode
        }
        fun complete(uri: Uri?) {
            dispatchResult(requestCode, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                uri?.let { Intent().setData(it) })
        }
    }

    @Test fun exportSurvivesPickerRestorationAndOnlyLaunchesOnce() {
        val registry = PickerRegistry()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) { export = rememberFavoriteBackupExport() }
        }
        compose.runOnIdle { repeat(2) { export.begin(listOf(name), true, false) } }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val before = export.state
        val path = requireNotNull(before.pendingPath)
        assertTrue(path.startsWith(compose.activity.noBackupFilesDir.absolutePath + File.separator))
        assertEquals(listOf(name), File(path).inputStream().use(FavoriteBackup::read))

        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertNotSame(before, export.state)
            assertEquals(path, export.state.pendingPath)
            assertTrue(export.state.busy)
        }
        val output = File.createTempFile("backup-test-", ".nhbackup", compose.activity.cacheDir)
        try {
            compose.runOnIdle { registry.complete(Uri.fromFile(output)) }
            compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
            assertEquals(listOf(name), output.inputStream().use(FavoriteBackup::read))
            assertEquals(1, registry.launches)
        } finally { output.delete(); File(path).delete() }
    }

    @Test fun restorationDuringCopyRestartsAtTheSameDestination() {
        val registry = PickerRegistry()
        val restoration = StateRestorationTester(compose)
        val attempts = AtomicInteger(0)
        val partialWritten = AtomicBoolean(false)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                export = rememberFavoriteBackupExport { source, uri ->
                    val bytes = source.readBytes()
                    File(requireNotNull(uri.path)).outputStream().use { output ->
                        if (attempts.incrementAndGet() == 1) {
                            output.write(bytes, 0, 12)
                            output.flush()
                            partialWritten.set(true)
                            awaitCancellation()
                        } else output.write(bytes)
                    }
                }
            }
        }
        compose.runOnIdle { export.begin(listOf(name), true, false) }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val path = requireNotNull(export.state.pendingPath)
        val output = File.createTempFile("backup-rotation-", ".nhbackup", compose.activity.cacheDir)
        try {
            compose.runOnIdle { registry.complete(Uri.fromFile(output)) }
            compose.waitUntil(5_000) { partialWritten.get() }
            assertEquals(12L, output.length())
            assertEquals(Uri.fromFile(output).toString(), export.state.destinationUri)
            assertTrue(File(path).exists())
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
            assertEquals(2, attempts.get())
            assertEquals(1, registry.launches)
            assertEquals(listOf(name), output.inputStream().use(FavoriteBackup::read))
        } finally { output.delete(); File(path).delete() }
    }

    @Test fun cancellingPickerRemovesTheStagedBackup() {
        val registry = PickerRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) { export = rememberFavoriteBackupExport() }
        }
        compose.runOnIdle { export.begin(listOf(name), false, false) }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val path = requireNotNull(export.state.pendingPath)
        compose.runOnIdle { registry.complete(null) }
        compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
    }

    @Test fun failedOutputClearsPendingStateAndKeepsOtherFiles() {
        val registry = PickerRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) { export = rememberFavoriteBackupExport() }
        }
        compose.runOnIdle { export.begin(listOf(name), false, false) }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val path = requireNotNull(export.state.pendingPath)
        compose.runOnIdle { registry.complete(Uri.fromFile(compose.activity.cacheDir)) }
        compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
        assertNotNull(export.state.error)
        assertTrue(compose.activity.cacheDir.isDirectory)
    }
}
