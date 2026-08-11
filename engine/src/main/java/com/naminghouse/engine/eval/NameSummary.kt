package com.naminghouse.engine.eval

import com.naminghouse.engine.oheng.OhengRelation
import com.samramanshang.manseryeok.orrery.model.Element

/** 총평 — 판정 한 줄, 강점, 주의점 */
data class NameSummary(
    val verdict: String,
    val strengths: List<String>,
    val cautions: List<String>,
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

    // 불용한자
    if (eval.bulyongWarnings.isNotEmpty()) {
        cautions += "전통적으로 이름에 꺼리는 글자 " +
            eval.bulyongWarnings.joinToString("·") { it.first.toString() } +
            "이(가) 있습니다(학파에 따라 이견이 있는 속설)."
    }

    return NameSummary(verdict, strengths, cautions)
}
