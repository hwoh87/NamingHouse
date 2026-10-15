package com.naminghouse.app

import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FavoriteBackupFilesTest {
    @get:Rule val folder = TemporaryFolder()
    private val name = FavoriteName("김", "도윤", "金度侖", 94, "대길", "너그러운 사람", "우리 메모", true)

    @Test fun stagedFileAndCopiedFileDecodeToTheSelectedExport() {
        val files = PendingFavoriteBackupFiles(folder.newFolder("pending"))
        val bytes = FavoriteBackup.encode(listOf(name), includeNotes = true)
        val staged = files.create(bytes)
        val output = folder.newFile("saved.nhbackup")
        output.outputStream().use { files.copyTo(staged.absolutePath, it) }
        assertArrayEquals(bytes, output.readBytes())
        assertEquals(listOf(name), output.inputStream().use(FavoriteBackup::read))
        assertTrue(staged.exists())
        files.discard(staged.absolutePath)
        assertFalse(staged.exists())
    }

    @Test fun interruptedCopyKeepsTheSourceForACompleteRetry() {
        val files = PendingFavoriteBackupFiles(folder.newFolder("pending"))
        val bytes = FavoriteBackup.encode(listOf(name.copy(meaning = "가".repeat(1_024))))
        val staged = files.create(bytes)
        val partial = ByteArrayOutputStream()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            files.copyTo(staged.absolutePath, partial) { if (++checks == 2) throw CancellationException("rotation") }
        }
        assertTrue(partial.size() in 1 until bytes.size)
        assertTrue(staged.exists())
        val retry = ByteArrayOutputStream()
        files.copyTo(staged.absolutePath, retry)
        assertArrayEquals(bytes, retry.toByteArray())
        assertEquals(1, FavoriteBackup.decode(retry.toByteArray()).size)
    }

    @Test fun corruptStagedDataNeverReachesTheDestination() {
        val files = PendingFavoriteBackupFiles(folder.newFolder("pending"))
        val staged = files.create(FavoriteBackup.encode(listOf(name)))
        staged.appendText("broken")
        val output = ByteArrayOutputStream()
        assertThrows(FavoriteBackupException::class.java) { files.copyTo(staged.absolutePath, output) }
        assertEquals(0, output.size())
    }

    @Test fun cleanupPreservesActiveAndUnrelatedFiles() {
        val directory = folder.newFolder("pending")
        val files = PendingFavoriteBackupFiles(directory)
        val active = files.create(FavoriteBackup.encode(listOf(name)))
        val old = files.create(FavoriteBackup.encode(listOf(name)))
        val unrelated = File(directory, "other.txt").apply { writeText("keep") }
        listOf(active, old, unrelated).forEach { assertTrue(it.setLastModified(1L)) }
        files.cleanOld(active.absolutePath, now = 8L * 24 * 60 * 60 * 1000)
        assertTrue(active.exists())
        assertFalse(old.exists())
        assertTrue(unrelated.exists())
        val outside = folder.newFile("namebook_outside.nhbackup")
        files.discard(outside.absolutePath)
        assertTrue(outside.exists())
    }

    @Test fun cleanupRecognizesTheActiveFileThroughAnEquivalentPath() {
        val directory = folder.newFolder("pending")
        val files = PendingFavoriteBackupFiles(directory)
        val active = files.create(FavoriteBackup.encode(listOf(name)))
        val expired = files.create(FavoriteBackup.encode(listOf(name)))
        listOf(active, expired).forEach { assertTrue(it.setLastModified(1L)) }
        val aliasedPath = File(directory, "../pending/${active.name}").absolutePath
        assertNotEquals(active.absolutePath, aliasedPath)
        files.cleanOld(aliasedPath, now = 8L * 24 * 60 * 60 * 1000)
        assertTrue(active.exists())
        assertFalse(expired.exists())
    }
}
