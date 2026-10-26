import json
import tempfile
import unittest
from pathlib import Path
from export_relevance import digest, export


class ExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        p = self.root = Path(self.temp.name)
        self.source, self.corpus, self.responses, self.privacy = [p/name for name in ('q.jsonl','c.json','r.json','p.json')]
        rows = [{'queryKey': k, 'maskedTextSha256': k, 'groupId': k, 'splitProposal': 'test', 'sourceCategory': 'test'} for k in ('a','b','c')]
        self.source.write_text('\n'.join(json.dumps(r) for r in rows), encoding='utf-8')
        self.corpus.write_text(json.dumps({'documents':[{'itemKey':'d','itemType':'OFFICIAL_GUIDE'}]}), encoding='utf-8')
        self.review = {'datasetSha256':digest(self.source),'corpusSha256':digest(self.corpus),'reviews':{
            k:{'sourceTextSha256':k,'labels':{'d':{'judgment':j}},'updatedAt':'test','revision':1}
            for k,j in [('a','RELEVANT'),('b','UNSURE')]}}
        self.responses.write_text(json.dumps(self.review), encoding='utf-8')
        self.privacy.write_text(json.dumps({'datasetSha256':digest(self.source),'reviews':{k:{'sourceTextSha256':k,'judgments':{'privacy':'PASS'}} for k in ('a','b','c')}}), encoding='utf-8')

    def tearDown(self):
        self.temp.cleanup()

    def run_export(self):
        return export(self.source,self.corpus,self.responses,self.privacy,self.root/'out')

    def test_unknown_never_becomes_negative(self):
        result=self.run_export()
        self.assertEqual(result['resolvedPairs'],1)
        self.assertEqual(result['negativePairs'],0)
        self.assertEqual(result['unresolvedQueries'],1)
        self.assertEqual(result['unreviewedQueries'],1)
        self.assertEqual(result['fullyResolvedQueries'],1)
        with self.assertRaises(FileExistsError):self.run_export()

    def test_rejects_stale_corpus(self):
        self.corpus.write_text(self.corpus.read_text(encoding='utf-8')+' ',encoding='utf-8')
        with self.assertRaises(ValueError):self.run_export()

    def test_expanded_pool_preserves_old_labels_without_completing_queries(self):
        documents = [{'itemKey': 'd', 'itemType': 'OFFICIAL_GUIDE'}] + [
            {'itemKey': str(i), 'itemType': 'OFFICIAL_GUIDE'} for i in range(4)]
        self.corpus.write_text(json.dumps({'documents': documents}), encoding='utf-8')
        self.review['corpusSha256'] = digest(self.corpus)
        for record in self.review['reviews'].values():
            record['labels'].update({str(i): {'judgment': None} for i in range(4)})
        self.responses.write_text(json.dumps(self.review), encoding='utf-8')
        result = self.run_export()
        self.assertEqual(result['resolvedPairs'], 1)
        self.assertEqual(result['negativePairs'], 0)
        self.assertEqual(result['fullyResolvedQueries'], 0)
        self.assertEqual(result['unselectedPairs'], 8)
        self.assertEqual(result['unsurePairs'], 1)
        self.assertEqual(result['documentCounts'], {'OFFICIAL_GUIDE': 5})
        self.assertEqual(result['channelsWithEntirePoolWithinTopK'], [])

    def test_rejects_unknown_document(self):
        self.review['reviews']['a']['labels']={'invented':{'judgment':'RELEVANT'}}
        self.responses.write_text(json.dumps(self.review),encoding='utf-8')
        with self.assertRaises(ValueError):self.run_export()


if __name__ == '__main__':unittest.main()
