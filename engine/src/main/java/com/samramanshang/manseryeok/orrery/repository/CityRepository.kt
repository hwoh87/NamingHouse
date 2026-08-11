package com.samramanshang.manseryeok.orrery.repository

import com.samramanshang.manseryeok.orrery.model.City

/**
 * 도시 검색 Repository — cities.ts 포팅
 */
class CityRepository {

    companion object {
        val KOREAN_CITIES = listOf(
            // 특별/광역시
            City("서울", region = "서울특별시", lat = 37.5665, lon = 126.9780),
            City("부산", region = "부산광역시", lat = 35.1796, lon = 129.0756),
            City("인천", region = "인천광역시", lat = 37.4563, lon = 126.7052),
            City("대구", region = "대구광역시", lat = 35.8714, lon = 128.6014),
            City("대전", region = "대전광역시", lat = 36.3504, lon = 127.3845),
            City("광주", region = "광주광역시", lat = 35.1595, lon = 126.8526),
            City("울산", region = "울산광역시", lat = 35.5384, lon = 129.3114),
            City("세종", region = "세종특별자치시", lat = 36.4800, lon = 127.2590),
            // 경기도
            City("수원", region = "경기도", lat = 37.2636, lon = 127.0286),
            City("고양", region = "경기도", lat = 37.6584, lon = 126.8320),
            City("용인", region = "경기도", lat = 37.2411, lon = 127.1776),
            City("성남", region = "경기도", lat = 37.4386, lon = 127.1378),
            City("부천", region = "경기도", lat = 37.5034, lon = 126.7660),
            City("안산", region = "경기도", lat = 37.3219, lon = 126.8309),
            City("안양", region = "경기도", lat = 37.3943, lon = 126.9568),
            City("남양주", region = "경기도", lat = 37.6360, lon = 127.2165),
            City("화성", region = "경기도", lat = 37.1996, lon = 126.8312),
            City("평택", region = "경기도", lat = 36.9921, lon = 127.0857),
            City("의정부", region = "경기도", lat = 37.7381, lon = 127.0338),
            City("파주", region = "경기도", lat = 37.7590, lon = 126.7802),
            City("김포", region = "경기도", lat = 37.6153, lon = 126.7156),
            City("광주", region = "경기도", lat = 37.4095, lon = 127.2550),
            // 강원도
            City("춘천", region = "강원도", lat = 37.8813, lon = 127.7299),
            City("원주", region = "강원도", lat = 37.3422, lon = 127.9202),
            City("강릉", region = "강원도", lat = 37.7519, lon = 128.8761),
            City("속초", region = "강원도", lat = 38.2070, lon = 128.5918),
            // 충청도
            City("청주", region = "충청북도", lat = 36.6424, lon = 127.4890),
            City("천안", region = "충청남도", lat = 36.8152, lon = 127.1139),
            City("충주", region = "충청북도", lat = 36.9910, lon = 127.9259),
            City("제천", region = "충청북도", lat = 37.1326, lon = 128.1910),
            City("아산", region = "충청남도", lat = 36.7898, lon = 127.0018),
            // 전라도
            City("전주", region = "전라북도", lat = 35.8242, lon = 127.1480),
            City("목포", region = "전라남도", lat = 34.8118, lon = 126.3922),
            City("여수", region = "전라남도", lat = 34.7604, lon = 127.6622),
            City("순천", region = "전라남도", lat = 34.9505, lon = 127.4873),
            City("군산", region = "전라북도", lat = 35.9677, lon = 126.7370),
            City("익산", region = "전라북도", lat = 35.9483, lon = 126.9576),
            // 경상도
            City("포항", region = "경상북도", lat = 36.0190, lon = 129.3435),
            City("경주", region = "경상북도", lat = 35.8562, lon = 129.2247),
            City("김해", region = "경상남도", lat = 35.2285, lon = 128.8894),
            City("창원", region = "경상남도", lat = 35.2281, lon = 128.6811),
            City("진주", region = "경상남도", lat = 35.1798, lon = 128.1076),
            City("구미", region = "경상북도", lat = 36.1197, lon = 128.3446),
            City("안동", region = "경상북도", lat = 36.5684, lon = 128.7294),
            // 제주도
            City("제주", region = "제주특별자치도", lat = 33.4996, lon = 126.5312),
            City("서귀포", region = "제주특별자치도", lat = 33.2542, lon = 126.5600)
        )

        val WORLD_CITIES = listOf(
            // 동아시아
            City("도쿄", country = "일본", lat = 35.6762, lon = 139.6503),
            City("오사카", country = "일본", lat = 34.6937, lon = 135.5023),
            City("교토", country = "일본", lat = 35.0116, lon = 135.7681),
            City("후쿠오카", country = "일본", lat = 33.5904, lon = 130.4017),
            City("삿포로", country = "일본", lat = 43.0618, lon = 141.3545),
            City("나고야", country = "일본", lat = 35.1815, lon = 136.9066),
            City("베이징", country = "중국", lat = 39.9042, lon = 116.4074),
            City("상하이", country = "중국", lat = 31.2304, lon = 121.4737),
            City("광저우", country = "중국", lat = 23.1291, lon = 113.2644),
            City("선전", country = "중국", lat = 22.5431, lon = 114.0579),
            City("홍콩", country = "중국", lat = 22.3193, lon = 114.1694),
            City("타이베이", country = "대만", lat = 25.0330, lon = 121.5654),
            City("울란바토르", country = "몽골", lat = 47.8864, lon = 106.9057),
            // 동남아시아
            City("방콕", country = "태국", lat = 13.7563, lon = 100.5018),
            City("하노이", country = "베트남", lat = 21.0278, lon = 105.8342),
            City("호치민", country = "베트남", lat = 10.8231, lon = 106.6297),
            City("싱가포르", country = "싱가포르", lat = 1.3521, lon = 103.8198),
            City("자카르타", country = "인도네시아", lat = -6.2088, lon = 106.8456),
            City("쿠알라룸푸르", country = "말레이시아", lat = 3.1390, lon = 101.6869),
            City("마닐라", country = "필리핀", lat = 14.5995, lon = 120.9842),
            // 남아시아
            City("뉴델리", country = "인도", lat = 28.6139, lon = 77.2090),
            City("뭄바이", country = "인도", lat = 19.0760, lon = 72.8777),
            // 서아시아
            City("두바이", country = "아랍에미리트", lat = 25.2048, lon = 55.2708),
            City("이스탄불", country = "튀르키예", lat = 41.0082, lon = 28.9784),
            City("텔아비브", country = "이스라엘", lat = 32.0853, lon = 34.7818),
            // 유럽
            City("런던", country = "영국", lat = 51.5074, lon = -0.1278),
            City("파리", country = "프랑스", lat = 48.8566, lon = 2.3522),
            City("베를린", country = "독일", lat = 52.5200, lon = 13.4050),
            City("뮌헨", country = "독일", lat = 48.1351, lon = 11.5820),
            City("암스테르담", country = "네덜란드", lat = 52.3676, lon = 4.9041),
            City("로마", country = "이탈리아", lat = 41.9028, lon = 12.4964),
            City("마드리드", country = "스페인", lat = 40.4168, lon = -3.7038),
            City("바르셀로나", country = "스페인", lat = 41.3874, lon = 2.1686),
            City("빈", country = "오스트리아", lat = 48.2082, lon = 16.3738),
            City("프라하", country = "체코", lat = 50.0755, lon = 14.4378),
            City("모스크바", country = "러시아", lat = 55.7558, lon = 37.6173),
            City("헬싱키", country = "핀란드", lat = 60.1699, lon = 24.9384),
            City("스톡홀름", country = "스웨덴", lat = 59.3293, lon = 18.0686),
            // 북아메리카
            City("뉴욕", country = "미국", lat = 40.7128, lon = -74.0060),
            City("로스앤젤레스", country = "미국", lat = 34.0522, lon = -118.2437),
            City("시카고", country = "미국", lat = 41.8781, lon = -87.6298),
            City("샌프란시스코", country = "미국", lat = 37.7749, lon = -122.4194),
            City("워싱턴 D.C.", country = "미국", lat = 38.9072, lon = -77.0369),
            City("시애틀", country = "미국", lat = 47.6062, lon = -122.3321),
            City("호놀룰루", country = "미국", lat = 21.3069, lon = -157.8583),
            City("토론토", country = "캐나다", lat = 43.6532, lon = -79.3832),
            City("밴쿠버", country = "캐나다", lat = 49.2827, lon = -123.1207),
            City("멕시코시티", country = "멕시코", lat = 19.4326, lon = -99.1332),
            // 남아메리카
            City("상파울루", country = "브라질", lat = -23.5505, lon = -46.6333),
            City("부에노스아이레스", country = "아르헨티나", lat = -34.6037, lon = -58.3816),
            City("산티아고", country = "칠레", lat = -33.4489, lon = -70.6693),
            // 아프리카
            City("카이로", country = "이집트", lat = 30.0444, lon = 31.2357),
            City("케이프타운", country = "남아프리카공화국", lat = -33.9249, lon = 18.4241),
            City("나이로비", country = "케냐", lat = -1.2921, lon = 36.8219),
            // 오세아니아
            City("시드니", country = "호주", lat = -33.8688, lon = 151.2093),
            City("멜버른", country = "호주", lat = -37.8136, lon = 144.9631),
            City("오클랜드", country = "뉴질랜드", lat = -36.8485, lon = 174.7633)
        )

        val SEOUL = KOREAN_CITIES[0]

        private val AMBIGUOUS_NAMES: Set<String> by lazy {
            KOREAN_CITIES.map { it.name }
                .filter { name -> KOREAN_CITIES.count { it.name == name } > 1 }
                .toSet()
        }
    }

