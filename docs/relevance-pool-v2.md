# 2차 관련성 검수 후보

2026-10-26 기존 공식 안내 3개에 경찰 관련 세부 안내 4개를 더해 7개로 확장했다.
기존 문서 객체가 동일함을 확인한 경우에만 이전 판단을 새 파일로 옮겼다.
107건의 321개 판단(관련 96, 무관 184, 보류 41)을 보존했고 새 문서 4개는 null이다.
원본 1차 응답 파일은 변경하지 않았다. 새 문서가 추가돼 기존 107건도 새 목록에 대해서는
미완료로 표시되는 것이 정상이다. 이는 이전 응답이 삭제됐다는 의미가 아니다.

## 추가한 문서

* 수사 이의·수사관 기피 신청 방법 — [경찰민원24 이용안내](https://minwon24.police.go.kr/custServiceCenter/siteUtztnGdInfo.do)
* 신청 민원 처리 결과 확인 — 같은 이용안내의 나의민원 및 신청 결과 구간
* 정보공개 청구 제도 — [서울경찰청 정보공개 안내](https://www.smpa.go.kr/user/nd21910.do)
* 경찰 직무집행 손실보상 — [서울경찰청 손실보상 안내](https://www.smpa.go.kr/user/nd61776.do)

공식 본문을 바탕으로 작성한 짧은 요약이며 원문 전체나 법적 판단 결과가 아니다.
신청·청구 방법 설명을 개별 사건의 권리 인정이나 처리 결과 보장으로 확장하지 않았다.
세종경찰청의 면허 처분·교통사고 이의조사 안내는 검색 결과에 있었지만 원문 직접 요청이
HTTP 404여서 이번 목록에서 제외했다.

## 화면 및 파일

http://127.0.0.1:8765/relevance 를 새로고침하면 2차 화면이 열린다.
‘확정한 문서 접기’가 기본 선택돼 새 문서와 기존 보류 문서가 표시된다.
기존 관련/무관 응답을 다시 보려면 이 옵션을 해제한다.

* 문서 목록: `tools/public_complaints/relevance_documents_v2.json`
* 새 응답: `.local-data/acrc-human-review/relevance-responses-v2.json`
* 이관 근거: `.local-data/acrc-human-review/relevance-responses-v2.migration.json`
* 이전 목록과 `relevance-responses.json`은 1차 스냅샷으로 유지한다.

확정한 문서를 숨겨도 저장 요청에는 전체 문서의 판단이 포함되므로 기존 응답이 사라지지
않는다. 새 문서는 자동으로 관련/무관을 정하지 않는다. 보류 이유와 검토 시각도 보존한다.
문서 내용이 달라졌거나 이전 목록의 일부가 빠지면 자동 이관을 거부한다.

검증: 전체 Python 테스트 34개, HTML JavaScript 문법 검사. 실제 저장 파일에서 기존
321개 판단의 동일성과 새 428개 판단(107×4)의 null 상태를 확인한다.

현재 후보는 유형 기준 1개/공식 안내 6개이며 검증 사례는 없다. 전체 서비스의 정확도를
검증할 corpus가 완성된 것은 아니다. 자료 이용 조건과 지식 적재 검수도 별도이며
이번 작업에서 GMS 임베딩이나 운영 DB 적재는 실행하지 않았다.

변경: `relevance_documents_v2.json`, `extend_relevance.py`, `test_extend_relevance.py`,
`review_server.py`, `relevance.html`(모두 tools/public_complaints/), 이 문서.
추천 커밋: `feat(evaluation): expand police guide pool and preserve prior relevance labels`
