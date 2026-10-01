package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The Sunken Workshop's engine, "El Motor de Terra" (docs/design/heliodor-ruins.md), for every
 * placed ruin whose markers carry one:
 *
 * <ul>
 *   <li>sluices open while one of their levers is on, so water reaches the wheel;
 *   <li>the engine room is a sandbox: only transmission parts and redstone go in (see
 *       {@link #check}), and the wheelhouse sockets take only their missing piece;
 *   <li>while every pump drinks (its own way, at R or faster, all on one network) the pit drains a
 *       slice at a time; the dry pit solves the pumps' challenge for the team that last worked the
 *       engine;
 *   <li>a seal bearing held at its angle and at rest opens the vault for that team;
 *   <li>the ruin's own Create blocks come back when a kinetic conflict breaks them, and Terra's
 *       notes come back to their lecterns;
 *   <li>once a team claims the piece, the workshop resets for the next team after it has been empty
 *       for ten minutes: the pit floods again, the ring returns, the parts go back to their placers
 *       or to the barrel at the door.
 * </ul>
 *
 * Create is read through {@link CreateCompat}; without it the ruin plays its lever puzzle and only
 * the sluices move.
 */
public final class RuinWorkshop {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final int INTERVAL = 10;
  static final long RESET_DELAY = 12_000;
  /** A sandbox or lever worker is credited for this long. */
  static final int CREDIT_TICKS = 12_000;
  static final int FEEDBACK_INTERVAL = 40, NOTE_INTERVAL = 100;
  static final double FEEDBACK_RADIUS = 5.5;
  public static final TagKey<Block> SANDBOX_BLOCKS =
      BlockTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "ruin/sandbox"));
  public static final TagKey<Item> SANDBOX_TOOLS =
      ItemTags.create(ResourceLocation.fromNamespaceAndPath("entrelumen", "ruin/sandbox_tools"));

  /** A sandbox: its box from {@code origin} cut to a disc of {@code radius} around the box centre. */
  record Sandbox(BlockPos origin, int[] size, double radius) {
    boolean contains(BlockPos pos) {
      return RuinRules.inSandbox(pos.getX(), pos.getY(), pos.getZ(), origin.getX(), origin.getY(), origin.getZ(), size,
          radius);
    }

    List<BlockPos> cells() {
      List<BlockPos> cells = new ArrayList<>();
      for (int y = 0; y < size[1]; y++)
        for (int z = 0; z < size[2]; z++)
          for (int x = 0; x < size[0]; x++) {
            BlockPos pos = origin.offset(x, y, z);
            if (contains(pos)) cells.add(pos);
          }
      return cells;
    }

    AABB area() {
      return new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + size[0], origin.getY() + size[1],
          origin.getZ() + size[2]);
    }
  }

  /** A ruin block of Create to put back when a conflict breaks it. */
  record Heal(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {}

  /** A placed ruin with an engine, rebuilt from its markers when the ruin registry changes. */
  static final class Site {
    final RuinData.Ruin ruin;
    final List<Sandbox> sandboxes = new ArrayList<>();
    final Map<BlockPos, RuinData.PlacedMarker> parts = new LinkedHashMap<>();
    final List<RuinData.PlacedMarker> pumps, sluices, levers, drains, notes, wheels, ports;
    @Nullable final RuinData.PlacedMarker seal, returns;
    @Nullable List<Heal> heals;
    int sealRest;
    long lastFeedback;
    boolean wasPumping;

    Site(RuinData.Ruin ruin) {
      this.ruin = ruin;
      for (var marker : ruin.markers(RuinMarkers.Kind.SANDBOX))
        sandboxes.add(new Sandbox(marker.pos(), marker.marker().size(), marker.marker().radius()));
      for (var marker : ruin.markers(RuinMarkers.Kind.PART)) parts.put(marker.pos(), marker);
      pumps = ruin.markers(RuinMarkers.Kind.PUMP);
      sluices = ruin.markers(RuinMarkers.Kind.SLUICE);
      levers = ruin.markers(RuinMarkers.Kind.LEVER).stream()
          .filter(marker -> marker.marker().params().containsKey("sluice")).toList();
      drains = ruin.markers(RuinMarkers.Kind.DRAIN);
      notes = ruin.markers(RuinMarkers.Kind.NOTE);
      wheels = ruin.markers(RuinMarkers.Kind.WHEEL);
      ports = ruin.markers(RuinMarkers.Kind.PORT);
      seal = ruin.markers(RuinMarkers.Kind.SEAL).stream().findFirst().orElse(null);
      returns = ruin.markers(RuinMarkers.Kind.RETURNS).stream().findFirst().orElse(null);
    }

    boolean engine() {
      return !sandboxes.isEmpty() || !pumps.isEmpty() || seal != null || !sluices.isEmpty() || !parts.isEmpty();
    }

    @Nullable
    Sandbox sandbox(BlockPos pos) {
      for (var sandbox : sandboxes) if (sandbox.contains(pos)) return sandbox;
      return null;
    }
  }

  private static final class State {
    int revision = -1;
    final Map<ResourceLocation, Site> sites = new LinkedHashMap<>();
  }

  private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

  private RuinWorkshop() {}

  private static State state(MinecraftServer server) {
    synchronized (STATES) {
      return STATES.computeIfAbsent(server, ignored -> new State());
    }
  }

  static void forget(MinecraftServer server) {
    synchronized (STATES) {
      STATES.remove(server);
    }
  }

  /** The engine sites of the placed ruins, rebuilt when the registry changed. */
  static Collection<Site> sites(MinecraftServer server) {
    State state = state(server);
    var data = RuinData.get(server);
    if (state.revision != data.revision()) {
      Map<ResourceLocation, Site> old = new HashMap<>(state.sites);
      state.sites.clear();
      for (var ruin : data.ruins()) {
        var previous = old.get(ruin.id());
        var site = previous != null && previous.ruin.equals(ruin) ? previous : new Site(ruin);
        if (site.engine()) state.sites.put(ruin.id(), site);
      }
      state.revision = data.revision();
    }
    return state.sites.values();
  }

  @Nullable
  static Site site(MinecraftServer server, ResourceLocation ruin) {
    for (var site : sites(server)) if (site.ruin.id().equals(ruin)) return site;
    return null;
  }

  // ---- Ticking ---------------------------------------------------------------------------------

  static void tick(MinecraftServer server) {
    if (server.getTickCount() % INTERVAL != 0) return;
    var sites = sites(server);
    if (sites.isEmpty()) return;
    var data = RuinWorkshopData.get(server);
    for (Site site : List.copyOf(sites)) {
      ServerLevel level = server.getLevel(site.ruin.dimension());
      if (level == null) continue;
      try {
        bookkeeping(level, site, data);
        if (!level.isLoaded(site.ruin.box().getCenter())) continue;
        var definition = RuinRegistry.get(site.ruin.id().toString()).orElse(null);
        sluices(level, site);
        if (server.getTickCount() % NOTE_INTERVAL == 0) notes(level, site, false);
        if (definition == null || !CreateCompat.loaded()) continue;
        heal(level, site);
        pumps(level, site, definition);
        seal(level, site, definition);
      } catch (RuntimeException e) {
        LOGGER.error("Ruin engine {} failed a tick", site.ruin.id(), e);
      }
    }
  }

  /** Whether a player (not a spectator) is inside the ruin. */
  static boolean occupied(ServerLevel level, RuinData.Ruin ruin) {
    for (ServerPlayer player : level.players())
      if (!player.isSpectator() && ruin.box().isInside(player.blockPosition())) return true;
    return false;
  }

  private static void bookkeeping(ServerLevel level, Site site, RuinWorkshopData data) {
    var entry = data.peek(site.ruin.id()).orElse(null);
    if (entry == null || !entry.pending) return;
    long now = level.getGameTime();
    if (occupied(level, site.ruin)) {
      if (entry.emptySince != -1) {
        entry.emptySince = -1;
        data.setDirty();
      }
      return;
    }
    if (entry.emptySince < 0) {
      entry.emptySince = now;
      data.setDirty();
    }
    if (RuinRules.resetDue(entry.emptySince, now, RESET_DELAY) && level.isLoaded(site.ruin.box().getCenter())) reset(level, site);
  }

  // ---- Sluices -----------------------------------------------------------------------------------

  /** Opens each sluice while one of its levers is on, and closes it otherwise. */
  static void sluices(ServerLevel level, Site site) {
    if (site.sluices.isEmpty()) return;
    Set<String> open = new HashSet<>();
    for (var lever : site.levers) {
      if (!level.isLoaded(lever.pos())) continue;
      BlockState state = level.getBlockState(lever.pos());
      if (state.getBlock() instanceof LeverBlock && state.getValue(BlockStateProperties.POWERED))
        open.add(lever.marker().param("sluice", ""));
    }
    for (var sluice : site.sluices) {
      if (!level.isLoaded(sluice.pos())) continue;
      boolean shouldOpen = open.contains(sluice.marker().param("id", ""));
      BlockState state = level.getBlockState(sluice.pos());
      BlockState closed = RuinPlacement.blockOf(sluice.marker());
      if (closed == null) continue;
      if (shouldOpen && state.is(closed.getBlock())) {
        level.setBlock(sluice.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, sluice.pos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
      } else if (!shouldOpen && !state.is(closed.getBlock())
          && (state.isAir() || state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER))) {
        level.setBlock(sluice.pos(), closed, Block.UPDATE_ALL);
        level.playSound(null, sluice.pos(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
      }
    }
  }

  // ---- The pumps ---------------------------------------------------------------------------------

  /** R for this site: the challenge's own, else from the live Create values of its wheels and pumps. */
  static int pumpSpeed(Site site, @Nullable RuinDefinitions.Challenge challenge) {
    if (challenge != null && challenge.engine().rpm() > 0) return challenge.engine().rpm();
    if (site.wheels.isEmpty() || site.pumps.isEmpty()) return 0;
    BlockState wheel = RuinPlacement.blockOf(site.wheels.getFirst().marker());
    BlockState pump = RuinPlacement.blockOf(site.pumps.getFirst().marker());
    if (wheel == null || pump == null) return 0;
    return CreateCompat.pumpSpeed(wheel.getBlock(), pump.getBlock(), site.wheels.size(), site.pumps.size());
  }

  /** What each pump does now, in marker order. */
  static RuinRules.Pump[] pumpStates(ServerLevel level, Site site, int rpm) {
    RuinRules.Pump[] states = new RuinRules.Pump[site.pumps.size()];
    for (int i = 0; i < states.length; i++) {
      var pump = site.pumps.get(i);
      states[i] = RuinRules.pump(CreateCompat.speed(level, pump.pos()), pump.marker().turn(), rpm);
    }
    return states;
  }

  /** Whether the site's pumps drain the pit right now. */
  static boolean pumping(ServerLevel level, Site site, int rpm) {
    int n = site.pumps.size();
    float[] speeds = new float[n];
    int[] turns = new int[n];
    Long[] networks = new Long[n];
    for (int i = 0; i < n; i++) {
      var pump = site.pumps.get(i);
      if (!level.isLoaded(pump.pos())) return false;
      speeds[i] = CreateCompat.speed(level, pump.pos());
      turns[i] = pump.marker().turn();
      networks[i] = CreateCompat.network(level, pump.pos());
    }
    return RuinRules.pumping(speeds, turns, networks, rpm);
  }

  static void pumps(ServerLevel level, Site site, RuinDefinitions.Definition definition) {
    if (site.pumps.isEmpty()) return;
    String id = site.pumps.getFirst().marker().challenge();
    var challenge = definition.challenges().get(id);
    if (challenge == null || challenge.type() != RuinDefinitions.ChallengeType.PUMPS) return;
    int rpm = pumpSpeed(site, challenge);
    if (rpm <= 0) return;
    boolean running = pumping(level, site, rpm);
    feedback(level, site, rpm, running);
    if (running != site.wasPumping) {
      level.playSound(null, site.ruin.box().getCenter(), running ? SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT
          : SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 1.5f, running ? 0.7f : 1.2f);
      site.wasPumping = running;
    }
    if (!running) return;
    boolean dry = true;
    for (var drain : site.drains) dry &= drainStep(level, drain);
    if (!dry) return;
    var credit = credited(level, site);
    if (credit != null) RuinChallenges.solve(level, site.ruin, definition, id, credit.campaign(), credit.founder());
  }

  /**
   * One step of draining: the water sources (and the ice a cold biome froze them into) of the drain
   * box's highest wet layer, a slice of them in turn around its centre. True when the box holds no
   * source any more. A reset restores the water from the template.
   */
  static boolean drainStep(ServerLevel level, RuinData.PlacedMarker drain) {
    int[] size = drain.marker().size();
    BlockPos o = drain.pos();
    double cx = o.getX() + (size[0] - 1) / 2.0, cz = o.getZ() + (size[2] - 1) / 2.0;
    var cursor = new BlockPos.MutableBlockPos();
    for (int y = size[1] - 1; y >= 0; y--) {
      List<BlockPos> sources = new ArrayList<>();
      for (int x = 0; x < size[0]; x++)
        for (int z = 0; z < size[2]; z++) {
          cursor.set(o.getX() + x, o.getY() + y, o.getZ() + z);
          if (!level.isLoaded(cursor)) continue;
          var fluid = level.getFluidState(cursor);
          // A cold biome freezes the open pit: its ice is water too, and goes with it.
          if (fluid.is(Fluids.WATER) && fluid.isSource() || frozen(level.getBlockState(cursor)))
            sources.add(cursor.immutable());
        }
      if (sources.isEmpty()) continue;
      sources.sort(Comparator.comparingDouble(p -> Math.atan2(p.getZ() - cz, p.getX() - cx)));
      int batch = Math.max(24, (sources.size() + 5) / 6);
      for (int i = 0; i < Math.min(batch, sources.size()); i++) {
        BlockPos pos = sources.get(i);
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED))
          level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
        else level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (i % 6 == 0)
          level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0);
      }
      level.playSound(null, sources.getFirst(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 0.6f);
      return false;
    }
    return true;
  }

  /** Ice the pit's water froze into, which draining removes like the water itself. */
  static boolean frozen(BlockState state) {
    return state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE);
  }

  /** Particles at the ports and intakes, and a word to the builders about what each pump does. */
  private static void feedback(ServerLevel level, Site site, int rpm, boolean running) {
    long now = level.getGameTime();
    boolean speak = now - site.lastFeedback >= FEEDBACK_INTERVAL;
    if (speak) site.lastFeedback = now;
    var states = pumpStates(level, site, rpm);
    boolean allDrinking = Arrays.stream(states).allMatch(s -> s == RuinRules.Pump.DRINKING);
    for (int i = 0; i < states.length; i++) {
      var pump = site.pumps.get(i);
      BlockPos port = nearestPort(site, pump.pos());
      int[] offset = pump.marker().triple("intake", "0,0,0");
      BlockPos intake = pump.pos().offset(offset[0], offset[1], offset[2]);
      switch (states[i]) {
        case DRINKING -> level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, intake.getX() + 0.5, intake.getY() + 0.5,
            intake.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
        case WRONG -> {
          level.sendParticles(ParticleTypes.BUBBLE_POP, intake.getX() + 0.5, intake.getY() + 0.5, intake.getZ() + 0.5, 6,
              0.4, 0.4, 0.4, 0.05);
          if (port != null) level.sendParticles(ParticleTypes.SMOKE, port.getX() + 0.5, port.getY() + 1.1,
              port.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.01);
        }
        default -> {}
      }
      if (!speak || port == null) continue;
      String key = switch (states[i]) {
        case WRONG -> "entrelumen.ruin.engine.wrong";
        case SLOW -> "entrelumen.ruin.engine.slow";
        case STILL -> CreateCompat.overStressed(level, pump.pos()) ? "entrelumen.ruin.engine.overstressed" : null;
        case DRINKING -> allDrinking && !running ? "entrelumen.ruin.engine.apart" : null;
      };
      if (key != null) tellNear(level, Vec3.atCenterOf(port), Component.translatable(key, rpm));
    }
  }

  @Nullable
  private static BlockPos nearestPort(Site site, BlockPos pump) {
    BlockPos best = null;
    double distance = Double.MAX_VALUE;
    for (var port : site.ports) {
      if (!"pump".equals(port.marker().param("role", ""))) continue;
      double d = Math.pow(port.pos().getX() - pump.getX(), 2) + Math.pow(port.pos().getZ() - pump.getZ(), 2);
      if (d < distance) {
        distance = d;
        best = port.pos();
      }
    }
    return best;
  }

  private static void tellNear(ServerLevel level, Vec3 at, Component message) {
    for (ServerPlayer player : level.players())
      if (!player.isSpectator() && player.position().distanceToSqr(at) <= FEEDBACK_RADIUS * FEEDBACK_RADIUS)
        player.displayClientMessage(message, true);
  }

  // ---- The seal ----------------------------------------------------------------------------------

  static void seal(ServerLevel level, Site site, RuinDefinitions.Definition definition) {
    if (site.seal == null || !level.isLoaded(site.seal.pos())) return;
    String id = site.seal.marker().challenge();
    var challenge = definition.challenges().get(id);
    if (challenge == null || challenge.type() != RuinDefinitions.ChallengeType.SEAL) return;
    var bearing = CreateCompat.bearing(level, site.seal.pos());
    boolean held = bearing != null && bearing.running() && bearing.angularSpeed() == 0
        && CreateCompat.speed(level, site.seal.pos()) == 0
        && RuinRules.aligned(bearing.angle(), challenge.engine().angle(), challenge.engine().tolerance());
    if (!held) {
      site.sealRest = 0;
      return;
    }
    site.sealRest += INTERVAL;
    if (site.sealRest < challenge.engine().rest()) return;
    var credit = credited(level, site);
    if (credit == null) return;
    var team = RuinProgress.get(level.getServer()).team(credit.campaign(), credit.founder());
    if (!RuinRules.ready(RuinChallenges.keys(definition, challenge.requires()), team.solved)) {
      if (site.sealRest - INTERVAL < challenge.engine().rest())
        for (ServerPlayer member : RuinChallenges.members(level.getServer(), credit.campaign()))
          member.displayClientMessage(Component.translatable("entrelumen.ruin.not_ready"), true);
      return;
    }
    if (RuinChallenges.solve(level, site.ruin, definition, id, credit.campaign(), credit.founder()))
      level.playSound(null, site.seal.pos(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.5f);
  }

  // ---- Credit ------------------------------------------------------------------------------------

  /** The team that last worked the engine (sandbox, sockets, levers), else the nearest team inside. */
  @Nullable
  static RuinChallenges.Credit credited(ServerLevel level, Site site) {
    var credit = RuinChallenges.lastCredit(level.getServer(), site.ruin.id(), CREDIT_TICKS);
    if (credit != null) return credit;
    ServerPlayer nearest = null;
    Vec3 center = Vec3.atCenterOf(site.ruin.box().getCenter());
    for (ServerPlayer player : level.players())
      if (!player.isSpectator() && site.ruin.box().isInside(player.blockPosition())
          && (nearest == null || player.distanceToSqr(center) < nearest.distanceToSqr(center)))
        nearest = player;
    var context = nearest == null ? null : HeliodorCompass.context(nearest).orElse(null);
    return context == null ? null : new RuinChallenges.Credit(context.campaignId(), context.founder(), level.getGameTime());
  }

  // ---- Protection: the sandbox and the sockets ------------------------------------------------

  /**
   * The engine's exception to a ruin's protection. In a sandbox cell a player may place and break the
   * blocks of {@link #SANDBOX_BLOCKS}, use any block and use {@link #SANDBOX_TOOLS} (the wrench); in a
   * socket only the missing piece goes in and out. Anything else in those cells is refused with its
   * own message; every other cell is left to the regions.
   */
  static StructureProtection.Exemption.Result check(ServerLevel level, BlockPos pos, ProtectionRules.Action action,
      Player player, BlockState state, ItemStack item) {
    var sites = sites(level.getServer());
    if (sites.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) return StructureProtection.Exemption.Result.PASS;
    for (Site site : sites) {
      if (!site.ruin.contains(level.dimension(), pos)) continue;
      var part = site.parts.get(pos);
      var sandbox = part == null ? site.sandbox(pos) : null;
      if (part == null && sandbox == null) return StructureProtection.Exemption.Result.PASS;
      if (serverPlayer instanceof net.neoforged.neoforge.common.util.FakePlayer)
        return StructureProtection.Exemption.Result.deny("entrelumen.ruin.sandbox.hands");
      BlockState expected = part == null ? null : RuinPlacement.blockOf(part.marker());
      boolean allowed = switch (action) {
        case PLACE -> part != null ? expected != null && state.is(expected.getBlock()) : state.is(SANDBOX_BLOCKS);
        case BREAK -> part != null ? expected != null && state.is(expected.getBlock()) : state.is(SANDBOX_BLOCKS);
        case USE_FREE_BLOCK, USE_GATED_BLOCK, USE_LOCKED_BLOCK -> true;
        case USE_ITEM -> item.isEmpty() || item.is(SANDBOX_TOOLS);
        default -> false;
      };
      if (!allowed)
        return StructureProtection.Exemption.Result.deny(part != null ? "entrelumen.ruin.socket.only" : "entrelumen.ruin.sandbox.only");
      HeliodorCompass.context(serverPlayer).ifPresent(context -> RuinChallenges.credit(level, pos, context));
      var data = RuinWorkshopData.get(level.getServer());
      if (action == ProtectionRules.Action.PLACE) {
        data.entry(site.ruin.id()).placers.put(pos.asLong(), player.getUUID());
        data.setDirty();
      } else if (action == ProtectionRules.Action.BREAK && data.peek(site.ruin.id()).map(e -> e.placers.remove(pos.asLong()) != null).orElse(false)) {
        data.setDirty();
      }
      return StructureProtection.Exemption.Result.ALLOW;
    }
    return StructureProtection.Exemption.Result.PASS;
  }

  // ---- Healing and notes -------------------------------------------------------------------------

  /** Puts back the ruin's Create blocks that a kinetic conflict broke, and takes their drops. */
  static void heal(ServerLevel level, Site site) {
    if (site.heals == null) site.heals = heals(level, site);
    for (Heal heal : site.heals) {
      if (!level.isLoaded(heal.pos()) || !level.getBlockState(heal.pos()).isAir()) continue;
      level.setBlock(heal.pos(), heal.state(), Block.UPDATE_ALL);
      if (heal.nbt() != null) {
        var entity = level.getBlockEntity(heal.pos());
        if (entity != null) {
          CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
          tag.merge(heal.nbt());
          entity.loadWithComponents(tag, level.registryAccess());
          entity.setChanged();
        }
      }
      Item dropped = heal.state().getBlock().asItem();
      for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(heal.pos()).inflate(2.5)))
        if (drop.getItem().is(dropped)) drop.discard();
      LOGGER.info("Ruin engine {}: put back {} at {}", site.ruin.id(), BuiltInRegistries.BLOCK.getKey(heal.state().getBlock()),
          heal.pos());
    }
  }

  /** The template's Create blocks (but the seal's chassis, which rides its contraption) and the engine markers. */
  static List<Heal> heals(ServerLevel level, Site site) {
    List<Heal> heals = new ArrayList<>();
    var server = level.getServer();
    var id = site.ruin.template();
    try {
      var resource = server.getResourceManager().getResource(
          ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt"));
      if (resource.isPresent()) {
        CompoundTag tag;
        try (InputStream stream = resource.get().open()) {
          tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
          String name = palette.getCompound(i).getString("Name");
          if (name.startsWith("create:") && !name.equals("create:radial_chassis"))
            states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
        }
        for (Tag element : tag.getList("blocks", Tag.TAG_COMPOUND)) {
          var block = (CompoundTag) element;
          int index = block.getInt("state");
          if (index < 0 || index >= states.length || states[index] == null || states[index].isAir()) continue;
          var p = block.getList("pos", Tag.TAG_INT);
          BlockPos pos = site.ruin.origin().offset(p.getInt(0), p.getInt(1), p.getInt(2));
          if (site.sandbox(pos) == null && !site.parts.containsKey(pos))
            heals.add(new Heal(pos, states[index], block.contains("nbt") ? block.getCompound("nbt") : null));
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Ruin engine {}: cannot read {} to heal it: {}", site.ruin.id(), id, e.toString());
    }
    for (var kind : List.of(RuinMarkers.Kind.WHEEL, RuinMarkers.Kind.PUMP, RuinMarkers.Kind.PORT))
      for (var marker : site.ruin.markers(kind)) {
        BlockState state = RuinPlacement.blockOf(marker.marker());
        if (state != null) heals.add(new Heal(marker.pos(), state, null));
      }
    if (site.seal != null) {
      BlockState state = RuinPlacement.blockOf(site.seal.marker());
      if (state != null) heals.add(new Heal(site.seal.pos(), state, scroll(site.seal.marker())));
    }
    return List.copyOf(heals);
  }

  /** The {@code scroll} a marker gives its block entity, as the NBT Create reads it. */
  @Nullable
  static CompoundTag scroll(RuinMarkers.Marker marker) {
    if (!marker.params().containsKey("scroll")) return null;
    var tag = new CompoundTag();
    tag.putInt("ScrollValue", Integer.parseInt(marker.param("scroll", "0")));
    return tag;
  }

  /** Terra's note for a lectern: EN/ES pages from the lang files, with R where the note names it. */
  static ItemStack note(String key, int rpm) {
    ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
    Object r = rpm > 0 ? rpm : "R";
    MutableComponent page = Component.translatable("entrelumen.ruin.note." + key, r);
    book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Terra"), "Terra", 0,
        List.of(Filterable.passThrough(page)), true));
    book.set(DataComponents.CUSTOM_NAME, Component.translatable("entrelumen.ruin.note.title").withStyle(ChatFormatting.GOLD));
    return book;
  }

  /** Puts a note back on every lectern that lost it ({@code rewrite}: on every lectern). */
  static void notes(ServerLevel level, Site site, boolean rewrite) {
    if (site.notes.isEmpty()) return;
    int rpm = CreateCompat.loaded() ? pumpSpeed(site, null) : 0;
    for (var marker : site.notes) {
      if (!level.isLoaded(marker.pos())) continue;
      BlockState state = level.getBlockState(marker.pos());
      if (!(state.getBlock() instanceof LecternBlock)) continue;
      ItemStack book = note(marker.marker().param("key", "engine"), rpm);
      if (!state.getValue(LecternBlock.HAS_BOOK)) LecternBlock.tryPlaceBook(null, level, marker.pos(), state, book);
      else if (rewrite && level.getBlockEntity(marker.pos()) instanceof LecternBlockEntity lectern) lectern.setBook(book);
    }
  }

  /** A ruin was just placed: its notes learn R. */
  static void placed(ServerLevel level, RuinData.Ruin ruin) {
    var site = site(level.getServer(), ruin.id());
    if (site != null) notes(level, site, true);
  }

  // ---- Claims and the reset ----------------------------------------------------------------------

  /** A team took the ruin's piece: the workshop resets for the next team once it has been empty long enough. */
  static void claimed(ServerLevel level, RuinData.Ruin ruin) {
    var site = site(level.getServer(), ruin.id());
    if (site == null || (site.pumps.isEmpty() && site.seal == null)) return;
    var data = RuinWorkshopData.get(level.getServer());
    var entry = data.entry(ruin.id());
    entry.pending = true;
    entry.emptySince = -1;
    data.setDirty();
  }

  static boolean pending(MinecraftServer server, ResourceLocation ruin) {
    return RuinWorkshopData.get(server).peek(ruin).map(entry -> entry.pending).orElse(false);
  }

  /** Floods the pit again, puts the ring back, gives the parts back and closes the sluices. */
  static void reset(ServerLevel level, Site site) {
    var server = level.getServer();
    var data = RuinWorkshopData.get(server);
    var entry = data.entry(site.ruin.id());
    var definition = RuinRegistry.get(site.ruin.id().toString()).orElse(null);
    List<BoundingBox> regions = new ArrayList<>();
    if (site.seal != null) regions.add(clearRing(level, site));
    Map<UUID, List<ItemStack>> owed = new HashMap<>();
    List<ItemStack> unclaimed = new ArrayList<>();
    List<BlockPos> cells = new ArrayList<>(site.parts.keySet());
    for (var sandbox : site.sandboxes) cells.addAll(sandbox.cells());
    for (BlockPos pos : cells) {
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) continue;
      // Only what builders can put there goes: the room's own lanterns stay.
      if (!site.parts.containsKey(pos) && !state.is(SANDBOX_BLOCKS)) continue;
      List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      UUID placer = entry.placers.get(pos.asLong());
      (placer == null ? unclaimed : owed.computeIfAbsent(placer, ignored -> new ArrayList<>())).addAll(drops);
    }
    for (var sandbox : site.sandboxes)
      for (ItemEntity spilled : level.getEntitiesOfClass(ItemEntity.class, sandbox.area().inflate(3)))
        if (spilled.getAge() < 40) {
          unclaimed.add(spilled.getItem().copy());
          spilled.discard();
        }
    for (var drain : site.drains) {
      int[] size = drain.marker().size();
      regions.add(BoundingBox.fromCorners(drain.pos(), drain.pos().offset(size[0] - 1, size[1] - 1, size[2] - 1)));
    }
    for (var region : regions) restore(level, site.ruin, definition, region);
    owed.forEach((who, stacks) -> {
      ServerPlayer player = server.getPlayerList().getPlayer(who);
      for (ItemStack stack : stacks) {
        if (player != null) {
          if (!player.getInventory().add(stack)) player.drop(stack, false);
        } else unclaimed.add(stack);
      }
      if (player != null)
        player.sendSystemMessage(Component.translatable("entrelumen.ruin.engine.returned").withStyle(ChatFormatting.GRAY));
    });
    stash(level, site, unclaimed);
    for (var lever : site.levers) {
      BlockState state = level.getBlockState(lever.pos());
      if (state.getBlock() instanceof LeverBlock && state.getValue(BlockStateProperties.POWERED))
        level.setBlock(lever.pos(), state.setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
    }
    sluices(level, site);
    site.sealRest = 0;
    site.wasPumping = false;
    site.heals = null;
    entry.pending = false;
    entry.emptySince = -1;
    entry.placers.clear();
    data.setDirty();
    LOGGER.info("Ruin engine {}: reset for the next team ({} regions); parts back to {} placer(s), {} to the barrel",
        site.ruin.id(), regions.size(), owed.size(), unclaimed.size());
  }

  /**
   * Takes the seal ring away for a restore: its contraption goes without putting its blocks back, and
   * so does the bearing, which would otherwise keep turning a contraption that is gone. Returns the
   * box to restore.
   */
  static BoundingBox clearRing(ServerLevel level, Site site) {
    BlockPos s = site.seal.pos();
    var bearing = CreateCompat.bearing(level, s);
    if (bearing != null && bearing.contraption() != null) bearing.contraption().discard();
    var ring = new BoundingBox(s.getX() - 9, s.getY() - 2, s.getZ() - 9, s.getX() + 9, s.getY(), s.getZ() + 9);
    for (Entity entity : level.getEntities((Entity) null, AABB.of(ring).inflate(2),
        e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().contains("contraption")))
      entity.discard();
    level.setBlock(s, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    return ring;
  }

  /** Puts the template's blocks back inside a box, then the markers that stand in it. */
  static void restore(ServerLevel level, RuinData.Ruin ruin, @Nullable RuinDefinitions.Definition definition,
      BoundingBox box) {
    var template = level.getServer().getStructureManager().get(ruin.template()).orElse(null);
    if (template == null) {
      LOGGER.warn("Ruin engine {}: template {} is missing, cannot restore {}", ruin.id(), ruin.template(), box);
      return;
    }
    var settings = new StructurePlaceSettings().setBoundingBox(box).setKnownShape(true);
    template.placeInWorld(level, ruin.origin(), ruin.origin(), settings, level.getRandom(),
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    for (var marker : ruin.markers())
      if (box.isInside(marker.pos()) && marker.marker().kind() != RuinMarkers.Kind.GROUND)
        RuinPlacement.placeMarker(level, definition, marker.marker(), marker.pos());
  }

  /** Items nobody can take now go to the barrel at the door (or beside it when it is full). */
  private static void stash(ServerLevel level, Site site, List<ItemStack> items) {
    if (items.isEmpty()) return;
    BlockPos at = site.returns != null ? site.returns.pos() : site.ruin.arrival();
    var container = level.getBlockEntity(at) instanceof Container c ? c : null;
    for (ItemStack stack : items) {
      if (container != null) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
          ItemStack there = container.getItem(slot);
          if (there.isEmpty()) {
            container.setItem(slot, stack.copy());
            stack.setCount(0);
          } else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
            int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
            there.grow(moved);
            stack.shrink(moved);
          }
        }
        container.setChanged();
      }
      if (!stack.isEmpty())
        level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, stack));
    }
  }
}
