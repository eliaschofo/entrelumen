"""Curate licensed local dependencies without copying another pack's content.

Refresh reads only CurseForge metadata and JAR metadata. Install is additive and
refuses to replace differing files. This never creates a CurseForge manifest.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'catalog'
BUILTINS = {'minecraft', 'neoforge', 'java'}

# Intent is pack-original. Version, license, IDs and URLs come from source metadata.
CONTENT = {
    'create_connected': ('II', 'Compact mechanical controls and drivetrain tools', 'Build readable Create workshops and regulate machines serving Atlas component production'),
    'copycats': ('II', 'Material-matched moving mechanisms and construction shapes', 'Clad Create shafts, pipes and contraptions to match player-built observatories; avoid redundant Connected copycats'),
    'mcwroofs': ('I', 'Purpose-built roofs and gutters', 'Finish expedition shelters and later inhabited Ark buildings without compulsory decoration ingredients'),
    'mcwbridges': ('I', 'Walkable bridges and railings', 'Join outposts, farms and workshop walkways while preserving player choice of materials'),
    'exposure': ('I-IV', 'Physical photography and expedition albums', 'Keep field records of discoveries and reconstruction, then exhibit them in the habitation district'),
    'immersive_aircraft': ('III', 'Fuelled expedition aircraft', 'Use a staged workshop engine to survey routes, transport explorers and connect distant provisioned outposts'),
    'ae2wtlib': ('III-V', 'Combined wireless terminals and upgrades', 'Provide real terminal combination serializers for AdvancedAE and retain pattern logistics in advanced workshops'),
    'mekmm': ('III-V', 'Additional chemical processing factories', 'Staged factory acquisition; replication disabled by replacing data maps and recipe removals; runtime balance audit required'),
    'jamd': ('II', 'Separate mining dimension', 'Supply reconstruction stone and ores without excavating inhabited landscapes'),
    'botanypots': ('I', 'Compact food and botanical cultivation', 'Provision kitchens and expeditions; hopper acquisition in act II'),
    'botanypotstiers': ('III', 'Tiered compact cultivation', 'Scale botanical supply with staged acquisition and measured throughput'),
    'modularbees': ('IV', 'Modular productive apiaries and centrifuges', 'Consolidate bee production for nature and industrial Ark components'),
    'extendedae': ('III', 'Expanded pattern handling and machines', 'Scale Atlas component workshops; assembler matrix in act V'),
    'advanced_ae': ('IV', 'Directional patterns and quantum crafting', 'Route chemical production; quantum armor obtained in VI'),
    'appmek': ('III', 'Chemical ME logistics', 'Connect Mekanism processing with AE2 requests'),
    'megacells': ('IV', 'High capacity cells and crafting CPUs', 'Consolidate advanced production with chemical cell integration'),
    'mekanism_extras': ('V', 'Advanced factories and transport tiers', 'Compact industrial Ark production; maximum tiers in VI'),
    'create': ('II', 'Precision manufacturing', 'Produce mechanical instruments for Atlas projects'),
    'mekanism': ('III', 'Chemical and industrial processing', 'Supply advanced alloys to the engineering Ark module'),
    'mekanismgenerators': ('III', 'Scalable energy', 'Power industrial reconstruction without mandatory reactors'),
    'ae2': ('III', 'Digital logistics', 'Coordinate inventories used by all six Ark disciplines'),
    'immersiveengineering': ('II', 'Visible electrical engineering', 'Bulk materials and wiring for observatory infrastructure'),
    'pneumaticcraft': ('III', 'Pressure automation', 'Precision logistics and pressure-made components'),
    'actuallyadditions': ('II', 'Laser conversion and compact production', 'Retain conversion recipes in advanced instruments'),
    'ars_nouveau': ('II', 'Spellcraft and source automation', 'Magic instruments and automated botanical supplies'),
    'occultism': ('II', 'Ritual craft and spirits', 'Spirit processing for the magic Ark module'),
    'malum': ('IV', 'Soul research', 'Alternative late magic research and stabilization'),
    'farmersdelight': ('I', 'Cooking and provisions', 'Expedition meals and the habitability Ark module'),
    'productivebees': ('III', 'Bee breeding and resources', 'Staged biological inputs and nature research'),
    'mysticalagriculture': ('III', 'Staged resource crops', 'Crop specialization as an alternative resource route'),
    'naturesaura': ('II', 'Environmental magic', 'Nature restoration and sustainable ritual materials'),
    'evilcraft': ('IV', 'Blood machinery', 'Alternative specialized transformation research'),
    'integrateddynamics': ('III', 'Logic and measurement', 'Instrument reconstruction systems'),
    'integratedtunnels': ('III', 'Programmable transport', 'Alternative precise material logistics'),
    'modularrouters': ('III', 'Compact item automation', 'Reduce entity-heavy automation in reconstruction projects'),
    'laserio': ('III', 'Compact channel logistics', 'Connect production disciplines with bounded networks'),
    'pipez': ('II', 'Introductory transport', 'Teach inventories before advanced logistics'),
    'functionalstorage': ('I', 'Bulk storage', 'Keep foundational material buffers useful at endgame'),
    'sophisticatedstorage': ('I', 'Configurable workshop storage', 'Upgrade existing workshop organization'),
    'sophisticatedbackpacks': ('I', 'Portable inventory', 'Expedition organization with staged advanced upgrades'),
    'cookingforblockheads': ('I', 'Practical kitchen', 'Teach shared provisioning infrastructure'),
    'farmingforblockheads': ('I', 'Accessible farming', 'Establish food production and garden planning'),
    'aquaculture': ('I', 'Fishing variety', 'Food and collection routes for coastal expeditions'),
    'supplementaries': ('I', 'Utility decoration', 'Useful infrastructure for inhabited reconstruction sites'),
    'amendments': ('I', 'Interactive building details', 'Make habitability projects tangible'),
    'chipped': ('I', 'Architectural materials', 'Express each reconstructed settlement identity'),
    'framedblocks': ('I', 'Custom construction shapes', 'Build the player-designed observatory and Ark shell'),
    'handcrafted': ('I', 'Interior furnishings', 'Optional habitation and storytelling spaces'),
    'rechiseled': ('I', 'Material variants', 'Optional cohesive copper and stone architecture'),
    'buildinggadgets2': ('III', 'Powered construction tools', 'Build large structures using supplied materials'),
    'rftoolsbase': ('II-III', 'McJty machine frames and instruments', 'Provide the common material and machine base for bounded building, routing and power projects'),
    'rftoolsbuilder': ('III', 'Powered construction and quarry cards', 'Build player-designed infrastructure and mine JAMD for named reconstruction projects'),
    'rftoolsutility': ('III-IV', 'Powered logic and finite services', 'Add measured screens, teleport routes and material-fed spawning for provisioned facilities'),
    'rftoolspower': ('II-III', 'Fuelled generation and energy buffers', 'Keep early finite power useful and stage wireless distribution for larger workshops'),
    'xnet': ('III', 'Multichannel logic and routing', 'Coordinate item, fluid, energy and signal paths between specialized workshops'),
    'constructionstick': ('I', 'Material-consuming construction assistance', 'Reduce repetitive placement from the first base'),
    'waystones': ('II', 'Established travel routes', 'Connect discovered expedition outposts'),
    'aether': ('IV', 'Sky expeditions', 'Recover observatory knowledge through a distinct dimension'),
    'twilightforest': ('IV', 'Structured adventure', 'Spaced encounters for exploration research'),
    'the_bumblezone': ('III', 'Pollinator expeditions', 'Link bees and nature module research'),
    'computercraft': ('III', 'Optional programming', 'Monitor workshop systems without making coding mandatory'),
}
QOL = {
    'defaultoptions': 'Apply packaged first-launch defaults while preserving existing player settings',
    'cleanswing': 'Attack entities through replaceable grass without changing damage',
    'crafting_on_a_stick': 'Portable vanilla workstations with native costs',
    'smithingtemplateviewer': 'Preview smithing templates while planning upgrades',
    'akashictome': 'Keep acquired guidebooks in one physical reference tome',
    'chat_heads': 'Identify cooperative chat speakers',
    'betterpingdisplay': 'Read numerical latency in the player list',
    'cherishedworlds': 'Favorite worlds and protect them from accidental deletion',
    'emi': 'Recipe trees and material planning', 'jei': 'Recipe plugin compatibility',
    'jade': 'Understand blocks, entities and machines', 'jadeaddons': 'Additional machine inspection',
    'invtweaks': 'Inventory sorting and restocking',
    'mousetweaks': 'Fast stack manipulation', 'polymorph': 'Resolve recipe conflicts explicitly',
    'controlling': 'Search and resolve keybinding conflicts', 'appleskin': 'Explain food and saturation',
    'craftingtweaks': 'Rotate and balance crafting ingredients', 'trashslot': 'Deliberate inventory disposal',
    'enchdesc': 'Explain enchantments',
    'jeed': 'Explain status effects through the existing EMI and JEI recipe viewers',
    'shulkerboxtooltip': 'Preview shulker and supported container contents on demand',
    'equipmentcompare': 'Compare hovered equipment with equipped items on demand',
    'journeymap': 'Map discoveries and expedition markers', 'naturescompass': 'Locate desired natural environments',
    'explorerscompass': 'Locate exploration destinations with balanced search configuration',
    'ftbultimine': 'Bounded connected mining with tool and hunger cost',
    'ftbchunks': 'Cooperative claims and permissions; bounded forced chunks',
    'comforts': 'Temporary expedition sleep without moving home',
    'lootr': 'Fair per-player expedition loot', 'tombstone': 'Recoverable death inventory; balance magic rewards',
    'netherportalfix': 'Reliable return portal links', 'clumps': 'Merge XP orbs to reduce entity cost',
    'betteradvancements': 'Readable advancement navigation', 'justenoughbreeding': 'Explain animal breeding',
    'justenoughprofessions': 'Explain villager jobs', 'jearchaeology': 'Explain archaeology targets',
    'jei_mekanism_multiblocks': 'Teach multiblock layouts', 'ae2jeiintegration': 'Explain AE2 processes',
    'extremesoundmuffler': 'Adjust repetitive machine audio', 'toastcontrol': 'Reduce distracting notifications',
    'justzoom': 'Accessible zoom', 'rebind_narrator': 'Prevent accidental narrator toggles',
    'moreoverlays': 'Inspect light and chunk boundaries', 'toolbelt': 'Quick tool selection',
    'cosmeticarmorreworked': 'Keep character appearance while upgrading equipment',
    'simplebackups': 'Recover local worlds using bounded retention',
    'lambdynlights': 'Dynamic light from held and worn light sources, including the luminous gear',
}
PERFORMANCE = {'sodium', 'modernfix', 'ferritecore', 'spark', 'immediatelyfast', 'fastsuite', 'fastfurnace', 'fastbench'}
INFRA = {'drippyloadingscreen', 'ftbteams', 'ftbquests', 'kubejs', 'almostunified', 'ponderjs', 'fancymenu'}
CLIENT = {'defaultoptions', 'drippyloadingscreen', 'smithingtemplateviewer', 'chat_heads', 'betterpingdisplay', 'cherishedworlds', 'emi', 'jei', 'mousetweaks', 'controlling', 'appleskin', 'trashslot',
          'shulkerboxtooltip', 'equipmentcompare', 'iceberg',
          'enchdesc', 'jeed', 'journeymap', 'betteradvancements', 'justenoughbreeding',
          'justenoughprofessions', 'jearchaeology', 'jei_mekanism_multiblocks', 'ae2jeiintegration',
          'extremesoundmuffler', 'toastcontrol', 'justzoom', 'rebind_narrator', 'moreoverlays',
          'sodium', 'immediatelyfast', 'fancymenu', 'konkrete', 'melody', 'searchables', 'lambdynlights'}
EXCLUDED = {'projecte', 'allthemodium', 'allthetweaks', 'alltheores', 'allthecompressed'}
FAMILY_PINS = {}


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write_json(path, data):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


def valid_pin_source(pin):
    """CurseForge pins name project/file IDs; Modrinth pins name the official version and both hashes.

    An optional `sourceSha512` on a CurseForge pin records the same bytes' official Modrinth SHA-512."""
    provider = pin.get('provider', 'curseforge')
    if provider == 'curseforge':
        return bool(pin.get('projectID') and pin.get('fileID')) and len(pin.get('sourceSha512', '0' * 128)) == 128
    if provider == 'modrinth':
        return (bool(pin.get('projectId') and pin.get('versionId'))
                and str(pin.get('downloadUrl', '')).startswith('https://cdn.modrinth.com/')
                and len(pin.get('sourceSha1', '')) == 40 and len(pin.get('sourceSha512', '')) == 128)
    return False


