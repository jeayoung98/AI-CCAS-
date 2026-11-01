"""Project a blinded review onto unchanged old documents for an apples-to-apples control."""
import argparse
from copy import deepcopy
import json
from pathlib import Path
from export_relevance import digest, export


def project(source_corpus,target_corpus,responses):
    full={d['itemKey']:d for d in source_corpus['documents']}
    subset={d['itemKey']:d for d in target_corpus['documents']}
    if not subset or not set(subset).issubset(full) or any(full[k]!=d for k,d in subset.items()):
        raise ValueError('Only unchanged subset documents can be projected')
    result=deepcopy(responses)
    for rec in result['reviews'].values():
        if set(rec['labels'])!=set(full): raise ValueError('Review keys differ')
        rec['labels']={k:rec['labels'][k] for k in subset}
        rec['completed']=all(v['judgment'] is not None for v in rec['labels'].values())
        rec['resolved']=all(v['judgment'] in ('RELEVANT','IRRELEVANT') for v in rec['labels'].values())
    return result


if __name__=='__main__':
    p=argparse.ArgumentParser()
    for name in ('source','privacy','source-corpus','target-corpus','responses','output'):
        p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args()
    if a.output.exists(): raise FileExistsError('Choose a new snapshot')
    load=lambda path:json.loads(path.read_text(encoding='utf-8'))
    data=load(a.responses)
    if data['corpusSha256']!=digest(a.source_corpus): raise ValueError('Source corpus mismatch')
    result=project(load(a.source_corpus),load(a.target_corpus),data)
    result['corpusSha256']=digest(a.target_corpus)
    result['projection']={'sourceResponsesSha256':digest(a.responses),'sourceCorpusSha256':digest(a.source_corpus),
                          'method':'Unchanged document subset; no relabeling'}
    a.output.mkdir(parents=True)
    target=a.output/'responses.json'
    target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    export(a.source,a.target_corpus,target,a.privacy,a.output/'qrels')
