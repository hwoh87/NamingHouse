package com.naminghouse.engine

import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.saju.SajuNamingService
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Test
import java.io.File

/**
 * 대규모 스윕 — 실제 성씨 분포 × 실제 인기 이름 × 여러 사주로 1만 건 넘게 채점해
 * 배점 곡선이 특정 성씨·특정 사주에 쏠리지 않는지 본다. 단정 없는 덤프다.
 *
 * 실행: ./gradlew :engine:testDebugUnitTest --tests '*ScoreSweepTest'
 */
class ScoreSweepTest {

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "engine/src/main/assets/$name")
            .map(::File).first(File::exists)

    private val db = asset("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }

    /** 2015 인구총조사 상위 성씨 — 인구 비중이 큰 순서대로 (전체의 약 60%) */
    private val SURNAMES = listOf(
        "김" to '金', "이" to '李', "박" to '朴', "최" to '崔', "정" to '鄭',
        "강" to '姜', "조" to '趙', "윤" to '尹', "장" to '張', "임" to '林',
        "한" to '韓', "오" to '吳', "서" to '徐', "신" to '申', "권" to '權',
        "황" to '黃', "안" to '安', "송" to '宋', "전" to '全', "홍" to '洪',
    )

    /** 계절·시각을 흩어 용신이 서로 다르게 나오도록 고른 출생 */
    private val BIRTHS = listOf(
        BirthInput(2026, 1, 8, 3, 10, Gender.F),
        BirthInput(2026, 3, 15, 9, 20, Gender.M),
        BirthInput(2025, 5, 30, 14, 5, Gender.F),
        BirthInput(2025, 7, 21, 23, 40, Gender.M),
        BirthInput(2024, 9, 3, 6, 55, Gender.F),
        BirthInput(2024, 11, 17, 18, 30, Gender.M),
    )

    private fun popularNames(limit: Int): List<String> =
        asset("name-stats.tsv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { l ->
                val c = l.split('\t')
                if (c.size < 4) return@mapNotNull null
                val total = (c[2].toIntOrNull() ?: 0) + (c[3].toIntOrNull() ?: 0)
                c[0] to total
            }.sortedByDescending { it.second }.map { it.first }.toList()
        }.filter { it.length == 2 }.take(limit)

    /**
     * 작명소가 골랐을 법한 한자 — 인명 적합도가 높은 상위 몇 자.
     * 앱이 점수로 고른 '최적 조합'이 아니라 '그럴듯한 조합'이어야 실사용에 가깝다.
     */
    private fun pick(syllable: String, n: Int): List<HanjaEntry> =
        db.candidatesFor(syllable).filter { it.usableForNaming }.take(n)

    private fun histogram(scores: List<Int>, buckets: IntProgression = 0..100 step 10): String =
        buckets.filter { it < 100 }.joinToString("\n") { lo ->
            val hi = lo + 10
            val c = scores.count { it >= lo && (if (hi == 100) it <= 100 else it < hi) }
            val bar = "█".repeat((60.0 * c / scores.size).toInt())
            "  %3d-%3d %s %5d (%4.1f%%)".format(lo, hi, bar.padEnd(60), c, 100.0 * c / scores.size)
        }

    @Test
    fun sweep() {
        val names = popularNames(120)
        val sajus: List<Pair<BirthInput, SajuSummary>> = BIRTHS.map { it to SajuNamingService.analyze(it) }

        val all = ArrayList<Triple<String, String, NameEvaluation>>(20000)
        for ((sur, surChar) in SURNAMES) {
            val surHanja = db.byChar[surChar] ?: continue
            for ((_, saju) in sajus) {
                for (nm in names) {
                    val c1 = pick(nm[0].toString(), 2)
                    val c2 = pick(nm[1].toString(), 2)
                    if (c1.isEmpty() || c2.isEmpty()) continue
                    // 음절당 2자씩 → 이름 하나에 최대 4조합. 그중 첫 조합만 써서 편중을 막는다.
                    val combo = listOf(c1.first(), c2.first())
                    all += Triple(sur, nm, NameEvaluator.evaluate(sur, nm, listOf(surHanja), combo, saju))
                }
            }
        }

        val scores = all.map { it.third.score }
        println("\n══════ 스윕 ${all.size}건 — 성씨 ${SURNAMES.size} × 사주 ${sajus.size} × 이름 ${names.size} ══════")
        println("평균 ${"%.1f".format(scores.average())} / 중앙 ${scores.sorted()[scores.size / 2]} " +
            "/ 최소 ${scores.min()} / 최대 ${scores.max()}")
        println("등급 분포: " + all.groupingBy { it.third.grade }.eachCount().entries
            .sortedByDescending { it.value }.joinToString("  ") { "${it.key} ${it.value}(${100 * it.value / all.size}%)" })
        println("점수 히스토그램:")
        println(histogram(scores))

        println("\n── 성씨별 (평균 / 70점 이상 / 발음 상극률)")
        SURNAMES.forEach { (sur, _) ->
            val g = all.filter { it.first == sur }
            if (g.isEmpty()) return@forEach
            val s = g.map { it.third.score }
            println("  $sur  %5.1f  %3d%%  %3d%%".format(
                s.average(), 100 * s.count { it >= 70 } / s.size,
                100 * g.count { it.third.baleum?.hasSanggeuk == true } / g.size))
        }

        println("\n── 사주별 — 다른 축은 사주와 무관하므로 편차는 전부 자원 축에서 나온다")
        println("  보완대상        총점   70↑   1순위적중  아무거나적중  기신사용")
        for ((birth, saju) in sajus) {
            val g = all.filter { it.third.sajuFit?.targets == saju.targetElements }
            if (g.isEmpty()) continue
            val s = g.map { it.third.score }
            val top = saju.targetElements.firstOrNull()
            val topHit = g.count { r -> top != null && top in r.third.sajuFit!!.matched }
            val anyHit = g.count { r -> r.third.sajuFit!!.matched.isNotEmpty() }
            val gisin = g.count { r -> r.third.sajuFit!!.gisinUsed.isNotEmpty() }
            println("  %-6s %-4s %5.1f  %3d%%     %3d%%       %3d%%        %3d%%".format(
                saju.targetElements.joinToString("") { it.hanja },
                if (saju.isStrong) "신강" else "신약",
                s.average(), 100 * s.count { it >= 70 } / s.size,
                100 * topHit / g.size, 100 * anyHit / g.size, 100 * gisin / g.size))
        }

        // 실무 작명소가 내놓는 이름 = 용신 보강 + 기신 회피를 이미 만족한 이름.
        // 그런 이름만 추리면 사주별 편차가 남는지 본다 — 남지 않으면 편차는
        // '아무 이름이나 넣었을 때'만 나타나는 것이고, 실사용에는 영향이 없다.
        println("\n── 작명소 조건(용신 보강 + 기신 회피)을 만족한 이름만")
        for ((_, saju) in sajus) {
            val g = all.filter { r ->
                r.third.sajuFit?.targets == saju.targetElements &&
                    r.third.sajuFit!!.matched.isNotEmpty() && r.third.sajuFit!!.gisinUsed.isEmpty()
            }
            if (g.isEmpty()) continue
            val s = g.map { it.third.score }
            println("  %-6s %4d건  총점 %5.1f  70↑ %3d%%".format(
                saju.targetElements.joinToString("") { it.hanja }, g.size,
                s.average(), 100 * s.count { it >= 70 } / s.size))
        }

        // 오행별 공급량 — 위 적중률이 공급량을 그대로 따라가는지 대조한다
        val supply = db.entries.filter { it.usableForNaming && it.nameFit >= 3 }
            .groupingBy { it.element?.hanja ?: "미상" }.eachCount()
        val supplyTotal = supply.values.sum()
        println("  (인명용 한자 자원오행 공급량: " +
            supply.entries.sortedByDescending { it.value }
                .joinToString(" ") { "${it.key}${100 * it.value / supplyTotal}%" } + ")")

        // 축별 '만점 대비 손실' — 어느 축이 아직 점수를 먹고 있는지
        println("\n── 축별 평균 획득률")
        val n = all.size.toDouble()
        val suriRate = all.sumOf { e ->
            e.third.suri.all.sumOf { g ->
                when (g.grade.name) { "DAEGIL" -> 1.0; "GIL" -> 0.9; "PYEONG" -> 0.6; "HYUNG" -> 0.35; else -> 0.2 }
            } / 4.0
        } / n
        println("  수리사격 ${"%.0f".format(100 * suriRate)}%")
        val baleumRate = all.sumOf { e ->
            val sg = e.third.baleum?.relations?.count { it.name == "SANGGEUK" } ?: 0
            when {
                e.third.baleum == null -> 9.0
                sg > 0 -> (18.0 - 6.0 * sg).coerceAtLeast(5.0)
                e.third.baleumVerdict.name == "GIL" -> 18.0
                else -> 13.0
            } / 18.0
        } / n
        println("  발음오행 ${"%.0f".format(100 * baleumRate)}%")
        println("  수리오행 ${"%.0f".format(100 * all.count { it.third.suriOhengVerdict.name == "GIL" } / n)}% 길판정")
        println("  자원·사주 길판정 ${"%.0f".format(100 * all.count { it.third.jawonVerdict.name == "GIL" } / n)}%" +
            " / 흉 ${"%.0f".format(100 * all.count { it.third.jawonVerdict.name == "HYUNG" } / n)}%")
        println("  음양 조화 ${"%.0f".format(100 * all.count { it.third.eumyangVerdict.name == "GIL" } / n)}%")
        println("  불용 '기피' 걸린 건 ${all.count { r -> r.third.bulyongWarnings.any { it.second.severity.name == "GIPI" } }}")

        println("\n── 최저 12건 (왜 낮은지)")
        all.sortedBy { it.third.score }.take(12).forEach { (sur, nm, e) ->
            println("  $sur$nm(${e.givenHanja.joinToString("") { it.char.toString() }}) ${e.score}점 ${e.grade}" +
                " | 수리 ${e.suri.all.joinToString("/") { "${it.number}${it.grade.label}" }}" +
                " | 발음 ${e.baleumVerdict.label} 수오 ${e.suriOhengVerdict.label}" +
                " 자원 ${e.jawonVerdict.label} 음양 ${e.eumyangVerdict.label}")
        }

        println("\n── 최고 6건")
        all.sortedByDescending { it.third.score }.take(6).forEach { (sur, nm, e) ->
            println("  $sur$nm(${e.givenHanja.joinToString("") { it.char.toString() }}) ${e.score}점 ${e.grade}")
        }
    }
}
