package com.samramanshang.manseryeok.orrery.model


// perf: PlanetId.displayName 게터가 행성마다 재컴파일하던 정규식을 파일 1회 생성으로 호이스팅(동작 불변).
private val CAMEL_BOUNDARY_RE = Regex("([a-z])([A-Z])")

/** 오행 (Five Elements) */
enum class Element(val hanja: String) {
    TREE("木"), FIRE("火"), EARTH("土"), METAL("金"), WATER("水")
}

/** 음양 (Yin-Yang) */
enum class YinYang(val symbol: String) {
    YANG("+"), YIN("-")
}

/** 성별 */
enum class Gender { M, F }

/** 자시법 (子時法) — 23:00~01:00 구간 일주 처리 방식 */
enum class JasiMethod {
    /** 야자시 인정: 23:00~24:00 일주 당일 유지 */
    SPLIT,
    /** 통자시: 23:00부터 일주 다음날로 넘김 */
    UNIFIED
}

/** 생년월일시 입력 */
data class BirthInput(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val gender: Gender,
    val unknownTime: Boolean = false,
    val jasiMethod: JasiMethod = JasiMethod.UNIFIED,
    val latitude: Double = 37.5194,
    val longitude: Double = 127.0992,
    val timezone: String? = null,
    /** 입력 날짜가 음력인지 여부 (true 시 SajuRepository 가 양력으로 변환 후 계산) */
    val isLunar: Boolean = false,
    /** 음력 윤달 여부 (isLunar=true 일 때만 의미) */
    val isLeapMonth: Boolean = false,
    /** 진태양시(경도×4분 + EoT) 보정 적용 여부. 한국 출생도 강제로 solar path 사용. */
    val useTrueSolarTime: Boolean = false
)

/** 천간 정보 */
data class StemInfo(
    val name: String,
    val yinyang: YinYang,
    val element: Element
)

/** 십신 관계 */
data class Relation(
    val hanja: String,
    val hangul: String
)

/** 12운성 */
data class Meteor(
    val hanja: String,
    val hangul: String
)

/** 12신살 */
data class Spirit(
    val hanja: String,
    val hangul: String
)

/** 사주 하나의 주 (柱) */
data class Pillar(
    val ganzi: String,
    val stem: String,
    val branch: String
)

/** 사주 결과에서의 하나의 주 상세 */
data class PillarDetail(
    val pillar: Pillar,
    val stemSipsin: String,
    val branchSipsin: String,
    val unseong: String,
    val sinsal: String,
    val jigang: String
)

/**
 * 순수 벽시계 시각 (P1a: JVM Date 대체 — KMP/JS 대응).
 * 대운 시작일 등 "달력상 시점" 표현 전용. 타임존 의미 없음(엔진 내부 규약 시각).
 */
data class WallDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 0,
    val minute: Int = 0
) : Comparable<WallDateTime> {
    override fun compareTo(other: WallDateTime): Int = compareValuesBy(
        this, other, { it.year }, { it.month }, { it.day }, { it.hour }, { it.minute }
    )
}

/** 대운 항목 */
data class DaewoonItem(
    val index: Int,
    val ganzi: String,
    val startDate: WallDateTime,
    val age: Int,
    val stemSipsin: String,
    val branchSipsin: String,
    val unseong: String,
    val sinsal: String,
    val isGongmang: Boolean
)

/** 세운(年運) 항목 */
data class SaewoonItem(
    val year: Int,
    val age: Int,
    val ganzi: String,
    val stemSipsin: String,
    val branchSipsin: String,
    val unseong: String,
    val sinsal: String,
    val isGongmang: Boolean
)

/** 관계 분석 결과 */
data class RelationResult(
    val type: String,
    val detail: String?
)

/** 주 쌍 관계 */
data class PairRelation(
    val stem: List<RelationResult>,
    val branch: List<RelationResult>
)

/** 전체 팔자 관계 분석 */
data class AllRelations(
    val pairs: Map<String, PairRelation>,
    val triple: List<RelationResult>,
    val directional: List<RelationResult>
)

