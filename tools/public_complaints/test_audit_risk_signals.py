import unittest
from audit_risk_signals import validate, compare

class RiskAuditTest(unittest.TestCase):
    def test_unknown_or_unlinked_signals_rejected(self):
        good={'signals':[{'code':'WEAPON','evidenceIds':['s0']}],'confidence':'LOW','reason':'불확실'}
        self.assertEqual(validate(good,{'s0':'test'})['signals'],good['signals'])
        with self.assertRaises(ValueError): validate(good,{'s1':'test'})
        good['signals'].append(good['signals'][0])
        with self.assertRaises(ValueError): validate(good,{'s0':'test'})

    def test_disagreement_is_not_reported_as_accuracy_or_mutated(self):
        rows=[{'queryKey':'q','request':{'structuredComplaint':{'riskSignals':[]}}}]
        summary,changes=compare(rows,{'q':{'signals':[{'code':'WEAPON'}],'confidence':'LOW'}})
        self.assertEqual(summary['exactSetAgreementRate'],0)
        self.assertEqual(summary['perCodeTransitions']['WEAPON']['0->1'],1)
        self.assertFalse(summary['accuracyClaim'])
        self.assertEqual(rows[0]['request']['structuredComplaint']['riskSignals'],[])
