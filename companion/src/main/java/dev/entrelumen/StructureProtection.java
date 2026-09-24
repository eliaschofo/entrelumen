package dev.entrelumen;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The common protection of Heliodor structures: Solsticio and the Overworld ruins. Regions come
 * from registered providers (each reads its own saved data) plus transient regions; they are
 * indexed per dimension by chunk and rebuilt lazily after {@link #invalidate}. Every check runs on
 * the logical server at the moment of the interaction, so noclip, spectator or client tricks do
 * not open anything the team has not unlocked. Pure decisions live in {@link ProtectionRules}.
 *
 * <p>Ruins join with one provider, for example:
 * {@code StructureProtection.registerProvider("entrelumen:ruins", server -> RuinData.get(server)
 * .ruins().stream().map(r -> StructureProtection.ruin(r.id(), r.dimension().location(), r.box(),
 * r.act())).toList())}, and a call to {@link #invalidate} after placing a ruin.
 */
public final class StructureProtection {
  /** Blocks anyone may use inside a protected structure (doors, buttons, workstations...). */
  public static final TagKey<net.minecraft.world.level.block.Block> USABLE =
      BlockTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "protection/usable"));
  /** Blocks unlocked by the structure's act gate, besides containers, lecterns and altars. */
  public static final TagKey<net.minecraft.world.level.block.Block> GATED =
      BlockTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "protection/gated"));
  /** Blocks nobody may use even if they open a menu (anvils wear themselves down). */
  public static final TagKey<net.minecraft.world.level.block.Block> LOCKED =
      BlockTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "protection/locked"));
  /** Regions larger than this many chunks are checked directly instead of chunk-indexed. */
  private static final int MAX_INDEXED_CHUNKS = 4096;
  private static final int MESSAGE_INTERVAL = 20;

  private static final Map<String, Function<MinecraftServer, Collection<ProtectionRules.Region>>>
      PROVIDERS = new LinkedHashMap<>();
  private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

  private StructureProtection() {}

  private static volatile MinecraftServer lastServer;
  private static volatile State lastState;

  private static final class State {
    final Map<String, ProtectionRules.Region> transients = new LinkedHashMap<>();
    final Map<String, DimensionIndex> dimensions = new HashMap<>();
    /** Dimension keys resolved to their index; {@link #EMPTY} when the level has no region. */
    final Map<net.minecraft.resources.ResourceKey<Level>, DimensionIndex> resolved = new java.util.IdentityHashMap<>();
    final Set<UUID> bypass = new HashSet<>();
    final Map<UUID, Long> lastMessage = new HashMap<>();
    boolean dirty = true;
  }

  private static final DimensionIndex EMPTY = new DimensionIndex();

  private static final class DimensionIndex {
    final Long2ObjectOpenHashMap<List<ProtectionRules.Region>> byChunk = new Long2ObjectOpenHashMap<>();
    final List<ProtectionRules.Region> wide = new ArrayList<>();

    void add(ProtectionRules.Region region) {
      var box = region.box();
      long chunks = (long) ((box.maxX() >> 4) - (box.minX() >> 4) + 1) * ((box.maxZ() >> 4) - (box.minZ() >> 4) + 1);
      if (chunks > MAX_INDEXED_CHUNKS) {
        wide.add(region);
        return;
      }
      for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
          byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), ignored -> new ArrayList<>()).add(region);
    }

    List<ProtectionRules.Region> covering(int x, int y, int z) {
      List<ProtectionRules.Region> chunk = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
      if (chunk == null && wide.isEmpty()) return List.of();
      List<ProtectionRules.Region> found = new ArrayList<>(2);
      if (chunk != null) for (var region : chunk) if (region.box().contains(x, y, z)) found.add(region);
      for (var region : wide) if (region.box().contains(x, y, z)) found.add(region);
      return found;
    }
  }

  // ---- Registration -----------------------------------------------------------------------

  /** Adds or replaces a region source; it is read whenever the index is rebuilt. */
  public static synchronized void registerProvider(String id,
      Function<MinecraftServer, Collection<ProtectionRules.Region>> provider) {
    PROVIDERS.put(id, provider);
    STATES.values().forEach(state -> state.dirty = true);
  }

  /** Rebuild the index on the next check, after a provider's data changed. */
  public static void invalidate(MinecraftServer server) {
    state(server).dirty = true;
  }

  /** A region that lives until removed or until the server stops (tests, temporary sites). */
  public static void addTransient(MinecraftServer server, ProtectionRules.Region region) {
    State state = state(server);
    state.transients.put(region.id(), region);
    state.dirty = true;
  }

  public static void removeTransient(MinecraftServer server, String id) {
    State state = state(server);
    if (state.transients.remove(id) != null) state.dirty = true;
  }

  /** Ruin regions from the {@code RuinData} fields: gated at the ruin's act, without holes. */
  public static ProtectionRules.Region ruin(ResourceLocation id, ResourceLocation dimension,
      net.minecraft.world.level.levelgen.structure.BoundingBox box, int act) {
    return new ProtectionRules.Region(id.toString(), dimension.toString(), box(box),
        ProtectionRules.ActGate.act(act), List.of());
  }

  public static ProtectionRules.Box box(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
    return new ProtectionRules.Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
  }

  /** Toggles the operator bypass for one player; returns the new state. */
  public static boolean toggleBypass(ServerPlayer player) {
    Set<UUID> bypass = state(player.server).bypass;
    if (!bypass.remove(player.getUUID())) {
      bypass.add(player.getUUID());
      return true;
    }
    return false;
  }

  private static State state(MinecraftServer server) {
    State cached = lastState;
    if (cached != null && lastServer == server) return cached;
    synchronized (StructureProtection.class) {
      State state = STATES.computeIfAbsent(server, ignored -> new State());
      lastServer = server;
      lastState = state;
      return state;
    }
  }

  /** The level's index, or null when no region lies in it (the common, allocation-free case). */
  private static DimensionIndex index(ServerLevel level) {
    State state = state(level.getServer());
    if (state.dirty) rebuild(level.getServer(), state);
    DimensionIndex index = state.resolved.get(level.dimension());
    if (index == null) {
      index = state.dimensions.getOrDefault(level.dimension().location().toString(), EMPTY);
      state.resolved.put(level.dimension(), index);
    }
    return index == EMPTY ? null : index;
  }

  private static void rebuild(MinecraftServer server, State state) {
    state.dirty = false;
    state.dimensions.clear();
    state.resolved.clear();
    List<ProtectionRules.Region> all = new ArrayList<>(state.transients.values());
    List<Function<MinecraftServer, Collection<ProtectionRules.Region>>> providers;
    synchronized (StructureProtection.class) {
      providers = List.copyOf(PROVIDERS.values());
    }
    for (var provider : providers) all.addAll(provider.apply(server));
    for (var region : all)
      state.dimensions.computeIfAbsent(region.dimension(), ignored -> new DimensionIndex()).add(region);
  }

  // ---- Queries ----------------------------------------------------------------------------

  public static List<ProtectionRules.Region> covering(ServerLevel level, int x, int y, int z) {
    DimensionIndex index = index(level);
    return index == null ? List.of() : index.covering(x, y, z);
  }

  /** Whether some region guards the block (inside a box and outside every active hole). */
  public static boolean isGuarded(ServerLevel level, BlockPos pos) {
    for (var region : covering(level, pos.getX(), pos.getY(), pos.getZ()))
      if (region.guards(pos.getX(), pos.getY(), pos.getZ())) return true;
    return false;
  }

  public static ProtectionRules.Verdict check(ServerLevel level, BlockPos pos,
      ProtectionRules.Action action, ProtectionRules.Actor actor) {
    var regions = covering(level, pos.getX(), pos.getY(), pos.getZ());
    if (regions.isEmpty()) return ProtectionRules.Verdict.ALLOW;
    return ProtectionRules.decide(regions, pos.getX(), pos.getY(), pos.getZ(), action, actor);
  }

  /** The acting team, read without creating campaigns; players without a team open nothing. */
  public static ProtectionRules.Actor actor(Player player) {
    if (!(player instanceof ServerPlayer serverPlayer)) return ProtectionRules.Actor.ENVIRONMENT;
    boolean automaton = player instanceof FakePlayer;
    boolean bypass = !automaton && serverPlayer.hasPermissions(2)
        && state(serverPlayer.server).bypass.contains(player.getUUID());
    UUID campaignId = null;
    int act = 0;
    Set<String> milestones = Set.of();
    if (!automaton && FTBTeamsAPI.api().isManagerLoaded()) {
      var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(serverPlayer);
      if (team.isPresent()) {
        campaignId = team.get().isPartyTeam() ? team.get().getId() : player.getUUID();
        var campaigns = CampaignData.get(serverPlayer.server).campaigns;
        var campaign = team.get().isPartyTeam() ? campaigns.parties.get(campaignId)
            : campaigns.personal.get(campaignId);
        if (campaign != null && !campaign.archived) {
          act = campaign.act;
          milestones = campaign.completed;
        } else {
          act = 1;
        }
      }
    }
    return new ProtectionRules.Actor(campaignId, act, milestones, bypass, automaton);
  }

  /** How right-clicking this block is judged inside a protected structure. */
  public static ProtectionRules.Action classify(Level level, BlockPos pos, BlockState state) {
    if (state.is(LOCKED)) return ProtectionRules.Action.USE_LOCKED_BLOCK;
    if (state.is(USABLE)) return ProtectionRules.Action.USE_FREE_BLOCK;
    if (state.is(GATED) || state.getBlock() instanceof LecternBlock || state.getBlock() instanceof AltarBlock)
      return ProtectionRules.Action.USE_GATED_BLOCK;
    BlockEntity entity = level.getBlockEntity(pos);
    if (entity instanceof Container
        || (entity != null && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, entity, null) != null))
      return ProtectionRules.Action.USE_GATED_BLOCK;
    if (entity == null && state.getMenuProvider(level, pos) != null) return ProtectionRules.Action.USE_FREE_BLOCK;
    return ProtectionRules.Action.USE_LOCKED_BLOCK;
  }

  private static boolean isDecoration(Entity entity) {
    return entity instanceof BlockAttachedEntity || entity instanceof ArmorStand;
  }

  /** Environment checks for the fire and fluid mixins; cheap when the level has no region. */
  public static boolean fireMaySpread(Level level, BlockPos pos) {
    return !(level instanceof ServerLevel server) || !isGuarded(server, pos);
  }

  public static boolean fluidMayFlow(Object level, BlockPos from, BlockPos to) {
    if (!(level instanceof ServerLevel server)) return true;
    var regions = covering(server, to.getX(), to.getY(), to.getZ());
    return regions.isEmpty() || ProtectionRules.fluidMayFlow(regions, from.getX(), from.getY(), from.getZ(),
        to.getX(), to.getY(), to.getZ());
  }

  private static void tell(Player player, ProtectionRules.Verdict verdict) {
    if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
    State state = state(serverPlayer.server);
    long now = serverPlayer.serverLevel().getGameTime();
    Long last = state.lastMessage.get(player.getUUID());
    if (last != null && now - last < MESSAGE_INTERVAL && now >= last) return;
    state.lastMessage.put(player.getUUID(), now);
    String key = switch (verdict) {
      case DENY_LOCKED -> "entrelumen.protection.locked";
      case DENY_NOT_OWNER -> "entrelumen.protection.not_owner";
      default -> "entrelumen.protection.protected";
    };
    serverPlayer.displayClientMessage(Component.translatable(key), true);
  }

  // ---- Events -----------------------------------------------------------------------------

  static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(StructureProtection::onBreak);
    bus.addListener(StructureProtection::onPlace);
    bus.addListener(StructureProtection::onLeftClick);
    bus.addListener(StructureProtection::onRightClickBlock);
    bus.addListener(StructureProtection::onRightClickItem);
    bus.addListener(StructureProtection::onToolModification);
    bus.addListener(StructureProtection::onExplosion);
    bus.addListener(StructureProtection::onPiston);
    bus.addListener(StructureProtection::onFluidPlace);
    bus.addListener(StructureProtection::onTrample);
    bus.addListener(StructureProtection::onGrief);
    bus.addListener(StructureProtection::onLivingDestroy);
    bus.addListener(StructureProtection::onInvulnerability);
    bus.addListener(StructureProtection::onEntityInteract);
    bus.addListener(StructureProtection::onEntityInteractSpecific);
    bus.addListener((ServerStoppedEvent event) -> {
      synchronized (StructureProtection.class) {
        STATES.remove(event.getServer());
        lastServer = null;
        lastState = null;
      }
    });
  }

  private static ServerLevel serverLevel(Object level) {
    return level instanceof ServerLevel server ? server : null;
  }

  static void onBreak(BlockEvent.BreakEvent event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null) return;
    var verdict = check(level, event.getPos(), ProtectionRules.Action.BREAK, actor(event.getPlayer()));
    if (!verdict.allowed()) {
      event.setCanceled(true);
      tell(event.getPlayer(), verdict);
    }
  }

  static void onPlace(BlockEvent.EntityPlaceEvent event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null) return;
    Entity entity = event.getEntity();
    var actor = entity instanceof Player player ? actor(player) : ProtectionRules.Actor.ENVIRONMENT;
    var action = entity instanceof Player ? ProtectionRules.Action.PLACE : ProtectionRules.Action.GRIEF;
    List<BlockPos> positions = new ArrayList<>();
    if (event instanceof BlockEvent.EntityMultiPlaceEvent multi)
      multi.getReplacedBlockSnapshots().forEach(snapshot -> positions.add(snapshot.getPos()));
    else positions.add(event.getPos());
    for (BlockPos pos : positions) {
      var verdict = check(level, pos, action, actor);
      if (!verdict.allowed()) {
        event.setCanceled(true);
        if (entity instanceof Player player) tell(player, verdict);
        return;
      }
    }
  }

  static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
    var verdict = check(level, event.getPos(), ProtectionRules.Action.BREAK, actor(event.getEntity()));
    if (!verdict.allowed()) {
      event.setCanceled(true);
      tell(event.getEntity(), verdict);
    }
  }

  static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null) return;
    BlockPos pos = event.getPos();
    var regions = covering(level, pos.getX(), pos.getY(), pos.getZ());
    if (regions.isEmpty()) return;
    var actor = actor(event.getEntity());
    BlockState state = level.getBlockState(pos);
    var use = ProtectionRules.decide(regions, pos.getX(), pos.getY(), pos.getZ(),
        classify(level, pos, state), actor);
    // Placement is judged where the block lands (EntityPlaceEvent); other items act on this block.
    boolean placing = event.getItemStack().getItem() instanceof BlockItem;
    var item = placing ? ProtectionRules.Verdict.ALLOW
        : ProtectionRules.decide(regions, pos.getX(), pos.getY(), pos.getZ(), ProtectionRules.Action.USE_ITEM, actor);
    if (!use.allowed()) event.setUseBlock(TriState.FALSE);
    if (!item.allowed()) event.setUseItem(TriState.FALSE);
    if ((!use.allowed() && (!item.allowed() || event.getItemStack().isEmpty()))
        || (!use.allowed() && event.getEntity().isSpectator())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.FAIL);
    }
    if (!use.allowed()) tell(event.getEntity(), use);
    else if (!item.allowed() && !event.getItemStack().isEmpty()) tell(event.getEntity(), item);
  }

  /** Buckets aim by themselves (no block click); refuse when they would touch a guarded block. */
  static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null || !(event.getItemStack().getItem() instanceof BucketItem)) return;
    Player player = event.getEntity();
    var eye = player.getEyePosition();
    var reach = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
    List<BlockPos> targets = new ArrayList<>();
    // Empty buckets aim at source fluids, filled ones at solid blocks: judge both aims.
    for (var fluid : List.of(ClipContext.Fluid.SOURCE_ONLY, ClipContext.Fluid.NONE)) {
      HitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, fluid, player));
      if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
        targets.add(blockHit.getBlockPos());
        targets.add(blockHit.getBlockPos().relative(blockHit.getDirection()));
      }
    }
    var actor = actor(player);
    for (BlockPos pos : targets) {
      var verdict = check(level, pos, ProtectionRules.Action.PLACE, actor);
      if (!verdict.allowed()) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        tell(player, verdict);
        return;
      }
    }
  }

  static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null || event.isSimulated()) return;
    var actor = event.getPlayer() == null ? ProtectionRules.Actor.ENVIRONMENT : actor(event.getPlayer());
    if (!check(level, event.getPos(), ProtectionRules.Action.USE_ITEM, actor).allowed()) event.setCanceled(true);
  }

  static void onExplosion(ExplosionEvent.Detonate event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null) return;
    event.getAffectedBlocks().removeIf(pos -> isGuarded(level, pos));
    event.getAffectedEntities().removeIf(entity -> isDecoration(entity) && isGuarded(level, entity.blockPosition()));
  }

  static void onPiston(PistonEvent.Pre event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null || index(level) == null) return;
    PistonStructureResolver resolver = event.getStructureHelper();
    if (resolver == null) return;
    if (!resolver.resolve()) return;
    List<BlockPos> touched = new ArrayList<>();
    if (event.getPistonMoveType().isExtend) touched.add(event.getFaceOffsetPos());
    Direction push = resolver.getPushDirection();
    for (BlockPos moved : resolver.getToPush()) {
      touched.add(moved);
      touched.add(moved.relative(push));
    }
    touched.addAll(resolver.getToDestroy());
    for (BlockPos p : touched)
      if (!check(level, p, ProtectionRules.Action.PISTON, ProtectionRules.Actor.ENVIRONMENT).allowed()) {
        event.setCanceled(true);
        return;
      }
  }

  static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level == null) return;
    if (!fluidMayFlow(level, event.getLiquidPos(), event.getPos())) {
      event.setNewState(event.getOriginalState());
      event.setCanceled(true);
    }
  }

  static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
    ServerLevel level = serverLevel(event.getLevel());
    if (level != null && isGuarded(level, event.getPos())) event.setCanceled(true);
  }

  static void onGrief(EntityMobGriefingEvent event) {
    Entity entity = event.getEntity();
    if (entity.level() instanceof ServerLevel level && !(entity instanceof Player)
        && isGuarded(level, entity.blockPosition())) event.setCanGrief(false);
  }

  static void onLivingDestroy(LivingDestroyBlockEvent event) {
    if (event.getEntity().level() instanceof ServerLevel level && isGuarded(level, event.getPos()))
      event.setCanceled(true);
  }

  /**
   * Frames, paintings and armor stands inside a guarded block ignore every damage source, except
   * that an unlocked team may take the item shown in a frame (the frame itself stays).
   */
  static void onInvulnerability(EntityInvulnerabilityCheckEvent event) {
    Entity entity = event.getEntity();
    if (event.isInvulnerable() || !isDecoration(entity) || !(entity.level() instanceof ServerLevel level)) return;
    BlockPos pos = entity.blockPosition();
    var regions = covering(level, pos.getX(), pos.getY(), pos.getZ());
    if (regions.isEmpty()) return;
    Entity source = event.getSource().getEntity();
    var actor = source instanceof Player player ? actor(player) : ProtectionRules.Actor.ENVIRONMENT;
    var action = source instanceof Player && entity instanceof ItemFrame frame && !frame.getItem().isEmpty()
        && !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
        ? ProtectionRules.Action.TAKE_DISPLAYED : ProtectionRules.Action.ENTITY_BREAK;
    var verdict = ProtectionRules.decide(regions, pos.getX(), pos.getY(), pos.getZ(), action, actor);
    if (!verdict.allowed()) {
      event.setInvulnerable(true);
      if (source instanceof Player player) tell(player, verdict);
    }
  }

  static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
    if (denyEntityUse(event.getEntity(), event.getTarget())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.FAIL);
    }
  }

  static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
    if (denyEntityUse(event.getEntity(), event.getTarget())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.FAIL);
    }
  }

  private static boolean denyEntityUse(Player player, Entity target) {
    if (!isDecoration(target) || !(target.level() instanceof ServerLevel level)) return false;
    var verdict = check(level, target.blockPosition(), ProtectionRules.Action.ENTITY_USE, actor(player));
    if (verdict.allowed()) return false;
    tell(player, verdict);
    return true;
  }
}
