# Retrieval optimization experiments

후속 작업: [500개 합성 시나리오 및 제한적 코사인 RRF 실험](retrieval-stress-experiment.md).
아래 수치와 파일 목록은 1차 실험의 고정 기록이다. 후속 전체 테스트는 615개 통과했다.

## Scope and baseline

Baseline source: `f2ca74322aa79787cceb070fdd17c893cd5df815`.
The default strategy remains `BASELINE`. Its SQL and provisional evidence thresholds are preserved.
No production accuracy claim is supported by the data currently in this repository.

## Repository audit

The request passes validation and masking checks, builds versioned deterministic search text,
calls `EmbeddingPort`, then persists the query and searches three independent channels in a
transaction. Only active, VERIFIED items with matching embedding version are eligible.
`RetrievalEvidenceEvaluator` applies channel cosine cutoffs, case category agreement and a
category margin. Risk presence is independent of similarity. This separation, explicit versions,
channel boundaries and existing ingestion/evaluation tools are strengths worth retaining.

Findings (code evidence, not assumed production incidents):

* `PgVectorRetrievalSearchRepository` uses a window rank over all eligible rows before LIMIT.
  This can prevent a bounded top-N sort. Actual plans and measurements are required before
  declaring it a bottleneck. The baseline query is retained for paired comparison.
* Structured codes are indexed but not used by baseline retrieval. Similar context does not
  guarantee the same requested action; this motivates soft reranking and independent code retrieval.
* `RetrievalEvidenceEvaluator.categoryMargin` subtracts cosine scores in result-rank order.
  Reranking can invert that order and produce a negative margin. Cosine evidence checks must
  remain independent of experimental ranking scores.
* Two references with the same category can still trigger a small-margin conflict. Official guide
  categories do not participate in agreement checks. Neither behavior is silently redefined here;
  reviewed counterexamples and a new threshold profile are needed before changing policy.
* `EvaluationMetricsCalculator` originally counts every supplied result, even beyond K; repeated
  returned keys can inflate recall, and duplicate expected keys change its denominator.
  Its precision is conditional on nonempty returned results. These metric definitions require
  an explicit version rather than comparing scores with different denominators.
* The human-reviewed directory contains only a README. The synthetic fixture has explicit
  `SYNTHETIC_VECTOR:*` tokens; its fake embedding implementation maps these tokens directly
  to hand-selected vectors. High scores are expected by construction, not evidence of semantic
  retrieval quality. There is no independent human review, difficulty distribution or held-out
  tuning/test split to establish generalization.
* `GeneralComplaintSimilarityExperimentService` reuses a corpus item's embedding as a query
  and excludes self hits. This is useful for pair similarity, but does not validate independently
  authored user questions or the three-channel evidence decision. Recreating its table and
  re-embedding the corpus on each run also differs from a frozen retrieval replay.
* `EvaluationRunnerService` ingests records and calls the full query service. It needs an isolated
  database: pre-existing records can enter search and fail the item-key mapping. Its source labels
  say synthetic even for other datasets. Use it for the existing synthetic smoke test, not as proof
  that data has been reviewed.

## Experimental policy

Keep API DTOs, stored cosine scores, risk codes and all three channels. Do not hard-filter a
candidate for a metadata mismatch. Use only existing PostgreSQL and Java components.
Metadata weights are experimental configuration, not learned or validated constants. Production
selection requires a separate reviewed development set and frozen held-out test set, with source
and duplicate groups kept in the same split. Include purpose contrasts, no-match, ambiguous,
missing metadata, risk signals and multiple relevant documents. Audit text/near-duplicate leakage
before tuning. Human-reviewed labels require a real reviewer, review date and documented provenance.

HNSW is deferred until scale measurements establish a need; there is no ANN recall or index-size
claim. Korean full-text search and pg_trgm are not enabled without a relevant language evaluation.
The code channel is exact structured-code matching, not Korean morphological lexical search.

## 구현 및 선택

