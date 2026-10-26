# 실제 민원 평가 준비 점검

`check_readiness.py`는 저장된 검수 응답과 초안, 문서 목록을 읽어 JSON과 HTML 보고서를 만든다.
기존 qrels 내보내기의 스냅샷 해시·문서 ID·개인정보 검수 검증을 재사용한다. 관련성 응답이
없는 사례도 개인정보 검수의 본문 해시가 일치하는지 확인한다.

2026-10-26 점검 결과:

- 개인정보·요약·의도·위험 표현·현재성·충분성: 각 500건 PASS.
- 관련성 확정 280쌍, 새 문서 미선택 428개, 보류 41개. 나머지 393건은 관련성 미검수.
- 구조화 초안 누락: observedFacts 500건, requestedActions 496건, riskSignals 495건,
  subjectMatters 469건. null 위험 신호를 위험 없음으로 변환하지 않는다.
- 검색 문서 7개 모두 문서 검수 및 이용 조건 확인 대기.
- 근거 상태·위험 여부·사례 없음·모호성 평가 정답 각각 500건 미작성.

사람의 검수 응답과 구조화 값의 완성 여부는 별개다. 누락 점검은 Java DTO 전체 유효성
검사나 DB 적재 승인이 아니다. 실제 민원의 이용 조건과 dev/test 분리 확정도 별도다.
원본/응답 수정, API 호출, 임베딩, DB 적재를 수행하지 않는다. 출력에는 집계만 포함한다.

## 실행

```powershell
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/check_readiness.py --output .local-data/readiness-new
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -v
```

출력 폴더의 `index.html`을 열면 현황과 관련성 검수 화면 링크가 표시된다. 정적 보고서이므로
응답이 바뀌면 새 출력 경로로 다시 생성한다. 기존 출력은 덮어쓰지 않는다.
`--source`, `--corpus`, `--responses`, `--privacy`로 다른 스냅샷을 지정할 수 있다.

검증: Python 38개 테스트 통과. 기존 입력 보존, 오래된 개인정보 검수 거부, 출력 보존을
포함한다. Java 변경은 없으며 이 작업에서는 Java 테스트를 다시 실행하지 않았다.

변경 파일:

- `tools/public_complaints/check_readiness.py`
- `tools/public_complaints/test_check_readiness.py`
- `docs/evaluation-readiness.md`

커밋 메시지: `feat(evaluation): report review progress and missing evaluation inputs`
