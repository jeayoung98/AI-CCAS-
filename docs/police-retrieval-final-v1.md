# 경찰관 업무 지원 검색 모듈 최종 평가

기준일: 2026-11-01. 이번 작업의 완료 범위는 retrieval-only 모듈과 경찰관 관점의
평가 자료 준비·실제 임베딩 검색 비교·재현 검증·문서화다. 새 자료 수집과 기능 확장은
여기서 종료한다. 최종 민원 분류·답변 생성·신고 접수는 프로젝트의 구현 범위에 포함되지 않는다.

## 같은 조건에서 비교한 결과

경찰청 공개 질의에서 AI 점검 후 선정한 55건을 40개 질의 그룹으로 묶고 개발 27건,
평가 28건으로 고정했다. 후보 문서는 유형 기준 4개, 공식 안내 16개다.
주 라벨은 실행 전에 정한 gpt-4o의 직접 근거 판단 1,100쌍이며 다른 모델과의 판단 차이는
보존했다. 임베딩은 text-embedding-3-large, 1536차원이다.

개발 공식 안내 MRR로 가중치 0.05/0.1/0.3 중 선택하고 동률은 작은 값으로 결정했다.
METADATA와 COSINE_CODE_RRF 모두 0.1이 선택됐다. 이 규칙과 입력 해시를 실행 전에
[경찰 55건 벤치마크](results/benchmark-police55-officer-ai-v1.json)로 고정했다.

| 평가용 공식 안내 | Recall@3 | Precision@3 | MRR@3 | Hit@1 |
|---|---:|---:|---:|---:|
| BASELINE | 81.94% | 23.81% | 0.7407 | 61.11% |
| METADATA 0.1 | 95.83% | 27.38% | 0.7593 | 55.56% |
| COSINE_CODE_RRF 0.1 | 98.61% | 28.57% | 0.7500 | 55.56% |
| CODE_RRF | 87.50% | 25.00% | 0.6944 | 55.56% |

Recall·MRR·Hit@1은 관련 문서가 있는 18개 질의 기준이다. Recall은 질의별 회수율의 평균이며,
전체 관련 문서 개수를 단순히 나눈 값과 다르다. Precision은 관련 문서가 없는 질의도 포함한
전체 28건과 고정 K=3 기준이다. 유형 기준은 관련 문서가 있는 7건에서 세 전략의 Recall과
MRR이 모두 1.0이었다. CODE_RRF의 MRR은 0.9048이었다. 후보가 4개로 작아 구별력이 제한된다.

COSINE_CODE_RRF는 기본보다 공식 안내 Recall이 16.67%p 높지만 Hit@1은 5.56%p 낮다.
상위 한 개의 적중은 11/18에서 10/18로 줄었다. METADATA는 MRR이 가장 높았다.
따라서 Recall 하나만으로 단일 최적 전략이나 운영 채택을 선언하지 않는다.
운영 기본 전략과 임시 임계값은 그대로 유지했다.

## 재현·검증

운영 `SearchTextBuilder`와 `CandidateRanker`를 사용해 같은 저장 벡터로 모든 전략을
비교했다. 네트워크 주소를 로컬의 연결 불가능한 주소로 바꾸고 재실행했을 때도 벡터 해시,
선택 가중치와 모든 점수가 일치했다. 이는 평가의 재현성 확인이며 외부 API 지연을 측정한
성능 수치가 아니다. 기존 500건 벤치마크의 입력도 보존했다.

평가 도구의 500건 고정 가정을 `pool.expectedCount` 설정으로 바꿨다. 기본값 500을 유지하고
55건을 명시할 수 있게 했으며, 비양수 값은 거절한다. API/운영 검색 로직을 바꾼 것은 아니다.

## 테스트 완료 상태와 환경 제한

