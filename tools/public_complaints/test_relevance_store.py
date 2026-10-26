import json
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from pathlib import Path
from review_server import ReviewStore, make_server, FIELDS
from relevance_store import RelevanceStore


class RelevanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / 'queries.jsonl'
        self.source.write_text(json.dumps({'queryKey': 'q', 'maskedTextSha256': 'hash'}), encoding='utf-8')
        self.privacy = ReviewStore(self.source, self.root / 'privacy.json')
        self.privacy.save({'queryKey': 'q', 'judgments': dict.fromkeys(FIELDS, 'PASS'), 'revision': 0})
        self.corpus = self.root / 'corpus.json'
        self.corpus.write_text(json.dumps({'documents': [{'itemKey': 'd'}]}), encoding='utf-8')
        self.store = RelevanceStore(self.privacy, self.corpus, self.root / 'relevance.json')

    def tearDown(self):
        self.temp.cleanup()

    def payload(self, judgment=None):
        return {'queryKey': 'q', 'corpusSha256': self.store.corpus_digest, 'revision': 0,
                'labels': {'d': {'judgment': judgment, 'reason': ''}}}

    def test_pending_unsure_and_resolved_are_distinct(self):
        for n, judgment in enumerate((None, 'UNSURE', 'IRRELEVANT')):
            payload = self.payload(judgment)
            payload['revision'] = n
            saved = self.store.save(payload)
            self.assertEqual(saved['completed'], judgment is not None)
            self.assertEqual(saved['resolved'], judgment == 'IRRELEVANT')
            self.assertNotIn('noReliableSimilarCaseExpected', saved)

    def test_resume_and_preserve_original_review(self):
        before = self.privacy.destination.read_bytes()
        self.store.save(self.payload('RELEVANT'))
        loaded = RelevanceStore(self.privacy, self.corpus, self.store.destination)
        self.assertEqual(loaded.reviews['q']['labels']['d']['judgment'], 'RELEVANT')
        self.assertEqual(self.privacy.destination.read_bytes(), before)
        with self.assertRaises(RuntimeError):
            self.store.save(self.payload('IRRELEVANT'))

    def test_corpus_change_invalidates_labels(self):
        self.store.save(self.payload('RELEVANT'))
        self.corpus.write_text('{"documents":[{"itemKey":"d","text":"changed"}]}', encoding='utf-8')
        with self.assertRaises(ValueError):
            RelevanceStore(self.privacy, self.corpus, self.store.destination)
        payload = self.payload()
        payload['corpusSha256'] = 'stale'
        with self.assertRaises(RuntimeError):
            self.store.save(payload)

    def test_unknown_document_and_missing_privacy_rejected(self):
        payload = self.payload()
        payload['labels'] = {'unknown': {'judgment': 'RELEVANT', 'reason': ''}}
        with self.assertRaises(ValueError):
            self.store.save(payload)
        self.privacy.reviews['q']['judgments']['privacy'] = 'UNSURE'
        self.assertEqual(self.store.snapshot()['rows'], [])
        with self.assertRaises(ValueError):
            self.store.save(self.payload())

    def test_http_routes_and_persistence(self):
        server = make_server(self.privacy, 0, self.store)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{server.server_port}'
        try:
            html = urllib.request.urlopen(base + '/relevance').read().decode()
            token = html.split("const TOKEN='")[1].split("'")[0]
            headers = {'X-Review-Token': token, 'Content-Type': 'application/json'}
            request = urllib.request.Request(base + '/api/relevance', headers=headers)
            self.assertEqual(len(json.load(urllib.request.urlopen(request))['documents']), 1)
            request = urllib.request.Request(base + '/api/relevance', data=json.dumps(self.payload('UNSURE')).encode(), headers=headers)
            self.assertEqual(urllib.request.urlopen(request).status, 200)
            request.add_header('Origin', 'https://example.invalid')
            with self.assertRaises(urllib.error.HTTPError) as caught:
                urllib.request.urlopen(request)
            self.assertEqual(caught.exception.code, 403)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == '__main__':
    unittest.main()
