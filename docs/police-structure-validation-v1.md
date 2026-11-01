# 경찰 업무 평가 후보 55건의 사실 구조화와 Java 검증

기준일: 2026-10-30. 고정한 개발 27건·평가 28건을 구조화하고 실제 Java DTO,
도메인 매핑, `SearchTextBuilder`로 검증했다. **55건 모두 통과**했고 실패는 0건이다.

2026-10-31 [경찰관 기준 관련성 AI 라벨 생성](police-officer-relevance-v1.md)을 완료했다.
아래 관련성 라벨 미생성 상태는 구조화 종료 시점의 기록이다.

## 이번에 확인한 결과

- 기존 사실 추출 도구를 재사용해 상황 요약·장소/관계/시간 맥락·관찰 사실·위험 신호·
  민원 대상·요청 행위·현재성·부족 정보를 추출했다.
- 실제 구조화 모델은 `gpt-4o-2024-08-06`이다. 모든 추출 항목의 출처 ID를 검증해
  해당 질의의 원문 구간으로 변환했다. 최종 분류·처리 경로·범죄 성립 필드는 만들지 않았다.
- 요청 행위를 확정하기 어려운 7건은 UNKNOWN을 유지했다.
- AI 추출에서 CHILD_ABUSE 1개, STALKING_PATTERN 2개, PERSONAL_DATA_EXPOSURE 2개가
  나타났다. 이는 코드 출현 수이며 실제 위험 여부나 법적 판단의 정답이 아니다.
- Java에서 구조화·검색 텍스트 버전과 검색 텍스트 해시를 기록했다. 검색 텍스트는
  모델이 작성하지 않고 운영 코드의 고정 템플릿으로 생성했다.
- 55건의 출처 ID·그룹·dev/test 배치가 원래 분할과 일치했고, 55건 모두 Java 위험 플래그가
  구조화 위험 목록의 존재 여부와 일치했다. 이는 추출 코드의 의미적 정확도 검증과 다르다.

## 개인정보 점검과 평가 요청의 경계

기존 후보 파일의 DRAFT·maskingCompleted=false 상태를 수정하지 않았다. 새 래퍼는
후보 파일 해시를 공개 분할 고정 결과와 대조하고, 각 질의의 AI 개인정보 PASS가
원래 점검 체크포인트와 일치하는지 검증한다. 변경된 텍스트·판단이나 배경 정보 자료는
이 경로에서 거절한다. 검증한 AI 판단으로 별도 privacy-gate.json을 생성해 기존 구조화
도구가 읽도록 했다.

별도로 생성한 평가 요청에만 maskingCompleted=true를 설정했다. 개인정보 검토 주체는
AI_SCREENING, humanReviewed=false로 보존했다. 이는 사용자가 선택한 AI 중심 점검을
적용한 것이며 개인정보 완전 제거를 보증하거나 사람이 검수했다는 의미가 아니다.
자료와 구조화 결과는 로컬 Git 제외 디렉터리에만 두고 운영 DB에는 적재하지 않았다.

## 검증의 한계

출처 ID가 존재한다는 사실은 모델의 요약·코드가 원문의 의미를 정확히 해석했다는 보장이
아니다. DTO 검증도 허용 코드·형식·출처 해시·마스킹 플래그를 확인하는 것이며 의미 정확도는
판정하지 않는다. 위험 신호 추출의 부정·과거·인용 처리와 요청 의도는 후속 의미 검증에
주의가 필요하다. 이번 결과를 검색 Recall 개선이나 민원 분류 정확도로 표현하지 않는다.

구조화와 입력 검증까지 완료했지만 임베딩·검색 순위·관련 문서 정답은 아직 없다.
기존 500건 벤치마크와 이 55건을 같은 성능 지표로 비교하지 않는다.

## 변경 파일과 산출물

- `tools/public_complaints/structure_police_candidates.py`: 고정된 후보와 AI 개인정보
  체크포인트를 확인하고 기존 구조화 도구로 연결하는 평가 전용 래퍼.
- `tools/public_complaints/test_structure_police_candidates.py`: 변경된 원문·개인정보 보류·
  선정되지 않은 자료 거절, AI 검수 출처 보존 검증.
- `docs/results/police-structure-validation-v1.json`: 구조화·Java 검증 집계, 입력/출력 해시.
- 이 문서, README·이전 분할 보고서 연결.
- `.local-data/police-structure-v1/`: 개인정보 판단 계보와 구조화 체크포인트·평가 요청.
- `.local-data/police-structure-java-v1/`: Java가 검증한 요청과 고정 검색 텍스트. Git 제외.

운영 Java 코드는 변경하지 않았고 기존 검증 실행기를 그대로 사용했다.

## 실행과 테스트

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/structure_police_candidates.py --output .local-data/police-structure-v1
$env:JAVA_HOME='C:\Users\SSAFY\.jdks\jdk-26.0.2.1'
.\gradlew.bat --console=plain validateStructuredBatch '-Dstructure.input=.local-data/police-structure-v1/generated/requests.jsonl' '-Dstructure.output=.local-data/police-structure-java-v1' '-Dstructure.expectedCount=55'
```

Java 출력 폴더가 이미 있으면 덮어쓰지 않으므로 재실행 시 새 폴더를 지정한다.
Python 테스트 98개, 관련 Java 테스트 21개 통과. Java 26으로 배치 55건 검증 성공.
API 키 없이 구조화 체크포인트를 재사용했을 때 평가 요청 파일이 동일했다.
기존 벤치마크 해시 검사도 통과했다.

다음은 경찰관 업무 기준으로 각 질의에 유용한 사례·유형 기준·공식 안내의 관련성 라벨을
만드는 것이다. 평가 후보 문서와 라벨을 고정한 뒤 동일 조건에서 검색 전략을 비교한다.
현재 3번 평가 데이터 준비는 질의 구조화까지 완료했으며 정답 라벨 준비는 남아 있다.

추천 커밋: `feat(evaluation): structure frozen police queries and validate Java inputs`
