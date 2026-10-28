import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from structure_complaints import enum_values, load_inputs, transform


class StructureTests(unittest.TestCase):
    def setUp(self):
        self.value = {'factualSummary':'신청인이 확인을 요청함','summaryEvidenceIds':['s0'],
            'context':{'placeType':'UNKNOWN','relationshipType':'UNKNOWN','timePattern':'UNKNOWN'},
            'contextEvidenceIds':['s0'], 'observedFacts':[{'factType':'ACTION','value':'확인 요청',
                'evidenceIds':['s0'],'certainty':'EXPLICIT'}], 'riskSignals':[],
            'subjectMatters':[{'code':'UNKNOWN','evidenceIds':['s0'],'certainty':'UNCERTAIN'}],
            'requestedActions':[{'code':'UNKNOWN','evidenceIds':['s0'],'certainty':'UNCERTAIN'}],
            'ongoingStatus':'UNKNOWN','ongoingEvidenceIds':['s0'],'missingInformation':['세부 내용'],
            'riskAssessmentReason':'명시적인 위험 코드 근거가 없음'}

    def test_unknown_is_preserved_and_evidence_is_copied_from_source(self):
        result=transform(self.value,{'s0':'확인해 주세요'},enum_values())
        self.assertEqual(result['observedFacts'][0]['evidence'],'확인해 주세요')
        self.assertEqual(result['requestedActions'][0]['code'],'UNKNOWN')
        self.assertEqual(result['requestedActions'][0]['certainty'],'UNCERTAIN')
        self.assertNotIn('searchText',result)

    def test_rejects_fabricated_ids(self):
        self.value['observedFacts'][0]['evidenceIds']=['nonexistent']
        with self.assertRaises(ValueError):transform(self.value,{'s0':'확인해 주세요'},enum_values())

    def test_rejects_final_decisions_and_unsupported_codes(self):
        self.value['finalRoute']='invented'
        with self.assertRaises(ValueError):transform(self.value,{'s0':'확인'},enum_values())
        del self.value['finalRoute']
        self.value['subjectMatters'][0]['code']='INVENTED'
        with self.assertRaises(ValueError):transform(self.value,{'s0':'확인'},enum_values())

    def test_requires_privacy_pass_and_actual_text_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            row={'queryKey':'q','maskedText':'가림 텍스트','maskedTextSha256':hashlib.sha256('가림 텍스트'.encode()).hexdigest()}
            source=root/'source.jsonl'
            source.write_text(json.dumps(row),encoding='utf-8')
            approval={'datasetSha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                      'reviews':{'q':{'sourceTextSha256':row['maskedTextSha256'],'judgments':{'privacy':'UNSURE'}}}}
            privacy=root/'privacy.json'
            privacy.write_text(json.dumps(approval),encoding='utf-8')
            with self.assertRaises(ValueError):load_inputs(source,privacy)
            approval['reviews']['q']['judgments']['privacy']='PASS'
            privacy.write_text(json.dumps(approval),encoding='utf-8')
            self.assertEqual(len(load_inputs(source,privacy)),1)


if __name__=='__main__':unittest.main()
