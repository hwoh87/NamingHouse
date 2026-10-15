package com.naminghouse.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val isFinalist: Boolean = false,
    val evaluationRecord: EvaluationRecord? = null,
) {
    val fullHangul: String get() = surname + givenName
    /** 성/이름의 경계까지 포함한다. 점수·메모·후보 상태는 이름의 정체성을 바꾸지 않는다. */
    val key: String get() = listOf(surname, givenName, hanja).joinToString("") { "${it.length}:$it" }

    /** 자유 입력인 메모는 탭·줄바꿈이 필드 경계를 바꾸지 않도록 인코딩한다. */
    fun serialize(): String =
        listOf(surname, givenName, hanja, score.toString(), grade, meaning,
            java.net.URLEncoder.encode(familyNote, "UTF-8"), isFinalist.toString(),
            java.net.URLEncoder.encode(evaluationRecord?.serialize().orEmpty(), "UTF-8")).joinToString("\t")

    companion object {
        fun parse(line: String): FavoriteName? {
            val c = line.split('\t')
            if (c.size < 6) return null
            return FavoriteName(c[0], c[1], c[2], c[3].toIntOrNull() ?: 0, c[4], c[5],
                runCatching { java.net.URLDecoder.decode(c.getOrNull(6).orEmpty(), "UTF-8") }.getOrDefault(""),
                c.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
                c.getOrNull(8)?.takeIf { it.isNotBlank() }?.let { encoded ->
                    runCatching { EvaluationRecord.parse(java.net.URLDecoder.decode(encoded, "UTF-8")) }.getOrNull()
                })
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
class FavoritesStore internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    val flow: Flow<List<FavoriteName>> = dataStore.data.map { prefs ->
        (prefs[FAVORITES] ?: emptySet())
            .mapNotNull(FavoriteName::parse)
            .groupBy { it.key }
            .values.map { it.preferredRecord() }
            .sortedWith(compareByDescending<FavoriteName> { it.score }.thenBy { it.fullHangul })
    }

    suspend fun toggle(item: FavoriteName) {
        editFavorite(item.key) { stored -> if (stored == null) item else null }
    }

    suspend fun updateNote(item: FavoriteName, note: String) {
        editFavorite(item.key) { stored -> stored?.copy(familyNote = note.take(160)) }
    }

    suspend fun setFinalist(item: FavoriteName, isFinalist: Boolean) {
        editFavorite(item.key) { stored -> stored?.copy(isFinalist = isFinalist) }
    }

    /** 호출한 화면의 오래된 사본 대신 실제로 삭제한 저장 레코드를 돌려준다. */
    suspend fun remove(item: FavoriteName): FavoriteName? = editFavorite(item.key) { null }

    /** 삭제 이후 같은 이름을 다시 담았으면 새 메모와 후보 상태를 그대로 둔다. */
    suspend fun restore(item: FavoriteName) {
        editFavorite(item.key) { stored -> stored ?: item }
    }

    /** 파일 전체를 검증한 다음 한 번에 합친다. 기존 이름의 기록은 어떤 필드도 바꾸지 않는다. */
    suspend fun importNames(items: List<FavoriteName>): FavoriteImportResult {
        val incoming = items.toList()
        FavoriteBackup.validate(incoming)
        var result = FavoriteImportResult(0, 0)
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES] ?: emptySet()
            val existingKeys = current.mapNotNull(FavoriteName::parse).map { it.key }.toSet()
            val additions = incoming.filterNot { it.key in existingKeys }
            result = FavoriteImportResult(additions.size, incoming.size - additions.size)
            if (additions.isNotEmpty()) prefs[FAVORITES] = current + additions.map { it.serialize() }
        }
        return result
    }

    private suspend fun editFavorite(key: String, update: (FavoriteName?) -> FavoriteName?): FavoriteName? {
        var previous: FavoriteName? = null
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES] ?: emptySet()
            val matching = current.mapNotNull { line ->
                FavoriteName.parse(line)?.takeIf { it.key == key }?.let { line to it }
            }
            previous = matching.map { it.second }.takeIf { it.isNotEmpty() }?.preferredRecord()
            val updated = update(previous)
            if (updated == previous && matching.size <= 1) return@edit
            val remaining = current - matching.map { it.first }.toSet()
            prefs[FAVORITES] = if (updated == null) remaining else remaining + updated.serialize()
        }
        return previous
    }
}

/** 예전 저장 형식에는 수정 시각이 없다. 중복이 있으면 메모가 있는 레코드를 우선한다. */
private fun List<FavoriteName>.preferredRecord(): FavoriteName = maxWith(
    compareBy<FavoriteName> { it.familyNote.isNotBlank() }
        .thenBy { it.isFinalist }
        .thenBy { it.score }
        .thenBy { it.serialize() }
)

/** 검색과 최종 후보 필터는 원래 이름책 정렬 순서를 유지한다. */
fun favoritesVisible(items: List<FavoriteName>, query: String, finalistsOnly: Boolean): List<FavoriteName> {
    val term = query.trim()
    return items.filter { item ->
        (!finalistsOnly || item.isFinalist) && (term.isBlank() ||
            listOf(item.fullHangul, item.hanja, item.meaning, item.familyNote).any { it.contains(term, ignoreCase = true) })
    }
}

/** 공유용 텍스트 — 카톡·메모 어디에 붙여도 읽히도록 단순하게. */
fun shareText(items: List<FavoriteName>, includeNotes: Boolean = false): String = buildString {
    appendLine("네이밍하우스에서 담아둔 이름")
    appendLine()
    items.forEachIndexed { i, f ->
        appendLine("${i + 1}. ${f.fullHangul} (${f.hanja})${if (f.isFinalist) " · 최종 후보" else ""}")
        appendLine("   ${f.meaning}")
        appendLine("   저장 당시 ${f.score}점 · ${f.grade}")
        if (includeNotes && f.familyNote.isNotBlank()) appendLine("   우리가 고른 이유: ${f.familyNote}")
        if (i < items.lastIndex) appendLine()
    }
}
