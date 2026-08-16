#!/usr/bin/env python3
"""③ 격자 스냅 — 4dp 격자를 벗어난 간격값을 토큰으로 옮긴다.

    python3 tools/design-snap.py            # 미리보기
    python3 tools/design-snap.py --apply

②(동일값 치환)와 달리 **시각 변화가 있는 유일한 단계**다. 되돌릴 수 있게
반드시 별도 커밋으로 둘 것.

규칙: 가장 가까운 4dp 격자값, 같으면 올림.

    3->4  5->4  6->8  7->8  9->8  10->12  11->12  14->16  18->20  22->24

"같으면 올림"을 고른 근거는 취향이 아니라 2026-08-16 실측이다. 이 앱은
8dp 이하 마이크로가 56.5%(material3 샘플 25.3%), 24dp 이상 매크로가
3.3%(7.2%)로 마이크로에 심하게 쏠려 있었다. 빈도 상위인 6·7·10 이 전부
올림 쪽이라 순효과는 화면이 헐거워지는 방향이다.
"""
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(REPO, 'app/src/main/java/com/naminghouse/app')

SNAP = {3: 4, 5: 4, 6: 8, 7: 8, 9: 8, 10: 12, 11: 12, 14: 16, 18: 20, 22: 24}

# 격자 밖이 정당한 자리 — 스냅하지 않는다.
#   1dp 는 글줄 광학 정렬 보정이라 격자를 따르면 오히려 틀어진다.
KEEP = {1}

CTX = re.compile(r'(padding\s*\(|spacedBy\s*\(|'
                 r'Spacer\s*\(\s*Modifier\.(?:height|width)\s*\(|PaddingValues\s*\()')
# 룩비하인드 필수 — `1.2.dp` 의 "2.dp" 를 간격값으로 오독하면 안 된다.
DP = re.compile(r'(?<![\d.])(\d+)\.dp')


def call_span(src, i):
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


def main():
    apply = '--apply' in sys.argv
    changes = []
    for base, _, names in os.walk(ROOT):
        if os.sep + 'theme' in base:
            continue
        for name in sorted(names):
            if not name.endswith('.kt'):
                continue
            path = os.path.join(base, name)
            src = open(path).read()
            edits = []
            for m in CTX.finditer(src):
                a, b = call_span(src, m.end() - 1)
                for dm in DP.finditer(src, a, b):
                    v = int(dm.group(1))
                    if v in KEEP or v not in SNAP:
                        continue
                    line = src.count('\n', 0, dm.start()) + 1
                    edits.append((dm.start(), dm.end(), f'InkSpace.s{SNAP[v]}'))
                    changes.append((os.path.relpath(path, REPO), line, v, SNAP[v]))
            if edits and apply:
                out = src
                for a, b, rep in sorted(set(edits), reverse=True):
                    out = out[:a] + rep + out[b:]
                open(path, 'w').write(out)

    by_pair = {}
    for _, _, v, t in changes:
        by_pair[(v, t)] = by_pair.get((v, t), 0) + 1
    print(('APPLIED' if apply else 'DRY RUN') + f'  {len(changes)}건')
    for (v, t), n in sorted(by_pair.items()):
        print(f'  {v:>3}dp -> {t:>3}dp  × {n}')
    if not apply:
        print()
        for f, ln, v, t in changes:
            print(f'  {f}:{ln}  {v} -> {t}')


if __name__ == '__main__':
    main()