| 전략 | 동작 | 상태 |
|---|---|---|
| BASELINE | 기존 window rank + cosine SQL | 기본값 유지, 원본 재현용 |
| EXACT | MATERIALIZED CTE에서 거리·ID Top-K 선택 후 본문 조회 | 순위 보존 성능 대안 |
| METADATA | EXACT 후보 20개에서 cosine + weight × metadata agreement | 실험 전용 |
| CODE_RRF | 벡터 후보와 코드 후보를 독립 조회한 뒤 RRF 결합 | 악화 확인, 기본값으로 미채택 |

메타데이터 점수는 대상·요청·위험 코드의 질의 측 포함 비율과 장소·관계·현재성 일치를
각각 계산해 6으로 나눈다. UNKNOWN은 일치 근거로 쓰지 않는다. 맥락은 검증 사례 payload에서
읽으며, 해당 필드가 없는 유형 기준·안내 자료는 가산점을 받지 않는다. 이 채널 간 차이도
검증 대상이다. 가중치 0/0.1/0.3을 비교했지만 최적값이라고 선정하지 않았다.
코드 후보는 대상/행위/위험 중 하나라도 일치하면 조회하고 일치한 코드 필드 수로 정렬한다.
위험 코드 불일치를 이유로 벡터 후보를 제거하지 않는다. 최종 Top-K 절단은 여전히 관련 위험
자료를 밀어낼 수 있으므로 위험 신호 보존과 위험 자료 recall은 서로 다른 검증 항목이다.

RRF는 각 목록의 `1/(rrfConstant+rank)` 합이며 기본 상수 60도 실험 설정이다.
동점은 item ID로 결정한다. 독립 후보 목록에서 역순인 두 문서는 동점이 날 수 있다.
코드 후보가 원래 벡터 후보 밖의 자료를 확보하는지는 PostgreSQL 통합 테스트로 확인했다.
모든 경로에 검수/활성/버전/유형 필터를 유지한다. 신규 인덱스나 외부 모델, 마이그레이션은 없다.

근거 평가에는 재정렬 점수를 전달하지 않는다. accepted hit와 category leader를 원래 코사인
점수 순으로 판단해 역전된 순위에서 음수 margin이 생기는 문제를 막았다. API와 DB의
`cosineSimilarity`는 실제 코사인 값이다. 기존 임계값은 변경하지 않았으며 후보 집합 변경의
판정 영향까지 검증됐다는 뜻은 아니다. 상충하는 공식 안내의 해석, 목적 불일치 거부,
같은 category의 중복 참조 처리 정책은 검수 사례 없이 임의로 변경하지 않았다.

## 측정 결과 — 2026-10-23

환경: Windows 11, Java 26.0.2.1, Docker Desktop Linux engine, PostgreSQL 17.11,
`pgvector/pgvector:pg17`. Spring Boot 4.0.6의 Java 26 지원은
[해당 버전 공식 소스](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.0.6/documentation/spring-boot-docs/src/docs/antora/modules/ROOT/pages/system-requirements.adoc)에서 확인했다.
이미지 태그는 mutable이므로 정확한 서버 버전과 실행 계획을 결과 파일에 남겼다.

### 검색 품질 및 조회 시간

문서 15개, 질의 8개, `synthetic-fake-embedding` 1,536차원, provisional-v1.
기존 synthetic fixture와 사람이 고른 가짜 벡터를 사용한 **기능 검증**이다.
학습이나 임계값 튜닝은 하지 않았다. 실제 임베딩 모델의 검색 품질과 무관하다.
3회 warmup 후 질의당 10회, 전략당 80개 표본. 전략 순서는 회전시켰다.

| 전략/가중치 | 사례 Recall@5 / MRR | 유형 Recall@3 / MRR | 근거 상태 정확도 | 조회 p50 / p95 (ms) |
|---|---:|---:|---:|---:|
| BASELINE | 1.000 / 1.000 | 1.000 / 1.000 | 1.000 | 3.2483 / 4.5261 |
| EXACT | 1.000 / 1.000 | 1.000 / 1.000 | 1.000 | 2.4611 / 3.1965 |
| METADATA / 0 | 1.000 / 1.000 | 1.000 / 1.000 | 1.000 | 2.5460 / 3.6275 |
| METADATA / 0.1 | 1.000 / 1.000 | 1.000 / 1.000 | 1.000 | 2.5699 / 3.8235 |
| METADATA / 0.3 | 1.000 / 1.000 | 1.000 / 1.000 | 1.000 | 2.5282 / 3.3026 |
| CODE_RRF | 1.000 / 1.000 | 0.833 / 0.833 | 0.875 | 5.0162 / 6.6962 |

