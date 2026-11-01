# 경찰관 업무 기준 관련성 라벨

기준일: 2026-10-31. 구조화·Java 검증을 통과한 고정 질의 55건에 기존 경찰관용
평가 기준을 적용한다. 후보는 기존 officer_documents_v1.json의 유형 기준 4개와
공식 안내 16개다. VERIFIED_CASE 후보는 없으며 운영 지식으로 승인된 자료는 아니다.

## 실행 전에 정한 기준

- 55건 × 후보 20개 × 두 모델 = 2,200개 등급이다.
- 기존처럼 문서를 5개씩 보여주고 문서 ID별 필수 항목과 출처 ID enum을 강제한다.
  질의·모델 단위는 110개이고 정상 경로의 묶음 요청은 440개다. 재시도는 별도다.
- 주 평가 모델은 **gpt-4o**로 고정했다. gpt-4o-mini는 교차 점검에 사용하며,
  모델이 다르게 판단한 항목을 다수결이나 높은 점수로 자동 교체하지 않는다.
- 두 모델에는 사전 코드·구조화 요약·검색 순위·이전 라벨·dev/test 정보를 주지 않는다.
  비식별 질의와 해당 묶음의 후보 본문·범위만 제시한다.
- 등급 2는 경찰관의 구체적인 처리·안내 업무에 유용한 직접 근거, 1은 관련 배경,
  0은 무관하거나 다른 절차, UNASSESSABLE은 정보 부족이다. 민원인의 명시적 절차 질문은
  직접 근거 판단의 필수 조건이 아니다.
- 검색 지표용 이진 라벨은 등급 2만 양성으로 둔다. 1은 ‘직접 근거 기준’에서만 음성이며
  원래 등급을 보존한다. 이를 모든 목적에서 무관한 자료로 해석하지 않는다.
- 주 모델의 UNASSESSABLE은 음성 라벨로 바꾸지 않는다. 해당 문서 라벨을 미판정으로 두고
  그 질의는 전체 문서 풀 평가용 resolved-queries에서 제외한다.

입력 질의, 구조화 검증 보고서, 후보 문서, 프롬프트·출력 스키마, 모델, 묶음 크기,
주 모델과 이진 투영 규칙을 체크포인트에 결합했다. 모든 모델 응답이 있어야 qrels를
내보내며 저장된 출력과 다른 라벨로 덮어쓰려 하면 중단한다.

## 완료 결과

110개 질의·모델 단위를 모두 완료해 2,200개 등급을 저장했다. 주 모델의 1,100개
질의·문서 쌍은 모두 판정됐으며 UNASSESSABLE과 누락은 0개다. 55건 모두 기존의
개발 27건·평가 28건과 그룹 배치를 유지한다.

주 모델은 75개 쌍을 직접 근거 양성으로 판단했다. 이 중 유형 기준은 12개, 공식 안내는
63개다. 나머지 1,025개는 등급 0 또는 1이며 원 등급을 보존했다.
모델 간 등급 자체가 다른 쌍은 262개, 직접 근거 여부가 다른 쌍은 104개였다.
단순 이진 일치율은 90.55%지만 양성 일치율은 55.56%다. 무관·배경 라벨이 많아
높아지는 단순 일치율만 보고 좋은 정답이라고 주장하지 않는다.

| 채널·분할 | 주 모델의 양성 문서가 있는 질의 | 양성 문서 없는 질의 | 두 모델 양성 일치율 |
|---|---:|---:|---:|
| 유형 기준 / 개발 | 5/27 | 22/27 | 40.00% |
| 유형 기준 / 평가 | 7/28 | 21/28 | 63.64% |
| 공식 안내 / 개발 | 20/27 | 7/27 | 62.00% |
| 공식 안내 / 평가 | 18/28 | 10/28 | 50.57% |

평가 28건 중 공식 안내 양성이 있는 18건은 검색 순위 품질을, 나머지 10건은 현재
후보 풀에서 직접 근거를 찾지 못하는 상황을 살필 수 있다. 10건에 대한 실제 근거 충분성
정답을 자동 생성한 것은 아니다. VERIFIED_CASE는 후보가 없어 양성·음성 질의 수도
미측정(null)이며, 사례 없음 정답으로 간주하지 않는다.

집계와 고정 해시: [police-officer-relevance-v1.json](results/police-officer-relevance-v1.json).
자료는 FROZEN_AI_PROVISIONAL이며, 모델 간 차이 104개를 자동 합의로 바꾸지 않았다.
이 상태 그대로 주 모델 기준과 교차 점검 결과를 구분해서 후속 검색 비교에 사용할 수 있다.

## 후속 검색 평가용 파일과 한계

로컬 `.local-data/police-officer-relevance-v1/`에 모델별 원 판단을 보존한다.
qrels/qrels.jsonl에는 주 모델의 이진·등급 라벨과 AI 출처를, resolved-queries.jsonl에는
그룹·분할·채널별 양성/음성 키를 기록한다. pending.jsonl은 정보 부족, disagreements.jsonl은
두 모델의 다른 판단을 담는다. 개인정보 점검과 운영 지식 승격은 별개이며 후보의
DRAFT 상태나 usageReview를 변경하지 않는다.

모호성·사례 없음·위험 신호·최종 근거 상태의 정답은 이 관련성 라벨에서 만들지 않는다.
VERIFIED_CASE 채널의 성능도 현재 후보로 측정할 수 없다. 같은 제공자 모델의 공통 오류와
작은 후보 풀이라는 한계가 있으며 모델 일치율을 정확도라고 표현하지 않는다.

## 재현

```powershell
. .\tools\Use-Gms.ps1
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/label_police_relevance.py --output .local-data/police-officer-relevance-v1
# 완료된 판단으로만 라벨 재생성, API 호출 없음
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/label_police_relevance.py --output .local-data/police-officer-relevance-v1 --offline
```

## 변경 파일

- `tools/public_complaints/label_police_relevance.py`: 고정 질의·문서 평가, 모델 간 차이 집계,
  주 모델·등급 기준 고정, 기존 Java 검색 실험 도구가 읽을 수 있는 qrels 내보내기.
- `tools/public_complaints/test_label_police_relevance.py`: 판단 불가 보존, 배경/직접 근거 투영,
  주 모델 고정 및 미판정 질의 제외 검증.
- `docs/results/police-officer-relevance-v1.json`: 개별 질의 없는 집계·입출력 해시.
- 이 문서, README·이전 구조화 보고서 연결.

이번 작업은 라벨 생성 단계다. 검색 순위·Recall·Precision·MRR 비교는 이후 같은
질의·문서·라벨에서 수행한다. 기존 500건의 점수를 새 기준과 직접 비교하지 않는다.

추천 커밋: `feat(evaluation): freeze officer-focused AI relevance labels for police queries`

검증: Python 테스트 100개, 기존 벤치마크 해시 검사, `git diff --check` 통과.
API 키 없이 별도 프로세스에서 재생성해 요약과 qrels가 동일함을 확인했다.
Java 코드 변경이 없어 Java 테스트는 이번 단계에서 재실행하지 않았다.
