import unittest
import json
from unittest.mock import patch
from officer_rubric_pilot import judge_batched, response_schema, judge
from officer_rubric_pilot import validate, select_police_dev, summarize, controls, MODELS


class OfficerRubricTest(unittest.TestCase):
    def test_strict_map_normalizes_but_does_not_bypass_evidence_validation(self):
        row = {'maskedText': 'text', 'targetKeys': ['d']}
        doc = dict(itemKey='d', itemType='OFFICIAL_GUIDE', title='guide', text='guide', boundary='scope')
        label = dict(itemKey='d', grade=2, officerTask='task', reason='reason', evidenceIds=['s0'], confidence='HIGH')
        def response(v):
            return {'model':'model', 'choices':[{'message':{'content':json.dumps({'labels':{'d':v}})}}]}
        with patch('officer_rubric_pilot.chat', return_value=response(label)):
            self.assertEqual(judge(row,[doc],'model',True)['labels']['d']['grade'],2)
        with patch('officer_rubric_pilot.chat', return_value=response(dict(label,evidenceIds=[]))):
            with self.assertRaises(ValueError):
                judge(row,[doc],'model',True)

    def test_strict_schema_limits_ids_to_current_batch_and_source(self):
        schema = response_schema(['doc1', 'doc2'], {'s0': 'text'})
        labels = schema['json_schema']['schema']['properties']['labels']
        self.assertEqual(labels['required'], ['doc1', 'doc2'])
        self.assertFalse(labels['additionalProperties'])
        item = labels['properties']['doc1']
        self.assertEqual(item['properties']['itemKey']['enum'], ['doc1'])
        self.assertEqual(item['properties']['evidenceIds']['items']['enum'], ['s0'])
        self.assertFalse(item['additionalProperties'])
        self.assertEqual(set(item['required']), set(item['properties']))

    def test_batches_cover_every_document_without_partial_success(self):
        row = {'targetKeys': list('abcdef')}
        def answer(r, docs, model):
            return {'labels': {k: {'grade': 0} for k in r['targetKeys']}, 'model': model}
        with patch('officer_rubric_pilot.judge', side_effect=answer) as mocked:
            result = judge_batched(row, [], 'model', 5)
            self.assertEqual(set(result['labels']), set('abcdef'))
            self.assertEqual([len(c.args[0]['targetKeys']) for c in mocked.call_args_list], [5, 1])
        with patch('officer_rubric_pilot.judge', side_effect=[answer({'targetKeys':list('abcde')}, [], 'model'), ValueError('invalid')]):
            with self.assertRaises(ValueError):
                judge_batched(row, [], 'model', 5)

    def test_only_police_development_rows_and_no_group_leakage(self):
        rows = [{'queryKey': str(i), 'groupId': str(i), 'splitProposal': 'dev',
                 'sourceCategory': '경찰민원' if i < 17 else '주택건축민원'} for i in range(100)]
        rows.append({'queryKey': 'test', 'groupId': 'test', 'splitProposal': 'test', 'sourceCategory': '경찰민원'})
        self.assertEqual(len(select_police_dev(rows)), 17)
        rows[-1]['groupId'] = '0'
        with self.assertRaises(ValueError):
            select_police_dev(rows)

    def test_direct_evidence_requires_task_and_source(self):
        label = {'itemKey': 'd', 'grade': 2, 'officerTask': '절차 안내', 'reason': '근거',
                 'evidenceIds': ['s0'], 'confidence': 'HIGH'}
        self.assertEqual(validate({'labels': [label]}, ['d'], {'s0': 'text'})['d']['grade'], 2)
        for update in ({'officerTask': ''}, {'evidenceIds': []}, {'evidenceIds': ['missing']}, {'grade': True}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                validate({'labels': [dict(label, **update)]}, ['d'], {'s0': 'text'})
        label.update(grade='UNASSESSABLE', evidenceIds=[])
        self.assertEqual(validate({'labels': [label]}, ['d'], {})['d']['grade'], 'UNASSESSABLE')

    def test_unknown_not_negative_and_no_case_channel_claim(self):
        row = {'queryKey': 'q', 'kind': 'dev', 'targetKeys': ['d']}
        state = {'binding': {}, 'jobs': {f'{m}|q': {'model': m, 'labels': {'d': {'grade': g}}}
                                       for m, g in zip(MODELS, ['UNASSESSABLE', 2])}}
        report = summarize([row], [{'itemKey': 'd', 'itemType': 'OFFICIAL_GUIDE'}], state)
        channels = report['results']['dev']['channels']
        self.assertEqual(channels['OFFICIAL_GUIDE']['unknownPairs'], 1)
        self.assertEqual(channels['OFFICIAL_GUIDE']['directEvidenceAgreement']['count'], 0)
        self.assertIsNone(channels['VERIFIED_CASE']['exactGradeAgreement'])
        self.assertEqual(channels['VERIFIED_CASE']['pairCount'], 0)

    def test_controls_have_paired_positive_negative_scopes(self):
        rows = controls()
        self.assertEqual(len(rows), 12)
        self.assertEqual(len({r['queryKey'] for r in rows}), 12)
        for key in {r['targetKeys'][0] for r in rows}:
            self.assertEqual({r['expected'][key] for r in rows if key in r['expected']}, {0, 2})
        self.assertTrue(all(r['kind'] == 'synthetic' for r in rows))

    def test_missing_response_is_reported_not_converted_to_negative(self):
        rows = [{'queryKey': 'q', 'kind': 'dev', 'targetKeys': ['d']}]
        state = {'binding': {}, 'jobs': {f'{MODELS[0]}|q': {'model': MODELS[0], 'labels': {'d': {'grade': 2}}}}}
        r = summarize(rows, [{'itemKey': 'd', 'itemType': 'OFFICIAL_GUIDE'}], state)
        self.assertFalse(r['complete'])
        self.assertEqual(r['completedResponses'], 1)
        c = r['results']['dev']['channels']['OFFICIAL_GUIDE']
        self.assertEqual(c['missingPairs'], 1)
        self.assertEqual(c['directEvidenceAgreement']['count'], 0)
        self.assertEqual(c['gradeCounts'][MODELS[0]]['2'], 1)
