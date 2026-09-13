// Generated read-only RecipeManager audit; never modifies recipes.
const elMalumAuditToken = "malum-five-20260912-1832";
const elMalumAuditSignature = "f5cc02fad2f036e3f9a7911828b7e15f3a42f42afc960a38c2bb70b588ba3f8e";
const elMalumAuditExpected = {"malum:malum/spirit_repair/occultism/gold_chalk": {"durabilityPercentage": 1, "repairMaterial": {"count": 1, "tag": "c:dusts/gold"}, "spirits": [{"type": "malum:arcane", "count": 8}], "validItems": ["occultism:chalk_gold"]}, "malum:malum/spirit_repair/occultism/purple_chalk": {"durabilityPercentage": 1, "repairMaterial": {"count": 2, "tag": "c:dusts/obsidian"}, "spirits": [{"type": "malum:arcane", "count": 8}], "validItems": ["occultism:chalk_purple"]}, "malum:malum/spirit_repair/occultism/red_chalk": {"durabilityPercentage": 1, "repairMaterial": {"count": 1, "item": "occultism:afrit_essence"}, "spirits": [{"type": "malum:arcane", "count": 8}], "validItems": ["occultism:chalk_red"]}, "malum:malum/spirit_repair/occultism/white_chalk": {"durabilityPercentage": 1, "repairMaterial": {"count": 1, "item": "occultism:burnt_otherstone"}, "spirits": [{"type": "malum:arcane", "count": 8}], "validItems": ["occultism:chalk_white"]}, "malum:create/milling/grim_talc": {"ingredients": [{"item": "malum:grim_talc"}], "processing_time": 100, "results": [{"count": 6, "id": "minecraft:bone_meal"}, {"chance": 0.25, "id": "minecraft:yellow_dye"}, {"chance": 0.25, "count": 4, "id": "minecraft:bone_meal"}]}};

ServerEvents.afterRecipes(event => {
  const ops = Java.loadClass('com.mojang.serialization.JsonOps').INSTANCE;
  const run = String(Date.now());
  const emit = row => {
    row.token = elMalumAuditToken; row.signature = elMalumAuditSignature; row.run = run;
    console.info('[ENTRELUMEN_MALUM_AUDIT] ' + JSON.stringify(row));
  };
  emit({kind: 'begin'});
  let failures = 0;
  Object.keys(elMalumAuditExpected).forEach(id => {
    let count = 0;
    try {
      event.forEachRecipe({id: id}, holder => {
        count++;
        const recipe = holder.getRecipe();
        const encoded = holder.getSerializer().codec().codec().encodeStart(ops, recipe).getOrThrow();
        emit({kind: 'recipe', id: id, actual: JSON.parse(String(encoded))});
      });
      if (count !== 1) { failures++; emit({kind: 'error', id: id, count: count}); }
    } catch (error) { failures++; emit({kind: 'error', id: id, message: String(error)}); }
  });
  emit({kind: 'summary', count: 5, failures: failures});
});
