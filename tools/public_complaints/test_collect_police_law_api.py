import unittest
import json
import tempfile
from pathlib import Path
from types import SimpleNamespace
from argparse import Namespace
from unittest.mock import patch
import requests
from collect_police_law_api import list_ids, query_draft, list_ids_json, query_draft_json, fetch, run


class PoliceLawApiTest(unittest.TestCase):
    def test_keyword_cap_keeps_later_keywords_in_collection(self):
        from collect_police_law_api import KEYWORDS
        def response(session,endpoint,params):
            if endpoint == 'lawSearch.do':
                first = KEYWORDS.index(params['query']) * 100 + 1
                return json.dumps({'CgmExpc': {'totalCnt':'3', 'cgmExpc':[
                    {'법령해석일련번호':str(first+i)} for i in range(3)]}})
            return json.dumps({'CgmExpcService': {'법령해석일련번호':params['ID'], '질의요지':'휴대전화를 분실했다.'}})
        with tempfile.TemporaryDirectory() as temp, patch.dict('os.environ',{'LAW_API_OC':'FAKE_TEST_ONLY'}), \
                patch('collect_police_law_api.fetch',side_effect=response), patch('collect_police_law_api.time.sleep'):
            out=Path(temp)/'intake'
            run(Namespace(execute=True,approved=True,limit=100,pages=1,per_keyword_limit=1,output=out))
            summary=json.loads((out/'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['draftCount'],len(KEYWORDS))
            self.assertTrue(all(n==1 for n in summary['discoveryKeywordCounts'].values()))

    def test_live_json_shape_and_detail_identity(self):
        text=json.dumps({'CgmExpc':{'totalCnt':'1','cgmExpc':[{'법령해석일련번호':'123'}]}})
        self.assertEqual(list_ids_json(text),(['123'],1))
        with self.assertRaises(ValueError):list_ids_json('{"error":"denied"}')
        detail=json.dumps({'CgmExpcService':{'법령해석일련번호':'123','질의요지':'휴대전화 분실 문의','회답':'ANSWER_NOT_SAVED'}})
        draft=query_draft_json(detail,'123','분실')
        self.assertNotIn('ANSWER_NOT_SAVED',str(draft))
        with self.assertRaises(ValueError):query_draft_json(detail,'456','분실')

    def test_http_200_target_denial_is_not_an_xml_parse_error(self):
        response=SimpleNamespace(status_code=200,text='<!DOCTYPE html><html>미신청된 목록/본문에 대한 접근입니다.</html>')
        with patch('requests.Session.get',return_value=response):
            with requests.Session() as session:
                with self.assertRaisesRegex(RuntimeError,'police list/body is not enabled'):
                    fetch(session,'lawSearch.do',{'OC':'SECRET'})

    def test_error_response_not_misread_as_empty_results(self):
        with self.assertRaises(ValueError):list_ids('<error>unauthorized</error>')
        self.assertEqual(list_ids('<r><totalCnt>0</totalCnt></r>'),([],0))

    def test_only_question_saved_and_identity_bound(self):
        xml='<r><법령해석일련번호>123</법령해석일련번호><질의요지>휴대전화 분실 문의 010-1234-5678</질의요지><회답>SECRET_ANSWER</회답></r>'
        r=query_draft(xml,'123','분실')
        self.assertNotIn('010-1234-5678',r['maskedText'])
        self.assertNotIn('SECRET_ANSWER',str(r))
        self.assertFalse(r['maskingCompleted'])
        self.assertFalse(r['benchmarkEligible'])
        with self.assertRaises(ValueError):query_draft(xml,'456','분실')

    def test_approval_and_transport_errors_do_not_leak_credentials(self):
        with self.assertRaises(ValueError):run(Namespace(execute=True,approved=False,limit=1,pages=1))
        with patch('requests.Session.get', side_effect=requests.ConnectionError('url?OC=SECRET')):
            with requests.Session() as session:
                with self.assertRaises(RuntimeError) as error:fetch(session,'lawSearch.do',{'OC':'SECRET'})
        self.assertNotIn('SECRET',str(error.exception))
