"""Prepare local review work, never infer labels or approve collected complaints."""
import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


def fingerprint(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def shingles(text):
    text = re.sub(r'\[[^\]]+\]', '', text)
    text = re.sub(r'[^가-힣a-zA-Z0-9]', '', text).lower()
    return {text[i:i + 5] for i in range(max(1, len(text) - 4))} - {''}


def group_rows(rows, threshold=.65):
    """Conservative candidate groups, not a claim of semantic duplicate detection."""
    if not 0 < threshold <= 1:
        raise ValueError('threshold must be in (0, 1]')
    rows = sorted(rows, key=lambda r: r['item_key'])
    parents = list(range(len(rows)))
    features = [shingles(r['masked_text']) for r in rows]

    def root(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i

    pairs = []
    for i, left in enumerate(features):
        for j in range(i):
            right = features[j]
            union = left | right
            score = len(left & right) / len(union) if union else 0
            if rows[i]['source_id'] == rows[j]['source_id'] or score >= threshold:
                parents[root(i)] = root(j)
                pairs.append({'left': rows[j]['item_key'], 'right': rows[i]['item_key'],
                              'character_5gram_jaccard': round(score, 6)})
    groups = defaultdict(list)
    for i, row in enumerate(rows):
        groups[root(i)].append(row)
    return sorted(groups.values(), key=lambda g: g[0]['item_key']), pairs


def split_groups(groups, dev_count):
    """Stable seeded subset sum; never split a duplicate candidate group."""
    total = sum(map(len, groups))
    if not 0 < dev_count < total:
        raise ValueError('development size must leave at least one test query')
    ordered = sorted(groups, key=lambda g: fingerprint('acrc-split-v1:' + g[0]['item_key']))
    paths = {0: ()}
    for i, group in enumerate(ordered):
        for size, chosen in list(paths.items()):
            next_size = size + len(group)
            if next_size <= dev_count and next_size not in paths:
                paths[next_size] = chosen + (i,)
    if dev_count not in paths:
        raise ValueError('Exact split impossible without separating a group; choose another dev count')
    selected = set(paths[dev_count])
    return [(g, 'dev' if i in selected else 'test') for i, g in enumerate(ordered)]


def prepare(source, output, count=500, dev_count=100, threshold=.65):
    rows = [json.loads(line) for line in source.read_text(encoding='utf-8').splitlines() if line]
    if len(rows) != count or len({r['item_key'] for r in rows}) != count:
        raise ValueError('Input count or unique query keys mismatch')
    for row in rows:
        if fingerprint(row['masked_text']) != row['masked_text_sha256'] or row['synthetic'] is not False:
            raise ValueError('Source fingerprint or provenance mismatch')
    # Refuse to overwrite work a reviewer may already have edited.
    if output.exists():
        raise FileExistsError('Review output exists; use a new output directory')
    groups, pairs = group_rows(rows, threshold)
    assignment = split_groups(groups, dev_count)
    packets = []
    for group, split in assignment:
        group_id = 'group-' + fingerprint('|'.join(r['item_key'] for r in group))[:16]
        for row in group:
            packets.append({
                'queryKey': row['item_key'], 'sourceId': row['source_id'],
                'sourceUrl': row['source_url'], 'sourceCategory': row['source_category'],
                'sourcePublishedDate': row['source_published_date'],
                'sourceMaskedTextSha256': row['masked_text_sha256'],
                'sourceTitleMasked': row['source_title_masked'],
                'maskedText': row['masked_text'], 'licenseNotice': row['license_notice'],
                'synthetic': False, 'humanReviewed': False, 'maskingCompleted': False,
                'groupId': group_id, 'splitProposal': split, 'splitFrozen': False,
                'reviewPriority': 'POLICE_FIRST' if row['source_category'] == '경찰민원' else 'STANDARD',
                'reviewFlags': (['TRUNCATED_EXCERPT'] if row['excerpt_truncated'] else [])
                    + (['POSSIBLE_DUPLICATE_GROUP'] if len(group) > 1 else []),
                'reviews': {stage: {'status': 'PENDING', 'reviewer': None, 'reviewedAt': None, 'notes': None}
                            for stage in ('privacy', 'usage', 'structure', 'relevance', 'caseGrouping')},
                'structuredComplaint': None,
                'corpusSnapshotSha256': None,
                'expectedRelevantVerifiedCaseKeys': None,
                'expectedRelevantCategoryReferenceKeys': None,
                'expectedRelevantOfficialGuideKeys': None,
                'expectedEvidenceStatus': None, 'expectedRiskSignalPresent': None,
                'noReliableSimilarCaseExpected': None, 'ambiguityExpected': None,
            })
    packets.sort(key=lambda r: (r['reviewPriority'] != 'POLICE_FIRST', r['queryKey']))
    split_counts = Counter(p['splitProposal'] for p in packets)
    split_groups_by_name = {split: {p['groupId'] for p in packets if p['splitProposal'] == split}
                           for split in ('dev', 'test')}
    overlap = len(split_groups_by_name['dev'] & split_groups_by_name['test'])
    if overlap:
        raise ValueError('Duplicate candidate group leaked across splits')
    categories = {split: dict(Counter(p['sourceCategory'] for p in packets if p['splitProposal'] == split))
                  for split in ('dev', 'test')}
    summary = {
        'datasetId': 'acrc-public-500-review-v1', 'sourceFileSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'queryCount': len(packets), 'groupCount': len(groups), 'candidateDuplicatePairs': len(pairs),
        'candidateDuplicateGroups': sum(len(g) > 1 for g in groups),
        'grouping': {'method': 'character-5gram Jaccard + shared source ID, connected components',
                     'threshold': threshold, 'humanReviewed': False},
        'splitCounts': dict(split_counts), 'splitCategoryCounts': categories,
        'crossSplitGroupOverlap': overlap, 'splitFrozen': False,
        'truncatedExcerptCount': sum('TRUNCATED_EXCERPT' in p['reviewFlags'] for p in packets),
        'readyForEmbedding': 0, 'readyForScoredEvaluation': 0,
        'pendingReviewCounts': {stage: len(packets) for stage in packets[0]['reviews']},
        'retrievalExperimentExecuted': False,
        'limitations': ['Lexical grouping can miss paraphrases or group unrelated cases; manual review required',
                       'Split is provisional, deterministic, and not category-stratified',
                       'Other source categories are not automatically no-match ground truth',
                       'No reviewed corpus snapshot or relevance labels supplied; no accuracy metrics computed']}
    output.mkdir(parents=True)
    for name, value in [('summary.json', summary), ('duplicate-candidates.json', pairs)]:
        (output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'review-queue.jsonl').write_text(''.join(json.dumps(p, ensure_ascii=False) + '\n' for p in packets), encoding='utf-8')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, default=Path('.local-data/acrc-public-500/complaints.jsonl'))
    parser.add_argument('--output', type=Path, default=Path('.local-data/acrc-review-500-v1'))
    parser.add_argument('--count', type=int, default=500)
    parser.add_argument('--dev-count', type=int, default=100)
    parser.add_argument('--duplicate-threshold', type=float, default=.65)
    args = parser.parse_args()
    print(json.dumps(prepare(args.input, args.output, args.count, args.dev_count, args.duplicate_threshold), ensure_ascii=False, indent=2))
