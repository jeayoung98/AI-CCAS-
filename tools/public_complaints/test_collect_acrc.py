"""Offline tests use invented strings, never fetched complaint originals."""
import unittest
from bs4 import BeautifulSoup
from collect_acrc import extract, redact, NAME_RISK


class CollectionTests(unittest.TestCase):
    def soup(self, content):
        return BeautifulSoup('<article class="board_view"><div class="contents">' + content + '</div></article>', 'html.parser')

    def test_extracts_complaint_without_answer(self):
        complaint = '신청인은 담당 기관의 처리 지연에 대해 설명을 요청하였으나 진행 상황을 안내받지 못하였으며, 여러 차례 처리 경과를 문의했지만 답변이 없어 처리 상황 안내와 신속한 조치를 요청하였다.'
        result, reason = extract(self.soup('주문: 시정권고. 1. 신청 원인 ' + complaint + ' 2. 피신청인의 주장 답변 본문. 3. 판단 법령 해석.'))
        self.assertIsNone(reason)
        self.assertEqual(result['masked_text'], complaint)
        self.assertNotIn('답변 본문', result['masked_text'])
        self.assertNotIn('시정권고', result['masked_text'])

    def test_skips_order_only_or_unknown_boundary(self):
        self.assertEqual(extract(self.soup('의결개요 주문 시정권고'))[1], 'no_public_complaint_section')
        self.assertEqual(extract(self.soup('신청원인 ' + '내용 ' * 50))[1], 'unbounded_complaint_section')

    def test_identifier_redaction(self):
        output = redact('김○○ 010-1234-5678 test@example.invalid 900101-1234567 2AA-1508-123456 2020. 3. 5. 123가4567')
        for raw in ('김○○', '010-1234-5678', 'test@example.invalid', '900101-1234567', '2AA-1508-123456', '2020. 3. 5.', '123가4567'):
            self.assertNotIn(raw, output)

    def test_excludes_possible_unmasked_name(self):
        text = '신청원인 가상인물 홍길동씨는 ' + '처리 지연에 대한 설명을 요청하였다. ' * 10 + '피신청인의 주장 답변'
        self.assertEqual(extract(self.soup(text))[1], 'possible_person_name')

    def test_limits_excerpt_without_inventing_text(self):
        result, reason = extract(self.soup('신청원인 ' + '처리 상황 안내를 요청하였다. ' * 100 + '피신청인의 주장 답변'))
        self.assertIsNone(reason)
        self.assertTrue(result['excerpt_truncated'])
        self.assertLessEqual(len(result['masked_text'].split()), 160)
        self.assertLessEqual(len(result['masked_text']), 1200)

    def test_missing_content(self):
        self.assertEqual(extract(BeautifulSoup('<p>로그인</p>', 'html.parser'))[1], 'missing_content')

    def test_plural_respondent_headings_are_boundaries(self):
        complaint = '신청인은 처리 상황 안내와 신속한 조치를 요청하였다. ' * 5
        for heading in ('피신청인들의 주장', '피신청인 등 주장', '피신청인의 의견'):
            with self.subTest(heading=heading):
                result, reason = extract(self.soup('신청원인 ' + complaint + ' 2. ' + heading + ' 기관 답변. 3. 사실관계'))
                self.assertIsNone(reason)
                self.assertEqual(result['masked_text'], complaint.strip())


if __name__ == '__main__':
    unittest.main()
