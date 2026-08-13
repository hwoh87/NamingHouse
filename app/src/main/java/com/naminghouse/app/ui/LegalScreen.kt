package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 약관·개인정보 처리방침 열람 화면.
 *
 * 문서 원본은 assets/legal 의 마크다운 한 벌이고, 여기서는 제목·소제목·불릿만
 * 아는 아주 작은 규칙으로 그린다 — 법률 문서에 그 이상은 필요 없다.
 * 같은 내용의 HTML 이 docs/legal 에 있어 스토어 등록 URL 로도 쓴다.
 */
@Composable
fun LegalScreen(title: String, assetPath: String, nav: NavHostController) {
    val context = LocalContext.current
    val text by produceState("", assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).bufferedReader().readText()
            }.getOrDefault("문서를 불러오지 못했습니다.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            text.lines().forEach { line ->
                when {
                    line.startsWith("# ") -> Unit // 문서 제목은 앱바가 대신한다
                    line.startsWith("## ") -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        InkStroke(
                            Modifier.fillMaxWidth(0.4f),
                            color = MaterialTheme.colorScheme.primary,
                            alpha = 0.22f,
                            thickness = 3.dp,
                        )
                    }
                    line.startsWith("- ") -> Row {
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            line.removePrefix("- "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    line.isBlank() -> Spacer(Modifier.height(2.dp))
                    else -> Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
