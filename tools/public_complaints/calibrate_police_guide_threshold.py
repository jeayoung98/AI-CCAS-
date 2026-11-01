"""Dev-only cutoff calibration: reward correctly accepted documents, not mere availability."""
import argparse
import json
from pathlib import Path
from export_relevance import digest
from evaluate_guide_cutoff import metrics
from benchmark_lock import verify

GRID=tuple(i/100 for i in range(20,91))
RULE={'objective':'filter dev adoption gate first, then accepted-document F0.5; ties higher cutoff; no eligible cutoff means diagnostic only',
      'minDevAcceptedPrecision':.8,'minDevCorrectlySupportedQueries':5,
      'strategy':'BASELINE','channel':'OFFICIAL_GUIDE','grid':GRID,
      'adoption':'experimental only; retain production defaults pending evidence'}


def score(rows,labels,threshold):
    result=metrics(rows,labels,threshold)
    total=sum(sum(v==1 for v in labels[r['queryKey']].values()) for r in rows)
    correct=sum(sum(labels[r['queryKey']][h['itemKey']]==1 for h in r['rankings']['BASELINE:OFFICIAL_GUIDE'] if h['cosine']>=threshold) for r in rows)
    accepted=result['acceptedHitCount'];precision=correct/accepted if accepted else None
    recall=correct/total if total else None
    f=(1.25*precision*recall/(.25*precision+recall)) if precision and recall else 0.0
    result.update(totalRelevantDocuments=total,correctAcceptedDocuments=correct,
                  incorrectAcceptedDocuments=accepted-correct,documentRecall=recall,documentF05=f)
    return result


def select(dev,labels):
    trials=[score(dev,labels,t) for t in GRID]
    eligible=[t for t in trials if t['acceptedHitPrecision'] is not None and t['acceptedHitPrecision']>=RULE['minDevAcceptedPrecision']
              and t['queriesWithRelevantAcceptedGuide']>=RULE['minDevCorrectlySupportedQueries']]
    selected=max(eligible or trials,key=lambda x:(x['documentF05'],x['threshold']))
    qualifies=bool(eligible)
    return selected,trials,qualifies


def run(args):
    root=Path.cwd();lock=json.loads(Path('docs/results/benchmark-police55-officer-ai-v1.json').read_text(encoding='utf-8'))
    verify(root,lock)
    database=Path('.local-data/police-pool-database-selected-v1');qrels=Path('.local-data/police-officer-relevance-v1/qrels')
    db=json.loads((database/'database-report.json').read_text(encoding='utf-8'))
    if db['queryInputSha256']!=lock['inputs']['queries']['sha256'] or db['corpusSha256']!=lock['inputs']['corpus']['sha256']:
        raise ValueError('Changed DB input')
    args.output.mkdir(parents=True,exist_ok=True)
    binding={'version':'police-guide-cutoff-v2','rule':RULE,'queries':lock['inputs']['queries']['sha256'],
             'qrels':digest(qrels/'qrels.jsonl'),'rankings':digest(database/'rankings.json'),'corpus':db['corpusSha256']}
    # Written before dev selection or test scoring.
    protocol=args.output/'protocol.json'
    encoded=json.dumps(binding,ensure_ascii=False,indent=2)+'\n'
    if protocol.exists() and protocol.read_text(encoding='utf-8')!=encoded:raise ValueError('Changed calibration protocol')
    protocol.write_text(encoded,encoding='utf-8')
    rows=json.loads((database/'rankings.json').read_text(encoding='utf-8'))
    resolved={r['queryKey']:r for r in (json.loads(s) for s in (qrels/'resolved-queries.jsonl').read_text(encoding='utf-8').splitlines())}
    if len(rows)!=55 or len({r['queryKey'] for r in rows})!=55 or set(resolved)!={r['queryKey'] for r in rows}:
        raise ValueError('Query identities changed')
    for r in rows:
        if r['split']!=resolved[r['queryKey']]['splitProposal'] or r['sourceTextSha256']!=resolved[r['queryKey']]['sourceTextSha256']:
            raise ValueError('Query split/source changed')
    labels={r['queryKey']:{} for r in rows}
    for line in (qrels/'qrels.jsonl').read_text(encoding='utf-8').splitlines():
        q=json.loads(line)
        if q['itemType']=='OFFICIAL_GUIDE':labels[q['queryKey']][q['itemKey']]=q['relevance']
    if any(len(v)!=16 for v in labels.values()):raise ValueError('Incomplete labels')
    dev=[r for r in rows if r['split']=='dev'];test=[r for r in rows if r['split']=='test']
    selected,trials,qualifies=select(dev,labels)
    report={'version':'police-guide-cutoff-v2','binding':binding,'selectedThreshold':selected['threshold'],
            'devSelected':selected,'devTrials':trials,'passesDevAdoptionGate':qualifies,
            'testSelected':score(test,labels,selected['threshold']),'testProvisional070':score(test,labels,.7),
            'decision':'EXPERIMENTAL_CANDIDATE' if qualifies else 'REJECT_FOR_PRODUCTION',
            'productionThresholdChanged':False,'limitations':['AI direct-relevance labels, not evidence-status ground truth',
                'Guide cutoff on BASELINE Top3 only; category/case thresholds unchanged',
                'Selection uses dev only; test is reported without selecting another cutoff',
                'v1 diagnostic test result was already observed before correcting gate-first selection; not a pristine holdout',
                'F0.5 and gate are experiment policy, not a certified safety target']}
    out=args.output/'report.json';text=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    if out.exists() and out.read_text(encoding='utf-8')!=text:raise ValueError('Frozen calibration report differs')
    out.write_text(text,encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ('binding','devTrials')},ensure_ascii=False))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);run(p.parse_args())
