package com.naminghouse.engine.eval

import com.naminghouse.engine.data.BulyongSeverity
import com.naminghouse.engine.oheng.BaleumPath
import com.naminghouse.engine.oheng.OhengRelation
import com.samramanshang.manseryeok.orrery.model.Element

/** 총평 — 판정 한 줄, 강점, 주의점, 그리고 걸리는 축을 어떻게 풀지 제안 */
data class NameSummary(
    val verdict: String,
    val strengths: List<String>,
    val cautions: List<String>,
    /** 나쁜 축이 있을 때만 — "무엇을 바꾸면 되는지"를 한 줄씩, 배점 큰 축부터 최대 3개 */
    val suggestions: List<String> = emptyList(),
)

/**
 * 오행 한자 뒤에 붙일 조사 — 그 오행의 한글 음에 받침이 있으면 '으로', 없으면 '로'.
 * (목·금은 받침이 있고 화·토·수는 없다) "土 으로" 같은 비문을 막는다.
 */
private fun particleOf(last: Element): String =
    if (last == Element.TREE || last == Element.METAL) " 으로" else " 로"

/**
 * 축별 결과를 읽어 총평 문장을 만든다.
 *
 * 작명왕은 등급만 갈아끼운 정형문("이 두 가지 분야에서 모두 좋은 의미를 가지는…")을 쓰는데,
 * 그러면 어느 이름이든 같은 말이라 쓸모가 없다. 여기서는 **실제로 어느 축이 좋고 어디가
 * 걸리는지**를 짚어, 후보끼리 비교할 근거가 되게 한다.
 */
/**
 * 이름 글자의 뜻을 한 줄로 — "길(道) + 오랠(永)" 처럼 훈에 글자를 병기한다.
 *
 * 사전 훈(訓)을 한 글자도 바꾸지 않는다. 명사로 다듬어 문장을 만들면 자연스럽긴 하지만
 * 한국어 ㄹ불규칙 때문에 기계 규칙으로는 永(길)이 "김"이 되는 식의 오차가 난다.
 * 문장화 대신 글자를 병기해, 뜻은 그대로 두면서 동음이의를 구분할 수 있게 했다
 * (道·永 이 둘 다 "길"이라 예전 표기 "길 · 길"로는 같은 글자처럼 보였다).
 *
 * 구분자로 가운뎃점 대신 `+` 를 쓴다 — 두 한자는 서로 수식하지 않고 각각 더해진
 * 관계라, 나열로 읽히는 `·` 보다 정확하다.
 */
fun meaningLine(eval: NameEvaluation): String =
    eval.givenHanja.joinToString(" + ") { "${hunOf(it.meaning)}(${it.char})" }

/** "길 도" → "길". 훈음에서 음을 떼어 훈만 남긴다. */
private fun hunOf(meaning: String): String {
    if (meaning.isBlank()) return "뜻 미상"
    return if (" " in meaning) meaning.substringBeforeLast(' ') else meaning
}