사례의 관련 정답이 있는 질의는 **2개**, 유형·안내는 각각 **6개**에 불과하다.
사례 Hit@1/3/5는 모든 전략에서 1.0, 안내 Hit@1/3·Recall@3·MRR도 1.0이다.
유형 Hit@1/3은 baseline 1.0 → RRF 0.833이다. 유형·안내의 Hit@5는 API K=3 때문에
미측정(null)이다. 고정 K Precision은 사례 0.05, 안내 0.25, 유형은 0.2917 → RRF 0.2083.
기존 조건부 Precision은 사례 0.125이며 이는 개선치가 아니라 분모 차이다.

모든 전략의 전체 채널 no-match는 TP=2/FP=0/FN=0/TN=6, 위험 신호 보존율 1.0.
모호성은 baseline/EXACT/METADATA에서 TP=1/FP=0/FN=0/TN=7,
RRF에서 TP=0/FP=0/FN=1/TN=7이다. RRF의 모호성 recall은 0이고 precision은 undefined.
원본 점수·질의별 순위·설정·지표·입력 SHA-256은 [JSON 결과](results/synthetic-comparison.json)에 있다.

### 개선·악화 사례

* 동일 대상 LOST_ITEM에서 REPORT 0.95, SEARCH 0.94인 두 합성 후보를 두고 SEARCH를
  요청하면 weight=0.1 재정렬로 SEARCH가 1위가 된다. 단위 테스트로 확인한 기전이며
  실제 민원 개선 수치로 합산하지 않았다. 위험 코드와 원래 cosine은 그대로 유지된다.
* `query-ambiguous-synthetic`: baseline 유형 1·2위는 ambiguous-a/b로 cosine이 각각
  0.999852/0.999852이고 margin은 약 9.14e-8이다. RRF는 코드 일치 자료인 lost/cyber/patrol을
  위로 올려 두 상충 자료를 Top-3에서 밀어냈다. 결과가 AMBIGUOUS에서
  CASE_AND_REFERENCE_SUPPORTED로 바뀌었다. 단순 fusion이 모호성 근거를 제거한 실패다.
* 메타데이터 0/0.1/0.3의 aggregate 품질 변화는 없었다. 쉬운 합성 fixture의 천장 효과가
  있어 어느 가중치가 좋은지 결정할 수 없다. 유형 정보가 잘못 추출된 질의에서는 오히려
  관련 문서 순위를 낮출 수 있다. 검수된 hard-negative와 held-out 평가가 필요하다.

### SQL 성능

전체 합성 행 100/1,000/10,000개, 단일 VERIFIED_CASE, K=5, 3 warmup + 20회.
문서의 세 유형, DRAFT/비활성/다른 임베딩 버전을 섞었다. 벡터는 1,536차원이지만
두 성분만 nonzero인 합성 값이다. 동시 부하·실제 벡터 분포·cold cache 실험이 아니다.

| 전체 행 수 | Baseline p50 / p95 ms | EXACT p50 / p95 ms |
|---:|---:|---:|
| 100 | 2.4007 / 5.9786 | 1.8646 / 2.3509 |
| 1,000 | 2.7311 / 3.4747 | 2.4107 / 3.2059 |
| 10,000 | 10.9768 / 11.6643 | 10.1021 / 10.9499 |

10,000건에서 필터 통과 행은 2,398개였다. Baseline은 WindowAgg 앞 quicksort(584 kB),
EXACT는 Top-N heapsort(25 kB)와 Top-5 PK lookup을 사용했다. 실행 계획상의 서버
Execution Time은 9.019 → 8.206 ms, shared buffer hit는 7,727 → 7,742였다.
클라이언트 p50은 약 8%, p95는 약 6% 감소했다. 한 로컬 실행의 관측값이며 SLA가 아니다.
현재 계획은 기존 복합 인덱스를 쓰고 버전 불일치 400개를 추가 필터링한다. 버전별 행 비중이
커지면 필터 인덱스 변경을 별도 비교해야 한다. 지금은 새 인덱스의 쓰기 비용을 추가하지 않았다.

