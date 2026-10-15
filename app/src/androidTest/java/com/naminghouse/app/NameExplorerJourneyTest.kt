package com.naminghouse.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.HanjiBackdrop
import com.naminghouse.app.ui.theme.NamingHouseTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NameExplorerJourneyTest {
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
        compose.waitUntil(20_000) { vm.inputsLoaded && vm.recentNamesLoaded && vm.namePool != null && vm.hanjaDb != null }
        compose.runOnIdle { vm.clearRecentNames(); nav.navigate(Routes.EXPLORE) }
        compose.waitUntil(5_000) { vm.recentNames.isEmpty() }
        waitForResults()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNodeWithTag("name-explorer").performScrollToNode(matcher)
    }

    private fun waitForResults() {
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("name-explorer").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "검색 완료"
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir, "improvement-shots").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun initialsRelatedEndingAndRecentHistoryLeadIntoHanjaInput() {
        launch()
        scrollTo(hasTestTag("explorer-query"))
        compose.onNodeWithTag("explorer-query").performTextReplacement("ㅅㅇ")
        compose.onNodeWithTag("explorer-query").performImeAction()
        waitForResults()
        scrollTo(hasTestTag("discovered-서윤"))
        compose.onNodeWithTag("discovered-서윤").assertExists()
        capture("01-explorer-initial-results")
        val related = hasText("끝 글자 ‘윤’ 더 찾기") and hasAnyAncestor(hasTestTag("discovered-서윤"))
        scrollTo(related)
        compose.onNode(related).performClick()
        waitForResults()
        scrollTo(hasTestTag("explorer-query"))
        compose.onNodeWithTag("explorer-query").assertTextContains("윤")
        val choose = hasText("이 이름의 한자 찾기") and hasAnyAncestor(hasTestTag("discovered-서윤"))
        scrollTo(choose)
        compose.onNode(choose).performClick()
        compose.waitUntil(5_000) { nav.currentDestination?.route == Routes.INPUT && vm.recentNames.contains("서윤") }
        assertEquals(AppMode.HANJA, vm.mode)
        assertEquals("서윤", vm.givenName)

        compose.runOnIdle { nav.popBackStack() }
        scrollTo(hasText("최근 본 이름 전체 지우기"))
        compose.onNodeWithText("최근 본 이름 전체 지우기").performClick()
        compose.waitUntil(5_000) { vm.recentNames.isEmpty() }
    }

    @Test fun largeTextMeaningEvidenceAndCancellingDirectInputPreserveCurrentWork() {
        launch(large = true)
        scrollTo(hasText("한자 뜻으로 찾기"))
        compose.onNodeWithText("한자 뜻으로 찾기").performClick()
        compose.onNodeWithTag("explorer-query").performTextReplacement("상서")
        compose.onNodeWithTag("explorer-query").performImeAction()
        waitForResults()
        val evidence = hasText("뜻이 맞는 한자 후보") and hasAnyAncestor(hasTestTag("discovered-서윤"))
        scrollTo(evidence)
        compose.onNode(evidence).assertIsDisplayed()
        val warning = hasText("다음 단계의 추천 조합은 이 한자와 다를 수 있어요.") and
            hasAnyAncestor(hasTestTag("discovered-서윤"))
        scrollTo(warning)
        compose.onNode(warning).assertIsDisplayed()
        capture("02-explorer-meaning-dark-large")

        val previousMode = vm.mode
        val previousName = vm.givenName
        scrollTo(hasTestTag("explorer-direct"))
        compose.onNodeWithTag("explorer-direct").performClick()
        compose.onNodeWithTag("explorer-direct-input").performTextInput("서ㅇ")
        compose.onNodeWithTag("explorer-direct-input").assertTextContains("서ㅇ")
        android.os.SystemClock.sleep(500) // Let the IME resize/transition settle before checking the actions.
        compose.waitForIdle()
        try {
            compose.onNodeWithText("닫기").assertIsDisplayed()
        } finally {
            capture("03-explorer-direct-dialog") // Preserve the settled frame even when visibility fails.
        }
        compose.onNodeWithText("닫기").performTouchInput { click() }
        compose.onNodeWithTag("explorer-direct-input").assertDoesNotExist()
        assertEquals(previousMode, vm.mode)
        assertEquals(previousName, vm.givenName)
        assertTrue(vm.recentNames.isEmpty())

        compose.onNodeWithTag("explorer-direct").performClick()
        compose.onNodeWithTag("explorer-direct-input").performTextReplacement("가나다")
        compose.onNodeWithText("한자 찾기", substring = false).performClick()
        compose.waitUntil(5_000) { nav.currentDestination?.route == Routes.INPUT }
        assertEquals("가나다", vm.givenName)
    }
}
