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
    /** 대법원 출생신고 통계 (없는 이름이면 null) */
    val stat: NameStat? = null,
)

data class GeneratorOptions(
    val school: BaleumSchool = BaleumSchool.UNHAE,
    /** 후보 최대 개수 */
    val limit: Int = 60,
    /**
     * 이름 풀 tier 상한 (1=인기 이름만).
     *
     * 기본 2 — tier 3(두자 444개)은 경자·순자·옥순·판수처럼 지금은 아기에게
     * 짓지 않는 옛 세대 이름이 대부분이라 두자 추천에서 뺀다. 성명학 조건만 보면
     * 만점이 나올 수 있어서, 걸러 내지 않으면 추천 첫 화면에 올라온다.
     *
     * 외자는 예외다 — 출생신고 통계에 순위가 잡히지 않아 45개 중 34개가 tier 3 로
     * 떨어진다. 외자 모드에서는 [NameGenerator.generate] 가 자동으로 3까지 연다.
     */
    val maxTier: Int = 2,
    /** 발음오행 상극 배열 이름 제외 */
    val excludeSanggeuk: Boolean = true,
    /** 불용한자 포함 조합 제외 */
    val excludeBulyong: Boolean = true,
    /** 원형이정 4격 모두 길수인 조합만 */
    val requireAllGoodSuri: Boolean = true,
    /** 외자(한 글자) 이름만 추천 */
    val singleSyllable: Boolean = false,
    /** 돌림자 — 지정하면 그 글자가 든 두자 이름만 후보로 쓴다(외자에는 무의미해 무시) */
    val fixedSyllable: Char? = null,
    /** 돌림자 위치 — false=첫 글자, true=끝 글자 */
    val fixedLast: Boolean = false,
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
    private val stats: NameStats = NameStats.EMPTY,
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
        val wantLength = if (options.singleSyllable) 1 else 2
        val validPairs =
            if (wantLength == 2) goodStrokePairs(surnameStrokes, options.requireAllGoodSuri)
            else emptySet()
        val validSingles =
            if (wantLength == 1) goodStrokeSingles(surnameStrokes, options.requireAllGoodSuri)
            else emptySet()
        val targets = saju?.targetElements.orEmpty().toSet()
        val gisin = saju?.gisin.orEmpty().toSet()

        val results = ArrayList<NameCandidate>(options.limit * 2)

        // 외자는 통계 순위가 안 잡혀 대부분 tier 3 다 — 외자 모드에서는 상한을 열어 준다.
        val tierCap = if (options.singleSyllable) maxOf(options.maxTier, 3) else options.maxTier

        for (pool in namePool.forGender(gender, tierCap)) {
            if (pool.name.length != wantLength) continue
            if (wantLength == 2 && options.fixedSyllable != null) {
                val at = if (options.fixedLast) 1 else 0
                if (pool.name[at] != options.fixedSyllable) continue
            }

            val full = surname + pool.name
            val baleum = BaleumOheng.evaluate(full, options.school) ?: continue
            if (options.excludeSanggeuk && baleum.hasSanggeuk) continue

            val best = if (wantLength == 1) {
                bestHanjaSingle(pool.name, validSingles, targets, gisin, options)
            } else {
                bestHanjaCombo(pool.name, surnameStrokes, validPairs, targets, gisin, options)
            } ?: continue

            val evaluation = NameEvaluator.evaluate(
                surname = surname,
                givenName = pool.name,
                surnameHanja = surnameHanja,
                givenHanja = best,
                saju = saju,
                school = options.school,
            )
            results.add(NameCandidate(pool.name, best, evaluation, pool.tier, stats[pool.name]))
        }

        // 만점 후보가 여럿 나오므로 동점 처리가 곧 추천 순위다.
        //
        // 사용자에게 보이는 등급(대길·길·보통)으로 뭉갠 뒤, 그 안에서 '요즘 쓰는
        // 이름인가'로 가른다. 96점과 99점은 성명학적으로 구분할 만한 차이가 아니고
        // 화면에도 똑같이 '대길'로 나가는데, 점수를 1점 단위로 세우면 출생신고 실적이
        // 없는 이름이 1점 차로 첫 화면을 차지한다.
        // tier 로는 못 거른다 — 두환·백승은 손으로 매긴 tier 2 지만 통계에는 없다.
        // 점수는 tier·한자 친숙도보다 앞에 둔다 — 통계를 안 넘긴 호출부에서는 대중성
        // 신호가 전부 같아지므로, 그때는 예전처럼 점수가 순위를 결정해야 한다.
        val ranked = results.sortedWith(
            compareByDescending<NameCandidate> { gradeBand(it.evaluation.score) }
                .thenBy { popularityBand(it) }
                .thenBy { it.stat?.latestRank?.second ?: Int.MAX_VALUE }
                .thenByDescending { it.stat?.total ?: 0 }
                .thenByDescending { it.evaluation.score }
                .thenBy { it.tier }
                .thenByDescending { c -> c.hanja.sumOf { it.nameFit } }
                .thenBy { it.givenName }
        )
        return diversify(ranked, options.limit)
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

    /**
     * 첫 글자가 같은 이름이 상위에 몰리는 걸 완화한다.
     *
     * 사주 보완 오행에 딱 맞는 한 글자(예: 土가 필요할 때 垈)가 있으면 그 글자를 쓰는 이름이
     * 죄다 만점 근처로 몰려 "김대영·김대호·김대현·김대운"처럼 첫인상이 단조로워진다.
     * 점수 순서는 그대로 두되 같은 첫 글자가 [MAX_PER_SYLLABLE]개를 넘으면 뒤로 미룬다.
     */
    /** 화면에 나가는 등급과 같은 구간 — [NameEvaluator.gradeOf] 와 경계를 맞춘다. */
    private fun gradeBand(score: Int): Int = when {
        score >= 85 -> 3
        score >= 70 -> 2
        score >= 50 -> 1
        else -> 0
    }

    /**
     * 요즘 쓰이는 이름인가 — 대법원 출생신고 집계 기준. 작을수록 앞.
     *
     * tier 는 손으로 매긴 값이 섞여 있어 이 판정에 쓸 수 없다(두환·백승이 tier 2 인데
     * 집계에는 없다). 다만 집계 자체가 시도별 상위 20위 합산이라 213개밖에 안 되므로,
     * 걸러 내는 조건이 아니라 **앞으로 끌어올리는** 신호로만 쓴다 — 필터로 쓰면 추천이
     * 213개 안으로 갇힌다. 통계를 안 넘긴 호출부에서는 전부 2 가 되어 순위에 영향이 없다.
     */
    private fun popularityBand(c: NameCandidate): Int = when {
        c.stat?.latestRank != null -> 0 // 최근 순위권에 든 이름
        c.stat != null -> 1             // 집계엔 있으나 순위권 밖
        else -> 2                       // 출생신고 집계에 아예 없음
    }

    private fun diversify(ranked: List<NameCandidate>, limit: Int): List<NameCandidate> {
        val picked = ArrayList<NameCandidate>(limit)
        val deferred = ArrayList<NameCandidate>()
        val seen = HashMap<Char, Int>()

        for (c in ranked) {
            val first = c.givenName.first()
            val n = seen.getOrDefault(first, 0)
            if (n < MAX_PER_SYLLABLE) {
                seen[first] = n + 1
                picked.add(c)
                if (picked.size == limit) return picked
            } else {
                deferred.add(c)
            }
        }
        // 다양성 제약으로 미뤄둔 후보로 남은 자리를 채운다(점수 순서 유지)
        for (c in deferred) {
            if (picked.size == limit) break
            picked.add(c)
        }
        return picked
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

    /** 외자용 — 4격 전부 길수가 되는 이름 한 글자 획수 */
    private fun goodStrokeSingles(surnameStrokes: List<Int>, requireAllGood: Boolean): Set<Int> =
        (1..MAX_STROKE).filterTo(HashSet()) { a ->
            !requireAllGood || SuriCalculator.calculate(surnameStrokes, listOf(a)).allGood
        }

    /** 외자 이름의 최적 한자 — 조합 탐색 없이 한 글자 후보에서 고른다. */
    private fun bestHanjaSingle(
        givenName: String,
        validSingles: Set<Int>,
        targets: Set<Element>,
        gisin: Set<Element>,
        options: GeneratorOptions,
    ): List<HanjaEntry>? =
        candidatesOf(givenName, options)
            .filter { it.wonhoek in validSingles }
            .maxByOrNull { comboScore(listOf(it), targets, gisin) }
            ?.let { listOf(it) }

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
                val score = comboScore(listOf(h1, h2), targets, gisin)
                if (score > bestScore) {
                    bestScore = score
                    best = listOf(h1, h2)
                }
            }
        }
        return best
    }

    /** 빠른 조합 적합도 — 전체 감명 이전의 가지치기용 점수. 외자(1글자)도 같은 기준. */
    private fun comboScore(picked: List<HanjaEntry>, targets: Set<Element>, gisin: Set<Element>): Int {
        var score = 0
        val elements = picked.mapNotNull { it.element }
        score += elements.size * 2 // 자원오행 미상보다 명확한 글자 우대
        score += targets.intersect(elements.toSet()).size * 10
        score -= elements.count { it in gisin && it !in targets } * 8
        // 흔히 쓰는 글자 우대 — 같은 획수·오행이면 낯선 글자보다 익숙한 글자를 고른다.
        score += picked.sumOf { it.nameFit } * 3
        score += picked.count { it.meaning.isNotEmpty() }
        return score
    }

    private companion object {
        const val MAX_STROKE = 30

        /** 한자 조합 탐색 상한 — '지'처럼 후보가 많은 음절이 겹치면 곱이 수천을 넘는다. */
        const val MAX_COMBOS = 1500

        /** 추천 목록에서 첫 글자가 같은 이름의 최대 개수 */
        const val MAX_PER_SYLLABLE = 2
    }
}
