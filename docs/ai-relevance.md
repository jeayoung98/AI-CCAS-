# AI 관련성 판단

이 문서는 2026-10-26 v1 실행 기록이다. 최신 500건 확장 및 원문 구간 ID 방식은
[500건 AI 관련성 판단](ai-relevance-500.md)을 따른다. 아래 v1 재현 명령은 커밋
`75a2d18`의 스크립트 기준이며, 최신 스크립트로 v1 체크포인트를 덮어쓰지 않는다.

사용자가 추가 사람 검수를 생략하고 AI 판단을 사용하도록 요청했다. 기존 사람 확정
280쌍은 보존하고, 이미 검수를 시작한 경찰 사례 107건의 미선택 428개 및 보류 41개를
GMS `gpt-4o-mini`로 판단한다. 나머지 393건은 이번 469쌍 처리 범위에 포함되지 않는다.

모델에는 개인정보 검수 PASS인 가림 텍스트, 문서 후보 7개, 같은 사례의 기존 판단을
전달한다. 원문이나 키를 로그에 출력하지 않는다. 요청 목적과 단어 일치를 구분하도록
지시하며, 최종 처리 경로나 법적 판단을 생성하지 않는다. 근거 인용이 입력에 정확히
존재하지 않으면 인용을 버리고 확신도를 LOW로 낮춘다. 판단 이유도 AI 추정이다.

## 저장 및 확인

- `.local-data/acrc-ai-relevance-v1/ai-judgments.json`: AI 판단, 이유, 확신도, 모델,
  프롬프트 버전, 입력 해시, 사용량, 검증된 인용. 건별 저장으로 중단 후 재개 가능.
- 같은 폴더의 `effective-responses.json`: 사람 판단을 유지하고 빈 값/보류만 AI로 채운
  별도 평가 입력. 각 AI 항목의 이전 판단도 남긴다.
- 같은 폴더의 `index.html`: AI 판단 결과. 로컬 검수 서버의 `/ai-relevance`에서 확인.
- 기존 `acrc-human-review` 응답은 수정하지 않는다. 기존 검수 화면은 사람 기록만 표시한다.

`export_relevance.py`는 AI/HUMAN 출처, 모델, 확신도, 프롬프트 버전을 qrels에도 보존한다.
AI 판단으로 빈칸을 채웠다는 사실은 사람 검수 정답이나 실제 검색 정확도 검증이 아니다.
자료 이용 조건, 구조화 데이터 완성 및 검색 지식 적재 준비는 여전히 별도다.

## 2026-10-26 실행 결과

107건의 469쌍을 완료했다. AI 관련 104개/무관 365개, 사람 확정 280개 유지로 총
749쌍(관련 200/무관 549)을 `.local-data/acrc-qrels-ai-v1/`에 내보냈다. 경찰 사례의
기존 분리안은 dev 17건/test 90건이며 그룹 중복은 없다. 전체 500건 중 나머지
393건은 여전히 이번 관련성 판단 범위 밖이다.

원문에 정확히 존재하는 인용은 19개였다. 나머지 450개는 모델이 제시한 인용이
정확히 일치하지 않아 인용을 폐기하고 LOW로 낮췄다. 최종 확신도는 HIGH 18,
MEDIUM 1, LOW 450이다. LOW도 사용자의 요청에 따라 이진 값을 저장하지만,
검증된 사실이나 고품질 정답으로 간주하지 않는다. AI 이유 자체도 오판 가능성이 있다.

초기 응답의 형식 오류를 고친 뒤 실패 항목만 재시도했다. 기존 사람 확정 280개가
병합 후에도 동일함을 비교했고, 실제 HTTP 결과 화면 200 응답 및 Python 테스트
41개 통과를 확인했다. Java 코드는 변경하지 않았다.

## 실행

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/judge_relevance.py --output .local-data/acrc-ai-relevance-v1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/export_relevance.py --responses .local-data/acrc-ai-relevance-v1/effective-responses.json --output .local-data/acrc-qrels-ai-v1
```

같은 입력 해시·모델·프롬프트 버전일 때만 중단 지점부터 재개한다. 작업 중 입력이
변경되면 결과 병합을 거부한다. 키는 현재 세션의 환경변수로만 읽는다.

변경 파일: `judge_relevance.py`, `test_judge_relevance.py`, `export_relevance.py`,
`test_export_relevance.py`, `review_server.py`, `test_review_server.py`, `relevance.html`
(이상 `tools/public_complaints/`) 및 이 문서.

커밋 메시지: `feat(evaluation): label pending relevance with traceable AI judgments`
