import unittest
from graded_rubric_pilot import agreement,select_dev,validate,summarize,MODELS,TARGETS

class GradedRubricTest(unittest.TestCase):
    def test_agreement_exposes_all_negative_collapse(self):
        score=agreement([(0,0)]*10)
        self.assertEqual(score['agreement'],1)
        self.assertIsNone(score['kappa']);self.assertIsNone(score['positiveAgreement'])
        self.assertEqual(agreement([(1,0),(0,1)])['kappa'],-1)

    def test_dev_only_selection_and_group_leakage(self):
        rows=[{'queryKey':str(i),'groupId':str(i),'splitProposal':'dev'} for i in range(100)]
        rows.append({'queryKey':'test','groupId':'test','splitProposal':'test'})
        self.assertEqual(len(select_dev(rows)),100)
        rows[-1]['groupId']='0'
        with self.assertRaises(ValueError):select_dev(rows)

    def test_unknown_not_silently_converted_to_negative_and_evidence_required(self):
        labels=[{'itemKey':d,'grade':'UNASSESSABLE','reason':'부족','evidenceIds':['s0'],'confidence':'LOW'} for d in TARGETS]
        self.assertEqual(validate({'labels':labels},{'s0':'text'},'new')[TARGETS[0]]['grade'],'UNASSESSABLE')
        labels[0]['grade']=True
        with self.assertRaises(ValueError):validate({'labels':labels},{'s0':'text'},'new')
        labels[0]['grade']=2;labels[0]['evidenceIds']=['missing']
        with self.assertRaises(ValueError):validate({'labels':labels},{'s0':'text'},'new')

    def test_unknown_queries_are_excluded_from_both_paired_comparisons(self):
        state={'binding':{},'jobs':{}}
        for arm in ('old','new'):
            for model in MODELS:
                labels={d:({'judgment':'RELEVANT'} if arm=='old' else {'grade':'UNASSESSABLE' if model==MODELS[0] else 2}) for d in TARGETS}
                state['jobs'][f'{arm}|{model}|q']={'labels':labels,'model':model}
        r=summarize([{'queryKey':'q'}],state)['results'][TARGETS[0]]
        self.assertEqual(r['oldBinary']['count'],1)
        self.assertEqual(r['newDirectEvidence']['count'],0)
        self.assertEqual(r['oldBinaryOnSameAssessedQueries']['count'],0)
        self.assertIsNone(r['newExactGradeAgreementOnAssessed'])
