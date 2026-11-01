"""Bind frozen police candidates to their AI privacy screen and reuse structure extraction."""
import argparse
import hashlib
import json
from pathlib import Path
from collections import Counter
from export_relevance import digest
from judge_relevance import save
from structure_complaints import run as structure


def privacy_gate(rows, checkpoint):
    reviews={}
    for row in rows:
        key=row['queryKey'];job=checkpoint['jobs'].get(key)
        if hashlib.sha256(row['maskedText'].encode()).hexdigest()!=row['maskedTextSha256']:
            raise ValueError('Source text changed')
        if not job or job!=row.get('screening') or job['review']['privacy']!='PASS':
            raise ValueError('Privacy review missing or changed')
        if row['screeningDisposition'] not in ('SPECIFIC_CANDIDATE','GENERAL_OR_EXAMPLE_CANDIDATE'):
            raise ValueError('Query is not a selected candidate')
        reviews[key]={'sourceTextSha256':row['maskedTextSha256'],'judgments':{'privacy':'PASS'},
                      'judgmentSource':'AI_SCREENING','model':job['model'],'humanReviewed':False}
    return reviews


def run(args):
    source=Path('.local-data/police-candidates-split-v1/candidates.jsonl')
    screen=Path('.local-data/police-intake-screen-v1/checkpoint.json')
    lock_path=Path('docs/results/police-candidates-split-v1.json')
    lock=json.loads(lock_path.read_text(encoding='utf-8'))
    if digest(source)!=lock['candidateSha256']:raise ValueError('Frozen candidates changed')
    rows=[json.loads(s) for s in source.read_text(encoding='utf-8').splitlines() if s]
    screen_state=json.loads(screen.read_text(encoding='utf-8'))
    reviews=privacy_gate(rows,screen_state)
    expected={k:(g['groupId'],g['split']) for g in lock['groupManifest'] for k in g['members']}
    if len(rows)!=55 or {r['queryKey']:(r['groupId'],r['splitProposal']) for r in rows}!=expected:
        raise ValueError('Frozen group or split mismatch')
    args.output.mkdir(parents=True,exist_ok=True)
    gate={'datasetSha256':digest(source),'reviews':reviews,'provenance':'AI_PRIVACY_SCREEN_NOT_HUMAN_CERTIFICATION',
          'screenCheckpointSha256':digest(screen),'splitLockSha256':digest(lock_path)}
    path=args.output/'privacy-gate.json'
    if path.exists() and json.loads(path.read_text(encoding='utf-8'))!=gate:
        raise ValueError('Privacy provenance changed')
    save(path,gate)
    structure(source,path,args.output/'generated',args.model,3)
    requests=args.output/'generated/requests.jsonl'
    exported=[json.loads(s) for s in requests.read_text(encoding='utf-8').splitlines() if s]
    summary={'version':'police-structure-input-v1','queryCount':len(exported),
             'splitCounts':Counter(r['splitProposal'] for r in exported),
             'inputHashes':{'candidates':digest(source),'screenCheckpoint':digest(screen),'splitLock':digest(lock_path),
                            'requests':digest(requests)},'privacySource':'AI_SCREENING','humanReviewed':False,
             'benchmarkEligibleCount':0,'javaValidationExecuted':False,
             'limitations':['AI privacy PASS is not a privacy guarantee',
                            'maskingCompleted=true is set only on separate evaluation requests after this AI gate',
                            'Source IDs establish traceability, not semantic correctness',
                            'Generated structures are not retrieval relevance labels or final classifications']}
    save(args.output/'summary.json',summary);print(json.dumps(summary,ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True)
    p.add_argument('--model',default='gpt-4o');run(p.parse_args())
