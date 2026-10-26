import json
import tempfile
import unittest
from pathlib import Path
from prepare_review import fingerprint, group_rows, split_groups, prepare


def sample(key, text, category='경찰민원'):
    return {'item_key': key, 'source_id': key, 'masked_text': text,
            'masked_text_sha256': fingerprint(text), 'synthetic': False,
            'source_url': 'https://www.acrc.go.kr/', 'source_category': category,
            'source_published_date': '2020-01-01', 'source_title_masked': '가상 테스트 제목',
            'license_notice': 'test only', 'excerpt_truncated': False}


class ReviewPreparationTests(unittest.TestCase):
    def test_groups_normalized_duplicates(self):
        rows = [sample('a', '민원 처리 진행 상황을 알려 주세요. [날짜]'),
                sample('b', '민원처리 진행상황을 알려주세요.'), sample('c', '완전히 다른 도로 시설 요청입니다.')]
        groups, pairs = group_rows(rows)
        self.assertEqual(sorted(map(len, groups)), [1, 2])
        self.assertEqual(len(pairs), 1)

    def test_shared_source_grouped_even_when_text_differs(self):
        rows = [sample('a', 'first'), sample('b', 'second')]
        rows[1]['source_id'] = 'a'
        self.assertEqual(len(group_rows(rows)[0]), 1)

    def test_split_is_exact_and_order_independent(self):
        groups = [[sample(str(i), str(i))] for i in range(10)]
        first = {g[0]['item_key']: s for g, s in split_groups(groups, 2)}
        second = {g[0]['item_key']: s for g, s in split_groups(list(reversed(groups)), 2)}
        self.assertEqual(first, second)
        self.assertEqual(list(first.values()).count('dev'), 2)

    def test_impossible_split_does_not_break_groups(self):
        with self.assertRaises(ValueError):
            split_groups([[sample('a', 'a'), sample('b', 'b')], [sample('c', 'c'), sample('d', 'd')]], 1)

    def test_unknown_labels_stay_null_and_original_is_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'input.jsonl'
            rows = [sample('a', '경찰의 민원 처리 경과에 대해 물었습니다.'), sample('b', '주택 건축 허가 절차를 문의합니다.', '주택건축민원')]
            original = ''.join(json.dumps(r, ensure_ascii=False) + '\n' for r in rows)
            source.write_text(original, encoding='utf-8')
            result = prepare(source, root / 'review', 2, 1)
            self.assertEqual(result['readyForScoredEvaluation'], 0)
            self.assertEqual(source.read_text(encoding='utf-8'), original)
            for line in (root / 'review/review-queue.jsonl').read_text(encoding='utf-8').splitlines():
                row = json.loads(line)
                self.assertIsNone(row['expectedRelevantVerifiedCaseKeys'])
                self.assertIsNone(row['noReliableSimilarCaseExpected'])
                self.assertIsNone(row['expectedRiskSignalPresent'])
                self.assertIsNone(row['structuredComplaint'])
                self.assertFalse(row['maskingCompleted'])
            with self.assertRaises(FileExistsError):
                prepare(source, root / 'review', 2, 1)

    def test_rejects_modified_source_fingerprint(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rows = [sample('a', 'a'), sample('b', 'b')]
            rows[0]['masked_text'] = 'changed'
            source = root / 'input.jsonl'
            source.write_text('\n'.join(json.dumps(r) for r in rows), encoding='utf-8')
            with self.assertRaises(ValueError):
                prepare(source, root / 'review', 2, 1)
            self.assertFalse((root / 'review').exists())


if __name__ == '__main__':
    unittest.main()
