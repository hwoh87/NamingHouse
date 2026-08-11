#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_name_stats.py — 대법원 '상위 출생신고 이름 현황' 수집기

engine/src/main/assets/name-stats.tsv 를 생성한다. (stdlib 만 사용)

    python3 tools/name-stats/build_name_stats.py

출처: 대한민국 법원 전자가족관계등록시스템 통계서비스
      https://stfamily.scourt.go.kr/st/StFrrStatcsView.do?pgmId=090000000025
      "가족관계의 등록 등에 관한 법률 시행(2008.1.1.) 이후 ... 이름 순위로 집계한 일단위 통계 잠정치"

조회 API 는 화면이 쓰는 것과 같은 것을 그대로 사용한다(WISE 리포트):
    POST /ds/report/query.do
      pid=1811 uid=999999 dsid=1261 dstype=DS sqlid=1811-1
      params={"@MultiCandType":{"value":["YY"]...}, "@MultiCandStDt":{"value":["2024"]},
              "@SidoCd":{"value":["11"]}, "@GenderCd":{"value":["1"]}, ...}
    응답: {"data":[{"순위":1,"이름":"태오","건수":424,"전체비율":"태오(7.95%)"}, ...]}

제약과 그로 인한 설계:
  - 시도(SidoCd)가 필수이고 '전체' 선택지가 없다 → 16개 시도를 각각 조회해 합산한다.
  - 한 번 조회에 상위 N개(대략 20개)만 돌아온다 → 전국 순위는 '각 지역 상위권의 합'
    이라 하위권 이름은 과소집계된다. 인기 이름 표시 용도로는 충분하지만 정밀 순위는 아니다.
  - 조회유형 '년도'는 시작~끝 범위가 2년 이내여야 한다 → 연도별로 한 해씩 조회한다.

