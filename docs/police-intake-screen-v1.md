# 공개 질의 89건의 AI 개인정보·업무 범위 점검

기준일: 2026-10-30. JSON API로 수집한 89건에 대해 `gpt-4o-2024-08-06`으로
개인정보 잔여 가능성, 경찰관 업무 지원에 대한 유용성, 구체적 상황 여부를 점검했다.
89개 응답을 모두 저장했고 최종 실패·누락은 0건이다.

이후 [질의 그룹과 분할 고정](police-candidate-split-v1.md)을 완료했다.
아래의 미완료 항목은 AI 점검 종료 시점의 기록이며, 관련성 정답은 아직 생성하지 않았다.

## 결과

| 처분 | 건수 | 의미 |
|---|---:|---|
| SPECIFIC_CANDIDATE | 32 | 구체적 상황이 있고 경찰관 업무에 유용하다고 AI가 판단 |
| GENERAL_OR_EXAMPLE_CANDIDATE | 23 | 경찰관 업무에 유용하지만 일반 절차 질의인 자료 |
| NOT_PRIMARY_EVALUATION_CANDIDATE | 34 | 배경 정보로만 유용하다고 AI가 판단 |

전체 질문 유형은 구체적 상황 32건, 일반 절차 57건이었다. 가정·예시로 분류된 자료는
0건이다. 위 상태 이름의 OR_EXAMPLE은 향후 예시도 구분하기 위한 코드이며 이번 23건은
모두 일반 절차 질문이었다. 34건을 영구 삭제하거나 경찰 업무와 완전히 무관하다고
확정하지 않는다. 후속 평가에서 경계 사례로 재검토할 수 있다.

| 구체적 상황 후보 32건의 AI 주제 코드 | 건수 |
|---|---:|
| LOST_ITEM | 8 |
| PATROL_REQUEST | 7 |
| CYBER_TRANSACTION | 7 |
| TRAFFIC_ADMIN | 7 |
| SAFETY_THREAT | 5 |
| GENERAL_CONSULTATION | 4 |
| POLICE_SERVICE_COMPLAINT | 1 |

복수 코드가 가능하므로 합계는 32를 넘는다. CASE_STATUS는 이번 AI 추출에서 0건으로
나타났다. 검색 키워드 ‘진행’으로 찾았다고 사건 진행 문의라고 보지 않는다. 이 코드들은
평가 자료의 포함 범위를 살피는 초안이며 최종 민원 분류나 처리 경로가 아니다.

## 개인정보와 사실 보존

API 초안에 기존 정규식 및 추가 치환 규칙을 적용한 뒤 이름·연락처·주민번호 위험 패턴이
남으면 외부 호출에서 제외하도록 구성했다. 이번 실행에서 사전 차단과 추가 치환은 0건이었다.
AI 개인정보 결과는 89건 모두 PASS였다. 이는 단일 모델의 점검 결과이며 개인정보가
완전히 없다는 인증이나 정확도 측정이 아니다.

모델에 전달한 것은 사전 점검한 질의 텍스트와 출처 구간 ID뿐이다. 답변·URL·이전 판단·
검색 순위를 제공하지 않았다. 모델에게 원문 재작성이나 사실 요약을 시키지 않아
AI가 새로 쓴 사실 문장이 입력 자료에 합쳐지는 경로를 두지 않았다. 따라서 ‘사실 추가 없음’은
모델 문장을 저장 텍스트에 반영하지 않는다는 의미이며, 공개된 질의의 주장을 실제 사실로
검증했다는 뜻은 아니다. 판단 이유의 의미 정확성도 별도 문제다.

32건도 DRAFT이며 maskingCompleted=false, benchmarkEligible=false를 유지한다.
후속 자료 선택·중복·분할 점검이 끝난 뒤 승인된 평가 입력을 별도로 생성해야 한다.
사용자가 선택한 AI 중심 검수 방식을 따르되 humanReviewed=false로 출처를 명시했다.

## 산출물과 재현

- `tools/public_complaints/review_police_intake.py`: 사전 점검, 구조화된 AI 응답 검증,
  프롬프트·입력·모델 결합과 체크포인트, 점검 결과별 후보 구분.
- `tools/public_complaints/test_review_police_intake.py`: 개인정보 보류·불확실성·일반 질의 구분,
  잘못된 코드·출처 거절 및 이름 의심 자료의 사전 차단 검증.
- `docs/results/police-intake-screen-v1.json`: 개별 텍스트가 없는 집계 결과.
- `.local-data/police-intake-screen-v1/`: Git 제외 체크포인트, 원문 계보와 개별 이유가 포함된
  screened-drafts.jsonl. 입력 자료의 groupId와 해시를 보존한다.
- 이 문서, README 및 이전 수집 보고서 연결.

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/review_police_intake.py --output .local-data/police-intake-screen-v1
# 저장된 응답만으로 집계하며 API는 호출하지 않음
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/review_police_intake.py --output .local-data/police-intake-screen-v1 --summarize-only
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -q
```

다음 작업은 32건과 일반 절차 23건의 출처·유사 질의 중복을 점검하고, 같은 원문·파생
사례가 개발/최종 평가에 나뉘지 않도록 그룹을 고정하는 것이다. 이후 고정 템플릿용 사실
구조화와 경찰관 관점 관련성 라벨을 만든다. 현재는 2번 자료 확보·점검 단계이며
평가 데이터 고정(3번), 검색 전략 최종 비교(4번)는 완료되지 않았다.

추천 커밋: `feat(evaluation): screen police query intake for privacy and officer usefulness`

검증 결과: Python 테스트 94개, 기존 벤치마크 해시 검사, `git diff --check` 통과.
API 키 없이 체크포인트 집계를 반복해 결과 파일이 동일함을 확인했다.
Java 코드 변경이 없어 Java 테스트는 이번 단계에서 재실행하지 않았다.
