# festivalSeries linking v1 — district-level placeholder 정규화 (2026-08-08)

이 문서는 series-linking v1 체크포인트(`feat: add multiyear festival series linking analysis`) 이후
발견된 "region-level district placeholder"(본청/시자체/-/서울시 등) 문제를 정리하고, 정규화 적용
전/후 통계와 남은 ambiguous 분석, chain-linking 설계 제안을 담는다.

**이번 패스는 series 품질 정리(district 정규화)까지만 다룬다.** 물가보정/recency/COVID
weight/backtest, BudgetEstimator/CandidateSelector/confidence/similarity/production 2026
파이프라인은 전혀 건드리지 않았다.

## 1. district-level placeholder 후보 (실제 10,198행 조사)

`multi_year_festival_record.district_raw`(= `district_text`, 258종 distinct) 전체 빈도를 조사해
"실제 기초자치단체가 아닌" 값만 골랐다. 전체 규칙과 근거는
`backend/.../multiyear/series/DistrictPlaceholderNormalizer.java`의 Javadoc에 있다. 요약:

| 분류 | 값 | 건수 |
|---|---|---|
| 범용 표현 | `-` | 421 |
| | `시자체` | 207 |
| | `본청` | 60 |
| | `도` | 19 |
| | `시` | 3 |
| | `지자체` | 3 |
| | `도자체` | 3 |
| | `시 자체` | 2 |
| | `미기재` | 1 |
| 광역지역명 반복 | `서울시` | 7 |
| | `울산시` | 9 |
| | `제주도` | 10 |
| | `세종시` | 4 |
| | `제주도 본청` | 3 |
| | `대구광역시`(단독) | 1 |
| 조직/시설명 | `인천관광공사` | 7 |
| | `대전마케팅공사` | 3 |
| | `경제청` | 5 |
| | `민간` | 6 |
| | 기타(서울관광재단/인천도시공사/서부공원녹지사업소/대공원/인천경제자유구역청/울 산 시설공단) | 1~3건씩 |
| 괄호 변형 | `시자체 (문화재단)`, `도자체 (관광과)`, `도자체 (식품 유통과)` | 각 1 |

**의도적으로 제외**(실제 시군구 + 접미어/오타/복합값이라 null 처리하면 정보 손실):
`중구청`, `제주시 건입동`, `청주시 청원구 오창읍`, `수원시 장안구`, `용인시 처인구`,
`김포시 (양촌읍)`, `서귀포시 표선면`, `제주시 (삼양동)`, `에산군`(오타), `김친시`(오타),
`지천면`(실제 면), 콤마로 여러 값이 뒤섞인 복합값(`대구광역시, 중구`, `시자체,중구` 등 11건).

`districtRaw`/`districtText` 원본 컬럼은 전혀 수정하지 않았다 — `FestivalSeriesLinkingService`가
클러스터링 키를 계산할 때만 이 목록을 참조해 null(REGION_LEVEL)로 취급한다.

## 2. Before / After 통계 (실제 10,198행)

| 항목 | Before | After | 변화 |
|---|---|---|---|
| distinct festivalSeries | 4,264 | 4,203 | -61 |
| SINGLETON(1년) | 2,111 | 2,059 | -52 |
| 2년 이상 | 2,153 | 2,144 | -9 |
| 5년 이상 | 528 | 552 | +24 |
| 8년 이상 | 225 | 231 | +6 |
| 10년(전체) | 100 | 102 | +2 |
| 최대 연속 관측 연도 | 10 | 10 | - |

matchMethod:

| method | Before | After |
|---|---|---|
| EXACT | 4,747 (46.5%) | 4,843 (47.5%) |
| NORMALIZED_EXACT | 2,664 (26.1%) | 2,724 (26.7%) |
| FUZZY | 683 (6.7%) | 579 (5.7%) |
| UNMATCHED | 2,104 (20.6%) | 2,052 (20.1%) |

fuzzy 후보 밴드:

| 밴드 | Before | After |
|---|---|---|
| HIGH | 821 | 719 |
| MEDIUM | 1,047 | 835 |
| LOW | 763 | 665 |
| applied(실제 자동 연결) | 515 | 434 |

**ambiguous(같은 singleton에 HIGH 후보 2개 이상이라 보류): 141건 → 131건 (10건 감소, -7.1%)**

