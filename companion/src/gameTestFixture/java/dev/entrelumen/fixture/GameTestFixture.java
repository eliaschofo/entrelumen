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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import java.util.List;
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

  /**
   * Act VI (Solsticio) stand-ins: four Farmer's Delight meals and the two Create: New Age parts Terra
   * asks for, registered only when those mods are absent.
   */
  private static final List<String[]> STORY_ITEMS = List.of(
      new String[] {"farmersdelight", "beef_stew"}, new String[] {"farmersdelight", "fish_stew"},
      new String[] {"farmersdelight", "fried_rice"}, new String[] {"farmersdelight", "ratatouille"},
      new String[] {"create_new_age", "generator_coil"}, new String[] {"create_new_age", "advanced_solar_heating_plate"});
  /** A 200,000 FE item battery for Terra's charged-battery check (any energy mod's item counts in the pack). */
  static final ResourceLocation BATTERY = ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "test_battery");
  static DataComponentType<Integer> energy;
  static Item battery;

  public GameTestFixture(IEventBus bus) {
    LOGGER.warn("Entrelumen isolated GameTests use synthetic cross-mod item IDs; this is not full-pack compatibility evidence.");
    bus.addListener(this::registerItems);
    bus.addListener(this::registerCapabilities);
    bus.addListener(this::addPlayerAttributes);
  }

  /**
   * Ark v2 stand-ins: Rechiseled's polished amethyst and its stairs (the Ark's rim and columns) and Iron's
   * Spells' mana attributes (the Arcane module's bonus), each only when its mod is absent.
   */
  static final ResourceLocation POLISHED_AMETHYST = ResourceLocation.fromNamespaceAndPath("rechiseled", "amethyst_block_polished");
  static final ResourceLocation POLISHED_AMETHYST_STAIRS =
      ResourceLocation.fromNamespaceAndPath("rechiseled", "amethyst_block_polished_stairs");
  static final ResourceLocation MAX_MANA = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "max_mana");
  static final ResourceLocation MANA_REGEN = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "mana_regen");
  static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> maxMana, manaRegen;

  private void addPlayerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) {
    if (maxMana != null) event.add(net.minecraft.world.entity.EntityType.PLAYER, maxMana);
    if (manaRegen != null) event.add(net.minecraft.world.entity.EntityType.PLAYER, manaRegen);
  }

  private void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerItem(Capabilities.EnergyStorage.ITEM,
        (stack, context) -> new ComponentEnergyStorage(stack, energy, 200_000), battery);
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
    event.register(Registries.BLOCK, registry -> {
      registry.register(ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "bite_block"), new BiteBlock());
      if (!ModList.get().isLoaded("rechiseled")) {
        var amethyst = net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK;
        registry.register(POLISHED_AMETHYST, new Block(BlockBehaviour.Properties.ofFullCopy(amethyst)));
        registry.register(POLISHED_AMETHYST_STAIRS, new net.minecraft.world.level.block.StairBlock(
            amethyst.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(amethyst)));
      }
    });
    event.register(Registries.ATTRIBUTE, registry -> {
      if (ModList.get().isLoaded("irons_spellbooks")) return;
      var max = new net.minecraft.world.entity.ai.attributes.RangedAttribute("attribute.irons_spellbooks.max_mana",
          100.0, 0.0, 1_000_000.0).setSyncable(true);
      var regen = new net.minecraft.world.entity.ai.attributes.RangedAttribute("attribute.irons_spellbooks.mana_regen",
          1.0, 0.0, 100.0).setSyncable(true);
      registry.register(MAX_MANA, max);
      registry.register(MANA_REGEN, regen);
      maxMana = net.neoforged.neoforge.registries.DeferredHolder.create(Registries.ATTRIBUTE, MAX_MANA);
      manaRegen = net.neoforged.neoforge.registries.DeferredHolder.create(Registries.ATTRIBUTE, MANA_REGEN);
    });
    event.register(Registries.DATA_COMPONENT_TYPE, registry -> {
      energy = DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
          .networkSynchronized(ByteBufCodecs.VAR_INT).build();
      registry.register(ResourceLocation.fromNamespaceAndPath("entrelumen_gametest_fixture", "energy"), energy);
    });
    event.register(Registries.ITEM, registry -> {
      ITEMS.forEach((mod, path) -> {
        if (ModList.get().isLoaded(mod)) return;
        var id = ResourceLocation.fromNamespaceAndPath(mod, path);
        if (!BuiltInRegistries.ITEM.containsKey(id))
          registry.register(id, new Item(new Item.Properties()));
      });
      for (String[] item : STORY_ITEMS) {
        if (ModList.get().isLoaded(item[0])) continue;
        var id = ResourceLocation.fromNamespaceAndPath(item[0], item[1]);
        if (!BuiltInRegistries.ITEM.containsKey(id)) registry.register(id, new Item(new Item.Properties().stacksTo(16)));
      }
      battery = new Item(new Item.Properties().stacksTo(1));
      registry.register(BATTERY, battery);
    });
  }
}
