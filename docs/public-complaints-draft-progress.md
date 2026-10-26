# 실제 민원 평가: 자동 점검 및 초안

2026-10-25 사용자가 사람이 검수한 자료가 없으며 자동 점검과 AI 초안을 먼저 준비하도록
지시했다. 자동으로 확인 가능한 범위와 사람이 검토해야 하는 범위를 구분했다.

## 현재 결과

* 원본 500건은 변경하지 않았다. 이전 수집 보고서의 SHA-256과 일치함을 확인했다.
* 추가 가림 처리는 검수용 새 사본 204건에 적용됐다. 띄어 쓴 가림 이름, 출생연도,
  띄어 쓴 나이, 가림 처리된 도로명의 잔여 번지 등을 대상으로 한다.
* 모든 사례에 요약 발췌·검토할 문장·마지막 문장의 요청 근거 후보를 생성했다.
  본문에서 확인되지 않은 장소·관계·현재성은 UNKNOWN으로 남겼다.
* 코드 후보는 본문의 해당 문장과 연결하고 UNCERTAIN으로 표시했다. 위험 후보가 나온
  사례는 5건이다. 나머지 495건이 안전하다는 뜻은 아니다. 부정·인용·과거 상황을 이 규칙이
  판단할 수 없으며 키워드 없는 위험 표현도 놓칠 수 있다.
* 관찰 사실의 확정, 근거 없는 코드 보완, 채널별 검색 정답은 생성하지 않았다.
* 발췌가 절단된 41건은 원문 확인 필요, 이전 주소 가림이 있는 67건은 정보 손실 여부
  확인 필요로 표시했다. 67건 모두 오류라는 뜻은 아니다.
* 공식 출처 3개를 후보로 기록했다. 검수·chunk 작성·프로젝트 코드 매핑은 미완료다.
* 단위 테스트 21개와 실제 500건의 증거 문장·해시·미검수 상태·정답 null 검사가 통과했다.

이 초안은 **AI가 작성한 로컬 규칙에 따른 추출 결과**다. 500건 각각을 LLM에 보내 의미를
분석한 결과나 사람이 검수한 구조화 JSON이 아니다. 외부 API 호출은 없다.
불완전한 초안을 정상 API 입력으로 쓰지 않도록 `draftStructuredComplaint`로 분리했고,
관찰 사실과 검출되지 않은 코드 목록은 null이다. 원본 검수 대기열은 덮어쓰지 않았다.

## 8단계 진행 상태

| 단계 | 수행한 작업 | 남은 조건 |
|---|---|---|
| 1. 데이터 검수 | 전체 자동 점검 및 추가 가림 | 사람의 개인정보·이용 조건 확인, 절단·유사 사례 확인 |
| 2. 구조화 | 500건 추출식 초안 | 사실/주장 구분 및 코드·현재성 검토 |
| 3. 검색 지식 | 공식 출처 후보 3개 | 별도의 검수 사례, 코드별 유형 기준, 공식 안내 chunk 확보 |
| 4. 정답 | 검수 필드와 근거 입력 구조 유지 | 고정 corpus를 보고 채널별 정답 지정 |
| 5. 평가 데이터 | 기존 dev 100/test 400 초안 유지 | 동일 사건·정답 검수 후 분리 확정 |
| 6. 기준 성능 | 실행 환경 확인 | 마스킹 완료 데이터, 검수 corpus/정답, API 키, Docker 또는 격리 DB |
| 7. 개선 비교 | 실행하지 않음 | 기준 실험 완료 후 개발 데이터로 전략 선정 |
| 8. 최종 검증 | 운영 설정 변경 없음 | 고정된 평가 데이터로 최종 검증 |

모든 사례의 maskingCompleted/humanReviewed는 false이며 평가 정답은 null이다.
AGENTS.md의 마스킹 완료 입력 조건에 따라 임베딩과 DB 적재를 실행하지 않았다.
현재 프로세스에서 OPENAI_API_KEY는 설정되지 않았고 Docker daemon 연결에도 실패했다.
다른 경로의 비밀정보를 찾거나 출력하지 않았다. 새 Java 코드를 추가하지 않았으므로
기존 Java 빌드/통합 테스트를 이번 Python 데이터 준비 작업에서 다시 실행하지 않았다.

## 파일과 재현

* `.local-data/acrc-drafts-500-v2/drafts.jsonl`: 현재 검수용 초안
* `.local-data/acrc-drafts-500-v2/summary.json`: 본문 없는 집계
* [집계 보고서](results/public-complaints-draft-summary.json)
* [공식 출처 후보](results/public-complaints-knowledge-source-candidates.json)

초기 v1 출력은 가림 규칙 적용 순서 테스트 실패 당시의 결과이므로 폐기했다. 규칙 순서를
보완하고 테스트를 모두 통과한 뒤 v2를 생성했다. 공개 번호 112와 역사적 신고 표현을
잘못 바꾸거나 현재 신고 요청으로 해석하지 않는지 테스트한다.

```powershell
$env:PYTHONIOENCODING='utf-8'
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -v
# 테스트 성공 후 새 출력 경로로 실행 (기존 검수 파일 덮어쓰기 금지)
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/draft_review.py --output .local-data/acrc-drafts-500-v3
```

## 출처 후보

* [국민권익위원회 고충민원 제도 소개](https://www.acrc.go.kr/menu.es?mid=a10102010100): 고충민원 범위 검토용.
* [고충민원 신청방법 안내](https://www.acrc.go.kr/menu.es?mid=a10201010100): 신청 방법 안내 구간 검토용.
* [국민신문고 소개](https://www.acrc.go.kr/menu.es?mid=a10104030100): 온라인 민원 창구 설명 검토용.

이 세 자료는 공식 출처라는 사실만 확인한 후보이며 검증된 검색 corpus가 아니다.
일반 포털 안내를 모든 민원의 정답으로 지정하거나 평가 질의를 자기 자신의 정답 사례로
적재하는 방법은 사용하지 않았다.

변경 파일: `tools/public_complaints/draft_review.py`, `test_draft_review.py`, 해당 폴더의
`README.md`, 이 문서, `docs/results/public-complaints-draft-summary.json`,
`docs/results/public-complaints-knowledge-source-candidates.json`.

추천 커밋: `feat(evaluation): add extractive complaint drafts and review audit`
