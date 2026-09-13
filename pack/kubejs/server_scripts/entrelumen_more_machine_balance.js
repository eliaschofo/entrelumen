// Original curatorial balance. Mekmm 1.4.1 / NeoForge 21.1.249 / KubeJS build.374.
// The data-map overrides are the functional guard; crafting removals alone are insufficient.
const entrelumenReplicationRecipes = [
  'mekmm:replicator', 'mekmm:fluid_replicator', 'mekmm:chemical_replicator',
  'mekmm:factory/basic/replicating', 'mekmm:factory/advanced/replicating',
  'mekmm:factory/elite/replicating', 'mekmm:factory/ultimate/replicating'
];
const entrelumenBlockedMoreMachine = /^(mekmm:compat\/(mysticalagriculture|mysticalagradditions)\/planting\/|mekmm:compat\/evolvedmekanism\/factory\/(dense|multiversal|overclocked|quantum|creative)\/replicating$)/;

ServerEvents.recipes(event => {
  entrelumenReplicationRecipes.forEach(id => event.remove({id: id}));
  // Missing optional integrations simply match no recipes.
  event.remove({id: entrelumenBlockedMoreMachine});
});

ServerEvents.commandRegistry(event => {
  event.register(event.commands.literal('entrelumen_more_machine_audit')
    .requires(source => source.hasPermission(2))
    .executes(context => {
      const row = {schema: 1, kind: 'runtime_maps', run: String(Date.now()),
        status: 'FAIL', maps: {}, probes: {}, forbiddenRecipes: [], retainedRecipes: []};
      try {
        if (!Item.exists('mekmm:replicator')) throw new Error('mekmm is not loaded');
        // Rhino build.91 const initialization in this try/callback produced var redeclaration.
        // Function-local vars avoid that path; prefixed names avoid shared script bindings.
        var entrelumenAuditMapTypes = Java.loadClass('com.jerry.mekmm.api.datamaps.IMoreMachineDataMapTypes');
        var entrelumenAuditRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries');
        var entrelumenAuditMekanism = Java.loadClass('mekanism.api.MekanismAPI');
        var entrelumenAuditLocation = Java.loadClass('net.minecraft.resources.ResourceLocation');
        var maps = entrelumenAuditMapTypes.INSTANCE;
        row.maps.item = entrelumenAuditRegistries.ITEM.getDataMap(maps.itemReplicatorRecipe()).size();
        row.maps.fluid = entrelumenAuditRegistries.FLUID.getDataMap(maps.fluidReplicatorRecipe()).size();
        row.maps.chemical = entrelumenAuditMekanism.CHEMICAL_REGISTRY.getDataMap(maps.chemicalReplicatorRecipe()).size();
        var probe = (registry, type, id) => {
          var holder = registry.getHolder(entrelumenAuditLocation.parse(id));
          if (!holder.isPresent()) throw new Error('Missing probe holder: ' + id);
          row.probes[id] = holder.get().getData(type) !== null;
        };
        probe(entrelumenAuditRegistries.ITEM, maps.itemReplicatorRecipe(), 'minecraft:iron_ingot');
        probe(entrelumenAuditRegistries.FLUID, maps.fluidReplicatorRecipe(), 'minecraft:water');
        probe(entrelumenAuditMekanism.CHEMICAL_REGISTRY, maps.chemicalReplicatorRecipe(), 'mekanism:fissile_fuel');
        probe(entrelumenAuditMekanism.CHEMICAL_REGISTRY, maps.chemicalReplicatorRecipe(), 'mekanism:antimatter');
        var keep = ['mekmm:factory/basic/oxidizing', 'mekmm:factory/ultimate/oxidizing'];
        context.getSource().getServer().getRecipeManager().getRecipes().forEach(holder => {
          var id = String(holder.id());
          if (entrelumenReplicationRecipes.indexOf(id) >= 0 || entrelumenBlockedMoreMachine.test(id)) row.forbiddenRecipes.push(id);
          if (keep.indexOf(id) >= 0) row.retainedRecipes.push(id);
        });
        row.status = row.maps.item === 0 && row.maps.fluid === 0 && row.maps.chemical === 0 &&
          Object.keys(row.probes).every(id => row.probes[id] === false) &&
          row.forbiddenRecipes.length === 0 && row.retainedRecipes.length === 2 ? 'PASS' : 'FAIL';
      } catch (error) { row.error = String(error); }
      // Receipt reads loaded registries and recipe manager, never the source override JSON.
      console.info('[ENTRELUMEN_MORE_MACHINE] ' + JSON.stringify(row));
      context.getSource().sendSystemMessage(Text.of('ENTRELUMEN More Machine audit: ' + row.status + ' (server log)'));
      return row.status === 'PASS' ? 1 : 0;
    }));
});