**해석**: district 오탐 mismatch가 사라지면서 EXACT/NORMALIZED_EXACT(결정적 매칭) 비중이
늘고 FUZZY 의존도가 줄었다(683→579건, -104건). 이미 fuzzy가 잘 잡던 케이스들이 이제 애초에
같은 결정적 키로 묶이기 때문이다. 5년 이상/8년 이상/10년 series가 전부 증가한 것도 같은
이유 — 전에는 "본청"/"시자체"/"-"로 흩어져 있던 같은 축제의 여러 연도가 이제 하나로 뭉친다.
ambiguous가 10건만 줄어든 이유는, district mismatch로 생기던 애매함은 해소됐지만
"같은 축제가 연속 3개 연도 이상 나타나 중간 연도가 앞뒤 둘 다에 HIGH로 걸리는" chain형
애매함은 이번 패스와 무관하게 그대로 남기 때문이다(6절 참고).

## 3. district 정규화로 새로 정상 연결된 series (Top 발췌)

`scope=REGION_LEVEL AND record_count>=2 AND distinct district_raw 변형 수>=2`로 걸러 실제
2개 이상의 서로 다른 placeholder 문자열이 하나로 합쳐진 series만 뽑았다(2026-08-08 기준
DB 조회, 전체 목록은 아래 SQL로 재현 가능).

| canonical name | region | 기존 district_raw 변형 | 연도 | recordCount | matchStatus |
|---|---|---|---|---|---|
| 형형색색 달구벌 관등놀이 | 대구 | `-` \| `본청` \| `시자체` | 2017~2026 | 10 | DETERMINISTIC |
| 대구생활문화제 | 대구 | `-` \| `본청` \| `시자체` | 2017~2026 | 10 | DETERMINISTIC |
| 대구치맥페스티벌 | 대구 | `-` \| `대구광역시` \| `본청` \| `시자체` | 2017~2026 | 10 | DETERMINISTIC |
| 제주해녀축제 | 제주 | `-` \| `도` \| `본청` \| `제주도` \| `제주도 본청` | 2017~2026 | 9 | DETERMINISTIC |
| 부산바다축제 | 부산 | `-` \| `본청` \| `시자체` | 2017~2026 (9) | 9 | DETERMINISTIC |
| 부산국제록페스티벌 | 부산 | `-` \| `본청` \| `시자체` | 2017~2026 (9) | 9 | DETERMINISTIC |
| 탐라문화제 | 제주 | `-` \| `도` \| `본청` \| `제주도` \| `제주도 본청` | 8건 | 8 | DETERMINISTIC |
| 아트피크닉 | 광주 | `-` \| `시자체` | 2019~2026 | 8 | DETERMINISTIC |
| 세종축제 | 세종 | `NULL` \| `-` \| `본청` \| `세종시` \| `시자체` | 2017~2025 | 7 | DETERMINISTIC |
| 명량대첩축제 | 전남 | `NULL` \| `-` \| `도` \| `도자체` \| `도자체(관광과)` \| `본청` | 2017~2024 | 7 | DETERMINISTIC |
| **대구국제오페라축제** | 대구 | `-` \| `본청` \| `시자체` | 2020~2026 | 6 | DETERMINISTIC |
| **대구포크페스티벌** | 대구 | `-` \| `본청` \| `시자체` | 2020~2025 | 6 | DETERMINISTIC |
| 서울드럼페스티벌 | 서울 | `-` \| `서울시` \| `시자체` | 2017~2024 | 6 | FUZZY_MERGED |
| 부산불꽃축제 | 부산 | `-` \| `본청` \| `시자체` | 2017~2025 | 6 | DETERMINISTIC |
| 성산일출축제 | 제주 | `-` \| `도` \| `본청` \| `제주도` | 2018~2026 | 6 | DETERMINISTIC |
| 인천펜타포트음악축제 | 인천 | `-` \| `본청` \| `시자체` \| `인천관광공사` | 2017~2025 | 5 | DETERMINISTIC |
| **대구약령시 한방문화축제** | 대구 | `-` \| `본청` \| `시자체` | 2017~2026 | 4 | DETERMINISTIC |
| **대구국제재즈축제** | 대구 | `-` \| `본청` \| `시자체` | 2020~2023 | 4 | DETERMINISTIC |
| 서울김장문화제 | 서울 | `서울시` \| `시자체` | 2017~2021 | 5 | DETERMINISTIC |
| 서울빛초롱축제 | 서울 | `본청` \| `시자체` | 2017~2022 | 5 | DETERMINISTIC |

