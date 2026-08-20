package com.naminghouse.engine

import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.gen.NameGenerator
import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.gen.NameStats
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.saju.SajuNamingService
import com.naminghouse.engine.suri.SuriCalculator
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Test
import java.io.File

/**
 * 배점 교정용 감사 덤프 — 단정 없음. 배점을 손볼 때마다 여기부터 돌린다.
 *
 * ScoreSweepTest 가 '총점이 어떻게 분포하는가'를 보는 반면, 이쪽은 '왜 그 분포인가'를 본다:
 * 축마다 실제로 몇 점을 주는지, 그 분포가 이론 확률과 맞는지, 등급 경계를 어디 둬야
 * 우리가 추천하는 이름이 그 등급에 들어오는지, 그리고 흔한 이름 표본이 실제로 몇 점인지.
 *
 * 실행: ./gradlew :engine:testDebugUnitTest --tests '*CalibrationAuditTest'
 */
class CalibrationAuditTest {
    private fun a(n: String) = listOf("src/main/assets/$n", "engine/src/main/assets/$n")
        .map(::File).first(File::exists)
    private val db = a("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }

    @Test fun `축별 획득 분포`() {
        val names = a("name-stats.tsv").bufferedReader().useLines { l ->
            l.drop(1).mapNotNull { it.split('\t').firstOrNull() }.filter { it.length == 2 }.toList()
        }.take(120)
        val surs = listOf("김" to '金', "이" to '李', "박" to '朴', "최" to '崔', "정" to '鄭',
            "강" to '姜', "윤" to '尹', "장" to '張', "한" to '韓', "권" to '權')
        val births = listOf(
            BirthInput(2026, 1, 8, 3, 10, Gender.F), BirthInput(2026, 3, 15, 9, 20, Gender.M),
            BirthInput(2025, 7, 21, 23, 40, Gender.M), BirthInput(2024, 9, 3, 6, 55, Gender.F))
        val sajus = births.map { SajuNamingService.analyze(it) }

        val suri = ArrayList<Double>(); val bal = ArrayList<Double>(); val soh = ArrayList<Double>()
        val jaw = ArrayList<Double>(); val eum = ArrayList<Double>(); val tot = ArrayList<Int>()
        for ((s, c) in surs) { val sh = db.byChar[c] ?: continue
            for (sj in sajus) for (nm in names) {
                val c1 = db.candidatesFor(nm[0].toString()).filter { it.usableForNaming }.take(2)
                val c2 = db.candidatesFor(nm[1].toString()).filter { it.usableForNaming }.take(2)
                if (c1.isEmpty() || c2.isEmpty()) continue
                val e = NameEvaluator.evaluate(s, nm, listOf(sh), listOf(c1[0], c2[0]), sj)
                suri += 30.0 * e.suri.all.sumOf { g -> when (g.grade.name) {
                    "DAEGIL" -> 1.0; "GIL" -> 0.9; "PYEONG" -> 0.6; "HYUNG" -> 0.35; else -> 0.2 } } / 4.0
                val sg = e.baleum?.relations?.count { it.name == "SANGGEUK" } ?: 0
                bal += when { e.baleum == null -> 9.0
                    sg > 0 -> (18.0 - 6.0 * sg).coerceAtLeast(5.0)
                    e.baleumVerdict.name == "GIL" -> 18.0; else -> 13.0 }
                val ssg = e.suriOheng.relations.count { it.name == "SANGGEUK" }
                soh += when { ssg > 0 -> (12.0 - 4.0 * ssg).coerceAtLeast(4.0)
                    e.suriOhengVerdict.name == "GIL" -> 12.0; else -> 9.0 }
                eum += (if (e.strokeEumyang.isBalanced) 6.0 else 2.0) +
                    (if (e.soundEumyang?.isBalanced != false) 4.0 else 1.5)
                val f = e.sajuFit!!
                jaw += (when { f.matched.size >= 2 -> 25.0
                    f.matched.size == 1 && f.covered.size >= 2 -> 25.0
                    f.matched.size == 1 -> 22.0
                    f.covered.isNotEmpty() -> 17.0
                    f.targets.isEmpty() -> 15.0; else -> 9.0 } -
                    if (f.gisinUsed.isEmpty()) 0.0 else 4.0).coerceIn(0.0, 25.0)
                tot += e.score
            }
        }
        fun row(label: String, v: List<Double>, max: Double) {
            val hist = v.groupingBy { it }.eachCount().entries.sortedByDescending { it.key }
                .joinToString(" ") { "${"%.0f".format(it.key)}점 ${100 * it.value / v.size}%" }
            println("  " + label.padEnd(10) + " 평균 " + "%.1f".format(v.average()) + " / " + max +
                "  (" + "%.0f".format(100 * v.average() / max) + "%)   " + hist)
        }
        println("\n═══ ${tot.size}건 · 축별 획득 ═══  총점 평균 ${"%.1f".format(tot.average())}")
        row("수리사격", suri, 30.0); row("발음오행", bal, 18.0); row("수리오행", soh, 12.0)
        row("자원·사주", jaw, 25.0); row("음양", eum, 10.0)
        println("  등급: " + tot.map(NameEvaluator::gradeOf).groupingBy { it }.eachCount())
    }

    @Test fun `수리사격 - 격별 흉수 비율`() {
        val names = a("name-stats.tsv").bufferedReader().useLines { l ->
            l.drop(1).mapNotNull { it.split('\t').firstOrNull() }.filter { it.length == 2 }.toList()
        }
        val surs = listOf('金', '李', '朴', '崔', '鄭', '姜', '尹', '張', '韓', '權', '吳', '徐')
        var n = 0; val bad = IntArray(4); val cnt = IntArray(5)
        for (sc in surs) { val sh = db.byChar[sc] ?: continue
            for (nm in names) {
                val c1 = db.candidatesFor(nm[0].toString()).filter { it.usableForNaming }.firstOrNull() ?: continue
                val c2 = db.candidatesFor(nm[1].toString()).filter { it.usableForNaming }.firstOrNull() ?: continue
                val g = SuriCalculator.calculate(listOf(sh.wonhoek), listOf(c1.wonhoek, c2.wonhoek))
                n++
                g.all.forEachIndexed { i, m -> if (!m.grade.isGood) bad[i]++ }
                cnt[g.goodCount]++
            }
        }
        val label = listOf("원격(초년)", "형격(청년·인격)", "이격(중년·외격)", "정격(총운)")
        println("\n실제 이름 ${n}건의 격별 흉수 비율")
        label.forEachIndexed { i, l -> println("  ${l.padEnd(16)} ${100 * bad[i] / n}%") }
        println("  4격 중 길수 개수 분포: " + (0..4).joinToString(" ") { "${it}개 ${100 * cnt[it] / n}%" })
        println("\n81수리 자체의 흉수 비율은 49%(40/81) — 격별 편차가 없으면 균등 가중이 타당하다.")
    }

    @Test fun `등급 경계 기준점`() {
        val pool = a("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
        val st = a("name-stats.tsv").bufferedReader().useLines { NameStats.parse(it) }
        val g = NameGenerator(db, pool, st)
        val all = ArrayList<Int>()
        for ((s, c, gd) in listOf(
            Triple("김", '金', Gender.M), Triple("이", '李', Gender.F), Triple("박", '朴', Gender.M),
            Triple("최", '崔', Gender.F), Triple("정", '鄭', Gender.M), Triple("권", '權', Gender.F),
        )) for (b in listOf(
            BirthInput(2026, 1, 8, 3, 10, gd), BirthInput(2025, 7, 21, 23, 40, gd),
            BirthInput(2024, 9, 3, 6, 55, gd),
        )) {
            val r = g.generate(s, listOf(db.byChar[c]!!), gd, SajuNamingService.analyze(b))
            val sc = r.map { it.evaluation.score }.sorted()
            if (sc.isEmpty()) continue
            all += sc
            println("  $s ${b.year}.${b.month} → ${r.size}건  최저 ${sc.first()} / 하위10% ${sc[sc.size/10]} / 중앙 ${sc[sc.size/2]} / 최고 ${sc.last()}")
        }
        val s2 = all.sorted()
        println("\n생성기가 '추천'으로 내놓는 이름 ${all.size}건 — 최저 ${s2.first()} / 5% ${s2[s2.size/20]} / 25% ${s2[s2.size/4]} / 중앙 ${s2[s2.size/2]}")
        listOf(80, 82, 85, 88).forEach { t ->
            println("  대길 경계 $t 이면 추천 중 ${100 * s2.count { c -> c >= t } / s2.size}% 가 대길")
        }
    }

    @Test fun `흔한 이름 표본`() {
        val saju = SajuNamingService.analyze(BirthInput(2026, 3, 15, 9, 20, Gender.M))
        val cases = listOf(
            Triple("김", '金', "서준" to "書俊"), Triple("이", '李', "서연" to "書涓"),
            Triple("박", '朴', "도윤" to "度潤"), Triple("최", '崔', "지우" to "智雨"),
            Triple("정", '鄭', "하준" to "河俊"), Triple("김", '金', "민준" to "珉俊"),
            Triple("이", '李', "지민" to "智珉"), Triple("강", '姜', "서윤" to "書潤"),
            Triple("윤", '尹', "예은" to "藝恩"), Triple("장", '張', "지훈" to "智勳"),
        )
        println()
        for ((sur, sc, nm) in cases) {
            val sh = db.byChar[sc] ?: continue
            val gh = nm.second.map { db.byChar[it] ?: return@map null }
            if (gh.any { it == null }) { println("  $sur${nm.first} — 한자 없음"); continue }
            val e = NameEvaluator.evaluate(sur, nm.first, listOf(sh), gh.filterNotNull(), saju)
            // 같은 한글 이름에 한자만 최적으로 골랐을 때 — 앱의 '한자 추천'이 하는 일
            val p1 = db.candidatesFor(nm.first[0].toString()).filter { it.usableForNaming }
            val p2 = db.candidatesFor(nm.first[1].toString()).filter { it.usableForNaming }
            val best = p1.flatMap { x -> p2.map { y -> listOf(x, y) } }
                .map { NameEvaluator.evaluate(sur, nm.first, listOf(sh), it, saju) }
                .maxByOrNull { it.score }
            println("  $sur${nm.first}(${nm.second})  ${e.score}점 ${e.grade}" +
                "   →  한자 최적 ${best?.givenHanja?.joinToString("") { it.char.toString() }} " +
                "${best?.score}점 ${best?.grade}" +
                "   [원래: 수리 ${e.suri.all.count { g -> !g.grade.isGood }}격 흉 · 발음 ${e.baleumVerdict.label}]")
        }
    }
}
