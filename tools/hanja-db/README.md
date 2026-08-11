# hanja-db — 인명용 한자 DB 생성기

`engine/src/main/assets/hanja.tsv` 를 생성하는 재현 가능한 파이프라인.

## 재생성

```bash
python3 tools/hanja-db/build_hanja_db.py
```

파이썬 표준 라이브러리만 사용한다(pip 불필요). 소스 파일이 `tools/hanja-db/raw/` 에
없으면 자동 다운로드하고, 있으면 캐시를 재사용한다. 빌드 후 옥편 대조 검증
(assert)과 외부 데이터 교차검증 통계를 출력한다.

## 출력 포맷 — `engine/src/main/assets/hanja.tsv`

UTF-8, 탭 구분, 헤더 1행, 유니코드 코드포인트 순 정렬. 대법원 인명용 한자만 수록.

| 컬럼 | 설명 |
|---|---|
| `hanja` | 한자 1자 |
| `readings` | 관인용 음(콤마 구분, 두음법칙 이형 포함 — 예: `樂` → `낙,락,악,요`) |
| `wonhoek` | 원획(수리성명학용). 부수 축약형을 정자 획수로 환산: 氵→水4, 忄→心4, 扌→手4, 犭→犬4, 王→玉5, 礻→示5, 月(육달월)→肉6, 艹→艸6, 衤→衣6, 罒→网6, 耂→老6, 辶→辵7, 우부방阝→邑7, 좌부변阝→阜8. 숫자 예외: 四=4 五=5 六=6 七=7 八=8 九=9 十=10 |
| `pilhoek` | 필획 = Unihan `kTotalStrokes` 원본값. 주의: Unihan 은 艹=3획·阝=2획(중국식) 으로 계수하므로 한국 옥편 필획과 일부 다를 수 있다. 원획 계산은 `잔여획+정자부수획` 방식이라 이 차이의 영향을 받지 않는다 |
| `element` | 자원오행(木火土金水 또는 빈값). 부수→오행 표 + 천간/지지/숫자/특수자 글자단위 예외. 근거는 `build_hanja_db.py` 의 `RADICAL_ELEMENT` 주석 참조 |
| `meaning` | 짧은 훈음(≤12자), libhangul 사전에서 관인 음과 일치하는 항목 우선 |
| `namefit` | 인명 적합도 0~4. Unihan `kIICore`/`kUnihanCore2020` 의 한국(K) 코어 포함 여부 + `kGradeLevel`. 벽자(汏·瘰·刲)는 0, 이름에 쓰는 글자(洙·珉·玗)는 2 이상 |
| `avoid` | 이름에 쓰지 않는 글자면 1. 아래 "인명 적합 판정" 참조 |

현재 9,054자 / 자원오행 커버리지 95.4% / 작명 후보로 쓰는 글자(`namefit≥2 && !avoid`) 1,020자.

## 인명 적합 판정 (`namefit` / `avoid`)

인명용 한자 8천여 자에는 蔬(나물)·嗽(기침할)·蜘(거미)처럼 **흔하지만 이름엔 안 쓰는** 글자가
대량으로 들어 있다. 이런 글자가 작명 후보에 오르면 결과가 통째로 신뢰를 잃으므로 두 단계로 거른다.

1. **`namefit` (상용도)** — Unihan 의 한국 코어 플래그로 벽자를 걷어낸다. 洙·珉·玗 처럼
   이름에만 쓰는 글자도 한국 코어라 살아남는다. 다만 상용도만으로는 위의 蔬·嗽 를 못 거른다.
2. **`avoid` (뜻 판정)** — 기피어 목록을 늘리는 대신 **통과 목록**을 둔다. 실제 인명 한자는
   뜻이 뚜렷하게 좋거나(밝을·어질·빛날) 아름다운 자연·기물(구슬·별·바다·소나무)에 몰려 있어,
   `NAME_HUN_KEYWORDS` 에 걸리는 훈만 통과시키는 편이 정밀도가 훨씬 높다.
   - 짧은 명사(물·별·못·금)는 부분일치 시 劣(못할)·贓(장물)·辨(분별할)·王(임금)에 오탐이 나므로
     `NAME_HUN_TOKENS` 로 낱말 단위 일치만 인정한다.
   - 훈이 무미건조한 표준 인명자(書·時·宇·河)와 성씨 한자(金·朴·崔)는 `NAME_CHAR_WHITELIST` /
     `SURNAME_HANJA` 로 명시 통과시킨다. 성씨가 빠지면 그 성을 아예 고를 수 없다.
   - 죽음·질병·범죄·배설·어조사 계열 훈과 이체자 표기(`年의 本字`)는 `AVOID_SUBSTRINGS` 로 차단.

