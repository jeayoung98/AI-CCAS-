import unittest
from evaluate_guide_cutoff import evaluate, metrics

def row(key,split,doc,score):
    return {'queryKey':key,'split':split,'rankings':{'BASELINE:OFFICIAL_GUIDE':[{'itemKey':doc,'cosine':score}]}}

class GuideCutoffTest(unittest.TestCase):
    def test_test_labels_cannot_change_dev_selection(self):
        rows=[row('p','dev','a',.6),row('n','dev','b',.3),row('t','test','a',.5)]
        labels={'p':{'a':1},'n':{'b':0},'t':{'a':1}}
        before=evaluate(rows,labels)
        labels['t']['a']=0
        after=evaluate(rows,labels)
        self.assertEqual(before['selectedThreshold'],after['selectedThreshold'])
        self.assertNotEqual(before['testSelected']['confusion'],after['testSelected']['confusion'])

    def test_wrong_accepted_hit_is_reported_even_if_another_document_is_relevant(self):
        result=metrics([row('p','test','wrong',.8)],{'p':{'right':1,'wrong':0}},.7)
        self.assertEqual(result['availableGuideRecall'],1)
        self.assertEqual(result['acceptedHitPrecision'],0)
        self.assertEqual(result['queriesWithOnlyIrrelevantAcceptedGuides'],1)
        self.assertEqual(result['relevantGuideRetrievalAndAcceptanceRecall'],0)

    def test_single_class_dev_refuses_misleading_selection(self):
        with self.assertRaises(ValueError):
            evaluate([row('d','dev','a',.8),row('t','test','a',.8)],{'d':{'a':1},'t':{'a':1}})
