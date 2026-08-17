#!/usr/bin/env bash
#
# Deploy 작명하우스 to Google Play internal testing via the
# "Android Internal Release" GitHub Actions workflow.
#
# What it does, hands-off:
#   1. Preflight: confirm we're on main and origin/main already contains the
#      local HEAD (so we don't silently deploy stale code).
#   2. Resolve versionName/versionCode: read the last *successful* run's
#      resolved version and bump versionCode by 1 (Play requires it to strictly
#      increase). versionName follows the house scheme MAJOR.MONTH.PATCH.
#   3. Trigger the workflow_dispatch on origin/main with those values.
#   4. Watch the run to completion and verify the Play upload actually happened.
#   5. On failure, print the failing step + log tail so triage is immediate.
#
# Usage:
#   deploy-internal.sh                       # full auto (recommended)
#   deploy-internal.sh --version-name 2.0.0  # override name, auto code
#   deploy-internal.sh --version-code 12     # override code
#   deploy-internal.sh --dry-run             # show the plan, don't trigger
#
# Requires: gh (authenticated) and a checkout of this repo.

set -euo pipefail

WORKFLOW="android-internal-release.yml"
BRANCH="main"
PLAY_SUCCESS_MARKER="Successfully finished the upload to Google Play"

# versionCode already consumed on Play before CI existed. 2026-08-17 에 로컬
# 빌드로 2 (1.1) 를 내부 트랙에 수동 업로드했다. CI 성공 이력이 아직 없을 때
# (= 첫 자동 배포) 여기서부터 +1 해서 3 으로 시작한다. CI 성공 이력이 한 번이라도
# 생기면 이 값은 더 쓰이지 않으니 갱신할 필요 없다.
BASELINE_VERSION_CODE=2
BASELINE_VERSION_NAME="1.1"

vn_override=""
vc_override=""
dry_run=0

while [ $# -gt 0 ]; do
  case "$1" in
    --version-name) vn_override="${2:-}"; shift 2 ;;
    --version-code) vc_override="${2:-}"; shift 2 ;;
    --dry-run)      dry_run=1; shift ;;
    -h|--help)      sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

note() { printf '%s\n' "$*" >&2; }

# --- Preflight: branch + sync state ------------------------------------------
cur_branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$cur_branch" != "$BRANCH" ]; then
  note "현재 브랜치가 '$cur_branch'입니다. '$BRANCH'에서 실행하세요."
  exit 1
fi

git fetch -q origin "$BRANCH" || true
ahead="$(git rev-list --count "origin/$BRANCH..HEAD" 2>/dev/null || echo 0)"
if [ "$ahead" -gt 0 ]; then
  note "로컬 $BRANCH 이 origin보다 $ahead 커밋 앞서 있습니다(미푸시)."
  note "배포는 origin/$BRANCH 기준이므로 이 커밋들은 포함되지 않습니다."
  note "포함하려면: git push origin $BRANCH  후 다시 실행하세요."
  exit 1
fi

dirty="$(git status --porcelain --untracked-files=no | wc -l | tr -d ' ')"
if [ "$dirty" -gt 0 ]; then
  note "참고: 커밋되지 않은 변경 $dirty 건이 있습니다 — 배포에는 포함되지 않습니다."
fi

# --- Resolve version from the last successful run ----------------------------
note "직전 성공 배포에서 버전 정보 조회 중..."
# `|| true`: 워크플로우가 아직 기본 브랜치에 없으면 gh 가 404 로 죽는다(첫 배포).
# 그 경우는 아래 BASELINE 경로가 받아 주므로 여기서 종료하면 안 된다.
last_id="$(gh run list --workflow="$WORKFLOW" --status success --limit 1 \
  --json databaseId --jq '.[0].databaseId // empty' 2>/dev/null || true)"

