# 경찰관 평가 응답 보완(v2~v4)과 자료 수집 후보

기준일: 2026-10-30. [이전 실험](officer-support-evaluation-v1.md)의 미완료 응답 3건을
보완하기 위해 문서를 5개씩 판단하는 별도 v2 실험을 실행한다.

이후 자료 확보 작업은 [공개 질의 수집 초안](officer-public-intake-v1.md)에 기록한다.

## 변경 방식

실제 경찰민원 개발 17건, 후보 20개, 두 모델, 합성 대조 12건을 그대로 사용한다.
실제 사례는 한 번에 5개 문서만 제시하므로 질의·모델당 4개 묶음, 합성 사례는 기존처럼
1개 문서다. 논리적 평가 단위는 58개이며 정상 경로에서 묶음 요청은 160개다.
형식 오류 재시도와 전송 재시도는 이 수에 포함하지 않는다.

판단 프롬프트와 등급 검증은 유지하지만 모델이 보는 문서 문맥이 달라진다. 따라서 기존
55개 응답에 실패 3개만 덧붙이지 않고 새 버전에서 전량 평가한다. 묶음 하나라도 실패하면
해당 질의·모델 결과를 완료로 처리하지 않는다. 완성된 다른 결과는 체크포인트로 재사용한다.
v1 결과는 보존하며 새 실행이 이전의 미완료 기록을 지우지 않는다.

5개 묶음만 적용한 v2는 58개 중 56개 완료했다. 두 응답에서 존재하지 않는 출처 ID가
반환돼 실패했다. [v2 집계](results/officer-rubric-dev-v2.json)는 미완료로 보존한다.

이를 보완하는 v3에는 strict JSON Schema를 적용했다. 문서 ID와 출처 ID를 현재 요청에
있는 값의 enum으로 제한하고 grade·confidence·필수 필드도 명시한다. GMS에 시험 호출해
수용 여부를 확인했다. 중복·누락 문서와 등급 2의 빈 출처는 기존 로컬 검증으로 다시
거절한다. 형식 제한은 출처 내용의 의미적 정확성이나 법적 판단을 보장하지 않는다.

출력 제약도 평가 조건이므로 v3는 v2의 결과를 이어붙이지 않고 같은 58개 단위를 다시
평가한다. 프로토콜에는 버전·묶음 크기·출력 스키마 해시가 추가되며 기존 디렉터리를
새 설정으로 덮어쓰려 하면 중단한다.

v3는 출처 enum을 적용했지만 배열 안의 문서 누락까지 막지는 못했다. 57/58개가
완료됐고 mini의 한 질의에서 라벨 수 검증이 실패했다. v4는 `labels`를 배열 대신
문서 ID별 필수 속성을 갖는 객체로 제한한다. 각 속성 안의 itemKey도 해당 문서 ID로
고정한다. 응답을 받은 뒤 기존 라벨 형식으로 변환하고 동일한 로컬 검증을 실행한다.
v4 역시 별도 전체 실행이며 55→56→57의 완료 수 변화는 검색 품질 향상 지표가 아니다.

v4 첫 실행은 18개 결과 저장 후 40개 요청 오류로 중단됐다. 당시 오류는 RuntimeError로만
저장되어 HTTP 원인은 확정하지 않는다. 두 모델의 후속 확인 호출이 정상 처리되어
동일 프로토콜의 체크포인트에서 남은 결과만 재개했다. 이후 오류 기록에는 허용된 형식의
HTTP 상태 코드만 추가하며 응답 본문·인증값은 기록하지 않는다.

## 최종 v4 결과

58/58개 질의·모델 평가 단위를 완료했다. 실제 경찰민원 17건 × 20개 후보 × 2개 모델의
680개 라벨과 합성 대조 12건 × 2개 모델의 24개 라벨, 총 704개 라벨을 수집했다.
최종 실패 0개, 누락 문서 쌍 0개다. v1에서 실패했던 3건도 v4에는 두 모델 응답이 모두 있다.
원래 v1 미완료 기록 자체를 수정한 것은 아니다.

| 실제 개발 데이터 채널 | 문서 쌍 | 직접 근거 이진 일치율 | 양성 일치율 | 등급 전체 일치율 |
|---|---:|---:|---:|---:|
| 유형 기준 | 68 | 100% | 정의 불가(두 모델 양성 0) | 97.06% |
| 공식 안내 | 272 | 92.65% | 66.67% | 74.63% |

공식 안내에서 mini는 36개, 4o는 24개를 직접 근거로 판단했다. 형식 검증은 완료했지만
판단 차이가 남아 있으며 이 라벨을 확정 정답으로 승격하지 않는다. 유형 기준의 100%는
직접 근거 양성이 없어서 나타나는 값이다. VERIFIED_CASE 후보는 없어 해당 채널은 미측정이다.

