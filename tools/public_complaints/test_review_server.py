import json
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from pathlib import Path
from review_server import ReviewStore, make_server, FIELDS


class ReviewServerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / 'source.jsonl'
        self.source.write_text(json.dumps({'queryKey': 'test-1', 'maskedTextSha256': 'hash'}), encoding='utf-8')
        self.output = self.root / 'answers.json'
        self.store = ReviewStore(self.source, self.output)

    def tearDown(self):
        self.temp.cleanup()

    def payload(self):
        return {'queryKey': 'test-1', 'judgments': dict.fromkeys(FIELDS, 'UNSURE'),
                'notes': '판단 보류', 'reviewer': 'test', 'revision': 0}

    def test_save_resume_and_conflict(self):
        saved = self.store.save(self.payload())
        self.assertTrue(saved['completed'])
        self.assertNotIn('maskingCompleted', saved)
        self.assertEqual(ReviewStore(self.source, self.output).reviews['test-1']['notes'], '판단 보류')
        with self.assertRaises(RuntimeError):
            self.store.save(self.payload())
        self.assertEqual(json.loads(self.output.read_text(encoding='utf-8'))['reviews']['test-1']['revision'], 1)

    def test_partial_save_and_invalid_values(self):
        payload = self.payload()
        payload['judgments']['risk'] = None
        self.assertFalse(self.store.save(payload)['completed'])
        payload['judgments']['privacy'] = 'APPROVE_EVERYTHING'
        with self.assertRaises(ValueError):
            self.store.save(payload)

    def test_changed_dataset_refuses_old_answers(self):
        self.store.save(self.payload())
        self.source.write_text(self.source.read_text(encoding='utf-8') + '\n', encoding='utf-8')
        with self.assertRaises(ValueError):
            ReviewStore(self.source, self.output)

    def test_http_access_and_save(self):
        server = make_server(self.store, 0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{server.server_port}'
        try:
            html = urllib.request.urlopen(base).read().decode()
            token = html.split("const TOKEN='")[1].split("'")[0]
            for headers in ({}, {'X-Review-Token': token, 'Origin': 'https://example.invalid'}):
                request = urllib.request.Request(base + '/api/review', data=json.dumps(self.payload()).encode(), headers=headers)
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(request)
                self.assertEqual(caught.exception.code, 403)
            request = urllib.request.Request(base + '/api/review', data=json.dumps(self.payload()).encode(), headers={'X-Review-Token': token, 'Content-Type': 'application/json'})
            self.assertEqual(urllib.request.urlopen(request).status, 200)
            self.assertTrue(self.output.exists())
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == '__main__':
    unittest.main()
