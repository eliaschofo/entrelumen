"""Synthetic parser fixtures only; these are not performance evidence."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import benchmark


class CaptureIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='entrelumen-synthetic-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = {'collector': benchmark.COLLECTOR, 'mode': 'singleplayer', 'evidence_kind': 'synthetic'}
        self.paths = tuple(self.root / (name + '.csv') for name in ('frames', 'ticks', 'memory'))
        self.integrity = {'status': 'closed_needs_review', 'reasons': [], 'pending_rows': 0,
                          'clock_origin_verified': True, 'acceptance': False, 'tick_scope': 'vanilla_tickServer'}
        self.write_json('session.json', {'collector': benchmark.COLLECTOR, 'clock': 'shared_process_nanoTime'})
        self.write_json('capture-integrity.json', self.integrity)
        for name, content in {
            'frames': 'elapsed_ms,frame_ms,detail\n10,10,valid\n20,10,valid\n',
            'ticks': 'elapsed_ms,tick_ms,detail\n10,2,1\n60,3,2\n',
            'memory': 'elapsed_ms,heap_used_mb,detail\n0,100,heap\n30000,110,heap\n',
            'events': 'elapsed_ms,value,detail\n',
            'gc': 'elapsed_ms,value,detail\n0,0,G1\n30000,1,G1\n',
            'post_gc': 'elapsed_ms,value,detail\n',
        }.items():
            (self.root / (name + '.csv')).write_text(content)

    def write_json(self, name, data):
        (self.root / name).write_text(json.dumps(data))

    def assert_rejected_before_metrics(self):
        with patch.object(benchmark, 'route_metrics', side_effect=AssertionError('metrics must not run')):
            result = benchmark.report(self.config, *self.paths)
        self.assertEqual('incomplete', result['status'])
        self.assertNotIn('metrics', result)
        self.assertTrue(result['reasons'])

    def test_complete_artifacts_validate_but_synthetic_is_not_acceptance(self):
        self.assertTrue(benchmark.validate_capture(self.config, self.paths))
        self.assertEqual('incomplete', benchmark.report(self.config, *self.paths)['status'])

    def test_missing_sidecar_and_each_missing_raw_are_rejected(self):
        for name in ['capture-integrity.json', 'session.json', 'frames.csv', 'ticks.csv', 'memory.csv', 'gc.csv', 'events.csv', 'post_gc.csv']:
            with self.subTest(name=name):
                path = self.root / name
                saved = path.read_bytes()
                path.unlink()
                self.assert_rejected_before_metrics()
                path.write_bytes(saved)

    def test_partial_overflow_and_pending_rows_rejected(self):
        for change in [{'status': 'recording_or_interrupted'}, {'reasons': ['buffer_overflow']},
                       {'pending_rows': 1}, {'clock_origin_verified': False}, {'status': 'invalid'}]:
            with self.subTest(change=change):
                self.write_json('capture-integrity.json', self.integrity | change)
                self.assert_rejected_before_metrics()

    def test_raw_pause_and_tick_gap_rejected_even_with_clean_sidecar(self):
        (self.root / 'events.csv').write_text('elapsed_ms,value,detail\n15,0,pause=true\n')
        self.assert_rejected_before_metrics()
        (self.root / 'events.csv').write_text('elapsed_ms,value,detail\n')
        (self.root / 'ticks.csv').write_text('elapsed_ms,tick_ms,detail\n10,2,1\n60,3,3\n')
        self.assert_rejected_before_metrics()

    def test_non_monotonic_and_invalid_frame_flag_rejected(self):
        for body in ['elapsed_ms,frame_ms,detail\n10,10,valid\n10,10,valid\n',
                     'elapsed_ms,frame_ms,detail\n10,10,valid\n20,10,focus=false\n']:
            (self.root / 'frames.csv').write_text(body)
            self.assert_rejected_before_metrics()

    def test_wrong_declared_collector_cannot_bypass_existing_sidecar(self):
        self.config['collector'] = 'external'
        self.assert_rejected_before_metrics()

    def test_external_synthetic_calculations_still_supported(self):
        self.assertEqual([], benchmark.validate_capture({'collector': 'synthetic-fixture'}, (None, None, None)))
        benchmark.self_test()


if __name__ == '__main__':
    unittest.main()
