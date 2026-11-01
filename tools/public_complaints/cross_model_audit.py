"""Targeted cross-model audit with agreement controls, never overwrite evaluation labels."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path
import random
import audit_risk_signals as risk
import judge_relevance as relevance
from blind_relevance import PROMPT
from export_relevance import digest

VERSION='cross-model-audit-v1'


def controls(positive,negative,count=50):
    rng=random.Random(20261029)
    positive=sorted(positive);negative=sorted(negative)
    selected=rng.sample(positive,min(count//2,len(positive)))
    return selected+rng.sample(negative,min(count-len(selected),len(negative)))


def selection(old,new,structured,risk_labels):
    disputed=[];positive=[];negative=[]
    for key in sorted(old['reviews']):
        before=old['reviews'][key];after=new['reviews'][key]
        if before['sourceTextSha256']!=after['sourceTextSha256']:raise ValueError('Changed query')
        for doc,label in before['labels'].items():
            pair=(key,doc)
            if label['judgment']!=after['labels'][doc]['judgment']:disputed.append(pair)
            elif label['judgment']=='RELEVANT':positive.append(pair)
            else:negative.append(pair)
    risk_disputed=[];rp=[];rn=[]
    for key,row in structured.items():
        before={s['code'] for s in row['request']['structuredComplaint']['riskSignals']}
        after={s['code'] for s in risk_labels[key]['signals']}
        if before!=after:risk_disputed.append(key)
        elif before:rp.append(key)
        else:rn.append(key)
    return {'relevanceDisputed':sorted(disputed),'relevanceControls':controls(positive,negative),
            'riskDisputed':sorted(risk_disputed),'riskControls':controls(rp,rn)}


def classify(before,blind,third):
    if before==blind:return 'CONFIRMS_AGREEMENT' if third==before else 'CHALLENGES_AGREEMENT'
    if third==before:return 'SUPPORTS_PREVIOUS'
    if third==blind:return 'SUPPORTS_BLIND'
    return 'DIFFERS_FROM_BOTH'


def report(selected,old,new,structured,risk_labels,state):
    detail=[];counts=defaultdict(Counter)
    for group in ('relevanceDisputed','relevanceControls'):
        for key,doc in selected[group]:
            third=state['relevance'][key]['labels'][doc]
            before=old['reviews'][key]['labels'][doc]['judgment'];blind=new['reviews'][key]['labels'][doc]['judgment']
            outcome=classify(before,blind,third['judgment']);counts[group][outcome]+=1
            detail.append({'kind':group,'queryKey':key,'itemKey':doc,'previous':before,'blind':blind,
                           'third':third['judgment'],'outcome':outcome,'confidence':third['confidence'],
                           'status':'RECHECK_REQUIRED' if third['confidence']=='LOW' or outcome=='CHALLENGES_AGREEMENT' else 'CROSS_MODEL_SUPPORT_NOT_GROUND_TRUTH'})
    for group in ('riskDisputed','riskControls'):
        for key in selected[group]:
            third=state['risk'][key]
            before={s['code'] for s in structured[key]['request']['structuredComplaint']['riskSignals']}
            blind={s['code'] for s in risk_labels[key]['signals']};actual={s['code'] for s in third['signals']}
            outcome=classify(before,blind,actual);counts[group][outcome]+=1
            detail.append({'kind':group,'queryKey':key,'previous':sorted(before),'blind':sorted(blind),'third':sorted(actual),
                           'outcome':outcome,'confidence':third['confidence'],
                           'status':'RECHECK_REQUIRED' if third['confidence']=='LOW' or outcome in ('CHALLENGES_AGREEMENT','DIFFERS_FROM_BOTH') else 'CROSS_MODEL_SUPPORT_NOT_GROUND_TRUTH'})
    return {'protocolVersion':VERSION,'binding':state['binding'],
            'selectionCounts':{k:len(v) for k,v in selected.items()},'outcomes':dict(counts),
            'confidenceCounts':{g:dict(Counter(d['confidence'] for d in detail if d['kind']==g)) for g in selected},
            'statusCounts':dict(Counter(d['status'] for d in detail)),
            'relevanceDisputesByDocument':dict(Counter(d['itemKey'] for d in detail if d['kind']=='relevanceDisputed')),
            'actualModels':sorted({v['model'] for kind in ('risk','relevance') for v in state[kind].values()}),
            'previousLabelsVisibleToJudge':False,'originalInputsChanged':False,'accuracyClaim':False,
            'limitations':['Targeted disagreements plus stratified controls; not an overall accuracy sample',
                'Different models from the same provider may share biases',
                'Two-model agreement is provisional support, not human ground truth',
                'No ranking or threshold tuning performed on audit results']},detail


def run(a):
    root=Path(__file__).resolve().parents[2]
    files={'source':root/'.local-data/acrc-drafts-500-v2/drafts.jsonl',
           'corpus':Path(__file__).with_name('relevance_documents_v3.json'),
           'oldCorpus':Path(__file__).with_name('relevance_documents_v2.json'),
           'previous':root/'.local-data/acrc-ai-relevance-v2/effective-responses.json',
           'blind':root/'.local-data/acrc-blind-v3/labels/effective-responses.json',
           'structured':root/'.local-data/acrc-structured-java-v1/validated-requests.jsonl',
           'risk':root/'.local-data/acrc-risk-audit-v1/judgments.json',
           'privacy':root/'.local-data/acrc-human-review/responses.json'}
    load=lambda k:json.loads(files[k].read_text(encoding='utf-8'))
    rows={r['queryKey']:r for r in (json.loads(s) for s in files['source'].read_text(encoding='utf-8').splitlines())}
    structured={r['queryKey']:r for r in (json.loads(s) for s in files['structured'].read_text(encoding='utf-8').splitlines())}
    old,new=load('previous'),load('blind');risk_state=load('risk');risk_labels=risk_state['judgments'];privacy=load('privacy')
    docs=load('corpus')['documents'];by_doc={d['itemKey']:d for d in docs}
    if len(rows)!=500 or set(rows)!=set(structured) or set(rows)!=set(risk_labels):raise ValueError('Incomplete inputs')
    if old['corpusSha256']!=digest(files['oldCorpus']) or new['corpusSha256']!=digest(files['corpus']):raise ValueError('Corpus mismatch')
    if any(by_doc.get(d['itemKey'])!=d for d in load('oldCorpus')['documents']):raise ValueError('Old document changed')
    if any(x['datasetSha256']!=digest(files['source']) for x in (old,new,privacy)):raise ValueError('Dataset mismatch')
    if risk_state['binding']['source']!=digest(files['structured']):raise ValueError('Risk snapshot mismatch')
    for key,row in rows.items():
        text_hash=hashlib.sha256(row['maskedText'].encode()).hexdigest()
        approval=privacy['reviews'].get(key,{})
        if text_hash!=row['maskedTextSha256'] or approval.get('sourceTextSha256')!=text_hash or approval.get('judgments',{}).get('privacy')!='PASS':raise ValueError('Privacy approval mismatch')
        if row['maskedText']!=structured[key]['request']['maskedText']:raise ValueError('Structured source mismatch')
    selected=selection(old,new,structured,risk_labels)
    binding={'hashes':{k:digest(v) for k,v in files.items()},'model':a.model,'version':VERSION}
    a.output.mkdir(parents=True,exist_ok=True);checkpoint=a.output/'judgments.json'
    state={'binding':binding,'relevance':{},'risk':{}}
    if checkpoint.exists():
        state=json.loads(checkpoint.read_text(encoding='utf-8'))
        if state['binding']!=binding:raise ValueError('Changed audit; choose a new directory')
    relevance.save(a.output/'selection.json',selected)
    relevance.SYSTEM=PROMPT;relevance.PROMPT_VERSION=VERSION
    grouped=defaultdict(set)
    for key,doc in selected['relevanceDisputed']+selected['relevanceControls']:grouped[key].add(doc)
    jobs=[]
    for key,keys in sorted(grouped.items()):
        if key not in state['relevance']:
            rec={'labels':{d:{'judgment':None} for d in sorted(keys)}}
            jobs.append(('relevance',key,lambda key=key,rec=rec:relevance.judge(rows[key],rec,docs,a.model)))
    for key in sorted(set(selected['riskDisputed']+selected['riskControls'])):
        if key not in state['risk']:jobs.append(('risk',key,lambda key=key:risk.judge(rows[key]['maskedText'],a.model)))
    failures=[]
    with ThreadPoolExecutor(max_workers=3) as pool:
        pending={pool.submit(fn):(kind,key) for kind,key,fn in jobs}
        for future in as_completed(pending):
            kind,key=pending[future]
            try:state[kind][key]=future.result();relevance.save(checkpoint,state)
            except Exception as e:failures.append({'kind':kind,'queryKey':key,'errorType':type(e).__name__})
            done=len(state['relevance'])+len(state['risk'])
            if (done+len(failures))%20==0:print(f'Cross-model jobs saved: {done}; failures: {len(failures)}',flush=True)
    relevance.save(a.output/'failures.json',failures)
    if failures:raise RuntimeError('Audit incomplete; checkpoint preserved')
    if binding['hashes']!={k:digest(v) for k,v in files.items()}:raise ValueError('Inputs changed during audit')
    summary,details=report(selected,old,new,structured,risk_labels,state)
    relevance.save(a.output/'summary.json',summary);relevance.save(a.output/'comparison.json',details)
    print(json.dumps(summary,ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True)
    p.add_argument('--model',default='gpt-4o');run(p.parse_args())
