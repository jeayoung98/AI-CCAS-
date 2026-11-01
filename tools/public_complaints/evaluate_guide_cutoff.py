"""Dev-only guide cutoff selection; pool-relative AI labels, never final routing truth."""
import argparse
from collections import Counter
import json
from pathlib import Path
from export_relevance import digest

GRID=tuple(i/100 for i in range(20,91,5))


def metrics(rows, labels, threshold):
    confusion=Counter(tp=0,fp=0,tn=0,fn=0)
    accepted_count=correct_accepted=unsupported_queries=correct_queries=0
    for row in rows:
        gold={k for k,v in labels[row['queryKey']].items() if v==1}
        accepted=[h['itemKey'] for h in row['rankings']['BASELINE:OFFICIAL_GUIDE'] if h['cosine']>=threshold]
        predicted=bool(accepted); expected=bool(gold)
        confusion['tp' if expected and predicted else 'fn' if expected else 'fp' if predicted else 'tn']+=1
        accepted_count+=len(accepted);correct_accepted+=len(set(accepted)&gold)
        unsupported_queries+=bool(accepted) and not bool(set(accepted)&gold)
        correct_queries+=bool(set(accepted)&gold)
    tp,fp,tn,fn=(confusion[k] for k in ('tp','fp','tn','fn'))
    recall=tp/(tp+fn) if tp+fn else None
    specificity=tn/(tn+fp) if tn+fp else None
    return {'queries':len(rows),'threshold':threshold,'confusion':dict(confusion),
            'availableGuideRecall':recall,'noRelevantGuideRejectionRate':specificity,
            'balancedAccuracy':(recall+specificity)/2 if recall is not None and specificity is not None else None,
            'acceptedHitPrecision':correct_accepted/accepted_count if accepted_count else None,
            'acceptedHitCount':accepted_count,'queriesWithOnlyIrrelevantAcceptedGuides':unsupported_queries,
            'queriesWithRelevantAcceptedGuide':correct_queries,
            'relevantGuideRetrievalAndAcceptanceRecall':correct_queries/(tp+fn) if tp+fn else None}


def evaluate(rows, labels):
    dev=[r for r in rows if r['split']=='dev'];test=[r for r in rows if r['split']=='test']
    if not dev or not test or len({r['queryKey'] for r in rows})!=len(rows):
        raise ValueError('Need unique dev and test inputs')
    trials=[metrics(dev,labels,t) for t in GRID]
    if any(t['balancedAccuracy'] is None for t in trials):
        raise ValueError('Dev must contain positive and negative guide labels')
    selected=max(trials,key=lambda t:(t['balancedAccuracy'],-t['threshold']))['threshold']
    return {'selectionRule':'Dev balanced accuracy of pool guide availability; tie chooses lower cutoff',
            'grid':GRID,'dev':trials,'selectedThreshold':selected,
            'testSelected':metrics(test,labels,selected),'testProvisional070':metrics(test,labels,.7),
            'productionThresholdChanged':False,
            'scope':'BASELINE official-guide channel only; no relevant guide in this pool is not globally no evidence',
            'limitations':['AI labels are not independent human truth',
                'Availability classification can count a positive even when accepted documents are wrong; see acceptedHitPrecision',
                'No VERIFIED_CASE refusal or real ambiguity semantic ground truth',
                'Risk extraction accuracy is not measured by flag preservation']}


def run(args):
    summary=json.loads((args.qrels/'summary.json').read_text(encoding='utf-8'))
    db=json.loads((args.database/'database-report.json').read_text(encoding='utf-8'))
    if summary['inputHashes']['corpus']!=db['corpusSha256']:
        raise ValueError('Corpus differs')
    rows=json.loads((args.database/'rankings.json').read_text(encoding='utf-8'))
    resolved={r['queryKey']:r for r in (json.loads(s) for s in (args.qrels/'resolved-queries.jsonl').read_text(encoding='utf-8').splitlines())}
    if set(resolved)!={r['queryKey'] for r in rows} or any(r['split']!=resolved[r['queryKey']]['splitProposal']
            or r.get('sourceTextSha256')!=resolved[r['queryKey']]['sourceTextSha256'] for r in rows):
        raise ValueError('Query splits differ')
    labels={r['queryKey']:{} for r in rows}
    for line in (args.qrels/'qrels.jsonl').read_text(encoding='utf-8').splitlines():
        q=json.loads(line)
        if q['itemType']=='OFFICIAL_GUIDE':
            if q['queryKey'] not in labels or q['itemKey'] in labels[q['queryKey']] or q['relevance'] not in (0,1):
                raise ValueError('Invalid or duplicate qrel')
            labels[q['queryKey']][q['itemKey']]=q['relevance']
    count=summary['documentCounts']['OFFICIAL_GUIDE']
    if any(len(v)!=count for v in labels.values()): raise ValueError('Incomplete guide labels')
    report=evaluate(rows,labels)
    report['inputs']={'qrelsSha256':digest(args.qrels/'qrels.jsonl'),'rankingsSha256':digest(args.database/'rankings.json'),
                      'corpusSha256':db['corpusSha256'],'queryInputSha256':db['queryInputSha256']}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ('dev','inputs')},ensure_ascii=False))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--qrels',type=Path,required=True)
    p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    run(p.parse_args())
