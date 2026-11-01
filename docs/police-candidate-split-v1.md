# 경찰관 평가 후보의 질의 그룹과 분할 고정

기준일: 2026-10-30. 이전 AI 점검에서 선정한 구체적 상황 32건과 일반 절차 23건,
총 55건의 출처 계보를 확인하고 개발·평가 분할을 만들었다. 원문을 새로 쓰거나 사례를
삭제하지 않았으며 검색 정답이나 임베딩을 생성하지 않았다.

후속 [55건 사실 구조화와 Java 입력 검증](police-structure-validation-v1.md)을 완료했다.
아래의 미완료 항목은 분할 종료 시점의 기록이다.

## 결과

| 구분 | 개발 | 평가 | 합계 |
|---|---:|---:|---:|
| 구체적 상황 후보 | 15 | 17 | 32 |
| 일반 절차 후보 | 12 | 11 | 23 |
| 전체 | 27 | 28 | 55 |

55건을 40개 질의 그룹으로 묶었다. 이 중 8개는 둘 이상의 질의가 있는 그룹이다.
분할 간 같은 그룹의 중복은 0개다. 이전 수동 조사에 노출된 출처 3건이 포함된 가족
전체를 개발용으로 배치했으며 이에 따라 개발용으로 고정된 자료는 9건이다.
이전에 노출된 그룹의 평가용 배치는 0건이다.

## 그룹을 만든 근거

1. URL의 페이지 번호 등과 무관한 게시물 ID로 동일 출처를 식별한다.
2. 후보 내부와 기존 500건에 대해 기호·공백을 제거한 동일 문장, 문자 3-gram
   Jaccard 0.30 이상이면서 SequenceMatcher 0.55 이상인 문장을 찾았다.
3. 후보 55건을 `gpt-4o-2024-08-06`에 보여주고, 주요 상황과 현재 요청 의미가
   사실상 같은 질의를 묶도록 했다. URL·이전 코드·이전 판단·목표 분할은 제공하지 않았다.
4. 동일 출처·문자 유사·AI 제안 관계를 전이적으로 연결해 그룹을 만들었다.

문자 검사에서 후보 내부와 기존 500건의 일치 쌍은 각각 0개였다. 반면 AI는 8개 묶음을
제안했다. 따라서 문자 검사만으로 의미 중복이 없다고 주장할 수 없다. AI 묶음도 잠정
판단이며 같은 사건이나 같은 신고자임을 입증하지 않는다. 여기서는 평가 누출 가능성을
줄이려는 질의 가족으로 다루고, 묶인 자료도 모두 보존한다.

기존 500건과의 비교는 문자 검사까지만 수행했다. 다른 사이트에 재게시된 자료나 의미가
같은 과거 질의를 놓칠 수 있으며, 새 평가 28건이 완전히 독립된 자료라고 인증하지 않는다.
원래 500건과 점수를 합산하거나 같은 벤치마크 성능으로 비교하지 않는다.

## 분할 고정 방식

정렬한 구성원 ID의 SHA256으로 그룹 ID를 만들고, 그룹 ID 해시의 정수 나머지가
40 미만이면 개발, 나머지는 평가로 배치한다. 이전 노출 출처가 하나라도 있으면
그 그룹 전체를 개발용으로 배치한다. 사례를 개별적으로 옮겨 비율을 맞추지 않는다.
결과적으로 개발 27건·평가 28건이며, 이는 업무별 균형을 보장하는 층화 분할은 아니다.

입력·기존 500건·이전 수동 출처·AI 프롬프트·모델·그룹 규칙·분할 규칙을 결합해
체크포인트를 재사용한다. 조건이 바뀌면 동일 디렉터리를 재개할 수 없다. 이미 생성한
candidates.jsonl과 다른 분할이 계산되면 덮어쓰지 않고 중단한다.
공개 [고정 결과](results/police-candidates-split-v1.json)에는 자료 해시, 그룹 구성원 ID,
분할과 노출 여부를 기록하며 개인 질의 텍스트는 포함하지 않는다.

## 변경 파일과 검증

- `tools/public_complaints/freeze_police_candidates.py`: 출처·문자 중복 확인, AI 질의 가족,
  전이 그룹과 이전 노출 처리, 안정적인 분할·해시 기록.
- `tools/public_complaints/test_freeze_police_candidates.py`: 전이 그룹의 분할 일관성,
  노출 그룹의 개발 강제, 입력 순서 독립성, 모델이 만든 잘못된 ID 거절.
- `docs/results/police-candidates-split-v1.json`: 55건의 분할과 그룹 ID 고정 결과.
- 이 문서, README·이전 점검 보고서 연결.
- `.local-data/police-candidates-split-v1/`: 질의 본문·AI 이유가 있는 로컬 결과. Git 제외.

Python 테스트 96개, 기존 벤치마크 해시 검사, `git diff --check` 통과.
API 키 없이 별도 프로세스에서 다시 집계해 후보와 요약 파일이 바이트 단위로 동일했다.
Java 변경이 없어 Java 테스트는 재실행하지 않았다.

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/freeze_police_candidates.py --output .local-data/police-candidates-split-v1
# 기존 AI 판단으로 재생성, API 호출 없음
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/freeze_police_candidates.py --output .local-data/police-candidates-split-v1 --offline
```

다음은 고정 후보의 사실 구조화와 Java DTO·SearchTextBuilder 검증이다. 이후 경찰관에게
유용한 근거의 관련성 라벨을 만들고, 같은 데이터에서 검색 전략을 비교한다. 이번에
고정한 것은 질의 구성·분할이며 **3번 평가 데이터 고정 전체가 완료된 것은 아니다.**
자료는 여전히 DRAFT, maskingCompleted=false, benchmarkEligible=false이며 구조화·검증을
통과한 평가 입력을 별도로 만들어야 한다. 이미 AI 점검을 거친 자료이므로 원시 상태의
미접촉 최종 평가 자료라고 부르지 않는다.

추천 커밋: `feat(evaluation): freeze police query families and development split`
