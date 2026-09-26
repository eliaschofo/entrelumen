"""Check the recipe design rules of Elias's playtest (docs/design/recipe-design-rules.md).

Reads only the repository's generated outputs, so CI runs it without the pinned JARs:
the recipe rows of pack/kubejs/server_scripts/*.js, pack/kubejs/data/*/recipe/**/*.json and the
companion datapack. Each generator's own --check keeps those outputs equal to its sources, and the
generators check against the native JARs what this tool cannot see (for example, that a gate never
breaks a symmetric native drawing).

Rules (fail with exit code 1):
  1. hitos: an act component (content/integration-design.json) or story item only appears in the
     recipes declared for it in HITOS, each with the milestone that justifies it;
  2. abanico: each component closes at most META[component] recipes;
  3. forma: a crafting-grid recipe that takes or makes an ENTRELUMEN item is shaped, reads the same
     mirrored left to right (Luminosities count as one kind) and keeps its ENTRELUMEN items on the
     vertical axis, the corners or the middle row;
  4. spawner augments are the five-item copper medallion;
  5. Emperor's Cloth stays hidden in EMI and JEI (static part; the loaded check is a GameTest).

--report prints the inventory and the fan-out table as Markdown; --baseline REF adds the fan-out of a
git revision (for example origin/main) as the "before" column.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = 'pack/kubejs/server_scripts'
PACK_DATA = 'pack/kubejs/data'
COMPANION_DATA = 'companion/src/main/resources/data'
DESIGN = 'content/integration-design.json'
# EMI 1.1.24 reads data files only from the emi namespace (EmiDataLoader.prepare); KubeJS always loads
# kubejs/assets as a client resource pack (ClientAssetPacks), so the filter needs no optional pack.
EMI_FILTER = 'pack/kubejs/assets/emi/recipe/filters/entrelumen_hidden_filler.json'
# JEI has no data-driven recipe filter; KubeJS 2101.7.2 posts RecipeViewerEvents.removeRecipes to
# client scripts for JEI only (KubeJSJEIPlugin.onRuntimeAvailable -> IRecipeManager.hideRecipes).
JEI_SCRIPT = 'pack/kubejs/client_scripts/entrelumen_recipe_viewer.js'
# Twilight Forest 4.8.3345: EMI shows the crafting recipe under its serializer ID with a leading slash
# (EmiEmperorsClothRecipe.getId, "twilightforest:/emperors_cloth_recipe"), the smithing one under its
# data ID (EmiPort.getId); JEI names both by their data IDs.
HIDDEN_RECIPES = {'emi': ['twilightforest:/emperors_cloth_recipe', 'twilightforest:emperors_cloth_smithing'],
                  'jei': ['twilightforest:emperors_cloth_recipe', 'twilightforest:emperors_cloth_smithing']}

LUMINOSITIES = {f'entrelumen:luminosity_{d}' for d in
                ('engineering', 'arcane', 'nature', 'exploration', 'logistics', 'habitation')}
STORY_ITEMS = ('entrelumen:raw_lens', 'entrelumen:survey_notes', 'entrelumen:signal_core')
GRID = ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'create:mechanical_crafting',
        'mcjtylib:copy_components', 'enderstorage:create_recipe', 'mekanism:mek_data',
        'enderio:shaped_entity_storage')

# The milestones each component closes. Keys are recipe IDs; the text is the milestone. Every other
# recipe that takes the component fails rule 1, and an entry that no recipe takes any more also fails,
# so this table and the generators cannot drift apart. The Ark modules and the integration chain are
# the story's spine: a component made of earlier ones is the act's milestone by definition.
HITOS = {
    'entrelumen:raw_lens': {
        'entrelumen:integration/precision_bench': 'Marco de Calibración (infusión)',
        'entrelumen:signal_core': 'núcleo de señal (acto I)',
        'entrelumen:vein_resonator_1': 'Resonador I',
    },
    'entrelumen:survey_notes': {
        'entrelumen:signal_core': 'núcleo de señal (acto I)',
        'entrelumen:integration/horizon_survey': 'Carta de Horizonte',
    },
    'entrelumen:signal_core': {
        'entrelumen:ark_controller': 'controlador del Arca',
        'entrelumen:survey_station': 'estación cartográfica',
    },
    'entrelumen:calibration_frame': {
        'entrelumen:integration/living_workshop': 'Matriz Viva',
        'entrelumen:integration/signal_exchange': 'Matriz de Enrutamiento',
        'entrelumen:integration/spectral_archive': 'Lente Espectral',
        'entrelumen:integration/ark_engineering': 'módulo de ingeniería',
        'mekanism:metallurgic_infuser': 'entrada a Mekanism (arranque de la historia)',
        'jamd:portal_block': 'portal a la dimensión minera',
        'entrelumen:cartographer_shelf': 'estante de Eterna del acto II',
        'entrelumen:vein_resonator_2': 'Resonador II',
    },
    'entrelumen:energy_coupler': {
        'entrelumen:integration/distributed_power': 'Regulador de Energía',
        'entrelumen:integration/ark_engineering': 'módulo de ingeniería',
        'create_new_age:shaped/carbon_brushes': 'electricidad de New Age (una escobilla por generador)',
    },
    'entrelumen:living_matrix': {
        'entrelumen:integration/nursery_protocol': 'Núcleo de Propagación',
        'entrelumen:integration/pollinator_treaty': 'Cápsula de Ecosistema',
        'entrelumen:integration/ark_nature': 'módulo de naturaleza',
        'entrelumen:integration/ark_habitation': 'módulo de habitabilidad',
        'easy_villagers:iron_farm': 'granja de hierro compacta',
    },
    'entrelumen:ration_bundle': {
        'entrelumen:integration/horizon_survey': 'Carta de Horizonte',
        'entrelumen:integration/settlement_supply': 'Contrato de Habitabilidad',
        'entrelumen:integration/ark_habitation': 'módulo de habitabilidad',
    },
    'entrelumen:routing_matrix': {
        'entrelumen:integration/measured_logistics': 'Sensor de Inventario',
        'entrelumen:integration/ark_logistics': 'módulo de logística',
        'ae2:network/blocks/controller': 'red ME de AE2',
        'refinedstorage:controller': 'red de Refined Storage',
        'mekanism:teleporter': 'teletransporte',
        'undergarden:catalyst': 'portal a Undergarden',
        'entrelumen:peace_altar_duplication': 'copia del Altar de Paz',
        'entrelumen:augment_spawn_range': 'aumentador de alcance',
    },
    'entrelumen:propagation_core': {
        'entrelumen:integration/renewal_engine': 'Motor de Renovación',
        'entrelumen:augment_youthful': 'aumentador juvenil',
        'entrelumen:growth_altar_duplication': 'copia del Altar de Crecimiento',
        'entrelumen:renewal_altar_duplication': 'copia del Altar de Renovación',
        'botanypotstiers:elite_upgrade': 'macetas Elite',
    },
    'entrelumen:power_regulator': {
        'entrelumen:integration/workshop_hands': 'Núcleo de Manipulación',
        'entrelumen:augment_min_delay': 'aumentador de espera mínima',
        'entrelumen:augment_max_delay': 'aumentador de espera máxima',
        'entrelumen:vein_resonator_3': 'Resonador III',
        'entrelumen:patina_shelf': 'estante de Quanta del acto III',
        'mekanism:jetpack': 'primer vuelo a motor',
        'psi:assembler': 'Psi entero (Elias, 24/9)',
        'immersive_aircraft:engine': 'aeronaves a motor',
    },
    'entrelumen:inventory_sensor': {
        'entrelumen:integration/resilient_backbone': 'Bus del Arca',
        'entrelumen:augment_player_range': 'aumentador de alcance de jugador',
        'mekanism:qio_drive_array': 'almacenamiento QIO',
    },
    'entrelumen:handling_core': {
        'entrelumen:integration/resilient_backbone': 'Bus del Arca',
        'entrelumen:integration/ark_logistics': 'módulo de logística',
        'entrelumen:augment_silent': 'aumentador silencioso',
        'entrelumen:terraform_altar_duplication': 'copia del Altar de Nivelación',
        'refinedstorage:autocrafter': 'autocrafteo de Refined Storage',
        'rftoolsbuilder:builder': 'constructor de RFTools',
        'easy_villagers:auto_trader': 'comercio automático',
    },
    'entrelumen:spectral_lens': {
        'entrelumen:integration/sealed_memory': 'Sello de Contención',
        'entrelumen:integration/ark_arcana': 'módulo arcano',
        'entrelumen:integration/ark_exploration': 'módulo de exploración',
        'entrelumen:augment_initial_health': 'aumentador de vida inicial',
        'entrelumen:lumen_shelf': 'estante de Arcana del acto IV',
        'entrelumen:light_key': 'Llave de Luz',
        'mekanism:digital_miner': 'cantera: Digital Miner',
        'entrelumen:vein_resonator_4': 'Resonador IV',
    },
    'entrelumen:horizon_chart': {
        'entrelumen:integration/ark_exploration': 'módulo de exploración',
        'entrelumen:light_key': 'Llave de Luz',
        'entrelumen:repose_altar_duplication': 'copia del Altar de Reposo',
        'entrelumen:horizon_shelf': 'estante del acto V',
        'jamd:end_portal_block': 'portal a la minería del End',
        'eternal_starlight:orb_of_prophecy': 'portal a Eternal Starlight',
        'justdirethings:upgrade_flight': 'vuelo creativo',
        'modern_industrialization:armor/gravichestplate': 'pechera de vuelo',
    },
    'entrelumen:ecosystem_capsule': {
        'entrelumen:integration/renewal_engine': 'Motor de Renovación',
        'entrelumen:integration/ark_nature': 'módulo de naturaleza',
        'entrelumen:augment_max_nearby': 'aumentador de entidades cercanas',
        'entrelumen:augment_spawn_count': 'aumentador de cantidad',
        'botanypotstiers:ultra_upgrade': 'macetas Ultra',
        'hostilenetworks:sim_chamber': 'granja de mobs: simulación',
        'industrialforegoing:mob_duplicator': 'granja de mobs: duplicador',
        'enderio:powered_spawner': 'granja de mobs: spawner con energía',
    },
    'entrelumen:containment_seal': {
        'entrelumen:integration/ark_arcana': 'módulo arcano',
        'entrelumen:augment_burning': 'aumentador ardiente',
        'entrelumen:time_altar_duplication': 'copia del Altar del Tiempo',
        'entrelumen:atlas_library': 'biblioteca del Atlas',
        'mekanismgenerators:fission_reactor/port': 'reactor de fisión',
        'jamd:nether_portal_block': 'portal a la minería del Nether',
        'forbidden_arcanus:forbidden_arcanus/hephaestus_forge/ritual/eternal_stella': 'herramientas irrompibles',
        'theurgy:crafting/shaped/sulfuric_flux_emitter': 'réplica alquímica',
    },
    'entrelumen:ark_bus': {
        'entrelumen:integration/ark_engineering': 'módulo de ingeniería',
        'entrelumen:integration/ark_logistics': 'módulo de logística',
        'entrelumen:vein_resonator_5': 'Resonador V',
        'entrelumen:augment_ignore_players': 'aumentador sin jugadores',
        'entrelumen:augment_no_ai': 'aumentador sin IA',
        'entrelumen:augment_redstone_control': 'aumentador con redstone',
        'mekanismgenerators:reactor/controller': 'reactor de fusión',
        'modern_industrialization:electric_age/machine/nuclear_reactor_asbl': 'reactor nuclear de MI',
    },
    'entrelumen:renewal_engine': {
        'entrelumen:integration/ark_nature': 'módulo de naturaleza',
        'entrelumen:augment_echoing': 'aumentador de eco',
        'entrelumen:augment_ignore_conditions': 'aumentador sin condiciones',
        'entrelumen:augment_ignore_light': 'aumentador sin luz',
        'mekanism:sps_port': 'antimateria (SPS)',
        'botanypotstiers:mega_upgrade': 'macetas Mega',
    },
    'entrelumen:habitation_contract': {
        'entrelumen:integration/ark_habitation': 'módulo de habitabilidad',
    },
}
# Fan-out goal per component: the playtest asks for few key recipes (six to eight).
META = {c: 8 for c in HITOS}
META.update({'entrelumen:raw_lens': 3, 'entrelumen:survey_notes': 2, 'entrelumen:signal_core': 2,
             'entrelumen:energy_coupler': 4, 'entrelumen:living_matrix': 6, 'entrelumen:ration_bundle': 4,
             'entrelumen:propagation_core': 6, 'entrelumen:inventory_sensor': 6,
             'entrelumen:renewal_engine': 6, 'entrelumen:habitation_contract': 2})

AUGMENT_PATTERN = [' K ', 'SOS', ' S ']


class Tree:
    """Repository files at the work tree, or at a git revision for the baseline."""

    def __init__(self, ref=None):
        self.ref = ref
        if ref:
            listing = subprocess.run(['git', '-C', str(ROOT), 'ls-tree', '-r', '--name-only', ref],
                                     capture_output=True, text=True, check=True).stdout
            self.files = listing.splitlines()

    def glob(self, prefix, suffix):
        if self.ref:
            return sorted(f for f in self.files if f.startswith(prefix) and f.endswith(suffix))
        base = ROOT / prefix
        return sorted(p.relative_to(ROOT).as_posix() for p in base.rglob('*' + suffix)) if base.exists() else []

    def read(self, path):
        if self.ref:
            done = subprocess.run(['git', '-C', str(ROOT), 'show', f'{self.ref}:{path}'], capture_output=True)
            if done.returncode:
                return None
            return done.stdout.decode('utf-8-sig')
        target = ROOT / path
        return target.read_text(encoding='utf-8-sig') if target.is_file() else None


DECODER = json.JSONDecoder()


def script_rows(text):
    """Every `const X = [...]` whose elements carry an 'id' and a recipe 'json'."""
    rows = []
    for match in re.finditer(r'^const (\w+) = ', text, re.M):
        try:
            value, _ = DECODER.raw_decode(text, match.end())
        except ValueError:
            continue
        if isinstance(value, list) and value and all(isinstance(v, dict) and 'id' in v and isinstance(v.get('json'), dict)
                                                     for v in value):
            rows.extend((match.group(1), v['id'], v['json']) for v in value)
    return rows


def data_id(path, base):
    """data/<ns>/recipe/<path>.json is <ns>:<path>; other data keeps its folder (Forbidden Arcanus rituals)."""
    rel = path[len(base) + 1:]
    namespace, _, rest = rel.partition('/')
    if rest.startswith('recipe/'):
        rest = rest[len('recipe/'):]
    return namespace + ':' + rest[:-5]


def disabled(recipe):
    return {'type': 'neoforge:false'} in recipe.get('neoforge:conditions', [])


def load(tree):
    """[(source, recipe ID, JSON)] for every recipe the pack adds or changes."""
    found = {}
    for path in tree.glob(SCRIPTS + '/', '.js'):
        for const, rid, recipe in script_rows(tree.read(path)):
            found[rid] = (Path(path).stem.replace('entrelumen_', ''), rid, recipe)
    for base, source in ((PACK_DATA, 'pack-data'), (COMPANION_DATA, 'companion')):
        for path in tree.glob(base + '/', '.json'):
            if '/recipe/' not in path and '/ritual/' not in path:
                continue
            recipe = json.loads(tree.read(path))
            if disabled(recipe) or ('type' not in recipe and '/ritual/' not in path):
                continue
            rid = data_id(path, base)
            found[rid] = (source, rid, recipe)
    return list(found.values())


def body(recipe):
    inner = recipe.get('recipe')
    return inner if isinstance(inner, dict) and ('pattern' in inner or 'ingredients' in inner) else recipe


OUTPUT_KEYS = ('result', 'output', 'results', 'outputs', 'item_outputs')


def inputs(recipe):
    """Item IDs a recipe consumes (tags are left out: no ENTRELUMEN item is a tag member here)."""
    found = []

    def walk(value, key=None):
        if isinstance(value, dict):
            for k, child in value.items():
                if k in OUTPUT_KEYS:
                    continue
                if k == 'item' and isinstance(child, str):
                    found.append(child)
                elif k in ('mainhand', 'template', 'base', 'addition') and isinstance(child, dict) and 'item' in child:
                    found.append(child['item'])
                else:
                    walk(child, k)
        elif isinstance(value, list):
            for child in value:
                walk(child, key)

    walk(body(recipe))
    return found


def outputs(recipe):
    found = []
    b = body(recipe)

    def walk(value):
        if isinstance(value, dict):
            for k in ('id', 'item'):
                if isinstance(value.get(k), str):
                    found.append(value[k])
            for child in value.values():
                if isinstance(child, (dict, list)):
                    walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    for key in OUTPUT_KEYS:
        if key in b:
            walk(b[key])
    return found


def grid_kind(recipe):
    """'shaped', 'shapeless' or None for a machine recipe."""
    b = body(recipe)
    if 'pattern' in b and 'key' in b:
        return 'shaped'
    kind = recipe.get('type', '')
    if 'ingredients' in b and (kind in GRID or 'shapeless' in kind):
        return 'shapeless'
    return None


def ingredient_kind(ingredient):
    if isinstance(ingredient, dict) and ingredient.get('item') in LUMINOSITIES:
        return 'luminosity'
    return json.dumps(ingredient, sort_keys=True)


def symmetric(b):
    def kind(symbol):
        return ' ' if symbol == ' ' else ingredient_kind(b['key'][symbol])
    return all([kind(c) for c in row] == [kind(c) for c in reversed(row)] for row in b['pattern'])


def placed(b, row, col):
    """Centre column, corners or middle row of the recipe's own drawing."""
    height, width = len(b['pattern']), len(b['pattern'][0])
    axis = width % 2 == 1 and col == width // 2
    corner = row in (0, height - 1) and col in (0, width - 1)
    middle = height % 2 == 1 and row == height // 2
    return axis or corner or middle