/** 신살 정보 */
data class SpecialSals(
    val yangin: List<Int>,
    val baekho: Boolean,
    val goegang: Boolean,
    val dohwa: List<Int>,
    val cheonul: List<Int>,
    val cheonduk: List<Int>,
    val wolduk: List<Int>,
    val munchang: List<Int>,
    val hongyeom: Boolean,
    val geumyeo: List<Int>,
    val hwagae: List<Int>     = emptyList(),
    val tanghwa: List<Int>    = emptyList(),
    val hyeonchim: List<Int>  = emptyList(),
    /** 학당귀인 — 학습·학문 능력 (일간 장생) */
    val hakdang: List<Int>    = emptyList(),
    /** 복성귀인 — 복록·행운 (일간 길성) */
    val bokseong: List<Int>   = emptyList(),
    /** 천주귀인 — 의식주 풍족 (일간 길성) */
    val cheonju: List<Int>    = emptyList(),
    /** 암록 — 숨은 자원·인연 (건록의 6합) */
    val amrok: List<Int>      = emptyList(),
    /** 월덕합 — 월덕귀인의 합 천간 */
    val woldukhap: List<Int>  = emptyList(),
    /** 장성 — 12신살의 將星(권력·리더십) */
    val jangseong: List<Int>  = emptyList(),
    /** 역마 — 12신살의 驛馬(이동·변화·해외) */
    val yeokma: List<Int>     = emptyList(),
    /**
     * 진도화(眞桃花) 여부 — 일지가 속한 삼합국의 다른 글자가 사주에 한 자 이상 함께
     * 등장해 도화국이 짜인 강한 도화. 단독 도화 글자만 있으면 가도화(假桃花)로 본다.
     */
    val isTrueDohwa: Boolean  = false,
    /**
     * 곤랑도화(滾浪桃花) — 천간이 합(干合)을 이루면서 지지가 子卯刑을 짜는 구조에 참여한
     * 기둥 인덱스. 일반 도화살보다 작용력이 훨씬 강함. 상세 풀이는 [detectGollangDohwa] 호출.
     */
    val gollangDohwa: List<Int> = emptyList()
)

/** 공망 정보 */
data class Gongmang(
    val branches: Pair<String, String>,
    val pillarIndices: List<Int>
)

/** 삼재 한 해 */
data class SamjaeYear(
    val year: Int,        // 양력 연도
    val ganji: String,    // 그 해 60갑자
    val phase: String     // "들음" / "눌음" / "날음"
)

/** 삼재 정보 — 생년 지지 기준 다음 도래 3년 */
data class Samjae(
    val branches: List<String>,   // 삼재 지지 3자 (예: ["巳","午","未"])
    val years: List<SamjaeYear>   // 가까운 삼재 사이클 3년
)

/** 좌법 항목 */
data class JwaEntry(
    val stem: String,
    val sipsin: String,
    val unseong: String
)

/** 인종법 항목 */
data class InjongEntry(
    val category: String,
    val yangStem: String,
    val unseong: String
)

/** 트랜짓 항목 */
data class TransitItem(
    val date: WallDateTime,
    val type: String,
    val transit: String,
    val natalName: String,
    val relations: List<TransitRelation>
)

data class TransitRelation(
    val prefix: String,
    val relation: RelationResult
)

/** 사주 계산 전체 결과 */
data class SajuResult(
    val input: BirthInput,
    val pillars: List<PillarDetail>,
    val daewoon: List<DaewoonItem>,
    val relations: AllRelations,
    val specialSals: SpecialSals,
    val gongmang: Gongmang,
    val jwabeop: List<List<JwaEntry>>,
    val injongbeop: List<InjongEntry>,
    val saewoon: List<SaewoonItem> = emptyList(),
    val samjae: Samjae = Samjae(emptyList(), emptyList()),
    // WS-4 정밀도 심화 (additive): 태원(胎元)·명궁(命宮) 간지. nullable default라 기존 생성 호출부(전부
    // named-arg) 무수정 컴파일. 명궁은 시주 미상(unknownTime)이면 계산 불가 → null.
    val taewon: String? = null,
    val myeonggung: String? = null
)

// =============================================
// 자미두수 타입
// =============================================

/** 오행국 */
data class WuXingJu(
    val name: String,
    val number: Int
)

/** 성요 정보 */
data class ZiweiStar(
    val name: String,
    val brightness: String,
    val siHua: String
)

/** 궁위 정보 */
data class ZiweiPalace(
    val name: String,
    val zhi: String,
    val gan: String,
    val ganZhi: String,
    val stars: List<ZiweiStar>,
    val isShenGong: Boolean
)

/** 자미두수 명반 */
data class ZiweiChart(
    val solarYear: Int,
    val solarMonth: Int,
    val solarDay: Int,
    val hour: Int,
    val minute: Int,
    val isMale: Boolean,
    val lunarYear: Int,
    val lunarMonth: Int,
    val lunarDay: Int,
    val isLeapMonth: Boolean,
    val yearGan: String,
    val yearZhi: String,
    val mingGongZhi: String,
    val shenGongZhi: String,
    val wuXingJu: WuXingJu,
    val palaces: Map<String, ZiweiPalace>,
    val daXianStartAge: Int
)

/** 유월 정보 */
data class LiuYueInfo(
    val month: Int,
    val mingGongZhi: String,
    val natalPalaceName: String
)

/** 유일 정보 */
data class LiuRiInfo(
    val year: Int,
    val month: Int,
    val day: Int,
    val lunarMonth: Int,
    val lunarDay: Int,
    val gan: String,
    val zhi: String,
    val mingGongZhi: String,
    val natalPalaceName: String,
    val siHua: Map<String, String>,
    val siHuaPalaces: Map<String, String>
)

