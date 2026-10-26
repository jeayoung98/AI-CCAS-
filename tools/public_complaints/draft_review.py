"""Local extractive review drafts. No API calls, embeddings, or approval promotion.

Rules suggest candidates with exact evidence. They cannot establish intent,
negation, current danger, privacy clearance, or retrieval ground truth.
"""
import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

from collect_acrc import EMAIL, PHONE, RESIDENT_ID, NAME_RISK
from prepare_review import fingerprint

PARTIAL_SPACED_NAME = re.compile(r'(?<![가-힣])[김이박최정강조윤장임한오서신권황안송전홍유고문양손배백허남심노하곽성차주우구민진지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예경봉사부]\s+(?:[○◯〇ＯO*＊]\s*){2,3}')
BIRTH_YEAR = re.compile(r'(?<!\d)(?:19|20)\d{2}\s*(?:년\s*)?(?:생|년생)')
SPACED_AGE = re.compile(r'(?<!\d)\d{1,3}\s+세(?=[\s,.)]|$)')
MASKED_STREET = re.compile(r'(?:[○◯〇]\s*){1,8}(?:길|로)\s*\d+(?:-\d+)?')

RISK_RULES = {
    'WEAPON': r'흉기|총기|(?:^|\s)칼(?:을|로|이|에|\s)|무기를',
    'DEATH_THREAT': r'죽이겠|죽여버|살해\s*협박',
    'ABDUCTION': r'납치|감금', 'MISSING_PERSON': r'실종|행방불명',
    'CHILD_ABUSE': r'아동\s*학대', 'STALKING_PATTERN': r'스토킹|반복.{0,12}미행',
    'PERSONAL_DATA_EXPOSURE': r'개인정보.{0,15}(?:노출|유포|유출)|주민등록번호.{0,15}(?:노출|유포|유출)',
    # Historical assault is not evidence that an assault is currently happening.
    'ASSAULT_IN_PROGRESS': r'(?:현재|지금).{0,12}폭행.{0,8}(?:중|있)',
}
SUBJECT_RULES = {
    'LOST_ITEM': r'유실물|분실물|습득물',
    'TRAFFIC_ADMIN': r'운전면허|범칙금|교통.{0,8}(?:단속|과태료)',
    'CYBER_TRANSACTION': r'(?:온라인|인터넷|중고).{0,15}(?:거래|사기)',
    'PATROL_REQUEST': r'순찰.{0,10}(?:요청|해\s*달라)',
    'CASE_STATUS': r'(?:사건|처리).{0,8}(?:진행\s*상황|처리\s*현황)',
    'POLICE_SERVICE_COMPLAINT': r'경찰.{0,25}(?:부당|불친절|미흡|업무처리)|(?:부당|불친절).{0,15}경찰',
    'SAFETY_THREAT': r'협박|위협|폭행|감금|실종',
}
ACTION_RULES = {
    # Do not confuse "신고하였다" in historical narrative with the current request.
    'REPORT': r'신고(?:하고\s*싶|하려|할\s*방법|\s*방법)',
    'CONSULT': r'상담(?:을\s*)?(?:요청|받고\s*싶)|상담해\s*달라',
    'SEARCH': r'조회(?:하고\s*싶|\s*방법)|찾아\s*달라',
    'APPLY': r'신청(?:하고\s*싶|\s*방법)|발급해\s*달라',
    'CHECK_STATUS': r'(?:처리|진행)\s*(?:상황|현황).{0,12}(?:알려|확인|문의)',
    'PROVIDE_INFORMATION': r'제보(?:하고\s*싶|하려|\s*방법)',
}


def extra_redact(text):
    changes = []
    for label, pattern, replacement in (
        ('BIRTH_YEAR', BIRTH_YEAR, '[출생정보]'),
        ('SPACED_AGE', SPACED_AGE, '[나이]'),
        ('MASKED_STREET_NUMBER', MASKED_STREET, '[상세주소]'),
        ('SPACED_PARTIAL_NAME', PARTIAL_SPACED_NAME, '[인물] '),
        ('EMAIL', EMAIL, '[이메일]'), ('PHONE', PHONE, '[전화번호]'),
        ('RESIDENT_ID', RESIDENT_ID, '[식별번호]'),
    ):
        text, n = pattern.subn(replacement, text)
        if n:
            changes.append({'pattern': label, 'count': n})
    return re.sub(r'\s+', ' ', text).strip(), changes


def sentences(text):
    # Preserve quotations/claims; do not split decimal amounts or abbreviations.
    return [s.strip() for s in re.split(r'(?<=다\.)\s+|(?<=요\.)\s+', text) if s.strip()]


def candidates(parts, rules):
    result = []
    for code, expression in rules.items():
        for part in parts:
            if re.search(expression, part):
                result.append({'code': code, 'evidence': part, 'certainty': 'UNCERTAIN'})
                break
    return result


