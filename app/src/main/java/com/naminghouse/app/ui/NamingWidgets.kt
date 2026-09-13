package com.naminghouse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.naminghouse.app.ui.theme.*

/** 네이밍하우스의 주요 행동. 상태와 이동을 외부에서 받아 다른 화면에도 그대로 붙인다. */
@Composable
fun NamingHero(hasSavedInput: Boolean, onStart: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = InkShape.large,
        color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(Modifier.padding(InkSpace.s24), verticalArrangement = Arrangement.spacedBy(InkSpace.s16)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("우리 가족의 첫 선물", style = MaterialTheme.typography.labelLarge)
                Text("名", fontFamily = HanjaFamily, style = MaterialTheme.typography.headlineMedium)
            }
            Text("오래 부를 이름,\n마음을 담아 찾다", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("이름의 뜻부터 한자의 조화까지.\n마음에 드는 이름을 천천히 골라보세요.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = InkShape.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(if (hasSavedInput) "이어서 이름 찾기  →" else "무료로 이름 찾기  →",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("이름 추천·점수 무료 · 감명서 한 편 무료 공개",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun NamingToolTile(glyph: String, title: String, description: String,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    InkCard(modifier, onClick = onClick, spacing = InkSpace.s12) {
        Text(glyph, fontFamily = HanjaFamily, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NamingTrustNote() {
    Text("점수는 전통 성명학 기준의 참고 정보예요.\n이름의 뜻과 부르기 좋은 소리, 가족의 마음도 함께 살펴보세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = InkSpace.s8))
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun NamingHeroPreview() {
    NamingHouseTheme { NamingHero(false, {}) }
}
