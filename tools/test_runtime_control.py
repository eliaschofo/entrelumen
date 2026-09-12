"""Queue tests with an isolated Python console, never Java or a real server."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import uuid
from unittest.mock import patch

import runtime


class RuntimeControlTest(unittest.TestCase):
    def test_fake_console_idempotence_live_guard_and_clean_stop(self):
        original_popen = subprocess.Popen
        with tempfile.TemporaryDirectory(prefix="entrelumen-fake-console-") as directory:
            root = Path(directory).resolve()
            (root / ".entrelumen-test-server.json").write_text('{"owner":"entrelumen"}')
            argsfile = root / "libraries/net/neoforged/neoforge" / runtime.NEOFORGE / ("win_args.txt" if runtime.os.name == "nt" else "unix_args.txt")
            argsfile.parent.mkdir(parents=True)
            argsfile.write_text("fake fixture, never passed to Java")
            script = root / "fake.py"
            script.write_text("import sys\nfrom pathlib import Path\nfor line in sys.stdin:\n with Path('commands.txt').open('a') as f: f.write(line)\n print(line.strip(),flush=True)\n if line.strip()=='stop': break\n")
            def launch_fake(command, **kwargs):
                return original_popen([sys.executable, '-u', str(script)], **kwargs)
            errors = []
            def run():
                try:
                    runtime.run(argparse.Namespace(destination=str(root), java=sys.executable, heap='1G'))
                except Exception as error:
                    errors.append(error)
            with patch.object(runtime.subprocess, 'Popen', side_effect=launch_fake):
                thread = threading.Thread(target=run)
                thread.start()
                deadline = time.monotonic() + 5
                while not (root / 'owned-process.json').exists() and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue((root / 'owned-process.json').exists())
                try:
                    with self.assertRaises(ValueError):
                        runtime.run(argparse.Namespace(destination=str(root), java=sys.executable, heap='1G'))
                    with self.assertRaises(ValueError):
                        runtime.check_previous_run(root)
                    request_id = str(uuid.uuid4())
                    args = argparse.Namespace(destination=str(root), text='list', request_id=request_id)
                    self.assertEqual(runtime.send_command(args), 0)
                    self.assertEqual(runtime.send_command(args), 0)
                    args.text = 'save-all'
                    with self.assertRaisesRegex(ValueError, 'different command'):
                        runtime.send_command(args)
                finally:
                    runtime.send_command(argparse.Namespace(destination=str(root), text='stop', request_id=str(uuid.uuid4())))
                    thread.join(5)
                self.assertFalse(thread.is_alive())
                self.assertEqual(errors, [])
                self.assertEqual((root / 'commands.txt').read_text().splitlines(), ['list', 'stop'])
                receipt = json.loads((root / 'owned-process.json').read_text())
                self.assertEqual(receipt['exitCode'], 0)
                self.assertIn('ended', receipt)

    def test_marker_and_command_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.entrelumen-test-server.json').write_text('{"owner":"someone-else"}')
            with self.assertRaises(ValueError):runtime.owned_destination(root)
        for value in ('', '   ', 'list\nstop', 'list\x7f', None):
            self.assertFalse(runtime.valid_command(value))
        self.assertTrue(runtime.valid_command('say hello'))

    def test_ambiguous_request_is_not_requeued(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / '.entrelumen-test-server.json').write_text('{"owner":"entrelumen"}')
            run_id, request_id = str(uuid.uuid4()), str(uuid.uuid4())
            runtime.write_json(root / 'owned-process.json', {'owner':'entrelumen','cwd':str(root),'pid':runtime.os.getpid(),'runId':run_id})
            control=root/'control'/run_id
            (control/'requests').mkdir(parents=True);(control/'results').mkdir()
            claimed=control/'requests'/(request_id+'.claimed')
            runtime.write_json(claimed, {'runId':run_id,'command':'save-all'})
            args=argparse.Namespace(destination=str(root),text='save-all',request_id=request_id)
            with patch.object(runtime.time,'monotonic',side_effect=[0,11]):
                self.assertEqual(runtime.send_command(args),2)
            self.assertFalse(claimed.with_suffix('.json').exists())


if __name__=='__main__':unittest.main()
