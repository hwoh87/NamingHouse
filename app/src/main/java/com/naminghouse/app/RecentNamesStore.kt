package com.naminghouse.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recentNamesDataStore by preferencesDataStore(name = "recent-names")
private val RECENT_NAMES = stringPreferencesKey("names_v1")
private const val RECENT_NAME_LIMIT = 16

/** Recent names are navigation history, separate from the family's saved favorites and evaluations. */
class RecentNamesStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.recentNamesDataStore)

    val flow: Flow<List<String>> = store.data.map { decodeRecentNames(it[RECENT_NAMES]) }

    suspend fun rememberName(name: String) {
        val valid = requireNotNull(normalizedDiscoveryName(name)) { "한글 이름을 1~3글자로 입력해 주세요" }
        store.edit { preferences ->
            val previous = decodeRecentNames(preferences[RECENT_NAMES])
            preferences[RECENT_NAMES] = (listOf(valid) + previous.filterNot { it == valid })
                .take(RECENT_NAME_LIMIT).joinToString("\n")
        }
    }

    suspend fun clear() { store.edit { it.remove(RECENT_NAMES) } }
}

private fun decodeRecentNames(raw: String?): List<String> = raw.orEmpty().lineSequence()
    .mapNotNull(::normalizedDiscoveryName).distinct().take(RECENT_NAME_LIMIT).toList()
