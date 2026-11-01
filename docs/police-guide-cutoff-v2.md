# 경찰관용 공식 안내 채택 임계값 교정 실험

기준일: 2026-11-01. 고정한 55건과 BASELINE의 공식 안내 Top 3를 사용해 임계값을
교정했다. 사례·유형 기준 임계값과 운영 기본 설정은 변경하지 않았다.

## 결정

개발 기준을 만족한 실험 후보는 **0.60**이다. 하지만 운영 기본 0.70을 자동 대체하지 않는다.
평가용에서 채택한 문서는 3/3개가 AI 직접 관련성 라벨과 일치했으나, 관련 안내가 있는
18개 질의 중 3개만 지원했다. 관측한 표본 3개를 안정적인 정밀도 100%로 일반화할 수 없다.

실험용 프로필 `experimental-police-guide-v2-060`으로 실제 DB/API 55건을 재생해
GUIDE_ONLY 8건·INSUFFICIENT_EVIDENCE 47건을 확인했다. 이전 임시값 0.70은 55건을
모두 근거 부족으로 처리했다. 이 상태 이름은 실제 근거 충분성의 독립 정답이 아니다.

## 측정·선택 기준

개발 27건에서 0.20~0.90을 0.01 단위로 비교했다. 관련성은 주 모델 gpt-4o의 등급 2다.
배경 안내인 등급 1은 ‘직접 근거’ 기준 음성이며 다른 목적에서도 무관하다고 확정하지 않는다.

올바르게 채택한 문서 수로 precision과 recall을 계산하고 precision에 비중을 둔 F0.5를
사용했다. gold가 있는 질의에서 잘못된 문서를 통과시켜도 단순 채택 여부만 보면 성공처럼
보일 수 있으므로, 실제 관련 문서를 채택했는지 따로 계산한다. recall의 분모에는 Top 3에서
이미 누락된 관련 문서도 포함한다.

최종 선택 규칙은 먼저 **개발 채택 문서 precision ≥80%, 올바른 근거를 채택한 질의 ≥5건**을
충족하는 임계값을 걸러내고, 남은 후보 중 F0.5 최대를 선택하는 것이다. 동률이면 높은
임계값을 고른다. 통과 후보가 없으면 무제약 최적값을 진단용으로만 남긴다.
80%와 5건은 이번 실험의 정책이며 인증된 안전 기준이나 사용자 요구 수치를 뜻하지 않는다.

처음의 v1 진단은 F0.5 최대인 0.57을 먼저 고른 후 채택 기준을 검사해 탈락시켰다.
그러나 개발 기준을 만족하는 다른 후보가 있었으므로 v2는 기준을 먼저 적용하도록
선택 절차를 수정했다. v1 결과도 보존했다. v1의 평가 결과를 이미 본 이후이므로 v2를
새로운 미접촉 holdout 평가라고 부르지 않는다. 최종 후보 선택 계산 자체는 개발 자료만 사용했고,
평가 결과를 보고 다른 임계값으로 다시 선택하지 않았다.

## 개발 결과

| 임계값 | 채택 문서 | 올바른 문서 | 잘못된 문서 | 문서 precision | 올바른 근거가 있는 질의 |
|---|---:|---:|---:|---:|---:|
| 0.57 진단 | 13 | 8 | 5 | 61.54% | 7/20 |
| 0.60 후보 | 6 | 5 | 1 | 83.33% | 5/20 |

0.60만 개발 채택 기준 두 개를 모두 충족했다. 개발 27건 중 관련 문서가 있는 질의는
20건이며, gold 문서는 38개다. 0.60의 문서 recall은 5/38=13.16%다.

## 고정 평가 28건 결과

| 임계값 | 채택 문서 | 올바른/잘못된 문서 | 관측 precision | 관련 근거로 지원한 질의 |
|---|---:|---:|---:|---:|
| 0.70 기존 임시값 | 0 | 0 / 0 | 정의 불가 | 0/18 |
| 0.57 진단 | 9 | 6 / 3 | 66.67% | 5/18 |
| 0.60 후보 | 3 | 3 / 0 | 3/3 | 3/18 |

0.60은 관련 안내가 없는 10건을 모두 거부했지만 관련 안내가 있는 15건도 지원하지 못했다.
문서 recall은 3/25=12%다. 단일 cosine cutoff를 낮추는 것만으로 높은 채택 정밀도와
충분한 지원 범위를 동시에 얻었다고 볼 수 없다. 0.57은 더 많이 지원하지만 잘못된
문서를 3개 채택한다. 또한 이 결과는 AI 라벨 기준이며 실제 법적 판단이나 처리 경로 정답이 아니다.

## DB/API 검증

후보 0.60과 프로필 버전을 일회용 Testcontainers 실행에만 주입했다. 55개 HTTP 요청이
모두 성공했고, 네 전략의 DB·메모리 순위와 위험 플래그 보존 검증도 통과했다. 검색 전략의
순위 일치 검사는 가중치 0.1, HTTP의 전략은 BASELINE이다. 새로 임베딩하거나 운영 DB의
임계값·지식을 변경하지 않았다. 외부 AI/API를 호출하지 않는 로컬 교정 실험이다.

결과: [v1 진단](results/police-guide-cutoff-diagnostic-v1.json),
[v2 후보](results/police-guide-cutoff-v2.json), [0.60 DB/API 재생](results/police-guide-cutoff-http-v2.json).

## 변경 파일·재현

- `tools/public_complaints/calibrate_police_guide_threshold.py`: 올바르게 채택한 문서 기반 지표,
  개발 기준 우선 선택, 입력 고정 검사와 재현 가능한 프로토콜.
- `tools/public_complaints/test_calibrate_police_guide_threshold.py`: 잘못된 문서를 성공으로
  오인하지 않기, 최소 지원 건수, precision gate를 목적함수보다 먼저 적용하기 검증.
- `PublicPoolDatabaseExperiment.java`: 실험용 threshold profile version 설정. 기본값은 보존.
- 위 집계 JSON 3개, 이 문서와 README·최종 평가 보고서 연결.

```powershell
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/calibrate_police_guide_threshold.py --output .local-data/police-guide-cutoff-v2
$env:JAVA_HOME='C:\Users\SSAFY\.jdks\jdk-26.0.2.1'
.\gradlew.bat publicPoolDatabaseExperiment '-Dpool.expectedCount=55' '-Dpool.rerankWeight=0.1' '-Dpool.guideThreshold=0.60' '-Dpool.thresholdProfileVersion=experimental-police-guide-v2-060' '-Dpool.queries=.local-data/police-structure-java-v1/validated-requests.jsonl' '-Dpool.corpus=tools/public_complaints/officer_documents_v1.json' '-Dpool.cache=.local-data/public-pool-experiment-v1/cache' '-Dpool.output=.local-data/police-guide-cutoff-http-v2'
```

운영 채택 결정은 **보류**다. 이번 임계값 교정 실험은 완료했고, 성능 한계를 함께 기록했다.
향후 더 높은 지원 범위가 필요하면 단순 cosine cutoff 외의 문서 적용 범위 평가를 개발
자료에서 검증해야 한다. 테스트 자료를 반복 선택에 사용해 성능을 끌어올렸다고 주장하지 않는다.

추천 커밋: `feat(evaluation): calibrate police guide acceptance with precision gates`

검증: Python 테스트 103개, Java 전체 627개 통과. 후보 임계값을 주입한 55건의
DB/API 재생도 별도 통과했다. 오프라인 재집계 결과가 동일했고 기존 두 벤치마크
해시 검사 및 `git diff --check`가 통과했다.
