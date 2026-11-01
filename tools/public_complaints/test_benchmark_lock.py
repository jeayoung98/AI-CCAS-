import tempfile
from pathlib import Path
import unittest
from benchmark_lock import fingerprint,verify

class BenchmarkLockTest(unittest.TestCase):
    def test_label_change_invalidates_frozen_benchmark(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);p=root/'labels.json';p.write_text('original')
            lock={'inputs':fingerprint(root,{'qrels':'labels.json'})}
            self.assertTrue(verify(root,lock))
            p.write_text('different labels')
            with self.assertRaises(ValueError):verify(root,lock)

    def test_external_paths_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(ValueError):fingerprint(Path(t),{'qrels':'../outside.json'})
