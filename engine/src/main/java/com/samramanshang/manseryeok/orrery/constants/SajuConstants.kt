package com.samramanshang.manseryeok.orrery.constants

import com.samramanshang.manseryeok.orrery.model.*

object SajuConstants {
    val SKY = "甲乙丙丁戊己庚辛壬癸"
    val EARTH = "子丑寅卯辰巳午未申酉戌亥"
    val SKY_KR = "갑을병정무기경신임계"
    val EARTH_KR = "자축인묘진사오미신유술해"

    val YANGGAN = listOf("甲", "丙", "戊", "庚", "壬")

    val RELATIONS = listOf(
        Relation("比肩", "비견"), Relation("劫財", "겁재"),
        Relation("食神", "식신"), Relation("傷官", "상관"),
        Relation("偏財", "편재"), Relation("正財", "정재"),
        Relation("偏官", "편관"), Relation("正官", "정관"),
        Relation("偏印", "편인"), Relation("正印", "정인")
    )

    val METEORS_12 = listOf(
        Meteor("長生", "장생"), Meteor("沐浴", "목욕"),
        Meteor("冠帶", "관대"), Meteor("乾祿", "건록"),
        Meteor("帝旺", "제왕"), Meteor("衰", "쇠"),
        Meteor("病", "병"), Meteor("死", "사"),
        Meteor("墓", "묘"), Meteor("絶", "절"),
        Meteor("胎", "태"), Meteor("養", "양")
    )

    val SPIRITS_12 = listOf(
        Spirit("劫殺", "겁살"), Spirit("災殺", "재살"),
        Spirit("天殺", "천살"), Spirit("地殺", "지살"),
        Spirit("年殺", "연살"), Spirit("月殺", "월살"),
        Spirit("亡身", "망신"), Spirit("將星", "장성"),
        Spirit("攀鞍", "반안"), Spirit("驛馬", "역마"),
        Spirit("六害", "육해"), Spirit("華蓋", "화개")
    )

    val BRANCH_ELEMENT = mapOf(
        "寅" to Element.TREE, "卯" to Element.TREE,
        "巳" to Element.FIRE, "午" to Element.FIRE,
        "辰" to Element.EARTH, "未" to Element.EARTH, "丑" to Element.EARTH, "戌" to Element.EARTH,
        "申" to Element.METAL, "酉" to Element.METAL,
        "子" to Element.WATER, "亥" to Element.WATER
    )

    val STEM_INFO = mapOf(
        "甲" to StemInfo("甲", YinYang.YANG, Element.TREE),
        "乙" to StemInfo("乙", YinYang.YIN, Element.TREE),
        "丙" to StemInfo("丙", YinYang.YANG, Element.FIRE),
        "丁" to StemInfo("丁", YinYang.YIN, Element.FIRE),
        "戊" to StemInfo("戊", YinYang.YANG, Element.EARTH),
        "己" to StemInfo("己", YinYang.YIN, Element.EARTH),
        "庚" to StemInfo("庚", YinYang.YANG, Element.METAL),
        "辛" to StemInfo("辛", YinYang.YIN, Element.METAL),
        "壬" to StemInfo("壬", YinYang.YANG, Element.WATER),
        "癸" to StemInfo("癸", YinYang.YIN, Element.WATER)
    )

    val ELEMENT_HANJA = mapOf(
        Element.TREE to "木", Element.FIRE to "火",
        Element.EARTH to "土", Element.METAL to "金", Element.WATER to "水"
    )

    val ELEMENT_KR = mapOf(
        Element.TREE to "목", Element.FIRE to "화",
        Element.EARTH to "토", Element.METAL to "금", Element.WATER to "수"
    )

    // 삼합 (三合)
    val TRIPLE_COMPOSES = listOf(
        Triple("寅", "午", "戌"),
        Triple("巳", "酉", "丑"),
        Triple("申", "子", "辰"),
        Triple("亥", "卯", "未")
    )

