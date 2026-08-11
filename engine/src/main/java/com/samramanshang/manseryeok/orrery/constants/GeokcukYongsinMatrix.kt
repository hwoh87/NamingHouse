package com.samramanshang.manseryeok.orrery.constants

import com.samramanshang.manseryeok.orrery.model.Element

/**
 * 격국 × 용신 매핑 — 격국이 정해진 후 용신 오행과 결합해 만드는 인생 톤.
 *
 * "정관격이고 용신이 인성(목)이면 학문·명예 안정형" 같은 한 줄 통찰을
 * 사용자에게 보여주기 위한 정적 테이블.
 *
 * 키: (격국 한자, 용신 오행 카테고리)
 *   - 격국: "正官格" / "偏官格" / "正印格" / "偏印格" / "食神格" / "傷官格" / "正財格" / "偏財格"
 *   - 용신 카테고리는 일간 기준 십신으로 추상화: "비겁"/"식상"/"재성"/"관성"/"인성"
 *     (오행 그 자체가 아닌 십신 카테고리로 매핑 — 일간이 木이든 水든 같은 격식+같은 십신용신이면 톤이 같음)
 *
 * 매핑은 의미 있는 조합 ~25개. 빠진 키는 lookup 함수가 null 반환.
 */
data class GeokcukYongsinTone(
    val lifeType: String,                 // 짧은 라벨 (예: "명예 안정형")
    val tagline: String,                  // 한 줄 통찰 (40자 이내)
    val strengths: List<String>,          // 3개
    val cautions: List<String>,           // 2개
    val careerKeywords: List<String>      // 3-4개
)

/**
 * 일간 오행 + 용신 오행 → 십신 카테고리.
 *   비겁: 일간 = 용신
 *   식상: 일간이 생하는 오행
 *   재성: 일간이 극하는 오행
 *   관성: 일간을 극하는 오행
 *   인성: 일간을 생하는 오행
 */
fun yongsinCategory(dayElement: Element, yongsin: Element): String? {
    val e = Element.entries
    val di = e.indexOf(dayElement)
    val yi = e.indexOf(yongsin)
    if (di < 0 || yi < 0) return null
    val genTo = (di + 1) % 5         // 식상
    val genBy = (di + 4) % 5         // 인성
    // 상극: 0→2(목극토), 2→4(토극수), 4→1(수극화), 1→3(화극금), 3→0(금극목)
    val ctrlTo = mapOf(0 to 2, 2 to 4, 4 to 1, 1 to 3, 3 to 0)[di]!!  // 재성
    val ctrlBy = mapOf(2 to 0, 4 to 2, 1 to 4, 3 to 1, 0 to 3)[di]!!  // 관성
    return when (yi) {
        di     -> "비겁"
        genTo  -> "식상"
        ctrlTo -> "재성"
        ctrlBy -> "관성"
        genBy  -> "인성"
        else   -> null
    }
}

