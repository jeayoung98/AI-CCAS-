import unittest
from copy import deepcopy
from judge_relevance import merge, validate_labels, prepare_reviews, pending_keys


class AiRelevanceTests(unittest.TestCase):
    def test_source_ids_preserve_low_confidence_and_reject_invented_ids(self):
        label = {'itemKey': 'd', 'judgment': 'IRRELEVANT', 'reason': '다른 안내',
                 'evidenceIds': ['s0'], 'confidence': 'LOW'}
        result = validate_labels({'labels': [label]}, ['d'], '신청', {'s0': '신청'})['d']
        self.assertEqual(result['evidence'], '신청')
        self.assertEqual(result['confidence'], 'LOW')
        label['evidenceIds'] = ['invented']
        with self.assertRaises(ValueError):
            validate_labels({'labels': [label]}, ['d'], '신청', {'s0': '신청'})

    def test_all_queries_requires_matching_privacy_and_does_not_modify_input(self):
        human = {'reviews': {}}
        rows = {'q': {'maskedTextSha256': 'hash'}}
        docs = [{'itemKey': 'd'}]
        with self.assertRaises(ValueError):
            prepare_reviews(human, rows, docs, {}, True)
        approved = {'q': {'sourceTextSha256': 'hash', 'judgments': {'privacy': 'PASS'}}}
        result = prepare_reviews(human, rows, docs, approved, True)
        self.assertEqual(human, {'reviews': {}})
        self.assertEqual(pending_keys(result['reviews']['q']), ['d'])
        self.assertEqual(result['reviews']['q']['revision'], 0)

    def test_recheck_only_selects_low_ai_and_preserves_old_judgment(self):
        human = {'reviews': {'q': {'labels': {
            'a': {'judgment': 'RELEVANT', 'confidence': 'LOW'},
            'b': {'judgment': 'IRRELEVANT', 'judgmentSource': 'AI', 'confidence': 'LOW'}}}}}
        self.assertEqual(pending_keys(human['reviews']['q'], True), ['b'])
        ai = {'judgments': {'q': {'labels': {'b': {'judgment': 'RELEVANT', 'confidence': 'LOW'}},
                                  'model': 'test', 'promptVersion': 'test', 'createdAt': 'now'}}}
        result = merge(human, ai, True)
        self.assertEqual(result['reviews']['q']['labels']['b']['previousJudgment']['judgment'], 'IRRELEVANT')
        self.assertEqual(human['reviews']['q']['labels']['b']['judgment'], 'IRRELEVANT')

    def test_rejects_invented_evidence_and_duplicate_keys(self):
        label = {'itemKey': 'd', 'judgment': 'RELEVANT', 'reason': '안내', 'evidence': '신청', 'confidence': 'LOW'}
        self.assertEqual(validate_labels({'labels': [label]}, ['d'], '신청 안내')['d']['confidence'], 'LOW')
        invalid_quote = validate_labels({'labels': [label]}, ['d'], '다른 내용')['d']
        self.assertEqual(invalid_quote['evidence'], '')
        self.assertFalse(invalid_quote['evidenceVerified'])
        self.assertEqual(invalid_quote['confidence'], 'LOW')
        with self.assertRaises(ValueError):
            validate_labels({'labels': [label, label]}, ['d', 'e'], '신청 안내')

    def test_preserves_human_and_tracks_ai_source(self):
        human = {'reviews': {'q': {'labels': {'a': {'judgment': 'RELEVANT', 'reason': '사람'},
                                              'b': {'judgment': 'UNSURE', 'reason': '보류'}}}}}
        before = deepcopy(human)
        ai = {'judgments': {'q': {'labels': {'b': {'judgment': 'IRRELEVANT', 'reason': 'AI'}},
                                  'model': 'test', 'promptVersion': 'test', 'createdAt': 'now'}}}
        result = merge(human, ai)
        self.assertEqual(human, before)
        self.assertEqual(result['reviews']['q']['labels']['a'], before['reviews']['q']['labels']['a'])
        self.assertEqual(result['reviews']['q']['labels']['b']['judgmentSource'], 'AI')
        self.assertTrue(result['reviews']['q']['resolved'])
        ai['judgments']['q']['labels']['a'] = {'judgment': 'IRRELEVANT'}
        with self.assertRaises(ValueError):
            merge(human, ai)


if __name__ == '__main__':
    unittest.main()