    val TRIPLE_COMPOSE_ELEMENTS = mapOf(
        "寅,午,戌" to "fire", "巳,酉,丑" to "metal",
        "申,子,辰" to "water", "亥,卯,未" to "tree"
    )

    // 반합 (半合)
    val HALF_COMPOSES = mapOf(
        "寅,午" to ("半合" to "fire"), "午,戌" to ("半合" to "fire"),
        "巳,酉" to ("半合" to "metal"), "酉,丑" to ("半合" to "metal"),
        "申,子" to ("半合" to "water"), "子,辰" to ("半合" to "water"),
        "亥,卯" to ("半合" to "tree"), "卯,未" to ("半合" to "tree")
    )

    // 방합 (方合)
    val DIRECTIONAL_COMPOSES = listOf(
        Triple("寅", "卯", "辰"),
        Triple("巳", "午", "未"),
        Triple("申", "酉", "戌"),
        Triple("亥", "子", "丑")
    )

    val DIRECTIONAL_COMPOSE_ELEMENTS = mapOf(
        "寅,卯,辰" to "tree", "巳,午,未" to "fire",
        "申,酉,戌" to "metal", "亥,子,丑" to "water"
    )

    // 천간합
    val STEM_COMBINES = mapOf(
        "甲,己" to ("合" to "earth"), "乙,庚" to ("合" to "metal"),
        "丙,辛" to ("合" to "water"), "丁,壬" to ("合" to "tree"),
        "戊,癸" to ("合" to "fire")
    )

    // 천간충
    val STEM_CLASHES = mapOf(
        "甲,庚" to "沖", "乙,辛" to "沖", "丙,壬" to "沖", "丁,癸" to "沖"
    )

    // 지지육합
    val BRANCH_COMBINES_6 = mapOf(
        "子,丑" to ("合" to "earth"), "寅,亥" to ("合" to "tree"),
        "卯,戌" to ("合" to "fire"), "辰,酉" to ("合" to "metal"),
        "巳,申" to ("合" to "water"), "午,未" to ("合" to "fire")
    )

    // 지지충
    val BRANCH_CLASHES = mapOf(
        "子,午" to "沖", "丑,未" to "沖", "寅,申" to "沖",
        "卯,酉" to "沖", "辰,戌" to "沖", "巳,亥" to "沖"
    )

    // 지지파
    val BRANCH_BREAKS = mapOf(
        "子,酉" to "破", "丑,辰" to "破", "寅,亥" to "破",
        "卯,午" to "破", "巳,申" to "破", "未,戌" to "破"
    )

    // 지지해
    val BRANCH_HARMS = mapOf(
        "子,未" to "害", "丑,午" to "害", "寅,巳" to "害",
        "卯,辰" to "害", "申,亥" to "害", "酉,戌" to "害"
    )

    // 지지형
    val BRANCH_PUNISHMENTS = mapOf(
        "寅,巳" to ("刑" to "無恩"), "巳,申" to ("刑" to "無恩"), "申,寅" to ("刑" to "無恩"),
        "丑,戌" to ("刑" to "持勢"), "戌,未" to ("刑" to "持勢"), "未,丑" to ("刑" to "持勢"),
        "子,卯" to ("刑" to "無禮"), "卯,子" to ("刑" to "無禮")
    )

    // 자형
    val BRANCH_SELF_PUNISHMENTS = setOf("辰", "午", "酉", "亥")

    // 원진
    val BRANCH_WONJIN = mapOf(
        "子,未" to "怨嗔", "丑,午" to "怨嗔", "寅,酉" to "怨嗔",
        "卯,申" to "怨嗔", "辰,亥" to "怨嗔", "巳,戌" to "怨嗔"
    )

    // 귀문관살
    val BRANCH_GWIMUN = mapOf(
        "子,酉" to "鬼門", "丑,午" to "鬼門", "寅,未" to "鬼門",
        "卯,申" to "鬼門", "辰,亥" to "鬼門", "巳,戌" to "鬼門"
    )

    // 양인살
    val YANGIN_MAP = mapOf("甲" to "卯", "丙" to "午", "戊" to "午", "庚" to "酉", "壬" to "子")

