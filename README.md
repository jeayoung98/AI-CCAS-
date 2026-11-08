# AI-CCAS · 경찰 민원 근거 검색과 답변 생성

경찰관의 민원 안내 업무를 지원하는 RAG 백엔드입니다. 개인정보를 제거한 구조화 민원을 받아 유사 사례·유형 기준·공식 안내를 검색합니다. 답변 API는 근거가 충분할 때 공식 안내에서 인용문을 찾아 민원인에게 전달할 한국어 답변을 작성합니다.

## 구현 기능

- **입력 검증:** 개인정보 마스킹 완료 여부와 구조화 민원의 필수 항목 검증
- **검색용 텍스트 생성:** 상황·관찰 사실·위험 신호·요청 목적을 Java의 고정 템플릿으로 구성
- **임베딩 생성:** OpenAI 임베딩 API 연동, 모델·차원·버전 설정 관리
- **근거 검색:** PostgreSQL·pgvector를 사용한 코사인 유사도 검색
- **근거 평가:** 관련 근거 부족과 상위 결과 간 모호성을 구분
- **민원 답변 생성** — GPT가 작성한 한국어 안내 문장마다 인용 번호를 붙이고, 서버가 인용문을 검색 문서 원문과 대조
- **검색 이력 저장:** 검색 요청·결과·근거 평가 결과 저장
- **지식 적재 및 평가:** 검색 지식 JSON 적재, 평가 데이터와 지표 계산 기반

## 검색 대상

| 유형 | 내용 | 기본 반환 수 |
|---|---|---:|
| 검증 사례 | 비식별 처리되고 검수된 유사 민원 사례 | 5개 |
| 유형 기준 | 민원 대상·유형을 구분하는 기준 자료 | 3개 |
| 공식 안내 | 공식 안내 문서의 검색 단위 | 3개 |

유형별로 별도 검색하며, 검수 완료·활성 상태·동일 임베딩 버전의 자료를 검색 대상으로 사용합니다.

## 설계 기준

- 입력 민원과 검색 지식에 같은 검색 텍스트 템플릿·버전을 적용합니다.
- 검색 점수와 근거 충분성을 구분합니다.
- 위험 신호는 검색 점수와 별도로 후속 모듈에 전달합니다.
- 최종 민원 분류·긴급 경로 결정·실제 신고 접수는 후속 모듈의 범위입니다.

## 기술 스택

| 분야 | 기술 |
|---|---|
| Backend | Java 26, Spring Boot 4.0.6, Gradle |
| Database | PostgreSQL 17, pgvector, JdbcTemplate, Flyway |
| External API | OpenAI `text-embedding-3-large` · 기본 1,536차원 |
| 답변 생성 | OpenAI 호환 Chat Completions · 기본 `gpt-4o-mini` |
| Test | JUnit, Spring Boot Test, Testcontainers |
| Local Environment | Docker Compose |

버전은 현재 [빌드 설정](build.gradle)과 [DB 구성](docker-compose.yml) 기준입니다.

## 로컬 실행

Java 26과 Docker가 필요합니다. 임베딩과 답변 생성에 사용할 API 키를 `OPENAI_API_KEY` 환경변수로 설정하세요.

### Windows PowerShell

