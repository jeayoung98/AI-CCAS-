# AI-CCAS · 민원 안내를 위한 근거 검색 모듈

경찰 관련 민원 안내 서비스에서 사용할 **벡터 검색 백엔드 모듈**입니다. 개인정보가 제거된 구조화 민원을 입력받아 유사 사례·유형 기준·공식 안내 문서를 검색하고, 후속 AI가 사용할 근거를 반환합니다.

## 구현 기능

- **입력 검증:** 개인정보 마스킹 완료 여부와 구조화 민원의 필수 항목 검증
- **검색용 텍스트 생성:** 상황·관찰 사실·위험 신호·요청 목적을 Java의 고정 템플릿으로 구성
- **임베딩 생성:** OpenAI 임베딩 API 연동, 모델·차원·버전 설정 관리
- **근거 검색:** PostgreSQL·pgvector를 사용한 코사인 유사도 검색
- **근거 평가:** 관련 근거 부족과 상위 결과 간 모호성을 구분
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
| Test | JUnit, Spring Boot Test, Testcontainers |
| Local Environment | Docker Compose |

버전은 현재 [빌드 설정](build.gradle)과 [DB 구성](docker-compose.yml) 기준입니다.

## 로컬 실행

Java 26과 Docker가 필요합니다. 검색 API의 임베딩 생성에는 `OPENAI_API_KEY` 환경변수가 필요합니다.

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

## 평가 상태

검색 임계값은 `provisional-v1` 데모용 설정입니다. 합성 데이터는 코드와 지표 계산을 확인하기 위한 자료이며, 실제 서비스의 검색 정확도를 입증하는 자료가 아닙니다.

평가 기반은 채널별 Recall@K·Precision@K·MRR, 근거 상태 정확도, 위험 신호 보존율, 근거 없음 거부 정확도, 모호성 감지 정확도를 계산합니다.

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
| [검색 처리](src/main/java/com/example/ccas/retrieval/application/) | 검색 텍스트 생성, 검색 실행, 근거 평가 |
| [DB 접근](src/main/java/com/example/ccas/retrieval/infrastructure/persistence/) | pgvector 검색 및 이력 저장 |
| [DB 스키마](src/main/resources/db/migration/V1__create_vector_retrieval_schema.sql) | Flyway 초기 스키마 |
| [검색 평가](docs/retrieval-evaluation.md) | 평가 데이터·지표·임계값 조정 절차 |
| [지식 적재](data/retrieval/import/README.md) | 검색 지식 적재 방법 |