    // 백호살 일주
    val BAEKHO_PILLARS = setOf("甲辰", "乙未", "丙戌", "丁丑", "戊辰", "壬戌", "癸丑")

    // 괴강살 일주
    val GOEGANG_PILLARS = setOf("庚辰", "庚戌", "壬辰", "戊戌")

    // 탕화살 (湯火殺): 일지 기준, 특정 지지 만나면
    val TANGHWA_MAP = mapOf("寅" to "巳", "午" to "辰", "丑" to "午")

    // 현침살 (懸針殺): 세로획이 많은 간지 — 침/칼/날카로움
    val HYEONCHIM_STEMS = setOf("甲", "辛")
    val HYEONCHIM_BRANCHES = setOf("卯", "午", "申")

    // 도화살
    val DOHWA_MAP = mapOf(
        "寅" to "卯", "午" to "卯", "戌" to "卯",
        "申" to "酉", "子" to "酉", "辰" to "酉",
        "巳" to "午", "酉" to "午", "丑" to "午",
        "亥" to "子", "卯" to "子", "未" to "子"
    )

    // 천을귀인
    val CHEONUL_MAP = mapOf(
        "甲" to listOf("丑", "未"), "戊" to listOf("丑", "未"), "庚" to listOf("丑", "未"),
        "乙" to listOf("子", "申"), "己" to listOf("子", "申"),
        "丙" to listOf("亥", "酉"), "丁" to listOf("亥", "酉"),
        "辛" to listOf("午", "寅"),
        "壬" to listOf("巳", "卯"), "癸" to listOf("巳", "卯")
    )

    // 천덕귀인
    val CHEONDUK_MAP = mapOf(
        "寅" to "丁", "卯" to "申", "辰" to "壬", "巳" to "辛",
        "午" to "亥", "未" to "甲", "申" to "癸", "酉" to "寅",
        "戌" to "丙", "亥" to "乙", "子" to "巳", "丑" to "庚"
    )

    // 월덕귀인
    val WOLDUK_MAP = mapOf(
        "寅" to "丙", "午" to "丙", "戌" to "丙",
        "申" to "壬", "子" to "壬", "辰" to "壬",
        "巳" to "庚", "酉" to "庚", "丑" to "庚",
        "亥" to "甲", "卯" to "甲", "未" to "甲"
    )

    // 문창귀인
    val MUNCHANG_MAP = mapOf(
        "甲" to "巳", "乙" to "午", "丙" to "申", "丁" to "酉",
        "戊" to "申", "己" to "酉", "庚" to "亥", "辛" to "子",
        "壬" to "寅", "癸" to "卯"
    )

    // 홍염살
    val HONGYEOM_PILLARS = setOf("甲午", "丙寅", "丁未", "戊辰", "庚戌", "辛酉", "壬子")

    // 금여록
    val GEUMYEO_MAP = mapOf(
        "甲" to "辰", "乙" to "巳", "丙" to "未", "丁" to "申",
        "戊" to "未", "己" to "申", "庚" to "戌", "辛" to "亥",
        "壬" to "丑", "癸" to "寅"
    )

    // 학당귀인 (學堂貴人) — 일간의 12운성 장생(長生) 지지
    val HAKDANG_MAP = mapOf(
        "甲" to "亥", "乙" to "午", "丙" to "寅", "丁" to "酉",
        "戊" to "寅", "己" to "酉", "庚" to "巳", "辛" to "子",
        "壬" to "申", "癸" to "卯"
    )

    // 복성귀인 (福星貴人) — 일간 기준 길성(자평진전 표준)
    val BOKSEONG_MAP = mapOf(
        "甲" to listOf("寅"), "乙" to listOf("丑", "亥"),
        "丙" to listOf("子", "申"), "丁" to listOf("酉", "亥"),
        "戊" to listOf("申"), "己" to listOf("未"),
        "庚" to listOf("午"), "辛" to listOf("巳"),
        "壬" to listOf("辰"), "癸" to listOf("卯")
    )