private val MATRIX: Map<Pair<String, String>, GeokcukYongsinTone> = mapOf(

    // ═══════════ 정관격(正官格) ═══════════
    ("正官格" to "인성") to GeokcukYongsinTone(
        lifeType = "명예 안정형",
        tagline = "권위 + 학문 — 조직에서 차곡차곡 신뢰를 쌓는 깊이 있는 인재.",
        strengths = listOf("규율과 학문이 동시 성장", "윗사람의 신뢰를 얻기 쉬움", "압박을 내공으로 변환"),
        cautions = listOf("변화 속도가 느려 답답할 수 있음", "안정에 익숙해 도전을 회피"),
        careerKeywords = listOf("공직", "교육", "연구", "법무·행정")
    ),
    ("正官格" to "비겁") to GeokcukYongsinTone(
        lifeType = "협력 신뢰형",
        tagline = "권위 안에서 동료와 함께 — 조직 안 핵심 멤버.",
        strengths = listOf("팀워크 + 책임감", "동료들이 따르는 리더형", "약속·신용 강함"),
        cautions = listOf("개인의 야망보다 집단을 우선해 손해", "결정 시 동료 눈치"),
        careerKeywords = listOf("관리직", "인사", "기획", "조직 리더")
    ),
    ("正官格" to "재성") to GeokcukYongsinTone(
        lifeType = "관록 + 재력형",
        tagline = "권위와 재물이 함께 자라는 사회적 성공 흐름.",
        strengths = listOf("부귀쌍전(富貴雙全)", "사회적 영향력", "자원 활용 능력"),
        cautions = listOf("재물·권위 동시 추구로 번아웃", "관계의 정치성"),
        careerKeywords = listOf("경영", "정치", "금융", "법조")
    ),

    // ═══════════ 편관격(偏官格) ═══════════
    ("偏官格" to "인성") to GeokcukYongsinTone(
        lifeType = "전문가형",
        tagline = "압박을 학문·관조로 풀어 깊이 있는 전문가가 됨.",
        strengths = listOf("위기 속 침착", "전문 영역에 깊이 파고듦", "고난을 성장 동력화"),
        cautions = listOf("결정이 느려 보일 수 있음", "혼자 짐 짊어지는 경향"),
        careerKeywords = listOf("연구", "전문직", "의료", "종교·철학")
    ),
    ("偏官格" to "식상") to GeokcukYongsinTone(
        lifeType = "실력 돌파형",
        tagline = "압박을 실력·기술로 정면 돌파하는 추진형.",
        strengths = listOf("위기 대응력", "실력 기반 자수성가", "정면 승부 강함"),
        cautions = listOf("정관(규율)과 마찰", "충동적 결정 위험"),
        careerKeywords = listOf("창업", "스포츠", "기술 전문직", "특수직")
    ),
    ("偏官格" to "비겁") to GeokcukYongsinTone(
        lifeType = "동료 의지형",
        tagline = "압박이 강할 땐 신뢰할 동료·연대로 풀어내는 형.",
        strengths = listOf("연대·팀워크 강함", "위기에 약하지 않음", "동료 신뢰 두터움"),
        cautions = listOf("혼자 결정 어려움", "동료 의존도 높음"),
        careerKeywords = listOf("팀 리더", "노조·연대", "프로젝트 매니저")
    ),

    // ═══════════ 정인격(正印格) ═══════════
    ("正印格" to "관성") to GeokcukYongsinTone(
        lifeType = "학문·권위형",
        tagline = "학문이 권위로 이어지는 학자·교수형.",
        strengths = listOf("깊은 학문·교양", "권위와 인덕 겸비", "장기적 안목"),
        cautions = listOf("실행력 부족 위험", "이론에 치우쳐 현실감 떨어짐"),
        careerKeywords = listOf("교수", "연구원", "공직", "지식 전문가")
    ),
    ("正印格" to "비겁") to GeokcukYongsinTone(
        lifeType = "신념 강한 사상가형",
        tagline = "학문 + 자기 신념이 결합된 견고한 사상가.",
        strengths = listOf("자기 세계관 견고", "흔들림 없는 가치관", "장인 정신"),
        cautions = listOf("외부 변화 수용 느림", "고집·아집 위험"),
        careerKeywords = listOf("작가", "사상가", "전통 장인", "독립 연구자")
    ),
    ("正印格" to "재성") to GeokcukYongsinTone(
        lifeType = "지식 → 재물형",
        tagline = "학문·전문성을 재물로 변환하는 지식 사업가.",
        strengths = listOf("전문성 기반 수익", "지식을 자원으로", "학문과 현실 균형"),
        cautions = listOf("재성이 인성을 깨면 학문 흔들림(재파인)", "수익화 압박"),
        careerKeywords = listOf("강의·교육 사업", "출판", "컨설팅", "전문 자영업")
    ),

    // ═══════════ 편인격(偏印格) ═══════════
    ("偏印格" to "관성") to GeokcukYongsinTone(
        lifeType = "독창 학자형",
        tagline = "독자적 통찰이 권위로 인정받는 비주류 전문가형.",
        strengths = listOf("독창적 사고", "남다른 관점·통찰", "비주류에서 두각"),
        cautions = listOf("주류에 적응 어려움", "외로움·고립 위험"),
        careerKeywords = listOf("기획자", "창의 직군", "심리·종교", "예술 평론")
    ),
    ("偏印格" to "비겁") to GeokcukYongsinTone(
        lifeType = "특이 자기영역형",
        tagline = "남다른 사고로 자기만의 독립 영역을 만드는 사람.",
        strengths = listOf("개성 강함", "독립적", "트렌드 리더 가능"),
        cautions = listOf("외부와 단절 위험", "고립 → 우울 가능성"),
        careerKeywords = listOf("프리랜서", "1인 전문가", "특수 전문직")
    ),

    // ═══════════ 식신격(食神格) ═══════════
    ("食神格" to "재성") to GeokcukYongsinTone(
        lifeType = "재능 → 결실형",
        tagline = "재능이 자연스럽게 재물로 결실되는 자수성가형.",
        strengths = listOf("창의력 + 수익화", "안정된 자기 영역", "기술·재능 기반"),
        cautions = listOf("관성 약하면 사회적 명예 부족", "혼자 일하는 경향"),
        careerKeywords = listOf("자영업", "창작", "기술 전문직", "신사업")
    ),
    ("食神格" to "비겁") to GeokcukYongsinTone(
        lifeType = "재능 + 동료형",
        tagline = "동료와 함께 재능을 발휘하는 협업형 창작자.",
        strengths = listOf("팀 창작에 강함", "동료의 재능 끌어냄", "지속 가능"),
        cautions = listOf("의존도 높을 수 있음", "혼자 결정력 약함"),
        careerKeywords = listOf("스튜디오", "팀 창작", "공동 사업", "교육·코칭")
    ),
    ("食神格" to "관성") to GeokcukYongsinTone(
        lifeType = "재능 + 권위형",
        tagline = "창작·기술이 권위·자격으로 인정받는 전문가.",
        strengths = listOf("재능의 사회적 인증", "전문 자격 활용", "안정성"),
        cautions = listOf("규율이 창의를 누를 위험", "스트레스 누적"),
        careerKeywords = listOf("전문 자격직", "라이센스 직군", "공인 전문가")
    ),

    // ═══════════ 상관격(傷官格) ═══════════
    ("傷官格" to "재성") to GeokcukYongsinTone(
        lifeType = "재능 폭발 사업가형",
        tagline = "독창적 표현이 재물로 직결되는 자유 창업형.",
        strengths = listOf("뚜렷한 개성", "시장 감각", "독립성"),
        cautions = listOf("정관과 충돌 — 조직 적응 약함", "변동성 큼"),
        careerKeywords = listOf("표현 전문가", "프리랜서", "개인 사업", "서비스업")
    ),
    ("傷官格" to "인성") to GeokcukYongsinTone(
        lifeType = "재능 + 깊이형",
        tagline = "표현력에 깊이 있는 학문이 결합된 성숙한 창작자.",
        strengths = listOf("표현 + 통찰 균형", "예술적 깊이", "지속 성장"),
        cautions = listOf("이론에 빠져 표현 잃을 위험", "결단 늦어짐"),
        careerKeywords = listOf("문학·예술", "사상 작가", "비평가", "큐레이터")
    ),
    ("傷官格" to "비겁") to GeokcukYongsinTone(
        lifeType = "독립 표현형",
        tagline = "자기 신념과 표현을 끝까지 밀어붙이는 독립 예술가형.",
        strengths = listOf("강한 자기 표현", "흔들리지 않는 신념", "독립성"),
        cautions = listOf("외부 충돌 잦음", "협업 어려움"),
        careerKeywords = listOf("독립 예술가", "1인 전문가", "활동가")
    ),

    // ═══════════ 정재격(正財格) ═══════════
    ("正財格" to "관성") to GeokcukYongsinTone(
        lifeType = "안정 부귀형",
        tagline = "성실한 재물 + 안정된 권위 — 가장 균형 잡힌 부귀.",
        strengths = listOf("성실·신용", "안정된 가정·재산", "사회적 신뢰"),
        cautions = listOf("보수적이라 변화 약함", "큰 도약 어려움"),
        careerKeywords = listOf("회계·세무", "공기업", "은행", "안정 전문직")
    ),
    ("正財格" to "식상") to GeokcukYongsinTone(
        lifeType = "성실 + 재능형",
        tagline = "꾸준한 노력 + 자기 재능이 결합된 자수성가형.",
        strengths = listOf("성실함 + 재능", "지속 가능 수익", "사람들이 신뢰"),
        cautions = listOf("재성이 강해 일간이 부담", "휴식 부족"),
        careerKeywords = listOf("자영업", "기술자", "전문직", "프랜차이즈")
    ),
    ("正財格" to "비겁") to GeokcukYongsinTone(
        lifeType = "동료 + 성실형",
        tagline = "동료·연대로 재물을 모으는 협업 사업형.",
        strengths = listOf("팀 사업 강함", "신뢰망 활용", "안정성"),
        cautions = listOf("동업 분쟁 위험", "재성 분담 갈등"),
        careerKeywords = listOf("동업", "협동조합", "팀 사업", "프랜차이즈 본사")
    ),

    // ═══════════ 편재격(偏財格) ═══════════
    ("偏財格" to "관성") to GeokcukYongsinTone(
        lifeType = "사업가 권위형",
        tagline = "큰 자원을 권위로 운영하는 통 큰 사업가형.",
        strengths = listOf("자원 활용 능력", "사회적 영향력", "통 큰 결단"),
        cautions = listOf("재물 변동성 큼", "관계 의존성"),
        careerKeywords = listOf("경영자", "투자자", "유통·무역", "정치")
    ),
    ("偏財格" to "식상") to GeokcukYongsinTone(
        lifeType = "재능 사업가형",
        tagline = "재능 + 자원 운영 — 트렌드 잡는 동적 창업가.",
        strengths = listOf("센스·기획력", "변화 적응 빠름", "다재다능"),
        cautions = listOf("한 곳에 정착 어려움", "안정성 약함"),
        careerKeywords = listOf("기획·마케팅", "신사업", "트렌드 산업", "변화 친화 직군")
    ),
    ("偏財格" to "비겁") to GeokcukYongsinTone(
        lifeType = "공격적 동료형",
        tagline = "큰 사업을 동료들과 함께 굴리는 그룹 사업가.",
        strengths = listOf("자원 + 인맥", "확장성", "기회 포착"),
        cautions = listOf("군겁쟁재 위험", "분배 갈등"),
        careerKeywords = listOf("공동 창업", "사모펀드", "M&A", "그룹 사업")
    )
)

/**
 * 격국 + 용신 오행 → 인생 톤.
 * 일간 오행으로 십신 카테고리 변환 후 매트릭스 lookup.
 *
 * @param geokcuk 격국 한자명 (예: "正官格")
 * @param dayElement 일간 오행
 * @param yongsin 용신 오행
 */
fun lookupGeokcukYongsinTone(
    geokcuk: String,
    dayElement: Element,
    yongsin: Element
): GeokcukYongsinTone? {
    val cat = yongsinCategory(dayElement, yongsin) ?: return null
    return MATRIX[geokcuk to cat]
}