last_vn=""
last_vc=""
if [ -n "$last_id" ]; then
  # Match the resolved *output* line (versionCode is digits), not the step's
  # echoed command line (which prints the literal "...versionCode=$CODE").
  resolved="$(gh run view "$last_id" --log 2>/dev/null \
    | grep -m1 -E 'Resolved versionName=[^ ]+ versionCode=[0-9]+' || true)"
  if [ -n "$resolved" ]; then
    last_vn="$(printf '%s' "$resolved" | sed -E 's/.*versionName=([^ ]+) versionCode=[0-9]+.*/\1/')"
    last_vc="$(printf '%s' "$resolved" | sed -E 's/.*versionCode=([0-9]+).*/\1/')"
    # guard: if parsing went sideways, treat as unknown rather than feeding
    # a non-numeric value into the arithmetic below.
    printf '%s' "$last_vc" | grep -qE '^[0-9]+$' || { last_vc=""; last_vn=""; }
  fi
fi

# 첫 자동 배포 — CI 성공 이력이 없으면 수동 업로드분(BASELINE)을 기준으로 잡는다.
baseline_used=0
if [ -z "$last_vc" ]; then
  last_vc="$BASELINE_VERSION_CODE"
  last_vn="$BASELINE_VERSION_NAME"
  baseline_used=1
fi

if [ -n "$vc_override" ]; then
  vc="$vc_override"; vc_src="(지정)"
elif [ "$baseline_used" -eq 1 ]; then
  vc=$(( last_vc + 1 )); vc_src="(수동 업로드분 $last_vc +1, CI 첫 배포)"
else
  vc=$(( last_vc + 1 )); vc_src="(직전 $last_vc +1)"
fi

# Version-name scheme: MAJOR.MONTH.PATCH (삼라·마이타로와 동일)
#   MAJOR = 연도-2025 (2026→1, 2027→2). 대격변은 --version-name 수동 상향으로,
#           직전 배포의 MAJOR가 날짜 유도값보다 크면 그 값을 유지한다.
#   MONTH = 달력 월 (8월→8). 달이 바뀌면 PATCH는 0으로 리셋.
#   PATCH = 같은 달 안의 배포 순번 (직전과 MAJOR.MONTH가 같으면 +1).
scheme_version() {
  local last="$1" year month major minor patch l_major l_minor l_patch
  year="$(date +%Y)"; month="$(date +%-m)"
  major=$(( year - 2025 )); minor="$month"; patch=0
  if printf '%s' "$last" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    l_major="${last%%.*}"
    l_minor="$(printf '%s' "$last" | cut -d. -f2)"
    l_patch="${last##*.}"
    # 대격변 수동 상향분은 날짜 유도값보다 크면 존중한다.
    [ "$l_major" -gt "$major" ] && major="$l_major"
    if [ "$l_major" -eq "$major" ] && [ "$l_minor" -eq "$minor" ]; then
      patch=$(( l_patch + 1 ))
    fi
  fi
  printf '%s.%s.%s' "$major" "$minor" "$patch"
}

if [ -n "$vn_override" ]; then
  vn="$vn_override"; vn_src="(지정)"
else
  # BASELINE 의 "1.1" 은 2자리라 스킴 정규식에 안 걸린다 — 의도된 동작이다.
  # 첫 배포는 날짜에서 유도한 값(2026-08 → 1.8.0)으로 새로 시작한다.
  vn="$(scheme_version "$last_vn")"
  if [ "$baseline_used" -eq 1 ]; then
    vn_src="(스킴 연.월.순번 — 수동분 $last_vn 이후 첫 CI 배포)"
  else
    vn_src="(직전 $last_vn → 스킴 연.월.순번)"
  fi
fi

head_sha="$(git rev-parse --short HEAD)"
note ""
note "배포 계획"
note "  워크플로우 : $WORKFLOW (workflow_dispatch, ref=$BRANCH)"
note "  versionName: $vn  $vn_src"
note "  versionCode: $vc  $vc_src"
note "  배포 대상   : origin/$BRANCH (HEAD=$head_sha)"
note ""

