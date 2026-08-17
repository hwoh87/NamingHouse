package com.naminghouse.app

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naminghouse.app.ads.AdsInit
import com.naminghouse.app.ads.ConsentManager
import com.naminghouse.app.ui.DetailScreen
import com.naminghouse.app.ui.FavoritesScreen
import com.naminghouse.app.ui.HanjiBackdrop
import com.naminghouse.app.ui.HomeScreen
import com.naminghouse.app.ui.InkNavBar
import com.naminghouse.app.ui.InputScreen
import com.naminghouse.app.ui.LegalScreen
import com.naminghouse.app.ui.RankingScreen
import com.naminghouse.app.ui.ResultScreen
import com.naminghouse.app.ui.SettingsScreen
import com.naminghouse.app.ui.theme.NamingHouseTheme

/** 화면 이름 — 문자열 라우트를 한 곳에 모아 오타를 줄인다. */
object Routes {
    const val HOME = "home"
    const val INPUT = "input"
    const val RESULT = "result"
    const val DETAIL = "detail"
    const val RANKING = "ranking"
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
        // 동의를 받은 뒤에야 광고 SDK 를 켠다 — 순서가 뒤집히면 GDPR 위반이다.
        // 광고 제거를 산 사람에게도 초기화 자체는 무해하다(배너 쪽에서 따로 막는다).
        ConsentManager.gatherConsent(this) { AdsInit.ensureInitialized(this) }
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

    override fun onResume() {
        super.onResume()
        // 결제 후 복귀와 대기 결제(편의점 결제 등)의 완료를 여기서 잡는다.
        vm.premium.refreshEntitlement()
    }
}

@Composable
private fun AppNavigation(vm: NamingViewModel) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // 결제 안내문은 어느 화면에서든 나올 수 있어 여기서 한 번만 소비한다.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.premium.message.collect { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                vm.premium.message.value = null
            }
        }
    }

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
            composable(Routes.RANKING) { RankingScreen(vm, nav) }
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
