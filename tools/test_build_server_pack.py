"""Exercise the server-pack builder against fake trees and the real pack/ layout; no server is started."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_server_pack as bsp


class ServerPackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / "repo"
        self.root.mkdir()
        self.jar = self.base / "entrelumen-9.9.jar"
        self.jar.write_bytes(b"companion")
        self.out = self.base / "out" / "server-pack"

    def fake_pack(self):
        files = {
            "config/server.toml": "a", "config/entrelumen/rules.json": "b", "config/entrelumen/client-preset.json": "x",
            "config/dynamic_fps.json": "x", "config/jei-client.ini": "x", "config/cumulus_menus-client.toml": "x",
            "config/fancymenu/options.txt": "x", "config/defaultoptions/options.txt": "x",
            "defaultconfigs/solcarrot-server.toml": "c", "kubejs/server_scripts/a.js": "d", "kubejs/data/x/y.json": "e",
            "kubejs/client_scripts/v.js": "x", "kubejs/assets/emi/z.json": "x", "resourcepacks/entrelumen/pack.mcmeta": "x",
        }
        for rel, text in files.items():
            path = self.root / "pack" / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)

    def fake_install(self, destination):
        mods = Path(destination) / "mods"
        mods.mkdir(parents=True, exist_ok=True)
        (mods / "dep-1.jar").write_bytes(b"dep")

    def build(self, **kwargs):
        self.fake_pack()
        with patch.object(bsp, "ROOT", self.root), patch.object(bsp, "install_jars", self.fake_install):
            return bsp.build(self.out, self.jar, **kwargs)

    def test_client_only_classification(self):
        for rel in ("resourcepacks/entrelumen/pack.mcmeta", "kubejs/client_scripts/a.js", "kubejs/assets/emi/a.json",
                    "config/fancymenu/x.txt", "config/defaultoptions/options.txt", "config/dynamic_fps.json",
                    "config/jei-client.ini", "config/ftbchunks-client.snbt", "config/entrelumen/client-preset.json"):
            self.assertTrue(bsp.is_client_only(rel), rel)
        for rel in ("config/ftbquests/quests/chapter.snbt", "kubejs/server_scripts/a.js", "kubejs/data/a/b.json",
                    "config/ftbchunks-world.snbt", "defaultconfigs/solcarrot-server.toml", "kubejs/README.md"):
            self.assertFalse(bsp.is_client_only(rel), rel)

    def test_template_has_required_server_settings(self):
        text = bsp.server_properties(False)
        values = dict(line.split("=", 1) for line in text.splitlines() if line and not line.startswith("#"))
        self.assertEqual(values["allow-flight"], "true")
        self.assertEqual(values["spawn-protection"], "0")
        self.assertEqual(values["simulation-distance"], "6")
        self.assertEqual(values["view-distance"], "10")
        self.assertEqual(values["max-players"], "10")
        self.assertEqual(values["server-ip"], "")
        self.assertEqual(values["level-seed"], "")
        self.assertIn("ENTRELUMEN", values["motd"])
        self.assertEqual(values["pvp"], "false")
        self.assertIn("pvp=true", bsp.server_properties(True))

    def test_build_layout_and_launchers(self):
        result = self.build()
        out = self.out
        self.assertEqual(result["packFiles"], 5)
        self.assertTrue((out / "mods/dep-1.jar").is_file())
        self.assertTrue((out / "mods/entrelumen-9.9.jar").is_file())
        for kept in ("config/server.toml", "config/entrelumen/rules.json", "defaultconfigs/solcarrot-server.toml",
                     "kubejs/server_scripts/a.js", "kubejs/data/x/y.json"):
            self.assertTrue((out / kept).is_file(), kept)
        for dropped in ("resourcepacks", "kubejs/client_scripts", "kubejs/assets", "config/fancymenu",
                        "config/defaultoptions", "config/dynamic_fps.json", "config/jei-client.ini",
                        "config/cumulus_menus-client.toml", "config/entrelumen/client-preset.json"):
            self.assertFalse((out / dropped).exists(), dropped)
        self.assertEqual((out / "eula.txt").read_text().splitlines()[-1], "eula=false")
        self.assertIn("-Xmx6G", (out / "user_jvm_args.txt").read_text())
        sh = (out / "start.sh").read_bytes()
        bat = (out / "start.bat").read_bytes()
        self.assertNotIn(b"\r", sh)
        self.assertTrue(bat.count(b"\r\n") == bat.count(b"\n") > 10)
        for script in (sh.decode(), bat.decode()):
            self.assertIn(bsp.NEOFORGE, script)
            self.assertIn("server.properties.template", script)
            self.assertIn("--installServer", script)
            self.assertIn("nogui", script)
            self.assertNotIn("@NEOFORGE@", script)
        self.assertIn("[ -f server.properties ] ||", sh.decode())
        self.assertIn("if not exist server.properties copy", bat.decode())
        self.assertEqual(json.loads((out / bsp.MARKER).read_text())["owner"], "entrelumen")

    def test_zip_keeps_launcher_executable(self):
        result = self.build(make_zip=True)
        with zipfile.ZipFile(result["zip"]) as bundle:
            names = bundle.namelist()
            self.assertIn("server-pack/start.sh", names)
            self.assertIn("server-pack/mods/entrelumen-9.9.jar", names)
            self.assertTrue(bundle.getinfo("server-pack/start.sh").external_attr >> 16 & 0o111)

    def test_rebuild_replaces_only_a_marked_folder(self):
        self.build()
        (self.out / "stale.txt").write_text("old")
        self.build(pvp=True)
        self.assertFalse((self.out / "stale.txt").exists())
        self.assertIn("pvp=true", (self.out / "server.properties.template").read_text())

    def test_refuses_unsafe_destinations(self):
        self.fake_pack()
        with patch.object(bsp, "ROOT", self.root):
            with self.assertRaisesRegex(SystemExit, "repository tree"):
                bsp.check_destination(self.root / "pack" / "server-pack")
            with self.assertRaisesRegex(SystemExit, "contains the repository"):
                bsp.check_destination(self.base)
            qa = self.base / "qa" / "world"
            qa.mkdir(parents=True)
            (self.base / "qa" / ".entrelumen-test-server.json").write_text("{}")
            with self.assertRaisesRegex(SystemExit, "QA instance"):
                bsp.check_destination(qa)
            other = self.base / "elsewhere" / "stuff"
            other.mkdir(parents=True)
            (other / "keep.txt").write_text("mine")
            with self.assertRaisesRegex(SystemExit, "not a previous server pack"):
                bsp.check_destination(other)
        self.assertTrue((other / "keep.txt").exists())

    def test_install_reuses_curate_pack_server_side(self):
        seen = {}

        def fake_main():
            seen["argv"] = list(bsp.sys.argv)
            return 0

        with patch.object(bsp.curate_pack, "main", fake_main):
            bsp.install_jars(self.out)
        self.assertEqual(seen["argv"][1:], ["--side", "server", "--install", str(self.out)])
        with patch.object(bsp.curate_pack, "main", lambda: 1):
            with self.assertRaisesRegex(SystemExit, "rejected"):
                bsp.install_jars(self.out)

    def test_real_pack_layout_drops_client_files(self):
        pack = bsp.ROOT / "pack"
        with tempfile.TemporaryDirectory() as folder:
            count = bsp.copy_pack(pack, Path(folder))
            copied = {p.relative_to(folder).as_posix() for p in Path(folder).rglob("*") if p.is_file()}
        self.assertGreater(count, 100)
        self.assertTrue(any(c.startswith("kubejs/server_scripts/") for c in copied))
        self.assertTrue(any(c.startswith("kubejs/data/") for c in copied))
        self.assertFalse([c for c in copied if bsp.is_client_only(c)])
        for forbidden in ("resourcepacks/", "kubejs/client_scripts/", "kubejs/assets/", "config/fancymenu/",
                          "config/defaultoptions/", "config/dynamic_fps.json"):
            self.assertFalse([c for c in copied if c.startswith(forbidden)], forbidden)


if __name__ == "__main__":
    unittest.main()