회귀 방지: 흔한 이름 한자 150자가 전부 통과하는지, 알려진 부적합자가 전부 차단되는지를
`engine/src/test/.../DataIntegrationTest.kt` 가 검사한다.

전통 **불용한자**(大·龍·天 등 속설상 기피자)는 이와 별개 개념으로,
앱의 `BulyongHanja.kt` 가 담당하며 차단이 아니라 경고로 처리한다.

## 소스 및 라이선스

| 데이터 | 출처 | 라이선스 | 용도 |
|---|---|---|---|
| 인명용 한자 목록 + 음 | [rutopio/Korean-Name-Hanja-Charset](https://github.com/rutopio/Korean-Name-Hanja-Charset) `data-gov.csv` (원천: 대한민국 공공데이터/대법원) | MIT | 수록 범위 + `readings` |
| 필획/부수 | [Unicode Unihan](https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip) `Unihan_IRGSources.txt` (`kTotalStrokes`, `kRSUnicode`) | Unicode License v3 | `pilhoek`, `wonhoek`, 부수→`element` |
| 훈음 | [libhangul](https://github.com/libhangul/libhangul) `data/hanja/hanja.txt` | BSD-3-Clause | `meaning` |
| (교차검증 전용) | [yumikang/saju](https://github.com/yumikang/saju) `hanja_dict.csv` / JSON 백업 | 라이선스 불명 | 통계 비교만, 데이터 재사용 없음 |

권장 고지 문구:

> 인명용 한자 데이터: 대한민국 대법원 인명용 한자표(공공데이터, rutopio/Korean-Name-Hanja-Charset 경유, MIT).
> 획수·부수: Unicode® Unihan Database, © Unicode, Inc., Unicode License v3.
> 훈음: libhangul 프로젝트 (© 2005-2006 Choe Hwanjin 외, BSD-3-Clause).

## 자원오행 표에 관하여

`RADICAL_ELEMENT` 는 강희부수 214자 전체에 대해, 다음을 대조하여 다수설을 채택했다
(부수별 근거를 소스 코드 주석에 기록):

- ksname.co.kr 「한자의 부수 및 자원오행」 (부수 전반)
- miso.co.kr / irum.com 성명학 이론 페이지 (오행별 예시자)
- agiirum.com 좋은이름 작명법 (역사 인명의 자원오행 분해 예시)
- 5KLetsGo/saju_naming 데이터셋의 글자별 자원오행 라벨 806자 (빈도 대조)
- 우당작명연구원 (示부 木/金 논쟁 정리)

유파가 갈리는 부수는 다수설로: `心→火, 言→金, 玉→金, 月→水, 口→水, 囗→水,
石→金(소수설 土), 示→木(오행대전은 金), 人→火, 肉(육달월)→水`.
확정 근거가 없는 부수(一, 儿, 力, 皿, 臣, 身 등 50개)는 빈값으로 두었으며(214개 중
164개 배정), 해당 부수의 글자는 `element` 빈값이 된다.
천간(甲乙…癸)·지지(子丑…亥)·숫자(一二=木 三四=火 五六=土 七八=金 九十=水)·中·曺 는
글자 단위로 우선 적용한다.

검증된 원획 예: 洙=10, 洪=10, 珉=10, 祐=10, 道=16, 防=12, 草=12, 羅=20, 藝=21,
恩=10, 性=9, 德=15, 裕=13, 泰=10(축약형 아님 → 원획=필획).

## 교차검증 결과 (2026-08-11)

- 필획: yumikang 수기 검수 191자 대비 89.0% 일치 (불일치는 상대측의 한국식 阝=3획
  계수·원획 혼입·단순 오기 — 예: 讚=19로 기재). 상대 JSON 백업 8,786자의 strokes 는
  Unihan 유래로 표기되어 있으나 실제와 무관한 손상 데이터(伐=23 등)라 참고 불가.
- 자원오행: yumikang 수기 검수 172자 대비 49.4% — 단, 불일치 87자 중 66%가 해당
  글자 '음의 발음오행'과 일치(姜→木=ㄱ, 道→火=ㄷ, 文→水=ㅁ 등), 즉 상대 라벨은
  발음오행/자원오행 혼입 데이터로 판단되어 부수표 수정 근거로 쓰지 않았다.
  독립 라벨인 5KLetsGo 데이터셋 778자 대비로는 97.3% 일치.
