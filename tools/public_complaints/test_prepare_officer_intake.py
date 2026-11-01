import unittest
from prepare_officer_intake import prepare, source_identity


class OfficerIntakeTest(unittest.TestCase):
    def seed(self):
        return dict(sourceUrl='https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq=123&mode=2',
                    sourceKind='PUBLISHED_PUBLIC_QUERY', access='BODY_CHECKED', maskedText='휴대전화를 잃어버려 찾는 방법을 문의한다.',
                    privacyReview='ASSISTANT_REVIEWED_PARAPHRASE', redactionNotes=['개인 식별 정보 없음'], topic='LOST_ITEM')

    def test_same_source_cannot_be_split_by_tracking_parameters(self):
        row=self.seed()
        other=dict(row,sourceUrl=row['sourceUrl']+'&pageIndex=12')
        self.assertEqual(source_identity(row['sourceUrl']), source_identity(other['sourceUrl']))
        with self.assertRaises(ValueError):prepare([row,other],[])
        with self.assertRaises(ValueError):prepare([row],[dict(other,maskedText='다른 요약')])

    def test_faq_and_unchecked_search_snippets_are_not_real_queries(self):
        for change in ({'sourceKind':'FAQ'}, {'access':'SEARCH_RESULT_ONLY'}):
            with self.assertRaises(ValueError):prepare([dict(self.seed(),**change)],[])

    def test_contact_details_are_rejected_and_drafts_stay_unapproved(self):
        with self.assertRaises(ValueError):prepare([dict(self.seed(),maskedText='연락처 010-1234-5678')],[])
        row=prepare([self.seed()],[])[0]
        self.assertFalse(row['benchmarkEligible'])
        self.assertFalse(row['humanReviewed'])
        self.assertIsNone(row['expectedLabels'])
        self.assertEqual(row['splitProposal'],'UNASSIGNED')
