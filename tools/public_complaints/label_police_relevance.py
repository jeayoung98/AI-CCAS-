"""Paired officer relevance labels for frozen queries; fixed primary AI, not human truth."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path
from export_relevance import digest
from judge_relevance import save
from officer_rubric_pilot import SYSTEM, judge_batched, response_schema, validate
from graded_rubric_pilot import MODELS, agreement

VERSION='police-officer-relevance-v1'
PRIMARY='gpt-4o'


def projection(grade):
    if grade=='UNASSESSABLE':return None
    if type(grade) is not int or grade not in (0,1,2):raise ValueError('Invalid graded relevance')
    return int(grade==2)


def export(rows, docs, state, destination):
    qrels=[];resolved=[];pending=[];audit=[];channels={}
    bykey={d['itemKey']:d for d in docs}
    for row in rows:
        positive={k:[] for k in ('VERIFIED_CASE','CATEGORY_REFERENCE','OFFICIAL_GUIDE')}
        negative={k:[] for k in positive};unknown=[]
        labels=state['jobs'][f'{PRIMARY}|{row["queryKey"]}']['labels']
        for key,label in labels.items():
            rel=projection(label['grade']);channel=bykey[key]['itemType']
            if rel is None:unknown.append(key)
            else:
                qrels.append(dict(queryKey=row['queryKey'],itemKey=key,itemType=channel,relevance=rel,
                                  grade=label['grade'],judgmentSource='AI',model=state['jobs'][f'{PRIMARY}|{row["queryKey"]}']['model'],
                                  promptVersion=VERSION,confidence=label['confidence'],evidenceIds=label['evidenceIds']))
                (positive if rel else negative)[channel].append(key)
            other=state['jobs'][f'{MODELS[0]}|{row["queryKey"]}']['labels'][key]
            if label['grade']!=other['grade']:
                audit.append({'queryKey':row['queryKey'],'itemKey':key,'primaryGrade':label['grade'],
                              'otherGrade':other['grade'],'directEvidenceDisagreement':projection(label['grade'])!=projection(other['grade'])})
        if unknown:pending.append({'queryKey':row['queryKey'],'unassessableItemKeys':unknown})
        else:resolved.append({'queryKey':row['queryKey'],'groupId':row['groupId'],'splitProposal':row['splitProposal'],
                              'sourceTextSha256':row['sourceTextSha256'],'sourceCategory':'PUBLIC_POLICE_QUERY',
                              'relevantKeysByChannel':positive,'irrelevantKeysByChannel':negative,
                              'scope':'DIRECT_OFFICER_USEFULNESS_IN_FIXED_POOL',
                              'evidenceStatusGroundTruth':None,'caseNoMatchGroundTruth':None,'riskGroundTruth':None})
    for channel in ('VERIFIED_CASE','CATEGORY_REFERENCE','OFFICIAL_GUIDE'):
        keys=[d['itemKey'] for d in docs if d['itemType']==channel]
        result={}
        for split in ('dev','test'):
            pairs=[];unknown=0;exact=0;grade_counts={m:Counter() for m in MODELS}
            for r in (r for r in rows if r['splitProposal']==split):
                for key in keys:
                    grades=[state['jobs'][f'{m}|{r["queryKey"]}']['labels'][key]['grade'] for m in MODELS]
                    for m,g in zip(MODELS,grades):grade_counts[m][str(g)]+=1
                    projected=[projection(g) for g in grades]
                    if None in projected:unknown+=1
                    else:pairs.append(tuple(projected));exact+=grades[0]==grades[1]
            result[split]={'pairedBinaryAgreement':agreement(pairs),'gradeCounts':grade_counts,
                           'exactGradeAgreement':exact/len(pairs) if pairs else None,'unknownPairs':unknown,
                           'primaryPositiveQueries':sum(bool(r['relevantKeysByChannel'][channel]) for r in resolved if r['splitProposal']==split) if keys else None,
                           'primaryNoPositiveQueries':sum(not r['relevantKeysByChannel'][channel] for r in resolved if r['splitProposal']==split) if keys else None}
        channels[channel]=result
    destination.mkdir(parents=True,exist_ok=True)
    for name,records in [('qrels.jsonl',qrels),('resolved-queries.jsonl',resolved),('pending.jsonl',pending),('disagreements.jsonl',audit)]:
        text=''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in records)
        path=destination/name
        if path.exists() and path.read_text(encoding='utf-8')!=text:raise ValueError('Frozen label export differs')
        path.write_text(text,encoding='utf-8')
    return {'fullyResolvedQueries':len(resolved),'unresolvedQueries':len(pending),'resolvedPairs':len(qrels),
            'positivePairs':sum(r['relevance'] for r in qrels),'gradeDisagreements':len(audit),
            'directEvidenceDisagreements':sum(r['directEvidenceDisagreement'] for r in audit),
            'completeQuerySplitCounts':Counter(r['splitProposal'] for r in resolved),'channels':channels,
            'groupOverlap':len({r['groupId'] for r in resolved if r['splitProposal']=='dev'}&{r['groupId'] for r in resolved if r['splitProposal']=='test'})}


def run(args):
    source=Path('.local-data/police-structure-java-v1/validated-requests.jsonl')
    structure_report=Path('docs/results/police-structure-validation-v1.json')
    corpus=Path(__file__).with_name('officer_documents_v1.json')
    report=json.loads(structure_report.read_text(encoding='utf-8'))
    if digest(source)!=report['validatedOutputSha256'] or not report['javaReport']['success']:
        raise ValueError('Changed/unvalidated structured input')
    raw=[json.loads(s) for s in source.read_text(encoding='utf-8').splitlines() if s]
    docs=json.loads(corpus.read_text(encoding='utf-8'))['documents'];keys=[d['itemKey'] for d in docs]
    if len(raw)!=55 or len(keys)!=20 or len(set(keys))!=20:raise ValueError('Unexpected frozen pool')
    rows=[]
    for r in raw:
        text=r['request']['maskedText']
        if not r['request']['maskingCompleted'] or hashlib.sha256(text.encode()).hexdigest()!=r['sourceTextSha256']:
            raise ValueError('Privacy/source hash mismatch')
        rows.append({k:r[k] for k in ('queryKey','groupId','splitProposal','sourceTextSha256')}|
                    {'maskedText':text,'targetKeys':keys})
    binding={'version':VERSION,'queries':digest(source),'structureReport':digest(structure_report),'corpus':digest(corpus),
             'promptSha256':hashlib.sha256(SYSTEM.encode()).hexdigest(),
             'schemaSha256':hashlib.sha256(json.dumps(response_schema(['d'],{'s0':'text'}),sort_keys=True).encode()).hexdigest(),
             'models':list(MODELS),'primary':PRIMARY,'batchSize':5,'strictOutput':True,
             'projection':'grade2 positive; 0/1 negative for DIRECT relevance only; unknown excluded, query unresolved',
             'modelPolicy':'primary fixed before generation; disagreements audited, never auto-merged'}
    args.output.mkdir(parents=True,exist_ok=True);path=args.output/'checkpoint.json'
    state={'binding':binding,'jobs':{}}
    if path.exists():
        state=json.loads(path.read_text(encoding='utf-8'))
        if state['binding']!=binding:raise ValueError('Changed relevance protocol')
    save(args.output/'protocol.json',binding)
    failures=[]
    if not args.offline:
        with ThreadPoolExecutor(max_workers=3) as pool:
            pending={pool.submit(judge_batched,r,docs,m,5,True):f'{m}|{r["queryKey"]}' for r in rows for m in MODELS if f'{m}|{r["queryKey"]}' not in state['jobs']}
            for future in as_completed(pending):
                try:state['jobs'][pending[future]]=future.result();save(path,state)
                except Exception as e:failures.append({'job':pending[future],'errorType':type(e).__name__})
                if (len(state['jobs'])+len(failures))%20==0:print(f'Relevance jobs saved {len(state["jobs"])}; failures {len(failures)}',flush=True)
        save(args.output/'failures.json',failures)
    if len(state['jobs'])!=len(rows)*len(MODELS):raise RuntimeError('Incomplete labels; checkpoint retained, no qrels exported')
    for r in rows:
        units={f's{i//220}':r['maskedText'][i:i+220] for i in range(0,len(r['maskedText']),220)}
        for m in MODELS:
            labels=state['jobs'][f'{m}|{r["queryKey"]}']['labels']
            if set(labels)!=set(keys):raise ValueError('Missing labels')
            validate({'labels':[dict(itemKey=k,**v) for k,v in labels.items()]},keys,units)
    if digest(source)!=binding['queries'] or digest(corpus)!=binding['corpus']:raise ValueError('Inputs changed during labeling')
    result=export(rows,docs,state,args.output/'qrels')
    result.update(version=VERSION,binding=binding,queryCount=len(rows),documentCount=len(docs),
                  modelResponseUnits=len(state['jobs']),gradedLabelCount=len(rows)*len(docs)*len(MODELS),
                  actualModels=sorted({j['model'] for j in state['jobs'].values()}),
                  primaryModel=PRIMARY,labelStatus='FROZEN_AI_PROVISIONAL',humanGroundTruth=False,
                  accuracyClaim=False,retrievalEvaluationExecuted=False,productionKnowledgePromoted=False,
                  documentCounts=Counter(d['itemType'] for d in docs),
                  inputHashes={'queries':digest(source),'corpus':digest(corpus)},
                  limitations=['AI labels, not human ground truth; agreement is not accuracy',
                               'Small draft pool; no VERIFIED_CASE candidates or final evidence/risk labels',
                               'Same-provider models may share errors; primary fixed, no adjudication of disagreement',
                               'Test label generation is not a search strategy evaluation or performance claim'])
    save(args.output/'qrels/summary.json',result)
    result['outputHashes']={n:digest(args.output/'qrels'/n) for n in ('qrels.jsonl','resolved-queries.jsonl','pending.jsonl','disagreements.jsonl')}
    save(args.output/'summary.json',result);print(json.dumps(result,ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--offline',action='store_true');run(p.parse_args())
