package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ENTRELUMEN content that builds on the Apotheosis suite: four enchanting shelves, the Atlas
 * Library and sixteen spawner augments, plus the World Tier projection. Nothing here links
 * against Apotheosis classes; with the suite absent the shelves act as plain bookshelves, the
 * library uses the vanilla shelf rule and the augments have no recipe that uses them.
 */
public final class ApotheosisContent {
  /** Shelf id to its vanilla enchanting power (used only without Apothic Enchanting). */
  public static final Map<String, Float> SHELVES = new LinkedHashMap<>();

  static {
    SHELVES.put("cartographer_shelf", 1.0f);
    SHELVES.put("patina_shelf", 1.0f);
    SHELVES.put("lumen_shelf", 1.5f);
    SHELVES.put("horizon_shelf", 2.0f);
  }

  public static final List<String> AUGMENTS =
      SpawnerAugmentItem.MODIFIERS.stream().map(SpawnerAugmentItem::id).toList();

  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, "entrelumen");
  public static final DeferredHolder<MenuType<?>, MenuType<AtlasLibraryMenu>> ATLAS_LIBRARY_MENU =
      MENUS.register("atlas_library", () -> IMenuTypeExtension.create(
          (id, inventory, buf) -> new AtlasLibraryMenu(id, inventory, buf.readBlockPos())));
  public static DeferredBlock<AtlasLibraryBlock> ATLAS_LIBRARY;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<AtlasLibraryBlockEntity>> ATLAS_LIBRARY_ENTITY;

  private ApotheosisContent() {}

  /** Adds entries to the companion's existing registers; call before they are attached. */
  static void register(DeferredRegister.Items items, DeferredRegister.Blocks blocks,
      DeferredRegister<BlockEntityType<?>> blockEntities) {
    SHELVES.forEach((id, power) -> {
      var properties = switch (id) {
        case "cartographer_shelf" -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
            .strength(1.5f).sound(SoundType.WOOD).ignitedByLava();
        case "patina_shelf" -> BlockBehaviour.Properties.of().mapColor(MapColor.WARPED_STEM)
            .strength(3f, 6f).sound(SoundType.COPPER).requiresCorrectToolForDrops();
        case "lumen_shelf" -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(3f, 6f).sound(SoundType.AMETHYST).lightLevel(state -> 7).requiresCorrectToolForDrops();
        default -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
            .strength(5f, 12f).sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 5).requiresCorrectToolForDrops();
      };
      var block = blocks.register(id, () -> new EnchantingShelfBlock(power, properties));
      items.registerSimpleBlockItem(id, block);
    });
    ATLAS_LIBRARY = blocks.register("atlas_library", () -> new AtlasLibraryBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(5f, 1200f)
            .sound(SoundType.WOOD).requiresCorrectToolForDrops()));
    items.register("atlas_library",
        () -> new BlockItem(ATLAS_LIBRARY.get(), new Item.Properties().stacksTo(1)));
    ATLAS_LIBRARY_ENTITY = blockEntities.register("atlas_library",
        () -> BlockEntityType.Builder.of(AtlasLibraryBlockEntity::new, ATLAS_LIBRARY.get()).build(null));
    for (String modifier : SpawnerAugmentItem.MODIFIERS)
      items.register(SpawnerAugmentItem.id(modifier),
          () -> new SpawnerAugmentItem(modifier, new Item.Properties().stacksTo(16)));
  }

  /** Mod-bus and game-bus wiring, called once from the companion constructor. */
  static void bootstrap(IEventBus bus) {
    MENUS.register(bus);
    bus.addListener(AtlasLibraryNetwork::register);
    bus.addListener(ApotheosisContent::registerCapabilities);
    NeoForge.EVENT_BUS.addListener(ApotheosisTiers::tick);
    TeamEvent.PLAYER_LOGGED_IN.register(event -> ApotheosisTiers.sync(event.getPlayer()));
    TeamEvent.PLAYER_JOINED_PARTY.register(event -> ApotheosisTiers.sync(event.getPlayer()));
    // Leaving a party moves the player back to their personal campaign and its tier.
    TeamEvent.PLAYER_CHANGED.register(event -> {
      if (event.getPlayer() != null) ApotheosisTiers.sync(event.getPlayer());
    });
  }

  private static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ATLAS_LIBRARY_ENTITY.get(),
        (library, side) -> library.depositHandler());
  }
}
