package com.naminghouse.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.*
import com.naminghouse.app.ui.theme.NamingHouseTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 실제 화면·실제 작명 엔진으로 검증한다. 별도 테스트 APK에서만 실행한다. */
class DesignJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: NamingViewModel
    private lateinit var nav: NavHostController
    private var premium by mutableStateOf(false)
    private var dark by mutableStateOf(false)
    private var large by mutableStateOf(false)

    private fun launch() {
        compose.runOnUiThread {
            vm = NamingViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        }
        compose.setContent {
            nav = rememberNavController()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                NamingHouseTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    HanjiBackdrop { AppNavigation(vm, nav) }
                    if (premium) PremiumSheet(vm) { premium = false }
                }
            }
        }
        compose.waitUntil(20_000) { vm.hanjaDb != null && vm.namePool != null }
        compose.runOnIdle {
            vm.favorites.toList().forEach(vm::removeFavorite)
            vm.onSurnameChanged("김")
            vm.surnameHanja.value = listOf(vm.surnameCandidates(0).first { it.char == '金' })
            vm.preBirth = true
            vm.popularOnly = true
            vm.singleName = false
            vm.dolimja = ""
            vm.year = "2026"; vm.month = "8"; vm.day = "11"
        }
        compose.waitUntil(5_000) { vm.favorites.isEmpty() }
        android.os.SystemClock.sleep(2500) // Let the emulator's Play connection notice dismiss.
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(if (name.contains("premium")) 4000 else 700) // Wait for GPU composition and transient Play notices.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "design-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) { try { block(this) } finally { recycle() } }

    @Test fun completeNamingJourneyAndScreens() {
        launch()
        capture("01-home")
        compose.runOnIdle { vm.mode = AppMode.RECOMMEND; nav.navigate(Routes.INPUT) }
        compose.onNodeWithText("다음 · 이름 취향").assertIsEnabled()
        capture("02-input-name")
        // 누락은 첫 단계에서 발견하며, 뒤로 가도 입력은 유지된다.
        compose.runOnIdle { vm.surnameHanja.value = listOf(null) }
        compose.onNodeWithText("다음 · 이름 취향").performClick()
        compose.onNodeWithText("성씨를 입력하고 한자를 선택해 주세요.").assertIsDisplayed()
        compose.runOnIdle { vm.surnameHanja.value = listOf(vm.surnameCandidates(0).first { it.char == '金' }) }
        compose.onNodeWithText("다음 · 이름 취향").performClick()
        capture("03-input-preferences")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("가족에게서 이어받은 성").assertIsDisplayed()
        compose.onNodeWithText("다음 · 이름 취향").performClick()
        compose.onNodeWithText("다음 · 출생 정보").performClick()
        compose.runOnIdle { vm.preBirth = false }
        capture("04-input-birth")
        compose.runOnIdle { vm.preBirth = true }
        compose.onNodeWithText("이름 추천 받기").performClick()
        compose.waitUntil(30_000) { vm.candidates.isNotEmpty() && !vm.busy }
        compose.waitForIdle()
        assertNull(vm.errorMessage)
        compose.onNodeWithText("마음에 닿는 이름").assertExists()
        capture("05-results")
        // 카드 안의 저장 버튼은 상세 이동을 일으키지 않는다.
        compose.onAllNodesWithContentDescription("이름 담아두기").onFirst().performClick()
        compose.waitUntil(5_000) { vm.favorites.isNotEmpty() }
        assertEquals(Routes.RESULT, nav.currentDestination?.route)
        compose.runOnIdle {
            val c = vm.candidates.first()
            vm.selected = c.evaluation to c.stat
            nav.navigate(Routes.DETAIL)
        }
        compose.onNodeWithText("이름 이야기").assertIsDisplayed()
        capture("06-detail-overview")
        compose.onNodeWithText("이 이름으로 첫 인사 건네보기").performScrollTo().performClick()
        compose.onNodeWithText("우리 아이에게 건네는 첫 인사").performScrollTo().assertIsDisplayed()
        capture("23-first-greeting")
        compose.onNodeWithText("첫 인사 접기").performScrollTo().performClick()
        compose.onNodeWithTag("name-cover").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = File(context.getExternalFilesDir(null), "design-review/sample-certificate.pdf")
        val cover = compose.onNodeWithTag("name-cover").captureToImage().asAndroidBitmap()
        val selected = requireNotNull(vm.selected)
        writeCertificatePdf(context, Uri.fromFile(pdf), cover, selected.first, vm.saju, selected.second,
            birthLine = null, issuedDate = "2026년 9월 14일")
        assertTrue(pdf.length() > 1000)
        compose.onNodeWithText("한자 풀이", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("글자 풀이").assertExists()
        capture("07-detail-hanja")
        compose.onNodeWithText("성명 분석", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("수리사격 (원형이정)").assertExists()
        capture("08-detail-analysis")
        compose.runOnIdle {
            vm.candidates.take(3).forEach { if (!vm.isFavorite(it.evaluation)) vm.toggleFavorite(it.evaluation) }
            nav.navigate(Routes.FAVORITES)
        }
        compose.waitUntil(5_000) { vm.favorites.size >= 3 }
        capture("09-favorites")
        repeat(2) { compose.onAllNodesWithText("비교에 추가").onFirst().performScrollTo().performClick() }
        compose.onNodeWithText("2/3 이름 비교").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("우리 가족의 이름 후보").assertExists()
        capture("10-comparison")
        compose.runOnIdle { nav.navigate(Routes.RANKING) }
        capture("11-ranking")
        compose.runOnIdle { nav.navigate(Routes.SETTINGS) }
        capture("12-settings")
        compose.runOnIdle { premium = true }
        capture("13-premium")
    }

    @Test fun darkAndLargeTextRemainUsable() {
        launch()
        compose.runOnIdle { dark = true; large = true; vm.mode = AppMode.HANJA; vm.onGivenNameChanged("서윤"); nav.navigate(Routes.INPUT) }
        compose.onNodeWithText("다음 · 출생 정보").assertIsDisplayed().performClick()
        compose.onNodeWithText("한자 조합 찾기").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { vm.hanjaCombos.isNotEmpty() && !vm.busy }
        assertNull(vm.errorMessage)
        capture("14-dark-large-results")
        compose.runOnIdle { vm.selected = vm.hanjaCombos.first() to null; nav.navigate(Routes.DETAIL) }
        capture("15-dark-large-detail")
        compose.onNodeWithText("한자 풀이", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("글자 풀이").performScrollTo().assertIsDisplayed()
        capture("16-dark-large-hanja")
        compose.runOnIdle { premium = true }
        compose.onNodeWithText("구매 복원").performScrollTo()
        capture("17-dark-large-premium")
        compose.onNodeWithText("구매 복원").assertIsDisplayed()
    }
    @Test fun evaluateModeAndSelectionDialogs() {
        launch()
        compose.runOnIdle { vm.mode = AppMode.EVALUATE; vm.onGivenNameChanged("서윤"); vm.givenHanja.value = listOf(null, null); nav.navigate(Routes.INPUT) }
        capture("18-evaluate-input")
        compose.onNodeWithText("다음 · 출생 정보").performClick()
        compose.onNodeWithText("이름의 한자를 모두 선택해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("서 · 한자 고르기").performScrollTo().performClick()
        compose.onNodeWithText("'서'에 담을 뜻").assertIsDisplayed()
        capture("19-hanja-picker")
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle { vm.givenHanja.value = vm.givenNameSyllables.indices.map { vm.givenNameCandidates(it).first() } }
        compose.onNodeWithText("다음 · 출생 정보").performClick()
        compose.runOnIdle { vm.preBirth = false }
        compose.onNodeWithText("서울").performScrollTo().performClick()
        compose.onAllNodesWithText("출생 지역").onLast().assertIsDisplayed()
        capture("20-city-picker")
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle { vm.preBirth = true }
        compose.onNodeWithText("이름 감명 보기").performClick()
        compose.waitUntil(20_000) { vm.evaluation != null && !vm.busy }
        compose.onNodeWithText("이름 이야기").assertIsDisplayed()
        capture("21-evaluate-detail")
        compose.runOnIdle { nav.navigate(Routes.TERMS) }
        compose.onNodeWithText("이용약관").assertIsDisplayed()
        capture("22-terms")
    }

}