def pin_matches(entry, pin):
    """The lock entry comes from exactly the pinned provider file."""
    if entry.get('filename') != pin['filename']:
        return False
    if pin.get('provider', 'curseforge') == 'modrinth':
        source = entry.get('source', {})
        return (source.get('provider') == 'modrinth' and source.get('projectId') == pin['projectId']
                and source.get('versionId') == pin['versionId'] and entry.get('sourceSha1') == pin['sourceSha1']
                and source.get('sourceSha512') == pin['sourceSha512'])
    return entry.get('projectID') == pin['projectID'] and entry.get('fileID') == pin['fileID']


def modrinth_pin_entry(path, meta, pin):
    """Lock entry for an official Modrinth download pinned by a family (no CurseForge IDs are invented)."""
    return {'filename': path.name, 'bytes': path.stat().st_size, 'projectID': None, 'fileID': None,
            'sourceSha1': pin['sourceSha1'],
            'source': {'provider': 'modrinth', 'projectId': pin['projectId'], 'versionId': pin['versionId'],
                       'projectUrl': pin.get('projectUrl', f"https://modrinth.com/mod/{pin['projectId']}"),
                       'downloadUrl': pin['downloadUrl'],
                       'metadataUrl': f"https://api.modrinth.com/v2/version/{pin['versionId']}",
                       'sourceSha512': pin['sourceSha512'],
                       'declaredLicense': pin.get('license'),
                       'curseforgeMappingStatus': 'pending official App resolution'},
            'metadata': meta, 'cfRequiredProjects': [],
            'side': 'client' if all(m['id'] in CLIENT for m in meta['mods']) else 'both'}


