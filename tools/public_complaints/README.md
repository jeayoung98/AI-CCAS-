# 실제 공개 민원 사례 수집

국민권익위원회의 [고충민원 의결정보](https://www.acrc.go.kr/board.es?mid=a10504010000&bid=1010)에서
본문의 신청 원인/민원 요지/신청 내용을 수집한다. FAQ, 작성 예시, 합성 질의가 아니다.
기관이 실제 고충민원을 정리해 공개한 결정례의 발췌문이며 민원인이 제출한 원문과는 다르다.

## 2026-10-23 수집 결과

실제 공개 게시물 755개를 확인하여 중복 없는 500건을 확보했다. 경찰 107건, 주택건축
136건, 복지노동 69건, 산업농림환경 51건, 국방보훈 51건, 재정세무 39건,
도시수자원 26건, 교통도로 11건, 행정문화교육 7건, 기업·취약계층·집단 3건이다.
게시일 범위는 2015-11-11~2020-02-17이며 41건은 길이 제한으로 발췌가 절단되었다.
최근 게시물은 첨부파일만 제공되는 경우가 있어 이 자료는 최신 민원 대표 표본이 아니다.

수집기 테스트 7개 및 500건 전체 자동 검사를 통과했다. ID·출처 URL·가림 텍스트
중복, 해시, 길이, 식별정보 패턴, 답변 제목 잔여, 검토 상태를 검사했다.
이는 사람의 개인정보 검토나 검색 정답 검수 완료를 뜻하지 않는다.
검색 실험은 이 데이터로 아직 실행하지 않았다.
집계와 데이터 파일 해시는 [수집 결과](../../docs/results/public-complaints-500-summary.json)에 있다.
민원 발췌문은 Git에서 제외된 로컬 파일에만 보관한다.

## 범위 및 데이터 보호

* 경찰민원 분류를 먼저 순회하고 이후 전체 분류의 오래된 페이지부터 순회한다.
  초기 실행에서 처리한 최신 게시물의 기록은 재개 시 유지한다. 첨부파일만 있는 최근
  게시물보다 HTML 본문이 공개된 과거 자료를 우선하며 최신 민원 분포를 대표하지 않는다.
* 로그인이나 비공개 자료에 접근하지 않는다. robots.txt를 확인하고 순차 요청한다.
  403/429 등 접근 거부·호출 제한 발생 시 중단한다.
* HTTP 응답 원문/첨부파일을 디스크에 저장하지 않는다. 공개 HTML의 민원 요구 부분만
  메모리에서 추출한다. 주문·기관 답변·판단은 질의용 텍스트에서 제외한다.
* 이메일·연락처·식별번호·사건번호·일부 가림 이름·날짜·나이·상세주소·차량번호 패턴을
  추가 가림 처리한다. 미가림 이름 의심 패턴이 있거나 섹션 경계가 불명확하면 제외한다.
* 발췌는 최대 160개 공백 단위 단어, 1,200자로 제한하고 절단 여부를 기록한다.
* 개인정보 처리 모델을 구현한 것이 아니다. 규칙으로 모든 개인정보를 탐지할 수 없으므로
  `privacy_review_status=AUTOMATED_REDACTION_PENDING_MANUAL_REVIEW`,
  `maskingCompleted=false`, `review_status=REVIEW_REQUIRED`, `human_reviewed=false`다.
* 출처별 이용 조건을 각 행의 `license_notice`에 보존한다. 데이터는 Git 제외 경로의 로컬
  검토 자료로 보관하며 공개 배포/상업적 사용/DB 적재를 수행하지 않는다. 수집한 페이지의
  공공누리 유형과 AI 이용 안내 문구는 `license_notice` 및 `validation.json`에서 확인한다.
  공개 배포나 재가공 활용 전 해당 조건을 별도로 확인해야 한다.
* 원문별 ID와 가림 텍스트 SHA-256으로 중복을 제거한다. 500건에 미달하면 실패를 보고하며
  합성 자료나 단순 제목 목록으로 부족분을 채우지 않는다.

## 실행

```powershell
python -m venv .local-data/collector-venv
.\.local-data\collector-venv\Scripts\python.exe -m pip install -r tools/public_complaints/requirements.txt
$env:PYTHONIOENCODING='utf-8'
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -v
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/collect_acrc.py --count 500
```

기본 요청 간격은 0.35초이며 네트워크 대기 시간이 추가된다. 동일 출력 경로로 재실행하면
완료/제외한 게시물을 건너뛴다. 새로 수집하려면 `--output .local-data/acrc-new-run`을 지정한다.

출력:

* `.local-data/acrc-public-500/complaints.jsonl`: 출처 URL, 분야, 게시일, 비식별 처리한 제목과
  민원 발췌, 이용 조건, 검토 상태를 포함한 실제 자료
* `manifest.json`: 수량·분야·제외 사유·수집 조건
* `audit.jsonl`: 처리한 공개 게시물 ID 및 채택/제외 사유. 원문 없음

수집 후 검증:

```powershell
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/validate_dataset.py
```

## 검색 평가에 사용하기 전

2026-10-25 자동 추가 가림 및 추출식 초안은 [진행 보고서](../../docs/public-complaints-draft-progress.md)를 따른다.
`draft_review.py`는 미검수 상태를 유지하며 초안의 코드 후보를 검색 정답으로 지정하지 않는다.

500건 검수 대기열과 dev/test 분리 초안은 [평가 준비 문서](../../docs/public-complaints-evaluation-preparation.md)를 따른다.
`prepare_review.py`는 정답을 추정하지 않으며 기존 검수 폴더를 덮어쓰지 않는다.

실제 민원 출처라는 사실은 정답 검수가 완료됐다는 뜻이 아니다. 먼저 개인정보와 이용 조건을
검토하고 실제 요청에서 사실·대상·행위를 구조화해야 한다. 각 질의의 관련 문서와 no-match,
ambiguity 정답도 사람이 별도로 검수해야 한다. 현재 스크립트는 구조화 정답·검색 정답을
추정하거나 OpenAI API를 호출하지 않고 production retrieval DB에도 적재하지 않는다.

의결 결과와 민원 발췌를 같은 검색 텍스트에 섞으면 정답 유출이 생길 수 있다. 동일 사건의
본문/변형/유사 재게시를 하나의 그룹으로 묶어 dev/test를 분리해야 한다. 경찰 관련 고충민원과
일반 유실물 조회·신고 안내 질의의 분포가 다르다는 점도 평가 결과에 명시해야 한다.
