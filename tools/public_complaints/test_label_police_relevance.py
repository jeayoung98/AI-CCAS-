import unittest
import tempfile
from pathlib import Path
from label_police_relevance import projection,export,MODELS


class PoliceRelevanceTest(unittest.TestCase):
    def test_unknown_is_not_negative_and_background_is_not_direct(self):
        self.assertIsNone(projection('UNASSESSABLE'))
        self.assertEqual([projection(i) for i in (0,1,2)],[0,0,1])
        with self.assertRaises(ValueError):projection(True)

    def test_primary_is_fixed_and_unknown_query_not_resolved(self):
        rows=[dict(queryKey='q',groupId='g',splitProposal='test',sourceTextSha256='h')]
        docs=[dict(itemKey='d',itemType='OFFICIAL_GUIDE')]
        state={'jobs':{f'{m}|q':{'model':m,'labels':{'d':dict(grade=g,confidence='HIGH',evidenceIds=['s0'])}}
                       for m,g in zip(MODELS,(2,'UNASSESSABLE'))}}
        with tempfile.TemporaryDirectory() as t:
            summary=export(rows,docs,state,Path(t))
            self.assertEqual(summary['fullyResolvedQueries'],0)
            self.assertEqual(summary['unresolvedQueries'],1)
            self.assertEqual(summary['resolvedPairs'],0)
            self.assertEqual(summary['gradeDisagreements'],1)
