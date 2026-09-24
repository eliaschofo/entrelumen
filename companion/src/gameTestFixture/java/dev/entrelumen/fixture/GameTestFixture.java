package dev.entrelumen.fixture;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

  /** Eaten in place like a Farmer's Delight pie: a slice's food values through FoodData.eat. */
  static final class BiteBlock extends Block {
    static final FoodProperties SLICE = new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build();

    BiteBlock() {
      super(BlockBehaviour.Properties.of());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit) {
      if (level.isClientSide) return InteractionResult.SUCCESS;
      if (!player.canEat(false)) return InteractionResult.PASS;
      player.getFoodData().eat(SLICE);
      return InteractionResult.SUCCESS;
    }
  }

  private void registerItems(RegisterEvent event) {
    event.register(Registries.BLOCK, registry -> registry.register(
        ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "bite_block"), new BiteBlock()));
    event.register(Registries.ITEM, registry -> ITEMS.forEach((mod, path) -> {
      if (ModList.get().isLoaded(mod)) return;
      var id = ResourceLocation.fromNamespaceAndPath(mod, path);
      if (!BuiltInRegistries.ITEM.containsKey(id))
        registry.register(id, new Item(new Item.Properties()));
    }));
  }
}