def load_families():
    """Merge separately owned selections without allowing implicit version updates."""
    for path in sorted((CATALOG / 'families').glob('*.json')):
        family = read_json(path)
        if family.get('schemaVersion') != 1:
            raise ValueError(f'Unsupported family schema: {path.name}')
        for field, target in (('content', CONTENT), ('qol', QOL)):
            for mod_id, role in family.get(field, {}).items():
                if mod_id in target or mod_id in EXCLUDED:
                    raise ValueError(f'Duplicate or excluded family selection: {mod_id}')
                if field == 'content' and (not isinstance(role, list) or len(role) != 3
                        or not all(isinstance(value, str) and value for value in role)):
                    raise ValueError(f'Expected act, role and integration for {mod_id}')
                if field == 'qol' and (not isinstance(role, str) or not role):
                    raise ValueError(f'Expected QoL role for {mod_id}')
                target[mod_id] = role
        for field, target in (('clientOnly', CLIENT), ('performance', PERFORMANCE), ('infrastructure', INFRA)):
            target.update(family.get(field, []))
        for pin in family.get('pins', []):
            name = pin['filename']
            if (Path(name).name != name or not name.endswith('.jar')
                    or len(pin['sha256']) != 64 or not pin['modIds'] or not valid_pin_source(pin)):
                raise ValueError(f'Invalid dependency pin in {path.name}: {name}')
            if name in FAMILY_PINS and FAMILY_PINS[name] != pin:
                raise ValueError(f'Conflicting family pin: {name}')
            FAMILY_PINS[name] = pin
        pinned_ids = {mod_id for pin in family.get('pins', []) for mod_id in pin['modIds']}
        requested = set(family.get('content', {})) | set(family.get('qol', {}))
        if not requested <= pinned_ids:
            raise ValueError(f'Unpinned family selection in {path.name}: {sorted(requested - pinned_ids)}')


