package com.naminghouse.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.naminghouse.app.ui.FavoriteBackupSheet
import com.naminghouse.app.ui.FavoriteImportConfirmation
import com.naminghouse.app.ui.FavoriteShareSheet
import com.naminghouse.app.ui.theme.NamingHouseTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoriteBackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val name = FavoriteName("김", "도윤", "金度侖", 94, "대길", "너그러운 사람", "가족끼리만 적어둔 메모", true)

    @Test fun sharingStartsWithoutNotesAndSendsExactlyThePreview() {
        var sent: String? = null
        compose.setContent {
            NamingHouseTheme(ThemeMode.LIGHT) { FavoriteShareSheet(listOf(name), onDismiss = {}, onShare = { sent = it }) }
        }
        compose.onNodeWithTag("share-include-notes").assertIsOff()
        compose.onNodeWithTag("favorite-share-preview").assertTextEquals(shareText(listOf(name)))
        screenshot("favorite-share-preview")
        compose.onNodeWithTag("share-include-notes").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag("favorite-share-preview").assertTextEquals(shareText(listOf(name), true))
        compose.onNodeWithTag("favorite-share-send").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(shareText(listOf(name), true), sent) }
    }

    @Test fun emptyNamebookCanImportAndExportOptionsStartPrivate() {
        compose.setContent {
            NamingHouseTheme(ThemeMode.LIGHT) {
                FavoriteBackupSheet(emptyList(), onImport = { FavoriteImportResult(it.size, 0) }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("backup-import-file").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("파일로 보관").performScrollTo().performClick()
        compose.onNodeWithTag("backup-include-notes").assertIsOff()
        compose.onNodeWithTag("backup-include-birth").assertIsOff()
        compose.onNodeWithTag("backup-export").performScrollTo().assertIsNotEnabled()
        screenshot("favorite-backup-options")
    }

    @Test fun importPreviewRequiresAnExplicitConfirmationAndSkipsExistingNames() {
        var confirmed = false
        val added = name.copy(givenName = "서윤", hanja = "金瑞潤")
        val preview = FavoriteBackup.preview(listOf(name, added), listOf(name.copy(familyNote = "기존 메모")))
        compose.setContent {
            NamingHouseTheme(ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FavoriteImportConfirmation(preview, false) { confirmed = true }
                }
            }
        }
        compose.onNodeWithText("추가 1개 · 이미 저장됨 1개").assertExists()
        compose.runOnIdle { assertFalse(confirmed) }
        compose.onNodeWithTag("backup-import-confirm").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test fun anAlreadyImportedFileCannotBeConfirmedAgain() {
        compose.setContent {
            NamingHouseTheme(ThemeMode.LIGHT) {
                FavoriteImportConfirmation(FavoriteBackup.preview(listOf(name), listOf(name)), false) { fail("Duplicate import must stay disabled") }
            }
        }
        compose.onNodeWithTag("backup-import-confirm").assertIsNotEnabled()
    }

    private fun screenshot(name: String) {
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
}
