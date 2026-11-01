"""Freeze identities of an AI-label benchmark; locking does not certify label correctness."""
import argparse
import hashlib
import json
from pathlib import Path

FILES={
    'queries':'.local-data/acrc-structured-java-v1/validated-requests.jsonl',
    'corpus':'tools/public_complaints/relevance_documents_v3.json',
    'qrels':'.local-data/acrc-blind-v3/qrels/qrels.jsonl',
    'resolvedQueries':'.local-data/acrc-blind-v3/qrels/resolved-queries.jsonl',
    'qrelsSummary':'.local-data/acrc-blind-v3/qrels/summary.json',
    'embeddingAndThresholdConfig':'src/main/resources/application.properties',
}


def fingerprint(root,files):
    result={}
    for name,relative in files.items():
        path=(root/relative).resolve()
        if not path.is_relative_to(root.resolve()):raise ValueError('Benchmark path escapes workspace')
        result[name]={'path':relative,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
    return result


def verify(root,lock):
    current=fingerprint(root,{k:v['path'] for k,v in lock['inputs'].items()})
    if current!=lock['inputs']:raise ValueError('Benchmark changed: use a new version; do not compare as the same benchmark')
    return True


def create(root,destination):
    if destination.exists():raise FileExistsError('Frozen benchmark must not be overwritten')
    summary=json.loads((root/FILES['qrelsSummary']).read_text(encoding='utf-8'))
    if summary['fullyResolvedQueries']!=500 or summary['resolvedPairs']!=10000 or summary['groupOverlap']!=0:
        raise ValueError('Expected complete split-bound v3 benchmark')
    inputs=fingerprint(root,FILES)
    if inputs['corpus']['sha256']!=summary['inputHashes']['corpus']:raise ValueError('Corpus differs from labels')
    lock={'benchmarkId':'public-500-pool20-ai-v3','labelStatus':'FROZEN_AI_PROVISIONAL_NOT_GROUND_TRUTH',
          'inputs':inputs,'queryCount':500,'documentCount':20,'splitCounts':summary['completeQuerySplitCounts'],
          'topK':{'CATEGORY_REFERENCE':3,'OFFICIAL_GUIDE':3},
          'metrics':{'recallMrrHit1Denominator':'Queries with at least one relevant document in the channel',
                     'precisionDenominator':'All queries, fixed K','mrrRankLimit':3},
          'selection':{'weights':[0.05,0.1,0.3],'objective':'Dev OFFICIAL_GUIDE MRR','tieBreak':'Smaller weight'},
          'releasePolicy':'Do not merge audit labels in place; new labels, corpus or queries require a new benchmark ID',
          'limitations':['Previously reused test set; not a pristine holdout','AI label agreement does not prove correctness']}
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return lock


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--lock',type=Path,required=True)
    p.add_argument('--verify',action='store_true');a=p.parse_args();root=Path(__file__).resolve().parents[2]
    if a.verify:verify(root,json.loads(a.lock.read_text(encoding='utf-8')));print('Frozen benchmark verified')
    else:print('Created:',create(root,a.lock)['benchmarkId'])
