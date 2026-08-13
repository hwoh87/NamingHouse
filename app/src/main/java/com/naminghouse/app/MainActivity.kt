package com.naminghouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naminghouse.app.ui.DetailScreen
import com.naminghouse.app.ui.FavoritesScreen
import com.naminghouse.app.ui.HanjiBackdrop
import com.naminghouse.app.ui.HomeScreen
import com.naminghouse.app.ui.InkNavBar
import com.naminghouse.app.ui.InputScreen
import com.naminghouse.app.ui.LegalScreen
import com.naminghouse.app.ui.ResultScreen
import com.naminghouse.app.ui.SettingsScreen
import com.naminghouse.app.ui.theme.NamingHouseTheme

/** 화면 이름 — 문자열 라우트를 한 곳에 모아 오타를 줄인다. */
object Routes {
    const val HOME = "home"
    const val INPUT = "input"
    const val RESULT = "result"
    const val DETAIL = "detail"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val TERMS = "legal_terms"
    const val PRIVACY = "legal_privacy"

    /** 하단 탭이 붙는 최상위 목적지 */
    val topLevel = setOf(HOME, FAVORITES, SETTINGS)
}

class MainActivity : ComponentActivity() {

    private val vm: NamingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamingHouseTheme(vm.themeMode) {
                // 한지 결과 원산(遠山)은 화면 전체를 덮어 시스템 바 뒤까지 이어지게 하고,
                // 인셋은 안쪽 내용에만 준다 — 모든 화면이 같은 종이 위에 놓인 것처럼 보인다.
                HanjiBackdrop {
                    AppNavigation(vm)
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(vm: NamingViewModel) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        // safeDrawing 에는 IME 가 들어 있다 — 입력 화면의 제출 바가 키보드에 안 가리게.
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (route in Routes.topLevel) {
                InkNavBar(current = route, onNavigate = { target ->
                    if (target != route) {
                        nav.navigate(target) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                })
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            // 화면 전환은 즉시 — 기본 슬라이드/페이드가 앱을 느리게 보이게 한다.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.INPUT) { InputScreen(vm, nav) }
            composable(Routes.RESULT) { ResultScreen(vm, nav) }
            composable(Routes.DETAIL) { DetailScreen(vm, nav) }
            composable(Routes.FAVORITES) { FavoritesScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.TERMS) {
                LegalScreen("이용약관", "legal/terms.md", nav)
            }
            composable(Routes.PRIVACY) {
                LegalScreen("개인정보 처리방침", "legal/privacy.md", nav)
            }
        }
    }
}
