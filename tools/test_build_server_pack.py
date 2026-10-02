"""Exercise the server-pack builder against fake trees and the real pack/ layout; no server is started."""
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
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

    def test_rebuild_replaces_a_pristine_marked_folder(self):
        self.build()
        self.build(pvp=True)
        self.assertIn("pvp=true", (self.out / "server.properties.template").read_text())

    def test_marker_records_what_the_builder_wrote(self):
        self.build()
        marker = json.loads((self.out / bsp.MARKER).read_text())
        self.assertTrue(marker["complete"])
        self.assertIn("mods/entrelumen-9.9.jar", marker["files"])
        self.assertIn("start.sh", marker["files"])
        self.assertNotIn(bsp.MARKER, marker["files"])
        self.assertIn("mods", marker["dirs"])
        digest = bsp.hashlib.sha256((self.out / "eula.txt").read_bytes()).hexdigest()
        self.assertEqual(marker["files"]["eula.txt"]["sha256"], digest)

    def test_rebuild_refuses_a_used_folder_and_keeps_it(self):
        self.build()
        (self.out / "world").mkdir()
        (self.out / "world" / "level.dat").write_text("save")
        (self.out / "server.properties").write_text("motd=mine\n")
        (self.out / "ops.json").write_text("[]")
        (self.out / "libraries" / "x").mkdir(parents=True)
        with self.assertRaisesRegex(SystemExit, "--replace") as caught:
            self.build(pvp=True)
        for name in ("world/level.dat", "server.properties", "ops.json", "libraries/"):
            self.assertIn(name, str(caught.exception))
        self.assertEqual((self.out / "world" / "level.dat").read_text(), "save")
        self.assertEqual((self.out / "server.properties").read_text(), "motd=mine\n")
        self.assertTrue((self.out / "libraries" / "x").is_dir())
        self.assertTrue((self.out / "mods/entrelumen-9.9.jar").is_file())

    def test_rebuild_refuses_a_folder_whose_file_was_edited(self):
        self.build()
        (self.out / "eula.txt").write_text("eula=true\n")
        with self.assertRaisesRegex(SystemExit, "eula.txt"):
            self.build()
        self.assertEqual((self.out / "eula.txt").read_text(), "eula=true\n")

    def test_rebuild_refuses_an_extra_empty_folder(self):
        self.build()
        (self.out / "logs").mkdir()
        with self.assertRaisesRegex(SystemExit, "logs/"):
            self.build()

    def test_replace_flag_wipes_a_used_folder(self):
        self.build()
        (self.out / "world").mkdir()
        (self.out / "world" / "level.dat").write_text("save")
        (self.out / "server.properties").write_text("motd=mine\n")
        self.build(replace=True)
        self.assertFalse((self.out / "world").exists())
        self.assertFalse((self.out / "server.properties").exists())
        self.assertTrue((self.out / "start.sh").is_file())

    def test_replace_flag_does_not_widen_to_unmarked_folders(self):
        self.fake_pack()
        self.out.mkdir(parents=True)
        (self.out / "keep.txt").write_text("mine")
        with patch.object(bsp, "ROOT", self.root):
            with self.assertRaisesRegex(SystemExit, "not a previous server pack"):
                bsp.check_destination(self.out, replace=True)
        self.assertTrue((self.out / "keep.txt").exists())

    def test_old_marker_without_a_file_list_needs_replace(self):
        self.build()
        (self.out / bsp.MARKER).write_text(json.dumps({"owner": "entrelumen", "kind": "server-pack", "neoforge": "x"}))
        with self.assertRaisesRegex(SystemExit, "--replace"):
            self.build()
        self.build(replace=True)
        self.assertTrue(json.loads((self.out / bsp.MARKER).read_text())["complete"])

    def test_unfinished_build_can_be_redone(self):
        self.build()
        (self.out / bsp.MARKER).write_text(json.dumps({"owner": "entrelumen", "kind": "server-pack", "complete": False}))
        (self.out / "half.txt").write_text("partial")
        self.build()
        self.assertFalse((self.out / "half.txt").exists())

    def test_unfinished_build_that_was_started_is_refused(self):
        # The launchers exist before the final marker scan: a started half-build holds a world to keep.
        self.build()
        (self.out / bsp.MARKER).write_text(json.dumps({"owner": "entrelumen", "kind": "server-pack", "complete": False}))
        (self.out / "world").mkdir()
        (self.out / "world" / "level.dat").write_text("save")
        (self.out / "server.properties").write_text("motd=mine\n")
        with self.assertRaisesRegex(SystemExit, "--replace") as caught:
            self.build()
        self.assertIn("world/", str(caught.exception))
        self.assertIn("server.properties", str(caught.exception))
        self.assertEqual((self.out / "world" / "level.dat").read_text(), "save")
        self.build(replace=True)
        self.assertFalse((self.out / "world").exists())

    def test_rebuild_refuses_a_link_inside_the_folder(self):
        self.build()
        target = self.base / "elsewhere"
        target.mkdir()
        (target / "precious.txt").write_text("keep")
        link = self.out / "link"
        if os.name == "nt":  # a junction needs no privilege, and it is the link kind that bit us before
            made = subprocess.run(["cmd", "/c", "mklink", "/J", str(link), str(target)], capture_output=True).returncode == 0
        else:
            os.symlink(target, link, target_is_directory=True)
            made = True
        if not made:
            self.skipTest("cannot create a link here")
        self.addCleanup(lambda: os.rmdir(link) if link.exists() else None)  # unlink first, never recurse through it
        with self.assertRaisesRegex(SystemExit, "link"):
            self.build(replace=True)
        self.assertTrue((target / "precious.txt").exists())

    def test_zip_never_overwrites_a_foreign_archive(self):
        self.build()
        archive = Path(str(self.out) + ".zip")
        archive.write_bytes(b"someone else's backup")
        (self.out / "extra").mkdir()
        # the unrelated zip must survive, and the folder must not be deleted before the refusal
        with self.assertRaisesRegex(SystemExit, "not a server pack archive"):
            self.build(make_zip=True, replace=True)
        self.assertEqual(archive.read_bytes(), b"someone else's backup")
        self.assertTrue((self.out / "extra").is_dir())

    def zip_used_folder(self):
        """A backup of a used pack folder, made like Explorer's "Compress to ZIP": the builder's marker comes along."""
        self.build()
        (self.out / "world").mkdir()
        (self.out / "world" / "level.dat").write_text("save")
        archive = Path(str(self.out) + ".zip")
        with zipfile.ZipFile(archive, "w") as bundle:
            for path in sorted(self.out.rglob("*")):
                if path.is_file():
                    bundle.write(path, Path(self.out.name, path.relative_to(self.out)).as_posix())
        return archive

    def test_zip_never_overwrites_a_zipped_backup_of_a_used_folder(self):
        archive = self.zip_used_folder()
        backup = archive.read_bytes()
        shutil.rmtree(self.out)  # the folder moved away: the zip is the only copy of the world
        with self.assertRaisesRegex(SystemExit, "not a server pack archive"):
            self.build(make_zip=True)
        self.assertEqual(archive.read_bytes(), backup)

    def test_zip_never_overwrites_a_zipped_backup_even_with_replace(self):
        archive = self.zip_used_folder()
        backup = archive.read_bytes()
        with self.assertRaisesRegex(SystemExit, "not a server pack archive"):
            self.build(make_zip=True, replace=True)
        self.assertEqual(archive.read_bytes(), backup)
        self.assertTrue((self.out / "world" / "level.dat").is_file(), "the folder went before the zip refusal")

    def test_zip_never_overwrites_its_own_archive_once_changed(self):
        result = self.build(make_zip=True)
        archive = Path(result["zip"])
        with zipfile.ZipFile(archive, "a") as bundle:
            bundle.writestr("server-pack/world/level.dat", "save")
        with self.assertRaisesRegex(SystemExit, "not a server pack archive"):
            self.build(make_zip=True)
        with zipfile.ZipFile(archive) as bundle:
            self.assertIn("server-pack/world/level.dat", bundle.namelist())

    def test_zip_replaces_its_own_previous_archive(self):
        self.build(make_zip=True)
        second = self.build(pvp=True, make_zip=True)
        with zipfile.ZipFile(second["zip"]) as bundle:
            self.assertIn("server-pack/" + bsp.MARKER, bundle.namelist())
            self.assertIn(b"pvp=true", bundle.read("server-pack/server.properties.template"))
        record = json.loads(Path(second["zip"] + bsp.ZIP_RECORD_SUFFIX).read_text())
        self.assertEqual(record["zip"], bsp.file_digest(Path(second["zip"])))

    def test_output_is_required(self):
        with patch.object(bsp.sys, "argv", ["build_server_pack.py", "--zip"]), patch.object(bsp.sys, "stderr", io.StringIO()):
            with self.assertRaises(SystemExit) as caught:
                bsp.main()
        self.assertEqual(caught.exception.code, 2)

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