def entrelumen_cells(b):
    cells = []
    for r, line in enumerate(b['pattern']):
        for c, symbol in enumerate(line):
            if symbol != ' ':
                item = b['key'][symbol].get('item', '') if isinstance(b['key'][symbol], dict) else ''
                if item.startswith('entrelumen:'):
                    cells.append((r, c, item))
    return cells


def depths(recipes):
    """ENTRELUMEN crafting levels below each ENTRELUMEN item (0 for anything else)."""
    makers = {}
    for _, rid, recipe in recipes:
        for out in outputs(recipe):
            if out.startswith('entrelumen:'):
                makers.setdefault(out, []).append(recipe)
    memo = {}

    def depth(item, stack=()):
        if not item.startswith('entrelumen:'):
            return 0
        if item in memo:
            return memo[item]
        if item in stack:
            return 0
        best = None
        for recipe in makers.get(item, []):
            parts = [i for i in inputs(recipe) if i.startswith('entrelumen:')]
            if item in parts:  # an altar copy consumes the altar itself
                continue
            level = 1 + max((depth(i, stack + (item,)) for i in parts), default=0)
            best = level if best is None else min(best, level)
        memo[item] = best if best is not None else 1
        return memo[item]

    return depth


def components(tree):
    design = json.loads(tree.read(DESIGN))
    return sorted({p['output']['id'] for p in design['projects']} | set(STORY_ITEMS))