- Python 테스트 100개 통과.
- Docker 비의존 Java 테스트 579개 통과. 기존 테스트 설정은 바꾸지 않고 Git 제외
  임시 init 스크립트로 Docker 의존 클래스 8개를 명시적으로 제외한 실행이다.
- 전체 Java 테스트 최초 실행은 587개 중 Docker 초기화 오류 8개로 실패했다.
  이는 DB 테스트를 통과한 것으로 처리하지 않는다.
- Docker Desktop 시작을 시도했지만 `dockerInference` Unix 소켓을 Windows에서
  접근하지 못하는 오류로 백엔드가 종료됐다. Windows 서비스 시작도 권한 오류였다.
  초기화·데이터 삭제·보안 설정 변경은 수행하지 않았다.
- 따라서 이번 55건의 일회용 pgvector·HTTP 재생은 실행하지 못했다. 이전 500건의
  DB/API 검증 기록과 구분한다. 실행 도구는 55건도 명시할 수 있게 준비했다.
- 실제 임베딩 오프라인 비교와 네트워크 없는 재생은 모두 완료했다.
  입력 고정 검증과 기존 500건 벤치마크 검사도 통과했다.

구현·오프라인 평가·문서화는 이번 작업의 산출물로 마감하고, 현재 환경에서의
DB/API 재검증은 위 Docker 오류를 해결한 뒤 실행할 수 있는 미완료 검증으로 남긴다.

## 결과를 해석할 범위

- AI 라벨은 사람 정답이 아니다. 두 모델은 104/1,100쌍에서 직접 근거 여부가 달랐다.
- 평가 질의 28건과 문서 20개의 작은 풀에서 얻은 결과다. 경찰 현장 민원 전체나 대규모
  지식 저장소를 대표한다고 보지 않는다. 기존 다른 500건의 95.62%·73.37%와 직접 비교하지 않는다.
- VERIFIED_CASE 후보와 최종 근거 상태 정답이 없어 사례 검색·근거 부족 거부·모호성의
  실제 정확도를 이 55건으로 입증하지 않는다. 위험 플래그의 코드상 보존과 의미 정확도도 구분한다.
- 후보는 DRAFT이며 이용 검토·운영 적재 승인과 실험 결과는 별개다. 최종 분류 정확도,
  범죄 성립 판단, 상용 서비스 완성으로 표현하지 않는다.

## 산출물과 변경 파일

- `PublicPoolExperimentMain.java`, 관련 테스트, `PublicPoolDatabaseExperiment.java`: 평가 건수 설정.
- `docs/results/benchmark-police55-officer-ai-v1.json`: 고정 입력·선택 규칙.
- `docs/results/police-pool-experiment-v1.json`: 실제 임베딩 비교 집계.
- 이 보고서, README 현재 상태, portfolio-notes.md의 최신 설명.
- 이전 단계의 자료 수집·AI 점검·그룹 분할·구조화·라벨 도구와 보고서는 작업별 커밋으로 보존한다.

## 재현 명령

```powershell
. .\tools\Use-Gms.ps1
$env:JAVA_HOME='C:\Users\SSAFY\.jdks\jdk-26.0.2.1'
.\gradlew.bat publicPoolExperiment '-Dpool.expectedCount=55' '-Dpool.queries=.local-data/police-structure-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/officer_documents_v1.json' '-Dpool.qrels=.local-data/police-officer-relevance-v1/qrels' '-Dpool.output=.local-data/police-pool-experiment-v1' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.benchmarkLock=docs/results/benchmark-police55-officer-ai-v1.json'
```

개별 질의·판단 이유·벡터·인증값은 Git 제외 로컬 경로에만 보관한다. 공개 변경에는
집계·출처 목록·고정 해시를 포함한다.

누적 작업은 평가 기반, 경찰관 관련성 기준, 공개 질의 데이터 준비, 최종 비교 결과의
네 커밋으로 분리한다. 커밋은 로컬에 저장하며 원격 푸시는 수행하지 않는다.
