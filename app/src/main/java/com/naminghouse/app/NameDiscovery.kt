package com.naminghouse.app

import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.gen.PoolName
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import java.text.Normalizer
import java.util.Locale

enum class DiscoveryMode(val label: String) { SOUND("소리로 찾기"), MEANING("한자 뜻으로 찾기") }
enum class DiscoveryPosition(val label: String) { ANY("포함"), START("시작 글자"), END("끝 글자") }
enum class DiscoveryGender(val label: String) { ALL("전체"), MALE("남아"), FEMALE("여아") }

data class NameDiscoveryQuery(
    val text: String = "",
    val mode: DiscoveryMode = DiscoveryMode.SOUND,
    val position: DiscoveryPosition = DiscoveryPosition.ANY,
    val gender: DiscoveryGender = DiscoveryGender.ALL,
    val length: Int? = null,
)

/** A matching Hanja is a possible letter for a syllable, never an asserted meaning of the whole name. */
data class DiscoveredName(val poolName: PoolName, val matchingHanja: List<HanjaEntry> = emptyList())

/** Normalization is used for searching only; UI text is kept intact for Korean IME composition. */
private fun discoveryText(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

/** Direct names and recent history contain only completed, one-to-three syllable Hangul names. */
fun normalizedDiscoveryName(text: String): String? = discoveryText(text).takeIf {
    it.length in 1..3 && it.all(::isHangulSyllable)
}

private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

private fun initialOf(char: Char): Char? = when {
    isHangulSyllable(char) -> INITIALS[(char.code - 0xAC00) / (21 * 28)]
    char in INITIALS -> char
    char.code in 0x1100..0x1112 -> INITIALS[char.code - 0x1100]
    else -> null
}

/** Match literal syllables and initial consonants at syllable boundaries; query text is never a regex. */
fun matchesNameSound(name: String, text: String, position: DiscoveryPosition = DiscoveryPosition.ANY): Boolean {
    val query = discoveryText(text).filterNot(Char::isWhitespace)
    return matchesNormalizedNameSound(name, query, position)
}

private fun matchesNormalizedNameSound(name: String, query: String, position: DiscoveryPosition): Boolean {
    if (query.isEmpty()) return true
    if (query.length > name.length) return false
    val starts = when (position) {
        DiscoveryPosition.ANY -> 0..(name.length - query.length)
        DiscoveryPosition.START -> 0..0
        DiscoveryPosition.END -> (name.length - query.length)..(name.length - query.length)
    }
    return starts.any { start ->
        query.indices.all { offset ->
            val token = query[offset]
            val syllable = name[start + offset]
            if (isHangulSyllable(token)) token == syllable
            else initialOf(token)?.let { it == initialOf(syllable) } ?: (token == syllable)
        }
    }
}

/** Build once per loaded database, then query off the main thread. No scores or statistical gender inference. */
class NameDiscoveryIndex(pool: NamePool, db: HanjaDb, checkActive: () -> Unit = {}) {
    private val names = pool.names.distinctBy { it.name }.sortedBy { it.name }
    private val meanings = db.entries.asSequence().filter { it.usableForNaming }.map {
        checkActive()
        it to discoveryText(it.meaning)
    }.toList()

    fun search(query: NameDiscoveryQuery, checkActive: () -> Unit = {}): List<DiscoveredName> {
        checkActive()
        require(query.length == null || query.length in 1..3) { "지원하지 않는 이름 길이입니다" }
        val term = discoveryText(query.text)
        val soundTerm = term.filterNot(Char::isWhitespace)
        if (query.mode == DiscoveryMode.MEANING && term.isEmpty()) return emptyList()
        val matchedReadings = if (query.mode == DiscoveryMode.MEANING) {
            val byReading = mutableMapOf<String, MutableList<HanjaEntry>>()
            meanings.forEach { (entry, meaning) ->
                checkActive()
                if (meaning.contains(term) || entry.char.toString() == term) {
                    entry.readings.forEach { reading -> byReading.getOrPut(reading) { mutableListOf() }.add(entry) }
                }
            }
            byReading
        } else emptyMap()
        return buildList {
            names.forEach { name ->
                checkActive()
                if (query.length != null && name.name.length != query.length) return@forEach
                if (query.gender == DiscoveryGender.MALE && name.gender != 'M' && name.gender != 'U') return@forEach
                if (query.gender == DiscoveryGender.FEMALE && name.gender != 'F' && name.gender != 'U') return@forEach
                if (query.mode == DiscoveryMode.SOUND) {
                    if (matchesNormalizedNameSound(name.name, soundTerm, query.position)) add(DiscoveredName(name))
                } else {
                    val matched = name.name.flatMap { matchedReadings[it.toString()].orEmpty() }.distinctBy { it.char }
                    if (matched.isNotEmpty()) add(DiscoveredName(name, matched))
                }
            }
        }
    }
}