    // 천주귀인 (天廚貴人) — 일간 기준 의식주 풍족 길성
    val CHEONJU_MAP = mapOf(
        "甲" to "巳", "乙" to "午", "丙" to "巳", "丁" to "午",
        "戊" to "申", "己" to "酉", "庚" to "亥", "辛" to "子",
        "壬" to "寅", "癸" to "卯"
    )

    // 암록 (暗錄) — 일간 건록 지지의 6合(육합) 지지
    // 甲건록寅·寅↔亥, 乙건록卯·卯↔戌, 丙·戊건록巳·巳↔申,
    // 丁·己건록午·午↔未, 庚건록申·申↔巳, 辛건록酉·酉↔辰,
    // 壬건록亥·亥↔寅, 癸건록子·子↔丑
    val AMROK_MAP = mapOf(
        "甲" to "亥", "乙" to "戌", "丙" to "申", "丁" to "未",
        "戊" to "申", "己" to "未", "庚" to "巳", "辛" to "辰",
        "壬" to "寅", "癸" to "丑"
    )

    // 월덕합 (月德合) — 월덕귀인 천간의 합(合) 천간
    // 寅午戌월(월덕丙) → 丙合辛 → 辛
    // 申子辰월(월덕壬) → 壬合丁 → 丁
    // 巳酉丑월(월덕庚) → 庚合乙 → 乙
    // 亥卯未월(월덕甲) → 甲合己 → 己
    val WOLDUKHAP_MAP = mapOf(
        "寅" to "辛", "午" to "辛", "戌" to "辛",
        "申" to "丁", "子" to "丁", "辰" to "丁",
        "巳" to "乙", "酉" to "乙", "丑" to "乙",
        "亥" to "己", "卯" to "己", "未" to "己"
    )

    // 12운성 룩업 (한글 기반)
    val METEOR_LOOKUP = mapOf(
        "갑해" to 0, "을오" to 0, "병인" to 0, "정유" to 0, "무인" to 0, "기유" to 0, "경사" to 0, "신자" to 0, "임신" to 0, "계묘" to 0,
        "갑자" to 1, "을사" to 1, "병묘" to 1, "정신" to 1, "무묘" to 1, "기신" to 1, "경오" to 1, "신해" to 1, "임유" to 1, "계인" to 1,
        "갑축" to 2, "을진" to 2, "병진" to 2, "정미" to 2, "무진" to 2, "기미" to 2, "경미" to 2, "신술" to 2, "임술" to 2, "계축" to 2,
        "갑인" to 3, "을묘" to 3, "병사" to 3, "정오" to 3, "무사" to 3, "기오" to 3, "경신" to 3, "신유" to 3, "임해" to 3, "계자" to 3,
        "갑묘" to 4, "을인" to 4, "병오" to 4, "정사" to 4, "무오" to 4, "기사" to 4, "경유" to 4, "신신" to 4, "임자" to 4, "계해" to 4,
        "갑진" to 5, "을축" to 5, "병미" to 5, "정진" to 5, "무미" to 5, "기진" to 5, "경술" to 5, "신미" to 5, "임축" to 5, "계술" to 5,
        "갑사" to 6, "을자" to 6, "병신" to 6, "정묘" to 6, "무신" to 6, "기묘" to 6, "경해" to 6, "신오" to 6, "임인" to 6, "계유" to 6,
        "갑오" to 7, "을해" to 7, "병유" to 7, "정인" to 7, "무유" to 7, "기인" to 7, "경자" to 7, "신사" to 7, "임묘" to 7, "계신" to 7,
        "갑미" to 8, "을술" to 8, "병술" to 8, "정축" to 8, "무술" to 8, "기축" to 8, "경축" to 8, "신진" to 8, "임진" to 8, "계미" to 8,
        "갑신" to 9, "을유" to 9, "병해" to 9, "정자" to 9, "무해" to 9, "기자" to 9, "경인" to 9, "신묘" to 9, "임사" to 9, "계오" to 9,
        "갑유" to 10, "을신" to 10, "병자" to 10, "정해" to 10, "무자" to 10, "기해" to 10, "경묘" to 10, "신인" to 10, "임오" to 10, "계사" to 10,
        "갑술" to 11, "을미" to 11, "병축" to 11, "정술" to 11, "무축" to 11, "기술" to 11, "경진" to 11, "신축" to 11, "임미" to 11, "계진" to 11
    )

