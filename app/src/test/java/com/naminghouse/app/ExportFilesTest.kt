package com.naminghouse.app

import com.naminghouse.app.ui.PendingPdfFiles
import com.naminghouse.app.ui.createSharedImageFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun repeatedImageExportsKeepIndependentContents() {
        val first = createSharedImageFile(temp.root).apply { writeText("first Hanja") }
        val second = createSharedImageFile(temp.root).apply { writeText("second Hanja") }
        assertNotEquals(first.canonicalPath, second.canonicalPath)
        assertEquals("first Hanja", first.readText())
        assertEquals("second Hanja", second.readText())
    }

    @Test fun aNewOwnerCanCopyACompletedPdfFromItsSavedPath() {
        val directory = temp.newFolder("pending")
        val original = PendingPdfFiles(directory).create().apply { writeText("%PDF-1.4\ncomplete document") }
        val restored = PendingPdfFiles(directory)
        val output = ByteArrayOutputStream()
        restored.copyTo(original.absolutePath, output)
        assertArrayEquals(original.readBytes(), output.toByteArray())
        restored.discard(original.absolutePath)
        assertFalse(original.exists())
    }

    @Test fun incompleteAndOutsideFilesAreRejectedWithoutDeletingOtherFiles() {
        val directory = temp.newFolder("pending")
        val files = PendingPdfFiles(directory)
        val incomplete = files.create()
        val outside = temp.newFile("certificate_other.pdf").apply { writeText("%PDF-1.4") }
        listOf(incomplete, outside).forEach {
            try { files.completed(it.absolutePath); fail("Incomplete or unowned file accepted") }
            catch (_: IOException) { }
        }
        files.discard(outside.absolutePath)
        assertTrue(outside.exists())
    }

    @Test fun cleaningExpiredFilesPreservesTheActiveRequestAndUnrelatedFiles() {
        val directory = temp.newFolder("pending")
        val files = PendingPdfFiles(directory)
        val expired = files.create()
        val active = files.create()
        val unrelated = File(directory, "notes.txt").apply { writeText("keep") }
        val old = System.currentTimeMillis() - 9L * 24 * 60 * 60 * 1000
        listOf(expired, active, unrelated).forEach { assertTrue(it.setLastModified(old)) }
        files.cleanOld(active.absolutePath)
        assertFalse(expired.exists())
        assertTrue(active.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun cleanupRecognizesTheActivePdfThroughAnEquivalentPath() {
        val directory = temp.newFolder("pending")
        val files = PendingPdfFiles(directory)
        val active = files.create()
        val expired = files.create()
        listOf(active, expired).forEach { assertTrue(it.setLastModified(1L)) }
        val aliasedPath = File(directory, "../pending/${active.name}").absolutePath
        assertNotEquals(active.absolutePath, aliasedPath)
        files.cleanOld(aliasedPath, now = 8L * 24 * 60 * 60 * 1000)
        assertTrue(active.exists())
        assertFalse(expired.exists())
    }
}
