package com.naminghouse.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.shareText

/**
 * 담아둔 이름 목록.
 *
 * 추천 60개를 훑고 서너 개를 추려 배우자·부모님과 상의하는 게 실제 흐름이라,
 * 담아두기와 내보내기가 같이 있어야 쓸모가 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(vm: NamingViewModel) {
    if (!vm.showFavorites) return
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = { vm.showFavorites = false }) {
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "담아둔 이름 ${vm.favorites.size}개",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (vm.favorites.isNotEmpty()) {
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(vm.favorites))
                        }
                        context.startActivity(Intent.createChooser(send, "이름 공유"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("공유")
                    }
                }
            }

            if (vm.favorites.isEmpty()) {
                Text(
                    "추천 목록에서 별을 눌러 마음에 드는 이름을 담아 보세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                vm.favorites.forEach { f ->
                    SectionCard(f.fullHangul) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.hanja, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    f.meaning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "종합 ${f.score}점 · ${f.grade}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { vm.removeFavorite(f) }) {
                                Icon(Icons.Filled.Close, contentDescription = "빼기")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
