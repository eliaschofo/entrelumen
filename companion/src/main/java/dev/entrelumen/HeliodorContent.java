package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Start of the game: the Heliodor start ruin, its pedestal and the Heliodor compass. */
public final class HeliodorContent {
  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("entrelumen");
  static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "entrelumen");

  public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompassState>>
      COMPASS_STATE = COMPONENTS.registerComponentType("compass_state",
          builder -> builder.persistent(CompassState.CODEC)
              .networkSynchronized(CompassState.STREAM_CODEC));
  public static final DeferredItem<HeliodorCompassItem> COMPASS = ITEMS.register(
      "heliodor_compass", () -> new HeliodorCompassItem(new Item.Properties().stacksTo(1)));
  public static final DeferredBlock<HeliodorPedestalBlock> PEDESTAL = BLOCKS.register(
      "heliodor_pedestal", () -> new HeliodorPedestalBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.TERRACOTTA_CYAN).strength(-1f, 3_600_000f).noLootTable()
          .sound(SoundType.TUFF).lightLevel(state -> 6)));

  static {
    ITEMS.registerSimpleBlockItem("heliodor_pedestal", PEDESTAL);
  }

  private HeliodorContent() {}

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    COMPONENTS.register(bus);
    NeoForge.EVENT_BUS.addListener(HeliodorRuins::onCreateSpawn);
    NeoForge.EVENT_BUS.addListener(HeliodorRuins::onServerStarted);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, HeliodorRuins::onLogin);
    NeoForge.EVENT_BUS.addListener(
        (AddReloadListenerEvent event) -> event.addListener(new TargetsReloadListener()));
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
        CompassLocator.of(event.getServer()).tick(CompassLocator.BUDGET_NANOS));
    NeoForge.EVENT_BUS.addListener(
        (ServerStoppedEvent event) -> CompassLocator.forget(event.getServer()));
    NeoForge.EVENT_BUS.addListener(HeliodorContent::commands);
  }

  /** {@code /entrelumen admin ruin}: places the start ruin in a world created before it existed. */
  private static void commands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("ruin").executes(context -> {
              var server = context.getSource().getServer();
              var data = RuinData.get(server);
              boolean existed = data.find(HeliodorRuins.START).isPresent();
              var overworld = server.overworld();
              var ruin = HeliodorRuins.place(overworld, data, overworld.getSharedSpawnPos(), true);
              if (ruin.isEmpty()) return 0;
              var arrival = ruin.get().arrival();
              context.getSource().sendSuccess(() -> Component.translatable(
                  existed ? "entrelumen.ruin.exists" : "entrelumen.ruin.placed",
                  arrival.getX(), arrival.getY(), arrival.getZ()), true);
              return 1;
            }))));
  }

  /** Reads {@code entrelumen:compass/targets.json}; a rejected document keeps the previous list. */
  static final class TargetsReloadListener
      extends SimplePreparableReloadListener<List<CompassTargets.Objective>> {
    static final ResourceLocation RESOURCE =
        ResourceLocation.fromNamespaceAndPath("entrelumen", "compass/targets.json");

    @Override
    protected List<CompassTargets.Objective> prepare(ResourceManager manager, ProfilerFiller profiler) {
      var resource = manager.getResource(RESOURCE).orElse(null);
      if (resource == null) {
        LogUtils.getLogger().warn("Missing {}; the Heliodor compass has no objectives", RESOURCE);
        return List.of();
      }
      try (var reader = resource.openAsReader()) {
        return CompassTargets.parse(JsonParser.parseReader(reader), ModList.get()::isLoaded,
            BuiltInRegistries.ITEM::containsKey);
      } catch (IOException | RuntimeException e) {
        throw new IllegalStateException("Rejected " + RESOURCE + " from datapack "
            + resource.sourcePackId() + "; previous compass objectives retained. "
            + e.getMessage(), e);
      }
    }

    @Override
    protected void apply(List<CompassTargets.Objective> objectives, ResourceManager manager,
        ProfilerFiller profiler) {
      CompassTargets.publish(objectives);
      LogUtils.getLogger().info("Loaded {} Heliodor compass objectives", objectives.size());
    }
  }
}
