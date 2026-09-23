package dev.entrelumen;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Included only in the QA JAR, where synthetic item stand-ins are forbidden. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class FullpackGameTests {
  private static final Map<String, String> NATIVE_ITEMS = Map.of(
      "mekanism", "alloy_atomic",
      "occultism", "iesnium_ingot",
      "twilightforest", "steeleaf_ingot",
      "aether", "zanite_gemstone");

  static void requireNativeInputs() {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Full-pack QA cannot load synthetic GameTest items");
    NATIVE_ITEMS.forEach((mod, path) -> {
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real mod is absent: " + mod);
      var id = ResourceLocation.fromNamespaceAndPath(mod, path);
      if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR)
        throw new IllegalStateException("Required real item is absent: " + id);
    });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeActSixInputsComeFromRealMods(GameTestHelper helper) {
    requireNativeInputs();
    helper.succeed();
  }
}