윈도 함수만 제거한 첫 시도는 10,000건 p50 10.9774 → 14.9986 ms로 **악화**했다.
정렬은 줄었지만 distance projection 반복으로 buffer hit가 7,727 → 14,921로 늘었다.
이를 근거로 거리·ID 선택을 materialize하고 payload projection을 LIMIT 이후로 옮겼다.
[실패한 시도](results/rejected-window-removal.txt)와 [최종 실제 SQL 실행 계획](results/exact-scale.txt)을
모두 보존했다. 합성 query vector 리터럴만 읽기 쉽게 치환했으며 계획 수치는 그대로다.

## 비용·제약과 미검증 항목

* 추가 모델·외부 인프라·API 호출은 0이다. 실제 OpenAI 호출 비용/시간은 측정하지 않았다.
* METADATA는 채널당 최대 max(K, candidateLimit)개를 가져와 Java에서 정렬한다.
  CODE_RRF는 SQL 왕복이 3→6회이며 이번 replay p50은 baseline보다 약 54% 증가했다.
* EXACT는 작은 CTE와 PK 재조회가 추가된다. 대규모 실제 corpus와 높은 동시성에서의
  효과는 아직 검증되지 않았다. baseline을 기본값으로 유지하므로 자동 운영 전환은 없다.
* JDBC 조회·매핑·재정렬 시간과 EXPLAIN 서버 시간은 각각 보고했다. 임베딩 API,
  HTTP E2E, 저장 트랜잭션 포함 E2E는 미측정이다. 가짜 벡터 시간을 API 시간으로 표시하지 않았다.
* 사람이 검수한 평가 데이터 0건. 실제 서비스 품질, 일반화, 한국어 형태소 검색 성능,
  ANN recall/속도/크기, 공식 문서 간 충돌 판단, 다수 위험 자료 회수율은 입증하지 못했다.
* 문서 신뢰성은 기존 VERIFIED/active gate만 사용한다. 검수 날짜·출처별 가중치를 새로
  정당화할 자료가 없어 임의 점수를 추가하지 않았다.

## 재현

Java 26과 실행 중인 Docker가 필요하다. 테스트는 별도 Testcontainers DB를 자동 생성하고
제거한다. 기존 로컬/운영 DB를 지우지 않는다. OPENAI_API_KEY는 필요 없다.

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jdk-26.0.2.1" # 설치 경로에 맞게 변경
.\gradlew.bat test
# 실험만 다시 실행 (기존 결과 캐시를 건너뛰기 위해 rerun 사용)
.\gradlew.bat test --tests '*EvaluationRunnerServiceIntegrationTest' --tests '*RetrievalSearchServiceIntegrationTest' --rerun-tasks
```

출력: `build/reports/retrieval/synthetic-comparison.json`, `build/reports/retrieval/exact-scale.txt`.
`docs/results/`는 위 표에 대응하는 고정 실행 스냅샷이며 재실행 시 자동 덮어쓰지 않는다.

검수된 별도 데이터로 재실행하려면 격리 DB에 지식을 기존 적재 도구로 넣고, 질의별
임베딩을 한 번만 생성해 캐시한다. 캐시는 JSON object이며 키는 queryKey, 값은
`{"vector":[1536개의 실수],"model":"text-embedding-3-large","dimensions":1536,"version":"embed-v1-large-1536"}`
형식이다. DB와 실제 model/version이 일치해야 하며 가짜 벡터에 실제 모델명을 붙이지 않는다.
캐시와 검수 데이터는 `.local-data/` 등 Git 제외 경로에 보관한다. 문서/질의의 개인정보 제거와
reviewer·검수일·출처 확인은 데이터 준비 책임이며 synthetic=false라는 플래그만으로 인증되지 않는다.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/ccas_evaluation'
$env:SPRING_DATASOURCE_USERNAME='ccas'
$env:SPRING_DATASOURCE_PASSWORD='<local evaluation password>'
$env:SPRING_FLYWAY_ENABLED='true'
.\gradlew.bat retrievalReplay '-Dreplay.dataset=.local-data/reviewed-test.json' '-Dreplay.embeddings=.local-data/query-embeddings.json' '-Dreplay.output=build/reviewed-replay.json' '-Dreplay.weights=0,0.1,0.3'
```

