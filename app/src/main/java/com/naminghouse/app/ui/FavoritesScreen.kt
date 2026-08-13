package com.naminghouse.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.R
import com.naminghouse.app.shareText
import com.naminghouse.app.ui.theme.HanjaFamily
import com.naminghouse.app.ui.theme.InkTheme

/** 담아둔 이름을 여는 별 — 결과 화면 앱바에서 쓴다. */
@Composable
fun FavoritesAction(count: Int, onClick: () -> Unit) {
    val empty = count == 0
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (!empty) {
                    Badge(
                        containerColor = InkTheme.colors.seal,
                        contentColor = InkTheme.colors.onSeal,
                    ) { Text("$count") }
                }
            }
        ) {
            Icon(
                if (empty) Icons.Outlined.StarBorder else Icons.Filled.Star,
                contentDescription = "담아둔 이름",
                tint = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else InkTheme.colors.gold,
            )
        }
    }
}

/**
 * 담아둔 이름 — 하단 탭의 한 화면.
 *
 * 추천 60개를 훑고 서너 개를 추려 배우자·부모님과 상의하는 게 실제 흐름이라,
 * 담아두기와 내보내기가 같이 있어야 쓸모가 있다.
 */
@Composable
fun FavoritesScreen(vm: NamingViewModel) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "담아둔 이름",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${vm.favorites.size}개",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.favorites.isNotEmpty()) {
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(vm.favorites))
                        }
                        context.startActivity(Intent.createChooser(send, "이름 공유"))
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("공유")
                }
            }
        }
        InkStroke(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, alpha = 0.22f)

        if (vm.favorites.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                InkArt(R.drawable.ink_plum, Modifier.size(190.dp), alpha = 0.45f)
                Text(
                    "추천 목록에서 별을 눌러\n마음에 드는 이름을 담아 보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else {
            vm.favorites.forEach { f ->
                InkCard(contentPadding = PaddingValues(14.dp), spacing = 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    f.fullHangul,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    f.hanja,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = HanjaFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                f.meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "종합 ${f.score}점 · ${f.grade}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(onClick = { vm.removeFavorite(f) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "빼기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
