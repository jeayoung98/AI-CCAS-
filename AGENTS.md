# AGENTS.md

## 프로젝트 범위

이 저장소는 CCAS 해커톤 백엔드 프로젝트이다.

현재 구현 범위는 경찰 관련 민원 안내 서비스의 **벡터 검색 모듈**로 제한한다.

벡터 검색 모듈은 개인정보가 제거되고 구조화가 완료된 민원 데이터를 입력받아 다음 작업을 수행한다.

- 고정 형식의 `search_text` 생성
- 임베딩 생성 및 저장
- PostgreSQL + pgvector 기반 근거 검색
- 검색 근거가 충분한지 평가
- 후단 분류 AI가 사용할 검색 결과 반환

---

## 구현 범위

별도 Task에서 명시적으로 범위를 변경하지 않는 한, 아래 기능만 구현한다.

- 구조화 민원 요청 DTO 및 validation
- 구조화 데이터 기반 `search_text` 생성
- 임베딩 생성 및 저장
- PostgreSQL + pgvector 스키마 및 검색 쿼리
- 검색 지식 적재 기능
- 검색 채널별 벡터 검색
- 검색 결과 로그 저장
- 근거 충분성 평가
- retrieval-only API
- 평가 fixture 및 threshold 조정 기반
- 후단 AI 연동을 위한 문서화

---

## 구현하지 않는 범위

벡터 검색 Task에서는 아래 기능을 구현하지 않는다.

- 음성 STT 변환
- 음성 파일 업로드 및 저장
- 개인정보 마스킹 모델 자체 구현
- 최종 민원 분류 LLM
- 112, 182, 경찰민원24 등 최종 처리 경로 확정
- 범죄 성립 여부에 대한 법적 판단
- 실제 민원 또는 신고 접수 기능
- 경찰 시스템 연동

---

## 처리 경계

벡터 검색 모듈에 데이터가 들어오기 전까지의 흐름은 다음과 같다.

    텍스트 입력 또는 STT 변환 결과
    → 개인정보 마스킹 완료
    → 앞단 AI가 사실 기반 구조화 JSON 생성
    → 벡터 검색 모듈에 전달

벡터 검색 모듈은 다음 작업만 수행한다.

    구조화된 민원 JSON
    → 고정 형식의 search_text 생성
    → 임베딩 생성
    → 채널별 벡터 검색
    → 근거 충분성 평가
    → 후단 AI용 검색 결과 반환

---

## 구조화 민원 데이터 규칙

앞단에서 전달받는 구조화 민원 데이터에는 다음과 같은 사실 추출 정보가 포함될 수 있다.

- `factualSummary`
- `context.placeType`
- `context.relationshipType`
- `context.timePattern`
- `observedFacts`
- `riskSignals`
- `subjectMatters`
- `requestedActions`
- `ongoingStatus`
- `missingInformation`

신규 민원 검색 요청 모델에는 아래와 같은 최종 판단 필드를 추가하지 않는다.

- `finalCategory`
- `finalRoute`
- `crimeDetermination`
- `confidence`
- 최종 긴급 경로 판단값

앞단 구조화 데이터는 사용자가 말한 사실, 상황, 위험 가능성, 요청 목적만 담는다.  
최종 분류와 최종 안내 경로 결정은 검색 이후의 별도 모듈 책임이다.

---

## 구조화 코드 기준

### 위험 신호 코드

초기 지원 대상은 다음과 같다.

- `WEAPON`: 흉기 또는 무기 언급
- `DEATH_THREAT`: 살해 협박성 표현
- `ASSAULT_IN_PROGRESS`: 현재 폭행 진행 가능성
- `ABDUCTION`: 납치 또는 감금 가능성
- `MISSING_PERSON`: 실종 관련 상황
- `CHILD_ABUSE`: 아동학대 의심
- `STALKING_PATTERN`: 반복 접근 또는 감시 정황
- `PERSONAL_DATA_EXPOSURE`: 개인정보 노출 또는 유포

### 민원 대상 코드

초기 지원 대상은 다음과 같다.

- `SAFETY_THREAT`: 안전 위협 관련 상황
- `LOST_ITEM`: 유실물
- `TRAFFIC_ADMIN`: 교통 행정
- `CYBER_TRANSACTION`: 온라인 거래 피해 의심
- `PATROL_REQUEST`: 순찰 요청
- `CASE_STATUS`: 사건 또는 처리 현황 문의
- `POLICE_SERVICE_COMPLAINT`: 경찰 서비스 관련 민원
- `GENERAL_CONSULTATION`: 일반 상담
- `OTHER`
- `UNKNOWN`