fun summarize(eval: NameEvaluation): NameSummary {
    val full = eval.surname + eval.givenName
    val hanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() }

    val verdict = "$full($hanja)은 종합 ${eval.score}점으로 '${eval.grade}'에 해당하는 이름입니다."

    val strengths = mutableListOf<String>()
    val cautions = mutableListOf<String>()

    // 수리사격
    if (eval.suri.allGood) {
        strengths += "원형이정 네 격(${eval.suri.all.joinToString("·") { "${it.number}" }})이 모두 길수입니다."
    } else {
        val bad = eval.suri.all.filterNot { it.grade.isGood }
        cautions += "수리사격 중 ${bad.joinToString("·") { "${it.number}수 ${it.title}" }}이(가) 흉수입니다."
    }

    // 발음오행
    eval.baleum?.let { b ->
        val chain = b.elements.joinToString("→") { it.hanja }
        when {
            b.rescuedByJongseong ->
                strengths += "발음오행은 초성만 보면 $chain 이지만, 성씨 받침(${eval.surname}의 " +
                    "받침)까지 세는 방식으로는 상극 없이 이어집니다."
            b.hasSanggeuk -> cautions += "발음오행 $chain 배열에 상극이 있어 소리의 흐름이 끊깁니다."
            b.relations.all { it == OhengRelation.SANGSAENG } ->
                strengths += "발음오행이 $chain${particleOf(b.elements.last())} 이어져 소리가 서로를 살립니다."
            b.relations.any { it == OhengRelation.SANGSAENG } ->
                strengths += "발음오행 $chain 은 상극 없이 이어집니다."
            else -> cautions += "발음오행이 $chain 으로 같은 기운만 겹칩니다."
        }
    }

    // 수리오행
    val suri = eval.suriOheng
    val suriChain = suri.elements.joinToString("→") { it.hanja }
    when {
        suri.hasSanggeuk -> cautions += "획수에서 나온 수리오행 $suriChain 에 상극이 있습니다."
        suri.relations.all { it == OhengRelation.SANGSAENG } ->
            strengths += "획수에서 나온 수리오행도 $suriChain${particleOf(suri.elements.last())} 상생합니다."
        suri.relations.any { it == OhengRelation.SANGSAENG } ->
            strengths += "획수에서 나온 수리오행 $suriChain 도 상극 없이 이어집니다."
        else -> Unit // 순수 비화는 굳이 언급하지 않는다
    }

    // 자원오행 · 사주보완
    val fit = eval.sajuFit
    if (fit != null) {
        if (fit.matched.isNotEmpty()) {
            strengths += "이름의 자원오행이 사주에 부족한 " +
                fit.matched.joinToString("·") { it.hanja } + " 기운을 채웁니다."
        } else if (fit.surnameCovered.isNotEmpty()) {
            strengths += "성씨 한자가 이미 " + fit.surnameCovered.joinToString("·") { it.hanja } +
                " 기운을 갖추고 있어 보완이 되어 있습니다."
        } else {
            cautions += "보완이 필요한 " + fit.targets.joinToString("·") { it.hanja } +
                " 기운을 이름이 채우지 못합니다."
        }
        if (fit.gisinUsed.isNotEmpty()) {
            cautions += "사주에 부담이 되는 " + fit.gisinUsed.joinToString("·") { it.hanja } +
                " 기운이 이름에 들어 있습니다."
        }
    }

    // 음양
    if (!eval.strokeEumyang.isBalanced) {
        val kind = if (eval.strokeEumyang.pattern.first()) "순양" else "순음"
        cautions += "획수 음양이 $kind(${eval.strokeEumyang.display})으로 한쪽에 치우칩니다."
    } else if (eval.soundEumyang?.isBalanced == true) {
        strengths += "획수와 소리 모두 음양이 고르게 섞였습니다."
    }

    // 불용한자 — 감점 대상인 '기피'만 주의로 올린다. 속설 등급은 상세 화면의 참고 카드에만 둔다.
    val gipi = eval.bulyongWarnings.filter { it.second.severity == BulyongSeverity.GIPI }
    if (gipi.isNotEmpty()) {
        cautions += "뜻이 좋지 않아 이름에 쓰지 않는 글자 " +
            gipi.joinToString("·") { it.first.toString() } + "이(가) 있습니다."
    }

    // ── 개선 제안 — 주의점을 어떻게 풀지. 소리를 바꿔야 하는 축(발음)과 한자만
    // 바꾸면 되는 축(수리·자원·수리음양)을 구분해 알려 준다. 배점 큰 축부터 최대 3개.
    val suggestions = mutableListOf<String>()

    if (!eval.suri.allGood) {
        suggestions += "네 격은 한자 획수에서 나옵니다 — 같은 이름이라도 획수가 다른 한자를 " +
            "고르면 달라지니, '한자 추천'에서 전길 조합을 찾아보세요."
    }
    if (fit != null && (fit.covered.isEmpty() || fit.gisinUsed.isNotEmpty())) {
        suggestions += "보완 대상 오행(" + fit.targets.joinToString("·") { it.hanja } +
            ") 자원의 한자로 바꿔 보세요 — '한자 추천'이 사주 보완 순으로 정렬해 줍니다."
    }
    if (eval.baleum?.path == BaleumPath.NONE) {
        suggestions += "발음오행은 소리에서 나와 한자로는 고칠 수 없습니다 — " +
            "'이름 추천'에서 상생 배열의 이름을 받아 보세요."
    }
    if (!eval.strokeEumyang.isBalanced) {
        suggestions += "홀수 획(양)과 짝수 획(음) 한자가 섞이면 풀립니다 — " +
            "같은 음의 다른 한자로 바꿔 보세요."
    }
    eval.soundEumyang?.let { se ->
        if (!se.isBalanced) {
            // 중성 ㅣ는 양으로 표시되므로, 편중일 때 음(●)이 하나라도 있으면 순음 쪽이다
            suggestions += if (se.pattern.all { it }) {
                "모음이 ㅏ·ㅗ 계열(양성)로만 쏠렸습니다 — ㅓ·ㅜ·ㅡ 같은 음성 모음이 든 글자를 섞어 보세요."
            } else {
                "모음이 ㅓ·ㅜ·ㅡ 계열(음성)로만 쏠렸습니다 — ㅏ·ㅗ 같은 양성 모음이 든 글자를 섞어 보세요."
            }
        }
    }
    if (gipi.isNotEmpty()) {
        suggestions += "같은 음의 다른 한자로만 바꿔도 됩니다 — 부르는 이름은 그대로 지킬 수 있습니다."
    }

    return NameSummary(verdict, strengths, cautions, suggestions.take(3))
}
