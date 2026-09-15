package com.naminghouse.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 담아둔 이름 하나 */
data class FavoriteName(
    val surname: String,
    val givenName: String,
    /** 성을 포함한 전체 한자 */
    val hanja: String,
    val score: Int,
    val grade: String,
    val meaning: String,
    val familyNote: String = "",
) {
    val fullHangul: String get() = surname + givenName

    /** 저장용 한 줄. 탭은 값에 나올 수 없으니 구분자로 안전하다. */
    fun serialize(): String =
        listOf(surname, givenName, hanja, score.toString(), grade, meaning, java.net.URLEncoder.encode(familyNote, "UTF-8")).joinToString("\t")

    companion object {
        fun parse(line: String): FavoriteName? {
            val c = line.split('\t')
            if (c.size < 6) return null
            return FavoriteName(c[0], c[1], c[2], c[3].toIntOrNull() ?: 0, c[4], c[5],
                runCatching { java.net.URLDecoder.decode(c.getOrNull(6).orEmpty(), "UTF-8") }.getOrDefault(""))
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "naminghouse")
private val FAVORITES = stringSetPreferencesKey("favorites")

/**
 * 즐겨찾기 저장소.
 *
 * 추천 60개를 훑고 서너 개를 추려 배우자와 상의하는 게 실제 사용 흐름인데,
 * 담아둘 곳이 없으면 스크린샷 말고는 방법이 없다.
 */
class FavoritesStore(private val context: Context) {

    val flow: Flow<List<FavoriteName>> = context.dataStore.data.map { prefs ->
        (prefs[FAVORITES] ?: emptySet())
            .mapNotNull(FavoriteName::parse)
            .sortedWith(compareByDescending<FavoriteName> { it.score }.thenBy { it.fullHangul })
    }

    suspend fun toggle(item: FavoriteName) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES] ?: emptySet()
            val line = item.serialize()
            // 같은 이름+한자면 같은 항목으로 본다(점수는 사주가 바뀌면 달라질 수 있다)
            val existing = current.firstOrNull { sameName(it, line) }
            prefs[FAVORITES] = if (existing != null) current - existing else current + line
        }
    }

    suspend fun updateNote(item: FavoriteName, note: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES] ?: emptySet()
            val old = current.firstOrNull { sameName(it, item.serialize()) } ?: return@edit
            val stored = FavoriteName.parse(old) ?: return@edit
            prefs[FAVORITES] = current - old + stored.copy(familyNote = note.take(160)).serialize()
        }
    }

    suspend fun remove(item: FavoriteName) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES] ?: emptySet()
            prefs[FAVORITES] = current.filterNot { sameName(it, item.serialize()) }.toSet()
        }
    }

    private fun sameName(a: String, b: String): Boolean {
        val ka = a.split('\t').take(3)
        val kb = b.split('\t').take(3)
        return ka == kb
    }
}

/** 공유용 텍스트 — 카톡·메모 어디에 붙여도 읽히도록 단순하게. */
fun shareText(items: List<FavoriteName>): String = buildString {
    appendLine("네이밍하우스에서 담아둔 이름")
    appendLine()
    items.forEachIndexed { i, f ->
        appendLine("${i + 1}. ${f.fullHangul} (${f.hanja})")
        appendLine("   ${f.meaning}")
        appendLine("   종합 ${f.score}점 · ${f.grade}")
        if (f.familyNote.isNotBlank()) appendLine("   우리가 고른 이유: ${f.familyNote}")
        if (i < items.lastIndex) appendLine()
    }
}
