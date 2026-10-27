# 500건 AI 관련성 판단 확장

2026-10-27 사용자의 요청으로 기존 경찰 사례 107건 외의 393건을 평가용 관련성
판단 범위에 추가했다. 기존 AI 저확신 450쌍도 재점검한다. 사람이 확정한 280쌍과
기존 AI HIGH/MEDIUM 19쌍은 유지한다. 추가 판단 2,751쌍과 재점검 450쌍을 합쳐
이번 실행 대상은 3,201쌍, 496개 사례다.

## 실행 결과

500건 × 문서 7개 = 3,500쌍의 관련성 입력이 완료됐다. 관련 353개, 무관 3,147개이며
사람 판단 280개와 AI 판단 3,220개를 구분해 저장했다. 이번 실행의 3,201쌍은 원문
구간 연결 검증을 모두 통과했다. 모델 자체 확신도는 HIGH 2,834개/MEDIUM 367개다.
기존 AI LOW 450개 중 37개에서 이진 판단이 바뀌었으며, 정확도 개선을 입증한 수치는 아니다.

기존 사람 판단 280개와 유지 대상 AI HIGH/MEDIUM 19개는 입력과 비교해 완전히
동일함을 확인했다. 이전 응답 파일은 보존했다. 최종 qrels는
`.local-data/acrc-qrels-ai-v2/`이며 dev 100건/test 400건, 그룹 중복 0개다.
[집계 스냅샷](results/ai-relevance-500-summary.json)에는 본문이나 개인별 응답이 없다.

처음의 구간 ID 누락 오류를 수정한 뒤 체크포인트에서 재개하여 496건을 실패 없이
마쳤다. 이 결과는 관련성 라벨 준비의 완료이며 실제 임베딩 검색 성능 측정 결과가 아니다.

## 원문 구간 ID 방식

이전에는 모델이 작성한 인용문과 입력 문자열을 비교했다. 인용 대신 요약을 작성하면
일치 검증에 실패했다. v2에서는 입력을 220자 구간으로 나눠 `s0`, `s1` 등의 ID를
제공하고 모델이 ID를 선택한다. 저장할 근거 문장은 프로그램이 해당 구간에서 가져온다.
무관 판단도 민원의 실제 요청이 드러나는 구간을 선택해야 한다. 없는 ID·빈 목록·중복
문서·잘못된 판단 값을 거부하며 형식 오류는 구체적인 검증 오류와 함께 재시도한다.

이 검사는 원문 연결의 무결성만 보장한다. 선택한 구간이 판단을 뒷받침하는지와
판단 자체가 맞는지는 별개다. 모델이 LOW라고 판단하면 ID 검증에 성공해도 LOW를
유지한다. 입력 구간 ID를 검증했다는 이유로 정확도 100%라고 표현하지 않는다.
HIGH/MEDIUM/LOW는 모델의 자체 판단이며 별도 보정된 확률이나 검증 정확도가 아니다.

새 사례를 추가할 때는 500건 각각의 개인정보 PASS와 본문 해시를 확인한다.
사람 확정값은 재점검 대상으로 선택할 수 없다. AI LOW만 명시적으로 재점검하고,
이전 값은 `previousJudgment`로 보존한다. 원본 응답과 v1 결과는 변경하지 않는다.

## 재현

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/judge_relevance.py --responses .local-data/acrc-ai-relevance-v1/effective-responses.json --output .local-data/acrc-ai-relevance-v2 --all-queries --recheck-low
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/export_relevance.py --responses .local-data/acrc-ai-relevance-v2/effective-responses.json --output .local-data/acrc-qrels-ai-v2
```

`--all-queries` 없이 실행하면 기존 응답이 있는 사례만 처리한다. `--recheck-low`가
없으면 이미 있는 AI 이진 판단도 유지한다. 입력 해시·모델·프롬프트 버전·두 옵션이
같을 때만 체크포인트에서 재개한다. 기존 출력 폴더를 다른 입력으로 덮어쓰지 않는다.

`.local-data/acrc-ai-relevance-v2/summary.json`에는 판단 출처, 확신도, 원문 연결,
재점검 변경 건수와 입력 해시를 기록한다. `/ai-relevance`는 v2 보고서가 있으면
이를 보여주고 없으면 v1을 보여준다. 다른 보고서는 서버 `--ai-report`로 지정한다.

검증: Python 44개 테스트 통과. 새 사례의 개인정보 검수 누락 거부, 사람 판단 보존,
이전 AI 판단 보존, 존재하지 않는 근거 ID 거부, LOW 유지 등을 포함한다. Java 변경은 없다.

자료 이용 조건, 구조화 DTO 완성, 검색 지식 적재와 실제 임베딩 검색 비교는 이 작업과
별개다. 500건의 관련성 값이 채워져도 전체 RAG 품질이나 분류 정확도 검증 완료가 아니다.

변경 파일: `judge_relevance.py`, `test_judge_relevance.py`, `export_relevance.py`,
`review_server.py` (이상 `tools/public_complaints/`), 이 문서, `docs/ai-relevance.md`,
`docs/results/ai-relevance-500-summary.json`. 포트폴리오 설명은 `docs/portfolio-notes.md`로 별도 커밋했다.
추천 커밋: `feat(evaluation): extend AI relevance labels to all 500 cases`
