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
}
