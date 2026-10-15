package com.naminghouse.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InputStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var data: DataStore<Preferences>
    private lateinit var store: InputStore

    @Before fun open() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        data = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "input.preferences_pb") }
        store = InputStore(data)
    }

    @After fun close() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    private fun input(mode: AppMode) = SavedInput(
        surname = "남궁", surnameHanja = "南宮", gender = "F", preBirth = false,
        popularOnly = true, year = "2026", month = "8", day = "11", hour = "23", minute = "40",
        isLunar = false, isLeapMonth = false, unknownTime = false, cityName = "광주",
        cityRegion = "경기도", school = "HAERYE", singleName = false, dolimja = "서",
        dolimjaLast = false, excludedSyllables = "민", diverseHanja = false, mode = mode.name,
        givenName = "서윤", givenHanja = "_允", savedAtEpochMillis = 1792090800000,
    )

    @Test fun completedTaskSurvivesFileReopeningWithNameModeAndEmptyHanjaSlot() = runBlocking {
        val saved = input(AppMode.EVALUATE)
        store.save(saved)
        close(); open()
        assertEquals(saved, store.load())
        store.save(input(AppMode.HANJA))
        assertEquals(AppMode.HANJA.name, store.load()!!.mode)
        assertEquals("_允", store.load()!!.givenHanja)
    }

    @Test fun legacyInputLoadsAsRecommendationWithoutInventingNameOrTimestamp() = runBlocking {
        data.edit {
            it[stringPreferencesKey("surname")] = "김"
            it[stringPreferencesKey("surnameHanja")] = "金"
        }
        val saved = store.load()!!
        assertEquals("김", saved.surname)
        assertEquals(AppMode.RECOMMEND.name, saved.mode)
        assertEquals("", saved.givenName)
        assertEquals("", saved.givenHanja)
        assertEquals(0L, saved.savedAtEpochMillis)
    }

    @Test fun missingInputIsEmptyAndFutureSchemaIsNotSilentlyMisread() = runBlocking {
        assertNull(store.load())
        data.edit {
            it[stringPreferencesKey("surname")] = "김"
            it[intPreferencesKey("formatVersion")] = 99
        }
        assertTrue(runCatching { store.load() }.exceptionOrNull() is IllegalArgumentException)
    }
}
