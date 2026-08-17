package com.naminghouse.engine

import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.gen.GeneratorOptions
import com.naminghouse.engine.gen.NameGenerator
import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.gen.NameStats
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.saju.SajuNamingService
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 번들 데이터(hanja.tsv / names.tsv) 무결성과 추천 파이프라인 통합 검증.
 * 데이터는 배포물의 일부이므로 없으면 실패한다.
 */
class DataIntegrationTest {

    private fun asset(name: String): File {
        val candidates = listOf("src/main/assets/$name", "engine/src/main/assets/$name")
        return candidates.map(::File).firstOrNull(File::exists)
            ?: error("$name 없음 — tools/hanja-db 파이프라인을 먼저 실행해야 함")
    }

    private val db: HanjaDb by lazy {
        asset("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }
    }
    private val pool: NamePool by lazy {
        asset("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
    }

    @Test
    fun `한자 DB - 규모와 대표 글자`() {
        assertTrue("인명용 한자는 8000자 이상이어야 함 (현재 ${db.entries.size})", db.entries.size >= 8000)

        val kim = db.byChar['金']
        assertNotNull(kim)
        assertEquals(8, kim!!.wonhoek)
        assertTrue("金 독음에 김·금 포함", kim.readings.containsAll(listOf("김", "금")))

        val su = db.byChar['洙']
        assertNotNull(su)
        assertEquals("洙 원획은 물수변 원획 환산으로 10", 10, su!!.wonhoek)
        assertEquals(9, su.pilhoek)
    }

    @Test
    fun `한자 DB - 인명 적합도 필터가 벽자와 부적합자를 걸러낸다`() {
        // 실제로 이름에 널리 쓰이는 글자는 살아남아야 한다
        val mustKeep = "民敏珉俊峻濬賢玄鉉炫恩銀書瑞序善成誠星聖昭秀洙修淑純昇承施時詩" +
            "娥雅愛彦姸妍榮英永泳睿藝玉溫容佑祐宇旭元原源裕潤允律義仁在載貞政定" +
            "珠智志知珍眞燦昌彩天哲淸秋泰河夏海香赫慧惠昊浩弘和煥孝勳輝熙姫"
        val rejected = mustKeep.filter { ch -> db.byChar[ch]?.usableForNaming != true }
        assertTrue("흔한 이름 한자가 걸러지면 안 됨: $rejected", rejected.isEmpty())

        // 벽자·부적합자는 걸러져야 한다
        // 叨 탐할 / 所 바(어조사) / 吻 입술 / 奈 어찌 / 刲 찌를 / 瘰 연주창 / 秊 年의 本字
        "叨所吻奈刲瘰秊".forEach { ch ->
            val entry = db.byChar[ch]
            if (entry != null) {
                assertTrue("$ch(${entry.meaning})는 작명 후보에서 제외돼야 함", !entry.usableForNaming)
            }
        }

        val usable = db.entries.count { it.usableForNaming }
        assertTrue("작명 가능 글자가 800자 이상이어야 함 (현재 $usable)", usable >= 800)
    }

    @Test
    fun `한자 DB - 자원오행 커버리지`() {
        val withElement = db.entries.count { it.element != null }
        val ratio = withElement.toDouble() / db.entries.size
        assertTrue("자원오행 배정 비율이 70% 이상이어야 함 (현재 ${"%.1f".format(ratio * 100)}%)", ratio >= 0.7)
    }

    @Test
    fun `이름 풀 - 규모와 성별 분포`() {
        assertTrue("이름 풀은 900개 이상이어야 함 (현재 ${pool.names.size})", pool.names.size >= 900)
        val m = pool.names.count { it.gender == 'M' || it.gender == 'U' }
        val f = pool.names.count { it.gender == 'F' || it.gender == 'U' }
        assertTrue("남아용 이름 350개 이상", m >= 350)
        assertTrue("여아용 이름 350개 이상", f >= 350)
    }

    @Test
    fun `추천 파이프라인 - 사주 기반 남아 이름`() {
        val saju = SajuNamingService.analyze(BirthInput(2024, 2, 4, 16, 30, Gender.M))
        val surnameKim = db.byChar.getValue('金')
        val generator = NameGenerator(db, pool)

        val candidates = generator.generate("김", listOf(surnameKim), Gender.M, saju)

        assertTrue("후보가 최소 10개는 나와야 함 (현재 ${candidates.size})", candidates.size >= 10)
        candidates.forEach { cand ->
            // 기본 옵션: 수리 4격 전부 길수
            assertTrue("${cand.givenName}: 4격 전부 길수여야 함", cand.evaluation.suri.allGood)
            // 기본 옵션: 발음오행 상극 없음
            assertTrue("${cand.givenName}: 발음 상극 배열 금지", cand.evaluation.baleum?.hasSanggeuk != true)
            // 기본 옵션: 불용한자 제외
            assertTrue("${cand.givenName}: 불용한자 없어야 함", cand.evaluation.bulyongWarnings.isEmpty())
            // 벽자·부적합 한자가 추천에 섞이면 안 됨
            cand.hanja.forEach { h ->
                assertTrue("${cand.givenName}: ${h.char}(${h.meaning})는 이름에 쓰는 글자가 아님", h.usableForNaming)
            }
        }
        // 1위는 전체 최고점이어야 한다
        assertEquals(candidates.maxOf { it.evaluation.score }, candidates.first().evaluation.score)
        assertTrue("최상위 점수는 70점 이상이어야 함", candidates.first().evaluation.score >= 70)

        // 다양성: 같은 첫 글자가 상위권을 도배하지 않아야 한다.
        // (사주 보완 오행에 딱 맞는 글자 하나 때문에 '김대영·김대호·김대현·김대운' 처럼
        //  쏠리던 것을 NameGenerator.diversify 가 완화한다)
        //
        // '글자당 2개 이하'로 못 박지 않는다 — 상한은 후보 풀이 허용하는 만큼이다.
        // 김씨는 金(8획)에 4격 전길이 되는 획수 조합이 적어 후보가 44건뿐이고 첫 글자도
        // 14종이라, 2개씩으로는 19개까지밖에 못 채운다. 지켜져야 할 성질은 '몇 개
        // 이하'가 아니라 '상위권이 몇 가지 소리로 이뤄져 있는가'다.
        val top = candidates.take(20)
        val distinct = top.map { it.givenName.first() }.distinct().size
        assertTrue("상위 ${top.size}개의 첫 글자가 ${distinct}종뿐 — 10종 이상이어야 함", distinct >= 10)
        val worst = top.groupingBy { it.givenName.first() }.eachCount().maxByOrNull { it.value }!!
        assertTrue(
            "상위 ${top.size}개에서 '${worst.key}'로 시작하는 이름이 ${worst.value}개 — 3개 이하여야 함",
            worst.value <= 3,
        )
    }

    /**
     * 출생신고 통계를 물리면 추천 상위가 '요즘 쓰는 이름'이어야 한다.
     *
     * 성명학 조건만 보면 판수(判樹)·백승(白承) 같은 옛 이름도 만점이 나와서, 예전에는
     * 점수 1점 차이로 첫 화면을 차지했다. 등급 구간 안에서는 대중성이 앞선다.
     */
    @Test
    fun `추천 파이프라인 - 통계를 물리면 요즘 쓰는 이름이 앞선다`() {
        val stats = asset("name-stats.tsv").bufferedReader().useLines { NameStats.parse(it) }
        val saju = SajuNamingService.analyze(BirthInput(2024, 2, 4, 16, 30, Gender.M))
        val generator = NameGenerator(db, pool, stats)
        val candidates = generator.generate("김", listOf(db.byChar.getValue('金')), Gender.M, saju)

        assertTrue("후보가 최소 10개는 나와야 함", candidates.size >= 10)

        // 최고 등급 구간 안에 출생신고 순위권 이름이 있다면, 1위도 순위권이어야 한다.
        val topBand = candidates.filter { it.evaluation.score >= 85 }
        if (topBand.any { it.stat?.latestRank != null }) {
            assertNotNull(
                "1위 ${candidates.first().givenName} 가 출생신고 순위권 이름이 아님",
                candidates.first().stat?.latestRank,
            )
        }

        // tier 3 은 경자·순자·판수 같은 옛 이름이라 두자 추천에 섞이면 안 된다.
        candidates.forEach {
            assertTrue("${it.givenName}: tier ${it.tier} — 두자 추천은 tier 2 이하여야 함", it.tier <= 2)
        }
    }

    /** 외자는 통계 순위가 안 잡혀 대부분 tier 3 다 — 상한을 열어 주지 않으면 후보가 말라붙는다. */
    @Test
    fun `추천 파이프라인 - 외자 모드는 tier 3 까지 연다`() {
        val generator = NameGenerator(db, pool)
        val saju = SajuNamingService.analyze(BirthInput(2024, 2, 4, 16, 30, Gender.M))
        val candidates = generator.generate(
            "김", listOf(db.byChar.getValue('金')), Gender.M, saju,
            GeneratorOptions(singleSyllable = true),
        )
        assertTrue("외자 후보가 나와야 함 (현재 ${candidates.size})", candidates.size >= 5)
        candidates.forEach { assertEquals("외자여야 함", 1, it.givenName.length) }
    }

    @Test
    fun `추천 파이프라인 - 출생 전(사주 없이) 여아 이름`() {
        val surnameLee = db.byChar['李'] ?: error("李 없음")
        val generator = NameGenerator(db, pool)
        val candidates = generator.generate("이", listOf(surnameLee), Gender.F, saju = null)
        assertTrue(candidates.size >= 10)
    }

    @Test
    fun `감명 - 임의 이름 평가가 완결된 결과를 낸다`() {
        val saju = SajuNamingService.analyze(BirthInput(1995, 7, 15, 10, 30, Gender.F))
        val s = db.byChar.getValue('金')
        val n1 = db.candidatesFor("지").firstOrNull() ?: error("'지' 한자 없음")
        val n2 = db.candidatesFor("아").firstOrNull() ?: error("'아' 한자 없음")

        val eval = NameEvaluator.evaluate("김", "지아", listOf(s), listOf(n1, n2), saju)

        assertEquals(4, eval.suri.all.size)
        assertNotNull(eval.baleum)
        assertNotNull(eval.sajuFit)
        assertTrue(eval.score in 0..100)
    }

    @Test
    fun `한자 조합 추천 - 고정 한글 이름`() {
        val generator = NameGenerator(db, pool)
        val saju = SajuNamingService.analyze(BirthInput(2020, 5, 5, 8, 0, Gender.M))
        val combos = generator.hanjaCombosFor(
            surname = "김",
            surnameHanja = listOf(db.byChar.getValue('金')),
            givenName = "민준",
            saju = saju,
        )
        assertTrue("민준 한자 조합이 나와야 함", combos.isNotEmpty())
        // 이름은 고정이므로 한글은 모두 같고 한자만 달라야 한다
        assertTrue(combos.all { it.givenName == "민준" })
        assertEquals(combos.size, combos.map { it.givenHanja.map(HanjaEntry::char) }.distinct().size)
        combos.forEach { e ->
            assertEquals(2, e.givenHanja.size)
            e.givenHanja.forEach { h -> assertTrue("${h.char}: 벽자 금지", h.usableForNaming) }
        }
        // 점수 내림차순
        assertEquals(combos.map { it.score }.sortedDescending(), combos.map { it.score })
    }

    @Test
    fun `한자 조합 추천 - 외자와 세 글자 이름도 지원한다`() {
        val generator = NameGenerator(db, pool)
        val surnameKim = listOf(db.byChar.getValue('金'))

        val single = generator.hanjaCombosFor("김", surnameKim, "훈", saju = null)
        assertTrue("외자 '훈' 조합이 나와야 함", single.isNotEmpty())
        assertTrue(single.all { it.givenHanja.size == 1 })

        val triple = generator.hanjaCombosFor("김", surnameKim, "다현우", saju = null)
        assertTrue("세 글자 '다현우' 조합이 나와야 함", triple.isNotEmpty())
        assertTrue(triple.all { it.givenHanja.size == 3 })

        // 지원 범위 밖
        assertTrue(generator.hanjaCombosFor("김", surnameKim, "", saju = null).isEmpty())
        assertTrue(generator.hanjaCombosFor("김", surnameKim, "가나다라", saju = null).isEmpty())
    }

    @Test
    fun `한자 조합 추천 - 후보 많은 음절도 상한 안에서 끝난다`() {
        val generator = NameGenerator(db, pool)
        // '지'·'영' 은 쓸 수 있는 한자가 많아 데카르트 곱이 수백을 넘는다
        val combos = generator.hanjaCombosFor(
            surname = "이",
            surnameHanja = listOf(db.byChar.getValue('李')),
            givenName = "지영",
            saju = null,
            limit = 15,
        )
        assertTrue(combos.isNotEmpty())
        assertTrue("limit 을 넘으면 안 됨 (현재 ${combos.size})", combos.size <= 15)
    }
}
