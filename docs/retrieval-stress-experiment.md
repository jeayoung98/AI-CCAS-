# 500개 합성 시나리오 재정렬 실험

실행일: 2026-10-23. 기존 8질의 실험에서 발견한 RRF의 상충 근거 누락을 재현하고,
메타데이터 활용의 이점과 손해를 분리해 확인했다. 기본 운영 전략은 BASELINE으로 유지한다.

## 개선 가설과 구현

1. **코사인을 기본으로 두고 RRF의 영향에 상한을 둔다.** 순위만 결합하는 CODE_RRF는
   cosine 0.0인 문서를 높은 의미 유사도의 문서보다 위로 올릴 수 있다.
   새 `COSINE_CODE_RRF`는 원래 cosine에 제한된 fusion 가산점만 더한다.
2. **목적 일치 재정렬의 부작용까지 측정한다.** METADATA는 가까운 점수의 REPORT/SEARCH를
   구별할 수 있지만 앞단의 목적 추출이 틀리면 반대로 작동한다. 잘못된 메타데이터를 별도
   시나리오로 평가한다. 이 실험에서 메타데이터 추출기를 새로 구현하거나 정답을 입력하지 않았다.
3. **이전의 쉬운 8개 fixture를 보완한다.** 후보 누락, 코드 일치 오탐, UNKNOWN, 동점,
   다중 정답, 위험 정보까지 포함한다. 정답을 맞히는지와 코드의 불변조건을 분리해서 검증한다.

새 점수:

```text
fusion = sum(1 / (rrfConstant + rank))  # vector/code 두 후보 목록
normalizedFusion = (rrfConstant + 1) * fusion / 2
rankingScore = cosineSimilarity + metadataWeight * normalizedFusion
```

normalizedFusion은 0~1, 따라서 가산점은 최대 metadataWeight이다. 이 설정명은 기존
METADATA에서 쓰던 실험 가중치를 재사용한 것으로, 새 전략에서는 fusion 상한을 뜻한다.
예를 들어 weight=0.05이면 cosine 차이가 0.05를 초과하는 두 후보의 순서를 뒤집을 수 없다.
차이가 작은 후보나 최종 Top-K 경계의 상충 문서까지 무조건 보존한다는 보장은 없다.
API/DB의 cosine 값은 수정하지 않고 기존 근거 임계값도 유지한다. 추가 모델은 없다.

## 500개의 의미와 데이터 분할

**500개는 사람이 작성·검수한 민원이나 실제 임베딩 질의가 아니다.** 사람이 설계한
10개 시나리오 유형에서 코드와 점수 차이를 생성한 순위 스트레스 사례이다.
각 사례에 후보 문서 12개가 있고 벡터/코드 후보를 각각 최대 5개 확보해 최종 3개를 반환한다.
이 실험의 후보 생성은 Java에서 SQL의 코드 일치 순서를 모사하며 PostgreSQL을 호출하지 않는다.
실제 SQL 검증은 아래의 별도 8질의 pgvector 통합 실험으로 구분한다.

| 유형 | 개발 | 최종 비교 | 확인할 내용 |
|---|---:|---:|---|
| purpose-near | 10 | 40 | 의미는 비슷하지만 요청 목적이 다름 |
| wrong-metadata | 10 | 40 | 목적 코드 추출 오류 |
| unknown-metadata | 10 | 40 | UNKNOWN이 유사성 근거가 되지 않음 |
| lexical-distractors | 10 | 40 | 코드만 일치하고 의미는 약한 문서 |
| ambiguity | 10 | 40 | 두 상충 근거가 함께 남아야 함 |
| no-match | 10 | 40 | 모든 문서가 코사인 임계값 미만 |
| risk | 10 | 40 | 위험 자료 순위와 위험 플래그 보존 |
| multiple-relevant | 10 | 40 | 복수 정답의 Recall 계산 |
| outside-vector | 10 | 40 | 정답이 벡터 후보 범위 밖에 있음 |
| ties | 10 | 40 | 동점에서 재현 가능한 순서 |
| 합계 | 100 | 400 | 총 500개 |

