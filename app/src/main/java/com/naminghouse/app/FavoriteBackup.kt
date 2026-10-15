package com.naminghouse.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class FavoriteImportResult(val added: Int, val skipped: Int)

data class FavoriteImportPreview(val additions: List<FavoriteName>, val duplicates: List<FavoriteName>)

class FavoriteBackupException(val userMessage: String) : IllegalArgumentException(userMessage)

/**
 * 앱 복원 전용 UTF-8 파일. 버전·항목 수·SHA-256 다음에 URI 인코딩한 9개 필드를 저장한다.
 * 체크섬은 우발적인 파일 손상을 감지하며, 작성자의 신원을 증명하는 서명이 아니다.
 */
object FavoriteBackup {
    const val MAX_BYTES = 1_048_576
    const val MAX_NAMES = 1_000
    const val EXTENSION = "nhbackup"
    private const val HEADER = "NAMINGHOUSE-NAMEBOOK\t1"
    private const val MAX_RECORD_LENGTH = 4_096

    fun encode(items: List<FavoriteName>, includeNotes: Boolean = false, includeBirthDetails: Boolean = false): ByteArray {
        val safeItems = items.map { item ->
            item.copy(
                familyNote = if (includeNotes) item.familyNote else "",
                evaluationRecord = if (includeBirthDetails) item.evaluationRecord else item.evaluationRecord?.withoutBirthDetails(),
            )
        }
        validate(safeItems)
        val body = safeItems.joinToString("\n") { item ->
            listOf(item.surname, item.givenName, item.hanja, item.score.toString(), item.grade,
                item.meaning, item.familyNote, item.isFinalist.toString(), item.evaluationRecord?.serialize().orEmpty())
                .joinToString("\t") { encodeField(it) }
        }
        val bytes = "$HEADER\n${safeItems.size}\n${checksum(body.toByteArray(Charsets.UTF_8))}\n$body".toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) fail("이름책 파일이 너무 커요. 한 번에 최대 1MB까지 보관할 수 있어요.")
        return bytes
    }

    /** 파일 제공자가 크기를 알려주지 않아도 제한을 넘는 입력은 끝까지 읽지 않는다. */
    fun read(input: InputStream): List<FavoriteName> {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count == -1) break
            if (count == 0) {
                val byte = input.read()
                if (byte == -1) break
                output.write(byte)
            } else output.write(buffer, 0, count)
            if (output.size() > MAX_BYTES) fail("1MB 이하의 이름책 파일만 가져올 수 있어요.")
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): List<FavoriteName> {
        if (bytes.size > MAX_BYTES) fail("1MB 이하의 이름책 파일만 가져올 수 있어요.")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { fail("파일의 글자가 손상됐어요. 원본 이름책 파일을 다시 선택해 주세요.") }
        val sections = text.split('\n', limit = 4)
        if (sections.size != 4) fail("네이밍하우스에서 내보낸 이름책 파일을 선택해 주세요.")
        if (sections[0] != HEADER) {
            if (sections[0].startsWith("NAMINGHOUSE-NAMEBOOK\t")) fail("이 파일의 저장 버전을 지원하지 않아요. 앱을 업데이트한 뒤 다시 시도해 주세요.")
            fail("네이밍하우스에서 내보낸 이름책 파일을 선택해 주세요.")
        }
        val count = sections[1].toIntOrNull()
        if (count == null || count !in 0..MAX_NAMES) fail("한 파일에 최대 ${MAX_NAMES}개 이름까지 가져올 수 있어요.")
        if (!sections[2].matches(Regex("[0-9a-f]{64}")) ||
            sections[2] != checksum(sections[3].toByteArray(Charsets.UTF_8))) {
            fail("파일 내용이 변경되거나 손상됐어요. 원본 이름책 파일을 다시 선택해 주세요.")
        }
        val lines = if (sections[3].isEmpty()) emptyList() else sections[3].split('\n')
        if (lines.size != count) fail("파일에 기록된 이름 수가 맞지 않아요. 원본 파일을 다시 선택해 주세요.")
        val items = lines.map { line ->
            val fields = line.split('\t')
            if (fields.size != 9) fail("이름 정보의 형식이 맞지 않아요. 원본 파일을 다시 선택해 주세요.")
            val values = fields.map(::decodeField)
            val score = values[3].toIntOrNull() ?: fail("이름의 저장 점수가 올바르지 않아요.")
            val finalist = values[7].toBooleanStrictOrNull() ?: fail("최종 후보 정보가 올바르지 않아요.")
            if (values[8].length > MAX_RECORD_LENGTH) fail("저장한 평가 기록이 너무 길어요.")
            val record = if (values[8].isEmpty()) null else {
                val parsed = EvaluationRecord.parse(values[8]) ?: fail("저장한 평가 기록을 읽을 수 없어요. 원본 파일을 확인해 주세요.")
                if (parsed.serialize() != values[8]) fail("저장한 평가 기록의 형식이 올바르지 않아요.")
                parsed
            }
            FavoriteName(values[0], values[1], values[2], score, values[4], values[5], values[6], finalist, record)
        }
        validate(items)
        return items
    }

    fun preview(items: List<FavoriteName>, existing: List<FavoriteName>): FavoriteImportPreview {
        val keys = existing.map { it.key }.toSet()
        return FavoriteImportPreview(items.filterNot { it.key in keys }, items.filter { it.key in keys })
    }

    /** 화면 미리보기를 우회해 저장소를 호출해도 손상된 데이터는 일부만 저장되지 않는다. */
    fun validate(items: List<FavoriteName>) {
        if (items.size > MAX_NAMES) fail("한 파일에 최대 ${MAX_NAMES}개 이름까지 가져올 수 있어요.")
        if (items.map { it.key }.toSet().size != items.size) fail("파일 안에 같은 이름과 한자가 중복돼 있어요. 원본 이름책을 다시 내보내 주세요.")
        items.forEach { item ->
            if (item.surname.length !in 1..2 || !item.surname.all(::isHangulSyllable) ||
                item.givenName.length !in 1..3 || !item.givenName.all(::isHangulSyllable)) {
                fail("성은 한글 1~2자, 이름은 한글 1~3자여야 해요.")
            }
            if (item.hanja.length != item.fullHangul.length || !item.hanja.all(::isHanja)) {
                fail("이름의 한자와 글자 수가 올바르지 않아요.")
            }
            if (item.score !in 0..100) fail("이름의 저장 점수는 0~100점이어야 해요.")
            if (item.grade.isBlank() || item.grade.length > 32 || item.grade.any { it.isISOControl() }) fail("이름의 등급 정보가 올바르지 않아요.")
            if (item.meaning.length > 1_024 || item.meaning.any { it.isISOControl() && it != '\n' && it != '\r' }) fail("이름 뜻이 너무 길거나 올바르지 않아요.")
            if (item.familyNote.length > 160 || item.familyNote.any { it.isISOControl() && it !in "\n\r\t" }) fail("가족 메모는 160자 이내여야 해요.")
            if (listOf(item.grade, item.meaning, item.familyNote).any { !Charsets.UTF_8.newEncoder().canEncode(it) }) {
                fail("이름 정보에 완성되지 않은 글자가 있어요. 메모와 뜻을 확인해 주세요.")
            }
            val recordText = item.evaluationRecord?.serialize()
            if (recordText != null && (recordText.length > MAX_RECORD_LENGTH || EvaluationRecord.parse(recordText) != item.evaluationRecord)) {
                fail("저장한 평가 기록이 올바르지 않아요.")
            }
        }
    }

    private fun isHanja(char: Char): Boolean = char.code in 0x3400..0x4DBF || char.code in 0x4E00..0x9FFF || char.code in 0xF900..0xFAFF
    private fun encodeField(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun decodeField(value: String): String {
        val decoded = try { URLDecoder.decode(value, "UTF-8") } catch (_: Exception) { fail("파일의 글자 형식이 올바르지 않아요.") }
        // URLDecoder의 잘못된 UTF-8 대체 문자 처리도 조용히 허용하지 않는다.
        if (encodeField(decoded) != value) fail("파일의 글자 형식이 올바르지 않아요.")
        return decoded
    }
    private fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun fail(message: String): Nothing = throw FavoriteBackupException(message)
}
