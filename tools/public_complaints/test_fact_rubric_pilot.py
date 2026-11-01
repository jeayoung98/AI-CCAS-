import unittest
from fact_rubric_pilot import grade,validate,controls

class FactRubricTest(unittest.TestCase):
    def test_absent_fact_allows_empty_evidence_but_true_requires_it(self):
        value={'administrativeGrievance':False,'asksProcedureNow':False,
               'administrativeEvidenceIds':[],'procedureEvidenceIds':[],
               'procedureQuote':'','confidence':'HIGH','reason':'absent'}
        validate(value,'actual',{'s0':'actual'})
        value['administrativeGrievance']=True
        with self.assertRaises(ValueError):validate(value,'actual',{'s0':'actual'})
        value['administrativeGrievance']=None
        value['procedureEvidenceIds']=['unknown']
        with self.assertRaises(ValueError):validate(value,'actual',{'s0':'actual'})

    def test_outcome_request_is_background_not_procedure(self):
        self.assertEqual(grade({'administrativeGrievance':True,'asksProcedureNow':False}),[2,1])
        self.assertEqual(grade({'administrativeGrievance':False,'asksProcedureNow':True}),[0,0])
        self.assertEqual(grade({'administrativeGrievance':True,'asksProcedureNow':None}),[2,'UNASSESSABLE'])

    def test_procedure_needs_exact_source_quote(self):
        value={'administrativeGrievance':True,'asksProcedureNow':True,'administrativeEvidenceIds':['s0'],
               'procedureEvidenceIds':['s0'],'procedureQuote':'invented','confidence':'HIGH','reason':'test'}
        with self.assertRaises(ValueError):validate(value,'actual',{'s0':'actual'})
        value['procedureQuote']='actual'
        self.assertEqual(validate(value,'actual',{'s0':'actual'})['procedureQuote'],'actual')
        value['asksProcedureNow']=False
        with self.assertRaises(ValueError):validate(value,'actual',{'s0':'actual'})

    def test_synthetic_controls_are_separate_and_cover_all_grades(self):
        rows=controls();self.assertEqual(len(rows),24)
        self.assertTrue(all(r['kind']=='synthetic' for r in rows))
        self.assertEqual(len({r['queryKey'] for r in rows}),24)
        self.assertEqual({tuple(r['expectedGrades']) for r in rows},{(2,2),(2,1),(0,0)})
