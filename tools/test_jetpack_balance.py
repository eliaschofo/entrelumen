"""Jetpack balance (Elias, 25 September 2026): generated files in sync, 2.5 times per tick, and the surcharge logic."""
from __future__ import annotations

from fractions import Fraction
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import generate_jetpack_balance as generator  # noqa: E402

DATA = json.loads((ROOT / 'content/jetpack-balance.json').read_text(encoding='utf-8'))
FACTOR = Fraction(DATA['factor'])
SCRIPT = ROOT / DATA['surcharge']['script']

# Runs the generated server script in Node with stand-ins for KubeJS and the mods' capabilities, then flies.
HARNESS = r"""
const fs = require('fs');
const vm = require('vm');
const source = fs.readFileSync(process.argv[2], 'utf8');
const handlers = {};
const caps = {energy: 'energy', fluid: 'fluid', chemical: 'chemical'};
const classes = {
  'net.minecraft.world.entity.EquipmentSlot': {CHEST: 'chest'},
  'net.minecraft.core.registries.BuiltInRegistries': {ITEM: {getKey: item => item.id}},
  'net.neoforged.neoforge.capabilities.Capabilities$EnergyStorage': {ITEM: caps.energy},
  'net.neoforged.neoforge.capabilities.Capabilities$FluidHandler': {ITEM: caps.fluid},
  'net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction': {EXECUTE: 'execute'},
  'mekanism.common.capabilities.Capabilities': {CHEMICAL: {item: () => caps.chemical}},
  'mekanism.api.Action': {EXECUTE: 'execute'},
};
const context = {
  Java: {loadClass: name => { if (!(name in classes)) throw new Error('class ' + name); return classes[name]; }},
  Platform: {isLoaded: () => true},
  PlayerEvents: {tick: fn => { handlers.tick = fn; }, loggedOut: fn => { handlers.out = fn; }},
  console: {error: message => { throw new Error(message); }},
};
vm.createContext(context);
vm.runInContext(source, context);

// A piece that stores `amount` of one kind of fuel (hydrogen sits in the second of two tanks).
function piece(id, kind, amount) {
  const p = {id: id, amount: amount};
  const take = n => { const t = Math.min(n, p.amount); p.amount -= t; return t; };
  const handlers = {
    energy: {getEnergyStored: () => p.amount, extractEnergy: (n, sim) => take(n)},
    fluid: {getTanks: () => 1, getFluidInTank: () => ({getAmount: () => p.amount}),
            drain: (n, action) => ({getAmount: () => take(n)})},
    chemical: {getChemicalTanks: () => 2,
               getChemicalInTank: i => i === 0
                 ? {isEmpty: () => true, getTypeRegistryName: () => 'mekanism:oxygen', getAmount: () => 0}
                 : {isEmpty: () => p.amount === 0, getTypeRegistryName: () => 'mekanism:hydrogen', getAmount: () => p.amount},
               extractChemical: (i, n, action) => ({getAmount: () => i === 1 ? take(n) : 0})},
  };
  p.stack = {isEmpty: () => false, getItem: () => ({id: id}), getCapability: cap => cap === kind ? handlers[kind] : null};
  return p;
}
let worn = null;
const player = {getStringUUID: () => 'p1', getItemBySlot: slot => worn ? worn.stack : {isEmpty: () => true}};
const tick = () => handlers.tick({player: player});
const out = {};
function fly(name, p, nativePerTick, ticks) {
  worn = p;
  tick(); tick();
  const idle = p.amount;
  for (let i = 0; i < ticks; i++) { p.amount -= nativePerTick; tick(); }
  out[name] = {idle: idle, flown: p.amount};
}
fly('mekanism', piece('mekanism:jetpack', 'chemical', 100), 1, 10);
fly('mekasuit', piece('mekanism:mekasuit_bodyarmor', 'chemical', 1000), 3, 4);
fly('diesel', piece('modern_industrialization:diesel_jetpack', 'fluid', 1000), 2, 3);
fly('suit', piece('ad_astra:jet_suit', 'energy', 100000), 50, 4);
fly('ironjetpacks', piece('ironjetpacks:jetpack', 'energy', 1000), 100, 3);
// Swap to an emptier piece of the same kind, then a tick in which a charger outpaced the flight.
const before = piece('mekanism:jetpack', 'chemical', 100);
worn = before; tick();
const swapped = piece('mekanism:jetpack', 'chemical', 40);
worn = swapped; tick();
out.swap = swapped.amount;
swapped.amount += 5; tick();
out.charged = swapped.amount;
// Logging out forgets the player: the next piece starts clean.
handlers.out({player: player});
const after = piece('mekanism:jetpack', 'chemical', 30);
worn = after; after.amount -= 1; tick();
out.afterLogout = after.amount;
process.stdout.write(JSON.stringify(out));
"""