(굵게 표시한 4개가 이번 분석에서 발견된 대구 사례들이다. 전체 40개 목록은 아래 SQL로 재현.)

**대구포크페스티벌 상세(수정 전/후):**

| 연도 | 원본 축제명 | district_raw | 이전(before) | 이후(after) |
|---|---|---|---|---|
| 2020 | 대구포크페스티벌 | 시자체 | 개별 singleton | series #1494, EXACT |
| 2021 | 대구포크페스티벌 | 시자체 | 개별 singleton | series #1494, EXACT |
| 2022 | 대구포크페스티벌 | 본청 | 개별 singleton (2020과 fuzzy HIGH였지만 [2636]↔[5557] 이중 후보로 ambiguous 보류) | series #1494, EXACT |
| 2023 | 대구포크페스티벌 | - | 개별 singleton | series #1494, EXACT |

이전에는 `district=-0.15`(mismatch 페널티) 때문에 fuzzy score가 0.95 안팎으로 떨어지고,
"본청"에 대해 2020년(시자체)과 2023년(-) 두 후보가 동시에 HIGH로 걸려 애매하다고 보류됐다
(141건 목록에 실제로 포함돼 있었다). 이제 셋 다 district=null(REGION_LEVEL)이라
정규화된 이름만으로 EXACT 매칭되어 하나의 series로 결정적으로 묶인다.

재현용 SQL(로컬 MySQL, `festival_budget` DB):

```sql
SELECT fs.id, fs.canonical_name, fs.canonical_region, fs.match_status, fs.record_count,
       GROUP_CONCAT(DISTINCT COALESCE(m.district_raw,'NULL') ORDER BY m.district_raw SEPARATOR ' | ') AS district_raw_variants
FROM festival_series fs
JOIN festival_series_membership fsm ON fsm.festival_series_id = fs.id
JOIN multi_year_festival_record m ON m.id = fsm.festival_record_id
WHERE fs.scope='REGION_LEVEL' AND fs.record_count>=2
GROUP BY fs.id
HAVING COUNT(DISTINCT COALESCE(m.district_raw,'NULL')) >= 2
ORDER BY fs.record_count DESC;
```

## 4. chain linking은 아직 구현하지 않음

`FestivalSeriesLinkingService`의 union-find는 여전히 "같은 singleton이 서로 다른 series를
가리키는 HIGH 후보를 2개 이상 받으면 자동 연결하지 않는다" 규칙을 그대로 유지한다.
2017 A ↔ 2018 B, 2018 B ↔ 2019 C처럼 chain으로만 연결되는 경우를 A/B/C 셋 다 자동으로
transitive merge하는 기능은 이번 패스에서 추가하지 않았다(6절 설계안 참고, 미구현).

## 5. 남은 ambiguous 131건 재분류

`nameSimilarity`(모든 충돌 후보 중 최솟값)와 보조신호 충돌 개수(district/type/year 중
음수 신호 개수)를 기준으로 자동 분류했다 — 사람이 한 건씩 읽은 게 아니라 이미 계산된
신호를 재사용한 휴리스틱이므로, 최종 판단은 아니고 검토 우선순위 참고용이다.

| 분류 | 기준 | 건수 |
|---|---|---|
| A. 동일 축제일 가능성 매우 높음 | 모든 후보 nameSim≥0.95, 부정 신호 없음 | 56 |
| B. 이름 변경 가능성 / 약한 부정 신호 | nameSim≥0.90, 부정 신호 최대 1개(대개 연도 간격이 멀거나 유형 분류가 해 다름) | 69 |
| C. 서로 다른 축제일 가능성 있음 | nameSim 0.80~0.90 이거나 부정 신호 2개 이상 | 6 |
| D. 판단 불가 | 후보 정보 부족 | 0 |

