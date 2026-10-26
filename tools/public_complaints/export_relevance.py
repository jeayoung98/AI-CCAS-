"""Export version-bound partial qrels; never invent unjudged negatives."""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def export(source, corpus, responses, privacy, output):
    if output.exists():
        raise FileExistsError('Choose a new snapshot directory')
    rows = [json.loads(s) for s in source.read_text(encoding='utf-8').splitlines() if s]
    by_id = {r['queryKey']: r for r in rows}
    if len(by_id) != len(rows):
        raise ValueError('Duplicate queries')
    documents = json.loads(corpus.read_text(encoding='utf-8'))['documents']
    types = {d['itemKey']: d['itemType'] for d in documents}
    if len(types) != len(documents):
        raise ValueError('Duplicate documents')
    review = json.loads(responses.read_text(encoding='utf-8'))
    privacy_review = json.loads(privacy.read_text(encoding='utf-8'))
    if review['datasetSha256'] != digest(source) or privacy_review['datasetSha256'] != digest(source):
        raise ValueError('Query snapshot changed')
    if review['corpusSha256'] != digest(corpus):
        raise ValueError('Document snapshot changed')
    qrels, complete, pending = [], [], []
    for key, record in review['reviews'].items():
        if key not in by_id or record['sourceTextSha256'] != by_id[key]['maskedTextSha256']:
            raise ValueError('Unknown or changed query')
        pr = privacy_review['reviews'].get(key, {})
        if pr.get('sourceTextSha256') != by_id[key]['maskedTextSha256'] or pr.get('judgments', {}).get('privacy') != 'PASS':
            raise ValueError('Privacy approval missing or stale')
        if set(record['labels']) != set(types):
            raise ValueError('Document keys differ')
        unresolved = []
        positive, negative = defaultdict(list), defaultdict(list)
        for doc, label in record['labels'].items():
            decision = label['judgment']
            if decision not in ('RELEVANT', 'IRRELEVANT', 'UNSURE', None):
                raise ValueError('Unknown judgment')
            if decision in ('UNSURE', None):
                unresolved.append({'itemKey': doc, 'state': decision, 'reason': label.get('reason', '')})
                continue
            relevance = int(decision == 'RELEVANT')
            qrels.append({'queryKey': key, 'itemKey': doc, 'itemType': types[doc], 'relevance': relevance,
                          'reason': label.get('reason', ''),
                          'reviewer': ('AI:' + label.get('model', 'unknown')) if label.get('judgmentSource') == 'AI' else record.get('reviewer', ''),
                          'reviewedAt': label.get('reviewedAt', record['updatedAt']), 'reviewRevision': record['revision'],
                          'judgmentSource': label.get('judgmentSource', 'HUMAN'),
                          'model': label.get('model'), 'confidence': label.get('confidence'),
                          'promptVersion': label.get('promptVersion')})
            (positive if relevance else negative)[types[doc]].append(doc)
        if unresolved:
            pending.append({'queryKey': key, 'state': 'UNRESOLVED_LABELS', 'items': unresolved})
        else:
            row = by_id[key]
            complete.append({'queryKey': key, 'groupId': row['groupId'], 'splitProposal': row['splitProposal'],
                             'sourceCategory': row['sourceCategory'], 'sourceTextSha256': row['maskedTextSha256'],
                             'relevantKeysByChannel': dict(positive), 'irrelevantKeysByChannel': dict(negative),
                             'scope': 'REVIEWED_DOCUMENT_POOL_ONLY', 'evidenceStatusGroundTruth': None,
                             'caseNoMatchGroundTruth': None, 'riskGroundTruth': None})
    pending.extend({'queryKey': key, 'state': 'NOT_REVIEWED', 'items': []} for key in by_id if key not in review['reviews'])
    split_counts = dict(Counter(r['splitProposal'] for r in complete))
    dev = {r['groupId'] for r in complete if r['splitProposal'] == 'dev'}
    test = {r['groupId'] for r in complete if r['splitProposal'] == 'test'}
    if dev & test:
        raise ValueError('Group leakage across proposed splits')
    document_counts = dict(Counter(types.values()))
    saturated_channels = [channel for channel, k in [('VERIFIED_CASE', 5), ('CATEGORY_REFERENCE', 3), ('OFFICIAL_GUIDE', 3)]
                          if 0 < document_counts.get(channel, 0) <= k]
    summary = {'queryCount': len(rows), 'reviewedQueries': len(review['reviews']),
               'fullyResolvedQueries': len(complete), 'unresolvedQueries': sum(p['state'] == 'UNRESOLVED_LABELS' for p in pending),
               'unreviewedQueries': sum(p['state'] == 'NOT_REVIEWED' for p in pending),
               'resolvedPairs': len(qrels), 'positivePairs': sum(q['relevance'] for q in qrels),
               'negativePairs': sum(1-q['relevance'] for q in qrels),
               'judgmentSourceCounts': dict(Counter(q['judgmentSource'] for q in qrels)),
               'unresolvedPairs': sum(len(p['items']) for p in pending),
               'completeQuerySplitCounts': split_counts, 'groupOverlap': 0,
               'documentCounts': document_counts,
               'channelsWithEntirePoolWithinTopK': saturated_channels,
               'unselectedPairs': sum(i['state'] is None for p in pending for i in p['items']),
               'unsurePairs': sum(i['state'] == 'UNSURE' for p in pending for i in p['items']),
               'inputHashes': {name: digest(path) for name, path in [('queries', source), ('corpus', corpus), ('relevanceReviews', responses), ('privacyReviews', privacy)]},
               'semanticEvaluationExecuted': False, 'readyForFullEvaluation': False,
               'limitations': ['Only a limited candidate pool has been judged; unjudged is not irrelevant',
                              'No VERIFIED_CASE documents or evidence-status/risk ground truth',
                              'Draft structured complaints remain incomplete; do not embed raw complaint text',
                              'Usage approval and knowledge ingestion review remain separate',
                              'Channels listed in channelsWithEntirePoolWithinTopK cannot distinguish rankers by Recall@K when all candidates are returned']}
    output.mkdir(parents=True)
    for name, records in [('qrels.jsonl', qrels), ('resolved-queries.jsonl', complete), ('pending.jsonl', pending)]:
        (output / name).write_text(''.join(json.dumps(r, ensure_ascii=False)+'\n' for r in records), encoding='utf-8')
    (output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return summary


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', type=Path, default=root/'.local-data/acrc-drafts-500-v2/drafts.jsonl')
    parser.add_argument('--corpus', type=Path, default=Path(__file__).with_name('relevance_documents_v2.json'))
    parser.add_argument('--responses', type=Path, default=root/'.local-data/acrc-human-review/relevance-responses-v2.json')
    parser.add_argument('--privacy', type=Path, default=root/'.local-data/acrc-human-review/responses.json')
    args = parser.parse_args()
    print(json.dumps(export(args.source, args.corpus, args.responses, args.privacy, args.output), ensure_ascii=False, indent=2))
