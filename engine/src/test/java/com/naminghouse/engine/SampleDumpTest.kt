package com.naminghouse.engine

import com.naminghouse.engine.gen.NameGenerator
import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.saju.SajuNamingService
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Test
import java.io.File

/**
 * 눈으로 확인하는 샘플 덤프 — 단정 없이 결과만 출력한다.
 * 실행: ./gradlew :engine:testDebugUnitTest --tests '*SampleDumpTest' -i
 */
class SampleDumpTest {

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "engine/src/main/assets/$name")
            .map(::File).first(File::exists)

    @Test
    fun dump() {
        val db = asset("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }
        val pool = asset("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
        val generator = NameGenerator(db, pool)

        val cases = listOf(
            Triple("김", '金', BirthInput(2026, 3, 15, 9, 20, Gender.M)),
            Triple("이", '李', BirthInput(2025, 11, 2, 21, 40, Gender.F)),
        )

        for ((surname, surChar, input) in cases) {
            val saju = SajuNamingService.analyze(input)
            println("\n===== $surname 씨 ${input.year}.${input.month}.${input.day} ${input.hour}시 (${input.gender}) =====")
            println("사주: ${saju.ganzis.joinToString(" ")}")
            println("일간 ${saju.dayStem}(${saju.dayElement.hanja}) · " +
                (if (saju.isNeutral) "중화" else if (saju.isStrong) "신강" else "신약"))
            println("오행: " + saju.simpleCounts.entries.joinToString(" ") { "${it.key.hanja}${it.value}" })
            println("용신: ${saju.yongsin.joinToString("·") { it.hanja }} / 기신: ${saju.gisin.joinToString("·") { it.hanja }}")
            println("보완 대상: ${saju.targetElements.joinToString("·") { it.hanja }}")

            val candidates = generator.generate(surname, listOf(db.byChar.getValue(surChar)), input.gender, saju)
            println("추천 ${candidates.size}개 중 상위 15:")
            candidates.take(15).forEachIndexed { i, c ->
                val e = c.evaluation
                val hanja = c.hanja.joinToString("") { it.char.toString() }
                val elements = c.hanja.joinToString("") { it.element?.hanja ?: "?" }
                val suri = e.suri.all.joinToString("/") { "${it.number}" }
                println(
                    "  ${"%2d".format(i + 1)}. $surname${c.givenName} $hanja  " +
                        "${e.score}점(${e.grade})  자원오행 $elements  4격 $suri  " +
                        "발음 ${e.baleum?.elements?.joinToString("") { it.hanja }}"
                )
            }
        }
    }
}