seed는 dev=2026102301, test=2026102302다. 서로 다른 수치 표본이지만 시나리오 유형은
공유하므로 독립적인 의미 일반화 검증이나 실사용 분포를 대표하지 않는다.
dev에서 새 전략의 weight 0.05/0.1/0.3을 비교했다. 선정 규칙은 MRR+모호성 recall 최대,
동점이면 작은 weight다. 모두 같은 dev 품질이어서 0.05가 선정됐다. 이후 test 결과로
설정을 바꾸지 않았다. METADATA 0.1은 앞선 실험에서 정한 고정 비교군이다.

## 최종 비교용 400개 결과

정답 문서가 있는 360개에서 Hit@1/Recall@3/MRR을 계산하고, Precision@3은 no-match까지
포함한 400개를 사용했다. 모호성 40개, no-match 40개에 대해 판정 기대값을 정의했다.
나머지 320개의 근거 상태 정답은 임의로 만들지 않았으며 판정 정확도 계산에서 제외한다.

| 전략 | Hit@1 | Recall@3 | Precision@3 | MRR | 모호성 recall | no-match recall |
|---|---:|---:|---:|---:|---:|---:|
| BASELINE | 44.44% | 88.89% | 33.33% | 0.6667 | 100% | 100% |
| METADATA 0.1 | 54.44% | 88.89% | 33.33% | 0.7167 | 100% | 100% |
| CODE_RRF | 66.67% | 77.78% | 26.67% | 0.7222 | 0% | 100% |
| COSINE_CODE_RRF 0.05 | 44.44% | 88.89% | 33.33% | 0.6667 | 100% | 100% |

**Hit@1만 보면 CODE_RRF가 좋아 보이지만 관련 근거의 회수와 모호성 감지를 악화시켰다.**
새 방식은 그 악화를 막았으나 Baseline보다 검색 품질을 높이지는 못했다.

유형별 결과:

* purpose-near: METADATA의 Hit@1은 baseline 0/40 → 16/40.
* wrong-metadata: METADATA는 40/40 → 24/40으로 악화. 추출 오류가 있으면 가산점이
  잘못된 자료를 올린다. 전체 점수 상승만으로 운영 채택하면 놓치게 되는 실패다.
* risk: 관련 위험 자료 Hit@1은 METADATA에서 0/40 → 19/40. 위험 플래그는 모든
  실행에서 보존했다. 플래그 보존과 자료 검색 성공은 서로 다른 결과다.
* multiple-relevant: METADATA의 Hit@1은 0/40 → 17/40, Recall@3은 그대로다.
* lexical-distractors와 ambiguity: CODE_RRF는 관련 문서를 Top-3 밖으로 밀어냈다.
  새 방식은 두 유형에서 baseline과 같은 Recall을 유지했다.
* outside-vector: CODE_RRF만 정답을 최종 순위에 넣었다. 새 방식은 의미 점수 격차가
  큰 이 사례를 해결하지 못했다. 보수적인 가산점의 이점과 손해가 함께 드러난다.

전체 설정·개발 결과·유형별 결과·400개 개별 결과는
[stress-500.json](results/stress-500.json)에 저장했다.
모든 500개 입력과 후보 점수는 재실행 시 `build/reports/retrieval/stress-500-inputs.json`에 생성된다.

## 실제 PostgreSQL 회귀 실험

기존 합성 문서 15개, 질의 8개, 가짜 임베딩을 동일하게 재사용했다. warmup 3회,
질의당 10회 반복으로 전략당 80개 조회 시간 표본을 측정했다. 아래 시간은 3채널 JDBC
조회·매핑·재정렬 시간이며 임베딩 API/HTTP E2E가 아니다.

| 전략 | 근거 상태 정확도 | 모호성 recall | p50 / p95 ms |
|---|---:|---:|---:|
| BASELINE | 100% | 100% | 2.8366 / 4.1920 |
| EXACT | 100% | 100% | 2.0154 / 2.4492 |
| METADATA 0.1 | 100% | 100% | 2.1072 / 2.5622 |
| CODE_RRF | 87.5% | 0% | 4.2147 / 5.8432 |
| COSINE_CODE_RRF 0.05 | 100% | 100% | 4.1251 / 5.5590 |

