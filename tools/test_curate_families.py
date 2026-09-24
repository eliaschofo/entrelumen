"""Small synthetic-JAR regression tests for additive family curation."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).with_name('curate_pack.py')
SPEC = importlib.util.spec_from_file_location('curate_pack_under_test', SCRIPT)
curate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(curate)


class FamilyCurationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.catalog = self.root / 'catalog'
        self.source = self.root / 'reference'
        (self.catalog / 'families').mkdir(parents=True)
        (self.source / 'mods').mkdir(parents=True)
        self.addons = []
        self.write_json(self.source / 'manifest.json', {
            'minecraft': {
                'version': '1.21.1',
                'modLoaders': [{'id': 'neoforge-21.1.249'}],
            },
        })
        self.patch = patch.multiple(
            curate, CATALOG=self.catalog, CONTENT={}, QOL={}, CLIENT=set(),
            PERFORMANCE=set(), INFRA=set(), FAMILY_PINS={}, REPLACED={},
        )
        self.patch.start()
        self.addCleanup(self.patch.stop)

    @staticmethod
    def write_json(path, value):
        path.write_text(json.dumps(value) + '\n', encoding='utf-8')

    def add_jar(self, filename, mod_id, version, project_id, file_id):
        path = self.source / 'mods' / filename
        metadata = (
            'modLoader="javafml"\n'
            'loaderVersion="[1,)"\n'
            'license="MIT"\n'
            '[[mods]]\n'
            f'modId="{mod_id}"\n'
            f'version="{version}"\n'
            f'displayName="{mod_id}"\n'
        )
        with zipfile.ZipFile(path, 'w') as jar:
            info = zipfile.ZipInfo('META-INF/neoforge.mods.toml', (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(info, metadata)
        contents = path.read_bytes()
        self.addons.append({
            'fileNameOnDisk': filename,
            'isEnabled': True,
            'addonID': project_id,
            'webSiteURL': f'https://www.curseforge.com/minecraft/mc-mods/{mod_id}',
            'allowModDistribution': True,
            'installedFile': {
                'id': file_id,
                'downloadUrl': f'https://edge.forgecdn.net/files/{file_id}/{filename}',
                'hashes': [{'type': 1, 'value': hashlib.sha1(contents).hexdigest()}],
                'dependencies': [],
            },
        })
        return {
            'modIds': [mod_id],
            'filename': filename,
            'sha256': hashlib.sha256(contents).hexdigest(),
            'projectID': project_id,
            'fileID': file_id,
        }

    def sync_instance(self):
        self.write_json(self.source / 'minecraftinstance.json', {
            'installedAddons': self.addons,
        })

    def write_family(self, pin):
        self.write_json(self.catalog / 'families' / 'magic.json', {
            'schemaVersion': 1,
            'content': {'magic': ['II', 'Synthetic magic', 'Synthetic workshop']},
            'pins': [pin],
        })

    def test_pinned_version_wins_over_newer_available_file(self):
        pin = self.add_jar('magic-1.jar', 'magic', '1.0', 100, 10)
        self.add_jar('magic-2.jar', 'magic', '2.0', 100, 20)
        self.sync_instance()
        self.write_family(pin)
        curate.load_families()

        curate.refresh(self.source)

        lock = curate.read_json(self.catalog / 'curated.json')
        self.assertEqual([entry['filename'] for entry in lock['mods']], ['magic-1.jar'])
        self.assertEqual(lock['mods'][0]['sha256'], pin['sha256'])
        self.assertEqual(lock['mods'][0]['fileID'], pin['fileID'])
        self.assertEqual(lock['missing'], [])

    def test_invalid_pin_does_not_replace_existing_full_refresh_files(self):
        pin = self.add_jar('magic-1.jar', 'magic', '1.0', 100, 10)
        self.sync_instance()
        lock_path = self.catalog / 'curated.json'
        paths_path = self.catalog / 'local-paths.json'
        lock_path.write_bytes(b'{ "mods": [], "sentinel": "old lock" }\n')
        paths_path.write_bytes(b'{ "sentinel": "old paths" }\n')
        before_lock, before_paths = lock_path.read_bytes(), paths_path.read_bytes()

        for change in ({'sha256': '0' * 64}, {'fileID': 11}):
            with self.subTest(change=change):
                self.write_family(dict(pin, **change))
                curate.CONTENT.clear()
                curate.FAMILY_PINS.clear()
                curate.load_families()
                with self.assertRaisesRegex(ValueError, 'Curation rejected before writing'):
                    curate.refresh(self.source)
                self.assertEqual(lock_path.read_bytes(), before_lock)
                self.assertEqual(paths_path.read_bytes(), before_paths)

    def test_additive_mode_keeps_complete_old_entry_with_newer_file_available(self):
        curate.CONTENT['base'] = ('I', 'Existing role', 'Existing integration')
        self.add_jar('base-1.jar', 'base', '1.0', 200, 10)
        self.sync_instance()
        curate.refresh(self.source)
        before = curate.read_json(self.catalog / 'curated.json')
        old_entry = copy.deepcopy(before['mods'][0])
        old_path = curate.read_json(self.catalog / 'local-paths.json')['base-1.jar']

        self.add_jar('base-2.jar', 'base', '2.0', 200, 20)
        pin = self.add_jar('magic-1.jar', 'magic', '1.0', 100, 30)
        self.sync_instance()
        self.write_family(pin)
        curate.load_families()
        curate.refresh(self.source, preserve=True)

        after = curate.read_json(self.catalog / 'curated.json')
        by_name = {entry['filename']: entry for entry in after['mods']}
        self.assertEqual(set(by_name), {'base-1.jar', 'magic-1.jar'})
        self.assertEqual(by_name['base-1.jar'], old_entry)
        self.assertEqual(curate.read_json(self.catalog / 'local-paths.json')['base-1.jar'], old_path)
        self.assertEqual(by_name['magic-1.jar']['sha256'], pin['sha256'])
        self.assertEqual(after['missing'], [])


    def add_download(self, filename, mod_id, version):
        """An official Modrinth download in catalog/downloads, outside the CurseForge reference."""
        (self.catalog / 'downloads').mkdir(exist_ok=True)
        path = self.catalog / 'downloads' / filename
        with zipfile.ZipFile(path, 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml',
                         f'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="{mod_id}"\n'
                         f'version="{version}"\ndisplayName="{mod_id}"\n')
        data = path.read_bytes()
        return {
            'modIds': [mod_id], 'filename': filename, 'provider': 'modrinth',
            'sha256': hashlib.sha256(data).hexdigest(), 'projectId': 'AbCdEf12', 'versionId': 'Gh34Ij56',
            'downloadUrl': f'https://cdn.modrinth.com/data/AbCdEf12/versions/Gh34Ij56/{filename}',
            'sourceSha1': hashlib.sha1(data).hexdigest(), 'sourceSha512': hashlib.sha512(data).hexdigest(),
        }

    def test_modrinth_pin_is_locked_with_its_official_version_and_both_hashes(self):
        self.sync_instance()
        pin = self.add_download('garden-1.jar', 'garden', '1.0')
        self.write_json(self.catalog / 'families' / 'garden.json', {
            'schemaVersion': 1,
            'content': {'garden': ['I', 'Synthetic garden', 'Synthetic kitchen']},
            'pins': [pin],
        })
        curate.load_families()

        curate.refresh(self.source)

        lock = curate.read_json(self.catalog / 'curated.json')
        entry = lock['mods'][0]
        self.assertEqual(entry['filename'], 'garden-1.jar')
        self.assertIsNone(entry['projectID'])
        self.assertEqual(entry['source']['provider'], 'modrinth')
        self.assertEqual(entry['source']['versionId'], pin['versionId'])
        self.assertEqual(entry['source']['sourceSha512'], pin['sourceSha512'])
        self.assertEqual(entry['sourceSha1'], pin['sourceSha1'])
        paths = curate.read_json(self.catalog / 'local-paths.json')
        self.assertEqual(curate.check(lock, paths, 'server')[1], [])

    def test_modrinth_pin_rejects_changed_bytes_or_version(self):
        self.sync_instance()
        pin = self.add_download('garden-1.jar', 'garden', '1.0')
        curate.CONTENT['garden'] = ('I', 'Synthetic garden', 'Synthetic kitchen')
        for change in ({'sourceSha512': '0' * 128}, {'sourceSha1': '0' * 40}, {'sha256': '0' * 64}):
            with self.subTest(change=change):
                curate.FAMILY_PINS.clear()
                curate.FAMILY_PINS['garden-1.jar'] = dict(pin, **change)
                with self.assertRaises(ValueError):
                    curate.refresh(self.source)
        curate.FAMILY_PINS['garden-1.jar'] = pin
        curate.refresh(self.source)
        lock = curate.read_json(self.catalog / 'curated.json')
        paths = curate.read_json(self.catalog / 'local-paths.json')
        curate.FAMILY_PINS['garden-1.jar'] = dict(pin, versionId='Other123')
        self.assertIn('Pinned family dependency changed: garden-1.jar', curate.check(lock, paths, 'client')[1])

    def test_modrinth_pin_needs_official_cdn_and_hashes(self):
        pin = self.add_download('garden-1.jar', 'garden', '1.0')
        self.assertTrue(curate.valid_pin_source(pin))
        for change in ({'downloadUrl': 'https://example.com/garden-1.jar'}, {'sourceSha1': ''}, {'versionId': ''}):
            with self.subTest(change=change):
                self.assertFalse(curate.valid_pin_source(dict(pin, **change)))

    def add_library_download(self, filename, provided_id):
        """A library-only JAR (like Kotlin for Forge): no top-level [[mods]], one JarJar provider."""
        (self.catalog / 'downloads').mkdir(exist_ok=True)
        inner = io.BytesIO()
        with zipfile.ZipFile(inner, 'w') as nested:
            nested.writestr('META-INF/neoforge.mods.toml',
                            f'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="LGPL"\n[[mods]]\n'
                            f'modId="{provided_id}"\nversion="5.0"\n')
        path = self.catalog / 'downloads' / filename
        with zipfile.ZipFile(path, 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml', 'modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="LGPL"\n')
            jar.writestr('META-INF/jarjar/lib.jar', inner.getvalue())
            jar.writestr('META-INF/jarjar/metadata.json', json.dumps({'jars': [{
                'identifier': {'group': 'x', 'artifact': 'lib'}, 'version': {'range': '[5,)', 'artifactVersion': '5.0'},
                'path': 'META-INF/jarjar/lib.jar'}]}))
        data = path.read_bytes()
        return {'modIds': [provided_id], 'filename': filename, 'provider': 'modrinth',
                'sha256': hashlib.sha256(data).hexdigest(), 'projectId': 'Kt12Kt12', 'versionId': 'Kv34Kv34',
                'downloadUrl': f'https://cdn.modrinth.com/data/Kt12Kt12/versions/Kv34Kv34/{filename}',
                'sourceSha1': hashlib.sha1(data).hexdigest(), 'sourceSha512': hashlib.sha512(data).hexdigest()}

    def test_library_only_jar_resolves_as_both_sides_dependency(self):
        self.sync_instance()
        (self.catalog / 'downloads').mkdir(exist_ok=True)
        slicer = self.catalog / 'downloads' / 'slicer-1.jar'
        with zipfile.ZipFile(slicer, 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml',
                         'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="slicer"\n'
                         'version="1.0"\n[[dependencies.slicer]]\nmodId="kotlinlib"\ntype="required"\n'
                         'versionRange="[5,)"\nside="BOTH"\n')
        data = slicer.read_bytes()
        slicer_pin = {'modIds': ['slicer'], 'filename': 'slicer-1.jar', 'provider': 'modrinth',
                      'sha256': hashlib.sha256(data).hexdigest(), 'projectId': 'Sl12Sl12', 'versionId': 'Sv34Sv34',
                      'downloadUrl': 'https://cdn.modrinth.com/data/Sl12Sl12/versions/Sv34Sv34/slicer-1.jar',
                      'sourceSha1': hashlib.sha1(data).hexdigest(), 'sourceSha512': hashlib.sha512(data).hexdigest()}
        library_pin = self.add_library_download('kotlinlib-all.jar', 'kotlinlib')
        self.write_json(self.catalog / 'families' / 'kitchen.json', {
            'schemaVersion': 1, 'content': {'slicer': ['II', 'Synthetic slicer', 'Synthetic kitchen']},
            'pins': [slicer_pin, library_pin]})
        curate.load_families()

        curate.refresh(self.source)

        lock = curate.read_json(self.catalog / 'curated.json')
        by_name = {entry['filename']: entry for entry in lock['mods']}
        self.assertEqual(set(by_name), {'slicer-1.jar', 'kotlinlib-all.jar'})
        self.assertEqual(by_name['kotlinlib-all.jar']['side'], 'both')
        self.assertEqual([role['modId'] for role in by_name['kotlinlib-all.jar']['roles']], ['kotlinlib'])
        paths = curate.read_json(self.catalog / 'local-paths.json')
        self.assertEqual(curate.check(lock, paths, 'server')[1], [])

    def test_family_replacement_swaps_one_old_entry_and_is_enforced(self):
        curate.CONTENT['base'] = ('I', 'Existing role', 'Existing integration')
        curate.CONTENT['lib'] = ('I', 'Old library', 'Old library')
        self.add_jar('base-1.jar', 'base', '1.0', 200, 10)
        self.add_jar('lib-1.jar', 'lib', '1.0', 300, 11)
        self.sync_instance()
        curate.refresh(self.source)
        del curate.CONTENT['lib']
        before = curate.read_json(self.catalog / 'curated.json')
        base_entry = copy.deepcopy(next(e for e in before['mods'] if e['filename'] == 'base-1.jar'))

        new_lib = self.add_jar('lib-2.jar', 'lib', '2.0', 300, 12)
        self.sync_instance()
        self.write_json(self.catalog / 'families' / 'magic.json', {
            'schemaVersion': 1, 'content': {'lib': ['II', 'Newer library', 'Required by a newer addon']},
            'replaces': [{'filename': 'lib-1.jar', 'by': 'lib-2.jar', 'why': 'Newer addon needs lib 2'}],
            'pins': [new_lib]})
        curate.load_families()
        curate.refresh(self.source, preserve=True)

        after = curate.read_json(self.catalog / 'curated.json')
        by_name = {entry['filename']: entry for entry in after['mods']}
        self.assertEqual(set(by_name), {'base-1.jar', 'lib-2.jar'})
        self.assertEqual(by_name['base-1.jar'], base_entry)
        paths = curate.read_json(self.catalog / 'local-paths.json')
        self.assertNotIn('lib-1.jar', paths)
        paths['lib-1.jar'] = str(self.source / 'mods' / 'lib-1.jar')
        self.assertIn('Replaced family dependency still locked: lib-1.jar (replaced by lib-2.jar)',
                      curate.check(before, paths, 'client')[1])

    def test_replacement_must_name_a_pin_of_the_same_family(self):
        pin = self.add_jar('magic-1.jar', 'magic', '1.0', 100, 10)
        for replaced in ({'filename': 'old.jar', 'by': 'other.jar', 'why': 'x'},
                         {'filename': 'old.jar', 'by': 'magic-1.jar'},
                         {'filename': 'magic-1.jar', 'by': 'magic-1.jar', 'why': 'x'}):
            with self.subTest(replaced=replaced):
                self.write_json(self.catalog / 'families' / 'magic.json', {
                    'schemaVersion': 1, 'content': {'magic': ['II', 'Synthetic', 'Synthetic']},
                    'replaces': [replaced], 'pins': [pin]})
                curate.CONTENT.clear()
                curate.FAMILY_PINS.clear()
                curate.REPLACED.clear()
                with self.assertRaisesRegex(ValueError, 'Invalid replacement'):
                    curate.load_families()


if __name__ == '__main__':
    unittest.main()
