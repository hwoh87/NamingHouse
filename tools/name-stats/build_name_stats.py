#!/usr/bin/env python3
"""Build the regional top-name sample. Incomplete/invalid runs never publish a bundle.

Only the Python standard library is used. See README.md for provenance limitations.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
PROJECT = HERE.parent.parent
OUT_TSV = PROJECT / "engine/src/main/assets/name-stats.tsv"
OUT_METADATA = OUT_TSV.with_name("name-stats-metadata.properties")
BASE = "https://stfamily.scourt.go.kr"
PAGE = BASE + "/st/StFrrStatcsView.do?pgmId=090000000025"
QUERY = BASE + "/ds/report/query.do"
# Preserve the existing query profile. This is NOT proof of historical region coverage.
# Administrative changes and old codes require source review before changing this list.
SIDO = {
    "11": "서울특별시", "12": "전남광주통합특별시", "26": "부산광역시", "27": "대구광역시",
    "28": "인천광역시", "30": "대전광역시", "31": "울산광역시", "36": "세종특별자치시",
    "41": "경기도", "43": "충청북도", "44": "충청남도", "47": "경상북도", "48": "경상남도",
    "50": "제주특별자치도", "51": "강원특별자치도", "52": "전북특별자치도",
}
GENDER = {"1": "M", "2": "F"}
YEARS = list(range(2008, date.today().year + 1))
SLEEP = 0.4
TIMEOUT = 60
RANK_YEARS = 6
MAX_ROWS = 20
MAX_INT = 2**31 - 1
CACHE_VERSION = 1
QUERY_VERSION = 1
HEADER = "name\tdominant\tmaleCount\tfemaleCount\tranks\n"


class ValidationError(ValueError):
    pass


@dataclass(frozen=True)
class Query:
    year: int
    sido: str
    gender: str

    @property
    def key(self):
        return f"{self.year}_{self.sido}_{self.gender}"

    def identity(self):
        return {"year": self.year, "sido": self.sido, "gender": self.gender}


@dataclass(frozen=True)
class Snapshot:
    query: Query
    rows: list
    fetched_at: str | None  # Legacy files have no trustworthy collection date.


def now_utc():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def rows_bytes(rows):
    return json.dumps(rows, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def validate_timestamp(value):
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ValidationError("fetchedAt must be an explicit UTC timestamp")
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValidationError("invalid fetchedAt") from error
    return value


def validate_rows(rows, *, allow_empty=False):
    if not isinstance(rows, list) or len(rows) > MAX_ROWS:
        raise ValidationError("data must be a list of at most 20 rows; source schema may have changed")
    if not rows and not allow_empty:
        raise ValidationError("empty data is ambiguous (no records or expired session); explicit review required")
    names = set()
    for row in rows:
        if not isinstance(row, dict):
            raise ValidationError("non-object source row")
        name, count, rank = row.get("이름"), row.get("건수"), row.get("순위")
        if not isinstance(name, str) or not name or name != name.strip() or any(ord(c) < 32 for c in name):
            raise ValidationError("invalid source name")
        if name in names:
            raise ValidationError(f"duplicate source name: {name}")
        names.add(name)
        if type(count) is not int or not 0 < count <= MAX_INT:
            raise ValidationError(f"invalid source count: {name}")
        if type(rank) is not int or not 0 < rank <= MAX_INT:
            raise ValidationError(f"invalid source rank: {name}")
        # Source ranks may tie. Do not reject duplicate rank values.
    return rows


def stage_file(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
    except BaseException:
        Path(temporary).unlink(missing_ok=True)
        raise
    return Path(temporary)


def atomic_write(path, data):
    temporary = stage_file(path, data)
    try:
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def publish_bundle(tsv_path, metadata_path, tsv, metadata):
    """Each replacement is atomic; metadata hash is the commit marker for the pair.

    Ordinary failures roll back both files. A process/power loss between replacements
    can leave a mismatched pair, which the app rejects using datasetSha256.
    """
    paths = [Path(tsv_path), Path(metadata_path)]
    if paths[0].resolve() == paths[1].resolve():
        raise ValidationError("TSV and metadata paths must differ")
    previous = [p.read_bytes() if p.exists() else None for p in paths]
    staged = []
    replaced = []
    try:
        for path, data in zip(paths, [tsv, metadata]):
            staged.append(stage_file(path, data))
        for index, (path, temporary) in enumerate(zip(paths, staged)):
            os.replace(temporary, path)
            replaced.append(index)
    except BaseException:
        for index in reversed(replaced):
            if previous[index] is None:
                paths[index].unlink(missing_ok=True)
            else:
                atomic_write(paths[index], previous[index])
        raise
    finally:
        for temporary in staged:
            temporary.unlink(missing_ok=True)


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
    op.addheaders = [("User-Agent", "NamingHouse-name-stats/2.0"), ("Referer", PAGE)]
    op.open(PAGE, timeout=TIMEOUT).read()
    return op


def read_empty_allowlist(path):
    if path is None:
        return {}
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if (not isinstance(payload, dict) or type(payload.get("schemaVersion")) is not int
            or payload.get("schemaVersion") != 1 or not isinstance(payload.get("queries"), dict)):
        raise ValidationError("invalid empty-query review file")
    for key, reason in payload["queries"].items():
        if not re.fullmatch(r"[0-9]{4}_[0-9]{2}_[12]", key) or not isinstance(reason, str) or not reason.strip():
            raise ValidationError("every approved empty query needs its exact key and review reason")
    return payload["queries"]


class Collector:
    def __init__(self, raw_dir=RAW, *, offline=False, allow_legacy_cache=False,
                 refresh_current_year=False, refresh_all=False, empty_allowlist=None,
                 opener_factory=open_session, current_year=None):
        self.raw_dir = Path(raw_dir)
        self.offline = offline
        self.allow_legacy_cache = allow_legacy_cache
        self.refresh_current_year = refresh_current_year
        self.refresh_all = refresh_all
        self.empty_allowlist = empty_allowlist or {}
        self.opener_factory = opener_factory
        self.opener = None
        self.current_year = current_year or date.today().year

    def fetch(self, query):
        path = self.raw_dir / f"{query.key}.json"
        allow_empty = query.key in self.empty_allowlist
        refresh = self.refresh_all or (self.refresh_current_year and query.year == self.current_year)
        if path.exists() and not refresh:
            payload = json.loads(path.read_text(encoding="utf-8"))
            if isinstance(payload, list):
                if not self.allow_legacy_cache:
                    raise ValidationError(f"{query.key}: unversioned cache; review or use --refresh-all")
                return Snapshot(query, validate_rows(payload, allow_empty=allow_empty), None)
            if not isinstance(payload, dict) or type(payload.get("cacheSchemaVersion")) is not int or payload.get("cacheSchemaVersion") != CACHE_VERSION:
                raise ValidationError(f"{query.key}: unsupported cache version")
            if (type(payload.get("querySchemaVersion")) is not int
                    or payload.get("querySchemaVersion") != QUERY_VERSION or payload.get("source") != QUERY
                    or payload.get("query") != query.identity()):
                raise ValidationError(f"{query.key}: cache query/source mismatch")
            rows = validate_rows(payload.get("rows"), allow_empty=allow_empty)
            if payload.get("rowsSha256") != sha256(rows_bytes(rows)):
                raise ValidationError(f"{query.key}: cache checksum mismatch")
            return Snapshot(query, rows, validate_timestamp(payload.get("fetchedAt")))
        if self.offline:
            raise ValidationError(f"{query.key}: network required but --offline is active")
        if self.opener is None:
            self.opener = self.opener_factory()
        body = urllib.parse.urlencode({
            "pid": "1811", "uid": "999999", "dsid": "1261", "dstype": "DS",
            "mapid": f"nh-{query.key}", "sqlid": "1811-1",
            "params": build_params(query.year, query.sido, query.gender),
        }).encode()
        request = urllib.request.Request(QUERY, data=body, headers={
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With": "XMLHttpRequest",
        })
        with self.opener.open(request, timeout=TIMEOUT) as response:
            data = response.read(512 * 1024 + 1)
        if len(data) > 512 * 1024:
            raise ValidationError("oversized source response")
        payload = json.loads(data.decode("utf-8"))
        if not isinstance(payload, dict) or "data" not in payload:
            raise ValidationError("source response has no data field")
        rows = validate_rows(payload["data"], allow_empty=allow_empty)
        fetched_at = now_utc()
        cache = {
            "cacheSchemaVersion": CACHE_VERSION, "querySchemaVersion": QUERY_VERSION,
            "source": QUERY, "query": query.identity(), "fetchedAt": fetched_at,
            "rowsSha256": sha256(rows_bytes(rows)), "rows": rows,
        }
        if not rows:
            cache["emptyReviewReason"] = self.empty_allowlist[query.key]
        atomic_write(path, (json.dumps(cache, ensure_ascii=False, sort_keys=True) + "\n").encode("utf-8"))
        time.sleep(SLEEP)
        return Snapshot(query, rows, fetched_at)


def collect(collector, years, regions):
    snapshots = []
    for year in years:
        for sido in regions:
            for gender in GENDER:
                query = Query(year, sido, gender)
                # Fail closed. Previously, a caught exception published incomplete totals.
                snapshots.append(collector.fetch(query))
    return snapshots


def render_tsv(snapshots, years):
    counts = {g: defaultdict(lambda: defaultdict(int)) for g in GENDER.values()}
    for snapshot in snapshots:
        for row in snapshot.rows:
            name = row["이름"]
            # Keep the established one-to-three Hangul syllable dataset scope.
            if re.fullmatch(r"[가-힣]{1,3}", name):
                counts[GENDER[snapshot.query.gender]][snapshot.query.year][name] += row["건수"]
    ranks = {g: {} for g in GENDER.values()}
    totals = {g: defaultdict(int) for g in GENDER.values()}
    for gender in ranks:
        for year, names in counts[gender].items():
            ordered = sorted(names.items(), key=lambda pair: (-pair[1], pair[0]))
            ranks[gender][year] = {name: i + 1 for i, (name, _) in enumerate(ordered)}
            for name, count in names.items():
                totals[gender][name] += count
    rows = []
    recent = sorted(years, reverse=True)[:RANK_YEARS]
    for name in sorted(set(totals["M"]) | set(totals["F"])):
        male, female = totals["M"][name], totals["F"][name]
        if not 0 < male + female <= MAX_INT:
            raise ValidationError(f"aggregate count outside Android integer range: {name}")
        dominant = "M" if male >= female else "F"
        history = [f"{year}:{ranks[dominant][year][name]}" for year in recent
                   if name in ranks[dominant].get(year, {})]
        rows.append((name, dominant, male, female, ",".join(history)))
    if not rows:
        raise ValidationError("no supported names; refusing to publish an empty bundle")
    return (HEADER + "".join("\t".join(map(str, row)) + "\n" for row in rows)).encode("utf-8"), len(rows)


def render_metadata(tsv, name_count, snapshots, years, regions):
    dates = [s.fetched_at for s in snapshots if s.fetched_at is not None]
    all_dated = len(dates) == len(snapshots)
    fields = {
        "schemaVersion": 1, "datasetSha256": sha256(tsv), "sourceUrl": PAGE,
        "sourceScope": "regional_top_names", "queryYearStart": min(years), "queryYearEnd": max(years),
        "regionCodes": ",".join(regions), "maxRowsPerRegion": MAX_ROWS,
        "coverageStatus": "complete_query_set" if all_dated else "legacy_unverified",
        "collectedFrom": min(dates) if all_dated else "unknown",
        "collectedThrough": max(dates) if all_dated else "unknown",
        # A successful request does not reveal the source's actual reporting cutoff.
        "sourcePeriodStart": "unknown", "sourcePeriodEnd": "unknown",
        "expectedRequestCount": len(years) * len(regions) * len(GENDER),
        "successfulRequestCount": len(snapshots), "nameCount": name_count,
        "generatedAt": now_utc(),
    }
    return "".join(f"{key}={value}\n" for key, value in fields.items()).encode("utf-8")


def build(collector=None, *, years=None, regions=None, out_tsv=OUT_TSV, out_metadata=OUT_METADATA):
    years = list(YEARS if years is None else years)
    regions = list(SIDO if regions is None else regions)
    if (not years or years != list(range(min(years), max(years) + 1))
            or min(years) < 2008 or max(years) > date.today().year):
        raise ValidationError("years must be a consecutive range from 2008 through the current year")
    if not regions or len(set(regions)) != len(regions) or any(r not in SIDO for r in regions):
        raise ValidationError("invalid or duplicate region codes")
    snapshots = collect(collector or Collector(), years, regions)
    tsv, count = render_tsv(snapshots, years)
    metadata = render_metadata(tsv, count, snapshots, years, regions)
    publish_bundle(out_tsv, out_metadata, tsv, metadata)
    print(f"[build] {count} names / {len(snapshots)} validated queries → {out_tsv}")
    return count


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start-year", type=int, default=2008)
    parser.add_argument("--end-year", type=int, default=date.today().year)
    parser.add_argument("--offline", action="store_true", help="never open a network session")
    parser.add_argument("--allow-legacy-cache", action="store_true", help="accept validated unversioned caches; collection date stays unknown")
    parser.add_argument("--refresh-current-year", action="store_true", help="re-query current-year caches only")
    parser.add_argument("--refresh-all", action="store_true", help="ignore all existing caches")
    parser.add_argument("--empty-allowlist", type=Path, help="reviewed query keys and reasons; see README")
    args = parser.parse_args(argv)
    try:
        collector = Collector(offline=args.offline, allow_legacy_cache=args.allow_legacy_cache,
                              refresh_current_year=args.refresh_current_year, refresh_all=args.refresh_all,
                              empty_allowlist=read_empty_allowlist(args.empty_allowlist))
        build(collector, years=range(args.start_year, args.end_year + 1))
    except (OSError, ValueError) as error:
        print(f"[error] bundle not published: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