/** 유년 정보 */
data class LiuNianInfo(
    val year: Int,
    val gan: String,
    val zhi: String,
    val mingGongZhi: String,
    val natalPalaceAtMing: String,
    val siHua: Map<String, String>,
    val siHuaPalaces: Map<String, String>,
    val palaces: Map<String, String>,
    val liuyue: List<LiuYueInfo>,
    val daxianPalaceName: String,
    val daxianAgeStart: Int,
    val daxianAgeEnd: Int
)

/** 오늘 자미 흐름: 유년 + 유월 + 유일을 한 번에 묶은 계산 결과 */
data class ZiweiDailyFlowInfo(
    val dateLabel: String,
    val liunian: LiuNianInfo,
    val liuyue: LiuYueInfo,
    val liuri: LiuRiInfo,
    val opportunityPalace: String,
    val cautionPalace: String,
    val prescription: String
)

/** 대한 정보 */
data class DaxianInfo(
    val ageStart: Int,
    val ageEnd: Int,
    val palaceName: String,
    val ganZhi: String,
    val mainStars: List<String>
)

// =============================================
// 서양 점성술 (Natal Chart) 타입
// =============================================

/** 12궁 별자리 */
enum class ZodiacSign(val ko: String, val symbol: String) {
    Aries("양자리", "♈"),
    Taurus("황소자리", "♉"),
    Gemini("쌍둥이자리", "♊"),
    Cancer("게자리", "♋"),
    Leo("사자자리", "♌"),
    Virgo("처녀자리", "♍"),
    Libra("천칭자리", "♎"),
    Scorpio("전갈자리", "♏"),
    Sagittarius("궁수자리", "♐"),
    Capricorn("염소자리", "♑"),
    Aquarius("물병자리", "♒"),
    Pisces("물고기자리", "♓")
}

/** 행성 ID */
enum class PlanetId(val ko: String, val symbol: String) {
    Sun("태양", "☉"),
    Moon("달", "☽"),
    Mercury("수성", "☿"),
    Venus("금성", "♀"),
    Mars("화성", "♂"),
    Jupiter("목성", "♃"),
    Saturn("토성", "♄"),
    Uranus("천왕성", "♅"),
    Neptune("해왕성", "♆"),
    Pluto("명왕성", "♇"),
    Chiron("키론", "⚷"),
    NorthNode("북교점", "☊"),
    SouthNode("남교점", "☋"),
    Fortuna("행운점", "⊕");

    /** 영문 표시명 (camelCase → 공백 분리). 예: NorthNode → "North Node" */
    val displayName: String
        get() = name.replace(CAMEL_BOUNDARY_RE, "$1 $2")
}

/** 행성 위치 */
data class PlanetPosition(
    val id: PlanetId,
    val longitude: Double,
    val latitude: Double,
    val speed: Double,
    val sign: ZodiacSign,
    val degreeInSign: Double,
    val isRetrograde: Boolean,
    val house: Int? = null
)

/** 하우스 cusp */
data class NatalHouse(
    val number: Int,
    val cuspLongitude: Double,
    val sign: ZodiacSign,
    val degreeInSign: Double
)

/** 앵글 포인트 */
data class AnglePoint(
    val longitude: Double,
    val sign: ZodiacSign,
    val degreeInSign: Double
)

/** 앵글 (ASC, MC, DESC, IC) */
data class NatalAngles(
    val asc: AnglePoint,
    val mc: AnglePoint,
    val desc: AnglePoint,
    val ic: AnglePoint
)

/** 애스펙트 종류 */
enum class AspectType(
    val angle: Double,
    val symbol: String,
    /** 사용자 노출 한글 명칭 (단일 출처 — SajuAiRepository 등 중복 private 매퍼는 이 값을 사용) */
    val koLabel: String,
    /** 조화(+)/강조(0)/긴장(-) 구분 — 캘린더 트랜짓 인라인 카드용 */
    val koTone: String
) {
    CONJUNCTION(0.0, "☌", "합", "강조"),
    SEXTILE(60.0, "⚹", "육각", "조화"),
    SQUARE(90.0, "□", "사각", "긴장"),
    TRINE(120.0, "△", "삼각", "조화"),
    OPPOSITION(180.0, "☍", "충", "긴장")
}

/** 애스펙트 */
data class NatalAspect(
    val planet1: PlanetId,
    val planet2: PlanetId,
    val type: AspectType,
    val angle: Double,
    val orb: Double
)

/** 네이탈 차트 전체 결과 */
data class NatalChart(
    val input: BirthInput,
    val planets: List<PlanetPosition>,
    val houses: List<NatalHouse>,
    val angles: NatalAngles?,
    val aspects: List<NatalAspect>,
    val houseSystem: String = "P"
)

// =============================================
// 도시 정보
// =============================================

/** 도시 정보 */
data class City(
    val name: String,
    val country: String? = null,
    val region: String? = null,
    val lat: Double,
    val lon: Double
)