def fanout(recipes, tracked):
    uses = {c: set() for c in tracked}
    for _, rid, recipe in recipes:
        for item in set(inputs(recipe)):
            if item in uses:
                uses[item].add(rid)
    return uses


def check(recipes, tree):
    problems = []
    tracked = components(tree)
    uses = fanout(recipes, tracked)
    for component in tracked:
        declared = set(HITOS.get(component, {}))
        extra, stale = uses[component] - declared, declared - uses[component]
        if extra:
            problems.append(f'hitos: {component} appears outside its milestones: {sorted(extra)}')
        if stale:
            problems.append(f'hitos: {component} is declared for recipes that no longer take it: {sorted(stale)}')
        if len(uses[component]) > META.get(component, 0):
            problems.append(f'abanico: {component} closes {len(uses[component])} recipes (goal {META.get(component, 0)})')
    for source, rid, recipe in recipes:
        kind = grid_kind(recipe)
        if kind is None:
            continue
        own = [i for i in inputs(recipe) + outputs(recipe) if i.startswith('entrelumen:')]
        if not own:
            continue
        if kind == 'shapeless':
            problems.append(f'forma: {rid} ({source}) is shapeless')
            continue
        b = body(recipe)
        if len({len(line) for line in b['pattern']}) != 1:
            problems.append(f'forma: {rid} has rows of different widths')
            continue
        if not symmetric(b):
            problems.append(f'forma: {rid} ({source}) is not symmetric: {"/".join(b["pattern"])}')
        for r, c, item in entrelumen_cells(b):
            if not placed(b, r, c):
                problems.append(f'forma: {rid} has {item} off the axis, corners and middle row at ({r},{c})')
    for source, rid, recipe in recipes:
        if rid.startswith('entrelumen:augment_') and grid_kind(recipe):
            b = body(recipe)
            filled = sum(c != ' ' for line in b['pattern'] for c in line)
            if b['pattern'] != AUGMENT_PATTERN or filled != 5:
                problems.append(f'aumentadores: {rid} is not the five-item copper medallion')
    problems.extend(check_hidden(tree))
    return problems


