"""Version-bound human relevance labels, separate from privacy/content reviews."""
import hashlib
import json
import os
import threading
from datetime import datetime, timezone


class RelevanceStore:
    def __init__(self, review_store, corpus, destination):
        self.query_store = review_store
        self.destination = destination
        self.lock = threading.Lock()
        self.corpus_digest = hashlib.sha256(corpus.read_bytes()).hexdigest()
        self.documents = json.loads(corpus.read_text(encoding='utf-8'))['documents']
        self.keys = {d['itemKey'] for d in self.documents}
        if not self.keys or len(self.keys) != len(self.documents):
            raise ValueError('Invalid corpus keys')
        self.reviews = {}
        if destination.exists():
            saved = json.loads(destination.read_text(encoding='utf-8'))
            if saved['datasetSha256'] != review_store.digest or saved['corpusSha256'] != self.corpus_digest:
                raise ValueError('Relevance dataset/corpus changed; use a new output path')
            self.reviews = saved['reviews']

    def snapshot(self):
        with self.query_store.lock:
            rows = [r for r in self.query_store.rows
                    if self.query_store.reviews.get(r['queryKey'], {}).get('judgments', {}).get('privacy') == 'PASS']
        with self.lock:
            return {'rows': rows, 'documents': self.documents, 'reviews': self.reviews,
                    'corpusSha256': self.corpus_digest, 'outputPath': str(self.destination)}

    def save(self, payload):
        key = payload.get('queryKey')
        if key not in self.query_store.by_id:
            raise ValueError('Unknown query')
        with self.query_store.lock:
            if self.query_store.reviews.get(key, {}).get('judgments', {}).get('privacy') != 'PASS':
                raise ValueError('Privacy review required')
        if payload.get('corpusSha256') != self.corpus_digest:
            raise RuntimeError('CORPUS_CHANGED')
        labels = payload.get('labels')
        if not isinstance(labels, dict) or set(labels) != self.keys:
            raise ValueError('Document keys mismatch')
        for label in labels.values():
            if not isinstance(label, dict) or set(label) != {'judgment', 'reason'}:
                raise ValueError('Invalid label')
            if label['judgment'] not in (None, 'RELEVANT', 'IRRELEVANT', 'UNSURE'):
                raise ValueError('Invalid judgment')
            if not isinstance(label['reason'], str) or len(label['reason']) > 3000:
                raise ValueError('Invalid reason')
        reviewer = payload.get('reviewer', '')
        if not isinstance(reviewer, str) or len(reviewer) > 80:
            raise ValueError('Invalid reviewer')
        with self.lock:
            previous = self.reviews.get(key, {})
            if payload.get('revision', 0) != previous.get('revision', 0):
                raise RuntimeError('CONFLICT')
            record = {'queryKey': key, 'sourceTextSha256': self.query_store.by_id[key]['maskedTextSha256'],
                      'labels': labels, 'reviewer': reviewer, 'revision': previous.get('revision', 0) + 1,
                      'updatedAt': datetime.now(timezone.utc).isoformat(),
                      'completed': all(v['judgment'] is not None for v in labels.values()),
                      'resolved': all(v['judgment'] in ('RELEVANT', 'IRRELEVANT') for v in labels.values())}
            updated = {**self.reviews, key: record}
            document = {'schemaVersion': 'relevance-review-v1', 'datasetSha256': self.query_store.digest,
                        'corpusSha256': self.corpus_digest, 'scope': 'LIMITED_DOCUMENT_POOL_ONLY',
                        'reviews': updated}
            self.destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.destination.with_suffix('.tmp')
            temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            os.replace(temporary, self.destination)
            self.reviews = updated
            return record
