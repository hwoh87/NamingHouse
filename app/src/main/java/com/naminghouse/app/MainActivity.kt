package com.naminghouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.naminghouse.app.ui.InputScreen
import com.naminghouse.app.ui.ResultScreen
import com.naminghouse.app.ui.theme.NamingHouseTheme

class MainActivity : ComponentActivity() {

    private val vm: NamingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NamingHouseTheme {
                // Surface 는 화면 전체를 덮어 시스템 바 뒤까지 앱 배경색이 이어지게 하고,
                // 인셋은 안쪽 내용에만 준다.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.safeDrawingPadding()) {
                        BackHandler(enabled = vm.screen == AppScreen.RESULT) { vm.backToInput() }
                        when (vm.screen) {
                            AppScreen.INPUT -> InputScreen(vm)
                            AppScreen.RESULT -> ResultScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
