package com.naminghouse.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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

class RecentNamesStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: RecentNamesStore

    @Before fun open() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "recent.preferences_pb") }
        store = RecentNamesStore(dataStore)
    }

    @After fun close() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun historyKeepsOnlyTheLatestSixteenDistinctNamesAndSurvivesReopening() = runBlocking {
        val names = (0 until 18).map { ('가'.code + it).toChar().toString() }
        names.forEach { store.rememberName(it) }
        assertEquals(names.reversed().take(16), store.flow.first())
        store.rememberName(names[5])
        val expected = listOf(names[5]) + names.reversed().take(16).filterNot { it == names[5] }
        close(); open()
        assertEquals(expected, store.flow.first())
    }

    @Test fun concurrentVisitsDoNotLoseDistinctNamesAndClearRemovesEverything() = runBlocking {
        coroutineScope {
            listOf("서윤", "도윤", "민서").forEach { name -> launch { store.rememberName(name) } }
        }
        assertEquals(setOf("서윤", "도윤", "민서"), store.flow.first().toSet())
        store.clear()
        assertTrue(store.flow.first().isEmpty())
        close(); open()
        assertTrue(store.flow.first().isEmpty())
    }

    @Test fun unfinishedOrInvalidNamesNeverReplaceHistory() = runBlocking {
        store.rememberName("서윤")
        for (name in listOf("서ㅇ", "ㅅ", "abc", "한글네자")) {
            try { store.rememberName(name); fail("Invalid history name: $name") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(listOf("서윤"), store.flow.first())
    }
}