    // =============================================
    // 한국어 초성 검색
    // =============================================

    private val JAMO_TO_INITIAL = mapOf(
        0x3131 to 0, 0x3132 to 1, 0x3134 to 2, 0x3137 to 3, 0x3138 to 4,
        0x3139 to 5, 0x3141 to 6, 0x3142 to 7, 0x3143 to 8, 0x3145 to 9,
        0x3146 to 10, 0x3147 to 11, 0x3148 to 12, 0x3149 to 13, 0x314A to 14,
        0x314B to 15, 0x314C to 16, 0x314D to 17, 0x314E to 18
    )

    private fun getInitialIndex(char: Char): Int? {
        val code = char.code
        if (code in 0xAC00..0xD7A3) {
            return (code - 0xAC00) / (21 * 28)
        }
        return JAMO_TO_INITIAL[code]
    }

    private fun isJamo(char: Char): Boolean = char.code in JAMO_TO_INITIAL

    private fun isAllChosung(query: String): Boolean =
        query.isNotEmpty() && query.all { isJamo(it) }

    private fun matchChosung(name: String, query: String): Boolean {
        if (query.length > name.length) return false
        for (i in query.indices) {
            val qIdx = JAMO_TO_INITIAL[query[i].code] ?: return false
            val nIdx = getInitialIndex(name[i]) ?: return false
            if (qIdx != nIdx) return false
        }
        return true
    }

    fun formatCityName(city: City): String {
        if (city.country != null) return "${city.name}, ${city.country}"
        if (city.region != null && city.name in AMBIGUOUS_NAMES) return "${city.name} (${city.region})"
        return city.name
    }

    /** 쿼리로 도시 필터링 (최대 20개, 한국 도시 우선) */
    fun filterCities(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val chosung = isAllChosung(q)
        val koreanResults = mutableListOf<City>()
        val worldResults = mutableListOf<City>()

        for (city in KOREAN_CITIES) {
            if (chosung) {
                if (matchChosung(city.name, q) || (city.region != null && matchChosung(city.region, q))) {
                    koreanResults.add(city)
                }
            } else {
                if (city.name.contains(q) || (city.region != null && city.region.contains(q))) {
                    koreanResults.add(city)
                }
            }
        }

        for (city in WORLD_CITIES) {
            if (chosung) {
                if (matchChosung(city.name, q)) worldResults.add(city)
            } else {
                val label = formatCityName(city)
                if (label.contains(q)) worldResults.add(city)
            }
        }

        return (koreanResults + worldResults).take(20)
    }
}
