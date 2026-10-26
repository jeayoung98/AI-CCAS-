import unittest
from draft_review import extra_redact, make_draft


def row(text, flags=None):
    return {'queryKey': 'test-only', 'maskedText': text, 'sourceTitleMasked': '가상 테스트',
            'sourceUrl': 'https://www.acrc.go.kr/', 'sourceCategory': '경찰민원',
            'sourceMaskedTextSha256': 'test-only', 'groupId': 'group-test',
            'splitProposal': 'dev', 'reviewFlags': flags or []}


class DraftReviewTests(unittest.TestCase):
    def test_additional_redaction_and_public_number_preservation(self):
        text, changes = extra_redact('김 ○ ○ (1935 생) 36 세 ○ ○ 길 39에서 112로 신고하였다.')
        for value in ('김 ○ ○', '1935 생', '36 세', '길 39'):
            self.assertNotIn(value, text)
        self.assertIn('112로 신고하였다.', text)
        self.assertEqual(len(changes), 4)

    def test_historical_report_is_not_current_report_request(self):
        draft = make_draft(row('경찰에 신고하였다. 부당한 업무처리를 조사해 달라.'))
        self.assertIsNone(draft['draftStructuredComplaint']['requestedActions'])

    def test_no_keyword_does_not_become_no_risk_ground_truth(self):
        draft = make_draft(row('처리 상황을 알려 달라.'))
        self.assertIsNone(draft['draftStructuredComplaint']['riskSignals'])
        self.assertIsNone(draft['expectedRiskSignalPresent'])
        self.assertIsNone(draft['noReliableSimilarCaseExpected'])
        self.assertFalse(draft['maskingCompleted'])
        self.assertFalse(draft['humanReviewed'])

    def test_negated_keyword_is_only_uncertain_candidate(self):
        draft = make_draft(row('흉기를 들었다는 말은 사실이 아니다.'))
        candidate = draft['draftStructuredComplaint']['riskSignals'][0]
        self.assertEqual(candidate['certainty'], 'UNCERTAIN')
        self.assertEqual(candidate['evidence'], draft['maskedText'])
        self.assertIn('NEGATION_AND_INTENT_UNREVIEWED', draft['reviewFlags'])
        self.assertIsNone(draft['expectedRiskSignalPresent'])

    def test_old_assault_not_assault_in_progress(self):
        draft = make_draft(row('과거 폭행 사건의 처리를 문의하였다.'))
        self.assertIsNone(draft['draftStructuredComplaint']['riskSignals'])

    def test_truncation_remains_unresolved(self):
        draft = make_draft(row('신청인은 처리 상황을 문의하였으나', ['TRUNCATED_EXCERPT']))
        self.assertIn('절단된 뒷부분 원문 확인', draft['draftStructuredComplaint']['missingInformation'])
        self.assertIsNone(draft['draftStructuredComplaint']['observedFacts'])
        self.assertFalse(draft['splitFrozen'])

    def test_all_candidate_evidence_occurs_in_sanitized_text(self):
        draft = make_draft(row('김 ○ ○ 은 흉기 사건에 관해 문의하였다. 처리 상황을 알려 달라.'))
        self.assertNotIn('김 ○ ○', draft['maskedText'])
        for field in ('riskSignals', 'subjectMatters', 'requestedActions'):
            for candidate in draft['draftStructuredComplaint'][field] or []:
                self.assertIn(candidate['evidence'], draft['maskedText'])

    def test_empty_text_rejected(self):
        with self.assertRaises(ValueError):
            make_draft(row(' '))


if __name__ == '__main__':
    unittest.main()
