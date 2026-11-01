"""Approved LAW API intake; default is an offline plan, never an API request."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import time
import xml.etree.ElementTree as ET
import requests
from bs4 import BeautifulSoup
from collect_acrc import normalize, redact, EMAIL, PHONE, RESIDENT_ID, NAME_RISK
from judge_relevance import save

KEYWORDS = ('유실물', '분실', '순찰', '중고', '교통', '진행', '안전조치', '불친절')
BASE = 'https://www.law.go.kr/DRF/'


def parse_xml(text):
    if '<!DOCTYPE' in text.upper() or '<!ENTITY' in text.upper():
        raise ValueError('Unsupported XML declaration')
    try:
        return ET.fromstring(text)
    except ET.ParseError:
        raise ValueError('Invalid XML response; body omitted') from None


def list_ids(text):
    root = parse_xml(text)
    total = root.findtext('.//totalCnt')
    if total is None or not total.isdigit():
        raise ValueError('Missing total count; possible API error')
    ids = [e.text for e in root.iter('법령해석일련번호')]
    if any(not i or not i.isdigit() for i in ids):
        raise ValueError('Invalid public record ID')
    return list(dict.fromkeys(ids)), int(total)


def list_ids_json(text):
    try:
        value = json.loads(text)['CgmExpc']
        total = value['totalCnt']
        if type(total) not in (str, int) or not str(total).isdigit():
            raise ValueError('Invalid count')
        items = value.get('cgmExpc', [])
        if isinstance(items, dict):
            items = [items]
        if not isinstance(items, list) or (int(total) > 0 and not items):
            raise ValueError('Missing items')
        ids = [str(item['법령해석일련번호']) for item in items]
        if any(not identifier.isdigit() for identifier in ids):
            raise ValueError('Invalid ID')
        return list(dict.fromkeys(ids)), int(total)
    except (ValueError, KeyError, TypeError):
        raise ValueError('Invalid LAW list JSON response; body omitted') from None


def query_draft_json(text, expected_id, keyword):
    try:
        value = json.loads(text)['CgmExpcService']
        identifier = str(value['법령해석일련번호'])
        raw = value.get('질의요지')
        if identifier != expected_id:
            raise ValueError('Identity mismatch')
        if raw is None:
            return None
        if not isinstance(raw, str):
            raise ValueError('Invalid question')
    except (ValueError, KeyError, TypeError):
        raise ValueError('Invalid LAW detail JSON response; body omitted') from None
    # Reuse the same redaction/length gates for both wire formats; answers never enter this tree.
    root = ET.Element('record')
    ET.SubElement(root, '법령해석일련번호').text = identifier
    ET.SubElement(root, '질의요지').text = raw
    return query_draft(ET.tostring(root, encoding='unicode'), expected_id, keyword)


def query_draft(text, expected_id, keyword):
    root = parse_xml(text)
    identifier = root.findtext('.//법령해석일련번호')
    if identifier != expected_id:
        raise ValueError('Record identity mismatch')
    field = root.find('.//질의요지')
    if field is None:
        return None
    # No response, names of officials, titles, or raw HTML written to disk.
    raw = BeautifulSoup(' '.join(field.itertext()), 'html.parser').get_text(' ', strip=True)
    masked = redact(raw)
    if not masked or len(masked) > 1200 or len(masked.split()) > 160:
        return None
    if any(p.search(masked) for p in (EMAIL, PHONE, RESIDENT_ID, NAME_RISK)):
        return None
    key = 'law-public-query-' + identifier
    return {'queryKey': key, 'groupId': key, 'sourceRecordId': identifier,
            'sourceUrl': 'https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=' + identifier,
            'sourceKind': 'PUBLISHED_PUBLIC_QUERY', 'sourceSection': '질의요지',
            'contentKind': 'AUTOMATICALLY_REDACTED_SOURCE_QUERY', 'discoveryKeyword': keyword,
            'maskedText': masked, 'maskedTextSha256': hashlib.sha256(masked.encode()).hexdigest(),
            'reviewStatus': 'DRAFT', 'privacyReview': 'AUTOMATED_SCREEN_ONLY',
            'maskingCompleted': False, 'benchmarkEligible': False, 'humanReviewed': False,
            'splitProposal': 'UNASSIGNED', 'expectedLabels': None}


def fetch(session, endpoint, params):
    try:
        response = session.get(BASE + endpoint, params=params, timeout=30, allow_redirects=False)
    except requests.RequestException:
        raise RuntimeError('LAW API transport error; request URL and credentials omitted') from None
    if response.status_code != 200:
        raise RuntimeError(f'LAW API HTTP {response.status_code}; body and request URL omitted')
    if '미신청된 목록/본문' in response.text:
        raise RuntimeError('LAW API access denied: police list/body is not enabled for this authentication value')
    if response.text.lstrip().lower().startswith(('<!doctype html', '<html')):
        raise RuntimeError('LAW API returned an HTML notice instead of XML; body and request URL omitted')
    return response.text


def run(args):
    per_keyword_limit = getattr(args, 'per_keyword_limit', 0)
    plan = {'version': 'police-law-api-intake-v2-json', 'target': 'npaCgmExpc', 'format': 'JSON', 'keywords': KEYWORDS,
            'limit': args.limit, 'pagesPerKeyword': args.pages, 'display': 20,
            'search': 2, 'sort': 'ddes', 'mode': 'execute' if args.execute else 'plan',
            'perKeywordLimit': per_keyword_limit or None,
            'approvalRequired': True, 'rawBodiesSaved': False, 'answersSaved': False,
            'autoPromote': False, 'note': 'Keywords select candidates, not validated subject labels'}
    if not args.execute:
        print(json.dumps(plan, ensure_ascii=False)); return
    if not args.approved:
        raise ValueError('Provider approval confirmation required before API use')
    oc = os.environ.get('LAW_API_OC', '').strip()
    if not oc:
        raise ValueError('LAW_API_OC not configured; GMS key cannot be used here')
    if args.output.exists():
        raise ValueError('Use a new output directory; do not overwrite prior intake')
    args.output.mkdir(parents=True)
    save(args.output/'protocol.json', plan)
    seen, skipped, count = set(), Counter(), 0
    keyword_counts = Counter()
    common = {'OC': oc, 'target': 'npaCgmExpc', 'type': 'JSON'}
    with requests.Session() as session, (args.output/'drafts.jsonl').open('w', encoding='utf-8') as output:
        for keyword in KEYWORDS:
            for page in range(1, args.pages + 1):
                if count >= args.limit or (per_keyword_limit and keyword_counts[keyword] >= per_keyword_limit):
                    break
                body = fetch(session, 'lawSearch.do', dict(common, query=keyword, search=2, display=20, page=page, sort='ddes'))
                ids, total = list_ids_json(body)
                time.sleep(1)
                for identifier in ids:
                    if per_keyword_limit and keyword_counts[keyword] >= per_keyword_limit:
                        break
                    if identifier in seen or count >= args.limit:
                        continue
                    seen.add(identifier)
                    row = query_draft_json(fetch(session, 'lawService.do', dict(common, ID=identifier)), identifier, keyword)
                    time.sleep(1)
                    if row is None:
                        skipped['missing_long_or_identifier_risk'] += 1; continue
                    output.write(json.dumps(row, ensure_ascii=False) + '\n'); output.flush()
                    count += 1
                    keyword_counts[keyword] += 1
                if not ids or page * 20 >= total:
                    break
    save(args.output/'summary.json', {'draftCount': count, 'uniqueSourceIdsSeen': len(seen),
                                    'discoveryKeywordCounts': keyword_counts,
                                    'skipped': skipped, 'benchmarkEligibleCount': 0,
                                    'liveQueryRecordsValidated': count,
                                    'limitations': ['Automatic redaction does not certify privacy',
                                                   'Responses are excluded to reduce answer leakage',
                                                   'Cross-source duplication and subject coverage still need review']})
    print(f'Collected draft queries: {count}; benchmark eligible: 0')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--execute', action='store_true')
    p.add_argument('--approved', action='store_true', help='Use only after actual provider approval')
    p.add_argument('--limit', type=int, choices=range(1, 501), default=100)
    p.add_argument('--pages', type=int, choices=range(1, 26), default=5)
    p.add_argument('--per-keyword-limit', type=int, choices=range(0, 101), default=0,
                   help='Cap accepted candidates per discovery keyword; 0 means no cap')
    p.add_argument('--output', type=Path, default=Path('.local-data/police-law-api-v1'))
    run(p.parse_args())