`replay.candidates=20`, `replay.rrf=60`, `replay.warmups=3`, `replay.repetitions=10`도 설정 가능하다.
운영 검색 전환은 `RETRIEVAL_SEARCH_STRATEGY=EXACT` 또는 METADATA/CODE_RRF이며 baseline으로
복귀하려면 BASELINE을 지정한다. 세 채널 K=5/3/3과 retrieval-only 응답 형식은 유지된다.
검수 dev에서 설정을 선정한 뒤 test에는 선정값을 고정하고, 결과를 보고 다시 튜닝하지 않는다.

## 테스트 결과

변경 전 baseline: `gradlew.bat test` — 107 tests, 실패/오류/skip 0.
1차 변경 후: 같은 명령 — 114 tests, 실패/오류/skip 0 (25 suites, 45초).
실제 PostgreSQL/pgvector Testcontainers 통합 테스트를 포함한다.
재정렬 목적 대비, UNKNOWN 처리, RRF 중복 제거와 동점, eligibility 필터, 무효 벡터,
재정렬 후 cosine margin, 다중 정답/중복/K 범위, 저장 벡터 재사용·모델 불일치 거부,
baseline/EXACT 결과 동등성 및 3개 규모의 실행 계획을 확인했다.
`git diff --check`도 통과했다. 초기 설정 바인딩 실패는 canonical constructor binding 지정으로,
RRF 테스트의 잘못된 역순 기대는 실제 동점 규칙에 맞춰 수정했다.

추천 커밋 메시지: `feat(retrieval): add frozen replay benchmarks and configurable exact and reranking strategies`

## 변경 파일 목록

아래 경로는 저장소 루트 기준이다. 커밋과 push는 수행하지 않았다.

- `build.gradle`
- `docs/results/exact-scale.txt`
- `docs/results/rejected-window-removal.txt`
- `docs/results/synthetic-comparison.json`
- `docs/retrieval-evaluation.md`
- `docs/retrieval-optimization.md`
- `README.md`
- `src/main/java/com/example/ccas/retrieval/application/evaluation/RetrievalEvidenceEvaluator.java`
- `src/main/java/com/example/ccas/retrieval/application/query/RetrievalQueryTransactionalExecutor.java`
- `src/main/java/com/example/ccas/retrieval/application/search/CandidateRanker.java`
- `src/main/java/com/example/ccas/retrieval/application/search/RetrievalSearchService.java`
- `src/main/java/com/example/ccas/retrieval/config/RetrievalSearchProperties.java`
- `src/main/java/com/example/ccas/retrieval/evaluation/ChannelMetrics.java`
- `src/main/java/com/example/ccas/retrieval/evaluation/EvaluationMetricsCalculator.java`
- `src/main/java/com/example/ccas/retrieval/evaluation/RetrievalReplayExperiment.java`
- `src/main/java/com/example/ccas/retrieval/evaluation/RetrievalReplayMain.java`
- `src/main/java/com/example/ccas/retrieval/infrastructure/persistence/PgVectorRetrievalSearchRepository.java`
- `src/main/resources/application.properties`
- `src/test/java/com/example/ccas/retrieval/application/evaluation/RetrievalEvidenceEvaluatorTest.java`
- `src/test/java/com/example/ccas/retrieval/application/search/CandidateRankerTest.java`
- `src/test/java/com/example/ccas/retrieval/application/search/RetrievalSearchServiceIntegrationTest.java`
- `src/test/java/com/example/ccas/retrieval/evaluation/EvaluationMetricsCalculatorTest.java`
- `src/test/java/com/example/ccas/retrieval/evaluation/EvaluationRunnerServiceIntegrationTest.java`
