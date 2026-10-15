package com.naminghouse.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.InputScreen
import com.naminghouse.app.ui.ResultScreen
import com.naminghouse.app.ui.theme.NamingHouseTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real stored input + fresh ViewModel, with Compose saved state restored independently. */
class InputRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = mutableListOf<NamingViewModel>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @After fun dispose() = compose.runOnUiThread {
        models.forEach { it.viewModelScope.cancel(); it.premium.dispose() }
    }

    private fun storeCompletedHanja() = runBlocking {
        InputStore(app).save(SavedInput("김", "金", "M", true, false,
            "2026", "8", "11", "12", "0", false, false, false,
            "서울", "서울특별시", "UNHAE", mode = "HANJA", givenName = "서준", givenHanja = "__"))
    }

    private fun freshModel(): NamingViewModel {
        lateinit var model: NamingViewModel
        compose.runOnUiThread { model = NamingViewModel(app); models += model }
        compose.waitUntil(20_000) { model.inputsLoaded && model.hanjaDb != null }
        return model
    }

    @Test fun oldThirdStepCannotCrashARecreatedTwoStepJourney() {
        storeCompletedHanja()
        val previous = freshModel()
        compose.runOnUiThread { previous.mode = AppMode.RECOMMEND; previous.pendingRoute = null }
        var displayed = previous
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NamingHouseTheme { InputScreen(displayed, rememberNavController()) }
        }
        compose.onNodeWithText("다음 · 이름 취향").performClick()
        compose.onNodeWithText("다음 · 출생 정보").performClick()
        compose.onNodeWithText("3 / 3  ·  출생 정보").assertIsDisplayed()
        // A real new VM reads HANJA from disk, while Compose still has RECOMMEND's step 2.
        val recreated = freshModel()
        assertEquals(AppMode.HANJA, recreated.mode)
        displayed = recreated
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1 / 2  ·  이름 정보").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("서준", recreated.givenName)
            assertEquals('金', recreated.surnameHanja.value.single()!!.char)
            assertEquals(listOf(null, null), recreated.givenHanja.value)
        }
        compose.runOnIdle { recreated.preBirth = false }
        compose.onNodeWithText("다음 · 출생 정보").performClick()
        compose.onNodeWithText("여아").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(com.samramanshang.manseryeok.orrery.model.Gender.F, recreated.gender) }
    }

    @Test fun missingResultsResumeTheSavedModeAndName() {
        storeCompletedHanja()
        val vm = freshModel()
        assertEquals(AppMode.RECOMMEND, vm.resultMode) // Transient results were intentionally not persisted.
        compose.setContent {
            NamingHouseTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = Routes.RESULT) {
                    composable(Routes.RESULT) { ResultScreen(vm, nav) }
                    composable(Routes.INPUT) { InputScreen(vm, nav) }
                }
            }
        }
        compose.onNodeWithText("조건 확인하고 다시 찾기").performClick()
        compose.onNodeWithText("1 / 2  ·  이름 정보").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AppMode.HANJA, vm.mode); assertEquals("서준", vm.givenName) }
    }
}
