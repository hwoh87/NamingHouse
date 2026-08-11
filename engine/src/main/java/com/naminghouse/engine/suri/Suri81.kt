package com.naminghouse.engine.suri

/** 수리 길흉 등급 */
enum class SuriGrade(val label: String, val isGood: Boolean) {
    DAEGIL("대길", true),
    GIL("길", true),
    PYEONG("반길반흉", false),
    HYUNG("흉", false),
    DAEHYUNG("대흉", false),
}

data class SuriMeaning(
    val number: Int,
    val title: String,
    val hanjaTitle: String,
    val grade: SuriGrade,
)

/**
 * 81수리 길흉표.
 *
 * 길수 40개·흉수 40개·77(반길반흉) 구분은 인터넷한국작명연구원 공식 표 기준.
 * 격 명칭은 유파별 이칭이 있어 표시용이며, 등급 판정은 길/흉 이분을 기준으로 한다.
 * 대길/대흉 세분은 통용 해설(1·3·5…81 최길수, 34·44 최흉수)을 따랐다.
 */
object Suri81 {

    private val D = SuriGrade.DAEGIL
    private val G = SuriGrade.GIL
    private val P = SuriGrade.PYEONG
    private val H = SuriGrade.HYUNG
    private val X = SuriGrade.DAEHYUNG

    private val table: List<SuriMeaning> = listOf(
        SuriMeaning(1, "태초격", "太初格", D),
        SuriMeaning(2, "분리격", "分離格", H),
        SuriMeaning(3, "명예격", "名譽格", D),
        SuriMeaning(4, "부정격", "否定格", H),
        SuriMeaning(5, "복덕격", "福德格", D),
        SuriMeaning(6, "계승격", "繼承格", G),
        SuriMeaning(7, "독립격", "獨立格", G),
        SuriMeaning(8, "발달격", "發達格", G),
        SuriMeaning(9, "궁박격", "窮迫格", H),
        SuriMeaning(10, "공허격", "空虛格", H),
        SuriMeaning(11, "신성격", "新成格", D),
        SuriMeaning(12, "박약격", "薄弱格", H),
        SuriMeaning(13, "지모격", "智謀格", D),
        SuriMeaning(14, "이산격", "離散格", H),
        SuriMeaning(15, "통솔격", "統率格", D),
        SuriMeaning(16, "덕망격", "德望格", D),
        SuriMeaning(17, "건창격", "健暢格", G),
        SuriMeaning(18, "발전격", "發展格", G),
        SuriMeaning(19, "고난격", "苦難格", H),
        SuriMeaning(20, "허망격", "虛望格", H),
        SuriMeaning(21, "두령격", "頭領格", D),
        SuriMeaning(22, "중절격", "中折格", H),
        SuriMeaning(23, "공명격", "功名格", D),
        SuriMeaning(24, "입신격", "立身格", D),
        SuriMeaning(25, "안전격", "安全格", G),
        SuriMeaning(26, "영웅시비격", "英雄是非格", H),
        SuriMeaning(27, "중단격", "中斷格", H),
        SuriMeaning(28, "파란격", "波瀾格", H),
        SuriMeaning(29, "성공격", "成功格", G),
        SuriMeaning(30, "부몽격", "浮夢格", H),
        SuriMeaning(31, "융창격", "隆昌格", D),
        SuriMeaning(32, "순풍격", "順風格", D),
        SuriMeaning(33, "승천격", "昇天格", D),
        SuriMeaning(34, "파멸격", "破滅格", X),
        SuriMeaning(35, "태평격", "泰平格", G),
        SuriMeaning(36, "영웅격", "英雄格", H),
        SuriMeaning(37, "인덕격", "仁德格", D),
        SuriMeaning(38, "문예격", "文藝格", G),
        SuriMeaning(39, "안락격", "安樂格", D),
        SuriMeaning(40, "무상격", "無常格", H),
        SuriMeaning(41, "대공격", "大功格", D),
        SuriMeaning(42, "고행격", "苦行格", H),
        SuriMeaning(43, "미혹격", "迷惑格", H),
        SuriMeaning(44, "마장격", "魔障格", X),
        SuriMeaning(45, "대지격", "大智格", D),
        SuriMeaning(46, "미운격", "未運格", H),
        SuriMeaning(47, "출세격", "出世格", D),
        SuriMeaning(48, "유덕격", "有德格", D),
        SuriMeaning(49, "은퇴격", "隱退格", H),
        SuriMeaning(50, "상반격", "相半格", H),
        SuriMeaning(51, "춘추격", "春秋格", G),
        SuriMeaning(52, "승룡격", "昇龍格", D),
        SuriMeaning(53, "내허격", "內虛格", H),
        SuriMeaning(54, "무공격", "無功格", H),
        SuriMeaning(55, "미달격", "未達格", H),
        SuriMeaning(56, "부족격", "不足格", H),
        SuriMeaning(57, "노력격", "努力格", G),
        SuriMeaning(58, "후복격", "後福格", G),
        SuriMeaning(59, "재화격", "災禍格", H),
        SuriMeaning(60, "동요격", "動搖格", H),
        SuriMeaning(61, "재리격", "財利格", G),
        SuriMeaning(62, "쇠퇴격", "衰退格", H),
        SuriMeaning(63, "순성격", "順成格", G),
        SuriMeaning(64, "침체격", "沈滯格", H),
        SuriMeaning(65, "흥가격", "興家格", G),
        SuriMeaning(66, "쇠망격", "衰亡格", H),
        SuriMeaning(67, "천복격", "天福格", G),
        SuriMeaning(68, "명지격", "明智格", G),
        SuriMeaning(69, "종말격", "終末格", H),
        SuriMeaning(70, "적막격", "寂寞格", H),
        SuriMeaning(71, "만달격", "晩達格", H),
        SuriMeaning(72, "후곤격", "後困格", H),
        SuriMeaning(73, "평길격", "平吉格", G),
        SuriMeaning(74, "우매격", "愚昧格", H),
        SuriMeaning(75, "평화격", "平和格", G),
        SuriMeaning(76, "선곤격", "先困格", H),
        SuriMeaning(77, "전후격", "前後格", P),
        SuriMeaning(78, "선길후흉격", "先吉後凶格", H),
        SuriMeaning(79, "종극격", "終極格", H),
        SuriMeaning(80, "종결격", "終結格", H),
        SuriMeaning(81, "환원격", "還元格", D),
    )

    /** 81 초과 수는 81을 뺀 나머지로 환원해 조회한다(성명학 관행). */
    fun of(number: Int): SuriMeaning {
        require(number >= 1) { "수리는 1 이상이어야 함: $number" }
        var n = number
        while (n > 81) n -= 81
        return table[n - 1]
    }

    val all: List<SuriMeaning> get() = table
}
