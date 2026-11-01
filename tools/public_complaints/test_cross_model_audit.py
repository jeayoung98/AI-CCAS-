import unittest
from cross_model_audit import controls,classify,selection

class CrossModelAuditTest(unittest.TestCase):
    def test_controls_are_deterministic_stratified_and_unique(self):
        a=controls(list(range(3)),list(range(100,200)))
        self.assertEqual(len(a),50);self.assertEqual(len(set(a)),50)
        self.assertEqual(a,controls(list(range(3)),list(range(100,200))))
        self.assertTrue({0,1,2}.issubset(a))

    def test_third_judge_can_disagree_with_both_risk_sets(self):
        self.assertEqual(classify({'WEAPON'},set(),{'CHILD_ABUSE'}),'DIFFERS_FROM_BOTH')
        self.assertEqual(classify(1,1,0),'CHALLENGES_AGREEMENT')
        self.assertEqual(classify(1,0,0),'SUPPORTS_BLIND')

    def test_disputes_and_controls_do_not_overlap_or_mutate_labels(self):
        old={'reviews':{'q':{'sourceTextSha256':'h','labels':{'d':{'judgment':'RELEVANT'}}}}}
        new={'reviews':{'q':{'sourceTextSha256':'h','labels':{'d':{'judgment':'IRRELEVANT'}}}}}
        rows={'q':{'request':{'structuredComplaint':{'riskSignals':[]}}}}
        risk={'q':{'signals':[{'code':'WEAPON'}]}}
        result=selection(old,new,rows,risk)
        self.assertEqual(result['relevanceDisputed'],[('q','d')]);self.assertEqual(result['relevanceControls'],[])
        self.assertEqual(result['riskDisputed'],['q']);self.assertEqual(result['riskControls'],[])
        self.assertEqual(old['reviews']['q']['labels']['d']['judgment'],'RELEVANT')
