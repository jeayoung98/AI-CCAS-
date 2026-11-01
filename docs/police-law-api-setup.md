# 경찰청 공개 질의 API 신청과 수집 준비

확인일: 2026-10-30. 현재 전체 작업의 1번(평가 누락 보완)은 완료했고,
2번(업무별 실제 사례 확보)은 진행 중이다. 수집 초안 4건만 있으며 새로운 대량 수집은
실행하지 않았다. 사용자는 아직 공동활용 승인 계정이 없다고 확인했다.

## 사용자가 진행할 신청

1. [국가법령정보 공동활용](https://open.law.go.kr/LSO/main.do)에 접속해 회원가입·로그인한다.
2. 메뉴의 [OPEN API 신청](https://open.law.go.kr/LSO/openApi/cuAskList.do)으로 이동한다.
3. 필요한 데이터로 **중앙부처 1차 해석 → 경찰청 법령해석 목록·본문**을 선택한다.
   화면의 항목 구성이 다르면 경찰청 목록과 본문 두 기능을 사용할 수 있는 신청 대상을 확인한다.
4. 활용 목적과 실제 신청자·서비스 정보를 입력해 신청한다. 아직 배포하지 않았다면
   실제 상태대로 개발·연구용이라고 기재하고 존재하지 않는 서비스 URL을 만들지 않는다.
5. 승인 결과를 확인한 뒤 신청한 API의 인증값(OC)을 로컬 환경에 설정한다.
   인증값을 채팅이나 Git에 올리지 않는다. 기존 GMS 키는 이 API에 사용할 수 없다.

[공식 이용안내](https://open.law.go.kr/LSO/information/guide.do)는 활용 신청과 승인 절차를
명시하고 승인 처리 기간을 1~2일 이내로 안내한다. 이는 제공기관 안내이며 실제 소요 시간을
보장하는 것은 아니다. 개인정보 입력과 신청 제출은 사용자가 직접 진행한다.

### 활용 목적 입력 초안

> 경찰관의 민원 처리·안내 업무를 지원하는 근거 검색 모듈의 연구·개발에 활용합니다.
> 경찰청 공개 질의 자료를 업무 유형별로 수집하고 개인정보 점검 및 비식별 처리 후
> 벡터 검색 평가용 초안을 구성하려 합니다. 원문과 회답 전체를 공개 재배포하지 않으며
> 출처와 자료 시점을 기록합니다. 초기에는 최대 100건의 수집 후보를 확인하고,
> 실제 사용 범위와 제공 조건에 맞춰 확장할 예정입니다. 최종 법적 판단이나 민원 접수
> 기능을 제공하는 서비스는 아닙니다. 승인 범위가 허용하는 비식별 자료에 한해 외부
> AI API를 이용한 구조화·검색 관련성 평가를 검토합니다.

해당 초안은 신청 설명용이다. 승인 범위에 외부 AI 전송이나 평가 데이터 재사용이 포함되는지
확인되지 않은 상태에서 이를 허용된 것으로 간주하지 않는다.

## 준비한 수집 도구

`tools/public_complaints/collect_police_law_api.py`를 추가했다. 기본 실행은 네트워크 호출
없는 계획 출력이다. 실행 옵션과 승인 확인, 환경변수 `LAW_API_OC`가 모두 있어야 실제
요청을 시작한다. 공식 API target은 `npaCgmExpc`이며, 목록에는 본문 검색과 업무별
키워드를 사용한다. 키워드는 후보 발견용이지 확정 업무 라벨이 아니다.

- 목록: [경찰청 목록 조회 명세](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=cgmExpcNpaListGuide)
- 본문: [경찰청 본문 조회 명세](https://open.law.go.kr/LSO/openApi/guideResult.do?htmlName=cgmExpcNpaInfoGuide)
- 승인·이용조건: [공식 안내](https://open.law.go.kr/LSO/information/guide.do)

OC는 환경변수에서만 읽고 요청 URL이나 응답 본문을 오류 메시지에 기록하지 않는다.
본문에서 질의요지만 추출해 기존 정규식으로 식별정보를 치환하며, 이름 의심 패턴이나
과도하게 긴 질의는 제외한다. 회답·담당자·원문 HTML은 저장하지 않는다.
추출 결과는 AUTOMATED_SCREEN_ONLY, maskingCompleted=false, DRAFT다.
따라서 구조화·임베딩·운영 적재로 바로 연결하지 않는다. 개인정보를 모두 제거했다는
보장이 아니며, 후속 AI 점검과 중복 확인이 필요하다.

```powershell
# 승인 전에도 실행 가능: 계획만 표시, API 호출 없음
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/collect_police_law_api.py --limit 100

# 실제 승인 및 LAW_API_OC 로컬 설정 이후에만 실행
.\.local-data\collector-venv\Scripts\python.exe tools/public_complaints/collect_police_law_api.py --execute --approved --limit 100 --output .local-data/police-law-api-v1
```

GMS 설정 스크립트와 분리되어 있다. 실제 인증값을 받은 후 로컬 설정을 이어서 할 수 있다.
이번에는 로그인·활용 신청 제출·인증 API 요청을 실행하지 않았다.

## 신청 후 로컬 설정

2026-10-30 사용자가 신청 제출 후 승인 완료를 확인했고 로컬 암호화 파일에서 OC를
불러오는 데 성공했다. 최초 최대 5건 수집 시도에서 서버는 HTTP 200의 HTML 안내로
“미신청된 목록/본문에 대한 접근”을 반환했다. 질의 자료는 수집되지 않았다.
현재 인증값과 신청 대상의 연결을 확인해야 하며, 승인 사실만으로 해당 API의 사용 가능
여부를 확정하지 않는다. 도구는 이 응답을 XML 오류 대신 대상 API 접근 거부로 보고한다.

신청 내역에서 등록된 API를 열어 **경찰청 법령해석 목록과 본문** 두 항목이 포함됐는지
확인하고, 저장한 OC가 그 신청에 해당하는 값인지 확인한다. 실제 메시지에는 등록된 API의
법령종류를 체크하라는 안내가 있었다. 현재 시도는 데이터 파일이 비어 있는 로컬 smoke
디렉터리만 남겼으므로 재시도할 때는 새 출력 디렉터리를 사용한다.

오류 처리 변경 검증: Python 89개 테스트는 재실행에서 통과했다. 첫 실행은 기존
로컬 검수 서버 HTTP 테스트에서 WinError 10053 연결 중단으로 1건 실패했다.
`git diff --check` 통과. 변경 파일은 API 수집 도구·관련 테스트·이 문서다.
추천 커밋: `fix(evaluation): report LAW API target authorization errors clearly`
신청 내역이 승인 완료로 표시되고 해당 API 인증값을 확인한 뒤 PowerShell에서 실행한다.

```powershell
Set-Location C:\Users\SSAFY\Desktop\RAG
. .\tools\Use-LawApi.ps1 -SaveCredential
```

숨겨진 입력창에 OC를 입력하면 현재 Windows 사용자용 DPAPI로 암호화하여
`.local-data/law-api/oc.dpapi`에 저장한다. 값은 출력하지 않으며 이 파일은 Git 제외 대상이다.
다른 PowerShell 세션에서는 `. .\tools\Use-LawApi.ps1`로 다시 불러온다.
앞의 점과 공백은 PowerShell 문법이다. 이 설정은 제공기관 승인을 대신하지 않는다.
환경변수는 해당 PowerShell과 그 자식 프로세스에 적용되며 다른 실행 세션에는 자동 전파되지 않는다.

변경 파일: `tools/Use-LawApi.ps1`, 이 문서. 스크립트 구문과 인증값이 없을 때의
중단 동작을 확인하며, 실제 인증값 저장·인증 API 호출 검증은 값을 설정한 뒤 진행한다.
추천 커밋: `chore(evaluation): add local LAW API credential loader`

## 검증·변경 범위

후속 확인에서 경찰청 목록·본문 JSON 요청이 모두 정상 수신됐다. 같은 시점의 XML
수집 시도는 미신청 안내였으므로 실제 수집 도구는 JSON 형식으로 변경했다.
최대 5건 smoke 수집은 5건 저장으로 완료했다. 이전 접근 오류 기록은 당시 관측이다.
본 수집과 다음 검증은 [JSON 수집 보고서](police-law-json-intake-v2.md)를 따른다.

연결 추가 점검: 동일 OC로 일반 법령 JSON 검색은 `LawSearch.totalCnt > 0`과 결과 항목이
확인됐다. 경찰청 목록과 본문은 JSON 요청에서도 모두 미신청 안내를 반환했다.
따라서 현재는 인증값 전체의 실패보다 경찰청 대상의 신청 연결을 우선 확인해야 한다.
단, 정확한 신청서 상태는 화면 접근 도구 오류로 확인하지 못했다.

변경 파일: 수집 도구, `test_collect_police_law_api.py`, 이 문서와 README 연결.
테스트는 API 오류를 빈 목록으로 오인하지 않는지, 출처 ID가 맞는지, 회답과 연락처를
저장하지 않는지, 승인 없이 실행되지 않는지, 오류에서 인증값이 숨겨지는지 검사한다.
모의 XML 기준 테스트이며 승인 계정으로 실제 응답 형식을 확인하는 작업은 남아 있다.
신규 자료 건수 증가·운영 DB 적재·검색 품질 개선을 주장하지 않는다.

검증 결과: Python 테스트 88개 통과, API 호출 없는 계획 출력 확인, 고정 벤치마크
해시 검사 및 `git diff --check` 통과. Java 코드 변경이 없어 Java 테스트는 재실행하지 않았다.

추천 커밋: `feat(evaluation): prepare approved police law API intake`
