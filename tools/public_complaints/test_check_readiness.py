import json
import unittest

from check_readiness import inspect, run
import test_export_relevance


class ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.fixture = test_export_relevance.ExportTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.tearDown)
        f = self.fixture
        self.inputs = (f.source, f.corpus, f.responses, f.privacy)

    def test_reports_missing_fields_without_promoting_reviewed_data(self):
        before = [p.read_bytes() for p in self.inputs]
        report = inspect(*self.inputs)
        self.assertEqual(report['contentReviewPassCounts']['privacy'], 3)
        self.assertEqual(report['missingStructuredFieldCounts']['observedFacts'], 3)
        self.assertEqual(report['missingGroundTruthCounts']['expectedRiskSignalPresent'], 3)
        self.assertEqual(report['knowledgeReviewPending'], 1)
        self.assertEqual(report['knowledgeUsagePending'], 1)
        self.assertEqual(report['relevance']['fullyResolvedQueries'], 1)
        self.assertFalse(report['embeddingExecuted'])
        self.assertEqual(before, [p.read_bytes() for p in self.inputs])

    def test_checks_stale_privacy_review_even_without_relevance_review(self):
        f = self.fixture
        value = json.loads(f.privacy.read_text(encoding='utf-8'))
        value['reviews']['c']['sourceTextSha256'] = 'stale'
        f.privacy.write_text(json.dumps(value), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'stale content review'):
            inspect(*self.inputs)

    def test_report_files_are_aggregate_only_and_never_overwritten(self):
        destination = self.fixture.root / 'report'
        result = run(*self.inputs, destination)
        self.assertEqual(json.loads((destination/'summary.json').read_text(encoding='utf-8')), result)
        html = (destination/'index.html').read_text(encoding='utf-8')
        self.assertIn('http://127.0.0.1:8765/relevance', html)
        self.assertNotIn('sourceTextSha256', html)
        self.assertNotIn('queryKey', html)
        with self.assertRaises(FileExistsError):
            run(*self.inputs, destination)


if __name__ == '__main__':
    unittest.main()