class JetpackBalanceTest(unittest.TestCase):
    def test_generated_files_are_in_sync(self):
        result = subprocess.run([sys.executable, str(ROOT / 'tools/generate_jetpack_balance.py'), '--check'],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_iron_jetpacks_keep_the_defaults_but_usage(self):
        section = DATA['ironjetpacks']
        folder = ROOT / 'pack/config/ironjetpacks/jetpacks'
        self.assertEqual({p.stem for p in folder.glob('*.json')}, set(section['defaults']))
        keys = set(section['defaults']['iron'])
        for name, default in section['defaults'].items():
            shipped = json.loads((folder / f'{name}.json').read_text(encoding='utf-8'))
            self.assertEqual(set(default), keys, name)
            self.assertEqual({k: v for k, v in shipped.items() if k != 'usage'},
                             {k: v for k, v in default.items() if k != 'usage'}, name)
            self.assertEqual(shipped['usage'], math.ceil(default['usage'] * FACTOR), name)
            self.assertGreaterEqual(shipped['usage'], default['usage'] * FACTOR, name)

    def test_partial_configs_raise_only_the_jetpack_keys(self):
        for entry in DATA['configs']:
            config = tomllib.loads((ROOT / 'pack/config' / entry['file']).read_text(encoding='utf-8'))
            section = config[entry['section']]
            self.assertEqual(set(section), set(entry['settings']), entry['section'])
            for key, (default, _) in entry['settings'].items():
                self.assertEqual(section[key], math.ceil(default * FACTOR), f"{entry['section']}.{key}")
        oritech = tomllib.loads((ROOT / 'pack/config/oritech-startup.toml').read_text(encoding='utf-8'))
        self.assertEqual(oritech['exoJetpack'], {'energyUsage': 640, 'fuelUsage': 38})

    def test_script_table_matches_the_data(self):
        source = SCRIPT.read_text(encoding='utf-8')
        self.assertTrue(source.startswith(generator.HEADER))
        table = json.loads(source.split('const entrelumenJetpackSurcharge = ', 1)[1].split(';', 1)[0])
        self.assertEqual(table, {row['item']: {'kind': row['kind'], 'max': row['max']}
                                 for row in DATA['surcharge']['items']})
        self.assertIn(f'const entrelumenJetpackExtra = {float(FACTOR - 1):g};', source)

    @unittest.skipUnless(shutil.which('node'), 'Node.js is not installed')
    def test_surcharge_takes_one_and_a_half_times_the_native_loss(self):
        with tempfile.TemporaryDirectory() as folder:
            harness = Path(folder) / 'harness.js'
            harness.write_text(HARNESS, encoding='utf-8')
            result = subprocess.run(['node', str(harness), str(SCRIPT)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        out = json.loads(result.stdout)
        # 2.5 times the native loss in total; fractions carry over (1, 2, 1, 2 ... for Mekanism's 1 mB).
        self.assertEqual(out['mekanism'], {'idle': 100, 'flown': 100 - 25})
        self.assertEqual(out['mekasuit'], {'idle': 1000, 'flown': 1000 - 30})
        self.assertEqual(out['diesel'], {'idle': 1000, 'flown': 1000 - 15})
        self.assertEqual(out['suit'], {'idle': 100000, 'flown': 100000 - 500})
        # Iron Jetpacks pay through their config, not the script.
        self.assertEqual(out['ironjetpacks'], {'idle': 1000, 'flown': 700})
        self.assertEqual(out['swap'], 40)
        self.assertEqual(out['charged'], 45)
        self.assertEqual(out['afterLogout'], 29)


if __name__ == '__main__':
    unittest.main()
