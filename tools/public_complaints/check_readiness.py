"""Read-only preparation report. Never certifies Java DTO validity or promotes data."""
import argparse
from collections import Counter
from datetime import datetime, timezone
from html import escape
import json
from pathlib import Path
import tempfile

from export_relevance import export

FIELDS = ('privacy', 'summary', 'intent', 'risk', 'currentness', 'completeness')
GROUND_TRUTH = ('expectedEvidenceStatus', 'expectedRiskSignalPresent',
                'noReliableSimilarCaseExpected', 'ambiguityExpected')


def inspect(source, corpus, responses, privacy):
    # Reuse the version/identity/privacy checks instead of trusting saved counters.
    with tempfile.TemporaryDirectory() as temporary:
        relevance = export(source, corpus, responses, privacy, Path(temporary) / 'qrels')
    rows = [json.loads(line) for line in source.read_text(encoding='utf-8').splitlines() if line]
    reviews = json.loads(privacy.read_text(encoding='utf-8'))
    by_id = {r['queryKey']: r for r in rows}
    approved = Counter()
    for key, review in reviews['reviews'].items():
        if key not in by_id or review.get('sourceTextSha256') != by_id[key]['maskedTextSha256']:
            raise ValueError('Unknown or stale content review')
        judgments = review.get('judgments', {})
        approved.update(field for field in FIELDS if judgments.get(field) == 'PASS')
    missing = Counter()
    truth = Counter()
    for row in rows:
        structured = row.get('draftStructuredComplaint') or {}
        for field in ('observedFacts', 'subjectMatters', 'requestedActions'):
            if not structured.get(field):
                missing[field] += 1
        if structured.get('riskSignals') is None:
            missing['riskSignals'] += 1
        truth.update(field for field in GROUND_TRUTH if row.get(field) is None)
    documents = json.loads(corpus.read_text(encoding='utf-8'))['documents']
    return {
        'generatedAt': datetime.now(timezone.utc).isoformat(),
        'reportKind': 'PREPARATION_AUDIT_NOT_INGESTION_AUTHORIZATION',
        'queryCount': len(rows),
        'contentReviewPassCounts': {field: approved[field] for field in FIELDS},
        'missingStructuredFieldCounts': dict(missing),
        'missingGroundTruthCounts': dict(truth),
        'knowledgeDocumentCount': len(documents),
        'knowledgeReviewPending': sum(d.get('reviewStatus') != 'VERIFIED' for d in documents),
        'knowledgeUsagePending': sum(d.get('usageReview') != 'APPROVED' for d in documents),
        'relevance': relevance,
        'sourceMutated': False, 'embeddingExecuted': False,
        'limitations': [
            'Missing-field counts are diagnostics, not full Java DTO validation',
            'PASS reviews do not fill missing structured fields or ground truth',
            'Complaint usage approval, split freeze, and ingestion remain separate checks',
            'Resolved labels cover only the supplied document pool',
        ],
    }


def render(report):
    r = report['relevance']
    rows = [
        ('개인정보 검수 통과', report['contentReviewPassCounts']['privacy'], report['queryCount'], '기존 응답을 유지합니다.'),
        ('문서 관련성 모두 확정', r['fullyResolvedQueries'], r['queryCount'], '관련성 화면에서 새 문서와 보류 항목을 판단합니다.'),
        ('검색 지식 검수 완료', report['knowledgeDocumentCount'] - report['knowledgeReviewPending'], report['knowledgeDocumentCount'], '문서 내용·버전·적재 적합성을 별도로 확인해야 합니다.'),
        ('검색 지식 이용 조건 확인', report['knowledgeDocumentCount'] - report['knowledgeUsagePending'], report['knowledgeDocumentCount'], '관련성 판단과 이용 조건 확인은 별개입니다.'),
    ]
    cards = ''.join(f'<section><h2>{escape(title)}</h2><strong>{done} / {total}</strong><p>{escape(note)}</p></section>' for title, done, total, note in rows)
    gaps = ''.join(f'<li>{escape(field)}: {count}건 누락</li>' for field, count in report['missingStructuredFieldCounts'].items())
    return f'''<!doctype html><html lang="ko"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>검색 평가 준비 현황</title><style>
body{{font-family:system-ui,sans-serif;max-width:1000px;margin:40px auto;padding:0 24px;background:#f5f7fb;color:#17223b;line-height:1.7}}
.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px}}section{{background:white;padding:24px;border-radius:12px}}h2{{font-size:18px}}strong{{font-size:28px}}a{{color:#164fc5}}</style>
<h1>검색 평가 준비 현황</h1><p>이미 끝낸 검수와 앞으로 필요한 작업을 구분했습니다. 실제 검색 성능 평가 결과는 아닙니다.</p>
<p>생성 시각: {escape(report['generatedAt'])} · 저장 파일의 정적 스냅샷입니다.</p><div class="cards">{cards}</div>
<h2>지금 판단할 항목</h2><p>검수를 시작한 {r['reviewedQueries']}건에서 새 문서 미선택 {r['unselectedPairs']}개,
판단 보류 {r['unsurePairs']}개가 남아 있습니다. 다른 {r['unreviewedQueries']}건은 관련성 미검수입니다.</p>
<p><a href="http://127.0.0.1:8765/relevance">문서 관련성 검수 열기</a></p>
<h2>구현·데이터 준비 작업</h2><p>구조화 초안에 아래 필드가 빠져 있습니다. 기존 통과 응답만으로 값을 추정하지 않습니다.</p><ul>{gaps}</ul>
<p>이는 필수 필드의 누락 점검이며 Java DTO 전체 유효성 검사를 대신하지 않습니다.
근거 상태·위험 여부·유사 사례 없음·모호성 정답, 민원 이용 조건과 dev/test 분리 확정도 별도로 준비해야 합니다.</p>
<p>이 보고서는 데이터를 수정하거나 GMS로 전송하지 않습니다.</p></html>'''


def run(source, corpus, responses, privacy, output):
    if output.exists():
        raise FileExistsError('Choose a new report directory')
    report = inspect(source, corpus, responses, privacy)
    output.mkdir(parents=True)
    (output / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'index.html').write_text(render(report), encoding='utf-8')
    return report


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=root/'.local-data/acrc-drafts-500-v2/drafts.jsonl')
    parser.add_argument('--corpus', type=Path, default=Path(__file__).with_name('relevance_documents_v2.json'))
    parser.add_argument('--responses', type=Path, default=root/'.local-data/acrc-human-review/relevance-responses-v2.json')
    parser.add_argument('--privacy', type=Path, default=root/'.local-data/acrc-human-review/responses.json')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = run(args.source, args.corpus, args.responses, args.privacy, args.output)
    print(json.dumps(result, ensure_ascii=False, indent=2))
