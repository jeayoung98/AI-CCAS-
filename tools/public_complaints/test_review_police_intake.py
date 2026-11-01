import unittest
from review_police_intake import disposition, validate, prepare


class IntakeScreenTest(unittest.TestCase):
    def review(self):
        return dict(privacy='PASS',officerUse='USEFUL',questionKind='SPECIFIC_SITUATION',
                    subjectCodes=['LOST_ITEM'],evidenceIds=['s0'],confidence='HIGH',reason='test')

    def test_privacy_and_unknown_never_become_primary_candidates(self):
        r=self.review();self.assertEqual(disposition(r),'SPECIFIC_CANDIDATE')
        for p in ('NEEDS_REDACTION','UNASSESSABLE'):
            self.assertEqual(disposition(dict(r,privacy=p)),'PRIVACY_HOLD')
        self.assertEqual(disposition(dict(r,questionKind='GENERAL_PROCEDURE')),'GENERAL_OR_EXAMPLE_CANDIDATE')
        self.assertEqual(disposition(dict(r,confidence='LOW')),'UNCERTAIN')

    def test_invented_evidence_and_final_route_codes_rejected(self):
        r=self.review();validate(r,{'s0':'text'})
        for change in ({'evidenceIds':['made-up']},{'subjectCodes':['FINAL_112']},{'subjectCodes':[]}):
            with self.assertRaises(ValueError):validate(dict(r,**change),{'s0':'text'})

    def test_preflight_preserves_lineage_and_blocks_names(self):
        row=prepare({'maskedText':'홍길동 씨가 휴대전화를 분실했다.','maskedTextSha256':'old'})
        self.assertTrue(row['preflightBlocked'])
        self.assertEqual(row['inputTextSha256'],'old')