**중요한 관찰**: 131건 거의 전부(125/131)가 nameSim≥0.90이다 — 즉 district 정규화 이후
남은 ambiguous는 "이름이 헷갈려서"가 아니라 거의 전부 **같은 축제가 3개 이상 연도에
나타나 중간 연도가 앞뒤 이웃 둘 다에 HIGH로 걸리는 chain 위상 문제**다. 오연결 위험보다는
"놓친 연결"(false negative) 쪽에 훨씬 가깝다.

### A 카테고리 샘플 (6건)

```
[1714] 제8회 낙동강 유채꽃 축제 (2019, 부산 시자체)
    -> [62] 제6회 낙동강유채꽃 축제 (2017) score=1.00 nameSim=1.00
    -> [4530] 제10회 낙동강유채꽃축제 (2022) score=1.00 nameSim=1.00
[5352] 대구 약령시 한방문화축제 (2022, 대구 본청)
    -> [94] 대구약령시 한방문화축제 (2017) score=1.00 nameSim=1.00
    -> [5566] 제45회대구약령시한방문화축제 (2023) score=1.00 nameSim=1.00
[9095] 제21회 인천 펜타포트 음악축제 (2026, 인천 -)
    -> [118] 인천펜타포트음악축제 (2017) score=1.00 nameSim=1.00
    -> [1782] 인천펜타포트 음악축제 (2019) score=1.00 nameSim=1.00
[8413] 제23회 세종조치원 복숭아 축제 (2025, 세종 -)
    -> [1858] 세종조치원복숭아축제 (2019) score=1.00 nameSim=1.00
    -> [9196] 제24회 세종 조치원복숭아 축제 (2026) score=1.00 nameSim=1.00
```

### B 카테고리 샘플 (5건) — 대개 연도 간격이 멀거나(6년+) 유형 분류가 해마다 다름

```
[3792] 안성맞춤남사당 바우덕이축제 (2021, 경기 안성시) — type=-0.08 (문화예술 vs 기타류 혼재로 추정)
    -> [223] 2017 안성맞춤 남사당 바우덕이축제 (2017) nameSim=1.00
    -> [1921] 안성맞춤남사당바우덕이축제 (2019) nameSim=1.00
    -> [5256] 안성맞춤 남사당 바우덕이 축제 (2022) nameSim=1.00
[1909] 제24회 광주남한산성 문화제 (2019, 경기 광주시)
    -> [7918] 제30회 광주시 남한산성문화제 (2025) nameSim=0.90, year=-0.05(6년 간격)
[198] 제16회 의정부 음악극축제 (2017, 경기 의정부시)
    -> [5756] 제22회 의정부 음악극 축제 (2023) year=-0.05(6년 간격)
```

### C 카테고리 샘플 (전체 6건)

```
[2820] 포천백운계곡동장군축제 (2020, 경기 포천시)
    -> [9311] 제22회 포천 백운계곡 동장군 축제 (2026) score=0.95, year=-0.05, type=-0.08 (6년+유형 불일치 중복)
[9418] 제11회 한강,낙동강발원지 축제 (2026, 강원 태백시)
    -> [1114] 한강•낙동강 발원지축제 (2018) nameSim=0.909, year=-0.05
    -> [7021] 한강낙동강발원지 축제 (2024) nameSim=0.909, type=-0.08
[347] 단양소백산철쭉제 (2017, 충북 단양군)
    -> [5987] 제39회 단양 소백산철쭉제 (2023) year=-0.05, type=-0.08
[2111] 아산 성웅이순신축제 (2019, 충남 아산시)
    -> [8796] 제64회 아산 성웅 이순신축제 (2025) year=-0.05, type=-0.08
[1449] 2018 청산도슬로걷기 축제 (2018, 전남 완도군)
    -> [9870] 2026 청산도 슬로걷기축제 (2026) year=-0.05, type=-0.08
[982] 언양한우불고기 축제 (2018, 울산 울주군)
    -> [6824] 언양 한우불고기 축제 (2024) year=-0.05
```

전체 131건 원문(후보별 region/district/festivalType/score/충돌 후보 전부)은
`backend/multiyear-series-linking-report.txt`(로컬 산출물, gitignore 처리됨 - 재현하려면
`FESTIVAL_MULTIYEAR_CSV_PATH` 설정 후 `FestivalSeriesLinkingRealDataAnalysisTest` 실행)에
그대로 남아 있다.

