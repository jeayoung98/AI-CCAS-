"""Inventory existing source categories and AI codes; no new semantic judgments."""
import argparse
from collections import Counter
import json
from pathlib import Path
from export_relevance import digest
from graded_rubric_pilot import select_dev
from judge_relevance import save

SUBJECTS = ('SAFETY_THREAT', 'LOST_ITEM', 'TRAFFIC_ADMIN', 'CYBER_TRANSACTION',
            'PATROL_REQUEST', 'CASE_STATUS', 'POLICE_SERVICE_COMPLAINT',
            'GENERAL_CONSULTATION', 'OTHER', 'UNKNOWN')


def audit(source_rows, structured_rows, docs):
    dev = select_dev(source_rows)
    indexed = {r['queryKey']: r for r in structured_rows}
    if len(indexed) != len(structured_rows) or set(indexed) != {r['queryKey'] for r in source_rows}:
        raise ValueError('Mismatched query identities')
    for r in source_rows:
        s = indexed[r['queryKey']]
        if s['sourceTextSha256'] != r['maskedTextSha256'] or s['splitProposal'] != r['splitProposal'] or s['groupId'] != r['groupId']:
            raise ValueError('Mismatched source lineage')
    groups = {'all': source_rows, 'dev': dev,
              'policeSourceDev': [r for r in dev if r['sourceCategory'] == '경찰민원']}
    result = {}
    for name, rows in groups.items():
        counts = Counter(code['code'] for r in rows
                         for code in indexed[r['queryKey']]['request']['structuredComplaint']['subjectMatters'])
        result[name] = {'queryCount': len(rows), 'sourceCategoryCounts': Counter(r['sourceCategory'] for r in rows),
                        'existingAiSubjectCodeCounts': {k: counts[k] for k in SUBJECTS}}
    return {'version': 'officer-coverage-inventory-v1', 'groups': result,
            'corpusTypeCounts': {kind: sum(d['itemType'] == kind for d in docs)
                                 for kind in ('VERIFIED_CASE', 'CATEGORY_REFERENCE', 'OFFICIAL_GUIDE')},
            'limitations': ['Source category is publisher metadata, not a police workload classification',
                           'Subject codes are existing AI extraction, not verified ground truth; multi-label counts may exceed queries',
                           'Zero observed code means a coverage warning, not proof of absence in original text',
                           'No new test labeling or retrieval evaluation performed']}


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parents[2]
    paths = {'source': root/'.local-data/acrc-drafts-500-v2/drafts.jsonl',
             'structured': root/'.local-data/acrc-structured-java-v1/validated-requests.jsonl',
             'corpus': Path(__file__).with_name('officer_documents_v1.json')}
    rows = {k: [json.loads(s) for s in paths[k].read_text(encoding='utf-8').splitlines() if s]
            for k in ('source', 'structured')}
    report = audit(rows['source'], rows['structured'], json.loads(paths['corpus'].read_text(encoding='utf-8'))['documents'])
    report['inputHashes'] = {k: digest(v) for k, v in paths.items()}
    save(args.output, report)
    print(json.dumps(report, ensure_ascii=False))
