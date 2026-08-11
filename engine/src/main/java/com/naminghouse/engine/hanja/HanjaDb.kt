package com.naminghouse.engine.hanja

import com.samramanshang.manseryeok.orrery.model.Element

/** 인명용 한자 한 글자 정보 */
data class HanjaEntry(
    val char: Char,
    /** 대법원 인명용 독음(가나다순 아님, 복수 가능) */
    val readings: List<String>,
    /** 원획(수리성명학용) */
    val wonhoek: Int,
    /** 필획(옥편 획수) */
    val pilhoek: Int,
    /** 자원오행 — 부수·자의 기반. 미상이면 null */
    val element: Element?,
    /** 훈(뜻) 요약 */
    val meaning: String,
    /**
     * 인명 적합도 0~4 (Unihan 한국 코어 여부 기반).
     * 0~1 은 벽자라 작명 후보 생성에서 제외하고, 한자 선택 목록에서는 뒤로 보낸다.
     */
    val nameFit: Int,
    /** 훈이 이름에 부적절한 글자(탐할·입술·어찌·이체자 표기 등) */
    val avoid: Boolean,
) {
    /** 작명 후보 생성에 쓸 수 있는 글자인지 */
    val usableForNaming: Boolean get() = nameFit >= 2 && !avoid
}

/**
 * hanja.tsv(인명용 한자 DB) 로더. 생성기는 tools/hanja-db/build_hanja_db.py.
 * 형식: hanja \t readings(콤마) \t wonhoek \t pilhoek \t element(木火土金水|빈값) \t meaning
 *       \t namefit(0~4) \t avoid(0|1)
 */
class HanjaDb private constructor(
    val entries: List<HanjaEntry>,
) {
    val byChar: Map<Char, HanjaEntry> = entries.associateBy { it.char }

    /** 독음 → 그 소리로 읽는 인명용 한자들. 이름에 쓸 만한 글자가 앞에 오도록 정렬한다. */
    val byReading: Map<String, List<HanjaEntry>> = run {
        val m = HashMap<String, MutableList<HanjaEntry>>()
        for (e in entries) for (r in e.readings) m.getOrPut(r) { mutableListOf() }.add(e)
        m.mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<HanjaEntry> { it.usableForNaming }
                    .thenByDescending { it.nameFit }
                    .thenBy { it.wonhoek }
            )
        }
    }

    /** 음에 해당하는 인명용 한자 — 인명 적합도 높은 순, 같으면 획수 순 */
    fun candidatesFor(syllable: String): List<HanjaEntry> = byReading[syllable] ?: emptyList()

    companion object {
        private val ELEMENT_BY_HANJA = Element.entries.associateBy { it.hanja }

        fun parse(lines: Sequence<String>): HanjaDb {
            val entries = ArrayList<HanjaEntry>(9000)
            var header = true
            for (line in lines) {
                if (header) { header = false; continue }
                if (line.isBlank()) continue
                val cols = line.split('\t')
                if (cols.size < 8) continue
                val ch = cols[0].firstOrNull() ?: continue
                val wonhoek = cols[2].toIntOrNull() ?: continue
                val pilhoek = cols[3].toIntOrNull() ?: wonhoek
                if (wonhoek <= 0) continue
                entries.add(
                    HanjaEntry(
                        char = ch,
                        readings = cols[1].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        wonhoek = wonhoek,
                        pilhoek = pilhoek,
                        element = ELEMENT_BY_HANJA[cols[4].trim()],
                        meaning = cols[5].trim(),
                        nameFit = cols[6].toIntOrNull() ?: 0,
                        avoid = cols[7].trim() == "1",
                    )
                )
            }
            return HanjaDb(entries)
        }
    }
}