## 6. chain linking 설계안 (제안만, 미구현)

목표: 2017 A ↔ 2018 B(HIGH), 2018 B ↔ 2019 C(HIGH)처럼 인접 연도 HIGH 엣지가 사슬로 이어질 때
A-B-C를 안전하게 하나의 series로 묶을지 검토한다. **아직 구현하지 않았다.**

### 제안하는 안전 조건 (전부 AND)

1. **same region**: 이미 fuzzy 버킷이 region 단위라 자동 충족.
2. **district compatible**: 두 노드의 district가 (a) 둘 다 null이거나 (b) 둘 다 non-null이고
   같거나 (c) 한쪽만 null인 경우까지만 허용 — 서로 다른 non-null district끼리는 chain으로도
   연결 금지(district mismatch가 있으면 chain 후보에서 아예 제외).
3. **small year gap**: 체인의 각 엣지(A-B, B-C)는 반드시 `year_sig >= 0`인 인접 연도 쌍이어야
   함(현재 정의로 gap<=1). 즉 A-C처럼 먼 연도끼리 직접 이어붙이는 건 A-B-C 경유로만 가능하고,
   경유 없이 먼 연도만 있는 엣지는 체인에 넣지 않는다.
4. **HIGH edge only**: MEDIUM/LOW 엣지는 체인에 참여시키지 않는다(지금과 동일하게 검토 목록에만).
5. **festival type compatible**: `type_sig < 0`(명시적으로 겹치지 않는 유형)인 엣지는 체인에서
   제외 - 유형 정보가 아예 없는 경우(0)는 허용.
6. **연도 중복 금지**: 하나의 series 안에 같은 연도(datasetYear)의 서로 다른 record가 2개 이상
   들어가면 안 된다 - union 시점에 이미 그 연도가 점유돼 있으면 그 엣지는 버리고
   ambiguous로 되돌린다(자동 merge 대신 사람 검토로).
7. **series 전체 이름 유사도 하한**: 체인이 3개 이상으로 길어질수록 "연쇄적으로 조금씩 달라져
   실제로는 다른 축제가 되는" drift 위험이 있다. 최종 병합 전에 체인에 속한 모든 원본
   normalizedName 쌍의 fuzzyKey 유사도가 전부 어떤 하한(예: 0.85) 이상인지 다시 확인하고,
   하나라도 하한 미만이면 그 체인 전체를 자동 merge하지 않고 ambiguous로 남긴다(현재
   pairwise 계산은 인접 엣지만 보므로, A-C처럼 체인 양 끝단의 직접 유사도가 낮아지는 경우를
   놓칠 수 있다 - 이 하한 재검사가 그 구멍을 막는다).

### 알고리즘 스케치 (구현 시 참고용, 지금은 코드에 없음)

1. HIGH 밴드 엣지만으로 그래프를 만든다(현재 union-find가 쓰는 것과 같은 엣지 집합).
2. 조건 2/3/5를 만족하지 않는 엣지는 그래프에서 미리 제거한다.
3. 남은 그래프에서 연결요소(connected component)를 구한다.
4. 각 연결요소 내부에서 조건 6(연도 중복)을 검사 - 위반하면 그 컴포넌트를 연도 중복이
   없는 가장 큰 부분그래프로 축소하거나, 아예 통째로 ambiguous 처리(보수적으로는 후자 권장).
5. 조건 7(전체 이름 유사도 하한)을 통과하면 그 컴포넌트를 하나의 series로 확정, 실패하면
   ambiguous로 남긴다.
6. 지금처럼 "같은 singleton이 서로 다른 컴포넌트에 양다리로 걸리는" 경우(예: 대구약령시
   한방문화축제 사례처럼 2022가 2017쪽과 2023쪽 둘 다에 HIGH)는 여전히 자동 처리하지 않고
   ambiguous로 남긴다 - 체인은 "선형"일 때만 다루고, "분기(fork)"가 생기면 지금 규칙을
   그대로 적용한다.

이 설계는 false positive를 늘리지 않는 방향(교집합적으로 더 엄격한 조건 추가)에
집중했다 - 특히 조건 6/7은 지금 pairwise 로직에는 없는 새로운 안전장치다. 실제 구현은
다음 단계로 미루고, 이번에는 설계만 남긴다.