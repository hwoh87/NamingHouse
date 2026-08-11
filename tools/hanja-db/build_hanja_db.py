#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_hanja_db.py — NamingHouse 인명용 한자 DB 생성기

engine/src/main/assets/hanja.tsv 를 재생성한다. (stdlib 만 사용, pip 불필요)

    python3 tools/hanja-db/build_hanja_db.py

소스 (자동 다운로드, tools/hanja-db/raw/ 에 캐시):
  1. 대법원 인명용 한자 charset  — rutopio/Korean-Name-Hanja-Charset data-gov.csv (MIT)
     -> 어떤 한자가 인명용인지 + 공식 음(reading). 한 한자가 여러 음 아래 나올 수 있다(모두 유지).
  2. Unihan (Unicode UCD)        — Unihan_IRGSources.txt 의 kTotalStrokes(필획), kRSUnicode(강희부수.잔여획)
  3. libhangul hanja.txt (BSD-3) — (음, 한자) -> 짧은 한국어 훈음
  4. (검증 전용) yumikang/saju hanja_dict.csv — 라이선스 불명이므로 데이터 재사용 없이 교차검증만 수행

계산:
  필획   = kTotalStrokes (Unihan 원본 값. 주의: 艹=3획, 阝=2획 등 중국식 계수 —
           한국 옥편 필획과 일부 다를 수 있으나 원획 계산에는 영향 없음)
  원획   = 수리성명학용 획수. 부수가 변형(축약)형으로 쓰인 경우 정자(canonical) 획수로 환산:
           원획 = 잔여획 + 정자부수획  (변형형 사용 시)
           예: 洙 = 9(氵3) -> 6 + 4 = 10,  道 = 12(辶3) -> 9 + 7 = 16
           숫자 예외: 四=4 五=5 六=6 七=7 八=8 九=9 十=10 (一二三은 그대로 1/2/3)
  자원오행 = 강희부수 -> 오행 표 (아래 RADICAL_ELEMENT, 근거 주석 포함)
           + 천간/지지/숫자/일부 글자의 글자단위 예외 (CHAR_ELEMENT_OVERRIDES)

  namefit  = 인명 적합도 0~4. kIICore/kUnihanCore2020 의 한국(K) 코어 여부 + 홍콩 학년.
             벽자(汏·瘰·刲) 는 0, 이름에 쓰는 글자(洙·珉·玗)는 2 이상이 된다.
  avoid    = 훈(뜻)이 이름에 부적절하면 1 (탐할·입술·어찌·이체자 표기 등).
             전통 불용한자와는 별개 개념 — 그쪽은 앱의 BulyongHanja.kt 담당.

출력: engine/src/main/assets/hanja.tsv
  hanja \t readings \t wonhoek \t pilhoek \t element \t meaning \t namefit \t avoid
