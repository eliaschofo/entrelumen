// Static acquisition balance for pinned Immersive Aircraft 1.5.2+1.21.1 NeoForge.
// Changes crafting only: given aircraft and components remain usable and transferable.
const entrelumenAircraftSourceSha256 = 'ef5b68c04171d1eadb3bb70e600eb766534ef7588a5b4737f9d55a4f38550ae9';
const entrelumenAircraftOverrides = [
  {
    "id": "immersive_aircraft:engine",
    "json": {
      "type": "minecraft:crafting_shaped",
      "pattern": [" R ", "PFP", "CIC"],
      "key": {
        "R": {"item": "entrelumen:power_regulator"},
        "P": {"item": "minecraft:piston"},
        "F": {"item": "minecraft:blast_furnace"},
        "C": {"item": "minecraft:cobblestone"},
        "I": {"item": "immersive_aircraft:boiler"}
      },
      "result": {"id": "immersive_aircraft:engine"}
    }
  },
  {
    "id": "immersive_aircraft:gyrodyne",
    "json": {
      "type": "minecraft:crafting_shaped",
      "pattern": [" S ", "HPH", " SC"],
      "key": {
        "S": {"item": "immersive_aircraft:sail"},
        "H": {"item": "immersive_aircraft:hull"},
        "P": {"item": "immersive_aircraft:propeller"},
        "C": {"item": "entrelumen:handling_core"}
      },
      "result": {"id": "immersive_aircraft:gyrodyne"}
    }
  }
];
const entrelumenAircraftRouteOutputs = [
  'engine', 'airship', 'biplane', 'gyrodyne', 'quadrocopter',
  'cargo_airship', 'warship', 'bamboo_hopper'
];

ServerEvents.recipes(event => {
  // Check all dependencies before changing either native recipe.
  var missingItems = [];
  var missingRecipes = [];
  entrelumenAircraftOverrides.forEach(row => {
    if (!event.containsRecipe({id: row.id})) missingRecipes.push(row.id);
    [row.json.result.id].concat(Object.keys(row.json.key).map(key => row.json.key[key].item))
      .forEach(id => { if (!Item.exists(id) && missingItems.indexOf(id) < 0) missingItems.push(id); });
  });
  if (missingItems.length || missingRecipes.length) {
    console.error('[ENTRELUMEN_AIRCRAFT] ' + JSON.stringify({status: 'failed-preflight',
      sourceSha256: entrelumenAircraftSourceSha256, missingItems: missingItems, missingRecipes: missingRecipes}));
    throw new Error('ENTRELUMEN aircraft balance preflight failed; native recipes were not changed');
  }
  entrelumenAircraftOverrides.forEach(row => {
    event.remove({id: row.id});
    event.custom(row.json).id(row.id);
  });
});

ServerEvents.afterRecipes(event => {
  var ops = Java.loadClass('com.mojang.serialization.JsonOps').INSTANCE;
  var failures = [];
  var routeIds = {};
  // Recipe codecs may serialize a single ingredient as an object or a one-element list.
  var ingredientId = value => {
    if (Array.isArray(value)) return value.length === 1 ? ingredientId(value[0]) : null;
    return value && typeof value === 'object' ? value.item : null;
  };
  entrelumenAircraftOverrides.forEach(row => {
    var found = [];
    try {
      event.forEachRecipe({id: row.id}, holder => {
        var encoded = holder.getSerializer().codec().codec()
          .encodeStart(ops, holder.getRecipe()).getOrThrow();
        found.push(JSON.parse(String(encoded)));
      });
      if (found.length !== 1) {
        failures.push({id: row.id, reason: 'recipe-count', count: found.length});
        return;
      }
      var actual = found[0];
      var expected = row.json;
      var keys = actual.key || {};
      var samePattern = JSON.stringify(actual.pattern) === JSON.stringify(expected.pattern);
      var sameKeys = Object.keys(keys).sort().join(',') === Object.keys(expected.key).sort().join(',') &&
        Object.keys(expected.key).every(key => ingredientId(keys[key]) === expected.key[key].item);
      var sameResult = actual.result && actual.result.id === expected.result.id &&
        (actual.result.count === undefined || actual.result.count === 1);
      if (!samePattern || !sameKeys || !sameResult) {
        failures.push({id: row.id, reason: 'loaded-recipe-mismatch',
          pattern: actual.pattern, key: keys, result: actual.result});
      }
    } catch (error) {
      failures.push({id: row.id, reason: 'codec', error: String(error)});
    }
  });
  entrelumenAircraftRouteOutputs.forEach(name => {
    var output = 'immersive_aircraft:' + name;
    var ids = [];
    event.forEachRecipe({output: output}, holder => ids.push(String(holder.getOrCreateId())));
    ids.sort();
    routeIds[output] = ids;
    if (ids.length !== 1 || ids[0] !== output) {
      failures.push({output: output, reason: 'unexpected-acquisition-route', ids: ids.slice(0, 32), total: ids.length});
    }
  });
  console.info('[ENTRELUMEN_AIRCRAFT] ' + JSON.stringify({status: failures.length ? 'FAIL' : 'PASS',
    sourceSha256: entrelumenAircraftSourceSha256, checkedOverrides: entrelumenAircraftOverrides.length,
    checkedOutputs: entrelumenAircraftRouteOutputs.length, routeIds: routeIds, failures: failures}));
});
