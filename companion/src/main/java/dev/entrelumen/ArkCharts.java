package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The Ark chart room. A filled map held at a complete Ark absorbs the explored detail of the
 * player's other maps of the same area: same or finer scale, same dimension, same kind of view.
 * Only unexplored pixels change, nothing is consumed or created, and there is no ticking.
 */
public final class ArkCharts {
  public enum Status { COMPILED, NOTHING_NEW, NO_SOURCES, LOCKED, NO_DATA, STRUCTURE, UNAVAILABLE }

  /** Pixels filled, maps that matched, maps that supplied a pixel and other maps left out. */
  public record Outcome(Status status, int pixels, int matched, int contributed, int skipped) {
    static Outcome of(Status status) {
      return new Outcome(status, 0, 0, 0, 0);
    }

    public boolean success() {
      return status == Status.COMPILED || status == Status.NOTHING_NEW;
    }
  }

  /**
   * Saved keys other mods add beside vanilla map data that change what a pixel shows. A pinned
   * Supplementaries slice map stores its depth as {@code depth_lock}; its pixels are a slice, not
   * the surface, so only charts with the same value may combine.
   */
  static final List<String> VIEW_KEYS = List.of("depth_lock");

  private ArkCharts() {}

  public static Outcome compile(ServerPlayer player, BlockPos module, InteractionHand hand) {
    if (!reachable(player, module, hand)) return Outcome.of(Status.UNAVAILABLE);
    ItemStack held = player.getItemInHand(hand);
    if (!held.is(Items.FILLED_MAP)) return Outcome.of(Status.UNAVAILABLE);
    ServerLevel level = player.serverLevel();
    MapId targetId = held.get(DataComponents.MAP_ID);
    MapItemSavedData target = targetId == null ? null : level.getMapData(targetId);
    if (target == null)
      return refuse(player, Status.NO_DATA, Component.translatable("entrelumen.exploration.chart.no_data"));
    if (target.locked)
      return refuse(player, Status.LOCKED, Component.translatable("entrelumen.exploration.chart.locked"));
    if (LogisticsModuleActions.controllerForDeposit(
        EngineeringDiagnostics.physicalView(level, module)) == null)
      return refuse(player, Status.STRUCTURE, Component.translatable("entrelumen.exploration.chart.structure"));

    HolderLookup.Provider registries = level.registryAccess();
    ArkChartRules.Grid grid = grid(target);
    CompoundTag view = view(target, registries);
    List<ArkChartRules.Chart> sources = new ArrayList<>();
    Set<MapId> seen = new HashSet<>();
    seen.add(targetId);
    int skipped = 0;
    for (ItemStack stack : candidates(player)) {
      if (!stack.is(Items.FILLED_MAP)) continue;
      MapId id = stack.get(DataComponents.MAP_ID);
      // Copies share one map id and one set of pixels; the chart's own copies are not sources.
      if (id == null || !seen.add(id)) continue;
      MapItemSavedData data = level.getMapData(id);
      if (data == null || !data.dimension.equals(target.dimension)
          || ArkChartRules.fit(grid, grid(data)) != ArkChartRules.Fit.FITS
          || !view.equals(view(data, registries))) {
        skipped++;
        continue;
      }
      sources.add(new ArkChartRules.Chart(grid(data), data.colors));
    }
    if (sources.isEmpty()) {
      player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.no_sources"));
      if (skipped > 0)
        player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.skipped", skipped));
      return new Outcome(Status.NO_SOURCES, 0, 0, 0, skipped);
    }

    // Everything is decided before the first pixel changes; the apply loop cannot stop halfway.
    var absorbed = ArkChartRules.absorb(new ArkChartRules.Chart(grid, target.colors), sources);
    if (absorbed.filled() == 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.nothing", sources.size()));
      if (skipped > 0)
        player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.skipped", skipped));
      return new Outcome(Status.NOTHING_NEW, 0, sources.size(), 0, skipped);
    }
    byte[] colors = absorbed.colors();
    for (int index = 0; index < ArkChartRules.PIXELS; index++)
      if (colors[index] != target.colors[index])
        target.updateColor(index % ArkChartRules.SIZE, index / ArkChartRules.SIZE, colors[index]);
    level.playSound(null, module, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 0.6f, 1.0f);
    player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.compiled",
        absorbed.filled(), absorbed.contributors(), sources.size()));
    if (skipped > 0)
      player.sendSystemMessage(Component.translatable("entrelumen.exploration.chart.skipped", skipped));
    return new Outcome(Status.COMPILED, absorbed.filled(), sources.size(), absorbed.contributors(), skipped);
  }

  /** Book rendering explains the gesture; the service keeps no team record. */
  public static List<Component> journalLines() {
    return List.of(Component.translatable("entrelumen.exploration.chart.journal"));
  }

  static ArkChartRules.Grid grid(MapItemSavedData data) {
    return new ArkChartRules.Grid(data.centerX, data.centerZ, data.scale);
  }

  /** The main inventory in slot order, then the offhand; armor and containers are never read. */
  private static List<ItemStack> candidates(ServerPlayer player) {
    List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
    stacks.addAll(player.getInventory().offhand);
    return stacks;
  }

  /** The view keys a map's saved data carries; vanilla maps have none. */
  static CompoundTag view(MapItemSavedData data, HolderLookup.Provider registries) {
    return viewOf(data.save(new CompoundTag(), registries));
  }

  static CompoundTag viewOf(CompoundTag saved) {
    CompoundTag view = new CompoundTag();
    for (String key : VIEW_KEYS) {
      var value = saved.get(key);
      if (value != null) view.put(key, value.copy());
    }
    return view;
  }

  private static boolean reachable(ServerPlayer player, BlockPos module, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND && !player.isSpectator()
        && player.canInteractWithBlock(module, 1.0)
        && player.serverLevel().hasChunkAt(module)
        && player.serverLevel().getBlockState(module).getBlock() instanceof ArkFieldJournalBlock block
        && block.kind() == ArkFieldJournals.Kind.EXPLORATION;
  }

  private static Outcome refuse(ServerPlayer player, Status status, Component message) {
    player.sendSystemMessage(message);
    return Outcome.of(status);
  }
}
