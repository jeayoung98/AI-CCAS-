"""Loopback-only review UI. Stores user judgments; never promotes source records."""
import argparse
import hashlib
import json
import os
import secrets
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

FIELDS = ('privacy', 'summary', 'intent', 'risk', 'currentness', 'completeness')
CHOICES = ('PASS', 'CHANGE', 'UNSURE')
ROOT = Path(__file__).resolve().parents[2]


class ReviewStore:
    def __init__(self, source, destination):
        self.rows = [json.loads(line) for line in source.read_text(encoding='utf-8').splitlines() if line]
        self.by_id = {r['queryKey']: r for r in self.rows}
        self.digest = hashlib.sha256(source.read_bytes()).hexdigest()
        self.destination = destination
        self.lock = threading.Lock()
        self.reviews = {}
        if destination.exists():
            saved = json.loads(destination.read_text(encoding='utf-8'))
            if saved['datasetSha256'] != self.digest:
                raise ValueError('Dataset changed: use a new review output path')
            self.reviews = saved['reviews']

    def save(self, payload):
        key = payload.get('queryKey')
        if key not in self.by_id:
            raise ValueError('Unknown case')
        judgments = payload.get('judgments')
        if not isinstance(judgments, dict) or set(judgments) != set(FIELDS):
            raise ValueError('Invalid judgment fields')
        if any(v not in (*CHOICES, None) for v in judgments.values()):
            raise ValueError('Invalid judgment value')
        notes, reviewer = payload.get('notes', ''), payload.get('reviewer', '')
        if not isinstance(notes, str) or len(notes) > 5000 or not isinstance(reviewer, str) or len(reviewer) > 80:
            raise ValueError('Invalid notes or reviewer')
        with self.lock:
            previous = self.reviews.get(key, {})
            if payload.get('revision', 0) != previous.get('revision', 0):
                raise RuntimeError('CONFLICT')
            record = {'queryKey': key, 'sourceTextSha256': self.by_id[key]['maskedTextSha256'],
                      'judgments': judgments, 'notes': notes, 'reviewer': reviewer,
                      'revision': previous.get('revision', 0) + 1,
                      'updatedAt': datetime.now(timezone.utc).isoformat(),
                      'completed': all(v in CHOICES for v in judgments.values())}
            updated = {**self.reviews, key: record}
            document = {'schemaVersion': 'human-review-ui-v1', 'datasetSha256': self.digest,
                        'sourceCount': len(self.rows), 'reviews': updated}
            self.destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.destination.with_suffix('.tmp')
            temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            os.replace(temporary, self.destination)
            self.reviews = updated
            return record


def make_server(store, port=8765, relevance=None):
    token = secrets.token_urlsafe(32)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # No complaint text, tokens, or notes in request logs.

        def reply(self, status, value, content_type='application/json; charset=utf-8'):
            data = value.encode('utf-8') if isinstance(value, str) else json.dumps(value, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; connect-src 'self'")
            self.end_headers()
            self.wfile.write(data)

        def allowed(self, authenticated=False):
            host = f'127.0.0.1:{self.server.server_port}'
            origin = self.headers.get('Origin')
            return (self.headers.get('Host') == host
                    and (origin is None or origin == 'http://' + host)
                    and (not authenticated or self.headers.get('X-Review-Token') == token))

        def do_GET(self):
            if not self.allowed():
                return self.reply(403, {'error': 'Access denied'})
            path = urlparse(self.path).path
            if path == '/relevance' and relevance is not None:
                html = Path(__file__).with_name('relevance.html').read_text(encoding='utf-8')
                return self.reply(200, html.replace('__TOKEN__', token), 'text/html; charset=utf-8')
            if path == '/api/relevance' and relevance is not None and self.allowed(True):
                return self.reply(200, relevance.snapshot())
            if path == '/':
                html = Path(__file__).with_name('review.html').read_text(encoding='utf-8')
                return self.reply(200, html.replace('__TOKEN__', token), 'text/html; charset=utf-8')
            if path == '/api/data' and self.allowed(True):
                with store.lock:
                    return self.reply(200, {'rows': store.rows, 'reviews': store.reviews,
                                           'outputPath': str(store.destination)})
            return self.reply(404, {'error': 'Not found'})

        def do_POST(self):
            if not self.allowed(True):
                return self.reply(403, {'error': 'Access denied'})
            if self.path not in ('/api/review', '/api/relevance') or (self.path == '/api/relevance' and relevance is None):
                return self.reply(404, {'error': 'Not found'})
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 32000:
                    raise ValueError('Invalid request size')
                payload = json.loads(self.rfile.read(length))
                if not isinstance(payload, dict):
                    raise ValueError('Invalid request')
                result = (relevance if self.path == '/api/relevance' else store).save(payload)
                self.reply(200, result)
            except (ValueError, TypeError):
                self.reply(400, {'error': 'Invalid review data'})
            except RuntimeError:
                self.reply(409, {'error': 'Another tab changed this review. Reload before saving.'})
            except OSError:
                self.reply(500, {'error': 'File save failed. Keep this page open and retry.'})

    return ThreadingHTTPServer(('127.0.0.1', port), Handler)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, default=ROOT / '.local-data/acrc-drafts-500-v2/drafts.jsonl')
    parser.add_argument('--output', type=Path, default=ROOT / '.local-data/acrc-human-review/responses.json')
    parser.add_argument('--port', type=int, default=8765)
    args = parser.parse_args()
    from relevance_store import RelevanceStore
    store = ReviewStore(args.input, args.output)
    relevance = RelevanceStore(store, Path(__file__).with_name('relevance_documents_v2.json'),
                               args.output.with_name('relevance-responses-v2.json'))
    server = make_server(store, args.port, relevance)
    print(f'Review UI: http://127.0.0.1:{server.server_port}', flush=True)
    server.serve_forever()
