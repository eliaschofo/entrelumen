package dev.entrelumen;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registration of the Plan v2 ruins: challenge blocks, key pieces, their binding and events. */
public final class RuinContent {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "entrelumen");

  private static BlockBehaviour.Properties fixed(MapColor color, SoundType sound) {
    return BlockBehaviour.Properties.of().mapColor(color).strength(-1f, 3_600_000f).noLootTable()
        .sound(sound).pushReaction(PushReaction.BLOCK);
  }

  public static final DeferredBlock<RuinBlocks.Answering> PEDESTAL = BLOCKS.register("ruin_pedestal",
      () -> new RuinBlocks.Answering(fixed(MapColor.GOLD, SoundType.TUFF).lightLevel(state -> 9).noOcclusion()));
  public static final DeferredBlock<RuinBlocks.Mirror> MIRROR = BLOCKS.register("ruin_mirror",
      () -> new RuinBlocks.Mirror(fixed(MapColor.METAL, SoundType.COPPER).noOcclusion()));
  public static final DeferredBlock<RuinBlocks.Socket> SOCKET = BLOCKS.register("ruin_socket",
      () -> new RuinBlocks.Socket(fixed(MapColor.STONE, SoundType.DECORATED_POT).noOcclusion()));
  public static final DeferredBlock<RuinBlocks.Hidden> HIDDEN = BLOCKS.register("ruin_hidden",
      () -> new RuinBlocks.Hidden(fixed(MapColor.STONE, SoundType.TUFF_BRICKS)));
  public static final DeferredBlock<RuinBlocks.Lock> LOCK = BLOCKS.register("ruin_lock",
      () -> new RuinBlocks.Lock(fixed(MapColor.COLOR_ORANGE, SoundType.COPPER)));
  public static final DeferredBlock<RuinBlocks.Gate> GATE = BLOCKS.register("ruin_gate",
      () -> new RuinBlocks.Gate(fixed(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST).noOcclusion()
          .dynamicShape().lightLevel(state -> state.getValue(RuinBlocks.LOOK) == RuinBlocks.Look.SEAL ? 7 : 0)
          .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
          .isValidSpawn((state, level, pos, type) -> false)));

  public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeyPieces.Binding>> BINDING =
      COMPONENTS.registerComponentType("key_binding", builder -> builder
          .persistent(KeyPieces.Binding.CODEC).networkSynchronized(KeyPieces.Binding.STREAM_CODEC));

  /** The ten key pieces by item path. */
  public static final Map<String, DeferredItem<KeyPieces.Piece>> PIECES = new LinkedHashMap<>();

  static {
    for (var entry : KeyPieces.PIECES.entrySet())
      PIECES.put(entry.getKey(), ITEMS.register(entry.getKey(), () -> new KeyPieces.Piece(
          new Item.Properties().stacksTo(1).rarity(entry.getValue()).fireResistant())));
    for (var block : java.util.List.of(PEDESTAL, MIRROR, SOCKET, HIDDEN, LOCK, GATE))
      ITEMS.registerSimpleBlockItem(block);
  }

  private RuinContent() {}

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    COMPONENTS.register(bus);
    bus.addListener(RuinGates::registerPayloads);
    var events = NeoForge.EVENT_BUS;
    events.addListener((AddReloadListenerEvent event) -> event.addListener(new RuinRegistry.ReloadListener()));
    events.addListener((ServerTickEvent.Post event) -> {
      RuinPlacement.tick(event.getServer());
      RuinChallenges.tick(event.getServer());
      RuinBosses.tick(event.getServer());
      RuinGates.tick(event.getServer());
    });
    events.addListener((ServerStartedEvent event) -> RuinPlacement.resumeReserved(event.getServer()));
    events.addListener((ServerStoppedEvent event) -> {
      RuinChallenges.forget(event.getServer());
      RuinBosses.forget(event.getServer());
      RuinGates.forget(event.getServer());
    });
    events.addListener(RuinPlacement::onChangedDimension);
    events.addListener(RuinPlacement::onLogin);
    events.addListener(EventPriority.HIGH, RuinChallenges::onRightClickBlock);
    events.addListener(RuinBosses::onDeath);
    events.addListener(RuinBosses::onJoin);
    events.addListener(RuinCommands::register);
  }
}
