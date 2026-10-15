package com.naminghouse.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import com.naminghouse.engine.oheng.BaleumSchool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FavoritesStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: FavoritesStore
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val name = FavoriteName("김", "도윤", "金度侖", 94, "대길", "너그러운 사람")

    @Before fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "favorites.preferences_pb")
        }
        store = FavoritesStore(dataStore)
    }

    @After fun closeStore() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test fun staleSnapshotsCannotOverwriteOtherChanges() = runBlocking {
        store.toggle(name)
        store.updateNote(name, "가족이 함께 고른 이름")
        val stale = name.copy(score = 1, grade = "옛 등급", meaning = "옛 뜻")
        store.setFinalist(stale, true)
        assertEquals(name.copy(familyNote = "가족이 함께 고른 이름", isFinalist = true), store.flow.first().single())

        store.updateNote(stale, "새 메모")
        assertEquals(name.copy(familyNote = "새 메모", isFinalist = true), store.flow.first().single())
        store.setFinalist(stale, false)
        assertEquals(name.copy(familyNote = "새 메모"), store.flow.first().single())
    }

    @Test fun removeReturnsTheActualRecordAndRestoreSurvivesReopening() = runBlocking {
        store.toggle(name)
        store.updateNote(name, "따뜻하게\n자라길\t바라 + % & 🌷")
        store.setFinalist(name, true)
        val stored = store.flow.first().single()

        val removed = store.remove(name)
        assertEquals(stored, removed)
        assertTrue(store.flow.first().isEmpty())
        assertNull(store.remove(name))
        store.restore(removed!!)
        closeStore()
        openStore()
        assertEquals(stored, store.flow.first().single())
    }

    @Test fun restoreDoesNotOverwriteANameSavedAgainAfterRemoval() = runBlocking {
        store.toggle(name.copy(familyNote = "예전 메모", isFinalist = true))
        val removed = store.remove(name)!!
        val newer = name.copy(score = 87, familyNote = "다시 고른 이유")
        store.toggle(newer)

        store.restore(removed)
        assertEquals(listOf(newer), store.flow.first())
    }

    @Test fun updatesForRemovedNamesDoNotBringThemBack() = runBlocking {
        store.toggle(name)
        store.remove(name)
        store.updateNote(name, "늦게 닫힌 메모 창")
        store.setFinalist(name, true)
        assertTrue(store.flow.first().isEmpty())
    }

    @Test fun noteAndFinalistUpdatesAreAtomicAndPreserveEachOther() = runBlocking {
        store.toggle(name)
        coroutineScope {
            launch { store.updateNote(name, "동시에 정한 메모") }
            launch { store.setFinalist(name, true) }
        }
        assertEquals(name.copy(familyNote = "동시에 정한 메모", isFinalist = true), store.flow.first().single())
    }

    @Test fun allMutationsUseStructuredIdentityRatherThanFullNameOrScore() = runBlocking {
        val first = FavoriteName("남궁", "민", "南宮珉", 94, "대길", "뜻")
        val second = first.copy(surname = "남", givenName = "궁민")
        val otherHanja = first.copy(hanja = "南宮旼")
        listOf(first, second, otherHanja).forEach { store.toggle(it) }
        store.updateNote(first, "첫 이름 메모")
        store.setFinalist(first, true)
        assertEquals(second, store.flow.first().single { it.key == second.key })
        assertEquals(otherHanja, store.flow.first().single { it.key == otherHanja.key })
        store.toggle(first.copy(score = 50))
        assertEquals(setOf(second, otherHanja), store.flow.first().toSet())
        store.remove(second)
        assertEquals(listOf(otherHanja), store.flow.first())
    }

    @Test fun legacyDuplicatesPreferAuthoredNotesAndAreFullyRemoved() = runBlocking {
        val annotated = name.copy(score = 88, familyNote = "기억하고 싶은 이유")
        val legacy = name.serialize().split('\t').take(6).joinToString("\t")
        val sevenFields = annotated.serialize().split('\t').take(7).joinToString("\t")
        val unrelated = name.copy(givenName = "서준", hanja = "金瑞俊", score = 91)
        dataStore.edit { it[favoritesKey] = setOf(legacy, sevenFields, unrelated.serialize(), "손상된 줄") }

        assertEquals(listOf(unrelated, annotated), store.flow.first())
        store.setFinalist(name, true)
        assertEquals(annotated.copy(isFinalist = true), store.flow.first().single { it.key == name.key })
        assertEquals(3, dataStore.data.first()[favoritesKey]!!.size)
        assertEquals(annotated.copy(isFinalist = true), store.remove(name))
        assertEquals(listOf(unrelated), store.flow.first())
        assertTrue(dataStore.data.first()[favoritesKey]!!.contains("손상된 줄"))
    }

    @Test fun notesRetainTheirLengthLimitWithoutChangingOtherFields() = runBlocking {
        store.toggle(name.copy(isFinalist = true))
        store.updateNote(name, "가".repeat(200))
        assertEquals(name.copy(familyNote = "가".repeat(160), isFinalist = true), store.flow.first().single())
    }

    @Test fun importPreservesExistingMetadataAndOnlyAddsNewIdentities() = runBlocking {
        val record = EvaluationRecord(BaleumSchool.HAERYE, 1_760_000_000_000L, "rules-v1", "hanja-v1")
        val existing = name.copy(familyNote = "현재 메모", isFinalist = true, evaluationRecord = record)
        val added = name.copy(givenName = "서윤", hanja = "金瑞潤", familyNote = "불러온 메모", evaluationRecord = record)
        store.toggle(existing)
        val old = name.copy(score = 70, familyNote = "오래된 메모")
        assertEquals(FavoriteImportResult(1, 1), store.importNames(listOf(old, added)))
        assertEquals(existing, store.flow.first().single { it.key == existing.key })
        assertEquals(added, store.flow.first().single { it.key == added.key })
        assertEquals(FavoriteImportResult(0, 2), store.importNames(listOf(old, added)))
        closeStore()
        openStore()
        assertEquals(setOf(existing, added), store.flow.first().toSet())
    }

    @Test fun invalidImportCannotPartiallyWriteEarlierValidNames() = runBlocking {
        val original = name.copy(familyNote = "남아 있어야 하는 메모")
        store.toggle(original)
        val validNewName = name.copy(givenName = "서윤", hanja = "金瑞潤")
        try {
            store.importNames(listOf(validNewName, name.copy(givenName = "invalid")))
            fail("Invalid import should fail")
        } catch (_: FavoriteBackupException) { /* Expected: nothing from the file is committed. */ }
        assertEquals(listOf(original), store.flow.first())
    }

    @Test fun concurrentImportRetriesOnlyCommitEachNameOnce() = runBlocking {
        val results = coroutineScope {
            listOf(async { store.importNames(listOf(name)) }, async { store.importNames(listOf(name)) }).awaitAll()
        }
        assertEquals(1, results.sumOf { it.added })
        assertEquals(1, results.sumOf { it.skipped })
        assertEquals(listOf(name), store.flow.first())
    }

    @Test fun noteFinalistAndRestoreKeepTheSavedEvaluationRecord() = runBlocking {
        val record = EvaluationRecord(BaleumSchool.UNHAE, 1_760_000_000_000L, "rules-v1", "hanja-v1")
        store.toggle(name.copy(evaluationRecord = record))
        store.updateNote(name, "이유")
        store.setFinalist(name, true)
        val removed = store.remove(name)!!
        assertEquals(record, removed.evaluationRecord)
        store.restore(removed)
        assertEquals(name.copy(evaluationRecord = record, familyNote = "이유", isFinalist = true), store.flow.first().single())
    }
}
