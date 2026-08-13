package com.naminghouse.engine

import com.naminghouse.engine.gen.GeneratorOptions
import com.naminghouse.engine.gen.NameGenerator
import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.hanja.HanjaDb
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NameGeneratorTest {

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "engine/src/main/assets/$name")
            .map(::File).first(File::exists)

    private val db: HanjaDb by lazy {
        asset("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }
    }
    private val pool: NamePool by lazy {
        asset("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
    }

    private val kim = listOf(db.byChar.getValue('金'))

    @Test
    fun `외자 옵션이면 한 글자 이름만, 4격은 전부 길수`() {
        val list = NameGenerator(db, pool)
            .generate("김", kim, Gender.M, saju = null, GeneratorOptions(singleSyllable = true))
        assertTrue("외자 후보가 있어야 함", list.isNotEmpty())
        list.forEach { c ->
            assertTrue(c.givenName, c.givenName.length == 1)
            assertTrue("${c.givenName} 한자 1개", c.hanja.size == 1)
            assertTrue("${c.givenName} 4격 전길", c.evaluation.suri.allGood)
        }
    }

    @Test
    fun `돌림자를 주면 그 글자가 그 자리에 든 이름만 나온다`() {
        val gen = NameGenerator(db, pool)
        val first = gen.generate(
            "김", kim, Gender.M, null,
            GeneratorOptions(fixedSyllable = '도', fixedLast = false),
        )
        assertTrue("'도' 첫 글자 후보가 있어야 함", first.isNotEmpty())
        first.forEach { assertTrue(it.givenName, it.givenName.first() == '도') }

        val last = gen.generate(
            "김", kim, Gender.M, null,
            GeneratorOptions(fixedSyllable = '준', fixedLast = true),
        )
        assertTrue("'준' 끝 글자 후보가 있어야 함", last.isNotEmpty())
        last.forEach { assertTrue(it.givenName, it.givenName.last() == '준') }
    }

    @Test
    fun `돌림자는 외자 옵션에서 무시된다`() {
        // 외자 + 돌림자를 같이 켜도 외자 추천이 그대로 나온다(호출부 UI도 이 조합을 막는다)
        val list = NameGenerator(db, pool).generate(
            "김", kim, Gender.M, null,
            GeneratorOptions(singleSyllable = true, fixedSyllable = '도'),
        )
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.givenName.length == 1 })
    }
}