"""
import csv
import io
import os
import re
import sys
import zipfile
import urllib.request
from collections import Counter, OrderedDict, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT_TSV = os.path.join(PROJECT, "engine", "src", "main", "assets", "hanja.tsv")

SOURCES = {
    "data-gov.csv": "https://raw.githubusercontent.com/rutopio/Korean-Name-Hanja-Charset/main/data-gov.csv",
    "hanja.txt": "https://raw.githubusercontent.com/libhangul/libhangul/master/data/hanja/hanja.txt",
    "Unihan.zip": "https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip",
    # 교차검증 전용(데이터 재사용 금지 — 라이선스 불명):
    "crosscheck_hanja_dict.csv": "https://raw.githubusercontent.com/yumikang/saju/main/data_migration/hanja_dict.csv",
    "crosscheck_hanja_full.json": "https://raw.githubusercontent.com/yumikang/saju/main/backups/hanja-dict-full-2025-10-31.json",
}


def fetch_if_missing():
    os.makedirs(RAW, exist_ok=True)
    for name, url in SOURCES.items():
        path = os.path.join(RAW, name)
        if os.path.exists(path) and os.path.getsize(path) > 0:
            continue
        print(f"[download] {name} <- {url}")
        req = urllib.request.Request(url, headers={"User-Agent": "NamingHouse-hanja-db/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())


# ---------------------------------------------------------------------------
# 원획 환산표: 강희부수번호 -> (정자 획수, {허용 변형형 획수})
# 변형형 획수는 kTotalStrokes - 잔여획 으로 탐지한다. Unihan 이 중국식으로 계수한
# 阝(2획)·辶(3획)·艹(3획) 도 "정자보다 적게 쓰였다" 판정에 자연히 포함되도록
# `사용획 < 정자획` 이면 환산한다. (예: 防 6-4=2 -> 阜 8 로 환산 = 12)
# 반대로 정자보다 크게 계수된 경우(泰 10-5=5 > 水 4)는 환산하지 않는다.
# ---------------------------------------------------------------------------
WONHOEK_RADICALS = {
    85: 4,   # 水  氵(3) -> 4
    61: 4,   # 心  忄(3) -> 4  (⺗ 은 4획으로 계수되어 변화 없음)
    64: 4,   # 手  扌(3) -> 4
    94: 4,   # 犬  犭(3) -> 4
    96: 5,   # 玉  王(4) -> 5
    113: 5,  # 示  礻(4) -> 5
    130: 6,  # 肉  月(4) -> 6
    140: 6,  # 艸  艹(3 또는 4) -> 6
    145: 6,  # 衣  衤(5) -> 6
    122: 6,  # 网  罒(5)/罓(4) -> 6
    125: 6,  # 老  耂(4) -> 6
    162: 7,  # 辵  辶(3 또는 4) -> 7
    163: 7,  # 邑  우부방 阝(2 또는 3) -> 7
    170: 8,  # 阜  좌부변 阝(2 또는 3) -> 8
}

# 숫자 한자는 숫자 그 자체를 원획으로 본다 (수리성명학 통설).
WONHOEK_NUMERALS = {"四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}

# ---------------------------------------------------------------------------
# 자원오행: 강희부수(1..214) -> 오행. 확정 근거가 없는 부수는 None(빈값).
#
# 대조 소스(2026-08 확인):
#  [A] ksname.co.kr "한자의 부수 및 자원오행" (부수 전반)
#  [B] miso.co.kr / irum.com 성명학 이론 (오행별 예시자: 家->木 晟->火 達/都->土
#      玲/瑞/言/白->金 月/夕/善/鮮/雨->水 등)
#  [C] agiirum.com 좋은이름 작명법 (역사 인명 자원오행 분해: 金泳三=금수화,
#      裵克廉=목목목, 金炳國=금화수(國->水), 金大中=금목토, 黃喜=토수(喜->水),
#      徐命善=화수수(彳->火, 口->水), 韓致亨=금토토(至->土), 韓明澮=금화수(韋->金) 등)
#  [D] 5KLetsGo/saju_naming 데이터셋의 글자별 자원오행 라벨 806자(빈도 대조용)
#  [E] 우당작명연구원: 示부는 오행대전=金, 김성배 사전 2007 정정 이후 다수설=木
#
# 유파가 갈려 다수설을 채택한 부수: 心->火, 言->金, 玉->金, 月->水, 口->水,
# 囗->水, 石->金, 示->木, 人->火, 肉(육달월)->水
# ---------------------------------------------------------------------------
E = {"목": "木", "화": "火", "토": "土", "금": "金", "수": "水"}
RADICAL_ELEMENT = {
    1: None,        # 一 한일 — 추상 지사자. 유파마다 갈려 빈값 (숫자 글자는 글자단위 예외로 처리)
    2: None,        # 丨 뚫을곤 — 근거 없음
    3: None,        # 丶 점주 — 근거 없음
    4: None,        # 丿 삐침별 — 근거 없음
    5: "木",        # 乙 새을 — 천간 乙=木(을목). [D] 木2:金1
    6: None,        # 亅 갈고리궐 — 근거 없음
    7: None,        # 二 두이 — 근거 없음
    8: None,        # 亠 돼지해머리 — [A] 화/토/수 혼재. 빈값
    9: "火",        # 人 사람인 — 유파 갈림. [A] 人/亻->火, [D] 火25:木1 로 다수설 火
    10: None,       # 儿 어진사람인 — 克->木[C], 光->火[B], [D] 土. 3파전이라 빈값
    11: None,       # 入 들입 — 근거 약함
    12: None,       # 八 여덟팔 — 부수로는 빈값 (八 글자는 숫자 예외 金)
    13: None,       # 冂 멀경 — 근거 없음
    14: None,       # 冖 민갓머리 — 근거 없음
    15: "水",       # 冫 이수변 — 얼음=水. [A][D]
    16: None,       # 几 안석궤 — [A] 木/水 갈림. 빈값
    17: None,       # 凵 입벌릴감 — 근거 없음
    18: "金",       # 刀 칼도 — 병장기=金. [A][B][D]
    19: None,       # 力 힘력 — [A] 오행 전부에 등장(문맥형). 빈값
    20: None,       # 勹 쌀포 — 근거 약함
    21: "金",       # 匕 비수비 — 비수(칼)=金. [A]
    22: None,       # 匚 상자방 — [A] 목/토/수 혼재. 빈값
    23: None,       # 匸 감출혜 — 근거 없음
    24: None,       # 十 열십 — 문맥형[A]. 빈값 (十 글자는 숫자 예외 水)
    25: None,       # 卜 점복 — [A] 목/화/토 혼재. 빈값
    26: None,       # 卩 병부절 — 근거 없음
    27: "土",       # 厂 민엄호 — 언덕/굴바위=土. [A][D]
    28: None,       # 厶 마늘모 — 근거 없음
    29: None,       # 又 또우 — [A] 금/수 갈림. 빈값
    30: "水",       # 口 입구 — 다수설 水. [C] 喜/命/善->水, [D] 水25:0
    31: "水",       # 囗 큰입구몸 — [C] 國->水(金炳國=금화수), [D] 水4:0
    32: "土",       # 土 흙토
    33: "木",       # 士 선비사 — 선비/학문=木. [A][D] (천간 壬 등은 글자단위 예외)
    34: None,       # 夂 뒤쳐올치 — 근거 없음
    35: None,       # 夊 천천히걸을쇠 — 근거 없음
    36: "水",       # 夕 저녁석 — 밤=음=水. [B] 夕->水, [D] 水3:0
    37: "木",       # 大 큰대 — [C] 金大中=금목토(大->木), [D] 木 우세
    38: "土",       # 女 계집녀 — [A][C] 姜->土, [D] 土25:0
    39: "水",       # 子 아들자 — 지지 子=水(자수). [A][D]
    40: "木",       # 宀 갓머리 — 집=木. [B] 家->木, [D] 木5 우세
    41: "木",       # 寸 마디촌 — [A] 木 병기, [D] 木2:0. 저신뢰
    42: None,       # 小 작을소 — [A] 금/수 갈림. 빈값
    43: None,       # 尢 절름발이왕 — 근거 없음
    44: "水",       # 尸 주검시 — 尹(성씨)->水 관행. [A] 수 병기, [D] 갈림. 저신뢰
    45: "木",       # 屮 싹날철 — 새싹=木. [A]
    46: "土",       # 山 뫼산 — [A][B][C][D]
    47: "水",       # 巛 개미허리(川) — 내=水. [A][D]
    48: "火",       # 工 장인공 — [A]. 저신뢰
    49: "土",       # 己 몸기 — 천간 己=土(기토). [A] (巳 글자는 지지 예외 火)
    50: "木",       # 巾 수건건 — 천/직물=木. [A][D]
    51: "木",       # 干 방패간 — 방패=나무. [A] (천간 庚 등 글자단위 예외)
    52: "火",       # 幺 작을요 — [A][D]. 저신뢰
    53: "木",       # 广 엄호 — 집=木. [A][C] 廉->木, [D]
    54: "木",       # 廴 민책받침 — [C] 柳廷顯=목목화(廷->木)
    55: None,       # 廾 받들공 — [A] 혼재. 빈값
    56: "金",       # 弋 주살익 — 무기=金
    57: "金",       # 弓 활궁 — 병장기=金. [D] 金 우세. 저신뢰
    58: None,       # 彐 돼지머리계 — 근거 약함
    59: None,       # 彡 터럭삼 — 彬->木 vs 彰/影->火 관행 갈림. 빈값
    60: "火",       # 彳 두인변 — [A][C] 徐->火, [D] 火3:0
    61: "火",       # 心/忄 마음심 — 심장=火 다수설. [A][B] 憬->火, [D] 火26:0
    62: "金",       # 戈 창과 — 병장기=金. [A][D] (천간 戊/戌 글자단위 예외 土)
    63: "木",       # 戶 지게호 — 문짝=나무. [A]
    64: "木",       # 手/扌 손수 — [A] 手->木, [D] 木25:1
    65: "土",       # 支 지탱할지 — [A]. 저신뢰
    66: "金",       # 攴/攵 등글월문 — 치다=金. [A][D] 金8:0
    67: "木",       # 文 글월문 — 학문=木. [A][C] 曺錫文=토금목(文->木)
    68: "火",       # 斗 말두 — 북두(별)=火. [A]. 저신뢰
    69: "金",       # 斤 도끼근 — [A][D]
    70: "土",       # 方 모방 — 방위=土. [A][D] 土2:0
    71: None,       # 无 없을무 — 근거 없음
    72: "火",       # 日 날일 — 태양=火. [A][B][D] 火29:0
    73: None,       # 曰 가로왈 — [A] 오행 전부(문맥형). 빈값 (曺 글자는 예외 土[C])
    74: "水",       # 月 달월 — 달=음=水. [B] 月->水, [D] 水3:0
    75: "木",       # 木 나무목
    76: "火",       # 欠 하품흠 — 欣->火 관행. [A][D]. 저신뢰
    77: "土",       # 止 그칠지 — 머무름=土. [A][D]
    78: "水",       # 歹 죽을사변 — 죽음=음=水. [A]
    79: "金",       # 殳 갖은등글월문 — 병장기=金. [A][D]
    80: "土",       # 毋 말무 — 母=土. [A]
    81: None,       # 比 견줄비 — 근거 없음
    82: "火",       # 毛 터럭모 — [A][D]. 저신뢰
    83: "火",       # 氏 각시씨 — 民->火[A], [D]. 저신뢰
    84: "水",       # 气 기운기 — 기운/수증기=水. [A][D]
    85: "水",       # 水/氵 물수
    86: "火",       # 火/灬 불화
    87: "木",       # 爪 손톱조 — 손(手=木) 계열. 저신뢰
    88: "木",       # 父 아비부 — [A][D]. 저신뢰
    89: None,       # 爻 점괘효 — 근거 없음
    90: "木",       # 爿 장수장변 — 나무 조각널=木
    91: "木",       # 片 조각편 — 나무 조각=木. [A][D]
    92: None,       # 牙 어금니아 — 근거 약함. 빈값
    93: "土",       # 牛 소우 — 축토(丑土) 가축=土. [A][D] 土3:0
    94: "土",       # 犬/犭 개견 — 술토(戌土)=土. [A][D]
    95: "水",       # 玄 검을현 — 검은색=水. [A][D]
    96: "金",       # 玉/王 구슬옥 — 玉은 金으로 보는 표가 다수. [A][B] 玲/瑞->金, [D] 金42:0
    97: "木",       # 瓜 오이과 — 식물=木. [A][D]
    98: "土",       # 瓦 기와와 — 흙으로 구움=土. [D]
    99: "土",       # 甘 달감 — 단맛=土(중앙). [A]
    100: "木",      # 生 날생 — 자라남=木. [A]
    101: "水",      # 用 쓸용 — [A]. 저신뢰
    102: "土",      # 田 밭전 — [A][B][D] (甲/申 글자는 천간지지 예외)
    103: None,      # 疋 필필 — 근거 없음
    104: "水",      # 疒 병질엄 — [A][D]
    105: None,      # 癶 필발머리 — [A] 화/수 갈림. 빈값 (癸 글자는 천간 예외 水)
    106: "金",      # 白 흰백 — 흰색=金(서방). [B] 白->金, [D] 金4:1
    107: "金",      # 皮 가죽피 — 벗겨냄=金. [A]. 저신뢰
    108: None,      # 皿 그릇명 — [A] 오행 전부, [D] 갈림. 빈값
    109: "木",      # 目 눈목 — [A][D] 木7:0
    110: "金",      # 矛 창모 — 병장기=金. [A]
    111: "金",      # 矢 화살시 — 병장기=金. [A][D]
    112: "金",      # 石 돌석 — 광물=金. [A][D] 金10:0 (土로 보는 소수설 있음: brunch 표)
    113: "木",      # 示/礻 보일시 — 김성배 사전 2007 정정 이후 다수설 木 [E] (오행대전=金 소수설)
    114: None,      # 禸 짐승발자국유 — 근거 없음
    115: "木",      # 禾 벼화 — 곡물=木. [A][B] 秀/積->木, [D]
    116: "水",      # 穴 구멍혈 — 샘/굴=水. [A][D]
    117: "金",      # 立 설립 — [A] 金 병기, [D] 金 우세. 저신뢰
    118: "木",      # 竹 대죽 — [A][D]
    119: "木",      # 米 쌀미 — 곡물=木. [A][D]
    120: "木",      # 糸 실사 — 실/직물=木. [A][B] 綱->木, [D] 木21:1
    121: "土",      # 缶 장군부 — 질그릇=土. [A][D]
    122: "木",      # 网/罒 그물망 — 그물=실=木. [A]
    123: "土",      # 羊 양양 — 미토(未土) 가축=土. [A][D]
    124: "火",      # 羽 깃우 — 날짐승=火. [A][D] 火5:1
    125: "土",      # 老/耂 늙을로 — [A][D]
    126: "水",      # 而 말이을이 — [A]. 저신뢰
    127: "土",      # 耒 가래뢰 — 밭갈이=土. [A] 토 병기, [D] 土2:0
    128: "火",      # 耳 귀이 — 聰->火 관행. [A][D]
    129: "木",      # 聿 붓율 — 붓대=나무. 저신뢰
    130: "水",      # 肉/月(육달월) — 혈육/몸=水 다수설. [D] 水5:0
    131: None,      # 臣 신하신 — [A] 화/토/수 혼재. 빈값
    132: None,      # 自 스스로자 — 근거 없음
    133: "土",      # 至 이를지 — [A][C] 韓致亨=금토토(致->土)
    134: "土",      # 臼 절구구 — [A]
    135: "火",      # 舌 혀설 — 말=火. [A][D]. 저신뢰
    136: None,      # 舛 어그러질천 — 근거 없음
    137: "木",      # 舟 배주 — 배=나무. [A][D] 木3:1
    138: "土",      # 艮 괘이름간 — 간토(艮=山 괘)=土. [A]
    139: None,      # 色 빛색 — 근거 없음
    140: "木",      # 艸/艹 풀초 — [A][B][D] 木33:0
    141: "木",      # 虍 범호엄 — 호랑이=인목(寅木). [A][D]
    142: "水",      # 虫 벌레충 — [A][D] 水5:0
    143: "水",      # 血 피혈 — 체액=水. [A][D]
    144: "火",      # 行 다닐행 — [A][B] 行/術->火, [D]
    145: "木",      # 衣/衤 옷의 — 직물=木. [A][C] 裵->木, [D] 木5:0
    146: None,      # 襾 덮을아 — 근거 없음
    147: "火",      # 見 볼견 — [A][B] 見->火, [D]
    148: "木",      # 角 뿔각 — [A]
    149: "金",      # 言 말씀언 — 다수설 金. [A][B] 言/謨->金, [C] 許->金, [D] 金26:0
    150: "水",      # 谷 골곡 — 계곡물=水. [A][D]
    151: "木",      # 豆 콩두 — 곡물=木. [A][D]
    152: "水",      # 豕 돼지시 — 해수(亥水)=水. [A][D] 水2:0
    153: "水",      # 豸 갖은돼지시변 — [A]. 저신뢰
    154: "金",      # 貝 조개패 — 재화=金. [A][D] 金12:0
    155: "火",      # 赤 붉을적 — 붉은색=火. [A][B]
    156: "火",      # 走 달릴주 — [A][D] 火2:0
    157: "土",      # 足 발족 — 땅을 디딤=土. [A][D] 土13:0
    158: None,      # 身 몸신 — [A] 화/토/수 혼재. 빈값
    159: "火",      # 車 수레거 — [A][B] 車->火, [C] 載->火, [D] 火6:0
    160: "金",      # 辛 매울신 — 천간 辛=金(신금). [A][D]
    161: "土",      # 辰 별진 — 지지 辰=土(진토). [A]
    162: "土",      # 辵/辶 책받침 — [A][B] 達->土, [D] 土17:0
    163: "土",      # 邑/우부방阝 — [A][B] 都/邑->土, [D] 土7:0
    164: "金",      # 酉 닭유 — 지지 酉=金(유금). [A][D] 金7:1
    165: None,      # 釆 분별할변 — [A] 화/금, [D] 목 우세로 혼재. 빈값
    166: "土",      # 里 마을리 — 땅/마을=土. [A][D]
    167: "金",      # 金 쇠금
    168: "木",      # 長 길장 — 자람/성장=木. 저신뢰
    169: "木",      # 門 문문 — 문짝=나무. [A][D] 木4:1
    170: "土",      # 阜/좌부변阝 — 언덕=土. [A][D] 土11:0
    171: "水",      # 隶 미칠이 — [A][D]. 저신뢰
    172: "火",      # 隹 새추 — 날짐승=火. [A][D] 火4:0
    173: "水",      # 雨 비우 — [A][B][D] 水4:0
    174: "木",      # 靑 푸를청 — 푸른색=木(동방). [A][D]
    175: "水",      # 非 아닐비 — [A]. 저신뢰
    176: "火",      # 面 낯면 — [A]. 저신뢰
    177: "金",      # 革 가죽혁 — 무두질=金. [A][D] 金2:0
    178: "金",      # 韋 다룸가죽위 — [A][C] 韓->金
    179: "木",      # 韭 부추구 — 식물=木
    180: "金",      # 音 소리음 — 쇳소리=金. [A][D] 金2:0
    181: "火",      # 頁 머리혈 — [A][C] 顯->火, [D] 火8:0
    182: "木",      # 風 바람풍 — 바람=木. [A][D]
    183: "火",      # 飛 날비 — 날짐승=火. [A][D]
    184: "水",      # 食 밥식 — [A][D] 水7:0
    185: "水",      # 首 머리수 — [A]. 저신뢰
    186: "木",      # 香 향기향 — 곡물 향=木. [A][D]
    187: "火",      # 馬 말마 — 오화(午火)=火. [A][D] 火4:0
    188: "金",      # 骨 뼈골 — [A] 금 병기. 저신뢰
    189: "火",      # 高 높을고 — [A]. 저신뢰
    190: "火",      # 髟 터럭발 — [A][D]
    191: "金",      # 鬥 싸울투 — [A]
    192: "木",      # 鬯 울창주창 — [A][D]. 저신뢰
    193: None,      # 鬲 솥력 — 土(토기)/金(쇠솥) 갈림. 빈값
    194: "火",      # 鬼 귀신귀 — [A][D]
    195: "水",      # 魚 고기어 — [A][B] 鮮->水, [D] 水8:0
    196: "火",      # 鳥 새조 — 날짐승=火. [A][D] 火4:0
    197: "水",      # 鹵 소금밭로 — 소금=바다=水. 저신뢰
    198: "土",      # 鹿 사슴록 — [A][D]
    199: "木",      # 麥 보리맥 — 곡물=木. [A]
    200: "木",      # 麻 삼마 — 식물=木. [A]
    201: "土",      # 黃 누를황 — 누런색=土(중앙). [A][C] 黃->土
    202: "木",      # 黍 기장서 — 곡물=木. [A][D]
    203: "水",      # 黑 검을흑 — 검은색=水. [A][D]
    204: "木",      # 黹 바느질할치 — 실/자수=木. 저신뢰
    205: None,      # 黽 맹꽁이맹 — 水(물짐승)/土[A] 갈림. 빈값
    206: "火",      # 鼎 솥정 — [C] agiirum 예시 "솥정(鼎)"을 火 로 분류, [D] 火1
    207: "金",      # 鼓 북고 — [A][D]
    208: "水",      # 鼠 쥐서 — 자수(子水)=水. [A]
    209: None,      # 鼻 코비 — 근거 없음
    210: "土",      # 齊 가지런할제 — [A][D]
    211: "金",      # 齒 이치 — [A][D]
    212: "土",      # 龍 용룡 — 진토(辰土)=土. [A][D]
    213: "水",      # 龜 거북귀 — 물짐승=水. [A]
    214: None,      # 龠 피리약 — 火[A]/木(대나무 악기) 갈림. 빈값
}

# 글자 단위 예외 (부수 오행보다 우선):
#  - 천간/지지 글자는 그 자체가 오행 (모든 유파 공통)
#  - 숫자는 1·2=木 3·4=火 5·6=土 7·8=金 9·10=水 (하도 수리 — [A] 병기, [C] 金泳三의
#    三->火, 南九萬의 九->水 와 일치)
#  - 기타: 中->土 [A][C], 曺->土 [C]
CHAR_ELEMENT_OVERRIDES = {
    # 천간
    "甲": "木", "乙": "木", "丙": "火", "丁": "火", "戊": "土",
    "己": "土", "庚": "金", "辛": "金", "壬": "水", "癸": "水",
    # 지지
    "子": "水", "丑": "土", "寅": "木", "卯": "木", "辰": "土", "巳": "火",
    "午": "火", "未": "土", "申": "金", "酉": "金", "戌": "土", "亥": "水",
    # 숫자 (하도 수리)
    "一": "木", "二": "木", "三": "火", "四": "火", "五": "土",
    "六": "土", "七": "金", "八": "金", "九": "水", "十": "水",
    # 기타
    "中": "土", "曺": "土",
}


def valid_cjk(cp):
    """실제 부여된 CJK 한자 코드포인트인지 (URO/ExtA/ExtB+/호환한자)"""
    return (0x4E00 <= cp <= 0x9FFF or 0x3400 <= cp <= 0x4DBF
            or 0x20000 <= cp <= 0x323AF or 0xF900 <= cp <= 0xFAFF)


def load_court_readings(path):
    """data-gov.csv -> OrderedDict[hanja] = [readings...] (등장 순서 유지)

    - hangul 필드가 "령,영" 처럼 복수 음을 담는 행이 있으므로 콤마로 분리한다.
    - 유니코드에 없는 사설(placeholder) 코드포인트 행(0xA0xxx 등, 433행)은 제외한다.
    """
    readings = OrderedDict()
    skipped = 0
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            h = row["hanja"].strip()
            if len(h) != 1 or not valid_cjk(ord(h)):
                skipped += 1
                continue
            rlist = [r.strip() for r in row["hangul"].split(",") if r.strip()]
            if not rlist:
                skipped += 1
                continue
            readings.setdefault(h, [])
            for r in rlist:
                if r not in readings[h]:
                    readings[h].append(r)
    if skipped:
        print(f"[load] data-gov.csv: skipped {skipped} rows (비유니코드 placeholder 코드포인트/빈 음)")
    return readings


def load_unihan(zip_path):
    """Unihan.zip -> (strokes, radrec, common)

    common[cp] = (iicore, core2020, grade) — 상용도 판정 원자료.
      kIICore        국제 표의문자 코어. 우선순위(A/B/C) + 사용 지역 코드(G T J H K M P).
                     K 포함 = 한국에서 코어로 쓰는 글자.
      kUnihanCore2020 지역 코드만. 역시 K 포함 여부를 본다.
      kGradeLevel    홍콩 학년(1~6). 낮을수록 기초 한자.
    """
    strokes, radrec = {}, {}
    iicore, core2020, grade = {}, {}, {}
    with zipfile.ZipFile(zip_path) as z:
        for member in ("Unihan_IRGSources.txt", "Unihan_DictionaryLikeData.txt"):
            with z.open(member) as f:
                for line in io.TextIOWrapper(f, encoding="utf-8"):
                    if line.startswith("#") or not line.strip():
                        continue
                    parts = line.rstrip("\n").split("\t", 2)
                    if len(parts) < 3:
                        continue
                    cp_s, field, val = parts
                    cp = int(cp_s[2:], 16)
                    if field == "kTotalStrokes":
                        strokes[cp] = int(val.split()[0])
                    elif field == "kRSUnicode":
                        m = re.match(r"(\d+)('{0,3})\.(-?\d+)", val.split()[0])
                        if m:
                            radrec[cp] = (int(m.group(1)), int(m.group(3)))
                    elif field == "kIICore":
                        iicore[cp] = val.split()[0]
                    elif field == "kUnihanCore2020":
                        core2020[cp] = val.split()[0]
                    elif field == "kGradeLevel":
                        grade[cp] = int(val.split()[0])
    common = {cp: (iicore.get(cp, ""), core2020.get(cp, ""), grade.get(cp))
              for cp in set(iicore) | set(core2020) | set(grade)}
    return strokes, radrec, common


def name_fit(ch, common):
    """인명 적합도 0~4 — 벽자(僻字) 걸러내기용 상용도 점수.

    작명 후보 생성은 fit>=2(= 한국 코어 한자)만 쓴다. 洙·珉·玗 처럼 이름에만
    쓰이는 글자도 kIICore 에 K 가 있어 살아남고, 汏·瘰·刲·潏 같은 벽자는 0 이 된다.
    """
    if ch in FIT_OVERRIDES:
        return FIT_OVERRIDES[ch]
    ii, c2020, grade = common.get(ord(ch), ("", "", None))
    fit = 0
    if ii:
        fit += 2 if "K" in ii[1:] else 1     # 첫 글자는 우선순위(A/B/C), 나머지가 지역 코드
    if "K" in c2020:
        fit += 1
    if grade is not None and grade <= 4:
        fit += 1
    return min(fit, 4)


def load_meanings(path):
    """libhangul hanja.txt -> {(reading, hanja): meaning}, {hanja: fallback meaning}"""
    by_reading, fallback = {}, {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            parts = line.rstrip("\n").split(":")
            if len(parts) < 3:
                continue
            key, hanja, meaning = parts[0], parts[1], ":".join(parts[2:])
            if len(key) != 1 or len(hanja) != 1 or not ("가" <= key <= "힣"):
                continue
            meaning = clean_meaning(meaning)
            if not meaning:
                continue
            by_reading.setdefault((key, hanja), meaning)
            fallback.setdefault(hanja, meaning)
    return by_reading, fallback


def clean_meaning(m):
    """괄호 주석 제거, 첫 번째 항목만, 12자 이내로 정리"""
    m = re.sub(r"[（(][^)）]*[)）]", "", m)          # 괄호 주석 제거
    m = m.split(",")[0].split("，")[0]                # 첫 항목만
    m = re.sub(r"\s+", " ", m).strip()
    if len(m) > 12:
        cut = m[:12]
        if " " in cut:                                # 낱말 중간에서 자르지 않기
            cut = cut.rsplit(" ", 1)[0]
        m = cut.strip()
    return m


# ---------------------------------------------------------------------------
# 인명 기피 판정 — 훈(뜻)이 이름에 쓰기 부적절한 글자를 걸러낸다.
# 상용도(kIICore)로는 못 거른다: 叨(탐낼)·吻(입술)·奈(어찌) 는 흔한 글자지만 이름엔 안 쓴다.
# 불용한자(전통 속설)와는 별개다 — 그쪽은 BulyongHanja.kt 가 담당한다.
# ---------------------------------------------------------------------------
AVOID_SUBSTRINGS = (
    # 죽음·질병·상해
    "죽을", "죽일", "주검", "시체", "무덤", "상여", "장사지낼", "병들", "앓을", "종기",
    "부스럼", "학질", "문둥", "고름", "딱지", "사마귀", "곱사", "벙어리", "귀머거리",
    "연주창", "곪을", "부어오를",
    "장님", "소경", "애꾸", "절름발이", "대머리", "여윌", "굶주릴", "주릴", "목마를",
    # 악덕·범죄
    "도둑", "훔칠", "탐낼", "속일", "거짓", "간사", "아첨", "교활", "음란", "방탕",
    "창녀", "노예", "더러울", "저주", "꾸짖을", "욕할", "형벌", "감옥", "사로잡을",
    "미칠", "미치광이", "어리석", "악할", "사악", "흉할", "나쁠",
    # 재앙·파괴·쇠퇴
    "재앙", "재난", "허물", "근심", "걱정", "슬플", "슬퍼", "서러울", "원망", "원수",
    "미워할", "시기할", "질투", "두려울", "겁낼", "무서울", "괴로울", "고달플",
    "가난할", "궁할", "망할", "멸할", "무너질", "부술", "깨질", "썩을", "곰팡이",
    "게으를", "어지러울", "끊어질", "끊을", "시끄러울", "다툴", "싸울", "때릴", "찌를", "해칠", "독할",
    "잃을", "패할", "내칠", "버릴", "없앨", "그칠", "늙을", "어두울", "작을", "좁을",
    # 벌레·혐오 동물
    "벌레", "구더기", "지렁이", "두꺼비", "거머리", "쥐며느리",
    # 신체 내부·배설
    "입술", "겨드랑", "항문", "배꼽", "창자", "쓸개", "지라", "콩팥", "방광", "자궁",
    "젖꼭지", "볼기", "사타구니", "정강이", "종아리", "발꿈치", "팔꿈치", "힘줄",
    "똥", "오줌", "방귀", "가래", "침뱉을",
    # 어조사·기능어 (뜻이 비어 이름이 되지 않음)
    "어조사", "어찌", "이에", "하여금", "오히려", "누구", "어느", "무엇", "아닐", "없을",
)
# 훈이 한 낱말뿐이거나 다른 훈의 부분문자열이라 부분일치로는 오탐이 나는 글자
# (예: '천할'은 徐 '천천할'에, '때'는 時 '때'에 걸린다) — 정확히 일치할 때만 기피.
AVOID_EXACT = frozenset((
    "울", "죽", "병", "독", "종", "옥", "첩", "탈", "흠", "티", "바", "천할", "탐할",
))
# 이체자·약자 표기(예: '年의 本字') — 이름의 대표자로 쓰지 않는다.
VARIANT_MARKERS = ("本字", "俗字", "古字", "略字", "同字", "本音", "訛字")
# 사전 뜻풀이가 이체자 안내뿐이지만 실제로는 인명에 널리 쓰이는 글자 — 기피 대상에서 제외.
# 훈이 우연히 통과 목록에 걸리지만 실제 인명에는 거의 안 쓰는 글자들
FORCE_AVOID = frozenset(("塗", "坰", "坮", "岵", "汎", "陳", "島", "潞", "蘿", "樵", "枏"))
AVOID_EXEMPT = frozenset(("彦", "姫", "眞", "淸", "峯", "昤", "晳", "喆"))


# ---------------------------------------------------------------------------
# 인명 적합 훈(뜻) 화이트리스트.
#
# 상용도(kIICore)만으로는 蔬(나물)·嗽(기침할)·蜘(거미)·汗(땀) 같은 "흔하지만 이름엔
# 안 쓰는" 글자를 못 거른다. 실제 인명 한자는 뜻이 뚜렷하게 좋거나(밝을·어질·빛날)
# 자연·기물의 아름다운 대상(구슬·별·바다·소나무)에 몰려 있으므로, 기피 목록을 늘리는
# 대신 통과 목록을 두는 편이 정밀도가 훨씬 높다. 재현율은 NAME_CHAR_WHITELIST 로 보완.
# ---------------------------------------------------------------------------
NAME_HUN_KEYWORDS = (
    # 밝음·빛
    "밝을", "밝힐", "빛날", "빛깔", "비칠", "불꽃", "환할", "맑을", "깨끗할", "흴",
    "붉을", "푸를", "검을", "희고", "찬란할",
    # 인품·덕
    "착할", "슬기", "총명할", "지혜", "미쁠", "정성", "성실할", "곧을", "바를",
    "굳을", "굳셀", "씩씩할", "용감할", "부지런할", "힘쓸", "삼갈", "공손할", "온화할",
    "너그러울", "화할", "화평할", "화목할", "편안할", "고요할", "조용할", "너그러",
    "효도", "충성", "의로울", "옳을", "예도", "예의", "믿을", "사랑", "은혜", "도울",
    "베풀", "구제할", "기를", "지킬", "보전할", "맡길", "두터울", "도타울", "후할",
    # 성취·번영
    "이룰", "이룩할", "세울", "일으킬", "성할", "창성할", "흥할", "번성할", "융성할",
    "나아갈", "오를", "통할", "형통할", "펼", "다스릴", "넉넉할", "풍성할",
    "가득할", "채울", "많을", "복", "길할", "상서", "다행할", "기쁠", "즐거울", "웃을",
    "빼어날", "뛰어날", "훌륭할", "준걸", "귀할", "높을", "클", "넓을", "깊을", "멀",
    "오랠", "으뜸", "근본", "근원", "처음", "우두머리", "성인", "선비", "스승", "벼슬",
    "임할", "다스",
    # 학문·예술
    "글", "글월", "문장", "책", "노래", "풍류", "그림", "무늬", "채색", "수놓을",
    "가르칠", "배울", "재주", "칭찬할", "기릴",
    # 옥·보배
    "구슬", "보배", "보물", "진주",
    # 하늘·자연
    "하늘", "구름", "이슬", "안개", "바람", "눈부실",
    "바다", "물결", "시내", "봉우리", "언덕", "고을", "나라", "마을",
    "나무", "소나무", "잣나무", "대나무", "매화", "난초", "국화", "연꽃", "열매",
    "향기", "향내", "새벽", "아침",
    "봉황", "기린",
    # 기타 이름에 흔한 추상어
    "가운데", "차례", "얼굴", "맵시", "조정", "정사", "도리", "법도", "있을",
    "백성", "민첩할", "평탄할", "새로울", "아름", "예쁠", "어여쁠", "고울",
)
# 짧은 명사는 부분일치로 오탐이 난다(劣 '못할'→못, 贓 '장물'→물, 辨 '분별할'→별,
# 王 '임금'→금, 哄 '떠들썩할'→들). 훈을 낱말 단위로 쪼개 정확히 일치할 때만 인정한다.
NAME_HUN_TOKENS = frozenset((
    "옥", "금", "은", "쇠", "돌", "구슬",
    "해", "달", "별", "빛", "물", "샘", "못", "강", "섬", "산", "들", "뜰", "집",
    "봄", "여름", "가을", "꽃", "학", "용", "복", "글", "법", "뜻", "길", "터",
    "어질", "열",
))
# 훈이 무미건조해 키워드로는 못 잡지만 실제로는 표준 인명 한자인 글자들.
NAME_CHAR_WHITELIST = frozenset(
    "書濬時素雨宇河中池澤新旭伊逸主載采致赫潤願徐彦姫漢準鉉桓芝知眞多任"
    "陶都燾濤鍍導堂棠塘"
    "元有在和平幸厚民敏銀玄志初秋忠夏海香憲亨睿慧恩惠昊浩弘熙輝燦熹容姿"
    "序敍宣善成星聖昭秀洙修淑純順崇昇承勝施詩信娥雅安愛姸妍榮英永泳映藝"
    "譽玉溫瑤佑祐沅原源遠裕允律殷義仁慈才宰貞廷政正定濟珠智珍進昌彩天哲"
    "淸泰太韓幸赫玟旻珉俊峻晙賢顯炫誾瑞孝勳桓晃皓和"
)


# 한국 성씨 한자 — 훈이 '성 금'(金)·'박달나무 박'(朴)처럼 무미건조해 키워드에 걸리지
# 않지만 반드시 통과해야 한다. 빠지면 해당 성씨를 아예 고를 수 없다.
SURNAME_HANJA = frozenset(
    "金李朴崔鄭姜趙尹張林韓吳申徐權黃安宋柳洪全高文孫梁裵曺白許南沈劉盧河"
    "田丁郭成車兪具禹朱羅任閔陳池嚴蔡元千方楊孔玄康咸卞廉呂秋都石蘇愼馬薛"
    "吉延魏表明奇潘王琴玉陸印諸卓牟睦房甘邕平桂太皮杜宣唐晉魯余慶芮龐昔"
    "史夫桓箕邢頓化葛尙荀彬雍芸邵般丘冰陰莊景昇襄異弓班秦浪判彭簡"
)


def is_name_suitable_hun(ch, meaning):
    """훈이 이름에 어울리는가 — 화이트리스트 통과 여부."""
    if ch in NAME_CHAR_WHITELIST or ch in SURNAME_HANJA:
        return True
    if not meaning:
        return False
    hun = meaning.rsplit(" ", 1)[0] if " " in meaning else meaning
    if any(kw in hun for kw in NAME_HUN_KEYWORDS):
        return True
    return any(tok in NAME_HUN_TOKENS for tok in hun.split())


def is_avoided(ch, meaning):
    """meaning 은 '훈 음' 형식(예: '탐할 도'). 훈만 떼어 판정한다."""
    if ch in FORCE_AVOID:
        return True
    if not meaning or ch in AVOID_EXEMPT:
        return False
    if any(mk in meaning for mk in VARIANT_MARKERS):
        return True
    hun = meaning.rsplit(" ", 1)[0] if " " in meaning else meaning
    if hun in AVOID_EXACT:
        return True
    return any(kw in hun for kw in AVOID_SUBSTRINGS)


# kIICore 의 한국 코어 플래그가 놓치는, 실제로 인명에 흔히 쓰이는 글자.
# (예: 妍 은 한국 코어에 없고 이체자 姸 만 들어 있으나 실제 이름엔 妍 이 더 많이 쓰인다.)
FIT_OVERRIDES = {
    "姫": 3,
    "妍": 3, "娜": 3, "娧": 2, "媛": 3, "嬅": 2, "彗": 2, "洧": 2, "湜": 3,
    "熲": 2, "玧": 3, "瑀": 3, "琁": 2, "禛": 2, "縝": 2, "耘": 2, "芢": 2,
}


def compute_strokes(ch, strokes, radrec):
    """returns (wonhoek, pilhoek, radical) — 없으면 (None, None, None)"""
    cp = ord(ch)
    total = strokes.get(cp)
    rr = radrec.get(cp)
    if total is None or rr is None:
        return None, None, (rr[0] if rr else None)
    radical, residual = rr
    won = total
    if ch in WONHOEK_NUMERALS:
        won = WONHOEK_NUMERALS[ch]
    elif radical in WONHOEK_RADICALS and residual >= 0:
        canonical = WONHOEK_RADICALS[radical]
        used = total - residual
        if 0 < used < canonical:      # 부수가 축약형으로 쓰임 -> 정자 획수로 환산
            won = residual + canonical
    return won, total, radical


def element_for(ch, radical):
    if ch in CHAR_ELEMENT_OVERRIDES:
        return CHAR_ELEMENT_OVERRIDES[ch]
    if radical is None:
        return ""
    return RADICAL_ELEMENT.get(radical) or ""


def build():
    fetch_if_missing()
    readings = load_court_readings(os.path.join(RAW, "data-gov.csv"))
    strokes, radrec, common = load_unihan(os.path.join(RAW, "Unihan.zip"))
    by_reading, fallback = load_meanings(os.path.join(RAW, "hanja.txt"))

    rows, issues = [], Counter()
    for ch, rlist in readings.items():
        won, pil, radical = compute_strokes(ch, strokes, radrec)
        if won is None:
            issues["no_unihan_strokes"] += 1
        el = element_for(ch, radical)
        meaning = ""
        for r in rlist:
            if (r, ch) in by_reading:
                meaning = by_reading[(r, ch)]
                break
        if not meaning:
            meaning = fallback.get(ch, "")
            if not meaning:
                issues["no_meaning"] += 1
        rows.append((ch, ",".join(rlist), won if won is not None else "",
                     pil if pil is not None else "", el, meaning,
                     name_fit(ch, common),
                     0 if is_name_suitable_hun(ch, meaning) and not is_avoided(ch, meaning) else 1))

    rows.sort(key=lambda r: ord(r[0]))
    os.makedirs(os.path.dirname(OUT_TSV), exist_ok=True)
    with open(OUT_TSV, "w", encoding="utf-8", newline="\n") as f:
        f.write("hanja\treadings\twonhoek\tpilhoek\telement\tmeaning\tnamefit\tavoid\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")

    n_el = sum(1 for r in rows if r[4])
    usable = sum(1 for r in rows if r[6] >= 2 and not r[7])
    print(f"[build] rows={len(rows)}  element coverage={n_el}/{len(rows)} "
          f"({100.0*n_el/len(rows):.1f}%)  issues={dict(issues)}")
    print(f"[build] namefit>=2 & !avoid = {usable} ({100.0*usable/len(rows):.1f}%) — 작명 후보 생성에 쓰는 글자")
    print(f"[build] avoid 판정 = {sum(1 for r in rows if r[7])}자")
    print(f"[build] wrote {OUT_TSV}")
    return rows


# ---------------------------------------------------------------------------
# 검증
# ---------------------------------------------------------------------------
def verify(rows):
    idx = {r[0]: r for r in rows}

    def won(ch):
        return idx[ch][2]

    def pil(ch):
        return idx[ch][3]

    checks = [
        # (char, expected wonhoek)
        ("洙", 10), ("金", 8), ("木", 4), ("永", 5),
        ("四", 4), ("五", 5), ("六", 6), ("七", 7), ("八", 8), ("九", 9), ("十", 10),
        ("藝", 21),                       # 艹ㅡ>艸 환산: 잔여 15 + 6
        ("洪", 10), ("珉", 10), ("祐", 10), ("道", 16), ("防", 12),
        ("草", 12), ("羅", 20), ("恩", 10), ("性", 9), ("德", 15), ("裕", 13),
    ]
    report = []
    ok = True
    for ch, expected in checks:
        got = won(ch)
        status = "OK" if got == expected else "FAIL"
        ok &= (got == expected)
        report.append(f"{ch} 원획 expected={expected} got={got} [{status}]")

    # 泰: 지시서에는 "泰=9(원획=필획)" 이나 Unihan/한국 옥편 모두 총획 10.
    # 핵심 주장(氺=5획이라 축약형 환산이 일어나지 않아 원획==필획)만 검증한다.
    t_w, t_p = won("泰"), pil("泰")
    status = "OK" if (t_w == t_p == 10) else "FAIL"
    ok &= (t_w == t_p == 10)
    report.append(f"泰 원획==필획==10 (지시서의 '9'는 오기: 玉篇 총획 10) got 원획={t_w} 필획={t_p} [{status}]")

    y_p = pil("姸")
    status = "OK" if y_p == 9 else "FAIL"
    ok &= (y_p == 9)
    report.append(f"姸 필획 expected=9 got={y_p} [{status}]")

    for line in report:
        print("[verify]", line)
    assert ok, "verification failed — see [verify] lines above"
    print("[verify] all assertions passed")
    return report


def crosscheck(rows):
    """yumikang/saju hanja_dict.csv 와 교차검증 (데이터 재사용 없음 — 통계만)."""
    path = os.path.join(RAW, "crosscheck_hanja_dict.csv")
    if not os.path.exists(path):
        print("[crosscheck] file missing — skipped")
        return
    idx = {r[0]: r for r in rows}
    csv.field_size_limit(10 ** 8)
    s_total = s_pil_ok = s_won_ok = 0
    e_total = e_ok = 0
    e_diverge = Counter()
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ch = (row.get("character") or "").strip()
            if len(ch) != 1 or ch not in idx:
                continue
            mine = idx[ch]
            try:
                their_strokes = int(row.get("strokes") or 0)
            except ValueError:
                their_strokes = 0
            if their_strokes > 0 and mine[3] != "":
                s_total += 1
                if their_strokes == mine[3]:
                    s_pil_ok += 1
                if their_strokes == mine[2]:
                    s_won_ok += 1
            their_el = (row.get("element") or "").strip()
            if their_el in "木火土金水" and their_el and mine[4]:
                e_total += 1
                if their_el == mine[4]:
                    e_ok += 1
                else:
                    e_diverge[(ch, mine[4], their_el)] += 1
    print(f"[crosscheck] strokes(curated csv rows): overlap={s_total}  "
          f"필획 agreement={100.0*s_pil_ok/max(s_total,1):.1f}%  "
          f"원획 agreement={100.0*s_won_ok/max(s_total,1):.1f}%")

    # 대량 획수 교차검증: JSON 백업(8,787자, strokes = unihan_database 유래)
    import json
    jpath = os.path.join(RAW, "crosscheck_hanja_full.json")
    if os.path.exists(jpath):
        j_total = j_pil = j_won = 0
        for rec in json.load(open(jpath, encoding="utf-8")):
            ch = (rec.get("character") or "").strip()
            st = rec.get("strokes")
            if len(ch) != 1 or ch not in idx or not isinstance(st, int) or st <= 0:
                continue
            mine = idx[ch]
            if mine[3] == "":
                continue
            j_total += 1
            if st == mine[3]:
                j_pil += 1
            if st == mine[2]:
                j_won += 1
        print(f"[crosscheck] strokes(full json, n={j_total}):  "
              f"필획 agreement={100.0*j_pil/max(j_total,1):.1f}%  "
              f"원획 agreement={100.0*j_won/max(j_total,1):.1f}%")
        if j_total and j_pil / j_total < 0.5:
            print("[crosscheck]   ^ 낮은 이유: 상대 JSON 백업의 strokes 는 'unihan_database' 라 적혀 있으나"
                  " 실제 Unihan 값과 무관한 손상 데이터 (예: 伐=23, 富=14, 蓁=9)."
                  " 획수 교차검증은 수기 검수된 curated csv 행(위)과 verify()의 옥편 대조 셋이 유효.")
    print(f"[crosscheck] element: overlap={e_total}  agreement={100.0*e_ok/max(e_total,1):.1f}% "
          f"(상대 element 는 8,787행 중 192행만 자원오행 계열, 나머지는 수리오행이라 비교 제외)")
    if e_diverge:
        print("[crosscheck] element divergences (char, mine, theirs):")
        for (ch, m, t), _ in sorted(e_diverge.items()):
            print(f"    {ch}: mine={m} theirs={t}")


if __name__ == "__main__":
    rows = build()
    verify(rows)
    crosscheck(rows)
