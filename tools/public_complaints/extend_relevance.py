"""Carry judgments only for byte-equivalent document objects into an expanded pool."""
import hashlib
import json
from pathlib import Path


def migrate(old_corpus, new_corpus, source, destination):
    if destination.exists():
        raise FileExistsError('Do not overwrite existing reviews')
    old = {d['itemKey']: d for d in json.loads(old_corpus.read_text(encoding='utf-8'))['documents']}
    new = {d['itemKey']: d for d in json.loads(new_corpus.read_text(encoding='utf-8'))['documents']}
    if not set(old) < set(new) or any(new[k] != v for k, v in old.items()):
        raise ValueError('Only strictly additive unchanged documents can carry judgments')
    data = json.loads(source.read_text(encoding='utf-8'))
    if data['corpusSha256'] != hashlib.sha256(old_corpus.read_bytes()).hexdigest():
        raise ValueError('Source review corpus mismatch')
    carried = 0
    for record in data['reviews'].values():
        if set(record['labels']) != set(old):
            raise ValueError('Source document keys mismatch')
        carried += len(record['labels'])
        record['labels'].update({k: {'judgment': None, 'reason': ''} for k in new if k not in old})
        record['completed'] = False
        record['resolved'] = False
    data['corpusSha256'] = hashlib.sha256(new_corpus.read_bytes()).hexdigest()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    audit = {'oldCorpusSha256': hashlib.sha256(old_corpus.read_bytes()).hexdigest(),
             'newCorpusSha256': data['corpusSha256'], 'sourceReviewsSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
             'carriedPairs': carried, 'newDocumentKeys': sorted(set(new)-set(old)),
             'method': 'Identical document objects only; new judgments null; original review timestamps retained'}
    destination.with_suffix('.migration.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return audit


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    here = Path(__file__).parent
    print(json.dumps(migrate(here/'relevance_documents.json', here/'relevance_documents_v2.json',
                             root/'.local-data/acrc-human-review/relevance-responses.json',
                             root/'.local-data/acrc-human-review/relevance-responses-v2.json'), ensure_ascii=False, indent=2))
