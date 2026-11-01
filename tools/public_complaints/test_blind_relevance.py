import json
from pathlib import Path
import tempfile
import unittest
from blind_relevance import seed, compare


class BlindRelevanceTest(unittest.TestCase):
    def test_seed_contains_no_old_labels_and_refuses_changed_input(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t); source=p/'queries'; corpus=p/'corpus'; target=p/'seed'
            source.write_text(json.dumps({'queryKey':'q','maskedTextSha256':'hash'}),encoding='utf-8')
            corpus.write_text(json.dumps({'documents':[{'itemKey':'d'}]}),encoding='utf-8')
            data=seed(source,corpus,target)
            self.assertIsNone(data['reviews']['q']['labels']['d']['judgment'])
            self.assertEqual(data,seed(source,corpus,target))
            source.write_text(json.dumps({'queryKey':'q','maskedTextSha256':'changed'}),encoding='utf-8')
            with self.assertRaises(ValueError): seed(source,corpus,target)

    def test_comparison_preserves_human_ai_provenance(self):
        old={'reviews':{'q':{'sourceTextSha256':'h','labels':{
            'a':{'judgment':'RELEVANT'},'b':{'judgment':'IRRELEVANT','judgmentSource':'AI'}}}}}
        new={'reviews':{'q':{'sourceTextSha256':'h','labels':{
            'a':{'judgment':'IRRELEVANT'},'b':{'judgment':'IRRELEVANT'}}}}}
        report, changed=compare(old,new)
        self.assertEqual(report['agreementRate'],0.5)
        self.assertEqual(report['byPreviousSource']['HUMAN']['disagree'],1)
        self.assertEqual(changed[0]['itemKey'],'a')
        self.assertFalse(report['accuracyClaim'])
        new['reviews']['q']['sourceTextSha256']='stale'
        with self.assertRaises(ValueError): compare(old,new)

    def test_expansion_keeps_all_old_document_objects(self):
        here=Path(__file__).parent
        old=json.loads((here/'relevance_documents_v2.json').read_text(encoding='utf-8'))['documents']
        new=json.loads((here/'relevance_documents_v3.json').read_text(encoding='utf-8'))['documents']
        by_key={d['itemKey']:d for d in new}
        self.assertEqual(len(new),20)
        self.assertEqual(len(new),len(by_key))
        self.assertTrue(all(by_key[d['itemKey']]==d for d in old))
        self.assertTrue(all(d['reviewStatus']=='DRAFT' for d in new))
        self.assertEqual(len({d['text'] for d in new}),len(new))


if __name__=='__main__': unittest.main()
