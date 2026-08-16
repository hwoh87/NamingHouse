#!/usr/bin/env python3
"""조형 규율 검사 — 간격 토큰이 조용히 되돌아가는 것을 막는다.

    python3 tools/design-guard.py          # 검사 (실패 시 exit 1)
    python3 tools/design-guard.py --report # 분포까지 전부 출력

간격 문맥(padding · spacedBy · Spacer · PaddingValues)만 본다.
size() · RoundedCornerShape() · strokeWidth 는 간격이 아니라서 세지 않는다.

기준선은 2026-08-16 감사 결과다. material3-1.3.1 샘플 소스를 같은
스크립트로 집계해 대조했고, 그때 이 앱은 격자 이탈 44%(레퍼런스 3.6%),
간격 토큰 준수율 0% 였다.
"""
import collections
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(REPO, 'app/src/main/java/com/naminghouse/app')

# InkSpace 가 담당하는 값. 이 값을 원시 dp 로 다시 쓰면 토큰이 무의미해진다.
TOKENISED = {2, 4, 8, 12, 16, 20, 24, 28}

# 격자 이탈 허용 상한 — 래칫. 줄이는 건 언제든 환영, 늘리는 건 실패.
# 2026-08-16 감사 직후 62건 -> 같은 날 ③ 스냅으로 0건. 잠갔다.
# 이제 격자 밖 간격값은 하나도 못 들어온다. 정당한 예외가 생기면 값을
# KEEP 에 넣고 왜 격자 밖이어야 하는지 여기 남길 것.
OFFGRID_BASELINE = 0

# 격자 밖이 정당한 자리. 광학 정렬 보정과 헤어라인은 격자를 따르면 오히려 틀어진다.
OFFGRID_EXEMPT = {1}

CTX = re.compile(r'(padding\s*\(|spacedBy\s*\(|'
                 r'Spacer\s*\(\s*Modifier\.(?:height|width)\s*\(|PaddingValues\s*\()')
# 룩비하인드 필수 — 없으면 `1.2.dp` 의 "2.dp" 를 간격값으로 오독한다.
DP = re.compile(r'(?<![\d.])(\d+)\.dp')
TOKEN = re.compile(r'InkSpace\.[A-Za-z0-9]+')
RAW_SP = re.compile(r'fontSize\s*=\s*(?<![\d.])\d+(?:\.\d+)?\.sp')
RAW_SHAPE = re.compile(r'RoundedCornerShape\s*\(')
ARGB = re.compile(r'0x[fF][fF][0-9a-fA-F]{6}')


def call_span(src, i):
    """`(` 위치에서 짝이 맞는 `)` 까지."""
    depth = 0
    j = i
    while j < len(src):
        if src[j] == '(':
            depth += 1
        elif src[j] == ')':
            depth -= 1
            if depth == 0:
                return i, j + 1
        j += 1
    return i, min(i + 120, len(src))


def palette_dupes():
    """팔레트에 이미 있는 색값을 다른 파일에서 리터럴로 다시 적었는지 본다.

    2026-08-16 이전에는 감명서 PDF 가 같은 16개 값을 손으로 베껴 갖고 있었다.
    팔레트만 고치면 화면은 바뀌고 유료 PDF 는 그대로 남는 구조였다.
    """
    theme = os.path.join(ROOT, 'ui/theme/Theme.kt')
    known = {c.lower() for c in ARGB.findall(open(theme).read())}
    hits = []
    for path in sources():
        src = open(path).read()
        for i, line in enumerate(src.split('\n'), 1):
            for c in ARGB.findall(line):
                if c.lower() in known:
                    hits.append((os.path.relpath(path, REPO), i, c))
    return hits


def sources():
    for base, _, names in os.walk(ROOT):
        if os.sep + 'theme' in base:      # 토큰 정의 파일은 대상이 아니다
            continue
        for n in sorted(names):
            if n.endswith('.kt'):
                yield os.path.join(base, n)


def scan():
    raw = collections.Counter()
    violations = []        # 토큰이 있는데 원시로 쓴 자리
    offgrid = []
    tokens = 0
    other = collections.Counter()
    for path in sources():
        src = open(path).read()
        rel = os.path.relpath(path, REPO)
        line_of = lambda p: src.count('\n', 0, p) + 1
        for m in CTX.finditer(src):
            a, b = call_span(src, m.end() - 1)
            body = src[a:b]
            tokens += len(TOKEN.findall(body))
            for dm in DP.finditer(body):
                v = int(dm.group(1))
                raw[v] += 1
                where = (rel, line_of(a + dm.start()), v)
                if v in TOKENISED:
                    violations.append(where)
                elif v % 4 and v not in OFFGRID_EXEMPT:
                    offgrid.append(where)
        other['raw_sp'] += len(RAW_SP.findall(src))
        other['raw_shape'] += len(RAW_SHAPE.findall(src))
    return raw, tokens, violations, offgrid, other


def main():
    report = '--report' in sys.argv
    raw, tokens, violations, offgrid, other = scan()
    total = tokens + sum(raw.values())
    rate = 100 * tokens / total if total else 100.0
    failed = False

    print('[HARD] 토큰이 있는 값을 원시 dp 로 쓰지 않는다 '
          f'({"·".join(str(v) for v in sorted(TOKENISED))})')
    if violations:
        failed = True
        for f, ln, v in violations:
            print(f'         FAIL {f}:{ln}  {v}.dp -> InkSpace.s{v}')
    else:
        print('         PASS  0건')

    print(f'[HARD] 4dp 격자 이탈 {len(offgrid)}건 <= 기준선 {OFFGRID_BASELINE}건')
    if len(offgrid) > OFFGRID_BASELINE:
        failed = True
        print(f'         FAIL  기준선보다 {len(offgrid) - OFFGRID_BASELINE}건 늘었다')
    else:
        slack = OFFGRID_BASELINE - len(offgrid)
        print(f'         PASS{"" if not slack else f"  기준선보다 {slack}건 줄었다 — 상수를 낮춰 잠글 것"}')

    print(f'[INFO] 간격 토큰 준수율 {rate:.1f}%  (토큰 {tokens} / 원시 {sum(raw.values())})'
          '   · 2026-08-16 기준선 98.9%')
    dupes = palette_dupes()
    print('[HARD] Theme.kt 의 색값을 다른 파일에 리터럴로 다시 적지 않는다')
    if dupes:
        failed = True
        for f, ln, c in dupes:
            print(f'         FAIL {f}:{ln}  {c} — 팔레트를 참조할 것')
    else:
        print('         PASS  0건')

    print(f'[INFO] 원시 fontSize {other["raw_sp"]}건 · 원시 RoundedCornerShape '
          f'{other["raw_shape"]}건   · 2026-08-16 기준선 5 / 9')

    if report:
        print('\n남은 원시 간격값:')
        for v, n in sorted(raw.items(), key=lambda x: -x[1]):
            tag = ' 격자이탈' if v % 4 and v not in OFFGRID_EXEMPT else ''
            print(f'  {v:>3}dp × {n}{tag}')
        if offgrid:
            print('\n격자 이탈 자리:')
            for f, ln, v in sorted(offgrid):
                print(f'  {f}:{ln}  {v}.dp')

    print('\n' + ('실패 — 위 [HARD] 항목을 고칠 것' if failed else '통과'))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
