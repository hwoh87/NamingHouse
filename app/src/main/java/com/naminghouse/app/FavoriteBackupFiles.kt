package com.naminghouse.app

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** 단계 파일은 문서 선택기가 열려 있는 동안 OS 캐시 정리나 자동 백업의 영향을 받지 않는다. */
internal class PendingFavoriteBackupFiles(private val directory: File) {
    fun create(bytes: ByteArray): File {
        FavoriteBackup.decode(bytes)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create backup staging directory")
        val file = File.createTempFile("namebook_", ".${FavoriteBackup.EXTENSION}", directory)
        try { file.writeBytes(bytes) } catch (error: Exception) { file.delete(); throw error }
        return file
    }

    fun completed(path: String): File {
        val file = owned(path) ?: throw IOException("Missing staged backup")
        if (!file.isFile || file.length() > FavoriteBackup.MAX_BYTES) throw IOException("Invalid staged backup")
        file.inputStream().use(FavoriteBackup::read)
        return file
    }

    fun copyTo(path: String, output: OutputStream, checkActive: () -> Unit = {}) {
        completed(path).inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
        checkActive()
        output.flush()
    }

    fun discard(path: String?) { path?.let(::owned)?.delete() }

    fun cleanOld(keepPath: String?, now: Long = System.currentTimeMillis()) {
        val keptFile = keepPath?.let(::owned)
        directory.listFiles()?.forEach { candidate ->
            val file = owned(candidate.absolutePath)
            if (file != null && file.isFile && file != keptFile && now - file.lastModified() > 7L * 24 * 60 * 60 * 1000) file.delete()
        }
    }

    private fun owned(path: String): File? {
        val file = File(path).canonicalFile
        return file.takeIf { it.parentFile == directory.canonicalFile && it.name.startsWith("namebook_") && it.extension == FavoriteBackup.EXTENSION }
    }
}
