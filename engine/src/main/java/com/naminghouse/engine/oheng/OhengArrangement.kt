package com.naminghouse.engine.oheng

/** 오행 배열의 품질 — 발음오행·수리오행 공용 판정 */
enum class ArrangementQuality {
    /** 상극이 없고 상생이 하나 이상 — 길 */
    SANGSAENG,

    /** 상극은 없지만 전부 비화(같은 오행) — 유파별 이견이 커 보통으로 둔다 */
    BIHWA_ONLY,

    /** 상극이 하나라도 있음 — 흉 */
    SANGGEUK,
}

/**
 * 인접 쌍 관계 목록으로 배열 품질을 판정한다.
 *
 * **비화는 감점하지 않는다.** 작명왕 실측 두 건이 근거다 —
 * 수리오행 木火火(생+비화)와 발음오행 金金土(비화+역생) 모두 최고 등급("매우 좋아요")을
 * 받았고 본문도 "상생 관계를 이루고 있다"고 서술했다. 즉 상극 유무만으로 가른다.
 * 한국식 발음오행 125칸 배합표도 116칸(93%)이 이 규칙으로 설명된다.
 *
 * 다만 순수 비화(木木木 등 다섯 가지)는 소스마다 길흉이 정반대라 길로 올리지 않고
 * 보통에 둔다.
 */
fun arrangementQualityOf(relations: List<OhengRelation>): ArrangementQuality = when {
    relations.any { it == OhengRelation.SANGGEUK } -> ArrangementQuality.SANGGEUK
    relations.any { it == OhengRelation.SANGSAENG } -> ArrangementQuality.SANGSAENG
    else -> ArrangementQuality.BIHWA_ONLY
}
