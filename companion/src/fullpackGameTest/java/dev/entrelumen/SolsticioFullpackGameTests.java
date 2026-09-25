package dev.entrelumen;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * QA-JAR-only check that the Solsticio shop and native tables (data/entrelumen/solsticio_shops and
 * solsticio_natives) name only items and item tags that exist once the whole pack is loaded. The
 * table parser checks syntax only; the commerce worker checked the IDs against the JARs.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class SolsticioFullpackGameTests {
  private SolsticioFullpackGameTests() {}

  private static void item(String where, String id, List<String> missing) {
    if (id == null) return;
    var location = ResourceLocation.tryParse(id);
    if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) missing.add(where + " " + id);
  }

  private static void offer(String where, CommerceRules.OfferSpec offer, List<String> missing) {
    var sell = offer.sell();
    if (sell.expandsTag()) {
      String tag = sell.tag().startsWith("#") ? sell.tag().substring(1) : sell.tag();
      var location = ResourceLocation.tryParse(tag);
      if (location == null || BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, location))
          .map(set -> set.size() == 0).orElse(true))
        missing.add(where + " empty or unknown tag " + sell.tag());
    } else {
      item(where + " sells", sell.item(), missing);
    }
    if (offer.price() != null) item(where + " price", offer.price().item(), missing);
    if (offer.extra() != null) item(where + " extra", offer.extra().item(), missing);
  }

  /**
   * Structure templates turn unknown block names into air without logging, so the city is checked
   * against the loaded block registry before placement: every palette entry of every Solsticio
   * piece must name a registered block.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void solsticioCityPaletteResolvesInTheFullPack(GameTestHelper helper) throws java.io.IOException {
    var resources = helper.getLevel().getServer().getResourceManager().listResources("structure/solsticio",
        location -> location.getPath().endsWith(".nbt"));
    helper.assertTrue(!resources.isEmpty(), "No Solsticio templates found");
    Map<String, Integer> unknown = new TreeMap<>();
    int entries = 0;
    for (var entry : resources.entrySet()) {
      if (!entry.getKey().getNamespace().equals("entrelumen")) continue;
      net.minecraft.nbt.CompoundTag tag;
      try (var in = entry.getValue().open()) {
        tag = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
      }
      List<net.minecraft.nbt.ListTag> palettes = new ArrayList<>();
      if (tag.contains("palette", net.minecraft.nbt.Tag.TAG_LIST))
        palettes.add(tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND));
      var many = tag.getList("palettes", net.minecraft.nbt.Tag.TAG_LIST);
      for (int i = 0; i < many.size(); i++) palettes.add(many.getList(i));
      for (var palette : palettes)
        for (int i = 0; i < palette.size(); i++) {
          String name = palette.getCompound(i).getString("Name");
          entries++;
          var location = ResourceLocation.tryParse(name);
          if (location == null || !BuiltInRegistries.BLOCK.containsKey(location))
            unknown.merge(entry.getKey().getPath() + " " + name, 1, Integer::sum);
        }
    }
    helper.assertTrue(entries > 0, "Solsticio templates have no palette");
    helper.assertTrue(unknown.isEmpty(), entries + " palette entries; unregistered: " + unknown);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void solsticioShopTablesResolveInTheFullPack(GameTestHelper helper) {
    Map<String, CommerceRules.Table> tables = new TreeMap<>();
    SolsticioCommerce.shops().forEach((id, table) -> tables.put("shop " + id, table));
    SolsticioCommerce.natives().forEach((id, table) -> tables.put("native " + id, table));
    helper.assertTrue(!SolsticioCommerce.shops().isEmpty() && !SolsticioCommerce.natives().isEmpty(),
        "Solsticio tables did not load: " + tables.keySet());
    List<String> missing = new ArrayList<>();
    int offers = 0;
    for (var entry : tables.entrySet()) {
      var table = entry.getValue();
      for (var spec : table.offers()) {
        offer(entry.getKey(), spec, missing);
        offers++;
      }
      if (table.luminosity() != null) {
        offer(entry.getKey() + " luminosity", table.luminosity(), missing);
        offers++;
      }
    }
    helper.assertTrue(missing.isEmpty(), offers + " offers checked; unresolved: " + missing);
    helper.succeed();
  }

  /**
   * Act VI's deliveries name real items once the whole pack is loaded: Juan's seeds and Farmer's
   * Delight meals (tags), Terra's Create: New Age parts, and at least one mod battery that can hold
   * the 100,000 FE Terra asks for.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void solsticioStoryDeliveriesResolveInTheFullPack(GameTestHelper helper) {
    Set<String> seeds = new TreeSet<>(), meals = new TreeSet<>();
    BuiltInRegistries.ITEM.getTagOrEmpty(SolsticioStory.SEEDS)
        .forEach(holder -> seeds.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
    BuiltInRegistries.ITEM.getTagOrEmpty(SolsticioStory.MEALS)
        .forEach(holder -> meals.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString()));
    helper.assertTrue(seeds.size() >= SolsticioStoryRules.SEED_SPECIES + 4, "Too few seed species: " + seeds);
    helper.assertTrue(meals.size() >= 20 && meals.stream().allMatch(id -> id.startsWith("farmersdelight:")),
        "Farmer's Delight meals: " + meals);
    List<String> missing = new ArrayList<>();
    for (String id : SolsticioStory.POWER_PARTS.keySet()) item("Terra", id, missing);
    helper.assertTrue(missing.isEmpty(), "Missing Create: New Age parts: " + missing);
    List<String> batteries = new ArrayList<>();
    for (var item : BuiltInRegistries.ITEM) {
      var energy = new net.minecraft.world.item.ItemStack(item)
          .getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
      if (energy != null && energy.getMaxEnergyStored() >= SolsticioStoryRules.BATTERY_CAPACITY)
        batteries.add(BuiltInRegistries.ITEM.getKey(item).toString());
    }
    helper.assertTrue(batteries.size() >= 3, "Batteries that fit Terra's request: " + batteries);
    com.mojang.logging.LogUtils.getLogger().info("Act VI deliveries: {} seed species, {} meals, {} batteries (e.g. {})",
        seeds.size(), meals.size(), batteries.size(), batteries.subList(0, Math.min(8, batteries.size())));
    helper.succeed();
  }
}