합성 대조의 작성자 기대 등급과 일치한 수는 mini 9/12, 4o 12/12였다. 합성 대조에서의
일치는 실제 민원 정확도가 아니다. 기존 v1과는 문서 문맥·출력 제약·비교 가능한 사례 수가
달라졌으므로 일치율 차이를 검색 품질 개선 또는 악화로 해석하지 않는다.

이번 단계에서 해결한 것은 **평가 응답의 누락·잘못된 출처 형식을 검사하고 전량 수집하는 문제**다.
자료의 업무 대표성과 AI 판단의 정확성은 남아 있다. 다음 우선 작업은 출처 후보의 중복·
이용 조건을 확인하고 유실물·순찰 등 실제 공개 사례를 추가 확보하는 것이다.

집계: [v2](results/officer-rubric-dev-v2.json), [v3](results/officer-rubric-dev-v3.json),
[v4 완료 결과](results/officer-rubric-dev-v4.json).

## 자료 조사 결과와 남은 수집 작업

수집 후보는 [출처 목록](../tools/public_complaints/officer_source_candidates_v1.json)에 기록했다.
이번에는 검색 결과를 실제 민원 데이터로 일괄 적재하지 않았다.

| 출처 | 확인한 자료 종류 | 평가 자료로 쓰기 전 남은 일 |
|---|---|---|
| [국가법령정보센터 경찰청 질의](https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=385538&mode=2&ofiClsCd=350128) | 국민신문고에서 수집한 중고거래 관련 공개 질의·회답 | 중복·이용 조건·분할 점검, 현재 안내와 구분 |
| [경찰민원24 순찰신문고](https://minwon24.police.go.kr/cvlcpt/cvlcptGdInfo.do?cvlcptId=MW-914) | 공식 절차 안내 | 기존 후보 중복 확인, 실제 요청 사례 별도 확보 |
| [ECRM](https://ecrm.police.go.kr/minwon/main) | 공식 안내와 작성 예시 | 안내 조건 보존, 작성 예시를 실제 사례로 세지 않기 |
| [LOST112](https://lost112.go.kr/index.do) | 검색 결과에 나타난 안내·FAQ 후보 | 본문 열기 시간 초과, 개별 본문 재확인 |

첫 자료는 2025-04-15 경찰청 회답이며 페이지에 2025-09-18 국민신문고 수집으로 표시된다.
개인 식별 정보가 없는 질의 요지를 별도 초안으로 바꾸어 출처 목록에 담았으며,
회답 전체나 원 민원 전문은 저장하지 않았다. 공개 질의라는 출처와 별개로 VERIFIED_CASE나
최종 평가 정답으로 승인하지 않았다. 회답을 근거와 정답 양쪽에 그대로 사용하면 평가 누출이
생기므로 동일 출처에서 파생한 질의·회답·요약은 같은 그룹으로 관리해야 한다.

유실물과 순찰 분야의 실제 사례 부족은 아직 해결되지 않았다. 안내 문서·FAQ·작성 예시를
실제 민원 수에 더하지 않으며 기존 500건의 수와 라벨도 유지한다.

## 재현 명령

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/officer_rubric_pilot.py --batch-size 5 --output .local-data/officer-rubric-dev-v2
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/officer_rubric_pilot.py --batch-size 5 --output .local-data/officer-rubric-dev-v2 --summarize-only
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/officer_rubric_pilot.py --batch-size 5 --strict-output --output .local-data/officer-rubric-dev-v4
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -q
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/benchmark_lock.py --lock docs/results/benchmark-public-500-pool20-ai-v3.json --verify
```

## 변경 파일

- `tools/public_complaints/officer_rubric_pilot.py`: 묶음 평가, 출처 ID 제한 스키마와 별도 프로토콜 버전.
- `tools/public_complaints/test_officer_rubric_pilot.py`: 문서 누락 없는 묶음 병합, 부분 실패 및 스키마 제약 검증.
- `tools/public_complaints/officer_source_candidates_v1.json`: 출처·자료 종류·본문 확인 상태·초안.
- 이 문서, 이전 보고서와 README 연결, `docs/results/officer-rubric-dev-v2.json`,
  `officer-rubric-dev-v3.json`, `officer-rubric-dev-v4.json` 집계.

운영 검색·임계값·DB 데이터와 Java 코드는 변경하지 않는다. AI 일치율은 정확도나
Recall 향상이 아니며 이번 단계에서 최종 분류·법적 판단 기능을 추가하지 않는다.

추천 커밋: `fix(evaluation): batch officer relevance judgments and track source candidates`

검증: Python 테스트 82개 통과, 기존 벤치마크 해시 검사 및 `git diff --check` 통과.
API 키 없이 체크포인트 집계를 재실행하여 결과 바이트 동일성을 확인했다.
총 704개 라벨과 기존 실패 3건의 v4 결과 존재를 별도로 확인했다.
Java 코드 변경이 없어 Java 테스트는 이번 단계에서 재실행하지 않았다.
