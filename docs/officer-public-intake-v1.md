# 경찰 업무별 공개 질의 수집 초안

기준일: 2026-10-30. 실제 공개 질의의 본문을 확인하고 경찰관 업무 지원 평가에 사용할
수집 초안 4건을 만들었다. 이번에 새로 확인한 출처는 3개이며 중고거래 1개는 이전 수집
후보를 옮긴 것이다. 기존 500건에 합산하거나 새 벤치마크로 승인하지 않았다.

| 업무 | 출처 | 수집한 사실 범위 |
|---|---|---|
| 유실물 | [휴대전화 분실 질의](https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=384644&mode=2&ofiClsCd=350128) | 분실 사실, 방문 곤란, 비방문 신고 문의 |
| 유실물 | [공항 유실물 질의](https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=384654&mode=2&ofiClsCd=350128) | 공항 면세구역의 지갑 분실, 해외 체류, 귀국 후 찾기 문의 |
| 순찰 요청 | [통학로 순찰 요청](https://www.guri.go.kr/mayor/selectBbsNttView.do?bbsNo=482&key=1165&nttNo=144606) | 전해 들은 유괴 시도 소식에 따른 불안, 통학 시간 순찰 요청 |
| 온라인 거래 | [중고거래 공개 질의](https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=385538&mode=2&ofiClsCd=350128) | 대금 송금 후 물품 미수령, 피해 사실을 알리는 방법 문의 |

국가법령정보센터의 세 자료는 국민신문고에서 수집한 경찰청 질의·회답으로 표시돼 있다.
회답을 그대로 정답으로 가져오지 않고 질의 부분만 재서술했다. 날짜가 있는 부처 회답을
현재의 확정된 법적 판단으로 사용하지 않는다. 순찰 자료는 지자체에 게시된 공개 민원이며,
경찰 직접 접수 원본이나 현장 신고 기록으로 표시하지 않는다.

## 비식별 및 출처 기록

원문 전문·전체 HTML·회답 전문은 저장하지 않았다. 기존 수집 코드의 식별정보 검사와
보조자의 의미 검토를 적용한 재서술 초안만 Git 제외 로컬 폴더에 저장했다.
지역·작성자·담당자 이름·연락처는 제외하고 해외 체류 국가는 일반화했다.
순찰 사례의 유괴 시도를 직접 목격하거나 확인된 사건으로 바꾸지 않았다.
정규식 검사가 모든 개인정보를 찾아낸다고 보장하지 않으며 사람 검수 완료로 표시하지 않는다.

원문 기반 재서술이므로 새로운 사실을 만든 합성 사례와 구분하지만 원 민원을 그대로
보존한 평가 자료도 아니다. sourceKind, sourceSection, contentKind, checkedDate,
redactionNotes, 개인정보 검토 주체, maskedTextSha256을 기록했다.

동일 게시물의 URL에 페이지 번호 등이 달라도 같은 source/group ID가 되도록 처리했다.
같은 출처의 원 질의·회답·후속 요약은 서로 다른 dev/test에 넣지 않는 것이 원칙이다.
이번에는 split을 UNASSIGNED로 두었다. 기존 500건과의 동일 URL 및 문자 유사도 검사에서
중복 경고는 0건이지만, 의미 중복이나 다른 사이트 재게시까지 검증한 것은 아니다.

## 이용 조건과 편입 보류

4건 모두 DRAFT, benchmarkEligible=false, expectedLabels=null 상태다.
구리시 [저작권 정책](https://www.guri.go.kr/www/contents.do?key=788)은 공공누리 표시가 없는
자료의 이용 전 협의를 안내한다. 해당 민원 게시물의 개별 이용허락은 확인하지 못했으므로
공개 출처 목록에는 HOLD_NO_ITEM_LICENSE_CONFIRMED로 기록했다. 이 초안은 임베딩,
평가 호출, 운영 적재 및 공개 재배포에 사용하지 않았다. 다른 세 출처도 개별 이용 범위를
확정하지 않았으므로 사용 검토를 완료한 자료로 표시하지 않는다.

도봉구의 다른 순찰 요청은 검색 결과에 나타났지만, 직접 접근한 HTTP 200 본문에
기대했던 민원 내용이 없어 수집 건수에서 제외했다. HTTP 성공만으로 본문 확인을 주장하지 않는다.

## 산출물과 검증

- `tools/public_complaints/officer_public_sources_v1.json`: 본문 텍스트 없는 출처 목록과 제외 사유.
- `tools/public_complaints/prepare_officer_intake.py`: 출처 ID·중복·개인정보 흔적·초안 상태 검사.
- `tools/public_complaints/test_prepare_officer_intake.py`: URL 변형 중복, FAQ/검색 스니펫 배제, 연락처 거절, 미승인 상태 검증.
- `docs/results/officer-public-intake-v1.json`: 4건, 출처 그룹 4개, 유실물 2/순찰 1/온라인 거래 1의 집계.
- `.local-data/officer-public-intake-v1/seeds.json`, `drafts.jsonl`: 로컬 비식별 재서술 초안. Git 제외.
- README와 이전 후속 보고서 연결, 이 보고서.

Python 테스트 85개와 기존 벤치마크 해시 검사가 통과했다. Java 운영 코드 변경이 없어
Java 테스트는 재실행하지 않았다. 기존 검색 설정·500건 라벨·DB는 변경하지 않았다.

```powershell
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/prepare_officer_intake.py --input .local-data/officer-public-intake-v1/seeds.json --output .local-data/officer-public-intake-v1
.\.local-data\collector-venv\Scripts\python.exe -m unittest discover -s tools/public_complaints -q
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/benchmark_lock.py --lock docs/results/benchmark-public-500-pool20-ai-v3.json --verify
```

다음 작업은 재사용 조건이 명확한 공개 자료를 우선 확보해 업무별 사례 수를 늘리고,
사용 가능한 자료에 한해 사실 구조화와 경찰관 관점의 AI 관련성 라벨을 생성하는 것이다.
아직 사례 수가 적고 일부 자료가 보류 상태이므로 경찰 업무 전반의 검색 성능이 검증됐다고
주장하지 않는다. FAQ나 안내문을 실제 민원 건수에 넣어 부족분을 채우지 않는다.

추천 커밋: `feat(evaluation): prepare provenance-checked police query intake drafts`
