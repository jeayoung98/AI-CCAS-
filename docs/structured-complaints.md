# 실제 사례의 AI 구조화와 Java 검증

500건의 가림 텍스트를 기존 Java DTO의 코드 체계에 맞춰 구조화한다. 기존 추출식
초안과 사람 검수 응답은 수정하지 않고 새 스냅샷에 저장한다. 관련성 정답이나 검색
문서는 구조화 모델 입력에 포함하지 않는다. 최종 분류·경로·법적 판단은 생성하지 않는다.

## 완료 결과 (2026-10-28)

- 구조화 500건 생성 및 실제 500건 Java 검증 통과. 검색문도 기존 `SearchTextBuilder`로 500개 생성했다.
- dev 100건/test 400건 유지. 중복 ID·그룹 교차·본문 해시 검사에서 오류가 없었다.
- 위험 신호가 있는 5건의 플래그를 보존했다. 요청 행위 UNKNOWN 132건, 현재성 UNKNOWN 361건은 추정으로 채우지 않았다.
- 절단된 발췌 41건 모두 뒷부분 확인 필요를 남겼다. 현재성은 원문 서술의 AI 해석이며 실제 현재 위험 확인이 아니다.
- Java 전체 테스트 620개 통과(2026-10-27), Python 테스트 48개 통과. Java 실제 데이터 일괄 검증은 2026-10-28 완료했다.

생성 도중 근거 ID 누락·지원하지 않는 코드·빈 요청 행위 배열을 거부하고 검증 오류를
구체적으로 알려 재시도했다. 잘못된 코드를 자동으로 다른 코드로 바꾸거나 빈 값을
임의로 보정하지 않았다. 마지막 2건까지 저장된 것을 재개 시 확인해 추가 API 호출 없이
Java 검증을 마쳤다. 기존 입력·개인정보 응답의 해시도 유지됨을 확인했다.

[최종 집계와 산출물 해시](results/structured-500-summary.json)에 생성·Java 검증 결과를
함께 기록했다. 생성 폴더의 `summary.json`은 Java 검증 이전 단계의 기록이므로
그 파일의 `javaValidationExecuted=false`와 최종 검증 완료를 혼동하지 않는다.

## 생성 기준

- GMS 모델이 요약, 맥락, 관찰 사실, 위험 신호, 민원 대상, 요청 행위, 현재성,
  미확인 정보를 작성한다. 지원 코드는 저장소의 Java enum 파일에서 읽는다.
- 원문 구간 ID를 선택하게 하고, `evidence` 문자열은 프로그램이 해당 구간에서
  가져온다. 없는 구간 ID와 지원하지 않는 코드, 최종 판단 필드를 거부한다.
- `EXPLICIT`는 원문에 명시됐다는 의미다. 신청인의 주장을 입증된 사실로 취급하지 않는다.
- 확인되지 않은 값은 UNKNOWN/UNCERTAIN으로 남긴다. `riskSignals=[]`는 모델이
  해당 코드의 명시적 근거를 찾지 못했다는 뜻이며, 사람이 위험 없음을 확인했다는 뜻이 아니다.
- 개인정보 PASS와 데이터셋·개별 본문 해시가 일치하는 경우에만 모델에 전달한다.
  기존 초안의 `maskingCompleted=false`는 바꾸지 않는다. 별도 요청 스냅샷에는
  실제 개인정보 PASS에 근거해 `maskingCompleted=true`를 기록한다.
- 41건의 절단된 발췌에는 원문 뒷부분 확인 필요를 `missingInformation`에 보존한다.

구조화의 출처는 AI이며 `humanStructureReviewed=false`다. 개인정보 검수 완료와
새로 생성한 구조화 내용의 사람 검수 완료는 구분한다. 출력은 Git 제외 경로에 저장한다.

## 오프라인 Java 검증

`StructuredBatchValidationMain`은 Spring 서버나 DB를 시작하지 않는다. 기존
`RetrievalRequest`와 도메인의 Bean Validation, `RetrievalApiMapper`를 실행하고,
`SearchTextBuilder`로 검색문을 생성한다. Python이나 LLM이 search_text를 만들지 않는다.
구조화 버전과 검색문 버전은 저장소 `application.properties`에서 읽는다.

필수값·코드·마스킹 플래그·본문 해시·중복 질의 ID·dev/test 그룹 중복·총 건수를
검사한다. 한 건이라도 실패하면 오류 위치와 코드만 보고서에 기록하고 검색문 출력은
생성하지 않는다. 예외 로그에 민원 텍스트를 출력하지 않는다. 기존 출력 폴더도 덮어쓰지 않는다.
검증 통과는 형식과 입력 연결을 확인한 것이며 AI 추출 정확도 검증은 아니다.

## 실행

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/structure_complaints.py --output .local-data/acrc-structured-v1
.\gradlew.bat validateStructuredBatch '-Dstructure.input=.local-data/acrc-structured-v1/requests.jsonl' '-Dstructure.output=.local-data/acrc-structured-java-v1' '-Dstructure.expectedCount=500'
```

생성은 건별 체크포인트로 재개한다. 입력·개인정보 응답·코드 체계·프롬프트·모델이
달라지면 새 출력 경로를 요구한다. 키는 환경변수에서만 읽는다.

출력:

- `.local-data/acrc-structured-v1/structures.json`: 구조화, 근거 선택, 모델·프롬프트·입력 해시.
- 같은 폴더의 `requests.jsonl`: Java DTO 검증 입력. `summary.json`은 생성 집계.
- `.local-data/acrc-structured-java-v1/validation.json`: Java 검증 집계와 버전.
- 같은 폴더의 `validated-requests.jsonl`: 검증된 요청과 Java가 생성한 검색문, 위험 신호 플래그.

이번 단계는 임베딩 API를 호출하거나 DB에 적재하지 않는다. 다음 단계에서 검색 지식의
적재 준비와 함께 실제 임베딩 및 비교 실험을 진행할 수 있다. 평가용 근거 상태·위험
정답은 구조화 모델의 출력을 그대로 복사해 만들지 않는다.

변경 파일: `tools/public_complaints/structure_complaints.py`, `test_structure_complaints.py`,
`build.gradle`, `src/main/java/com/example/ccas/retrieval/evaluation/StructuredBatchValidationMain.java`,
`src/test/java/com/example/ccas/retrieval/evaluation/StructuredBatchValidationMainTest.java`,
`docs/results/structured-500-summary.json`, 이 문서.
추천 커밋: `feat(evaluation): structure masked complaints and validate with production Java pipeline`
