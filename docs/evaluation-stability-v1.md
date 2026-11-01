# 평가 기준 고정과 다른 모델의 교차 평가

실행일: 2026-10-29. 앞선 Recall@3 차이를 검색 코드의 성능 변화와 평가 조건 변화로
구분하기 위해 교차 평가와 고정 벤치마크 검증을 추가했다. 이전 작업 파일과 라벨은 보존했다.

## 무엇을 비교했는가

이전 두 판단은 주로 `gpt-4o-mini` 기반이다. 이번에는 GMS에서 확인한
`gpt-4o-2024-08-06`으로 아래 항목을 평가했다. 이전 정답, 이유, 검색 점수와
질의의 구조화 코드는 모델에 전달하지 않았다. 관련성 평가에는 동일한 20개 문서를
문맥으로 제공하고 선택된 문서만 판단하게 했다. 위험 평가는 마스킹된 글만 사용했다.

| 평가 대상 | 불일치 항목 | 기존 일치 대조군 | 합계 |
|---|---:|---:|---:|
| 민원–문서 관련성 | 339쌍 | 50쌍 | 389쌍 |
| 위험 코드 집합 | 49건 | 50건 | 99건 |

관련성 대상은 245개 질의에 걸쳐 있고 위험 99건과 함께 344개 모델 요청을 완료했다.
대조군은 고정 난수로 선택했으며 가능한 범위에서 양성·음성을 나눠 뽑았다.
따라서 이 표본의 비율을 전체 500건 정확도나 전체 10,000쌍의 오류율로 확장하면 안 된다.

## 교차 평가 결과

| 대상 | 결과 |
|---|---|
| 관련성 불일치 339쌍 | 이전 판단 지지 169, 새 블라인드 판단 지지 170 |
| 관련성 일치 대조군 50쌍 | 기존 일치 유지 42, 반대 판단 8 |
| 위험 불일치 49건 | 이전 구조화 지지 30, 블라인드 판단 지지 9, 양쪽과 다른 코드 집합 10 |
| 위험 일치 대조군 50건 | 기존 일치 유지 44, 반대 판단 6 |

관련성 평가 389쌍 모두 모델은 HIGH 확신도를 보고했다. 그런데 기존 일치 대조군에서도
8쌍을 뒤집었다. 이는 어느 판단이 틀렸다는 증명이 아니며 **확신도만으로 라벨의 신뢰성을
확정할 수 없다는 관찰**이다. 같은 제공자의 다른 모델들도 학습 자료와 오류를 공유할 수 있다.

488개 평가 항목 중 450개는 교차 모델의 잠정 지지가 있고, 38개는 낮은 확신도,
기존 일치의 반대 판단, 양쪽과 다른 위험 코드 집합 때문에 추가 재검토 대상으로 표시했다.
관련성은 쌍, 위험은 질의 단위이므로 이 숫자는 488개 고유 민원이라는 뜻이 아니다.
잠정 지지는 사람 검수 정답이나 운영 승인으로 승격하지 않는다.

## 불일치가 집중된 문서

| 문서 | 관련성 불일치 쌍 수 |
|---|---:|
| 고충민원의 범위와 일반 문의·신고 구분 | 189 |
| 고충민원 작성과 신청 방법 | 106 |
| 수사 이의·수사관 기피 신청 | 29 |
| 경찰 직무집행 손실보상 | 12 |
| 국민신문고 소개 | 2 |
| 경찰민원 처리 결과 확인 | 1 |

339쌍 중 295쌍(87.0%)이 고충민원 범위와 신청 방법 문서에 집중됐다.
일반적인 유형 설명이나 신청 안내를 구체적인 민원의 관련 근거로 인정하는 범위가
주요 분석 대상이라는 추론을 할 수 있다. 이 집계만으로 모든 불일치의 원인이 같다고
확정하지는 않는다. 개별 판단과 출처 구간은 Git 제외 로컬 비교 파일에 보존했다.

