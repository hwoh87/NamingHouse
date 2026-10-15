package com.naminghouse.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.oheng.BaleumSchool
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.cancel

/** State regression tests use the real database/engine without changing the user's saved names. */
class NamingStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: NamingViewModel

    @After fun cleanUp() {
        if (::vm.isInitialized) compose.runOnUiThread {
            vm.viewModelScope.cancel()
            vm.premium.dispose()
        }
    }

    private fun launch() {
        compose.runOnUiThread {
            vm = NamingViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        }
        compose.waitUntil(20_000) { vm.hanjaDb != null && vm.inputsLoaded }
        compose.runOnUiThread {
            vm.onSurnameChanged("김")
            vm.surnameHanja.value = listOf(vm.hanjaDb!!.byChar.getValue('金'))
            vm.preBirth = true
            vm.school = BaleumSchool.UNHAE
            vm.singleName = false
            vm.dolimja = ""
            vm.excludedSyllables = ""
        }
    }

    private fun pickName(name: String) {
        vm.onGivenNameChanged(name)
        vm.givenHanja.value = name.map { vm.hanjaDb!!.candidatesFor(it.toString()).first { entry -> entry.usableForNaming } }
    }

    private fun favorites(): List<FavoriteName> = listOf("하윤", "서준").map { name ->
        val hanja = "金" + name.map { vm.hanjaDb!!.candidatesFor(it.toString()).first { entry -> entry.usableForNaming }.char }.joinToString("")
        FavoriteName("김", name, hanja, 1, "예전 등급", "예전 뜻")
    }

    @Test fun submittedNameAndSchoolRemainFrozenWhileFieldsAreEdited() {
        launch()
        compose.runOnUiThread {
            pickName("하윤")
            vm.runEvaluate()
            pickName("서준")
            vm.school = BaleumSchool.HAERYE
            vm.year = "invalid"
        }
        compose.waitUntil(20_000) { !vm.busy }
        compose.runOnUiThread {
            val actual = requireNotNull(vm.selected).first
            assertEquals("하윤", actual.givenName)
            assertEquals(NameEvaluator.evaluate(actual.surname, actual.givenName, actual.surnameHanja,
                actual.givenHanja, null, BaleumSchool.UNHAE), actual)
            assertNull(vm.selectedSaju)
            assertEquals(BaleumSchool.UNHAE, vm.selectedEvaluationRecord!!.school)
            assertEquals(EvaluationVersions.RULES, vm.selectedEvaluationRecord!!.rulesVersion)
            assertEquals(64, vm.selectedEvaluationRecord!!.hanjaDataVersion.length)
            assertNull(vm.selectedEvaluationRecord!!.birth)
            assertNull(vm.errorMessage)
        }
    }

    @Test fun newestRequestWinsWhenAnEarlierCalculationIsStillRunning() {
        launch()
        compose.runOnUiThread {
            vm.runRecommend()
            pickName("서준")
            vm.runEvaluate()
        }
        compose.waitUntil(20_000) { !vm.busy }
        compose.runOnUiThread {
            assertEquals("서준", vm.selected!!.first.givenName)
            assertEquals(Routes.DETAIL, vm.pendingRoute)
            assertTrue(vm.candidates.isEmpty())
        }
    }

    @Test fun completedInputAndProvenanceKeepBirthAndSameNameCityBeforeLaterEdits() {
        launch()
        val cities = com.samramanshang.manseryeok.orrery.repository.CityRepository.KOREAN_CITIES.filter { it.name == "광주" }
        val original = cities.first { it.region == "경기도" }
        compose.runOnUiThread {
            vm.city = original
            vm.preBirth = false; vm.isLunar = false; vm.isLeapMonth = false
            vm.unknownTime = false; vm.gender = com.samramanshang.manseryeok.orrery.model.Gender.F
            vm.year = "2026"; vm.month = "8"; vm.day = "11"; vm.hour = "12"; vm.minute = "30"
            pickName("서윤")
            vm.runEvaluate()
            vm.city = cities.first { it.region == "광주광역시" }
            vm.year = "2027"; vm.gender = com.samramanshang.manseryeok.orrery.model.Gender.M
            vm.onGivenNameChanged("지우")
        }
        compose.waitUntil(20_000) { !vm.busy && vm.lastSavedInput?.givenName == "서윤" }
        compose.runOnIdle {
            val record = requireNotNull(vm.selectedEvaluationRecord)
            assertEquals("광주 (경기도)", record.birthPlace)
            assertEquals(original.lon, record.birth!!.longitude, 0.0)
            assertEquals(com.samramanshang.manseryeok.orrery.model.Gender.F, record.birth!!.gender)
            assertEquals(2026, record.birth!!.year)
            vm.resumeLastInput()
            assertEquals(AppMode.EVALUATE, vm.mode)
            assertEquals("서윤", vm.givenName)
            assertEquals(vm.lastSavedInput!!.givenHanja, vm.givenHanja.value.joinToString("") { it!!.char.toString() })
            assertEquals(original, vm.city)
            assertEquals("2026", vm.year)
            assertEquals(com.samramanshang.manseryeok.orrery.model.Gender.F, vm.gender)
        }
    }

    @Test fun clearingAComparisonPreventsLateResultsAndInvalidDatesFail() {
        launch()
        compose.runOnUiThread {
            vm.compareFavorites(favorites())
            vm.clearComparison()
            assertFalse(vm.comparisonBusy)
            assertNull(vm.comparison)
            vm.preBirth = false
            vm.year = "2026"; vm.month = "2"; vm.day = "31"
            vm.isLunar = false
            vm.hour = "12"; vm.minute = "0"
            vm.compareFavorites(favorites())
        }
        compose.runOnUiThread {
            assertNull(vm.comparison)
            assertFalse(vm.comparisonBusy)
            assertNotNull(vm.comparisonError)
        }
    }

    @Test fun openingComparedNameKeepsTheComparisonContextAndResultListContextSeparate() {
        launch()
        compose.runOnUiThread { vm.runRecommend() }
        compose.waitUntil(20_000) { !vm.busy }
        compose.runOnUiThread {
            assertTrue(vm.candidates.isNotEmpty())
            assertNull(vm.saju)
            vm.preBirth = false
            vm.year = "2026"; vm.month = "8"; vm.day = "11"
            vm.hour = "12"; vm.minute = "0"
            vm.isLunar = false; vm.unknownTime = false
            vm.compareFavorites(favorites())
        }
        compose.waitUntil(20_000) { !vm.comparisonBusy }
        compose.runOnUiThread {
            val compared = requireNotNull(vm.comparison)
            vm.year = "2027"
            vm.preBirth = true
            vm.school = BaleumSchool.HAERYE
            vm.openComparedFavorite(compared.entries.first())
            assertEquals(2026, vm.selectedSaju!!.input.year)
            assertEquals(2026, vm.selectedEvaluationRecord!!.birth!!.year)
            assertEquals(BaleumSchool.UNHAE, vm.selectedEvaluationRecord!!.school)
            assertEquals(compared.birthLine, vm.selectedBirthLine)
            assertNull(vm.saju)
            vm.selectResult(vm.candidates.first().evaluation, vm.candidates.first().stat)
            assertNull(vm.selectedSaju)
            assertNull(vm.selectedBirthLine)
        }
    }
}
