import hashlib
import unittest
from structure_police_candidates import privacy_gate


class PoliceStructureGateTest(unittest.TestCase):
    def data(self):
        job={'review':{'privacy':'PASS'},'model':'test-model'}
        row={'queryKey':'q','maskedText':'비식별 질의','maskedTextSha256':hashlib.sha256('비식별 질의'.encode()).hexdigest(),
             'screening':job,'screeningDisposition':'SPECIFIC_CANDIDATE'}
        return row,{'jobs':{'q':job}}

    def test_ai_provenance_is_explicit_and_changed_source_is_rejected(self):
        row,state=self.data();result=privacy_gate([row],state)
        self.assertFalse(result['q']['humanReviewed'])
        self.assertEqual(result['q']['judgmentSource'],'AI_SCREENING')
        row['maskedText']='다른 질의'
        with self.assertRaises(ValueError):privacy_gate([row],state)

    def test_privacy_hold_and_background_never_pass(self):
        row,state=self.data()
        row['screeningDisposition']='NOT_PRIMARY_EVALUATION_CANDIDATE'
        with self.assertRaises(ValueError):privacy_gate([row],state)
        row,state=self.data();state['jobs']['q']['review']['privacy']='UNASSESSABLE'
        with self.assertRaises(ValueError):privacy_gate([row],state)
