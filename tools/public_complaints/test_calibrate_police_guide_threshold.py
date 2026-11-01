import unittest
from calibrate_police_guide_threshold import score,select


class PoliceCutoffTest(unittest.TestCase):
    def test_wrong_document_does_not_count_as_correct_support(self):
        rows=[{'queryKey':'q','rankings':{'BASELINE:OFFICIAL_GUIDE':[{'itemKey':'bad','cosine':.8}]}}]
        labels={'q':{'good':1,'bad':0}}
        result=score(rows,labels,.5)
        self.assertEqual(result['availableGuideRecall'],1)
        self.assertEqual(result['relevantGuideRetrievalAndAcceptanceRecall'],0)
        self.assertEqual(result['documentF05'],0)
        self.assertEqual(result['incorrectAcceptedDocuments'],1)

    def test_correct_sparse_selection_still_fails_minimum_support_gate(self):
        rows=[{'queryKey':'q','rankings':{'BASELINE:OFFICIAL_GUIDE':[{'itemKey':'good','cosine':.5}]}}]
        selected,_,qualified=select(rows,{'q':{'good':1}})
        self.assertEqual(selected['threshold'],.5)
        self.assertFalse(qualified)

    def test_precision_gate_is_applied_before_f_score_optimization(self):
        rows=[];labels={}
        for i in range(13):
            key=str(i);good=i<5 or 6<=i<9
            rows.append({'queryKey':key,'rankings':{'BASELINE:OFFICIAL_GUIDE':[{'itemKey':'hit','cosine':.6 if i<6 else .57}]}})
            labels[key]={'hit':int(good)}
            if i<5:labels[key].update({f'unretrieved{k}':1 for k in range(6)})
        selected,trials,qualified=select(rows,labels)
        self.assertTrue(qualified)
        self.assertEqual(selected['threshold'],.6)
        self.assertGreater(max(t['documentF05'] for t in trials),selected['documentF05'])
