package dev.entrelumen;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Player commands the Ark's modules unlock, for every player with or without cheats: {@code /sethome}
 * and {@code /home} (Habitation: one home per player, 15 minutes between trips, after five seconds
 * standing still and unhurt) and {@code /rtp} (Exploration: once an hour). {@code /rtp} loads its
 * candidate chunks with a ticket and reads them on later ticks, so the server thread never waits for
 * chunk generation. There is no {@code /back}: the only one in the pinned pack, Moonlight's
 * operator-only {@code /moonlight back}, has no config switch, so it is refused before it runs
 * ({@link #refuseBack}).
 */
public final class ArkCommands {
  static final TicketType<ChunkPos> RTP_TICKET =
      TicketType.create("entrelumen_rtp", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
  /** Candidates tried per {@code /rtp}, and ticks a candidate's chunk may take to load. */
  static final int RTP_ATTEMPTS = 8, RTP_WAIT_TICKS = 20 * 15;
  private static final Map<UUID, Search> SEARCHES = new HashMap<>();
  private static final Map<UUID, Warmup> WARMUPS = new HashMap<>();

  private ArkCommands() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(ArkCommands::commands);
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
      tickSearches(event.getServer());
      tickWarmups(event.getServer());
    });
    NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post event) -> {
      if (event.getEntity() instanceof ServerPlayer player && WARMUPS.remove(player.getUUID()) != null)
        player.sendSystemMessage(Component.translatable("entrelumen.home.hurt"));
    });
    NeoForge.EVENT_BUS.addListener(ArkCommands::refuseBack);
  }

  private static void commands(RegisterCommandsEvent event) {
    var dispatcher = event.getDispatcher();
    dispatcher.register(Commands.literal("sethome").executes(ctx -> setHome(ctx.getSource().getPlayerOrException())));
    dispatcher.register(Commands.literal("home").executes(ctx -> home(ctx.getSource().getPlayerOrException())));
    dispatcher.register(Commands.literal("rtp").executes(ctx -> rtp(ctx.getSource().getPlayerOrException())));
    dispatcher.register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("ark").executes(ctx -> stats(ctx.getSource())))));
  }

  private static String dimension(Level level) {
    return level.dimension().location().toString();
  }

  private static boolean active(ServerPlayer player, ArkRules.Module module) {
    if (ArkState.status(player).active(module)) return true;
    player.sendSystemMessage(Component.translatable("entrelumen.ark.command_locked",
        Component.translatable("block.entrelumen." + module.id)));
    return false;
  }

  // ---- Habitation: home --------------------------------------------------------------------------

  public static int setHome(ServerPlayer player) {
    if (!active(player, ArkRules.Module.HABITATION)) return 0;
    if (!ArkRules.homeAllowed(dimension(player.level()))) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.solsticio"));
      return 0;
    }
    ArkData data = ArkData.get(player.server);
    data.homes.put(player.getUUID(), new ArkData.Home(player.level().dimension(), player.getX(), player.getY(),
        player.getZ(), player.getYRot(), player.getXRot()));
    data.setDirty();
    BlockPos at = player.blockPosition();
    player.sendSystemMessage(Component.translatable("entrelumen.home.set", at.getX(), at.getY(), at.getZ()));
    return 1;
  }

  /** A {@code /home} waiting out its five seconds: where the player stood and when it ends. */
  private record Warmup(ResourceKey<Level> dimension, double x, double y, double z, long endsAt) {}

  /** Starts the five-second warm-up; the trip happens if the player neither moves nor gets hurt. */
  public static int home(ServerPlayer player) {
    if (!homeReady(player)) return 0;
    long now = player.server.overworld().getGameTime();
    WARMUPS.put(player.getUUID(), new Warmup(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
        now + ArkRules.HOME_WARMUP_TICKS));
    player.sendSystemMessage(Component.translatable("entrelumen.home.warmup", ArkRules.HOME_WARMUP_TICKS / 20));
    return 1;
  }

  static void tickWarmups(MinecraftServer server) {
    if (WARMUPS.isEmpty()) return;
    long now = server.overworld().getGameTime();
    Iterator<Map.Entry<UUID, Warmup>> it = WARMUPS.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      Warmup warmup = entry.getValue();
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      if (player == null || !player.isAlive() || !player.level().dimension().equals(warmup.dimension())) {
        it.remove();
        continue;
      }
      if (ArkRules.moved(player.getX() - warmup.x(), player.getY() - warmup.y(), player.getZ() - warmup.z())) {
        it.remove();
        player.sendSystemMessage(Component.translatable("entrelumen.home.moved"));
        continue;
      }
      if (now < warmup.endsAt()) continue;
      it.remove();
      travelHome(player);
    }
  }

  /** Whether a {@code /home} is waiting out its warm-up (tests). */
  static boolean warming(ServerPlayer player) {
    return WARMUPS.containsKey(player.getUUID());
  }

  /** Checks before and after the warm-up: module, home, Solsticio, cooldown, dimension, room. */
  private static boolean homeReady(ServerPlayer player) {
    if (!active(player, ArkRules.Module.HABITATION)) return false;
    ArkData data = ArkData.get(player.server);
    ArkData.Home home = data.homes.get(player.getUUID());
    if (home == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.none"));
      return false;
    }
    if (!ArkRules.homeAllowed(dimension(player.level())) || !ArkRules.homeAllowed(home.dimension().location().toString())) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.solsticio"));
      return false;
    }
    long now = player.server.overworld().getGameTime();
    long left = ArkRules.cooldownLeft(now, data.homeUsed.getOrDefault(player.getUUID(), 0L), ArkRules.HOME_COOLDOWN_TICKS);
    if (left > 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.cooldown", minutes(left), seconds(left)));
      return false;
    }
    ServerLevel level = player.server.getLevel(home.dimension());
    if (level == null) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.gone"));
      return false;
    }
    BlockPos feet = home.blockPos();
    if (!clear(level, feet)) {
      player.sendSystemMessage(Component.translatable("entrelumen.home.blocked", feet.getX(), feet.getY(), feet.getZ()));
      return false;
    }
    return true;
  }

  static long minutes(long ticks) {
    return (ticks + 19) / 20 / 60;
  }

  static long seconds(long ticks) {
    return (ticks + 19) / 20 % 60;
  }

  private static boolean travelHome(ServerPlayer player) {
    if (!homeReady(player)) return false;
    ArkData data = ArkData.get(player.server);
    ArkData.Home home = data.homes.get(player.getUUID());
    ServerLevel level = player.server.getLevel(home.dimension());
    long now = player.server.overworld().getGameTime();
    player.stopRiding();
    player.teleportTo(level, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
    data.homeUsed.put(player.getUUID(), Math.max(1, now));
    data.setDirty();
    player.sendSystemMessage(Component.translatable("entrelumen.home.arrived"));
    return true;
  }

  /** Room for a player at {@code feet}: no collision, fluid or harm at feet and head. */
  static boolean clear(ServerLevel level, BlockPos feet) {
    for (BlockPos part : new BlockPos[] {feet, feet.above()}) {
      if (level.isOutsideBuildHeight(part)) return false;
      BlockState state = level.getBlockState(part);
      if (!state.getCollisionShape(level, part).isEmpty() || !state.getFluidState().isEmpty()
          || EntityType.PLAYER.isBlockDangerous(state)) return false;
    }
    return true;
  }

  // ---- Exploration: random travel ----------------------------------------------------------------

  /** One {@code /rtp} in progress: the candidate whose chunk is loading and the tries left. */
  private static final class Search {
    final ResourceKey<Level> dimension;
    final BlockPos spawn;
    int attempt;
    long x, z;
    ChunkPos chunk;
    long waitingSince;

    Search(ResourceKey<Level> dimension, BlockPos spawn) {
      this.dimension = dimension;
      this.spawn = spawn;
    }
  }

  public static int rtp(ServerPlayer player) {
    if (!active(player, ArkRules.Module.EXPLORATION)) return 0;
    if (!ArkRules.rtpAllowed(dimension(player.level()))) {
      player.sendSystemMessage(Component.translatable("entrelumen.rtp.dimension"));
      return 0;
    }
    if (SEARCHES.containsKey(player.getUUID())) {
      player.sendSystemMessage(Component.translatable("entrelumen.rtp.searching"));
      return 0;
    }
    ArkData data = ArkData.get(player.server);
    long now = player.server.overworld().getGameTime();
    long left = ArkRules.cooldownLeft(now, data.rtpUsed.getOrDefault(player.getUUID(), 0L), ArkRules.RTP_COOLDOWN_TICKS);
    if (left > 0) {
      player.sendSystemMessage(Component.translatable("entrelumen.rtp.cooldown", minutes(left), seconds(left)));
      return 0;
    }
    ServerLevel level = player.serverLevel();
    Search search = new Search(level.dimension(), level.getSharedSpawnPos());
    SEARCHES.put(player.getUUID(), search);
    next(level, search);
    player.sendSystemMessage(Component.translatable("entrelumen.rtp.started"));
    return 1;
  }

  private static void next(ServerLevel level, Search search) {
    if (search.chunk != null) level.getChunkSource().removeRegionTicket(RTP_TICKET, search.chunk, 0, search.chunk);
    var random = level.getRandom();
    long[] point = ArkRules.rtpCandidate(search.spawn.getX(), search.spawn.getZ(), random.nextDouble(), random.nextDouble());
    search.attempt++;
    search.x = point[0];
    search.z = point[1];
    search.chunk = new ChunkPos(BlockPos.containing(point[0], 0, point[1]));
    search.waitingSince = level.getGameTime();
    level.getChunkSource().addRegionTicket(RTP_TICKET, search.chunk, 0, search.chunk);
  }

  static void tickSearches(MinecraftServer server) {
    if (SEARCHES.isEmpty()) return;
    Iterator<Map.Entry<UUID, Search>> it = SEARCHES.entrySet().iterator();
    while (it.hasNext()) {
      var entry = it.next();
      Search search = entry.getValue();
      ServerLevel level = server.getLevel(search.dimension);
      ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
      if (level == null || player == null || !player.isAlive() || !player.level().dimension().equals(search.dimension)) {
        if (level != null && search.chunk != null)
          level.getChunkSource().removeRegionTicket(RTP_TICKET, search.chunk, 0, search.chunk);
        it.remove();
        continue;
      }
      boolean loaded = level.getChunkSource().getChunkNow(search.chunk.x, search.chunk.z) != null;
      if (!loaded && level.getGameTime() - search.waitingSince < RTP_WAIT_TICKS) continue;
      BlockPos stand = loaded && level.getWorldBorder().isWithinBounds(BlockPos.containing(search.x, 0, search.z))
          ? stand(level, (int) search.x, (int) search.z) : null;
      if (stand != null) {
        player.stopRiding();
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, player.getYRot(), player.getXRot());
        level.getChunkSource().removeRegionTicket(RTP_TICKET, search.chunk, 0, search.chunk);
        ArkData data = ArkData.get(server);
        data.rtpUsed.put(player.getUUID(), Math.max(1, server.overworld().getGameTime()));
        data.setDirty();
        player.sendSystemMessage(Component.translatable("entrelumen.rtp.arrived", stand.getX(), stand.getY(), stand.getZ()));
        it.remove();
      } else if (search.attempt >= RTP_ATTEMPTS) {
        level.getChunkSource().removeRegionTicket(RTP_TICKET, search.chunk, 0, search.chunk);
        player.sendSystemMessage(Component.translatable("entrelumen.rtp.failed"));
        it.remove();
      } else {
        next(level, search);
      }
    }
  }

  /**
   * A safe place to stand in a loaded column: on the surface, or, in a dimension with a roof, the
   * highest room under it. Solid floor, two free blocks, no fluid and nothing that hurts.
   */
  static BlockPos stand(ServerLevel level, int x, int z) {
    int bottom = level.getMinBuildHeight() + 1;
    int top = level.getMinBuildHeight() + level.getLogicalHeight();
    int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    if (!level.dimensionType().hasCeiling() && surface < top - 2) {
      BlockPos feet = new BlockPos(x, surface, z);
      return surface > bottom && standable(level, feet) ? feet : null;
    }
    for (int y = Math.min(surface, top) - 2; y > bottom; y--) {
      BlockPos feet = new BlockPos(x, y, z);
      if (standable(level, feet)) return feet;
    }
    return null;
  }

  static boolean standable(ServerLevel level, BlockPos feet) {
    BlockPos floor = feet.below();
    BlockState ground = level.getBlockState(floor);
    return ground.isFaceSturdy(level, floor, Direction.UP) && ground.getFluidState().isEmpty()
        && !EntityType.PLAYER.isBlockDangerous(ground) && clear(level, feet);
  }

  // ---- no /back ----------------------------------------------------------------------------------

  /**
   * Refuses Moonlight 3.5.2's {@code /moonlight back} (operators only; it returns to where the last
   * teleport began). Moonlight has no setting to turn its commands off, so the command is cancelled
   * before it runs. ENTRELUMEN has one way home: {@code /home}.
   */
  static void refuseBack(CommandEvent event) {
    var nodes = event.getParseResults().getContext().getNodes();
    if (nodes.size() >= 2 && "moonlight".equals(nodes.get(0).getNode().getName())
        && "back".equals(nodes.get(1).getNode().getName())) {
      event.setCanceled(true);
      event.getParseResults().getContext().getSource().sendFailure(Component.translatable("entrelumen.home.no_back"));
    }
  }

  // ---- admin -------------------------------------------------------------------------------------

  private static int stats(CommandSourceStack source) {
    ArkData data = ArkData.get(source.getServer());
    source.sendSuccess(() -> Component.literal("arks=" + data.arks.size() + " homes=" + data.homes.size()
        + " effectPass=" + ArkEffects.lastPassNanos / 1000 + "us (max " + ArkEffects.maxPassNanos / 1000
        + "us) players=" + ArkEffects.lastPassPlayers + " charging=" + ArkEffects.lastChargeNanos / 1000
        + "us items=" + ArkEffects.lastChargedItems + " every " + ArkEffects.PERIOD_TICKS + " ticks"), false);
    data.arks.forEach((team, ark) -> source.sendSuccess(() -> Component.literal(team + " " + ark.dimension.location()
        + " " + ark.anchor.origin().toShortString() + " r" + ark.anchor.rotation() + " core=" + ark.core
        + " missing=" + ark.missingCore + " controller=" + ark.controller + " modules=" + ark.modules), false));
    return data.arks.size();
  }
}
