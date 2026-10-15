package com.naminghouse.app

import com.naminghouse.engine.oheng.BaleumSchool
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class FavoriteBackupTest {
    private val record = EvaluationRecord(BaleumSchool.UNHAE, 1_760_000_000_000L, "rules-v1", "hanja-test-v1",
        BirthInput(2025, 5, 12, 8, 30, Gender.F), "서울")
    private val name = FavoriteName("김", "도윤", "金度侖", 94, "대길", "너그럽고 따뜻한 사람", "메모\n따뜻하게\t+ % & 🌷", true, record)

    @Test fun defaultExportOmitsNotesAndBirthDetailsButKeepsProvenance() {
        val restored = FavoriteBackup.decode(FavoriteBackup.encode(listOf(name))).single()
        val restoredRecord = requireNotNull(restored.evaluationRecord)
        assertEquals("", restored.familyNote)
        assertNull(restoredRecord.birth)
        assertEquals("", restoredRecord.birthPlace)
        assertTrue(restoredRecord.birthInfoOmitted)
        assertEquals(record.school, restoredRecord.school)
        assertEquals(record.evaluatedAtEpochMillis, restoredRecord.evaluatedAtEpochMillis)
        assertEquals(record.rulesVersion, restoredRecord.rulesVersion)
        assertEquals(record.hanjaDataVersion, restoredRecord.hanjaDataVersion)
        assertTrue(restored.isFinalist)
        assertEquals(name.score, restored.score)
    }

    @Test fun consentOptionsAreIndependentAndFullExportRoundTrips() {
        assertEquals(name, FavoriteBackup.decode(FavoriteBackup.encode(listOf(name), true, true)).single())
        val notesOnly = FavoriteBackup.decode(FavoriteBackup.encode(listOf(name), true, false)).single()
        assertEquals(name.familyNote, notesOnly.familyNote)
        assertNull(notesOnly.evaluationRecord!!.birth)
        val birthOnly = FavoriteBackup.decode(FavoriteBackup.encode(listOf(name), false, true)).single()
        assertEquals("", birthOnly.familyNote)
        assertEquals(record, birthOnly.evaluationRecord)
    }

    @Test fun unknownAndBirthlessRecordsStayDistinct() {
        val old = name.copy(evaluationRecord = null)
        assertNull(FavoriteBackup.decode(FavoriteBackup.encode(listOf(old))).single().evaluationRecord)
        val withoutBirth = record.copy(birth = null, birthPlace = "")
        val restored = FavoriteBackup.decode(FavoriteBackup.encode(listOf(name.copy(evaluationRecord = withoutBirth)))).single()
        assertEquals(withoutBirth, restored.evaluationRecord)
        assertFalse(restored.evaluationRecord!!.birthInfoOmitted)
    }

    @Test fun storageRecordRoundTripsAndOldOptionalCorruptionDoesNotHideTheName() {
        assertEquals(name, FavoriteName.parse(name.serialize()))
        val broken = name.serialize().split('\t').dropLast(1).plus("%invalid").joinToString("\t")
        assertEquals(name.copy(evaluationRecord = null), FavoriteName.parse(broken))
    }

    @Test fun sharePreviewNeverIncludesBirthDetailsAndNotesRequireOptIn() {
        val plain = shareText(listOf(name))
        assertFalse(plain.contains(name.familyNote))
        assertFalse(plain.contains("서울"))
        assertFalse(plain.contains("2025년 5월 12일"))
        assertTrue(shareText(listOf(name), true).contains(name.familyNote))
        assertFalse(shareText(listOf(name), true).contains("서울"))
    }

    @Test fun checksumDetectsEditsBeforeParsing() {
        val bytes = FavoriteBackup.encode(listOf(name))
        val edited = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertInvalid { FavoriteBackup.decode(edited) }
        assertInvalid { FavoriteBackup.decode(bytes.copyOf(bytes.size - 4)) }
    }

    @Test fun rejectsUnsupportedVersionsCountMismatchAndUnexpectedFields() {
        val bytes = FavoriteBackup.encode(listOf(name))
        assertInvalid { FavoriteBackup.decode(bytes.toString(Charsets.UTF_8).replace("NAMEBOOK\t1", "NAMEBOOK\t2").toByteArray()) }
        assertInvalid { FavoriteBackup.decode(envelope(validRow(), count = 2)) }
        assertInvalid { FavoriteBackup.decode(envelope(validRow().substringBeforeLast('\t'))) }
        assertInvalid { FavoriteBackup.decode(envelope(validRow() + "\textra")) }
        assertInvalid { FavoriteBackup.decode(envelope("", count = FavoriteBackup.MAX_NAMES + 1)) }
    }

    @Test fun rejectsInvalidIdentityEvenWithAValidChecksum() {
        listOf(0 to "ㄱ", 0 to "김가나", 1 to "James", 1 to "가나다라", 2 to "金度", 2 to "Kim").forEach { (index, value) ->
            assertInvalid { FavoriteBackup.decode(envelope(validRow(index, value))) }
        }
    }

    @Test fun rejectsInvalidScoresFlagsLengthsAndEvaluationRecords() {
        listOf(3 to "101", 3 to "-1", 3 to "NaN", 7 to "yes", 4 to "길".repeat(33),
            5 to "뜻".repeat(1_025), 6 to "가".repeat(161), 8 to "invalid-record").forEach { (index, value) ->
            assertInvalid { FavoriteBackup.decode(envelope(validRow(index, value))) }
        }
    }

    @Test fun rejectsDuplicateIdentitiesRatherThanChoosingConflictingMetadata() {
        assertInvalid { FavoriteBackup.decode(envelope(validRow() + "\n" + validRow(6, "서로 다른 메모"), count = 2)) }
        assertInvalid { FavoriteBackup.encode(listOf(name, name.copy(score = 80))) }
    }

    @Test fun rejectsMalformedUtf8AndMalformedEscapes() {
        assertInvalid { FavoriteBackup.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        val fields = validRow().split('\t').toMutableList()
        fields[6] = "%FF"
        assertInvalid { FavoriteBackup.decode(envelope(fields.joinToString("\t"))) }
        fields[6] = "%invalid"
        assertInvalid { FavoriteBackup.decode(envelope(fields.joinToString("\t"))) }
    }

    @Test fun sizeLimitStopsReadingUnknownLengthStreams() {
        var bytesRead = 0
        val infinite = object : InputStream() {
            override fun read(): Int { bytesRead++; return 65 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(65, offset, offset + length)
                bytesRead += length
                return length
            }
        }
        assertInvalid { FavoriteBackup.read(infinite) }
        assertEquals(FavoriteBackup.MAX_BYTES + 1, bytesRead)
        assertInvalid { FavoriteBackup.decode(ByteArray(FavoriteBackup.MAX_BYTES + 1)) }
    }

    @Test fun previewUsesStructuredIdentityAndKeepsImportOrder() {
        val added = name.copy(givenName = "서윤", hanja = "金瑞潤")
        val preview = FavoriteBackup.preview(listOf(added, name), listOf(name.copy(familyNote = "새 메모", isFinalist = false)))
        assertEquals(listOf(added), preview.additions)
        assertEquals(listOf(name), preview.duplicates)
    }

    @Test fun emptyFileAndStreamRoundTripAreSupported() {
        assertTrue(FavoriteBackup.decode(FavoriteBackup.encode(emptyList())).isEmpty())
        assertEquals(listOf(name), FavoriteBackup.read(FavoriteBackup.encode(listOf(name), true, true).inputStream()))
    }

    private fun validRow(changeIndex: Int = -1, changeValue: String = ""): String {
        val values = mutableListOf("김", "도윤", "金度侖", "94", "대길", "너그러운 사람", "메모", "true", "")
        if (changeIndex >= 0) values[changeIndex] = changeValue
        return values.joinToString("\t") { URLEncoder.encode(it, "UTF-8") }
    }

    private fun envelope(body: String, count: Int = 1): ByteArray {
        val checksum = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "NAMINGHOUSE-NAMEBOOK\t1\n$count\n$checksum\n$body".toByteArray(Charsets.UTF_8)
    }

    private fun assertInvalid(block: () -> Unit) {
        assertThrows(FavoriteBackupException::class.java, block)
    }
}
