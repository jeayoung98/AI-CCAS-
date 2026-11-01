# 확장 후보·블라인드 AI·실제 DB/API 평가

기준일: 2026-10-29. 검색 모듈의 네 가지 후속 과제를 실험 환경에서 연결했다.
운영 전환이나 최종 답변 생성은 포함하지 않는다. 네 과제의 1차 실험을 완료했으며,
실사용 품질 검증 완료를 의미하지 않는다. AI 라벨 불안정성과 낮은 채택 precision을 확인했다.

## 1. 문서 후보 확장

`tools/public_complaints/relevance_documents_v3.json`은 기존 7개 문서 객체를 그대로
보존하고 13개를 더한다. 총 20개이며 유형 기준 4개, 공식 안내 16개다. 이전보다
후보가 늘었지만 여전히 소규모 corpus다. 새 문서는 공개 안내의 짧은 요약이다.

| 분야 | 추가 내용 | 공식 출처 |
|---|---|---|
| 유실물 | 범위, 분실 신고, 물품 검색 | [경찰민원24 이용안내](https://minwon24.police.go.kr/custServiceCenter/siteUtztnGdInfo.do) |
| 순찰 | 예방 순찰의 범위, 희망 장소·시간 신청 | [순찰신문고 안내](https://minwon24.police.go.kr/cvlcpt/cvlcptGdInfo.do?cvlcptId=MW-914) |
| 사이버 거래 | 거래 피해 유형, 신고 자료, 상담, 일반 절차, 신고 이력조회 | [ECRM](https://ecrm.police.go.kr/minwon/main), [유형 설명](https://ecrm.police.go.kr/minwon/crs/quick/cyber1), [절차](https://ecrm.police.go.kr/minwon/crs/quick/process) |
| 실종 예방 | 보호자의 지문 등 사전등록 | [사전등록 안내](https://minwon24.police.go.kr/cvlcpt/cvlcptGdInfo.do?cvlcptId=MW-076) |
| 교통 | 사고 사실확인원 발급 | [발급 안내](https://minwon24.police.go.kr/cvlcpt/cvlcptGdInfo.do?cvlcptId=MW-005) |
| 고소·고발 | 담당 관서 확인 후 우편·팩스 송부 | [전북경찰청](https://www.jbpolice.go.kr/index.police?menuCd=DOM_000000209003000000) |

자료의 `boundary`로 신청·조회·예방 안내의 적용 범위를 제한한다. 신규 문서는
확인일과 실험용 코드 매핑을 기록한다. 기존 7개 코드 매핑은 이전 결과를 재현하도록
유지했다. 새 코드 매핑은 문서 내용에 따른 실험 해석이며 공식 분류 정답이 아니다.
문서 객체는 `DRAFT`, 이용 검토는 `PENDING`이다. 원문 접근 실패한 유실물 도움말과
세종경찰청 안내는 새 출처로 채택하지 않았다.

## 2. 기존 정답을 보여주지 않는 AI 재평가

`blind_relevance.py`는 500건의 마스킹된 민원과 20개 문서에 대해 10,000쌍을 새로
판단한다. 이전 관련성 판단·검색 점수·구조화된 질의 코드를 프롬프트에 넣지 않는다.
기존 응답은 변경하지 않고 새 판단이 끝난 후에만 기존 3,500쌍과 비교한다.

- 모델: `gpt-4o-mini`. 이전 평가와 같은 모델 계열일 수 있어 독립 모델 검증은 아니다.
- 별도 프롬프트 및 null 판단 seed를 사용한다. 체크포인트는 입력 해시와 모델에 묶인다.
- 각 판단은 민원의 출처 구간 ID, 이유, 확신도를 포함한다.
- 사람 판단과 AI 판단별로 일치·불일치를 나누고, 불일치를 정답 변경으로 자동 확정하지 않는다.
- 출처 구간 연결은 해당 글이 존재한다는 뜻이며 판단의 정확성을 증명하지 않는다.

같은 500건을 재사용하므로 새로운 독립 holdout을 확보한 것은 아니다. 개발 100건에서만
전략 가중치를 선택하고 평가 400건의 결과를 비교한다. 이번 결과를 보고 다시 조정하면
다음 결과는 추가 실험으로 구분해야 한다.

`project_relevance_pool.py`는 새 AI 판단 중 내용이 동일한 기존 7개 문서만 투영한다.
이를 대조군으로 사용하면 **라벨 변경의 영향**과 **후보 확장의 영향**을 구분할 수 있다.
대조군은 새로 평가한 라벨을 사용하므로 이전 2026-10-28 보고서와 정답 조건이 다르다.

## 3. 실제 pgvector 및 HTTP API 검증

`publicPoolDatabaseExperiment`는 Testcontainers의 일회용 PostgreSQL 17 + pgvector에서
실행한다. 원본 DRAFT를 수정하거나 운영 DB에 연결하지 않는다. 테스트 DB 안에서만
운영 검색 조건을 실행하기 위한 VERIFIED fixture 표시를 사용한다. 사람 검수·이용
승인을 받은 운영 지식이라는 의미가 아니다.

- 실제 GMS 임베딩 캐시로 문서 20개와 질의 500개를 재사용한다.
- 네 전략의 DB 순위와 코사인 전수 계산·Java 재정렬 순위를 비교한다.
- 실제 localhost HTTP API로 500건을 요청해 응답, 채널, 저장 로그, 위험 플래그를 확인한다.
- 첫 10건은 워밍업으로 제외하고 전략별 490개 지연 표본을 수집한다. 실행 순서는 회전한다.
- HTTP 시간에는 디스크 캐시 임베딩이 포함된다. 원격 GMS 응답 시간이나 운영 E2E 지연이 아니다.
- 서로 다른 질의의 단일 실행 표본이다. 부하 테스트나 대규모 corpus 벤치마크가 아니다.

현재 임시 임계값으로는 500건 모두 `INSUFFICIENT_EVIDENCE`였다. 순위 성능과
근거 채택 판단은 별개이며, 실제 점수 분포에 맞는 임계값 평가가 필요함을 확인했다.

## 4. 근거 부족·모호성·위험 평가의 범위

`evaluate_guide_cutoff.py`는 BASELINE 공식 안내 채널에서 임계값 0.20~0.90을 0.05 간격으로
비교한다. 개발 데이터의 안내 유무 balanced accuracy로 선택하고 동점이면 낮은 값을
선택한다. 평가 데이터는 설정 선택에 사용하지 않는다. 운영 임계값은 바꾸지 않는다.

관련 안내가 하나라도 있는지를 예측하는 지표와 실제 통과한 문서의 관련성을 구분한다.
오탐·누락 혼동행렬, 관련 안내가 없는 질의 거부율, 채택 문서 precision, 무관한 문서만
통과시킨 질의 수도 기록한다. 이 후보군에 관련 안내가 없다는 판단을 전체 지식에 대한
근거 부족이나 유사 사례 없음으로 확장하지 않는다.

모호성은 기존 500개 합성 스트레스 시나리오로 회귀 검증한다. 실제 공개 사례의 모호성에
대한 독립 정답은 확보하지 않았다. 위험 평가는 입력 플래그 전달과 별도 AI 추출 재평가로
나누었다. `audit_risk_signals.py`는 기존 구조화 코드를 보여주지 않고 마스킹된 글만 읽는다.
500건 중 위험 코드 집합이 451건(90.2%)에서 일치하고 49건에서 달랐다. 새 판단의 확신도는
LOW 450건, MEDIUM 43건, HIGH 7건이다. 이는 일치율이며 정확도 90.2%가 아니다.
원래 입력을 자동 수정하지 않았다. 양쪽 모두 같은 모델 계열의 AI 판단이므로 추가된
코드를 실제 누락, 사라진 코드를 실제 오탐이라고 단정할 수 없다.

API는 원래 위험 플래그가 있는 5건을 포함해 입력 플래그를 모두 보존했다. 전달 성공이
추출 정확성을 보장하지 않는다. 합성 정답이나 입력 플래그를 실제 민원 정답으로 대체하지 않는다.

GMS 연결 실패로 일부 요청이 중단됐지만 완료된 체크포인트를 재사용해 재개했다.
일시적인 연결 오류와 429/5xx에는 제한된 재시도를 추가했다. 인증 등 영구 오류는 즉시
실패시키며 키·응답 본문을 오류 메시지에 넣지 않는다. 위험 재평가의 중복 코드 출력은
형식 재요청으로 보완했으며 누락 항목을 임의 판단으로 채우지 않았다.

## 완료 결과

### 관련성 라벨과 동일 라벨 대조군

10,000쌍을 모두 AI가 판단했다. 관련 192쌍, 무관 9,808쌍이며 확신도는 HIGH 8,849쌍,
MEDIUM 1,151쌍이다. 확신도는 모델의 자기 보고이며 정확도 보증이 아니다.
기존 3,500쌍과 비교해 3,161쌍(90.31%)이 일치하고 339쌍이 달랐다.
이전 사람 판단 280쌍 중 일치는 185쌍, 이전 AI 판단 3,220쌍 중 일치는 2,976쌍이다.
특히 이전 관련 판단 353쌍 중 255쌍이 새 평가에서 무관으로 바뀌었다.
전체 일치율이 높아도 관련 판단이 안정적이라는 뜻은 아니다.

아래는 **새 AI 라벨을 공통으로 사용한 공식 안내 Top 3** 결과다. MRR은 상위 3개 안의
첫 관련 문서 순위를 사용한다. 가중치는 각 후보군의 개발 100건에서 선택했으며
METADATA 0.3, COSINE_CODE_RRF 0.1이었다.

| 후보군 / 전략 | Recall@3 | Precision@3 | MRR@3 | Hit@1 |
|---|---:|---:|---:|---:|
| 기존 공식 안내 6개 / BASELINE | 77.85% | 5.50% | 0.5772 | 41.46% |
| 기존 공식 안내 6개 / METADATA 0.3 | 85.16% | 6.08% | 0.6809 | 54.88% |
| 기존 공식 안내 6개 / COSINE_CODE_RRF 0.1 | 86.38% | 6.17% | 0.6768 | 52.44% |
| 확장 공식 안내 16개 / BASELINE | 67.05% | 5.25% | 0.5192 | 39.08% |
| 확장 공식 안내 16개 / METADATA 0.3 | 70.50% | 5.50% | 0.5613 | 42.53% |
| 확장 공식 안내 16개 / COSINE_CODE_RRF 0.1 | 73.37% | 5.75% | 0.5805 | 43.68% |
| 확장 공식 안내 16개 / CODE_RRF | 70.50% | 5.50% | 0.5594 | 42.53% |

기존 후보군에서 관련 안내가 있는 평가 질의는 82건, 확장 후보군에서는 87건이다.
Recall/MRR/Hit@1은 각각 이 집합에서 평균을 내고 Precision은 전체 평가 400건에서
K=3을 고정한다. 후보군 사이에는 정답 집합과 분모도 달라지므로 단순한 성능 하락률을
동일 질의의 인과 효과로 해석하면 안 된다. 2026-10-28 결과와도 라벨 조건이 다르다.

확장 후보군 안에서는 제한된 RRF 가산 전략이 BASELINE보다 Recall +6.32%p,
MRR +0.0613을 보였다. 그러나 문서 확장 자체가 회수율을 높인 것은 아니다.
신규 문서의 관련 판단은 500건 전체에서 10쌍(9개 질의)에 그쳤다. 과거 고충민원
발췌에 일반 유실물·예방 안내를 더하는 것만으로 정답 근거의 부족이 해결되지 않았다.

유형 기준은 4개로 늘어 Top 3이 전체 후보를 항상 포함하던 조건을 벗어났다.
확장 후보의 BASELINE Recall@3은 98.67%, MRR@3은 0.5511이며 METADATA는
100%, 0.7356이다. 독립 사람 정답이나 대규모 검색 성능이라는 주장은 하지 않는다.

### DB·API와 임계값 적용

기존 0.70 안내 임계값과 개발 데이터에서 선택된 0.50 임계값을 각각 별도 일회용 DB에서
500건 HTTP 요청으로 검증했다. 두 실행 모두 HTTP 500/500 성공, DB/메모리 순위 일치,
위험 플래그 보존율 100%였다. 네 전략의 DB 순위 비교에서 METADATA·COSINE_CODE_RRF
가중치는 비교용 0.3으로 고정했으며, 위 MRR 실험의 선택값 0.1을 사용한 결과와 구분한다.

| 안내 임계값 | API 전체 500건의 상태 | 평가 400건 중 실제 관련 안내 채택 질의 |
|---|---|---:|
| 0.70 (기존 임시값) | 근거 부족 500건 | 0 / 87 |
| 0.50 (실험용 후보) | GUIDE_ONLY 61건, 근거 부족 439건 | 21 / 87 |

0.50에서 평가 400건 중 안내를 하나라도 통과시킨 질의는 50건이다. 이 중 29건은 무관한
문서만 통과했고, 관련 문서를 실제로 하나 이상 통과시킨 질의는 21건이다. 통과한 문서
102개 중 관련 문서는 22개(21.57%)다. 관련 안내가 없는 질의 313건 중 296건(94.57%)은
거부했지만, 이 높은 거부율만으로 적절한 근거를 제공한다고 주장할 수 없다.

임계값 0.50은 개발 데이터의 안내 유무 balanced accuracy를 기준으로 선택했다.
실제 관련 문서 채택과 어긋날 수 있는 목적함수의 한계를 드러낸 결과이며 운영 채택하지 않았다.
테스트 결과를 본 뒤 다른 임계값으로 재선택하지 않았다.

기존 임계값 실행에서 DB BASELINE 검색 p50/p95는 12.88/19.24ms, 캐시 임베딩을 사용하는
HTTP는 40.98/57.79ms였다. 다른 실행의 수치는 별도 JSON에 남겼으며 환경·캐시 상태가
달라진 실행 간 시간 차이를 임계값 변경의 성능 개선으로 해석하지 않는다.

### 검증 및 남은 과제

- 전체 Java 테스트 **625개 통과**, 실패·오류·건너뜀 0개.
- Python 테스트 **59개 통과**.
- 별도 DB·HTTP 실험: 기본값과 선택값에서 각각 500건 통과.
- 실제 벡터 순위 비교는 API 키를 제거하고 원격 연결이 불가능한 base URL을 지정해도
  기존 캐시로 두 후보군 모두 완료됐다. 새 임베딩 생성은 추가 문서 13개에 한정했다.
- 합성 500개 모호성·거부·위험 시나리오도 전체 Java 테스트에 포함해 회귀 검증했다.

다음 단계의 우선순위는 문서 수의 추가 증가 자체보다 **실제 질의에 맞는 근거 확보와
339쌍의 라벨 불일치 분석**이다. 위험 코드 불일치 49건도 별도 분석 대상이다.
사람 검수를 수행하지 않는 현재 정책에서는 다른 모델·평가 기준을 통한 교차 검증을
추가할 수 있지만, 그 결과도 사람 정답과 동일시할 수 없다. 실제 모호성 정답,
VERIFIED_CASE 후보, 운영 이용 조건 검토, 대규모·원격 임베딩 포함 부하 검증은 남아 있다.

결과 파일:

- [블라인드 판단 비교](results/blind-relevance-v3.json)
- [같은 라벨·기존 후보군](results/public-pool-blind-original-v3.json)
- [확장 후보군 순위](results/public-pool-expanded-v3.json)
- [기본 DB/API 검증](results/public-pool-database-v3.json)
- [실험 임계값 DB/API 검증](results/public-pool-database-cutoff050-v3.json)
- [임계값 지표](results/guide-cutoff-v3.json)
- [위험 신호 AI 재평가](results/risk-audit-v1.json)

## 재현 명령

Java 26 및 Docker가 필요하다. 아래 파일의 질의·라벨·벡터는 Git 제외 로컬 자료다.

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/blind_relevance.py --output .local-data/acrc-blind-v3

.\gradlew.bat publicPoolExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v3.json' '-Dpool.qrels=.local-data/acrc-blind-v3/qrels' '-Dpool.output=.local-data/public-pool-experiment-v3' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache'

.\gradlew.bat publicPoolDatabaseExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v3.json' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.output=.local-data/public-pool-database-v3'

.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/evaluate_guide_cutoff.py --qrels .local-data/acrc-blind-v3/qrels --database .local-data/public-pool-database-v3 --output .local-data/public-pool-database-v3/guide-cutoff.json

.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/audit_risk_signals.py --output .local-data/acrc-risk-audit-v1
```

개발 데이터에서 선택된 0.50을 **실험 DB에서만** 적용한 재현 명령:

```powershell
.\gradlew.bat publicPoolDatabaseExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v3.json' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.output=.local-data/public-pool-database-v3-cutoff050' '-Dpool.guideThreshold=0.5'
```

동일 라벨의 기존 후보 대조군:

```powershell
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/project_relevance_pool.py --source .local-data/acrc-drafts-500-v2/drafts.jsonl --privacy .local-data/acrc-human-review/responses.json --source-corpus tools/public_complaints/relevance_documents_v3.json --target-corpus tools/public_complaints/relevance_documents_v2.json --responses .local-data/acrc-blind-v3/labels/effective-responses.json --output .local-data/acrc-blind-v3-original-pool

.\gradlew.bat publicPoolExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v2.json' '-Dpool.qrels=.local-data/acrc-blind-v3-original-pool/qrels' '-Dpool.output=.local-data/public-pool-blind-original-v3' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache'
```

## 코드 변경과 검증

- `relevance_documents_v3.json`: 기존 문서를 보존한 20개 후보.
- `blind_relevance.py`, `project_relevance_pool.py`: 블라인드 판단 및 동일 라벨 대조군.
- `audit_risk_signals.py`: 구조화 입력을 변경하지 않는 별도 위험 재평가.
- `gms_request.py`, `judge_relevance.py`: 응답 본문을 숨기는 제한된 오류 재시도.
- `evaluate_guide_cutoff.py`: 개발 데이터만 사용한 임계값 선택과 거부·채택 지표.
- 위 Python 도구의 `test_*.py` 5개: 버전 결합, 입력 보존, 지표·재시도 검증.
- `PublicPoolExperimentMain.java`, `PublicPoolExperimentMainTest.java`: 문서 수 고정 제거,
  명시적 코드 검증, 벡터 캐시 재사용, 후보 수 제한 일치.
- `PublicPoolDatabaseExperiment.java`, `build.gradle`: 일반 테스트와 분리된 일회용 DB·HTTP 실험.
- `README.md`, 이 문서, `docs/results/*v3.json`, `risk-audit-v1.json`: 결과와 한계.

재현용 로컬 데이터 없이 실행하는 일반 검증은 `gradlew test`와
`python -m unittest discover -s tools/public_complaints -q`다. DB 실험은 로컬 입력 및
벡터 캐시가 있어야 하며 임베딩 API로 자동 대체하지 않는다.

추천 커밋 분할:

1. `feat(evaluation): expand official corpus and add blinded AI audits`
2. `feat(evaluation): replay real vectors through isolated pgvector and HTTP`
3. `docs(evaluation): report expanded-pool results and uncertainty limits`

개별 민원 내용, 벡터 배열, API 키는 보고서나 커밋에 포함하지 않는다.
