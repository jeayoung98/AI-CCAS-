import unittest
from audit_officer_coverage import audit


class OfficerCoverageTest(unittest.TestCase):
    def rows(self):
        source = [{'queryKey': str(i), 'groupId': str(i), 'splitProposal': 'dev',
                   'sourceCategory': '경찰민원', 'maskedTextSha256': str(i)} for i in range(100)]
        structured = [dict(queryKey=r['queryKey'], groupId=r['groupId'], splitProposal='dev',
                           sourceTextSha256=r['maskedTextSha256'],
                           request={'structuredComplaint': {'subjectMatters': [{'code': 'OTHER'}]}}) for r in source]
        return source, structured

    def test_absent_subjects_and_channels_remain_visible(self):
        source, structured = self.rows()
        report = audit(source, structured, [])
        self.assertEqual(report['groups']['dev']['existingAiSubjectCodeCounts']['LOST_ITEM'], 0)
        self.assertEqual(report['corpusTypeCounts']['VERIFIED_CASE'], 0)

    def test_rejects_unmatched_source_hash(self):
        source, structured = self.rows()
        structured[0]['sourceTextSha256'] = 'different'
        with self.assertRaises(ValueError):
            audit(source, structured, [])
