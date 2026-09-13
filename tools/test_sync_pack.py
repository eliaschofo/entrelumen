"""Exercise installer preservation and drift checks without a live instance."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sync_pack


class SyncPackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "pack/config/example.toml"
        self.source.parent.mkdir(parents=True)
        self.source.write_bytes(b"[feature]\nenabled = true\n")
        self.destination = self.root / "instance"
        self.destination.mkdir()
        (self.destination / ".entrelumen-test-server.json").write_text(
            json.dumps({"owner": "entrelumen"})
        )
        self.jar = self.root / "entrelumen-test.jar"
        self.jar.write_bytes(b"test jar")
        self.target = self.destination / "config/example.toml"
        self.target.parent.mkdir()
        self.expanded = (
            b"# Generated defaults\r\n[feature]\r\n enabled = true\r\n"
            b"default_limit = 12\r\n\r\n[user]\r\nextra = 'keep me'\r\n"
        )
        self.target.write_bytes(self.expanded)
        self.receipt = self.destination / "entrelumen-managed-files.json"

    def sync(self):
        with patch.object(sync_pack, "ROOT", self.root), patch(
            "sys.argv", ["sync_pack", str(self.destination), "--jar", str(self.jar)]
        ), contextlib.redirect_stdout(io.StringIO()):
            sync_pack.main()

    def test_repeated_sync_preserves_expanded_toml_and_receipt(self):
        self.sync()
        first_receipt = self.receipt.read_bytes()
        self.assertEqual(self.target.read_bytes(), self.expanded)
        self.assertEqual(
            json.loads(first_receipt)["config/example.toml"],
            sync_pack.digest(self.target),
        )
        self.sync()
        self.assertEqual(self.target.read_bytes(), self.expanded)
        self.assertEqual(self.receipt.read_bytes(), first_receipt)

    def test_changed_owned_setting_rejects_unreviewed_drift(self):
        self.sync()
        first_receipt = self.receipt.read_bytes()
        drifted = self.expanded.replace(b"enabled = true", b"enabled = false")
        self.target.write_bytes(drifted)
        with self.assertRaisesRegex(SystemExit, "Locally changed file needs review"):
            self.sync()
        self.assertEqual(self.target.read_bytes(), drifted)
        self.assertEqual(self.receipt.read_bytes(), first_receipt)

    def test_authored_update_accepts_previous_receipt_checksum(self):
        self.target.write_bytes(b"# Generated formatting\n[feature]\n enabled = true\n")
        self.sync()
        updated = b"[feature]\nenabled = false\n"
        self.source.write_bytes(updated)
        self.sync()
        self.assertEqual(self.target.read_bytes(), updated)
        self.assertEqual(
            json.loads(self.receipt.read_text())["config/example.toml"],
            sync_pack.digest(self.source),
        )

    def test_authored_update_with_extra_keys_requires_merge_without_writes(self):
        for extras in (
            b"\n[user]\nextra = 'keep me'\n",
            b"default_limit = 12\n",
        ):
            with self.subTest(extras=extras):
                self.source.write_bytes(b"[feature]\nenabled = true\n")
                original = self.source.read_bytes() + extras
                self.target.write_bytes(original)
                self.sync()
                receipt_before = self.receipt.read_bytes()
                jar_target = self.destination / "mods" / self.jar.name
                jar_before = jar_target.read_bytes()
                self.jar.write_bytes(b"updated jar")
                self.source.write_bytes(b"[feature]\nenabled = false\n")
                with self.assertRaisesRegex(SystemExit, "merge/review required"):
                    self.sync()
                self.assertEqual(self.target.read_bytes(), original)
                self.assertEqual(self.receipt.read_bytes(), receipt_before)
                self.assertEqual(jar_target.read_bytes(), jar_before)


if __name__ == "__main__":
    unittest.main()
