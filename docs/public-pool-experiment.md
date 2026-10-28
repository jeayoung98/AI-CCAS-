# 실제 임베딩 기반 소규모 문서 후보 비교 (2026-10-28)

실제 공개 민원 발췌 500건과 기존 문서 후보 7개로 GMS 임베딩을 생성하고 채널별
검색 순위를 비교했다. 운영 DB 적재나 PostgreSQL 성능 측정은 하지 않았다.
문서의 DRAFT 상태와 이용 조건 검토 상태는 그대로 유지한다.

## 실험 구성

- 모델: `text-embedding-3-large`, 1,536차원, `embed-v1-large-1536`.
- 민원 500개와 문서 7개, 총 507개 벡터를 생성했다. 기존 `OpenAiEmbeddingAdapter`를 재사용했다.
- 민원 입력은 Java DTO·마스킹 플래그·본문 해시·검색문 해시를 다시 확인했고,
  기존 `SearchTextBuilder`로 재생성한 검색문이 저장된 것과 일치하는지 검사했다.
- 문서도 기존 `CategoryReference`/`OfficialGuideChunk`를 검증하고 동일한 Java 생성기를 사용했다.
- `prepared-document-pool.json`에 실험용 문서 구조와 검색문을 저장했다. 원본 문서 상태를 승격하지 않는다.
- 전수 코사인 유사도와 운영 코드의 `CandidateRanker`로 후보를 정렬한다. 채널을 섞지 않는다.
- 유형 기준은 1개, 공식 안내는 6개다. 검증 사례 채널은 자료가 없어 평가하지 않는다.
- 라벨은 사람 280쌍과 AI 3,220쌍으로 구성된다. 500건 모두 해당 7개 문서에 대한 값이 있다.

## 설정 선택과 결과

dev 100건에서 METADATA와 COSINE_CODE_RRF의 가중치 0.05/0.1/0.3을 비교하고,
공식 안내 MRR 최대값을 선택했다. 동점이면 작은 가중치를 선택한다. 둘 다 0.3이
선택됐다. 이후 test 400건에서 고정 설정을 평가했다. CODE_RRF는 가중치를 선택하지 않는 비교군이다.

공식 안내 Top 3, 평가용 400건 결과:

| 전략 | Recall@3 | Precision@3 | MRR | Hit@1 |
|---|---:|---:|---:|---:|
| BASELINE | 91.24% | 10.83% | 0.6934 | 51.82% |
| METADATA 0.3 | 95.62% | 11.33% | 0.8187 | 70.07% |
| COSINE_CODE_RRF 0.3 | 95.62% | 11.33% | 0.8248 | 70.07% |
| CODE_RRF | 95.62% | 11.33% | 0.8260 | 70.80% |

Recall·MRR·Hit@1은 관련 문서가 하나 이상 있는 **137건**에서 평균을 계산했다.
Precision@3은 관련 문서가 없는 나머지 사례를 포함한 **400건 전체**에서 분모를
고정 K=3으로 계산했다. 결과 개수가 적다고 분모를 줄이지 않으며, 중복 문서로 적중 수를 늘리지 않는다.
유형 기준은 후보 1개라 관련 라벨이 있으면 항상 첫 번째로 반환된다. 이 채널의
Recall/MRR 1.0은 검색 품질이 검증됐다는 의미가 아니다.

## 해석과 범위

메타데이터 재정렬은 이 제한된 후보 목록에서 기본 검색보다 좋은 지표를 보였다.
CODE_RRF의 MRR이 가장 높지만, 앞선 합성 실험에서 상충 근거 누락이 있었으므로
이 결과만으로 운영 전략을 바꾸지 않았다. 기본값 BASELINE과 근거 판정 임계값은 유지한다.

주의할 평가 한계:

- 공식 안내 후보가 6개뿐이다. 대규모 지식 corpus에서의 회수율·지연·일반화 성능을 입증하지 않는다.
- 정답 대부분과 구조화 입력이 AI 생성이다. 사람 독립 검수 정확도나 전체 민원 분류 정확도가 아니다.
- 문서의 민원 대상·요청 행위 코드는 문서 내용만 보고 미리 정한 실험용 매핑이다.
  공식 분류 기준이 아니다. 소규모 문서별 매핑에 결과가 의존할 수 있다.
- 검증 사례, 근거 상태·위험·모호성의 독립 정답이 없어 그 지표는 생성하지 않았다.
- 임계값 기반 거부를 비교한 실험이 아니다. Top 3 반환 결과만 평가했으므로 비관련 사례의 안내 적합성을 주장하지 않는다.
- DB 적재·SQL 실행·API E2E 시간을 측정하지 않았다. 기존 pgvector 통합 테스트와 이번 벡터 풀 실험은 별개다.

## 코드 매핑

수사 이의는 POLICE_SERVICE_COMPLAINT/APPLY, 민원 결과 조회는 CASE_STATUS/CHECK_STATUS,
정보공개는 OTHER/APPLY·SEARCH, 손실보상은 OTHER/APPLY로 매핑했다. 권익위 안내 3개는
POLICE_SERVICE_COMPLAINT·OTHER/APPLY·CONSULT를 사용했다. 위험 코드는 문서에 추가하지 않았다.
이 매핑은 개발·평가 질의의 라벨을 보고 최적화하지 않았다.

## 재현과 산출물

```powershell
. .\tools\Use-Gms.ps1
.\gradlew.bat publicPoolExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v2.json' '-Dpool.qrels=.local-data/acrc-qrels-ai-v2' '-Dpool.output=.local-data/public-pool-experiment-v1'
```

캐시는 검색문·모델·차원·임베딩 버전을 결합한 해시로 구분하고 차원·유한값·벡터 크기를
검증한다. 데이터/문서/라벨/실험용 문서 구조의 해시가 달라지면 같은 실험 폴더의 재사용을
거부한다. 실패한 호출을 재시도할 때 완료된 벡터는 다시 호출하지 않는다.
보고서에는 실제 사용 벡터 집합의 SHA-256도 기록한다. API 키를 제거한 재실행이
캐시만으로 성공했고, 최초 실행과 선택 가중치·모든 지표가 동일함을 확인했다.

검증: 전체 Java 테스트 623개 통과, 실패/오류/건너뜀 0개. 지표의 중복 적중 방지,
고정 K 분모, 정답 없는 채널의 null 처리, 실제 벡터 정규화와 잘못된 벡터 거부를 검증했다.
키·벡터·개별 민원 입력은 커밋하지 않는다.

집계 결과는 [결과 JSON](results/public-pool-experiment.json), 벡터와 개인별 입력은
Git에서 제외된 `.local-data/public-pool-experiment-v1/`에 있다.

변경 파일: `README.md`, `build.gradle`, `src/main/java/com/example/ccas/retrieval/evaluation/PublicPoolExperimentMain.java`,
`src/test/java/com/example/ccas/retrieval/evaluation/PublicPoolExperimentMainTest.java`, 이 문서,
`docs/results/public-pool-experiment.json`, `docs/portfolio-notes.md`.
커밋 메시지: `feat(evaluation): compare real embeddings in an isolated candidate pool`
