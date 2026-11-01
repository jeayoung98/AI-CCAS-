# 경찰 질의 55건의 pgvector·HTTP 재검증

확인일: 2026-11-01. 이전에 Docker 시작 오류로 미완료였던 DB/API 검증을 완료했다.
이번 실행 시작 시 Docker 엔진이 정상 응답했다. 소켓 파일 삭제·폴더 변경·초기화·설정
수정을 수행하지 않았으며, 이전 오류가 어떤 조치로 해소됐는지는 확인하지 않았다.

## 이번 할 일과 완료 상태

| 작업 | 상태 | 결과 |
|---|---|---|
| Docker 사용 가능 여부 확인 | 완료 | 엔진 응답 확인, 로컬 복구 조치 불필요 |
| 고정 55건의 DB/API 재생 | 완료 | 실제 pgvector 검색과 HTTP 요청 55건 성공 |
| Docker를 포함한 전체 테스트 | 완료 | Java 627개, Python 100개 통과 |
| 결과 보고서 갱신·후속 커밋 | 완료 | 집계·재현 명령·제한 기록, 로컬 커밋 |

## 확인한 동작

일회용 Testcontainers PostgreSQL + pgvector에 후보 20개와 저장된 실제 임베딩 벡터를
적재했다. 테스트 DB의 VERIFIED 표시는 운영 코드의 검색 조건을 통과시키기 위한 fixture
표시이며 원래 후보 파일은 DRAFT 상태를 유지한다. 실제 운영 DB에는 적재하지 않았다.

- BASELINE, METADATA, CODE_RRF, COSINE_CODE_RRF의 두 검색 채널 순위를 메모리
  계산과 비교했고, 55건 모두 일치했다. 코사인 값도 허용 오차 이내였다.
- 먼저 기존 기본 가중치 0.3으로 재생한 뒤, 개발 자료에서 선택된 가중치 0.1을 명시해
  다시 검증했다. 이 보고서의 집계는 0.1 실행이다. CODE_RRF는 별도의 가산 가중치가 없다.
- 기본 검색 전략의 실제 `/api/vector/retrieve` HTTP 요청 55건이 모두 200이었다.
- 위험 신호 코드가 있는 입력 5건을 포함해 모든 요청의 riskSignalPresent가 입력 목록과
  일치했다. API 응답에는 embedding 배열이 노출되지 않았다. 이는 위험 추출 정확도와 다르다.
- DB의 retrieval_query와 retrieval_decision 로그도 각각 55건 생성됐음을 확인했다.
- API의 사례 채널은 후보가 없으므로 비어 있었다. 이를 사례 없음 정답으로 쓰지 않는다.

[집계 JSON](results/police-database-http-v1.json)의 질의·후보 해시가 고정 벤치마크와
일치하는지 확인했다. 개별 질의·순위 내역·벡터는 Git 제외 로컬 경로에만 보관한다.

## 이번 실행에서 드러난 제한

임시 `provisional-v1` 임계값에서는 55건 모두 INSUFFICIENT_EVIDENCE였다. 안내 임계값은
0.7, 유형 기준은 0.75다. 따라서 검색 순위가 라벨 기준으로 개선됐다는 결과를 현재
서비스가 충분한 근거를 잘 채택한다는 결론으로 확장할 수 없다.

다음 개선 작업의 우선순위는 개발 자료에서 근거 채택 임계값을 교정하고, 평가 자료의
직접 근거 채택과 잘못된 채택을 함께 측정하는 것이다. 정답 근거 상태가 없으므로
AI 관련성 라벨에 대한 채택 분석과 실제 근거 충분성 정확도를 구분해야 한다.
이번에는 운영 임계값을 변경하지 않았다.

지연 집계는 첫 10건을 제외한 45개 표본이다. 20개 문서의 작은 풀과 디스크 캐시 임베딩을
사용한 로컬 측정이므로 대규모 DB 성능이나 외부 GMS 응답 지연을 나타내지 않는다.
운영 HTTP는 기본 전략만 검증했고, 개선 전략들은 DB 검색 경로에서 순위 일치를 검증했다.

## 실행·변경 파일

```powershell
$env:JAVA_HOME='C:\Users\SSAFY\.jdks\jdk-26.0.2.1'
.\gradlew.bat --console=plain publicPoolDatabaseExperiment '-Dpool.expectedCount=55' '-Dpool.rerankWeight=0.1' '-Dpool.queries=.local-data/police-structure-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/officer_documents_v1.json' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.output=.local-data/police-pool-database-selected-v1'
.\gradlew.bat --console=plain test
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -q
```

변경 파일은 `PublicPoolDatabaseExperiment.java`의 평가용 가중치 설정 및 집계 필드,
집계 JSON, 이 보고서, 최종 평가 보고서·README·포트폴리오 설명이다.
평가용 `pool.rerankWeight`의 기본값은 이전 실험과 같은 0.3이며 이번에는 0.1을 명시했다.
운영 Java 코드는 변경하지 않았다.

추천 커밋: `test(evaluation): verify frozen police queries through pgvector and HTTP`