    // 지장간
    val JIJANGGAN = mapOf(
        "寅" to "戊丙甲", "卯" to "甲 乙", "辰" to "乙癸戊",
        "巳" to "戊庚丙", "午" to "丙己丁", "未" to "丁乙己",
        "申" to "戊壬庚", "酉" to "庚 辛", "戌" to "辛丁戊",
        "亥" to "戊甲壬", "子" to "壬 癸", "丑" to "癸辛己"
    )

    // 공망 테이블
    val GONGMANG_TABLE = listOf(
        "戌" to "亥",  // 甲子旬
        "申" to "酉",  // 甲戌旬
        "午" to "未",  // 甲申旬
        "辰" to "巳",  // 甲午旬
        "寅" to "卯",  // 甲辰旬
        "子" to "丑"   // 甲寅旬
    )

    // 60갑자
    val HGANJI = listOf(
        "甲子", "乙丑", "丙寅", "丁卯", "戊辰", "己巳", "庚午", "辛未", "壬申", "癸酉", "甲戌", "乙亥",
        "丙子", "丁丑", "戊寅", "己卯", "庚辰", "辛巳", "壬午", "癸未", "甲申", "乙酉", "丙戌", "丁亥",
        "戊子", "己丑", "庚寅", "辛卯", "壬辰", "癸巳", "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥",
        "庚子", "辛丑", "壬寅", "癸卯", "甲辰", "乙巳", "丙午", "丁未", "戊申", "己酉", "庚戌", "辛亥",
        "壬子", "癸丑", "甲寅", "乙卯", "丙辰", "丁巳", "戊午", "己未", "庚申", "辛酉", "壬戌", "癸亥"
    )

    val PILLAR_NAMES = listOf("時柱", "日柱", "月柱", "年柱")

    /**
     * 지지암합(地支暗合) — 지장간끼리 干合을 이루는 표준 4대 페어 (WS-4 additive).
     * 근거: 협기변방서·삼명통회 계통에서 공통적으로 인용되는 정통 4조. 각 페어는 JIJANGGAN 상수의
     * 실제 지장간 구성으로 干合(STEM_COMBINES 5조 중 하나)이 성립함을 개별 확인했다:
     *   子(지장간 "壬 癸"→癸)-戌(지장간 "辛丁戊"→戊) = 戊癸合(化火)
     *   丑(지장간 "癸辛己"→己)-寅(지장간 "戊丙甲"→甲) = 甲己合(化土)
     *   卯(지장간 "甲 乙"→乙)-申(지장간 "戊壬庚"→庚) = 乙庚合(化金)
     *   午(지장간 "丙己丁"→丁)-亥(지장간 "戊甲壬"→壬) = 丁壬合(化木)
     * BRANCH_COMBINES_6(육합)·TRIPLE_COMPOSES(삼합)·DIRECTIONAL_COMPOSES(방합)와는 별개의 독립 관계.
     * 전수 순열 매칭(모든 지장간 조합에서 干合 가능성 있는 쌍 전부)은 하지 않음 — 고전 문헌에 명시적으로
     * 등재된 이 4조만 "확실한 페어"로 채택(예: 丙辛合을 이루는 巳-戌 등은 고전 표에 통상 등재되지 않아 제외).
     * key = "지지1,지지2"(값은 정기/干合명, 화기오행) — AmhapDetector.detectAmhap 에서 양방향 조회.
     */
    val BRANCH_HIDDEN_COMBINES: Map<String, Pair<String, String>> = mapOf(
        "子,戌" to ("戊癸合" to "fire"),
        "丑,寅" to ("甲己合" to "earth"),
        "卯,申" to ("乙庚合" to "metal"),
        "午,亥" to ("丁壬合" to "tree")
    )
}