새 방식은 기존 RRF 대비 상충 근거 누락 1건을 회복했다. 그러나 SQL 왕복은 여전히 6회로,
BASELINE/EXACT의 3회보다 많다. p50은 BASELINE보다 약 45%, EXACT보다 약 105% 길었다.
가짜 임베딩 실험이므로 OpenAI 비용/지연은 측정하지 않았다.
[전체 pgvector 결과](results/synthetic-comparison-v2.json)는 이전 스냅샷과 별도 보존했다.

## 결정과 후속 개선

* 기본 전략 변경 없음. 정확한 순위 보존과 성능이 우선이면 기존 EXACT를 검토한다.
* METADATA는 올바른 구조화 정보가 있는 조건에서만 이점이 있다. 실제 검수 평가에서
  추출 오류율·certainty별 성능을 확인하고 가중치를 선정해야 한다. 합성 점수 상승만으로
  실서비스 향상을 주장하거나 0.1을 최적값으로 확정하지 않는다.
* COSINE_CODE_RRF는 RRF 실패의 완화안으로 보존하되 추가 SQL 비용을 정당화할 순위
  개선이 없어 운영 기본값으로 채택하지 않는다.
* 다음 실험은 검수 질의와 동결된 실제 임베딩으로 수행해야 한다. UNKNOWN/UNCERTAIN과
  잘못 추출된 목적을 포함하고, 최종 Top-K와 별도로 후보 풀의 상충 근거를 평가하는 방안을
  비교한다. 후보 풀 확대는 오탐 모호성도 늘릴 수 있으므로 판정 precision도 함께 검증해야 한다.

## 테스트와 재현

500개 동적 테스트는 전략별 중복·순위·원래 cosine·위험 코드 보존·결정적 정렬·no-match
거부를 검사한다. 검색 정답률을 100%라고 강제하지 않으며 실패한 검색도 실험 결과에 기록한다.
기존 114개 + 동적 500개 + 리포트 생성/개발 선정 검증 1개 = **615 tests**.
Java 26, Docker PostgreSQL 통합 테스트를 포함해 실패/오류/skip 모두 0, 전체 45초.

```powershell
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jdk-26.0.2.1" # 실제 설치 경로
.\gradlew.bat test
.\gradlew.bat test --tests '*RetrievalStressExperimentTest' --tests '*EvaluationRunnerServiceIntegrationTest' --rerun-tasks
```

첫 번째 stress 테스트만 실행할 때는 Docker와 OPENAI_API_KEY가 필요 없다.
PostgreSQL 회귀 테스트에는 Docker가 필요하다. 결과물은 `build/reports/retrieval/`에 생성된다.
운영 검색에서 실험하려면 `RETRIEVAL_SEARCH_STRATEGY=COSINE_CODE_RRF`,
`RETRIEVAL_SEARCH_METADATA_WEIGHT=0.05`를 명시한다. 이 값은 합성 실험 설정이며 운영 권장값이 아니다.

이번 후속 작업의 변경 파일:

* `src/main/java/com/example/ccas/retrieval/config/RetrievalSearchProperties.java`
* `src/main/java/com/example/ccas/retrieval/application/search/CandidateRanker.java`
* `src/main/java/com/example/ccas/retrieval/application/search/RetrievalSearchService.java`
* `src/main/java/com/example/ccas/retrieval/evaluation/RetrievalReplayMain.java`
* `src/test/java/com/example/ccas/retrieval/application/search/RetrievalStressExperimentTest.java` (신규)
* `src/test/java/com/example/ccas/retrieval/evaluation/EvaluationRunnerServiceIntegrationTest.java`
* `docs/retrieval-stress-experiment.md` (이 문서), `docs/results/stress-500.json`,
  `docs/results/synthetic-comparison-v2.json` (신규)
* `README.md`, `docs/retrieval-optimization.md`

추천 커밋 메시지: `feat(retrieval): evaluate bounded cosine RRF with 500 synthetic stress cases`
