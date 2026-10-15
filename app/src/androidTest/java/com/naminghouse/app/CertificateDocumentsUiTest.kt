package com.naminghouse.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.CertificateExport
import com.naminghouse.app.ui.rememberCertificateExport
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real SAF integration: requires the system DocumentsUI app with a local Downloads location. */
class CertificateDocumentsUiTest {
    // ui-test-manifest supplies this empty host; no MainActivity or paid entitlement is involved.
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun pdfSavedThroughDocumentsUiSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        val automation = instrumentation.uiAutomation
        val oldFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        val host = ExportHost()
        val createdActivity = AtomicReference<ComponentActivity?>()
        val stagedPath = AtomicReference<String?>()
        val preparations = AtomicInteger()
        val source = smallPdf()
        val filename = "naminghouse-documents-test-${UUID.randomUUID()}.pdf"
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity.javaClass == ComponentActivity::class.java) {
                    val recreated = activity as ComponentActivity
                    createdActivity.set(recreated)
                    // Install after onCreate so the real SavedStateRegistry has been restored.
                    recreated.window.decorView.post {
                        if (!recreated.isDestroyed) host.install(recreated)
                    }
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            activityRule.scenario.onActivity { host.install(it) }
            waitFor(automation, "the export host to compose") { host.latest.get() != null }
            val original = requireNotNull(host.latest.get())
            instrumentation.runOnMainSync {
                original.export.begin(filename) { file ->
                    preparations.incrementAndGet()
                    file.writeBytes(source)
                    stagedPath.set(file.absolutePath)
                }
            }

            waitFor(automation, "the real document picker") {
                documentsRoot(automation) != null && stagedPath.get() != null
            }
            val staged = File(requireNotNull(stagedPath.get()))
            assertArrayEquals("The PDF is complete before choosing a destination", source, staged.readBytes())
            chooseDownloads(automation)
            setFilename(automation, filename)

            // Recreate the actual stopped Activity while the system picker remains in front.
            // This intentionally does not simulate process death or replace ActivityResultRegistry.
            // Scenario.recreate() first forces RESUMED, which cannot happen behind DocumentsUI.
            // Ask the real Activity to recreate without moving it in front of the picker.
            activityRule.scenario.onActivity { it.recreate() }
            waitFor(automation, "a new host Activity behind the picker") {
                createdActivity.get()?.let { it !== original.activity } == true &&
                    documentsRoot(automation) != null
            }
            assertTrue("Recreation must retain the completed staging file", staged.isFile)
            assertEquals("Recreation must not regenerate the PDF", 1, preparations.get())

            clickDocumentNode(automation, "the Save button") {
                it.viewIdResourceName == "android:id/button1" &&
                    it.className?.toString()?.endsWith("Button") == true
            }
            waitFor(automation, "the returned content URI and completed export", timeoutMs = 20_000) {
                val current = host.latest.get()
                host.destination.get() != null && current != null &&
                    current.activity !== original.activity && !current.busy && !staged.exists()
            }

            val current = requireNotNull(host.latest.get())
            assertNotSame(original.export.state, current.export.state)
            assertEquals("The saved pending path must be restored", staged.absolutePath, host.restoredPath.get())
            assertEquals(1, preparations.get())
            assertFalse("The completed staging file must be removed", staged.exists())
            val uri = Uri.parse(requireNotNull(host.destination.get()))
            assertEquals("The real provider must return a content URI", "content", uri.scheme)
            assertTrue(DocumentsContract.isDocumentUri(context, uri))

            val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            assertArrayEquals("The provider file must contain the exact prepared PDF", source, actual)
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                assertTrue(it.moveToFirst())
                assertEquals(filename, it.getString(0))
            } ?: error("The saved document has no metadata")
            assertReadablePages(uri)
            assertTrue("Remove only this test's saved document", DocumentsContract.deleteDocument(context.contentResolver, uri))
            host.destination.set(null)
        } finally {
            // Failures must not leave a picker, test PDF, or lifecycle hook behind for other tests.
            if (documentsRoot(automation) != null) {
                automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            host.destination.get()?.let { destination ->
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(destination)) }
            }
            stagedPath.get()?.let { File(it).delete() }
            application.unregisterActivityLifecycleCallbacks(callbacks)
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
        }
    }

    private data class HostSnapshot(
        val activity: ComponentActivity,
        val export: CertificateExport,
        val busy: Boolean,
    )

    private class ExportHost {
        val latest = AtomicReference<HostSnapshot?>()
        val destination = AtomicReference<String?>()
        val restoredPath = AtomicReference<String?>()
        private var firstActivity: ComponentActivity? = null

        fun install(activity: ComponentActivity) {
            if (firstActivity == null) firstActivity = activity
            activity.setContent {
                // The helper is registered even though the host has no selected NameEvaluation.
                val export = rememberCertificateExport()
                val path = export.state.pendingPath
                val uri = export.state.destinationUri
                val busy = export.state.busy
                SideEffect {
                    latest.set(HostSnapshot(activity, export, busy))
                    if (uri != null) destination.compareAndSet(null, uri)
                    if (activity !== firstActivity && path != null) restoredPath.compareAndSet(null, path)
                }
                Text(if (busy) "PDF 저장 중" else "PDF 저장 준비")
            }
        }
    }

    private fun smallPdf(): ByteArray = ByteArrayOutputStream().use { output ->
        val document = PdfDocument()
        try {
            listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(320, 240, index + 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(20f, 20f, 100f, 100f, Paint().apply { this.color = color })
                page.canvas.drawText("Prepared PDF page ${index + 1}", 20f, 140f, Paint().apply {
                    this.color = Color.BLACK
                    textSize = 16f
                })
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally { document.close() }
        output.toByteArray()
    }

    private fun assertReadablePages(uri: Uri) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        requireNotNull(resolver.openFileDescriptor(uri, "r")).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                    renderer.openPage(index).use { page ->
                        assertEquals(320, page.width)
                        assertEquals(240, page.height)
                        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertEquals("Page ${index + 1} must render its original content", color, bitmap.getPixel(40, 40))
                        } finally { bitmap.recycle() }
                    }
                }
            }
        }
    }

    private fun chooseDownloads(automation: UiAutomation) {
        clickDocumentNode(automation, "the location drawer") { node ->
            val description = node.contentDescription?.toString().orEmpty()
            description in setOf("Show roots", "Open navigation drawer", "루트 표시", "탐색 메뉴 열기") ||
                (node.className?.toString() == "android.widget.ImageButton" &&
                    node.parent?.viewIdResourceName?.endsWith("/toolbar") == true)
        }
        clickDocumentNode(automation, "Downloads in the location drawer") { node ->
            node.text?.toString() in setOf("Downloads", "다운로드") &&
                node.viewIdResourceName == "android:id/title"
        }
        waitFor(automation, "the Downloads save location") {
            val nodes = nodes(documentsRoot(automation))
            nodes.any { it.isVisibleToUser && it.text?.toString() in setOf("Downloads", "다운로드") } &&
                nodes.any { isFilename(it) && it.isVisibleToUser }
        }
    }

    private fun setFilename(automation: UiAutomation, filename: String) {
        waitFor(automation, "the document filename field") {
            val field = nodes(documentsRoot(automation)).firstOrNull { isFilename(it) && it.isVisibleToUser }
                ?: return@waitFor false
            if (field.text?.toString() == filename) return@waitFor true
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
            })
            false
        }
    }

    private fun isFilename(node: AccessibilityNodeInfo): Boolean =
        node.viewIdResourceName == "android:id/title" && node.className?.toString() == "android.widget.EditText"

    private fun clickDocumentNode(
        automation: UiAutomation,
        description: String,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ) {
        waitFor(automation, description) {
            val node = nodes(documentsRoot(automation)).firstOrNull {
                it.isVisibleToUser && it.isEnabled && predicate(it)
            } ?: return@waitFor false
            var clickable: AccessibilityNodeInfo? = node
            repeat(5) {
                val current = clickable ?: return@waitFor false
                if (current.isClickable) return@waitFor current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickable = current.parent
            }
            false
        }
    }

    private fun documentsRoot(automation: UiAutomation): AccessibilityNodeInfo? =
        automation.rootInActiveWindow?.takeIf { it.packageName?.toString()?.contains("documentsui") == true }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val result = ArrayList<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty() && result.size < 400) {
            val current = pending.removeFirst()
            result.add(current)
            repeat(current.childCount) { index -> current.getChild(index)?.let(pending::addLast) }
        }
        return result
    }

    private fun waitFor(automation: UiAutomation, description: String, timeoutMs: Long = 10_000, ready: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (ready()) return
            SystemClock.sleep(100)
        }
        val tree = nodes(automation.rootInActiveWindow).joinToString("\n") {
            "${it.className} id=${it.viewIdResourceName} text=${it.text} description=${it.contentDescription}"
        }
        throw AssertionError("Timed out waiting for $description. Active accessibility tree:\n$tree")
    }
}