def jar_metadata(data):
    result = {'mods': [], 'dependencies': [], 'license': 'unknown', 'embedded': []}
    with zipfile.ZipFile(io.BytesIO(data) if isinstance(data, bytes) else data) as jar:
        for name in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
            if name not in jar.namelist():
                continue
            meta = tomllib.loads(jar.read(name).decode('utf-8-sig'))
            result['license'] = meta.get('license', 'unknown')
            manifest = jar.read('META-INF/MANIFEST.MF').decode('utf-8', errors='replace') if 'META-INF/MANIFEST.MF' in jar.namelist() else ''
            impl = next((x.split(': ', 1)[1] for x in manifest.splitlines() if x.startswith('Implementation-Version: ')), 'unknown')
            result['mods'] = [{'id': m['modId'], 'version': str(m.get('version', 'unknown')).replace('${file.jarVersion}', impl), 'name': m.get('displayName', m['modId'])} for m in meta.get('mods', [])]
            dep_groups = meta.get('dependencies') or {}
            if isinstance(dep_groups, list):
                dep_groups = {result['mods'][0]['id']: dep_groups}
            def flatten(value):
                if isinstance(value, list):
                    for child in value:
                        yield from flatten(child)
                elif isinstance(value, dict):
                    if 'modId' in value:
                        yield value
                    else:
                        for child in value.values():
                            yield from flatten(child)
            for owner, deps in dep_groups.items():
                for dep in flatten(deps):
                    result['dependencies'].append({'owner': owner, 'id': dep['modId'], 'required': str(dep.get('type', 'required' if dep.get('mandatory', True) else 'optional')).lower() == 'required', 'type': dep.get('type', ''), 'range': dep.get('versionRange', ''), 'side': dep.get('side', 'BOTH').lower()})
            break
        if 'META-INF/jarjar/metadata.json' in jar.namelist():
            for entry in json.loads(jar.read('META-INF/jarjar/metadata.json')).get('jars', []):
                path = entry.get('path')
                if path and path in jar.namelist():
                    nested = jar_metadata(jar.read(path))
                    result['embedded'].append({'path': path, 'artifact': entry.get('identifier', {}), 'version': entry.get('version', {}), **nested})
    return result