### 요청 행위 코드

초기 지원 대상은 다음과 같다.

- `REPORT`: 신고 문의
- `CONSULT`: 상담 요청
- `SEARCH`: 검색 또는 조회 요청
- `APPLY`: 신청 요청
- `CHECK_STATUS`: 처리 현황 확인
- `PROVIDE_INFORMATION`: 정보 제공 또는 제보
- `UNKNOWN`

---

## search_text 생성 규칙

- 임베딩 대상은 raw 민원 텍스트가 아니라 `search_text`이다.
- 단순 요약문만 임베딩하지 않는다.
- `search_text`는 LLM이 자유롭게 생성하지 않는다.
- Java의 `SearchTextBuilder`가 구조화 데이터를 기반으로 고정 템플릿으로 생성한다.
- 저장된 검색 지식과 신규 검색 요청은 동일한 템플릿 및 버전을 사용한다.
- 위험 신호는 `search_text`에서 제거하지 않는다.
- 비긴급 민원을 구분할 수 있도록 `subjectMatters`와 `requestedActions`를 반드시 반영한다.
- `missingInformation`은 기본적으로 임베딩 대상에서 제외한다.
- `structure_version`, `search_text_version`, `embedding_version`을 저장한다.

### 신규 민원 및 검증 사례용 search_text 기본 템플릿

    발생 상황: {factualSummary}
    장소 맥락: {context.placeType}
    관계 맥락: {context.relationshipType}
    발생 패턴: {context.timePattern}
    관찰된 사실: {observedFacts.value 목록}
    위험 신호: {riskSignals.code 목록 또는 "명시적 위험 신호 없음"}
    민원 대상: {subjectMatters.code 목록}
    요청 행위: {requestedActions.code 목록}
    현재성: {ongoingStatus}

---

## 검색 지식 유형

검색 가능한 모든 지식은 공통 `retrieval_item` 모델에 저장하고, `item_type`으로 구분한다.

지원하는 `item_type`은 다음과 같다.

- `VERIFIED_CASE`: 비식별 처리되고 검수 완료된 유사 민원 사례
- `CATEGORY_REFERENCE`: 민원 대상 또는 유형 기준 자료
- `OFFICIAL_GUIDE`: 공식 안내 문서의 검색 단위 chunk

검색은 반드시 유형별로 나누어 수행한다.

- `VERIFIED_CASE`: Top 5
- `CATEGORY_REFERENCE`: Top 3
- `OFFICIAL_GUIDE`: Top 3

세 종류 결과를 하나의 통합 Top-K 순위로 섞어서 반환하지 않는다.

---

## 데이터 안전 규칙

- raw 민원 원문을 DB에 저장하지 않는다.
- 음성 파일을 저장하지 않는다.
- 민원 텍스트 전문을 불필요하게 로그에 남기지 않는다.
- 민원 관련 텍스트를 저장할 때는 개인정보가 제거된 `masked_text`만 사용한다.
- `maskingCompleted=true`가 아닌 검색 요청은 저장 및 임베딩 대상으로 받지 않는다.
- API 응답에 embedding 벡터 배열을 노출하지 않는다.
- API Key 또는 secret을 코드에 하드코딩하지 않는다.
- secret은 환경변수 또는 기존 프로젝트의 secret 관리 방식을 따른다.

---

## DB 및 임베딩 기준

기본 기술 구성은 다음과 같다.

- PostgreSQL
- pgvector
- cosine similarity 검색
- 초기에는 exact nearest-neighbor 검색 사용

기본 임베딩 설정은 다음과 같다.

- model: `text-embedding-3-large`
- dimensions: `1536`
- version: `embed-v1-large-1536`

모델명, 차원 수, 임베딩 버전은 반드시 설정값으로 관리한다.

초기 구현에서는 HNSW 또는 IVFFlat 인덱스를 추가하지 않는다.  
추후 평가 결과와 데이터 규모를 확인한 뒤 별도 Task에서 검토한다.

검색 대상은 아래 조건을 만족해야 한다.

- `review_status = 'VERIFIED'`
- `is_active = true`
- 검색 요청과 동일한 `embedding_version`

---

## 검색 결과 불확실성 처리 규칙

