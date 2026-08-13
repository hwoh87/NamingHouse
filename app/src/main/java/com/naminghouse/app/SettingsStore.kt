package com.naminghouse.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naminghouse.engine.oheng.BaleumSchool
import kotlinx.coroutines.flow.first

/** 화면 모드 — 시스템을 따르거나 한쪽으로 고정한다. */
enum class ThemeMode(val label: String) {
    SYSTEM("시스템"),
    LIGHT("라이트"),
    DARK("다크"),
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

private val THEME_MODE = stringPreferencesKey("themeMode")
private val SCHOOL = stringPreferencesKey("school")

/**
 * 설정 화면의 값들.
 *
 * 성명학 학파는 원래 입력 화면에 실려 [InputStore] 로 저장됐지만, 입력이 아니라
 * 기준이므로 설정으로 옮겼다. 여기 값이 없으면 InputStore 의 옛 값을 그대로 쓴다
 * (업데이트 전 사용자의 선택이 날아가면 안 된다).
 */
class SettingsStore(private val context: Context) {

    data class Settings(val themeMode: ThemeMode, val school: BaleumSchool?)

    suspend fun load(): Settings {
        val p = context.settingsStore.data.first()
        return Settings(
            themeMode = p[THEME_MODE]?.let { m -> runCatching { ThemeMode.valueOf(m) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            school = p[SCHOOL]?.let { s -> runCatching { BaleumSchool.valueOf(s) }.getOrNull() },
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun saveSchool(school: BaleumSchool) {
        context.settingsStore.edit { it[SCHOOL] = school.name }
    }
}
