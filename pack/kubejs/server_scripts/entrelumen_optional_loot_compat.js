// Only guard upstream block loot whose item is optional in the pinned addons.
// Present items keep their original loot tables, including block-entity components.
const entrelumenOptionalLoot = {
  mekmm: [],
  mekanism_extras: [
    'absolute_chemical_infusing_factory',
    'cosmic_chemical_infusing_factory',
    'infinite_chemical_infusing_factory',
    'supreme_chemical_infusing_factory'
  ],
  extendedae: ['ex_emc_interface'],
  create_connected: []
};

// Connected ships these Dye Depot loot tables even when that optional mod is absent.
[
  'amber', 'aqua', 'beige', 'coral', 'forest', 'ginger', 'indigo', 'maroon',
  'mint', 'navy', 'olive', 'rose', 'slate', 'tan', 'teal', 'verdant'
].forEach(color => {
  entrelumenOptionalLoot.create_connected.push('dye_depot_' + color + '_fan_dyeing_catalyst');
});

const entrelumenMoreMachineOperations = [
  'centrifuging', 'crystallizing', 'dissolving', 'lathing',
  'liquifying', 'oxidizing', 'painting', 'pigment_extracting',
  'planting', 'pressing', 'pressurised_reacting', 'recycling',
  'replicating', 'rolling_mill', 'stamping', 'washing'
];
['creative', 'dense', 'multiversal', 'overclocked', 'quantum'].forEach(tier => {
  entrelumenMoreMachineOperations.forEach(operation => {
    entrelumenOptionalLoot.mekmm.push(tier + '_' + operation + '_factory');
  });
});

ServerEvents.generateData('after_mods', event => {
  const empty = {};
  let checked = 0;
  Object.keys(entrelumenOptionalLoot).forEach(namespace => {
    empty[namespace] = 0;
    entrelumenOptionalLoot[namespace].forEach(name => {
      checked++;
      const item = namespace + ':' + name;
      if (Item.exists(item)) return;
      // NeoForge replaces a failed conditional loot table with an empty table.
      // The condition is evaluated before the absent item could reach a codec.
      event.json(namespace + ':loot_table/blocks/' + name + '.json', {
        type: 'minecraft:block',
        'neoforge:conditions': [{type: 'neoforge:item_exists', item: item}]
      });
      empty[namespace]++;
    });
  });
  console.info('[ENTRELUMEN_OPTIONAL_LOOT] ' + JSON.stringify({
    checked: checked, empty: empty
  }));
});
