import unittest
from project_relevance_pool import project

class ProjectPoolTest(unittest.TestCase):
    def test_projection_preserves_provenance_and_does_not_mutate_original(self):
        a={'itemKey':'a','text':'same'};b={'itemKey':'b','text':'new'}
        labels={'a':{'judgment':'RELEVANT','judgmentSource':'AI'},'b':{'judgment':'IRRELEVANT'}}
        original={'reviews':{'q':{'labels':labels}}}
        result=project({'documents':[a,b]},{'documents':[a]},original)
        self.assertEqual(result['reviews']['q']['labels'],{'a':labels['a']})
        self.assertEqual(len(original['reviews']['q']['labels']),2)
        with self.assertRaises(ValueError):
            project({'documents':[a,b]},{'documents':[{'itemKey':'a','text':'changed'}]},original)
