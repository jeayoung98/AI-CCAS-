import json
import tempfile
import unittest
from pathlib import Path
from hashlib import sha256
from extend_relevance import migrate


class ExtendTests(unittest.TestCase):
    def test_carry_and_refuse_modified_document(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder);old=p/'old.json';new=p/'new.json';src=p/'reviews.json';dest=p/'out.json'
            old.write_text(json.dumps({'documents':[{'itemKey':'a','text':'original'}]}),encoding='utf-8')
            new.write_text(json.dumps({'documents':[{'itemKey':'a','text':'original'},{'itemKey':'b','text':'new'}]}),encoding='utf-8')
            data={'corpusSha256':sha256(old.read_bytes()).hexdigest(),'reviews':{'q':{'labels':{'a':{'judgment':'UNSURE','reason':'keep'}},'completed':True,'resolved':False,'updatedAt':'original-time','revision':3}}}
            src.write_text(json.dumps(data),encoding='utf-8');before=src.read_bytes()
            report=migrate(old,new,src,dest)
            result=json.loads(dest.read_text(encoding='utf-8'))['reviews']['q']
            self.assertEqual(report['carriedPairs'],1)
            self.assertEqual(result['labels']['a'],data['reviews']['q']['labels']['a'])
            self.assertIsNone(result['labels']['b']['judgment'])
            self.assertFalse(result['completed'])
            self.assertEqual(result['updatedAt'],'original-time')
            self.assertEqual(src.read_bytes(),before)
            with self.assertRaises(FileExistsError):migrate(old,new,src,dest)
            changed=json.loads(new.read_text(encoding='utf-8'));changed['documents'][0]['text']='modified'
            new.write_text(json.dumps(changed),encoding='utf-8')
            with self.assertRaises(ValueError):migrate(old,new,src,p/'other.json')


if __name__ == '__main__':unittest.main()
