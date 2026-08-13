package com.naminghouse.engine

import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.eval.summarize
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.saju.SajuNamingService
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NameSummaryTest {

    private val db: HanjaDb by lazy {
        listOf("src/main/assets/hanja.tsv", "engine/src/main/assets/hanja.tsv")
            .map(::File).first(File::exists)
            .bufferedReader().useLines { HanjaDb.parse(it) }
    }

    private fun eval(surname: Char, n1: Char, n2: Char, hangul: String, withSaju: Boolean = true) =
        NameEvaluator.evaluate(
            surname = hangul.first().toString(),
            givenName = hangul.drop(1),
            surnameHanja = listOf(db.byChar.getValue(surname)),
            givenHanja = listOf(db.byChar.getValue(n1), db.byChar.getValue(n2)),
            saju = if (withSaju) SajuNamingService.analyze(BirthInput(2026, 3, 15, 9, 20, Gender.M)) else null,
        )

    @Test
    fun `뜻줄은 훈에 글자를 병기한다`() {
        // 道 "길 도" → "길(道)". 음(도)은 떼고 글자를 붙인다.
        val line = meaningLine(eval('金', '道', '沇', "김도윤"))
        assertTrue(line, line.startsWith("길(道)"))
        assertTrue(line, line.contains(" + "))
        assertTrue(line, line.contains("(沇)"))
        assertFalse("음이 남아 있으면 안 됨: $line", line.contains("길 도"))
    }

    @Test
    fun `동음이의 훈도 글자로 구분된다`() {
        // 道·永 둘 다 훈이 "길" — 예전 표기 "길 · 길" 로는 같은 글자처럼 보였다
        val line = meaningLine(eval('金', '道', '永', "김도영"))
        assertTrue(line, line.contains("(道)"))
        assertTrue(line, line.contains("(永)"))
        assertEquals("길(道) + 길(永)", line)
    }

    @Test
    fun `훈은 사전 그대로 두고 변형하지 않는다`() {
        // 명사화(ㄹ→ㅁ)는 永(길)→"김" 같은 오차가 나므로 하지 않는다
        val line = meaningLine(eval('金', '道', '永', "김도영"))
        assertFalse("永 의 훈이 변형됨: $line", line.contains("김("))
        assertFalse(line, line.contains("긺"))
    }

    @Test
    fun `뜻이 없는 글자는 미상으로 표기한다`() {
        val noMeaning = db.entries.firstOrNull { it.meaning.isBlank() && it.usableForNaming }
        if (noMeaning != null) {
            val e = NameEvaluator.evaluate(
                surname = "김", givenName = "가나",
                surnameHanja = listOf(db.byChar.getValue('金')),
                givenHanja = listOf(noMeaning, db.byChar.getValue('永')),
                saju = null,
            )
            assertTrue(meaningLine(e), meaningLine(e).contains("뜻 미상"))
        }
    }

    @Test
    fun `총평 첫 문장에 이름과 점수와 등급이 들어간다`() {
        val e = eval('金', '道', '沇', "김도윤")
        val s = summarize(e)
        assertTrue(s.verdict, s.verdict.contains("김도윤"))
        assertTrue(s.verdict, s.verdict.contains("金道沇"))
        assertTrue(s.verdict, s.verdict.contains("${e.score}점"))
        assertTrue(s.verdict, s.verdict.contains(e.grade))
    }

    @Test
    fun `강점이든 주의점이든 최소 한 줄은 나온다`() {
        val s = summarize(eval('金', '道', '沇', "김도윤"))
        assertTrue(s.strengths.isNotEmpty() || s.cautions.isNotEmpty())
    }

    @Test
    fun `4격이 모두 길수면 강점으로, 아니면 주의점으로 짚는다`() {
        val e = eval('金', '道', '沇', "김도윤")
        val s = summarize(e)
        if (e.suri.allGood) {
            assertTrue(s.strengths.any { "원형이정" in it })
            assertFalse(s.cautions.any { "수리사격" in it })
        } else {
            assertTrue(s.cautions.any { "수리사격" in it })
        }
    }

    @Test
    fun `사주가 없으면 보완 관련 문장이 없다`() {
        val s = summarize(eval('金', '道', '沇', "김도윤", withSaju = false))
        assertFalse((s.strengths + s.cautions).any { "사주" in it || "보완" in it })
    }

    @Test
    fun `조사가 문법에 맞는다 - 土 으로 같은 비문이 없어야 한다`() {
        // 오행 한자 뒤 조사는 그 음의 받침에 따라 갈린다(목·금=으로, 화·토·수=로)
        val bad = listOf("火 으로", "土 으로", "水 으로", "木 로", "金 로")
        listOf(
            eval('金', '道', '沇', "김도윤"),
            eval('金', '垈', '永', "김대영"),
            eval('李', '宙', '河', "이주하"),
        ).forEach { e ->
            val all = summarize(e).let { it.verdict + it.strengths.joinToString(" ") + it.cautions.joinToString(" ") }
            bad.forEach { b -> assertFalse("$b 비문 발견: $all", all.contains(b)) }
        }
    }

    @Test
    fun `비화가 섞이면 상생이라고 단정하지 않는다`() {
        // 김대영 金(8) 垈(8) 永(5) → 金 비화 金 생 土 : "상생합니다" 로 쓰면 틀린 말
        val e = eval('金', '垈', '永', "김대영")
        val s = summarize(e)
        val suriLine = (s.strengths + s.cautions).firstOrNull { "수리오행" in it }
        if (suriLine != null && e.suriOheng.relations.any { it.label == "비화" }) {
            assertFalse(suriLine, suriLine.endsWith("상생합니다."))
        }
    }

    @Test
    fun `모든 문장이 서로 다르고 비어 있지 않다`() {
        val s = summarize(eval('金', '道', '沇', "김도윤"))
        val all = s.strengths + s.cautions + s.suggestions
        assertTrue(all.none { it.isBlank() })
        assertTrue("중복 문장 없음", all.size == all.distinct().size)
    }

    @Test
    fun `주의점이 없으면 제안도 없다`() {
        // 김대영은 전 축이 길한 대표 사례 — 고칠 것이 없는데 제안이 나오면 안 된다
        val e = eval('金', '垈', '永', "김대영")
        val s = summarize(e)
        if (s.cautions.isEmpty()) assertTrue(s.suggestions.joinToString(), s.suggestions.isEmpty())
    }

    @Test
    fun `수리사격이 흉하면 한자 추천으로 안내한다`() {
        // 흉수 조합을 실제 평가로 찾아 고정한다 — 획수 8+9+9=26(영웅시비) 등
        val e = NameEvaluator.evaluate(
            surname = "김", givenName = "성정",
            surnameHanja = listOf(db.byChar.getValue('金')),
            givenHanja = listOf(db.byChar.getValue('城'), db.byChar.getValue('貞')),
            saju = null,
        )
        val s = summarize(e)
        if (!e.suri.allGood) {
            assertTrue(s.suggestions.joinToString(), s.suggestions.any { "한자 추천" in it })
        }
    }

    @Test
    fun `제안은 최대 3개다`() {
        listOf(
            eval('金', '道', '沇', "김도윤"),
            eval('金', '垈', '永', "김대영"),
            eval('李', '宙', '河', "이주하"),
        ).forEach { e ->
            assertTrue(summarize(e).suggestions.size <= 3)
        }
    }
}
