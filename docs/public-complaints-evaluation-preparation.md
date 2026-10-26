# 실제 민원 500건 평가 준비

2026-10-25 수집 자료를 재검사하고 검수 대기열 및 개발/평가 분리 초안을 생성했다.
500건은 실제 공개 민원 발췌이며, 검색 정답이 완성된 데이터가 아니다.
이번 작업은 임베딩 생성이나 검색 성능 실험을 실행하지 않았다.

## 실행 결과

* 원본 500건 자동 검사를 통과했다. 원본 파일은 수정하지 않았다.
* 문자 5-gram Jaccard 0.65 이상 또는 동일 출처 ID를 연결해 496개 그룹을 만들었다.
  유사 후보는 4쌍/4그룹이다. 동일 사건이라는 확정 판단은 아니다.
* 그룹을 나누지 않고 dev 100건, test 400건을 배정했다. 그룹 교집합은 0개다.
* 경찰민원은 dev 17건/test 90건이다. 이 분리는 분야별 비율을 맞춘 표본이 아니며
  아직 확정하지 않았다. 결과를 보기 전에 사례 그룹과 분야별 구성을 검토해 확정한다.
* 길이 제한으로 절단된 41건은 `TRUNCATED_EXCERPT`로 표시했다.
* 개인정보/이용 조건/구조화/관련 문서/동일 사건 검수는 각 500건 모두 대기 상태다.
* 수집기 7개와 준비 도구 6개, 총 13개 단위 테스트가 통과했다.

집계: [평가 준비 결과](results/public-complaints-preparation-summary.json).
본문과 검수 파일은 Git에서 제외된 `.local-data/acrc-review-500-v1/`에 있다.

## 검수 파일 사용법

`review-queue.jsonl`의 각 행은 하나의 민원이다. 경찰민원을 먼저 배치했으며 출처,
가림 텍스트, 출처 텍스트 해시, 분리 초안 및 검수 항목을 포함한다.
기존 원본을 수정하지 않고 이 검수용 사본에서 작업한다. 새 개인정보를 기입하지 않는다.

1. `reviews.privacy`: 남아 있는 식별정보와 제목을 확인하고 필요한 가림 처리를 보완한다.
   검토자·검토일·근거를 기록한 뒤에만 `maskingCompleted`를 true로 변경한다.
2. `reviews.usage`: 각 행의 `licenseNotice`와 실제 사용 목적의 조건을 확인한다.
3. `reviews.caseGrouping`: `duplicate-candidates.json`의 후보를 확인하고, 누락된 동일 사건도
   같은 그룹으로 묶는다. 어휘가 비슷하다고 동일 사건으로 확정하지 않는다.
4. `reviews.structure`: `StructuredComplaint`의 기존 스키마에 맞춰 사실 기반 구조화 JSON을
   입력한다. `structuredComplaint`는 현재 null이며 추정한 코드나 위험 없음 값을 넣지 않았다.
   사실 요약, 장소/관계/패턴, 관찰 사실, 위험, 대상, 요청, 현재성, 누락 정보가 필요하다.
   각 관찰 사실·코드에는 해당 발췌에서 확인 가능한 evidence와 certainty를 기록한다.
   판단 필드나 최종 처리 경로는 추가하지 않는다. search_text는 기존 Java builder로 생성한다.
5. 별도의 검수된 검색 지식 corpus를 고정하고 `corpusSnapshotSha256`을 기록한다.
   질의를 그대로 VERIFIED_CASE로 넣고 자기 자신을 정답으로 지정하지 않는다.
   동일 사건의 답변·재게시가 corpus와 평가 질의에 겹치는지도 확인한다.
6. `reviews.relevance`: 고정된 corpus를 기준으로 3개 채널의 관련 item key, 근거 상태,
   위험 신호, 신뢰 사례 없음, 모호성 정답을 검수한다. 관련성 판단에는 설명을 남긴다.
   `null`은 미검수이고 `[]`는 검토 결과 관련 문서가 없다는 뜻이다. 둘을 바꾸어 쓰지 않는다.
   원문 분야가 경찰이 아니라는 이유만으로 no-match를 true로 정하지 않는다.
7. 그룹 분리와 정답 검수가 끝나면 분리안을 확정한다. dev에서 설정을 정하고 test 결과를
   보고 다시 조정하지 않는다. 검토 상태와 실제 검토자 정보 없이 humanReviewed를 올리지 않는다.

현재 파일은 **검수 대기열**이며 Java `EvaluationDataset` 또는 API 입력 파일이 아니다.
미완성 정답을 실험기로 전달하지 않도록 스키마를 구분했다. 검수된 corpus와 정답이 준비되면
기존 EvaluationDataset 형식으로 변환·검증하고 격리 DB에서 실제 임베딩 평가를 진행한다.

## 재현

```powershell
$env:PYTHONIOENCODING='utf-8'
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/validate_dataset.py
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -v
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/prepare_review.py --output .local-data/acrc-review-500-v2
```

출력 폴더가 이미 있으면 실패하여 검수자의 편집 내용을 덮어쓰지 않는다.
동일 입력에서는 동일한 후보 그룹과 분리 결과가 생성된다. 입력 해시와 합성 여부,
건수·키 중복을 확인하며 dev 100건을 그룹 단위로 만들 수 없으면 그룹을 쪼개지 않고 실패한다.

## 남은 조건

AGENTS.md의 “`maskingCompleted=true`가 아닌 검색 요청은 저장 및 임베딩 대상으로 받지 않는다”를
따라 미검수 500건을 임베딩하지 않았다. API 키나 DB 연결의 성공 여부는 이번 작업에서
확인하지 않았으며, 임베딩 사용 비용·Recall·MRR·응답 지연을 측정했다고 주장하지 않는다.
단어 기반 유사도는 검색 품질 지표가 아닌 중복 검토 보조 수단이다.

변경 파일: `tools/public_complaints/prepare_review.py`, `test_prepare_review.py`, 해당 폴더의
`README.md`, 이 문서, `docs/results/public-complaints-preparation-summary.json`.

추천 커밋: `feat(evaluation): prepare public complaint review queue and grouped split`
