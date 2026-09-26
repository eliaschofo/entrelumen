package dev.entrelumen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps each team's one Ark in {@link ArkData} up to date. The six modules and the controller report
 * their own placement and removal (any cause: players, explosions, pistons, commands); a player's
 * block changes inside an Ark and explosions schedule a rescan of that Ark; and every Ark whose
 * blocks are loaded is rescanned every five seconds. Effects read the saved state, so they keep
 * working while the Ark is unloaded, and never load a chunk to check it.
 *
 * <p>One Ark per team: the controller a team member places founds it (the controller marks where the
 * Ark is built), at the facing that best matches the core and modules already there; later pieces
 * count only inside it. It is forgotten when its controller and all its modules are gone, so the team
 * can build again elsewhere.
 */
public final class ArkState {
  static final int RESCAN_TICKS = 100;
  private static final Set<UUID> SOON = new HashSet<>();

  public enum Placement { FOUNDED, JOINED, WRONG_SLOT, OTHER_ARK, OTHER_TEAM, NEEDS_CONTROLLER, NO_TEAM }

  private ArkState() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> soon(event.getLevel(), event.getPos()));
    NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> soon(event.getLevel(), event.getPos()));
    NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate event) -> {
      for (BlockPos pos : event.getAffectedBlocks()) soon(event.getLevel(), pos);
    });
  }

  /** The Ark slot a block fills: the controller or a module ID; empty for any other block. */
  public static String slotOf(Block block) {
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
    return id.getNamespace().equals("entrelumen") && ArkRules.SLOTS.contains(id.getPath()) ? id.getPath() : "";
  }

  /**
   * Copper matches any oxidation and its waxed form; a Rechiseled block matches its {@code _connecting}
   * variant; every other block matches itself.
   */
  public static ResourceLocation normalise(ResourceLocation id) {
    ResourceLocation plain = ArkMultiblock.withoutConnecting(id);
    if (!plain.equals(id)) return plain;
    Block block = BuiltInRegistries.BLOCK.get(id);
    Block unwaxed = DataMapHooks.getBlockUnwaxed(block);
    if (unwaxed != null) block = unwaxed;
    return BuiltInRegistries.BLOCK.getKey(WeatheringCopper.getFirst(block));
  }

  /** Reads blocks and their properties without loading chunks. */
  static ArkMultiblock.Matcher matcher(ServerLevel level) {
    return ArkMultiblock.matcher(reader(level), (at, name) -> property(level, at, name), ArkState::normalise);
  }

  private static String property(ServerLevel level, BlockPos at, String name) {
    var state = level.getBlockState(at);
    var property = state.getBlock().getStateDefinition().getProperty(name);
    return property == null ? null : value(state, property);
  }

  private static <T extends Comparable<T>> String value(net.minecraft.world.level.block.state.BlockState state,
      net.minecraft.world.level.block.state.properties.Property<T> property) {
    return property.getName(state.getValue(property));
  }

  static Function<BlockPos, ResourceLocation> reader(ServerLevel level) {
    return pos -> level.isOutsideBuildHeight(pos) ? BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR)
        : level.hasChunkAt(pos) ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) : null;
  }

  /** The Ark status of the player's team. */
  public static ArkRules.Status status(ServerPlayer player) {
    return ArkData.get(player.server).status(CampaignActions.campaignId(player));
  }

  public static ArkData.Ark ark(ServerPlayer player) {
    return ArkData.get(player.server).arks.get(CampaignActions.campaignId(player));
  }

  // ---- placement -------------------------------------------------------------------------------

  /** A module or controller was placed; a player's placement founds or joins their team's Ark. */
  public static void onPlaced(ServerLevel level, BlockPos pos, String slot, LivingEntity placer) {
    if (slot.isEmpty()) return;
    if (placer instanceof ServerPlayer player) {
      Placement result = place(level, pos, slot, CampaignActions.campaignId(player));
      tell(player, result, level.getServer());
    } else {
      refreshAt(level, pos);
    }
  }

  /** Records a piece placed by a member of {@code team}; returns what it did. */
  public static Placement place(ServerLevel level, BlockPos pos, String slot, UUID team) {
    if (team == null) return Placement.NO_TEAM;
    ArkData data = ArkData.get(level.getServer());
    var definition = ArkMultiblock.definition();
    ArkData.Ark ark = data.arks.get(team);
    if (ark != null) {
      if (!ark.dimension.equals(level.dimension())) return Placement.OTHER_ARK;
      if (slot.equals(ArkMultiblock.slotAt(definition, ark.anchor, pos))) {
        rescan(level, ark, data);
        return Placement.JOINED;
      }
      // A fresh Ark (no module yet) turns to face the first module placed round its controller.
      if (ark.modules.isEmpty())
        for (int rotation = 0; rotation < 4; rotation++) {
          var turned = new ArkMultiblock.Anchor(ark.anchor.origin(), rotation);
          if (slot.equals(ArkMultiblock.slotAt(definition, turned, pos))) {
            ark.anchor = turned;
            rescan(level, ark, data);
            data.setDirty();
            return Placement.JOINED;
          }
        }
      if (ArkMultiblock.contains(definition, ark.anchor, pos)) {
        rescan(level, ark, data);
        return Placement.WRONG_SLOT;
      }
      return Placement.OTHER_ARK;
    }
    // Only the controller founds an Ark: a stray module never claims a place for the team.
    if (!slot.equals(ArkMultiblock.CONTROLLER)) return Placement.NEEDS_CONTROLLER;
    var anchor = ArkMultiblock.locate(definition, slot, pos, matcher(level));
    UUID owner = data.owner(level.dimension(), anchor);
    if (owner != null && !owner.equals(team)) return Placement.OTHER_TEAM;
    ark = new ArkData.Ark(level.dimension(), anchor);
    data.arks.put(team, ark);
    rescan(level, ark, data);
    data.setDirty();
    return Placement.FOUNDED;
  }

  private static void tell(ServerPlayer player, Placement result, MinecraftServer server) {
    ArkData.Ark ark = ArkData.get(server).arks.get(CampaignActions.campaignId(player));
    switch (result) {
      case FOUNDED, JOINED -> {
        if (ark != null && !ark.core)
          player.displayClientMessage(Component.translatable("entrelumen.ark.core_missing", ark.missingCore), true);
      }
      case WRONG_SLOT -> player.displayClientMessage(Component.translatable("entrelumen.ark.wrong_slot"), true);
      case OTHER_ARK -> {
        if (ark != null) {
          BlockPos at = ark.anchor.origin();
          player.displayClientMessage(Component.translatable("entrelumen.ark.other_ark", at.getX(), at.getY(),
              at.getZ(), ark.dimension.location().toString()), false);
        }
      }
      case OTHER_TEAM -> player.displayClientMessage(Component.translatable("entrelumen.ark.other_team"), true);
      case NEEDS_CONTROLLER -> player.displayClientMessage(Component.translatable("entrelumen.ark.needs_controller"), true);
      case NO_TEAM -> {}
    }
  }

  // ---- removal and rescans ---------------------------------------------------------------------

  /** A module or controller left its position (broken, blown up, moved or replaced). */
  public static void onRemoved(ServerLevel level, BlockPos pos) {
    refreshAt(level, pos);
  }

  /** Rescans every Ark of the level that has a part at {@code pos}. */
  static void refreshAt(ServerLevel level, BlockPos pos) {
    ArkData data = ArkData.get(level.getServer());
    var definition = ArkMultiblock.definition();
    for (var entry : List.copyOf(data.arks.entrySet())) {
      ArkData.Ark ark = entry.getValue();
      if (!ark.dimension.equals(level.dimension()) || !ArkMultiblock.contains(definition, ark.anchor, pos)) continue;
      rescan(level, ark, data);
      if (ark.empty()) {
        data.arks.remove(entry.getKey());
        data.setDirty();
      }
    }
  }

  private static void soon(net.minecraft.world.level.LevelAccessor accessor, BlockPos pos) {
    if (!(accessor instanceof ServerLevel level)) return;
    ArkData data = ArkData.get(level.getServer());
    for (var entry : data.arks.entrySet()) {
      ArkData.Ark ark = entry.getValue();
      if (ark.dimension.equals(level.dimension()) && near(ark, pos)) SOON.add(entry.getKey());
    }
  }

  private static boolean near(ArkData.Ark ark, BlockPos pos) {
    BlockPos origin = ark.anchor.origin();
    return Math.abs(pos.getX() - origin.getX()) <= 16 && Math.abs(pos.getZ() - origin.getZ()) <= 16
        && Math.abs(pos.getY() - origin.getY()) <= 16;
  }

  /**
   * Reads an Ark's blocks again. An Ark with an unreadable (unloaded) part keeps its saved state. One
   * without modules turns to the facing its built core matches best.
   */
  public static boolean rescan(ServerLevel level, ArkData.Ark ark, ArkData data) {
    var definition = ArkMultiblock.definition();
    var reader = matcher(level);
    var scan = ArkMultiblock.scan(definition, ark.anchor, reader);
    if (scan.unloaded()) return false;
    if (ark.modules.isEmpty() && scan.filledSlots().stream().noneMatch(slot -> !slot.equals(ArkMultiblock.CONTROLLER))) {
      int bestRotation = ark.anchor.rotation();
      int bestMissing = scan.missingCore().size();
      ArkMultiblock.Scan best = scan;
      for (int rotation = 0; rotation < 4; rotation++) {
        var turned = ArkMultiblock.scan(definition, new ArkMultiblock.Anchor(ark.anchor.origin(), rotation), reader);
        if (!turned.unloaded() && turned.missingCore().size() < bestMissing) {
          bestMissing = turned.missingCore().size();
          bestRotation = rotation;
          best = turned;
        }
      }
      if (bestRotation != ark.anchor.rotation()) {
        ark.anchor = new ArkMultiblock.Anchor(ark.anchor.origin(), bestRotation);
        data.setDirty();
      }
      scan = best;
    }
    Set<String> modules = new java.util.TreeSet<>(scan.filledSlots());
    boolean controller = modules.remove(ArkMultiblock.CONTROLLER);
    int beacons = Math.min(scan.beacons(), ArkRules.MAX_BEACONS);
    boolean changed = ark.core != scan.coreComplete() || ark.controller != controller
        || !ark.modules.equals(modules) || ark.missingCore != scan.missingCore().size() || ark.beacons != beacons;
    if (changed) {
      ark.core = scan.coreComplete();
      ark.controller = controller;
      ark.modules.clear();
      ark.modules.addAll(modules);
      ark.missingCore = scan.missingCore().size();
      ark.beacons = beacons;
      data.setDirty();
    }
    return changed;
  }

  /**
   * Whether a beacon at this position sits in a beacon place of a standing Ark (its core and its
   * controller in place): {@code BeaconBlockEntityMixin} then counts it as a full pyramid.
   */
  public static boolean arkBeacon(Level level, int x, int y, int z) {
    if (!(level instanceof ServerLevel server)) return false;
    ArkData data = ArkData.get(server.getServer());
    if (data.arks.isEmpty()) return false;
    BlockPos pos = new BlockPos(x, y, z);
    var definition = ArkMultiblock.definition();
    for (ArkData.Ark ark : data.arks.values()) {
      if (!ark.dimension.equals(server.dimension()) || !ark.core || !ark.controller || !near(ark, pos)) continue;
      for (BlockPos offset : definition.beacons())
        if (ArkMultiblock.world(ark.anchor, offset).equals(pos)) return true;
    }
    return false;
  }

  /** The scan behind the screens and the guide: which core positions are still missing. */
  public static ArkMultiblock.Scan scan(ServerLevel level, ArkMultiblock.Anchor anchor) {
    return ArkMultiblock.scan(ArkMultiblock.definition(), anchor, matcher(level));
  }

  static void tick(MinecraftServer server) {
    boolean periodic = server.getTickCount() % RESCAN_TICKS == 0;
    if (!periodic && SOON.isEmpty()) return;
    ArkData data = ArkData.get(server);
    List<UUID> forget = new ArrayList<>();
    for (Map.Entry<UUID, ArkData.Ark> entry : data.arks.entrySet()) {
      if (!periodic && !SOON.contains(entry.getKey())) continue;
      ArkData.Ark ark = entry.getValue();
      ServerLevel level = server.getLevel(ark.dimension);
      if (level == null) continue;
      rescan(level, ark, data);
      if (ark.empty()) forget.add(entry.getKey());
    }
    SOON.clear();
    for (UUID team : forget) {
      data.arks.remove(team);
      data.setDirty();
    }
  }

  /** The dimension key of an Ark, for messages. */
  static String where(ResourceKey<Level> dimension) {
    return dimension.location().toString();
  }
}
