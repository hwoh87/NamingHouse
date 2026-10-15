package com.naminghouse.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.HanjiBackdrop
import com.naminghouse.app.ui.theme.NamingHouseTheme
import com.naminghouse.engine.oheng.BaleumSchool
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoritesJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: NamingViewModel
    private lateinit var nav: NavHostController

    private fun launch(large: Boolean = false) {
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, ViewModelProvider.AndroidViewModelFactory(
                compose.activity.application as Application))[NamingViewModel::class.java]
        }
        compose.setContent {
            nav = rememberNavController()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                NamingHouseTheme(if (large) ThemeMode.DARK else ThemeMode.LIGHT) {
                    HanjiBackdrop { AppNavigation(vm, nav) }
                }
            }
        }
        compose.waitUntil(20_000) { vm.inputsLoaded && vm.favoritesLoaded && vm.hanjaDb != null && vm.namePool != null }
        compose.runOnIdle {
            vm.favorites.toList().forEach(vm::removeFavorite)
            vm.onSurnameChanged("김")
            vm.surnameHanja.value = listOf(vm.hanjaDb!!.byChar.getValue('金'))
            vm.preBirth = true; vm.school = BaleumSchool.UNHAE
            vm.popularOnly = true; vm.singleName = false; vm.dolimja = ""; vm.excludedSyllables = ""
            vm.runRecommend()
        }
        compose.waitUntil(30_000) { !vm.busy && vm.candidates.size >= 4 && vm.favorites.isEmpty() }
        compose.runOnIdle {
            vm.recentlyRemoved?.let(vm::dismissRemovedFavorite)
            vm.pendingRoute = null
            vm.candidates.take(4).forEach { vm.toggleFavorite(it.evaluation) }
        }
        compose.waitUntil(5_000) { vm.favorites.size == 4 }
        compose.runOnIdle { nav.navigate(Routes.FAVORITES) }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(3500) // Let transient Play connection notices leave the screenshot.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "benchmark-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun shortlistSearchUndoAndComparisonPreserveFamilyDecisions() {
        launch()
        val first = vm.favorites.first()
        val note = "다정하게 자라길 바라는 마음"
        compose.runOnIdle { vm.updateFavoriteNote(first, note) }
        compose.waitUntil(5_000) { vm.favorites.first { it.key == first.key }.familyNote == note }
        compose.onNodeWithTag("finalist-${first.key}").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.favorites.count { it.isFinalist } == 1 }
        compose.onNodeWithText("최종 후보 1").performScrollTo().performClick()
        compose.onAllNodesWithText("현재 조건으로 풀이 →").assertCountEquals(1)
        compose.onNodeWithTag("favorites-search").performScrollTo().performTextInput("다정")
        compose.onNodeWithTag("favorite-${first.key}").assertExists()
        compose.onNodeWithTag("favorites-search").performTextReplacement("없는검색결과")
        compose.onNodeWithText("찾는 이름이 없어요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전체 이름 보기").performScrollTo().performClick()
        compose.onNodeWithTag("favorite-${first.key}").performScrollTo()
        compose.onNodeWithContentDescription("${first.fullHangul} 이름책에서 빼기").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.favorites.size == 3 }
        compose.onNodeWithText("되돌리기").performClick()
        compose.waitUntil(5_000) { vm.favorites.size == 4 }
        assertTrue(vm.favorites.first { it.key == first.key }.isFinalist)
        assertEquals(note, vm.favorites.first { it.key == first.key }.familyNote)
        val names = vm.favorites.toList()
        names.take(3).forEach { compose.onNodeWithTag("compare-${it.key}").performScrollTo().performClick() }
        compose.onNodeWithTag("compare-${names.last().key}").assertIsNotEnabled()
        compose.onNodeWithText("3/3 이름 비교").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("담아둔 이름").performScrollTo()
        capture("01-shortlist-light")
        compose.onNodeWithText("3/3 이름 비교").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.comparison != null }
        compose.onNodeWithText("우리 가족의 이름 후보").assertIsDisplayed()
        compose.onNodeWithText("사주 없이 · 성명학 기준 · 운해본(다수설)").assertExists()
        compose.onNodeWithText(note).assertExists()
        capture("02-comparison-light")
        val compared = vm.comparison!!
        compose.runOnIdle { vm.school = BaleumSchool.HAERYE; vm.preBirth = false; vm.year = "잘못된 입력" }
        compose.onNodeWithTag("comparison-open-${compared.entries.first().favorite.key}").performScrollTo().performClick()
        compose.onNodeWithText("이름 이야기").assertIsDisplayed()
        assertEquals(compared.entries.first().evaluation, vm.selected!!.first)
        assertNull(vm.selectedSaju)
        assertEquals(names, vm.favorites)
    }

    @Test fun darkLargeTextComparisonAndEmptyStateRemainUsable() {
        launch(large = true)
        vm.favorites.take(2).forEach { compose.onNodeWithTag("compare-${it.key}").performScrollTo().performClick() }
        compose.onNodeWithText("2/3 이름 비교").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.comparison != null }
        compose.onNodeWithText("이번 비교 기준").performScrollTo().assertIsDisplayed()
        capture("03-comparison-dark-large")
        compose.onNodeWithText("판정이 다른 항목만").performScrollTo().performClick()
        // performScrollTo only reaches the nearest (horizontal) scroll ancestor.
        // Use actual vertical gestures to verify the nested comparison at system font scale 2.0.
        repeat(6) {
            if (!compose.onAllNodesWithText("이름 한자 획수").onFirst().isDisplayed()) {
                compose.onNodeWithTag("favorite-comparison").performTouchInput { swipeUp() }
            }
        }
        capture("04-comparison-dark-large-details")
        compose.onAllNodesWithText("이름 한자 획수").onFirst().assertIsDisplayed()
        compose.onNodeWithText("비교 닫기").performScrollTo().performClick()
        compose.onNodeWithText("최종 후보 0").performScrollTo().performClick()
        compose.onNodeWithText("최종 후보를 골라보세요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전체 이름 보기").performScrollTo().performClick()
        compose.onNodeWithText("선택 해제").performScrollTo().performClick()
        compose.onNodeWithText("0/3 이름 비교").assertIsNotEnabled()
    }
}
