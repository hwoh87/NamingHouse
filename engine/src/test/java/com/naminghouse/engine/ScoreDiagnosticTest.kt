package com.naminghouse.engine

import com.naminghouse.engine.data.BulyongHanja
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
 * 진단용 덤프 — 단정 없음. 실제로 널리 쓰이는 이름(작명소 산출물 포함)을
 * 우리 평가기에 넣었을 때 점수가 어디서 깎이는지 축별로 집계한다.
 *
 * 실행: ./gradlew :engine:testDebugUnitTest --tests '*ScoreDiagnosticTest' -i
 */
class ScoreDiagnosticTest {

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "engine/src/main/assets/$name")
            .map(::File).first(File::exists)

    private val db = asset("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }

    /** name-stats.tsv 에서 실제 출생신고 빈도 상위 이름 */
    private fun popularNames(limit: Int): List<Pair<String, Int>> =
        asset("name-stats.tsv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { l ->
                val c = l.split('\t')
                if (c.size < 4) return@mapNotNull null
                val total = (c[2].toIntOrNull() ?: 0) + (c[3].toIntOrNull() ?: 0)
                c[0] to total
            }.sortedByDescending { it.second }.take(limit).toList()
        }

    private fun candidates(syllable: String): List<HanjaEntry> =
        db.candidatesFor(syllable).filter { it.nameFit >= 3 }.take(12)

    @Test
    fun bestCaseScoreOfRealNames() {
        val saju = SajuNamingService.analyze(BirthInput(2026, 3, 15, 9, 20, Gender.M))
        println("사주 용신=${saju.yongsin.joinToString{it.hanja}} 기신=${saju.gisin.joinToString{it.hanja}} " +
            "보완대상=${saju.targetElements.joinToString{it.hanja}}")

        for ((label, s) in listOf<Pair<String, SajuSummary?>>("사주 있음" to saju, "출생 전(사주 없음)" to null)) {
            val surHanja = db.byChar['金']!!
            val rows = popularNames(200).mapNotNull { (name, cnt) ->
                if (name.length != 2) return@mapNotNull null
                val c1 = candidates(name[0].toString())
                val c2 = candidates(name[1].toString())
                if (c1.isEmpty() || c2.isEmpty()) return@mapNotNull null
                val best = c1.flatMap { a -> c2.map { b -> listOf(a, b) } }
                    .map { NameEvaluator.evaluate("김", name, listOf(surHanja), it, s) }
                    .maxByOrNull { it.score }!!
                Triple(name, cnt, best)
            }

            println("\n########## $label — 김씨 + 실제 인기 이름 ${rows.size}개, 한자 최적 조합 ##########")
            val scores = rows.map { it.third.score }
            println("평균 ${scores.average().toInt()} / 중앙 ${scores.sorted()[scores.size/2]} / 최소 ${scores.min()} / 최대 ${scores.max()}")
            listOf(85, 70, 50).forEach { t ->
                println("  ${t}점 이상: ${scores.count { it >= t }}건 (${100 * scores.count { it >= t } / scores.size}%)")
            }

            // 축별 손실 집계
            var suriLoss = 0.0; var baleumLoss = 0.0; var suriOhLoss = 0.0
            var jawonLoss = 0.0; var eumLoss = 0.0; var bulLoss = 0.0
            for ((_, _, e) in rows) {
                suriLoss += 30 - 30.0 * e.suri.all.sumOf { g ->
                    when (g.grade.name) { "DAEGIL" -> 1.0; "GIL" -> 0.85; "PYEONG" -> 0.4; "HYUNG" -> 0.05; else -> 0.0 }
                } / 4.0
                baleumLoss += 18 - when (e.baleumVerdict.name) { "GIL" -> 18.0; "BOTONG" -> 13.0; else -> 4.0 }
                suriOhLoss += 12 - when (e.suriOhengVerdict.name) { "GIL" -> 12.0; "BOTONG" -> 8.0; else -> 2.0 }
                eumLoss += 10 - ((if (e.strokeEumyang.isBalanced) 6.0 else 0.0) +
                    (if (e.soundEumyang?.isBalanced != false) 4.0 else 1.0))
                bulLoss += if (e.bulyongWarnings.isEmpty()) 0.0 else 5.0 + 6.0 * e.bulyongWarnings.size
                jawonLoss += 25 - if (e.sajuFit != null) {
                    val top = e.sajuFit!!.targets.take(2)
                    val cov = if (top.isEmpty()) 0.5 else e.sajuFit!!.matched.count { it in top }.toDouble() / top.size
                    (25.0 * cov - 5.0 * e.sajuFit!!.gisinUsed.size).coerceIn(0.0, 25.0)
                } else when (e.jawonVerdict.name) { "GIL" -> 22.0; "BOTONG" -> 15.0; else -> 7.0 }
            }
            val n = rows.size
            println("축별 평균 손실점 (배점 대비):")
            println("  수리사격 /30 : ${"%.1f".format(suriLoss / n)}")
            println("  발음오행 /18 : ${"%.1f".format(baleumLoss / n)}")
            println("  수리오행 /12 : ${"%.1f".format(suriOhLoss / n)}")
            println("  자원·사주/25 : ${"%.1f".format(jawonLoss / n)}")
            println("  음양     /10 : ${"%.1f".format(eumLoss / n)}")
            println("  불용한자 /5  : ${"%.1f".format(bulLoss / n)}  (경고시 -11 이상)")

            println("하위 15건:")
            rows.sortedBy { it.third.score }.take(15).forEach { (name, cnt, e) ->
                println("  $name(${e.givenHanja.joinToString(""){it.char.toString()}}) ${e.score}점 ${e.grade}" +
                    " | 수리 ${e.suri.all.joinToString("/"){ "${it.number}${it.grade.label}" }}" +
                    " | 발음 ${e.baleumVerdict.label} 수오 ${e.suriOhengVerdict.label} 자원 ${e.jawonVerdict.label} 음양 ${e.eumyangVerdict.label}" +
                    " | 불용 ${e.bulyongWarnings.joinToString(""){it.first.toString()}}")
            }
        }
    }

    /** 사용자 실제 시나리오: 작명소에서 받은 이름+한자를 그대로 입력 (최적 조합이 아님) */
    @Test
    fun realisticScenario() {
        val saju = SajuNamingService.analyze(BirthInput(2026, 3, 15, 9, 20, Gender.M))
        val surnames = listOf("김" to '金', "이" to '李', "박" to '朴', "최" to '崔', "정" to '鄭')
        val names = popularNames(200).filter { it.first.length == 2 }

        for ((sur, surChar) in surnames) {
            val surHanja = db.byChar[surChar]!!
            val evals = names.flatMap { (name, _) ->
                val c1 = candidates(name[0].toString()).take(4)
                val c2 = candidates(name[1].toString()).take(4)
                c1.flatMap { a -> c2.map { b -> listOf(a, b) } }
                    .map { NameEvaluator.evaluate(sur, name, listOf(surHanja), it, saju) }
            }
            if (evals.isEmpty()) continue
            val sc = evals.map { it.score }.sorted()
            val sanggeuk = evals.count { it.baleumVerdict.name == "HYUNG" }
            println("$sur($surChar) — 조합 ${evals.size}개 | 평균 ${sc.average().toInt()} 중앙 ${sc[sc.size/2]} " +
                "| 70점이상 ${100*sc.count{it>=70}/sc.size}% | 발음오행 상극 ${100*sanggeuk/evals.size}%" +
                " | 자원25점 만점 ${100*evals.count{ e -> e.sajuFit!!.targets.take(2).all { it in e.sajuFit!!.matched } }/evals.size}%")
        }

        // 발음오행: 성씨 초성 오행별로 실제 인기 이름의 상극률
        println("\n성씨 초성오행 × 실제 인기이름 발음오행 상극률")
        for ((sur, _) in surnames) {
            val bad = names.count { (nm, _) ->
                com.naminghouse.engine.oheng.BaleumOheng.evaluate(sur + nm)?.hasSanggeuk ?: false
            }
            println("  $sur: ${bad}/${names.size} (${100*bad/names.size}%) 상극")
        }
    }

    /** 수정안 시뮬레이션 — 배점 곡선만 바꿨을 때 실제 이름들의 점수가 어디로 가는지 */
    @Test
    fun simulateRelaxedScoring() {
        val saju = SajuNamingService.analyze(BirthInput(2026, 3, 15, 9, 20, Gender.M))
        val names = popularNames(200).filter { it.first.length == 2 }
        for ((sur, surChar) in listOf("김" to '金', "이" to '李', "박" to '朴')) {
            val surHanja = db.byChar[surChar]!!
            val evals = names.flatMap { (name, _) ->
                candidates(name[0].toString()).take(4).flatMap { a ->
                    candidates(name[1].toString()).take(4).map { b ->
                        NameEvaluator.evaluate(sur, name, listOf(surHanja), listOf(a, b), saju)
                    }
                }
            }
            val relaxed = evals.map { e ->
                val suri = 30.0 * e.suri.all.sumOf { g ->
                    when (g.grade.name) { "DAEGIL" -> 1.0; "GIL" -> 0.9; "PYEONG" -> 0.6; "HYUNG" -> 0.35; else -> 0.2 }
                } / 4.0
                // 발음: 상극 1개는 감점하되 치명타는 아님 (상극 개수 비례)
                val sg = e.baleum?.relations?.count { it.name == "SANGGEUK" } ?: 0
                val baleum = (18.0 - 5.0 * sg).coerceAtLeast(6.0)
                val suriOh = when (e.suriOhengVerdict.name) { "GIL" -> 12.0; "BOTONG" -> 9.0; else -> 5.0 }
                // 자원: 성씨 포함 + 용신 1개만 맞아도 상당 점수
                val withSur = e.givenHanja.mapNotNull { it.element } + listOfNotNull(surHanja.element)
                val fit = e.sajuFit!!
                val hit = fit.targets.count { it in withSur }
                val jawon = (when { hit >= 2 -> 25.0; hit == 1 -> 20.0; else -> 8.0 } -
                    4.0 * fit.gisinUsed.size).coerceIn(0.0, 25.0)
                val eum = (if (e.strokeEumyang.isBalanced) 6.0 else 2.0) +
                    (if (e.soundEumyang?.isBalanced != false) 4.0 else 2.0)
                val bul = if (e.bulyongWarnings.isEmpty()) 5.0 else 5.0 - 2.0 * e.bulyongWarnings.size
                (suri + baleum + suriOh + jawon + eum + bul).coerceIn(0.0, 100.0).toInt()
            }.sorted()
            val cur = evals.map { it.score }.sorted()
            println("$sur — 현재 평균 ${cur.average().toInt()}/70점이상 ${100*cur.count{it>=70}/cur.size}%" +
                "  →  수정안 평균 ${relaxed.average().toInt()}/70점이상 ${100*relaxed.count{it>=70}/relaxed.size}%" +
                " (85이상 ${100*relaxed.count{it>=85}/relaxed.size}%)")
        }
    }

    @Test
    fun bulyongCoverageOfPopularNameHanja() {
        // 인기 이름 음절들의 '쓸 만한' 한자 중 몇 %가 불용 목록에 걸리는지
        val syllables = popularNames(300).flatMap { it.first.map(Char::toString) }.distinct()
        var total = 0; var hit = 0
        val hitChars = sortedSetOf<Char>()
        for (s in syllables) for (e in db.candidatesFor(s).filter { it.nameFit >= 3 }) {
            total++
            if (BulyongHanja.map.containsKey(e.char)) { hit++; hitChars.add(e.char) }
        }
        println("\n인기 이름 음절 ${syllables.size}개의 nameFit>=3 한자 ${total}자 중 불용 ${hit}자 (${100*hit/total}%)")
        println("걸린 글자: ${hitChars.joinToString("")}")

        // 이름 단위: 두 음절 모두 '불용 아닌' 한자를 가진 이름 비율
        val names = popularNames(300).filter { it.first.length == 2 }
        val allBlocked = names.filter { (nm, _) ->
            nm.map(Char::toString).any { syl ->
                val c = db.candidatesFor(syl).filter { it.nameFit >= 3 }
                c.isNotEmpty() && c.all { BulyongHanja.map.containsKey(it.char) }
            }
        }
        println("한 음절 이상이 '불용 한자밖에 없는' 이름: ${allBlocked.size}/${names.size} — ${allBlocked.joinToString(" "){it.first}}")
    }
}
