# 경찰청 공개 질의 JSON API 수집

기준일: 2026-10-30. 사용자가 경찰청 목록·본문 JSON 항목을 설정한 뒤 실제 목록과
본문 JSON 수신에 성공했다. 기존 XML 수집 시도는 미신청 안내를 반환했으므로 JSON으로
요청·파싱하도록 도구를 수정했다. 이 관측으로 제공기관의 권한 구성 원인까지 확정하지 않는다.

후속 [AI 개인정보·경찰 업무 점검 결과](police-intake-screen-v1.md)를 추가했다.
아래의 점검 전 상태는 수집 종료 시점의 기록이다.

## 실행 범위

- 최대 5건 smoke 수집을 실행해 질의 초안 5건을 저장했다.
- 본 수집은 최대 100건, 키워드별 최대 15건, 키워드당 최대 5페이지다.
- 유실물·분실·순찰·중고·교통·진행·안전조치·불친절로 본문 검색한다.
- 키워드는 후보 발견용이다. 예를 들어 순찰 검색에 걸린 질의가 실제 순찰 요청이라고
  자동 판정하지 않는다. 검색별 개수를 업무별 평가 사례 수로 부르지 않는다.
- 같은 API 원문 ID는 수집 실행 내에서 중복 저장하지 않는다. 기존 수동 초안 및 다른
  사이트에 재게시된 질의와의 중복은 별도 확인해야 한다.

출처 ID가 같은 목록·본문인지 확인하고 질의요지만 추출한다. 회답·이유·관련법령·원문
HTML은 저장하지 않는다. 개인정보 정규식 치환과 의심 이름·길이 제한을 통과한 초안만
Git 제외 경로에 저장한다. 자동 점검만으로 개인정보 제거가 완료됐다고 선언하지 않고
`maskingCompleted=false`, `privacyReview=AUTOMATED_SCREEN_ONLY`로 유지한다.

## 수집 결과

수집을 정상 종료해 원문 ID가 서로 다른 공개 질의 초안 **89건**을 저장했다.
상한 100건을 채우기 위해 특정 키워드의 상한을 늘리거나 질의를 생성하지 않았다.

| 발견 키워드 | 초안 수 |
|---|---:|
| 유실물 | 13 |
| 분실 | 15 |
| 순찰 | 15 |
| 중고 | 10 |
| 교통 | 15 |
| 진행 | 15 |
| 안전조치 | 5 |
| 불친절 | 1 |

수집 실행 내 원문 ID 중복은 없다. 이전 수동 초안 4건 중 국가법령정보센터 출처 3건은
이번 89건에 포함된다. 이를 새 사례로 중복 합산하지 않는다. 지자체 순찰 초안은 별도
보류 상태를 유지한다. 기존 500건의 다른 사이트 재게시·의미 중복 확인은 남아 있다.

89건 모두 비식별 결과와 업무 범위에 대한 추가 점검이 필요하다. 이번 실행의 자동 제외는
0건이며 정규식 검사를 통과한 것이 개인정보 제거 완료를 뜻하지 않는다.
검색 관련성 정답은 아직 없고 benchmarkEligibleCount도 0이다.

개별 질의는 `.local-data/police-law-api-intake-v2-json/drafts.jsonl`에만 저장했다.
공개 변경에는 [집계](results/police-law-json-intake-v2.json)와
[출처 목록](../tools/public_complaints/police_law_sources_v2.json)만 포함한다.
집계에 실제 수집 프로토콜·초안 파일 해시와 기존 수동 초안의 중복 그룹을 기록했다.

## 재현 명령

```powershell
. .\tools\Use-LawApi.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/collect_police_law_api.py --execute --approved --limit 100 --pages 5 --per-keyword-limit 15 --output .local-data/police-law-api-intake-v2-json
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -q
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/benchmark_lock.py --lock docs/results/benchmark-public-500-pool20-ai-v3.json --verify
```

이미 존재하는 출력 폴더에는 덮어쓰지 않는다. 재실행 시 새 폴더를 지정한다.

## 변경 파일·검증

- `tools/public_complaints/collect_police_law_api.py`: 실제 JSON 응답 구조 파싱, 출처 ID 확인,
  기존 비식별 검사 재사용, 키워드별 수집 상한.
- `tools/public_complaints/test_collect_police_law_api.py`: JSON 오류 응답·ID 불일치 거절,
  회답 미저장, 뒤쪽 키워드도 수집되는지 검증.
- 이 문서, API 신청 안내서·README 연결, 결과 집계와 출처 목록.

Python 테스트 91개 통과. 기존 벤치마크 해시 검사 통과. Java 코드 변경이 없어
Java 테스트는 재실행하지 않았다. API 응답 형식은 실제 smoke 수집에서 확인했다.

다음은 실제 업무 포함 범위·비식별 결과·기존 출처 중복을 AI로 점검하고, 통과 자료만
구조화와 관련성 라벨 생성으로 넘기는 것이다. 이번 수집 자체는 검증 사례 승인이나
경찰관 업무 전반의 검색 품질 검증이 아니다.

추천 커밋: `feat(evaluation): collect police public queries through approved JSON API`
