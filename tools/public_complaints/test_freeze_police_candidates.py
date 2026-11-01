import unittest
from freeze_police_candidates import assign,validate_groups,similar


class FreezeCandidatesTest(unittest.TestCase):
    def rows(self):
        return [dict(queryKey=k,groupId=k,sourceUrl='https://www.law.go.kr/LSW/cgmExpcInfoP.do?cgmExpcDatSeq='+str(i),
                     maskedText=t) for i,(k,t) in enumerate([('a','지갑 분실'),('b','순찰 요청'),('c','온라인 거래 피해')])]

    def test_transitive_family_and_prior_exposure_force_one_dev_group(self):
        rows,groups=assign(self.rows(),[{'members':['a','b']},{'members':['b','c']}],{'c'})
        self.assertEqual(len(groups),1)
        self.assertEqual({r['splitProposal'] for r in rows},{'dev'})
        self.assertTrue(all(r['previouslyExposed'] for r in rows))
        self.assertTrue(all(not r['benchmarkEligible'] for r in rows))

    def test_order_independent_and_invalid_model_ids_rejected(self):
        a,_=assign(self.rows(),[],set());b,_=assign(list(reversed(self.rows())),[],set())
        self.assertEqual(a,b)
        with self.assertRaises(ValueError):validate_groups({'groups':[{'members':['a','invented'],'reason':'x'}]},{'a','b'})
        self.assertTrue(similar('지갑 분실!','지갑 분실'))
