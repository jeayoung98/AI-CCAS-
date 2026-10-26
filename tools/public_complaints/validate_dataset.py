"""Structural/obvious-identifier checks only; does not certify human privacy review."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse
from collect_acrc import EMAIL, PHONE, RESIDENT_ID, CASE_ID, NAME_RISK, END


def validate(path, count):
    rows = [json.loads(line) for line in (path / 'complaints.jsonl').read_text(encoding='utf-8').splitlines() if line]
    problems = []
    if len(rows) != count:
        problems.append(f'Expected {count} records; found {len(rows)}')
    for field in ('item_key', 'source_url', 'masked_text_sha256'):
        if len({row[field] for row in rows}) != len(rows):
            problems.append(f'Duplicates in {field}')
    for row in rows:
        text = row['masked_text']
        key = row['item_key']
        if not 80 <= len(text) <= 1200 or len(text.split()) > 160:
            problems.append(key + ': invalid excerpt size')
        if hashlib.sha256(text.encode()).hexdigest() != row['masked_text_sha256']:
            problems.append(key + ': hash mismatch')
        if any(pattern.search(text) for pattern in (EMAIL, PHONE, RESIDENT_ID, CASE_ID, NAME_RISK)):
            problems.append(key + ': residual identifier pattern')
        if urlparse(row['source_url']).netloc != 'www.acrc.go.kr':
            problems.append(key + ': unexpected source')
        if row['synthetic'] or row['human_reviewed'] or row['maskingCompleted'] or row['review_status'] != 'REVIEW_REQUIRED':
            problems.append(key + ': inaccurate review/source status')
        if END.search(text):
            problems.append(key + ': possible answer leakage')
    summary = {'count': len(rows), 'unique_ids': len({r['item_key'] for r in rows}),
               'category_counts': dict(Counter(r['source_category'] for r in rows)),
               'published_date_min': min((r['source_published_date'] for r in rows if r['source_published_date']), default=None),
               'published_date_max': max((r['source_published_date'] for r in rows if r['source_published_date']), default=None),
               'truncated_count': sum(r['excerpt_truncated'] for r in rows),
               'license_counts': dict(Counter(r['license_notice'] for r in rows)),
               'automated_checks_passed': not problems, 'human_reviewed': False,
               'problems': problems}
    (path / 'validation.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if problems:
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, default=Path('.local-data/acrc-public-500'))
    parser.add_argument('--count', type=int, default=500)
    args = parser.parse_args()
    validate(args.input, args.count)
