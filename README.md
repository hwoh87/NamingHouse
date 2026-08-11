# 작명하우스 (NamingHouse)

사주와 성명학으로 아기 이름을 짓고 감명하는 Android 앱.
[작명왕](https://play.google.com/store/apps/details?id=com.sisomobile.android.makename) 류의 상용 작명 앱 기능을 참고해, 사주 계산은 삼라만상(Samra) `engine-core`를 이식해 사용한다.

## 모듈 구조

```
:app     Compose UI (입력 → 결과)
:engine  ┬ com.samramanshang.manseryeok.orrery.*  삼라 사주 엔진 이식본 (수정 금지)
         └ com.naminghouse.engine.*               성명학 엔진 (자체 구현)
```

## 세 가지 모드

| 모드 | 입력 | 결과 |
|---|---|---|
| 이름 추천 | 성씨(+한자), 성별, 출생정보 | 이름 풀에서 성명학·사주 조건을 만족하는 이름 60개, 각 이름에 최적 한자 조합을 붙여 점수순 |
| 한자 추천 | 성씨(+한자), 정해둔 한글 이름, 출생정보 | 그 이름에 붙일 수 있는 한자 조합 30개를 점수순 (`gen/NameGenerator.hanjaCombosFor`) |
| 이름 감명 | 성씨·이름의 한자까지 직접 지정 | 그 이름 하나의 축별 상세 풀이 |

세 모드 모두 "출생 전/사주 없이" 토글로 사주 없이 성명학만 볼 수 있다.

### 삼라 엔진 이식 (`com.samramanshang.manseryeok.orrery`)

`../Samra/engine-core/src/commonMain` 에서 사주 경로 14개 파일만 복사(패키지 유지, 무수정).
만세력(절기·자시·역사적 KST 오프셋), 오행 가중 분포(지장간 포함), 합화 보정, 신강약(억부),
용신/기신, 격국까지 사용한다. 원본과의 동일성은 삼라 골든 덤프 기반
`SajuGoldenTest` 가 보증한다. **이 패키지는 수정하지 말 것** — 삼라 쪽 갱신 시 디렉터리 diff 로 재동기화한다.

### 성명학 엔진 (`com.naminghouse.engine`)

| 축 | 구현 | 비고 |
|---|---|---|
| 수리사격(원형이정) | `suri/SuriCalculator` | 한국식(허수 없음), 원획 기준, 외자·복성·3자 변형 지원 |
| 81수리 길흉 | `suri/Suri81` | 길수 40·흉수 40·77 반길반흉 (인터넷한국작명연구원 표 기준) |
| 발음오행 | `oheng/BaleumOheng` | 운해본(다수설, ㅇㅎ=土) 기본 + 해례본(ㅇㅎ=水) 옵션 |
| 음양 배열 | `oheng/EumYang` | 수리음양(획수 홀짝) + 발음음양(모음) |
| 자원오행·사주보완 | `eval/NameEvaluator` | 용신 우선 + 결핍 오행 보충, 기신 페널티 |
| 불용한자 | `data/BulyongHanja` | 239자, 차단이 아닌 경고 용도 |
| 후보 생성 | `gen/NameGenerator` | 이름 풀 → 발음 필터 → 4격 전길 획수 조합 → 자원오행 최적 한자 |

배점: 수리 35 + 발음오행 20 + 자원오행·사주보완 30 + 음양 10 + 불용 무결 5 = 100.

작명왕 대비 아직 없는 것: 이름별 출생신고 인기순위·남녀비율(현재는 tier 1/2/3 근사),
개명 전용 모드, 결제·프리미엄 게이트.

## 데이터 (`engine/src/main/assets`)

| 파일 | 내용 | 출처 |
|---|---|---|
| `hanja.tsv` | 인명용 한자 9,054자: 독음, 원획, 필획, 자원오행, 뜻, 인명 적합도 | 대법원 인명용 한자표(rutopio MIT 정리본) + Unihan(획수·부수·상용도) + libhangul(훈음, BSD-3). 원획은 부수 원형 환산, 자원오행은 부수 기반 자체 매핑 |
| `names.tsv` | 한글 이름 후보 풀 1,104개 (성별·티어) | 대법원 출생신고 이름 통계 등 공개 순위 집계 |

재생성:

```bash
python3 tools/hanja-db/build_hanja_db.py && python3 tools/name-pool/build_names.py
```

원본 데이터를 자동 내려받고 옥편 대조 assert까지 돌린다.

### 인명 적합도 필터

인명용 한자에는 蔬(나물)·嗽(기침할)·蜘(거미)처럼 흔하지만 이름엔 쓰지 않는 글자가 많다.
`hanja.tsv` 의 `namefit`(Unihan 한국 코어 기반 상용도)과 `avoid`(훈 기반 판정)로 걸러
작명 후보 생성에는 1,020자만 쓴다. 한자 선택 화면에서는 전부 보여주되 적합한 글자를 앞에 정렬한다.
판정 규칙과 근거는 [tools/hanja-db/README.md](tools/hanja-db/README.md) 참조.

## 빌드·테스트

```bash
./gradlew :app:assembleDebug          # APK
./gradlew :engine:testDebugUnitTest   # 엔진 테스트 (골든 패리티 + 데이터 무결성 포함)
```

스택: AGP 9.1.1 · Kotlin 2.2.10 · Compose BOM 2024.09 · minSdk 24 / targetSdk 36 (삼라와 동일 버전 유지).

## 성명학 주의사항

- 발음오행 ㅇㅎ 배속은 학파 논쟁이 있어 설정으로 노출한다(기본 운해본).
- 불용한자는 현대 성명학에서도 이견이 큰 속설이라 경고만 하고 후보에서 제외하는 옵션으로 처리.
- 원형이정 3자 이름 공식은 표준 문헌이 드물어 통용 방식(원=이름합, 형=성+첫자, 이=성+끝자)을 따름.
