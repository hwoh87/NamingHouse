package com.naminghouse.engine.gen

import com.naminghouse.engine.data.BulyongHanja
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.oheng.BaleumOheng
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.saju.SajuSummary
import com.naminghouse.engine.suri.SuriCalculator
import com.samramanshang.manseryeok.orrery.model.Element
import com.samramanshang.manseryeok.orrery.model.Gender

/** 추천 후보 하나 — 한글 이름 + 최적 한자 조합 + 감명 결과 */
data class NameCandidate(
    val givenName: String,
    val hanja: List<HanjaEntry>,
    val evaluation: NameEvaluation,
    val tier: Int,
)

data class GeneratorOptions(
    val school: BaleumSchool = BaleumSchool.UNHAE,
    /** 후보 최대 개수 */
    val limit: Int = 60,
    /** 이름 풀 tier 상한 (1=인기 이름만) */
    val maxTier: Int = 3,
    /** 발음오행 상극 배열 이름 제외 */
    val excludeSanggeuk: Boolean = true,
    /** 불용한자 포함 조합 제외 */
    val excludeBulyong: Boolean = true,
    /** 원형이정 4격 모두 길수인 조합만 */
    val requireAllGoodSuri: Boolean = true,
)

/**
 * 이름 후보 생성기.
 *
 * 흐름: 한글 이름 풀(성별 필터) → 발음오행·음양 1차 필터 →
 * 성 원획에 대해 4격 전부 길수가 되는 획수 조합 사전계산 →
 * 획수·자원오행 조건에 맞는 한자 조합 탐색 → 종합 감명 점수로 정렬.
 */
