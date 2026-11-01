"""Validate local paraphrased public-query drafts; never promote them to qrels."""
import argparse
from collections import Counter
from difflib import SequenceMatcher
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from collect_acrc import EMAIL, PHONE, RESIDENT_ID, CASE_ID, NAME_RISK, redact, normalize
from judge_relevance import save
from export_relevance import digest


def source_identity(url):
    parsed = urlparse(url)
    host = parsed.hostname or ''
    query = parse_qs(parsed.query)
    if parsed.scheme != 'https':
        raise ValueError('Expected HTTPS source')
    if host in ('law.go.kr', 'www.law.go.kr') and parsed.path == '/LSW/cgmExpcInfoP.do':
        key = query.get('cgmExpcDatSeq', [])
        prefix = 'law-public-query'
    elif host in ('guri.go.kr', 'www.guri.go.kr') and parsed.path == '/mayor/selectBbsNttView.do':
        key = query.get('nttNo', [])
        prefix = 'guri-public-query'
    else:
        raise ValueError('Unsupported source identity; add an explicit adapter')
    if len(key) != 1 or not key[0].isdigit():
        raise ValueError('Missing or ambiguous source identity')
    return prefix + '-' + key[0]


def prepare(seeds, existing):
    seen, texts, output = set(), set(), []
    existing_texts = [normalize(r['maskedText']) for r in existing]
    existing_urls = {r.get('sourceUrl') for r in existing}
    existing_identities = set()
    for url in existing_urls:
        if url:
            try:
                existing_identities.add(source_identity(url))
            except ValueError:
                pass  # Other collectors have separate source adapters.
    for row in seeds:
        if row['sourceKind'] != 'PUBLISHED_PUBLIC_QUERY' or row['access'] != 'BODY_CHECKED':
            raise ValueError('Only body-checked public queries; no FAQ or examples')
        identity = source_identity(row['sourceUrl'])
        text = normalize(row['maskedText'])
        if not text or redact(text) != text or any(p.search(text) for p in (EMAIL, PHONE, RESIDENT_ID, CASE_ID, NAME_RISK)):
            raise ValueError('Residual identifier or unnormalized draft')
        if row.get('privacyReview') != 'ASSISTANT_REVIEWED_PARAPHRASE' or not row.get('redactionNotes'):
            raise ValueError('Missing paraphrase/privacy provenance')
        if identity in seen or text in texts:
            raise ValueError('Duplicate source or draft')
        if row['sourceUrl'] in existing_urls or identity in existing_identities:
            raise ValueError('Source already in benchmark')
        seen.add(identity); texts.add(text)
        similarities = [SequenceMatcher(None, text, other).ratio() for other in existing_texts]
        highest = max(similarities, default=0)
        output.append(dict(row, queryKey=identity, groupId=identity,
                           contentKind='ASSISTANT_PARAPHRASE_OF_PUBLIC_QUERY',
                           maskedTextSha256=hashlib.sha256(text.encode()).hexdigest(), maskedText=text,
                           reviewStatus='DRAFT', maskingCompleted=True, splitProposal='UNASSIGNED',
                           humanReviewed=False, usageReview='PENDING', benchmarkEligible=False,
                           expectedLabels=None, maxExistingTextSimilarity=highest,
                           nearDuplicateFlag=highest >= 0.8))
    return output


def run(args):
    root = Path(__file__).resolve().parents[2]
    existing_path = root/'.local-data/acrc-drafts-500-v2/drafts.jsonl'
    seeds = json.loads(args.input.read_text(encoding='utf-8'))
    existing = [json.loads(s) for s in existing_path.read_text(encoding='utf-8').splitlines() if s]
    rows = prepare(seeds, existing)
    args.output.mkdir(parents=True, exist_ok=True)
    data = ''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows)
    destination = args.output/'drafts.jsonl'
    if destination.exists() and destination.read_text(encoding='utf-8') != data:
        raise ValueError('Existing intake differs; use new output')
    destination.write_text(data, encoding='utf-8')
    summary = {'version':'officer-public-intake-v1', 'checkedDate':'2026-10-30',
               'draftCount':len(rows), 'topicCounts':Counter(r['topic'] for r in rows),
               'uniqueSourceGroups':len({r['groupId'] for r in rows}),
               'nearDuplicateFlags':sum(r['nearDuplicateFlag'] for r in rows),
               'benchmarkEligibleCount':0, 'originalBenchmarkChanged':False,
               'inputHashes':{'seeds':digest(args.input), 'existing':digest(existing_path), 'drafts':digest(destination)},
               'limitations':['Paraphrased public queries, not full raw complaints or verified retrieval cases',
                              'Privacy review by assistant plus regex checks, not a guarantee of de-identification',
                              'Similarity check is lexical only; semantic and cross-publication duplicates may remain',
                              'One source and all derived question/answer/paraphrases must share a split group',
                              'Usage pending; no qrels, embeddings or production imports generated']}
    save(args.output/'summary.json', summary)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args())
