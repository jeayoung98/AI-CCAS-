import unittest
from copy import deepcopy
from judge_relevance import merge, validate_labels


class AiRelevanceTests(unittest.TestCase):
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