```powershell
docker compose up -d
$env:OPENAI_API_KEY="YOUR_API_KEY"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

### Linux / macOS

```bash
docker compose up -d
export OPENAI_API_KEY="YOUR_API_KEY"
bash gradlew bootRun --args='--spring.profiles.active=local'
```

`local` 프로필은 Compose의 PostgreSQL에 연결하고 Flyway 마이그레이션을 활성화합니다. 검색 지식 적재는 [적재 안내](data/retrieval/import/README.md)를 참고하세요.

## 검색 API

```http
POST /api/vector/retrieve
Content-Type: application/json
```

| 요청 필드 | 내용 |
|---|---|
| `inputSource` | 입력 출처 |
| `maskingCompleted` | 개인정보 마스킹 완료 여부. `true` 필수 |
| `maskedText` | 개인정보가 제거된 텍스트 |
| `structuredComplaint` | 사실 요약, 맥락, 관찰 사실, 위험 신호, 민원 대상, 요청 행위 등 |

응답에는 `queryId`, `riskSignalPresent`, `decision`, `channels`가 포함됩니다.

[요청 DTO](src/main/java/com/example/ccas/retrieval/api/dto/RetrievalRequest.java) · [구조화 민원 DTO](src/main/java/com/example/ccas/retrieval/api/dto/StructuredComplaintRequest.java) · [응답 DTO](src/main/java/com/example/ccas/retrieval/api/dto/RetrievalResponse.java)

## 민원 답변 API

`POST /api/rag/answer`는 검색 API와 같은 요청을 받습니다. 마스킹을 마친 유실물 민원 [예제](docs/examples/rag-answer-request.json)를 로컬 서버에 보내려면 다음 명령을 사용하세요.

```powershell
curl.exe -H "Content-Type: application/json" --data-binary "@docs/examples/rag-answer-request.json" http://localhost:8080/api/rag/answer
```

검색과 생성은 동일한 `OPENAI_API_KEY`와 `OPENAI_BASE_URL`을 사용합니다. 생성 모델은 `RAG_ANSWER_MODEL`로 설정하며 Chat Completions의 JSON 응답 모드를 지원해야 합니다. 안내를 생성하려면 [검수한 공식 안내 지식](data/retrieval/import/README.md)을 먼저 적재하세요.

| 응답 필드 | 의미 |
|---|---|
| `queryId`, `evidenceStatus`, `riskSignalPresent` | 검색 이력 ID, 근거 판정, 입력 위험 신호 여부 |
| `answerStatus`, `answer` | `GENERATED`이면 한국어 안내 문장과 인용 번호, `ABSTAINED`이면 안내를 유보한 이유 |
| `citations` | 인용 번호 순서대로 검색 항목 ID, 문서 제목, 구간 제목, 실제 문서에 있는 인용문 |

답변에는 검색 임계값을 통과한 공식 안내만 사용합니다. 근거 부족·충돌 때는 생성 호출을 생략합니다. 모델도 자료로 답할 수 없으면 유보할 수 있습니다. 답변 API는 생성된 답변을 저장하지 않습니다. 모델 호출 실패나 문서에서 확인할 수 없는 인용은 HTTP 502로 반환합니다.

### 실제 모델로 재현하기

Docker를 실행하고 API 키를 설정한 뒤 `.\gradlew.bat liveRagAnswer`를 실행하세요. 테스트는 임시 pgvector DB에서 같은 유실물 민원을 두 번 보냅니다. 빈 DB에서는 `ABSTAINED`를 반환하고, 합성 안내 문서 한 건을 적재한 뒤에는 `GENERATED` 답변과 해당 문서의 인용문을 반환합니다.

2026-11-08 검증에서 GPT가 합성 문서를 바탕으로 생성한 답변은 다음과 같습니다.

> 도서관에서 잃어버린 지갑을 조회하려면 분실물 조회 화면에서 물품 종류를 지갑으로 선택하고 분실 장소를 입력합니다. [1]

인용 번호 `[1]`은 응답의 `citations[0]`에 있는 합성 문서 「도서관 분실물 조회」의 구절을 가리킵니다. 이 예제는 안내 임계값 0.45에서 생성됐습니다. 서비스 기본값 0.70으로 같은 예제를 실행했을 때는 근거 부족으로 답변을 유보했습니다.

Java 기본 테스트 639건과 실제 모델을 호출하는 종단 간 테스트 1건이 모두 통과했습니다. 재현한 답변과 인용문은 `build/reports/tests/liveRagAnswer/index.html`에서 해당 테스트의 표준 출력으로 확인할 수 있습니다.

## 평가 상태

**최종 결과는 [경찰관 업무 지원 검색 평가](docs/police-retrieval-final-v1.md)를 기준으로 봅니다.**
55건·20개 문서의 고정 AI 라벨 비교에서 공식 안내 Recall@3은 기본 81.94%, 코사인 기반
RRF 98.61%였으나 Hit@1은 감소했습니다. 아래는 각 단계의 당시 진행 기록입니다.
Python 103개·Docker 통합 테스트를 포함한 Java 627개 테스트가 통과했습니다.
[55건 DB/API 재검증](docs/police-database-http-v1.md)도 완료했습니다. 기본 임시 임계값에서는
55건 모두 근거 부족이어서, 순위 개선과 운영 근거 채택 품질을 구분해 해석해야 합니다.
[안내 채택 임계값 교정](docs/police-guide-cutoff-v2.md)은 0.60을 실험 후보로 검증했습니다.
평가용에서 채택 문서 3/3개는 AI 라벨과 일치했지만 지원한 질의는 3/18건으로 적어 운영 적용은 보류했습니다.

현재 평가 목적은 [경찰관 업무 지원 기준](docs/officer-support-evaluation-v1.md)입니다.
후속 [묶음 평가·자료 수집 후보](docs/officer-support-followup-v2.md)에서는 응답 누락을
보완하고 실제 질의와 공식 안내·작성 예시의 출처를 구분합니다.
[공개 질의 수집 초안](docs/officer-public-intake-v1.md)에 유실물·순찰 등 4건의
출처와 편입 보류 상태를 기록했습니다. 기존 500건 벤치마크에는 합산하지 않았습니다.
[경찰청 공개 질의 API 신청 안내](docs/police-law-api-setup.md)와 승인 후 사용할 수집
도구를 준비했고, [JSON API 수집](docs/police-law-json-intake-v2.md)으로 공개 질의 초안
89건을 저장했습니다. 비식별·업무 범위 추가 점검 전이므로 평가 정답이나 운영 자료로 승격하지 않았습니다.
[89건 AI 점검](docs/police-intake-screen-v1.md)에서 구체적 상황 후보 32건과 일반 절차 후보
23건을 구분했습니다. 단일 모델 초안이며 중복·분할·정답 라벨 고정은 남아 있습니다.
[후보 그룹·분할 고정](docs/police-candidate-split-v1.md)에서 55건을 40개 그룹으로 묶고
개발 27건·평가 28건으로 나눴습니다. 사실 구조화와 검색 정답 라벨 생성은 남아 있습니다.
[55건 구조화·Java 검증](docs/police-structure-validation-v1.md)을 완료해 고정 템플릿의
검색 텍스트를 생성했습니다. 관련성 정답과 검색 성능 평가는 후속 단계입니다.
[경찰관 기준 관련성 AI 라벨](docs/police-officer-relevance-v1.md)도 고정했습니다.
두 모델의 2,200개 등급 중 주 모델 1,100쌍을 평가 기준으로 사용하며, 검색 성능 비교는 아직 실행하지 않았습니다.
기존 500건은 여러 분야의 고충민원이 섞인 자료이므로 경찰 민원 전체를 대표하지 않습니다.
민원인의 명시적 절차 질문을 필수로 삼았던 아래 실험은 과거 탐색 기록으로 보존합니다.

[채널별 관련성 기준 개발 실험](docs/channel-rubric-pilot-v1.md)에서는 개발 100건으로
평가 기준의 안정성을 시험합니다. 모델 간 일치율을 정확도로 해석하지 않으며,
기존 벤치마크 라벨과 운영 설정에 자동 반영하지 않습니다.

[평가 기준 고정·교차 모델 검증](docs/evaluation-stability-v1.md)을 추가했습니다.
라벨과 후보가 바뀐 점수를 같은 실험으로 비교하지 않도록 고정 벤치마크 해시를 검사하며,
교차 평가 결과를 기존 정답에 자동 병합하지 않습니다.

2026-10-29에는 [확장 후보·블라인드 AI·실제 DB/API 평가](docs/retrieval-validation-v3.md)를
추가했습니다. 후보는 유형 기준 4개·공식 안내 16개이며, 일회용 DB에서 500건 HTTP 요청과
검색 순위를 검증합니다. 임시 임계값이 모든 근거를 거부하는 문제도 별도로 평가합니다.
운영 승인이나 최종 민원 분류 정확도와는 구분합니다.

2026-10-28 실제 공개 사례 500건의 임베딩과 문서 후보 7개를 사용한
[소규모 후보 비교](docs/public-pool-experiment.md)를 완료했습니다. AI 중심 라벨 기준의
공식 안내 순위 비교이며, 운영 DB 적재·대규모 corpus 성능·전체 분류 정확도 검증은 아닙니다.

검색 임계값은 `provisional-v1` 데모용 설정입니다. 합성 데이터는 코드와 지표 계산을 확인하기 위한 자료이며, 실제 서비스의 검색 정확도를 입증하는 자료가 아닙니다.

평가 기반은 채널별 Recall@K·Precision@K·MRR, 근거 상태 정확도, 위험 신호 보존율, 근거 없음 거부 정확도, 모호성 감지 정확도를 계산합니다.

검색 전략은 `retrieval.search.strategy`로 전환할 수 있습니다. 기본값 `BASELINE`을 보존하며,
`EXACT`는 거리·ID Top-K를 먼저 선택하는 SQL 대안입니다. `METADATA` 재정렬과 `CODE_RRF`는
검증 전 실험 옵션입니다. 동일 저장 벡터를 재사용하는 비교 도구, Hit@K·고정 K Precision,
판정 혼동행렬 및 실행 계획 측정을 추가했습니다. 합성 평가에서 메타데이터 재정렬의 품질 향상은
확인되지 않았고 코드 RRF는 모호성 감지를 악화시켰습니다.
[측정 결과·재현 명령·한계](docs/retrieval-optimization.md)를 확인하세요.

추가로 500개 합성 순위 시나리오(개발 100/최종 비교 400)와 코사인 기반 제한적 RRF 가산 방식
`COSINE_CODE_RRF`를 비교했습니다. 메타데이터 오류에 따른 악화와 RRF의 모호성 근거 누락을
분리해 확인했으며 기본 검색 전략은 유지했습니다. [500개 실험 결과](docs/retrieval-stress-experiment.md)

테스트에는 Java 26이 필요하며, Testcontainers 통합 테스트에는 Docker가 필요합니다.

```powershell
.\gradlew.bat test
```

```bash
bash gradlew test
```

## 코드 및 문서

| 경로 | 내용 |
|---|---|
| [API](src/main/java/com/example/ccas/retrieval/api/) | 검색 요청·응답, 입력 검증 |
| [답변 생성](src/main/java/com/example/ccas/answer/) | 근거 판정에 따른 답변 생성·유보, GPT 호출, 인용문 검증 |
| [검색 처리](src/main/java/com/example/ccas/retrieval/application/) | 검색 텍스트 생성, 검색 실행, 근거 평가 |
| [DB 접근](src/main/java/com/example/ccas/retrieval/infrastructure/persistence/) | pgvector 검색 및 이력 저장 |
| [DB 스키마](src/main/resources/db/migration/V1__create_vector_retrieval_schema.sql) | Flyway 초기 스키마 |
| [검색 평가](docs/retrieval-evaluation.md) | 평가 데이터·지표·임계값 조정 절차 |
| [지식 적재](data/retrieval/import/README.md) | 검색 지식 적재 방법 |