class NameGenerator(
    private val hanjaDb: HanjaDb,
    private val namePool: NamePool,
) {

    fun generate(
        surname: String,
        surnameHanja: List<HanjaEntry>,
        gender: Gender,
        saju: SajuSummary?,
        options: GeneratorOptions = GeneratorOptions(),
    ): List<NameCandidate> {
        require(surnameHanja.isNotEmpty()) { "성씨 한자가 필요함" }
        val surnameStrokes = surnameHanja.map { it.wonhoek }
        val validPairs = goodStrokePairs(surnameStrokes, options.requireAllGoodSuri)
        val targets = saju?.targetElements.orEmpty().toSet()
        val gisin = saju?.gisin.orEmpty().toSet()

        val results = ArrayList<NameCandidate>(options.limit * 2)

        for (pool in namePool.forGender(gender, options.maxTier)) {
            if (pool.name.length != 2) continue // v1 은 두 글자 이름만 생성

            val full = surname + pool.name
            val baleum = BaleumOheng.evaluate(full, options.school) ?: continue
            if (options.excludeSanggeuk && baleum.hasSanggeuk) continue

            val best = bestHanjaCombo(pool.name, surnameStrokes, validPairs, targets, gisin, options)
                ?: continue

            val evaluation = NameEvaluator.evaluate(
                surname = surname,
                givenName = pool.name,
                surnameHanja = surnameHanja,
                givenHanja = best,
                saju = saju,
                school = options.school,
            )
            results.add(NameCandidate(pool.name, best, evaluation, pool.tier))
        }

        // 만점 후보가 여럿 나오므로 동점 처리가 곧 추천 순위다:
        // 점수 → 이름 대중성(tier) → 한자 친숙도 → 이름(결정적 순서) 순으로 가른다.
        return results
            .sortedWith(
                compareByDescending<NameCandidate> { it.evaluation.score }
                    .thenBy { it.tier }
                    .thenByDescending { c -> c.hanja.sumOf { it.nameFit } }
                    .thenBy { it.givenName }
            )
            .take(options.limit)
    }

    /**
     * 이미 정해진 한글 이름에 붙일 한자 조합을 점수순으로 나열한다(한자 추천 화면).
     *
     * 이름 후보 생성과 달리 여기서는 4격 전길을 강제하지 않는 편이 낫다 —
     * 사용자가 이름을 이미 정해 온 상황이라 "결과 없음"보다 차선 조합이라도 보여주고
     * 점수로 우열을 알려주는 쪽이 쓸모 있다. 호출부가 options 로 조일 수는 있다.
     */
    fun hanjaCombosFor(
        surname: String,
        surnameHanja: List<HanjaEntry>,
        givenName: String,
        saju: SajuSummary?,
        options: GeneratorOptions = GeneratorOptions(requireAllGoodSuri = false),
        limit: Int = 30,
    ): List<NameEvaluation> {
        if (givenName.isEmpty() || givenName.length > 3) return emptyList()
        val perSyllable = givenName.map { candidatesOf(it.toString(), options) }
        if (perSyllable.any { it.isEmpty() }) return emptyList()

        val combos = ArrayList<NameEvaluation>()
        forEachCombo(perSyllable, MAX_COMBOS) { picked ->
            if (!options.requireAllGoodSuri ||
                SuriCalculator.calculate(surnameHanja.map { it.wonhoek }, picked.map { it.wonhoek }).allGood
            ) {
                combos.add(
                    NameEvaluator.evaluate(surname, givenName, surnameHanja, picked, saju, options.school)
                )
            }
        }
        return combos
            .sortedWith(
                compareByDescending<NameEvaluation> { it.score }
                    .thenByDescending { e -> e.givenHanja.sumOf { it.nameFit } }
            )
            .take(limit)
    }

    /** 음절별 후보의 데카르트 곱을 [cap] 개까지 순회한다. */
    private inline fun forEachCombo(
        perSyllable: List<List<HanjaEntry>>,
        cap: Int,
        action: (List<HanjaEntry>) -> Unit,
    ) {
        val sizes = perSyllable.map { it.size }
        val total = sizes.fold(1L) { acc, n -> acc * n }
        val count = minOf(total, cap.toLong()).toInt()
        val idx = IntArray(perSyllable.size)
        repeat(count) {
            action(perSyllable.mapIndexed { i, list -> list[idx[i]] })
            // 자릿수 올림 — 마지막 음절부터 증가시킨다.
            for (i in idx.indices.reversed()) {
                if (++idx[i] < sizes[i]) break
                idx[i] = 0
            }
        }
    }

    /** 4격 전부 길수가 되는 (이름1획, 이름2획) 조합 — 성 획수에 대해 사전계산 */
    private fun goodStrokePairs(surnameStrokes: List<Int>, requireAllGood: Boolean): Set<Pair<Int, Int>> {
        val pairs = HashSet<Pair<Int, Int>>()
        for (a in 1..MAX_STROKE) for (b in 1..MAX_STROKE) {
            val gyeok = SuriCalculator.calculate(surnameStrokes, listOf(a, b))
            if (!requireAllGood || gyeok.allGood) pairs.add(a to b)
        }
        return pairs
    }

    /**
     * 후보 한자 — 벽자·부적합자를 먼저 걷어낸다.
     * 이 필터가 없으면 획수·자원오행만 맞는 叨(탐할)·瘰(연주창) 같은 글자가 추천에 올라온다.
     */
    private fun candidatesOf(syllable: String, options: GeneratorOptions): List<HanjaEntry> =
        hanjaDb.candidatesFor(syllable)
            .asSequence()
            .filter { it.wonhoek in 1..MAX_STROKE }
            .filter { it.usableForNaming }
            .filter { !options.excludeBulyong || BulyongHanja.map[it.char] == null }
            .sortedWith(compareByDescending<HanjaEntry> { it.nameFit }.thenBy { it.wonhoek })
            .toList()

    /**
     * 이름 두 글자의 최적 한자 조합.
     * 자원오행이 보완 대상 오행을 최대한 덮고 기신을 피하는 조합을 고른다.
     */
    private fun bestHanjaCombo(
        givenName: String,
        surnameStrokes: List<Int>,
        validPairs: Set<Pair<Int, Int>>,
        targets: Set<Element>,
        gisin: Set<Element>,
        options: GeneratorOptions,
    ): List<HanjaEntry>? {
        val c1 = candidatesOf(givenName[0].toString(), options)
        val c2 = candidatesOf(givenName[1].toString(), options)
        if (c1.isEmpty() || c2.isEmpty()) return null

        var best: List<HanjaEntry>? = null
        var bestScore = Int.MIN_VALUE
        var examined = 0

        for (h1 in c1) {
            for (h2 in c2) {
                if ((h1.wonhoek to h2.wonhoek) !in validPairs) continue
                if (++examined > 2000) return best
                val score = comboScore(h1, h2, targets, gisin)
                if (score > bestScore) {
                    bestScore = score
                    best = listOf(h1, h2)
                }
            }
        }
        return best
    }

    /** 빠른 조합 적합도 — 전체 감명 이전의 가지치기용 점수 */
    private fun comboScore(h1: HanjaEntry, h2: HanjaEntry, targets: Set<Element>, gisin: Set<Element>): Int {
        var score = 0
        val elements = listOfNotNull(h1.element, h2.element)
        score += elements.size * 2 // 자원오행 미상보다 명확한 글자 우대
        score += targets.intersect(elements.toSet()).size * 10
        score -= elements.count { it in gisin && it !in targets } * 8
        // 흔히 쓰는 글자 우대 — 같은 획수·오행이면 낯선 글자보다 익숙한 글자를 고른다.
        score += (h1.nameFit + h2.nameFit) * 3
        if (h1.meaning.isNotEmpty()) score += 1
        if (h2.meaning.isNotEmpty()) score += 1
        return score
    }

    private companion object {
        const val MAX_STROKE = 30

        /** 한자 조합 탐색 상한 — '지'처럼 후보가 많은 음절이 겹치면 곱이 수천을 넘는다. */
        const val MAX_COMBOS = 1500
    }
}
