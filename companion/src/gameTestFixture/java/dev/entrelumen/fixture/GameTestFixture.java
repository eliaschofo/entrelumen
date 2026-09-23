package dev.entrelumen.fixture;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Registry stand-ins for the isolated GameTest server; never included in pack artifacts. */
@Mod("entrelumen_gametest_fixture")
public final class GameTestFixture {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Map<String, String> ITEMS = Map.of(
      "mekanism", "alloy_atomic",
      "occultism", "iesnium_ingot",
      "twilightforest", "steeleaf_ingot",
      "aether", "zanite_gemstone");

  public GameTestFixture(IEventBus bus) {
    LOGGER.warn("Entrelumen isolated GameTests use four synthetic cross-mod item IDs; this is not full-pack compatibility evidence.");
    bus.addListener(this::registerItems);
  }

  private void registerItems(RegisterEvent event) {
    event.register(Registries.ITEM, registry -> ITEMS.forEach((mod, path) -> {
      if (ModList.get().isLoaded(mod)) return;
      var id = ResourceLocation.fromNamespaceAndPath(mod, path);
      if (!BuiltInRegistries.ITEM.containsKey(id))
        registry.register(id, new Item(new Item.Properties()));
    }));
  }
}