정중하게: 요청 사이에 SLEEP 초 쉬고, 받은 원본은 raw/ 에 캐시해 재실행 시 다시 받지 않는다.
"""

import json
import os
import sys
import time
import urllib.parse
import urllib.request
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_TSV = os.path.join(PROJECT, "engine", "src", "main", "assets", "name-stats.tsv")

BASE = "https://stfamily.scourt.go.kr"
PAGE = BASE + "/st/StFrrStatcsView.do?pgmId=090000000025"
QUERY = BASE + "/ds/report/query.do"

# 현재 유효한 16개 시도. '(구)' 로 표기된 옛 행정구역 코드는 중복 집계를 피하려 제외한다.
SIDO = {
    "11": "서울특별시", "12": "전남광주통합특별시", "26": "부산광역시", "27": "대구광역시",
    "28": "인천광역시", "30": "대전광역시", "31": "울산광역시", "36": "세종특별자치시",
    "41": "경기도", "43": "충청북도", "44": "충청남도", "47": "경상북도", "48": "경상남도",
    "50": "제주특별자치도", "51": "강원특별자치도", "52": "전북특별자치도",
}
GENDER = {"1": "M", "2": "F"}
YEARS = list(range(2008, 2027))
SLEEP = 0.4
TIMEOUT = 60

# 결과에 몇 개 연도의 순위를 실을지 (최근순)
RANK_YEARS = 6


def build_params(year, sido, gender):
    def p(v, where=None):
        d = {"value": [v], "type": "STRING", "defaultValue": "[All]" if where else ""}
        if where:
            d["whereClause"] = where
        return d

    return json.dumps({
        "@MultiCandType": p("YY"),
        "@MultiCandStDt": p(str(year)),
        "@MultiCandEdDt": p(str(year)),
        "@SidoCd": p(sido, "C.SIDO_CD"),
        "@CggCd": p("_EMPTY_VALUE_", "D.CGG_CD"),
        "@UmdCd": p("_EMPTY_VALUE_", "E.UMD_CD"),
        "@GenderCd": p(gender, "F.GENDER_CD"),
    }, ensure_ascii=False)


def open_session():
    """세션 쿠키를 받아 둔 opener 를 만든다 (쿠키 없이는 query.do 가 빈 응답)."""
    import http.cookiejar
    jar = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    op.addheaders = [("User-Agent", "NamingHouse-name-stats/1.0"), ("Referer", PAGE)]
    op.open(PAGE, timeout=TIMEOUT).read()
    return op


def fetch(op, year, sido, gender):
    cache = os.path.join(RAW, f"{year}_{sido}_{gender}.json")
    if os.path.exists(cache) and os.path.getsize(cache) > 2:
        with open(cache, encoding="utf-8") as f:
            return json.load(f)

    body = urllib.parse.urlencode({
        "pid": "1811", "uid": "999999", "dsid": "1261", "dstype": "DS",
        "mapid": f"nh-{year}-{sido}-{gender}", "sqlid": "1811-1",
        "params": build_params(year, sido, gender),
    }).encode()
    req = urllib.request.Request(QUERY, data=body, headers={
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
    })
    with op.open(req, timeout=TIMEOUT) as r:
        payload = json.loads(r.read().decode("utf-8"))
    rows = payload.get("data", [])
    os.makedirs(RAW, exist_ok=True)
    with open(cache, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False)
    time.sleep(SLEEP)
    return rows


def collect():
    op = open_session()
    # counts[gender][year][name] = 건수 합
    counts = {g: defaultdict(lambda: defaultdict(int)) for g in GENDER.values()}
    total, done, failed = len(YEARS) * len(SIDO) * len(GENDER), 0, 0

    for year in YEARS:
        for sido in SIDO:
            for gcode, glabel in GENDER.items():
                done += 1
                try:
                    rows = fetch(op, year, sido, gcode)
                except Exception as e:
                    failed += 1
                    print(f"  [warn] {year} {SIDO[sido]} {glabel}: {e}", file=sys.stderr)
                    continue
                for row in rows:
                    name = str(row.get("이름", "")).strip()
                    cnt = row.get("건수") or 0
                    if not name or not isinstance(cnt, int):
                        continue
                    # 한글 1~3자만 (외국식 표기·특수문자 제외)
                    if not (1 <= len(name) <= 3 and all("가" <= c <= "힣" for c in name)):
                        continue
                    counts[glabel][year][name] += cnt
            if done % 60 == 0:
                print(f"  {done}/{total} …")
    print(f"[collect] {done}건 조회, 실패 {failed}건")
    return counts


def build():
    counts = collect()

    # 연도·성별별 순위
    ranks = {g: {} for g in GENDER.values()}
    for g in ranks:
        for year, names in counts[g].items():
            ordered = sorted(names.items(), key=lambda kv: (-kv[1], kv[0]))
            ranks[g][year] = {n: i + 1 for i, (n, _) in enumerate(ordered)}

    male_total = defaultdict(int)
    female_total = defaultdict(int)
    for year, names in counts["M"].items():
        for n, c in names.items():
            male_total[n] += c
    for year, names in counts["F"].items():
        for n, c in names.items():
            female_total[n] += c

    all_names = sorted(set(male_total) | set(female_total))
    recent = sorted(counts["M"].keys() | counts["F"].keys(), reverse=True)[:RANK_YEARS]

    rows = []
    for name in all_names:
        m, f = male_total.get(name, 0), female_total.get(name, 0)
        if m + f == 0:
            continue
        dominant = "M" if m >= f else "F"
        hist = []
        for year in recent:
            r = ranks[dominant].get(year, {}).get(name)
            if r:
                hist.append(f"{year}:{r}")
        rows.append((name, dominant, m, f, ",".join(hist)))

    os.makedirs(os.path.dirname(OUT_TSV), exist_ok=True)
    with open(OUT_TSV, "w", encoding="utf-8", newline="\n") as out:
        out.write("name\tdominant\tmaleCount\tfemaleCount\tranks\n")
        for r in rows:
            out.write("\t".join(str(x) for x in r) + "\n")

    with_rank = sum(1 for r in rows if r[4])
    print(f"[build] {len(rows)}개 이름 / 최근 순위 보유 {with_rank}개 → {os.path.relpath(OUT_TSV, PROJECT)}")
    if rows:
        top = sorted(rows, key=lambda r: -(r[2] + r[3]))[:8]
        print("  최다 집계:", ", ".join(f"{n}({m + f}건, 남{m}/여{f})" for n, d, m, f, h in top))
    return rows


if __name__ == "__main__":
    raise SystemExit(0 if build() else 1)