if [ "$dry_run" -eq 1 ]; then
  note "(--dry-run) 트리거하지 않고 종료합니다."
  printf 'version_name=%s\nversion_code=%s\n' "$vn" "$vc"
  exit 0
fi

# --- Trigger -----------------------------------------------------------------
prev_top="$(gh run list --workflow="$WORKFLOW" --limit 1 \
  --json databaseId --jq '.[0].databaseId // empty')"
gh workflow run "$WORKFLOW" --ref "$BRANCH" \
  -f version_name="$vn" -f version_code="$vc"

# Find the new run: wait until the newest run id differs from the pre-trigger one.
run_id=""
for _ in $(seq 1 20); do
  sleep 3
  run_id="$(gh run list --workflow="$WORKFLOW" --limit 1 \
    --json databaseId --jq '.[0].databaseId // empty')"
  [ -n "$run_id" ] && [ "$run_id" != "$prev_top" ] && break
  run_id=""
done
if [ -z "$run_id" ]; then
  note "새 런 ID를 찾지 못했습니다. GitHub Actions 탭에서 직접 확인하세요."
  exit 1
fi

run_url="$(gh run view "$run_id" --json url --jq .url)"
note "런 시작: $run_url"
note "완료까지 대기 중... (보통 ~7분)"

# `gh run watch --exit-status` has been observed to exit 0 even on failure,
# so don't trust its exit code — re-read the conclusion explicitly afterward.
gh run watch "$run_id" --exit-status --interval 20 >/dev/null 2>&1 || true
conclusion="$(gh run view "$run_id" --json conclusion --jq .conclusion)"

# --- Verify / triage ---------------------------------------------------------
# `gh run view --log` 는 런이 끝난 뒤에도 한동안 **불완전한** 로그를 준다 —
# GitHub 이 스텝 로그를 사후 집계하기 때문. 삼라에서 실제로 "런은 success 인데
# 업로드 마커 없음" 오탐이 났고(배포는 정상), 몇 분 뒤 같은 grep 이 매치됐다.
#
# ⚠️ 오탐 원인은 ANSI 색코드가 아니다 — fastlane 이 색을 입혀도 마커 문자열
#    자체는 끊기지 않아 `grep -F` 가 정상 매치한다. 원인은 오직 로그 집계 지연.
verify_play_upload() {
  local id="$1" attempts=24 delay=5 i log
  for i in $(seq 1 "$attempts"); do
    log="$(gh run view "$id" --log 2>/dev/null || true)"
    if printf '%s' "$log" | grep -qF "$PLAY_SUCCESS_MARKER"; then
      return 0
    fi
    [ "$i" -lt "$attempts" ] && sleep "$delay"
  done
  return 1
}

if [ "$conclusion" = "success" ]; then
  if verify_play_upload "$run_id"; then
    note ""
    note "[성공] Play 내부 테스트 트랙 업로드 확인됨"
  else
    note ""
    note "[주의] 런은 success지만 2분 안에 Play 업로드 마커를 못 찾았습니다."
    note "        보통 로그 집계 지연이며 배포는 성공했을 가능성이 높습니다. 아래로 직접 확인하세요:"
    note "          gh run view $run_id --log | grep -F '$PLAY_SUCCESS_MARKER'"
  fi
  note "   versionName=$vn  versionCode=$vc"
  note "   $run_url"
  exit 0
fi

note ""
note "[실패] conclusion=$conclusion"
note "   $run_url"
note ""
note "실패 단계:"
gh run view "$run_id" --json jobs \
  --jq '.jobs[].steps[] | select(.conclusion=="failure") | "  - " + .name' >&2 || true
note ""
note "실패 로그(마지막 40줄):"
gh run view "$run_id" --log-failed 2>/dev/null | tail -40 >&2 || true
exit 1
