package com.naminghouse.app.ui

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Completed exports outlive an Activity. Only their small path belongs in saved UI state. */
internal class PendingPdfFiles(private val directory: File) {
    fun create(): File {
        checkDirectory(directory)
        return File.createTempFile("certificate_", ".pdf", directory)
    }

    fun completed(path: String): File {
        val file = owned(path) ?: throw IOException("저장할 감명서를 찾지 못했습니다")
        if (!file.isFile || file.length() < 5L) throw IOException("감명서 파일이 비어 있습니다")
        val header = ByteArray(5)
        file.inputStream().use { if (it.read(header) != 5) throw IOException("감명서 파일을 읽지 못했습니다") }
        if (!header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
            throw IOException("감명서 파일을 확인하지 못했습니다")
        }
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
    }

    fun discard(path: String?) { path?.let(::owned)?.delete() }

    fun cleanOld(keepPath: String?, now: Long = System.currentTimeMillis()) {
        cleanOldExports(directory, "certificate_", ".pdf", keepPath, now)
    }

    private fun owned(path: String): File? {
        val file = File(path).canonicalFile
        return file.takeIf {
            it.parentFile == directory.canonicalFile && it.name.startsWith("certificate_") && it.extension == "pdf"
        }
    }
}

/** Every share has its own URI, including same-Hangul names with different Hanja. */
internal fun createSharedImageFile(cacheDirectory: File): File {
    val directory = File(cacheDirectory, "shared")
    checkDirectory(directory)
    cleanOldExports(directory, "gammyeong_", ".png", null, System.currentTimeMillis())
    return File.createTempFile("gammyeong_", ".png", directory)
}

private const val EXPORT_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

private fun checkDirectory(directory: File) {
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("임시 저장 공간을 만들지 못했습니다")
}

private fun cleanOldExports(directory: File, prefix: String, suffix: String, keepPath: String?, now: Long) {
    val keptFile = keepPath?.let { File(it).canonicalFile }
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(prefix) && file.name.endsWith(suffix) &&
            file.canonicalFile != keptFile && now - file.lastModified() > EXPORT_RETENTION_MILLIS
        ) file.delete()
    }
}