Top-K 결과가 반환되었다고 해서 관련 근거가 충분하다고 판단하지 않는다.

벡터 검색 모듈은 다음 `evidence_status`를 지원해야 한다.

- `CASE_AND_REFERENCE_SUPPORTED`
- `REFERENCE_SUPPORTED`
- `GUIDE_ONLY`
- `AMBIGUOUS`
- `INSUFFICIENT_EVIDENCE`

### 판정 원칙

- 검증 사례, 유형 기준, 공식 안내 근거가 충분하면 `CASE_AND_REFERENCE_SUPPORTED`를 반환할 수 있다.
- 신뢰 가능한 유사 사례는 없지만 유형 기준과 공식 안내 근거가 충분하면 `REFERENCE_SUPPORTED`를 반환한다.
- 공식 안내 문서만 충분히 관련 있으면 `GUIDE_ONLY`를 반환한다.
- 상위 사례 또는 유형 기준이 서로 충돌하면 `AMBIGUOUS`를 반환한다.
- 모든 검색 채널에서 근거가 약하면 `INSUFFICIENT_EVIDENCE`를 반환한다.
- 신뢰 가능한 유사 사례가 없는데도 사례 기반 분류를 강제로 만들지 않는다.
- `riskSignalCodes`가 하나 이상 존재하면 검색 점수와 무관하게 `riskSignalPresent=true`를 보존해 후단에 전달한다.
- 벡터 모듈은 최종 긴급 경로를 결정하지 않는다.

---

## 임계값 및 평가 규칙

- similarity threshold를 코드에 확정값처럼 하드코딩하지 않는다.
- threshold는 설정값과 `threshold_profile_version`으로 관리한다.
- 초기 threshold는 평가 전 임시값 또는 데모용 값임을 명시한다.
- 평가 이전에 자동 분류 정확도가 검증되었다고 주장하지 않는다.

평가 기반에는 최소한 다음 지표를 포함한다.

- 채널별 `Recall@K`
- 채널별 `Precision@K`
- `MRR`
- 유사 사례 없음 거부 정확도
- 모호성 감지 정확도
- 위험 신호 보존율

---

## 저장소 적응 규칙

구현 코드를 추가하기 전에 반드시 현재 저장소를 확인한다.

확인 대상:

- Java 버전
- Spring Boot 버전
- Gradle 또는 Maven 사용 여부
- JPA, JdbcTemplate, MyBatis, Spring Data JDBC 중 현재 사용 방식
- Flyway 또는 Liquibase 사용 여부
- 기존 OpenAI client, Spring AI, RestClient, WebClient 설정 존재 여부
- Docker Compose 또는 Testcontainers 존재 여부
- 기존 예외 처리 및 validation 방식
- 기존 관리자 API 및 인증 규칙

기존 프로젝트 방식으로 충분히 구현할 수 있으면 새로운 인프라나 라이브러리를 중복 추가하지 않는다.

---

## Task 수행 규칙

각 Task에서는 다음 원칙을 따른다.

- 현재 요청된 Task 범위만 구현한다.
- 이후 Task의 기능을 미리 구현하지 않는다.
- 관련 테스트 또는 검증 명령을 실행한다.
- 변경 파일 목록을 보고한다.
- 실행한 테스트와 결과를 보고한다.
- 해결되지 않은 위험 요소와 가정을 보고한다.
- 추천 커밋 메시지를 함께 제시한다.
- 요청과 무관한 리팩터링은 수행하지 않는다.

---

## 완료 기준

하나의 Task는 아래 조건을 모두 충족해야 완료된 것으로 본다.

- 요청된 범위가 구현되었다.
- 관련 테스트가 통과했거나, 실패 사유가 명확히 보고되었다.
- raw 민원 원문이나 secret이 새로 저장·노출되지 않았다.
- 불필요한 범위 확장이나 무관한 리팩터링이 없다.
- 다음 Task를 진행할 수 있도록 변경 사항이 정리되었다.

---

## 현재 프로젝트 기술 기준

- Java: 26
- Spring Boot: 현재 build 파일에 선언된 버전을 기준으로 한다.
- Spring Boot 버전이 Java 26을 공식 지원하지 않거나 빌드가 실패하는 경우, 임의로 구현을 진행하지 말고 먼저 보고한다.
- 팀원 로컬 환경과 배포 환경도 동일한 Java 버전을 사용할 수 있도록 확인한다.