class LauncherTest(unittest.TestCase):
    """Run the generated launchers against a fake java on PATH: no network, no installer, no server."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.pack = self.base / "pack"
        self.pack.mkdir()
        bsp.write_launchers(self.pack, False)
        self.fake_bin = self.base / "bin"
        self.fake_bin.mkdir()

    def fake_java(self, banner, windows):
        if windows:
            text = ("@echo off\r\nif \"%1\"==\"-version\" (\r\n  echo " + banner + " 1>&2\r\n  exit /b 0\r\n)\r\n"
                    "echo FAKE-JAVA-RAN %*\r\n")
            (self.fake_bin / "java.cmd").write_bytes(text.encode())
        else:
            script = self.fake_bin / "java"
            text = ("#!/bin/sh\nif [ \"$1\" = \"-version\" ]; then echo '" + banner + "' >&2; exit 0; fi\n"
                    "echo FAKE-JAVA-RAN \"$@\"\n")
            script.write_bytes(text.encode())
            script.chmod(0o755)

    def args_file(self, name):
        path = self.pack / "libraries/net/neoforged/neoforge" / bsp.NEOFORGE / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("-Dfake=1\n")

    def run_launcher(self, command):
        env = dict(os.environ, PATH=str(self.fake_bin) + os.pathsep + os.environ["PATH"])
        return subprocess.run(command, cwd=self.pack, env=env, capture_output=True, text=True, timeout=60)

    def check(self, command, windows):
        self.fake_java('openjdk version "17.0.11" 2024-04-16', windows)
        old = self.run_launcher(command)
        self.assertNotEqual(old.returncode, 0)
        self.assertIn("Java 21 or newer", old.stdout + old.stderr)
        self.assertNotIn("FAKE-JAVA-RAN", old.stdout)
        self.assertFalse((self.pack / "server.properties").exists())
        self.assertFalse(list(self.pack.glob("*installer*")))
        for banner in ('openjdk version "21.0.12" 2025-07-15 LTS', 'java version "25" 2026-09-15 LTS'):
            self.fake_java(banner, windows)
            ok = self.run_launcher(command)
            self.assertEqual(ok.returncode, 0, ok.stdout + ok.stderr)
            self.assertIn("FAKE-JAVA-RAN", ok.stdout)
            self.assertIn("nogui", ok.stdout)
            self.assertTrue((self.pack / "server.properties").is_file())
            self.assertIn("eula=false", (self.pack / "eula.txt").read_text())

    def test_start_sh_checks_the_java_version(self):
        sh = shutil.which("sh")
        if not sh:
            self.skipTest("no sh available")
        self.args_file("unix_args.txt")
        self.check([sh, "start.sh"], windows=False)

    @unittest.skipUnless(os.name == "nt", "start.bat needs cmd.exe")
    def test_start_bat_checks_the_java_version(self):
        self.args_file("win_args.txt")
        self.check(["cmd", "/c", ".\\start.bat"], windows=True)

    def test_start_sh_keeps_an_existing_server_properties(self):
        sh = shutil.which("sh")
        if not sh:
            self.skipTest("no sh available")
        self.args_file("unix_args.txt")
        self.fake_java('openjdk version "21.0.12" 2025-07-15 LTS', windows=False)
        (self.pack / "server.properties").write_text("motd=mine\n")
        self.assertEqual(self.run_launcher([sh, "start.sh"]).returncode, 0)
        self.assertEqual((self.pack / "server.properties").read_text(), "motd=mine\n")


if __name__ == "__main__":
    unittest.main()