def provided(meta):
    return {m['id'] for m in meta['mods']} | set().union(*(provided(x) for x in meta['embedded']))


def dependencies(meta):
    return meta['dependencies'] + [d for x in meta['embedded'] for d in dependencies(x)]


def refresh(source, preserve=False):
    source = Path(source).resolve()
    previous_lock = read_json(CATALOG / 'curated.json') if preserve else None
    previous_paths = read_json(CATALOG / 'local-paths.json') if preserve else None
    manifest = read_json(source / 'manifest.json')
    instance = read_json(source / 'minecraftinstance.json')
    files = {a['fileNameOnDisk']: a for a in instance.get('installedAddons', []) if a.get('isEnabled', True)}
    if (CATALOG / 'external-sources.json').exists():
        for addon in read_json(CATALOG / 'external-sources.json'):
            files[addon['fileNameOnDisk']] = addon
    inventory, by_id, paths = [], {}, {}
    jar_paths = sorted((source / 'mods').glob('*.jar')) + sorted((CATALOG / 'downloads').glob('*.jar'))
    for path in jar_paths:
        try:
            meta = jar_metadata(path)
        except (ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
            raise RuntimeError(f'Invalid metadata {path.name}: {error}') from error
        if not meta['mods']:
            continue
        addon = files.get(path.name, {})
        cf = addon.get('installedFile', {})
        pin = FAMILY_PINS.get(path.name)
        if pin and pin.get('provider') == 'modrinth':
            entry = modrinth_pin_entry(path, meta, pin)
        else:
            entry = {'filename': path.name, 'bytes': path.stat().st_size,
                     'projectID': addon.get('addonID'), 'fileID': cf.get('id'),
                     'sourceSha1': next((h['value'] for h in cf.get('hashes', []) if h.get('type') == 1), None),
                     'source': {'provider': 'curseforge', 'projectUrl': addon.get('webSiteURL'), 'downloadUrl': cf.get('downloadUrl'), 'allowModDistribution': addon.get('allowModDistribution')},
                     'metadata': meta, 'cfRequiredProjects': [d['addonId'] for d in cf.get('dependencies', []) if d.get('type') == 3],
                     'side': 'client' if all(m['id'] in CLIENT for m in meta['mods']) else 'both'}
            if pin and pin.get('sourceSha512'):
                # Same bytes published on Modrinth: its SHA-512 is verified on every check.
                entry['source']['sourceSha512'] = pin['sourceSha512']
                entry['source']['modrinthMapping'] = pin.get('modrinthMapping')
        inventory.append(entry)
        paths[path.name] = str(path.resolve())
        for mod_id in provided(meta):
            by_id.setdefault(mod_id, []).append(entry)
    # Preserve explicitly locked official Modrinth additions outside the CF source instance.
    # This keeps refresh reproducible without mislabelling their IDs as CurseForge IDs.
    if (CATALOG / 'curated.json').exists() and (CATALOG / 'local-paths.json').exists():
        previous_paths = read_json(CATALOG / 'local-paths.json')
        for previous in read_json(CATALOG / 'curated.json')['mods']:
            if previous['source']['provider'] != 'modrinth' and not previous['source'].get('lockedLocalCache'):
                continue
            path = Path(previous_paths.get(previous['filename'], ''))
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != previous['sha256']:
                raise ValueError(f"Missing or changed locked official file: {previous['filename']}")
            entry = dict(previous, metadata=jar_metadata(path))
            inventory.append(entry)
            paths[path.name] = str(path)
            for mod_id in provided(entry['metadata']):
                by_id.setdefault(mod_id, []).append(entry)
    requested = set(CONTENT) | set(QOL) | PERFORMANCE | INFRA
    selected = {entry['filename']: entry for entry in previous_lock['mods']} if preserve else {}
    existing_providers = set().union(*(provided(entry['metadata']) for entry in selected.values()))
    if preserve:
        paths.update(previous_paths)
    missing = []
    queue = [(mod, 'selected') for mod in sorted(requested)]
    while queue:
        mod, reason = queue.pop(0)
        if mod in BUILTINS or mod in existing_providers:
            continue
        options = by_id.get(mod, [])
        pins = [pin for pin in FAMILY_PINS.values() if mod in pin['modIds']]
        if len(pins) > 1:
            raise ValueError(f'Multiple family files claim the same mod: {mod}')
        if pins:
            pin = pins[0]
            options = [entry for entry in options if pin_matches(entry, pin)
                       and hashlib.sha256(Path(paths[entry['filename']]).read_bytes()).hexdigest() == pin['sha256']]
        if not options:
            missing.append({'id': mod, 'reason': reason})
            continue
        # Prefer a top-level declaration over a bundled implementation.
        options = sorted(options, key=lambda e: (mod not in [m['id'] for m in e['metadata']['mods']], -(e['fileID'] or 0), e['filename']))
        entry = options[0]
        if entry['filename'] in selected:
            continue
        if provided(entry['metadata']) & EXCLUDED:
            raise ValueError(f'Excluded dependency introduced by {mod}')
        selected[entry['filename']] = entry
        entry['sha256'] = hashlib.sha256(Path(paths[entry['filename']]).read_bytes()).hexdigest()
        if entry['sourceSha1'] and hashlib.sha1(Path(paths[entry['filename']]).read_bytes()).hexdigest() != entry['sourceSha1']:
            raise ValueError(f"Local file differs from official source metadata: {entry['filename']}")
        entry['selectionReason'] = reason
        entry['roles'] = []
        for m in entry['metadata']['mods']:
            key = m['id']
            if key in CONTENT:
                act, role, integration = CONTENT[key]
                entry['roles'].append({'modId': key, 'category': 'content', 'act': act, 'role': role, 'integration': integration, 'status': 'planned-not-yet-balanced'})
            elif key in QOL:
                entry['roles'].append({'modId': key, 'category': 'qol', 'role': QOL[key]})
            else:
                entry['roles'].append({'modId': key, 'category': 'performance' if key in PERFORMANCE else 'infrastructure' if key in INFRA else 'dependency', 'role': 'Runtime optimization and measurement' if key in PERFORMANCE else f'Required support for {reason}'})
        for dep in dependencies(entry['metadata']):
            if dep['required'] and dep['id'] not in provided(entry['metadata']):
                queue.append((dep['id'], f"required by {mod} ({dep['side']})"))
        for project in entry['cfRequiredProjects']:
            candidates = [e for e in inventory if e['projectID'] == project]
            if candidates:
                queue.append((candidates[0]['metadata']['mods'][0]['id'], f'CF required project of {mod}'))
            else:
                missing.append({'id': f'curseforge:{project}', 'reason': f'CF required project of {mod}'})
    lock = {'schemaVersion': 1, 'minecraft': manifest['minecraft']['version'], 'loader': manifest['minecraft']['modLoaders'][0]['id'],
            'publicationStatus': 'local-curation-only; official CurseForge App export required',
            'sidePolicy': 'Explicit client-only seed list; other libraries conservatively both. Actual dedicated-server launch remains required.',
            'missing': sorted(missing, key=lambda m: m['id']), 'mods': sorted(selected.values(), key=lambda e: e['filename'].lower())}
    if preserve:
        lock = dict(previous_lock, mods=lock['mods'], missing=lock['missing'])
    _, client_errors = check(lock, paths, 'client')
    _, server_errors = check(lock, paths, 'server')
    errors = sorted(set(client_errors + server_errors))
    if errors:
        raise ValueError('Curation rejected before writing: ' + '; '.join(errors))
    write_json(CATALOG / 'curated.json', lock)
    write_json(CATALOG / 'local-paths.json', {e['filename']: paths[e['filename']] for e in lock['mods']})
    print(f"Curated {len(lock['mods'])} JARs; missing requested IDs: {', '.join(x['id'] for x in lock['missing'])}")


def check(lock, paths, side='client'):
    errors = [f"Requested dependency unavailable: {item['id']} ({item['reason']})" for item in lock['missing']]
    active = [e for e in lock['mods'] if side == 'client' or e['side'] != 'client']
    supplied = BUILTINS | set().union(*(provided(e['metadata']) for e in active))
    locked_by_name = {entry['filename']: entry for entry in lock['mods']}
    for name, pin in FAMILY_PINS.items():
        entry = locked_by_name.get(name)
        if entry is None:
            errors.append(f'Missing pinned family dependency: {name}')
        elif (entry.get('sha256') != pin['sha256'] or not pin_matches(entry, pin)
                or pin.get('sourceSha512', entry['source'].get('sourceSha512')) != entry['source'].get('sourceSha512')
                or not set(pin['modIds']) <= provided(entry['metadata'])):
            errors.append(f'Pinned family dependency changed: {name}')
    projects = {e['projectID'] for e in active}
    seen = set()
    for entry in active:
        name = entry['filename']
        if name in seen:
            errors.append(f'Duplicate file: {name}')
        seen.add(name)
        if Path(name).name != name or not name.endswith('.jar'):
            errors.append(f'Unsafe filename: {name}')
        path = Path(paths.get(name, ''))
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
            errors.append(f'Missing or hash mismatch: {name}')
        source = entry['source']
        if source['provider'] == 'curseforge':
            if not entry['projectID'] or not entry['fileID']:
                errors.append(f'Missing CurseForge reference: {name}')
        elif source['provider'] == 'modrinth':
            if not source.get('projectId') or not source.get('versionId') or not source.get('downloadUrl', '').startswith('https://cdn.modrinth.com/') or not entry.get('sourceSha1'):
                errors.append(f'Incomplete official Modrinth reference: {name}')
        else:
            errors.append(f'Unsupported source provider: {name}')
        if path.is_file() and entry.get('sourceSha1') and hashlib.sha1(path.read_bytes()).hexdigest() != entry['sourceSha1']:
            errors.append(f'Official source SHA1 mismatch: {name}')
        if path.is_file() and source.get('sourceSha512') and hashlib.sha512(path.read_bytes()).hexdigest() != source['sourceSha512']:
            errors.append(f'Official source SHA512 mismatch: {name}')
        if provided(entry['metadata']) & EXCLUDED:
            errors.append(f'Excluded mod: {name}')
        for dep in dependencies(entry['metadata']):
            if dep['required'] and dep['side'] in ('both', side) and dep['id'] not in supplied:
                errors.append(f"{name}: missing {dep['id']} ({side})")
        for project in entry['cfRequiredProjects']:
            if project not in projects:
                errors.append(f'{name}: missing CF required project {project} ({side})')
    return active, sorted(set(errors))


def main():
    load_families()
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--refresh', metavar='SOURCE_INSTANCE')
    selection.add_argument('--add-families', metavar='SOURCE_INSTANCE',
                           help='Add selected families while retaining every existing lock entry')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--install', type=Path, metavar='DEST_INSTANCE')
    parser.add_argument('--side', choices=['client', 'server'], default='client')
    args = parser.parse_args()
    if args.refresh:
        refresh(args.refresh)
    if args.add_families:
        refresh(args.add_families, preserve=True)
    lock, paths = read_json(CATALOG / 'curated.json'), read_json(CATALOG / 'local-paths.json')
    active, errors = check(lock, paths, args.side)
    if args.check:
        _, other = check(lock, paths, 'server' if args.side == 'client' else 'client')
        errors += other
    if errors:
        print('\n'.join(sorted(set(errors))))
        return 1
    if args.install:
        dest = args.install.resolve()
        source_roots = {Path(p).parent.parent.resolve() if Path(p).parent.name == 'mods' else Path(p).parent.resolve() for p in paths.values()}
        if any(dest.is_relative_to(source_root) for source_root in source_roots):
            raise ValueError('Refusing installation into source instance')
        mods = dest / 'mods'
        for entry in active:
            target = mods / entry['filename']
            if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() != entry['sha256']:
                raise ValueError(f'Refusing to overwrite different JAR: {target.name}')
        mods.mkdir(parents=True, exist_ok=True)
        for entry in active:
            target = mods / entry['filename']
            if not target.exists():
                shutil.copy2(paths[entry['filename']], target)
        write_json(dest / 'entrelumen-dependencies.json', {'side': args.side, 'minecraft': lock['minecraft'], 'loader': lock['loader'], 'mods': [{'filename': e['filename'], 'sha256': e['sha256'], 'projectID': e['projectID'], 'fileID': e['fileID']} for e in active]})
        print(f'Installed {len(active)} {args.side} JARs; source untouched. No CurseForge manifest generated.')
    print(f"PASS: {len(active)} {args.side} JAR hashes, references and required dependency closure. Requested-but-unavailable: {len(lock['missing'])}. Runtime/version compatibility still needs loader verification.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
