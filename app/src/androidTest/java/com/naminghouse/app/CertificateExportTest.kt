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
import com.naminghouse.app.ui.CertificateExport
import com.naminghouse.app.ui.rememberCertificateExport
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise saved-state restoration and ActivityResult delivery without depending on a picker app. */
class CertificateExportTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var export: CertificateExport

    private class PickerRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var launches = 0
        var requestCode = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) {
            launches++
            this.requestCode = requestCode
        }
        fun complete(uri: Uri?) {
            dispatchResult(requestCode, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                uri?.let { Intent().setData(it) })
        }
    }

    @Test fun completedPdfSurvivesRestorationAndDuplicateTapsLaunchOnlyOnce() {
        val registry = PickerRegistry()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                // No NameEvaluation or Bitmap exists in the restored composition.
                export = rememberCertificateExport()
            }
        }
        val bytes = "%PDF-1.4\nprepared before picker".toByteArray()
        var preparations = 0
        compose.runOnIdle {
            repeat(2) { export.begin("test.pdf") { preparations++; it.writeBytes(bytes) } }
        }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val before = export.state
        val path = requireNotNull(before.pendingPath)
        assertEquals(1, preparations)
        assertArrayEquals(bytes, File(path).readBytes())

        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertNotSame(before, export.state)
            assertEquals(path, export.state.pendingPath)
            assertTrue(export.state.busy)
        }
        val output = File.createTempFile("export-test-", ".pdf", compose.activity.cacheDir)
        try {
            compose.runOnIdle { registry.complete(Uri.fromFile(output)) }
            compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
            assertArrayEquals(bytes, output.readBytes())
            assertEquals(1, registry.launches)
        } finally { output.delete(); File(path).delete() }
    }

    @Test fun cancellingThePickerDeletesTheStagedDocument() {
        val registry = PickerRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                export = rememberCertificateExport()
            }
        }
        compose.runOnIdle { export.begin("test.pdf") { it.writeText("%PDF-1.4\ntest") } }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val path = requireNotNull(export.state.pendingPath)
        compose.runOnIdle { registry.complete(null) }
        compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
    }

    @Test fun failedPreparationDoesNotLaunchThePickerOrKeepPartialFiles() {
        val registry = PickerRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                export = rememberCertificateExport()
            }
        }
        var staged: File? = null
        compose.runOnIdle {
            export.begin("test.pdf") {
                staged = it
                it.writeText("partial")
                throw java.io.IOException("injected write failure")
            }
        }
        compose.waitUntil(5_000) { staged != null && !export.state.busy }
        assertFalse(staged!!.exists())
        assertEquals(0, registry.launches)
    }

    @Test fun anUnwritableDestinationClearsThePendingRequest() {
        val registry = PickerRegistry()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                export = rememberCertificateExport()
            }
        }
        compose.runOnIdle { export.begin("test.pdf") { it.writeText("%PDF-1.4\ntest") } }
        compose.waitUntil(5_000) { registry.launches == 1 && !export.state.preparing }
        val path = requireNotNull(export.state.pendingPath)
        // An existing directory cannot be opened as a PDF output stream.
        compose.runOnIdle { registry.complete(Uri.fromFile(compose.activity.cacheDir)) }
        compose.waitUntil(5_000) { !export.state.busy && !File(path).exists() }
        assertTrue(compose.activity.cacheDir.isDirectory)
    }
}