def check_hidden(tree):
    problems = []
    emi = tree.read(EMI_FILTER)
    if emi is None:
        problems.append(f'Emperor\'s Cloth: missing EMI filter {EMI_FILTER}')
    else:
        ids = {f.get('id') for f in json.loads(emi).get('filters', [])}
        if not set(HIDDEN_RECIPES['emi']) <= ids:
            problems.append(f'Emperor\'s Cloth: EMI filter lacks {sorted(set(HIDDEN_RECIPES["emi"]) - ids)}')
    text = tree.read(JEI_SCRIPT)
    if text is None or 'RecipeViewerEvents.removeRecipes' not in text \
            or not all(f"'{rid}'" in text for rid in HIDDEN_RECIPES['jei']):
        problems.append(f'Emperor\'s Cloth: {JEI_SCRIPT} does not hide {HIDDEN_RECIPES["jei"]}')
    return problems


def describe(recipe):
    b = body(recipe)
    kind = grid_kind(recipe)
    if kind == 'shaped':
        return 'forma', '/'.join(line.replace(' ', '_') for line in b['pattern'])
    if kind == 'shapeless':
        return 'sin forma', f'{len(b.get("ingredients", []))} ítems'
    return 'máquina', recipe.get('type', '?')


def report(recipes, tree, baseline=None):
    tracked = components(tree)
    uses = fanout(recipes, tracked)
    depth = depths(recipes)
    before = fanout(baseline, tracked) if baseline is not None else None
    lines = ['| Componente | Antes | Ahora | Meta |', '|---|---:|---:|---:|']
    for c in sorted(tracked, key=lambda c: -len((before or uses)[c])):
        if c not in META:
            continue  # the Ark modules are milestones themselves; nothing consumes them
        lines.append(f'| `{c.split(":", 1)[1]}` | {len(before[c]) if before else "—"} | {len(uses[c])} | {META.get(c, "—")} |')
    lines += ['', '| Receta | Fuente | Forma | Grilla | Componentes | Anidado |', '|---|---|---|---|---|---:|']
    for source, rid, recipe in sorted(recipes, key=lambda r: (r[0], r[1])):
        parts = sorted({i for i in inputs(recipe) if i.startswith('entrelumen:')})
        if not parts and not any(o.startswith('entrelumen:') for o in outputs(recipe)):
            continue
        shape, grid = describe(recipe)
        level = max((depth(i) for i in parts), default=0)
        names = ', '.join(p.split(':', 1)[1] for p in parts) or '—'
        lines.append(f'| `{rid}` | {source} | {shape} | `{grid}` | {names} | {level} |')
    return '\n'.join(lines)


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='fail on any rule (default)')
    parser.add_argument('--report', action='store_true', help='print the inventory and fan-out tables')
    parser.add_argument('--baseline', help='git revision for the "before" fan-out column')
    args = parser.parse_args()
    tree = Tree()
    recipes = load(tree)
    if args.report:
        baseline = load(Tree(args.baseline)) if args.baseline else None
        print(report(recipes, tree, baseline))
        return 0
    problems = check(recipes, tree)
    for problem in problems:
        print(problem)
    tracked = components(tree)
    uses = fanout(recipes, tracked)
    print(json.dumps({'status': 'FAIL' if problems else 'PASS', 'recipes': len(recipes), 'problems': len(problems),
                      'fanout': {c.split(':', 1)[1]: len(uses[c]) for c in tracked}}, ensure_ascii=False))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