다음 라벨 규칙을 개정할 때는 유형 기준 채널의 ‘상황 유형을 설명하는 근거’와
공식 안내 채널의 ‘현재 요청에 직접 도움이 되는 절차’를 구분해야 한다.
일반 배경 설명과 직접 답변 가능한 근거를 별도 등급으로 다루는 방안이 후속 검토 대상이다.
이번 작업에서는 이 새 정의로 과거 정답을 소급 변경하거나 지표를 다시 유리하게 선택하지 않았다.

## 같은 벤치마크를 사용하는 장치

고정 ID는 **`public-500-pool20-ai-v3`**다. 고정 상태는 **AI 임시 라벨**이며
정답의 의미적 정확성을 인증한 상태가 아니다.

- 구조화 질의 500건, 문서 후보 20개, 관련성 라벨, 해결된 질의·분할 정보 및 요약,
  임베딩·임계값 설정 파일의 SHA-256을 기록했다.
- 고정 파일 생성은 기존 파일 덮어쓰기를 거부한다.
- Java 평가 실행에 `pool.benchmarkLock`을 주면 입력 해시가 하나라도 다를 때 중단한다.
- 잠금 없이 실행하면 보고서에 `UNLOCKED_EXPERIMENT`를 기록한다.
- 라벨·후보·질의가 바뀌면 새 버전을 발행하고, 다른 버전끼리의 점수를 같은 조건의
  개선율처럼 표현하지 않는다.
- 이미 반복 사용한 test 400건이다. 고정했다고 새로운 독립 holdout이 되지는 않는다.

API 키를 제거하고 원격 연결이 불가능한 주소를 사용해 고정 벤치마크를 재실행했다.
기존 확장 후보 실험과 **선택 가중치, 전체 지표, 실제 벡터 해시, 입력 해시가 모두 동일**했다.
공식 안내 Recall@3은 기존과 동일하게 BASELINE 67.05%, COSINE_CODE_RRF 0.1은
73.37%다. 이번 단계의 성과는 새로운 Recall 향상이 아니라 평가 조건을 고정하고
재현 여부를 확인한 것이다. 교차 평가 결과는 원본 qrels나 구조화 입력에 병합하지 않았다.

## 재현

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/cross_model_audit.py --output .local-data/cross-model-audit-v1

.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/benchmark_lock.py --lock docs/results/benchmark-public-500-pool20-ai-v3.json --verify

.\gradlew.bat publicPoolExperiment '-Dpool.queries=.local-data/acrc-structured-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/relevance_documents_v3.json' '-Dpool.qrels=.local-data/acrc-blind-v3/qrels' '-Dpool.output=.local-data/public-pool-locked-v3' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.benchmarkLock=docs/results/benchmark-public-500-pool20-ai-v3.json'
```

로컬 원본·라벨 파일은 Git에서 제외하므로 해당 파일이 없는 환경에서는 잠금 검증이나
실험 재생이 불가능하다. 배포용 데이터셋을 공개했다는 의미가 아니다.

## 검증과 변경 파일

- Python 전체 테스트 **64개 통과**.
- Java 전체 테스트 **626개 통과**, 실패·오류·건너뜀 0개.
- 모델 호출 344개 완료, 최종 실패 0개. 체크포인트 재실행은 API 없이 완료됐다.
- 라벨 변경·불완전한 잠금·작업 폴더 밖 경로 거부, 대조군 재현성과 원본 판단 보존을 검증했다.

이번 단계의 변경:

- `tools/public_complaints/cross_model_audit.py`, `test_cross_model_audit.py`
- `tools/public_complaints/benchmark_lock.py`, `test_benchmark_lock.py`
- `src/main/java/com/example/ccas/retrieval/evaluation/PublicPoolExperimentMain.java`
- `src/test/java/com/example/ccas/retrieval/evaluation/PublicPoolExperimentMainTest.java`
- 이 문서, README의 연결 및 아래 결과 JSON 3개.

결과:

- [교차 모델 평가 집계](results/cross-model-audit-v1.json)
- [고정 벤치마크](results/benchmark-public-500-pool20-ai-v3.json)
- [동일 결과 재현 확인](results/locked-replay-verification-v1.json)

추천 커밋: `feat(evaluation): audit disputed labels across models and freeze benchmark inputs`