def make_draft(row):
    text, changes = extra_redact(row['maskedText'])
    if not text:
        raise ValueError('Empty complaint cannot produce a review draft')
    title, title_changes = extra_redact(row['sourceTitleMasked'])
    parts = sentences(text)
    risks = candidates(parts, RISK_RULES)
    subjects = candidates(parts, SUBJECT_RULES)
    actions = candidates(parts[-1:], ACTION_RULES)
    flags = list(row['reviewFlags']) + ['HUMAN_PRIVACY_REVIEW_REQUIRED', 'NEGATION_AND_INTENT_UNREVIEWED']
    if changes or title_changes:
        flags.append('ADDITIONAL_REDACTION_APPLIED')
    if NAME_RISK.search(text) or NAME_RISK.search(title):
        flags.append('POSSIBLE_UNMASKED_PERSON_NAME')
    if '[상세주소]' in row['maskedText']:
        flags.append('CHECK_PREVIOUS_ADDRESS_REDACTION_FOR_INFORMATION_LOSS')
    if not risks:
        flags.append('RISK_ABSENCE_NOT_ESTABLISHED')
    selected = list(dict.fromkeys(parts[:2] + parts[-1:]))
    return {
        'queryKey': row['queryKey'], 'sourceUrl': row['sourceUrl'],
        'sourceCategory': row['sourceCategory'], 'sourceMaskedTextSha256': row['sourceMaskedTextSha256'],
        'inputReviewTextSha256': fingerprint(row['maskedText']),
        'maskedText': text, 'maskedTextSha256': fingerprint(text), 'sourceTitleMasked': title,
        'method': 'AI_AUTHORED_DETERMINISTIC_EXTRACTIVE_RULES_V1',
        'modelGeneratedPerRecord': False, 'humanReviewed': False, 'maskingCompleted': False,
        'reviewStatus': 'DRAFT', 'groupId': row['groupId'], 'splitProposal': row['splitProposal'],
        'splitFrozen': False, 'reviewFlags': sorted(set(flags)),
        'additionalRedactions': changes, 'additionalTitleRedactions': title_changes,
        'draftStructuredComplaint': {
            'factualSummary': ' '.join(selected),
            'context': {'placeType': 'UNKNOWN', 'relationshipType': 'UNKNOWN', 'timePattern': 'UNKNOWN'},
            'observedFacts': None,
            'riskSignals': risks or None, 'subjectMatters': subjects or None,
            'requestedActions': actions or None, 'ongoingStatus': 'UNKNOWN',
            'missingInformation': [
                '신청인의 주장과 확인된 사실 구분', '장소·관계·현재성 확인',
                '위험 신호의 부정·인용·과거 상황 확인', '요청 행위와 대상 코드 검수',
                *(['절단된 뒷부분 원문 확인'] if 'TRUNCATED_EXCERPT' in flags else [])],
        },
        'observationEvidenceCandidates': parts,
        'requestEvidenceCandidate': parts[-1] if parts else None,
        'expectedRelevantVerifiedCaseKeys': None, 'expectedRelevantCategoryReferenceKeys': None,
        'expectedRelevantOfficialGuideKeys': None, 'expectedEvidenceStatus': None,
        'expectedRiskSignalPresent': None, 'noReliableSimilarCaseExpected': None, 'ambiguityExpected': None,
    }


def run(source, output, expected_count=500):
    if output.exists():
        raise FileExistsError('Output exists; choose a new directory to preserve review edits')
    rows = [json.loads(line) for line in source.read_text(encoding='utf-8').splitlines() if line]
    if len(rows) != expected_count or len({r['queryKey'] for r in rows}) != expected_count:
        raise ValueError('Unexpected count or duplicate query key')
    drafts = [make_draft(row) for row in rows]
    summary = {
        'inputSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'recordCount': len(drafts),
        'method': 'deterministic extractive draft, not per-record LLM inference',
        'additionalRedactionRecords': sum(bool(d['additionalRedactions'] or d['additionalTitleRedactions']) for d in drafts),
        'flagCounts': dict(Counter(f for d in drafts for f in d['reviewFlags'])),
        'riskCandidateCounts': dict(Counter(r['code'] for d in drafts for r in d['draftStructuredComplaint']['riskSignals'] or [])),
        'subjectCandidateCounts': dict(Counter(r['code'] for d in drafts for r in d['draftStructuredComplaint']['subjectMatters'] or [])),
        'requestCandidateCounts': dict(Counter(r['code'] for d in drafts for r in d['draftStructuredComplaint']['requestedActions'] or [])),
        'humanReviewed': False, 'embeddingExecuted': False, 'retrievalEvaluationExecuted': False,
        'readyForEmbedding': 0, 'readyForScoredEvaluation': 0,
        'limitations': ['Rules miss paraphrases and cannot resolve negation or intent',
                       'All draft candidate codes require review; no candidate does not mean no risk',
                       'Extractive summary preserves claims, not independently verified facts',
                       'Ground truth and reviewed knowledge corpus remain unavailable'],
    }
    output.mkdir(parents=True)
    (output / 'drafts.jsonl').write_text(''.join(json.dumps(d, ensure_ascii=False) + '\n' for d in drafts), encoding='utf-8')
    (output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, default=Path('.local-data/acrc-review-500-v1/review-queue.jsonl'))
    parser.add_argument('--output', type=Path, default=Path('.local-data/acrc-drafts-500-v2'))
    parser.add_argument('--count', type=int, default=500)
    args = parser.parse_args()
    print(json.dumps(run(args.input, args.output, args.count), ensure_ascii=False, indent=2))
