package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.FloorState;
import dev.entrelumen.EnvesData.Placement;
import dev.entrelumen.EnvesData.Status;
import dev.entrelumen.EnvesLayout.Role;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * El Envés, the generative descent (docs/design/dungeon-enves.md): its dimension, blocks and the
 * life of an attempt. A team pays the offering at the gate under the start ruin and picks a
 * difficulty; the attempt takes a slot, floor I is placed and the group walks down, lighting seals
 * to open each stairwell while the floor below is placed in the background. Falls come out of a
 * shared pool; the attempt ends when the pool runs dry or ten minutes after the last member left,
 * and its slot is wiped. Encounters, loot, shrines, vaults and the boss plug in through
 * {@link EnvesHooks}.
 */
public final class Enves {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final ResourceKey<Level> LEVEL =
      ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("entrelumen", "enves"));
  public static final String REGION_ID = "entrelumen:enves";
  /** Arrivals stand north-west of the stairwell and face it (south-east). */
  public static final float ARRIVAL_YAW = -45f;

  static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("entrelumen");
  public static final DeferredBlock<EnvesSealBlock> SEAL = BLOCKS.register("enves_seal",
      () -> new EnvesSealBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
          .strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.LODESTONE).pushReaction(PushReaction.BLOCK)
          .lightLevel(state -> state.getValue(EnvesSealBlock.LIT) ? 12 : 3)));
  public static final DeferredBlock<EnvesPortalBlock> PORTAL = BLOCKS.register("enves_portal",
      () -> new EnvesPortalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
          .strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)
          .isValidSpawn((state, level, pos, type) -> false)
          .lightLevel(state -> state.getValue(EnvesPortalBlock.ACTIVE) ? 13 : 4)));
  public static final DeferredBlock<EnvesGateBlock> GATE = BLOCKS.register("enves_gate",
      () -> new EnvesGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
          .strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.DEEPSLATE).pushReaction(PushReaction.BLOCK)
          .lightLevel(state -> 5)));

  /** Descents redrawn from their seeds; a handful at most are live. */
  private static final Map<Long, EnvesLayout.Descent> DESCENTS = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, EnvesLayout.Descent> eldest) {
      return size() > 32;
    }
  };

  /** Players the server moved in or out itself: the travel guard lets them through. */
  static boolean ownTravel;
  /** Map signatures already sent, per player. */
  private static final Map<UUID, Long> SENT_MAPS = new HashMap<>();
  /** Players who saw the HUD: they get one "outside" update when they leave. */
  private static final Set<UUID> HUD_SHOWN = new HashSet<>();
  /** Payers waiting for floor I: moved in when it is ready, if still at the gate. */
  private static final Map<UUID, UUID> WAITING = new HashMap<>();
  /** Members who asked to give up once (command): the game time of that first ask. */
  private static final Map<UUID, Long> GIVE_UP_ARMED = new HashMap<>();

  private Enves() {}

  static void register(IEventBus bus) {
    BLOCKS.register(bus);
    bus.addListener(EnvesNetwork::register);
    StructureProtection.registerProvider(REGION_ID, Enves::regions);
    NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new EnvesConfig.Listener()));
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> EnvesPlacer.resume(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> EnvesPlacer.stop(event.getServer()));
    NeoForge.EVENT_BUS.addListener(EnvesPlacer::onEntityJoin);
    NeoForge.EVENT_BUS.addListener(Enves::onLogin);
    NeoForge.EVENT_BUS.addListener(Enves::onChangedDimension);
    NeoForge.EVENT_BUS.addListener(Enves::onLogout);
    TeamEvent.CREATED.register(Enves::onTeamCreated);
    TeamEvent.PLAYER_JOINED_PARTY.register(Enves::onJoinedParty);
    TeamEvent.DELETED.register(Enves::onTeamDeleted);
    NeoForge.EVENT_BUS.addListener(EnvesCommands::register);
    bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
        event.enqueueWork(EnvesGuard::hookWaystones));
    EnvesGuard.register();
    EnvesDeaths.register();
    EnvesEntrance.register();
    EnvesContent.register(bus);
  }

  public static ServerLevel level(MinecraftServer server) {
    return server.getLevel(LEVEL);
  }

  public static boolean inEnves(ServerPlayer player) {
    return player.level().dimension().equals(LEVEL);
  }

  /** The whole dimension is one protected region: nothing is broken, placed or griefed. */
  static List<ProtectionRules.Region> regions(MinecraftServer server) {
    ServerLevel level = level(server);
    if (level == null) return List.of();
    var box = new ProtectionRules.Box(-30_000_000, level.getMinBuildHeight(), -30_000_000, 30_000_000,
        level.getMaxBuildHeight() - 1, 30_000_000);
    return List.of(new ProtectionRules.Region(REGION_ID, LEVEL.location().toString(), box, ProtectionRules.ActGate.NONE,
        List.of()));
  }

  // ---- Layouts and positions ----------------------------------------------------------------

  public static synchronized EnvesLayout.Descent descent(Attempt attempt) {
    return DESCENTS.computeIfAbsent(attempt.seed, EnvesLayout::descent);
  }

  /** The layout of a floor (depth 1..5). */
  public static EnvesLayout.Floor layout(Attempt attempt, int depth) {
    return descent(attempt).floor(depth);
  }

  /** The cell a depth's template sits on: floor I's start for the vestibule. */
  static List<Integer> cells(Attempt attempt, int depth) {
    if (depth == EnvesGeometry.VESTIBULE) return List.of(layout(attempt, 1).start());
    return layout(attempt, depth).cells();
  }

  public static String tileset(int depth) {
    return EnvesConfig.settings().tileset(Math.max(1, depth));
  }

  public static EnvesHooks.Floor floor(ServerLevel level, Attempt attempt, int depth) {
    return new EnvesHooks.Floor(level, attempt, layout(attempt, depth), depth, tileset(depth));
  }

  /** The template a cell uses: role, doors and a variant drawn from the seed. */
  static String templateName(Attempt attempt, int depth, int cell) {
    if (depth == EnvesGeometry.VESTIBULE) return EnvesTemplates.name("vestibule", 0, 0);
    var layout = layout(attempt, depth);
    String role = layout.role(cell).id;
    int variants = EnvesTemplates.variants(role);
    long mix = attempt.seed * 6364136223846793005L + depth * 1442695040888963407L + cell * 0x9E3779B97F4A7C15L;
    mix ^= mix >>> 29;
    return EnvesTemplates.name(role, layout.doors(cell), (int) Math.floorMod(mix, (long) variants));
  }

  static BlockPos origin(Attempt attempt, int depth, int cell) {
    int[] o = EnvesGeometry.cellOrigin(attempt.slot, depth, cell);
    return new BlockPos(o[0], o[1], o[2]);
  }

  /** A cell's markers in world coordinates (empty when its template is missing). */
  static List<EnvesHooks.WorldMarker> markers(MinecraftServer server, Attempt attempt, int depth, int cell) {
    var loaded = EnvesTemplates.get(server, tileset(depth), templateName(attempt, depth, cell));
    if (loaded.isEmpty()) return List.of();
    BlockPos origin = origin(attempt, depth, cell);
    Role role = depth == EnvesGeometry.VESTIBULE ? null : layout(attempt, depth).role(cell);
    List<EnvesHooks.WorldMarker> out = new ArrayList<>();
    for (var local : loaded.get().markers())
      out.add(new EnvesHooks.WorldMarker(local.marker(), origin.offset(local.pos()), cell, role));
    return out;
  }

  static List<EnvesHooks.WorldMarker> markers(EnvesHooks.Floor floor, int cell) {
    return markers(floor.level().getServer(), floor.attempt(), floor.depth(), cell);
  }

  static Optional<BlockPos> marker(MinecraftServer server, Attempt attempt, int depth, int cell, EnvesMarkers.Kind kind) {
    return markers(server, attempt, depth, cell).stream().filter(m -> m.marker().kind() == kind)
        .map(EnvesHooks.WorldMarker::pos).findFirst();
  }

  /** Where a player arrives on a floor: its start cell's arrival marker (feet). */
  public static BlockPos arrival(MinecraftServer server, Attempt attempt, int depth) {
    int d = Math.clamp(depth, 1, EnvesLayout.FLOORS);
    int start = layout(attempt, d).start();
    return marker(server, attempt, d, start, EnvesMarkers.Kind.ARRIVAL).orElseGet(() -> {
      BlockPos o = origin(attempt, d, start);
      return o.offset(EnvesGeometry.C - 3, 1, EnvesGeometry.C - 3);
    });
  }

  /** The attempt whose slot holds a position of the Envés, if any. */
  public static Optional<Attempt> attemptAt(MinecraftServer server, BlockPos pos) {
    int slot = EnvesGeometry.slotAt(pos.getX(), pos.getZ());
    return slot < 0 ? Optional.empty() : EnvesData.get(server).inSlot(slot);
  }

  // ---- Teams --------------------------------------------------------------------------------

  /** The campaign key of the player's team: the party id, or the player for a solo team. */
  static UUID teamOf(ServerPlayer player) {
    var api = FTBTeamsAPI.api();
    if (!api.isManagerLoaded()) return player.getUUID();
    var team = api.getManager().getTeamForPlayer(player);
    return team.isPresent() && team.get().isPartyTeam() ? team.get().getId() : player.getUUID();
  }

  /** Whether the player owns the FTB party the attempt belongs to. */
  static boolean partyOwner(ServerPlayer player, Attempt attempt) {
    var api = FTBTeamsAPI.api();
    if (!api.isManagerLoaded()) return false;
    return api.getManager().getTeamByID(attempt.team)
        .map(team -> team.isPartyTeam() && player.getUUID().equals(team.getOwner())).orElse(false);
  }

  /**
   * A solo player who founds a party brings their live attempt along: the attempt follows the
   * campaign key, which is now the party's.
   */
  static void onTeamCreated(TeamCreatedEvent event) {
    if (!event.getTeam().isPartyTeam() || !FTBTeamsAPI.api().isManagerLoaded()) return;
    rekey(FTBTeamsAPI.api().getManager().getServer(), event.getCreatorId(), event.getTeam().getId());
  }

  /** A player who joins a party without a live attempt brings their own along. */
  static void onJoinedParty(PlayerJoinedPartyTeamEvent event) {
    Team previous = event.getPreviousTeam();
    UUID from = previous != null && previous.isPartyTeam() ? previous.getId() : event.getPlayer().getUUID();
    rekey(event.getPlayer().server, from, event.getTeam().getId());
  }

  /**
   * A disbanded party's live attempt goes back to its payer (now solo again), or to the party's
   * owner when the payer has another live attempt or belongs to another party.
   */
  static void onTeamDeleted(TeamEvent event) {
    Team team = event.getTeam();
    if (!team.isPartyTeam() || !FTBTeamsAPI.api().isManagerLoaded()) return;
    MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
    if (server == null) return;
    var attempt = EnvesData.get(server).forTeam(team.getId());
    if (attempt.isEmpty()) return;
    UUID payer = attempt.get().payer;
    var payerTeam = payer == null ? Optional.<Team>empty() : FTBTeamsAPI.api().getManager().getTeamForPlayerID(payer);
    boolean payerFree = payer != null
        && (payerTeam.isEmpty() || !payerTeam.get().isPartyTeam() || payerTeam.get().getId().equals(team.getId()));
    if (payerFree && rekey(server, team.getId(), payer)) return;
    if (team.getOwner() != null) rekey(server, team.getId(), team.getOwner());
  }

  private static boolean rekey(MinecraftServer server, UUID from, UUID to) {
    if (server == null || from == null || to == null || from.equals(to)) return false;
    EnvesData data = EnvesData.get(server);
    var attempt = data.forTeam(from);
    if (!data.rekey(from, to)) return false;
    LOGGER.info("Envés: attempt {} moved from team {} to team {}", attempt.map(a -> a.id).orElse(null), from, to);
    return true;
  }

  static List<ServerPlayer> onlineMembers(MinecraftServer server, UUID team) {
    List<ServerPlayer> out = new ArrayList<>();
    for (ServerPlayer player : server.getPlayerList().getPlayers()) if (teamOf(player).equals(team)) out.add(player);
    return out;
  }

  /** The team's live attempt. */
  public static Optional<Attempt> attemptOf(ServerPlayer player) {
    return EnvesData.get(player.server).forTeam(teamOf(player));
  }

  /** The World Tier the team's campaign reached; the gate's difficulty choices go up to it. */
  static Tier currentTier(ServerPlayer player) {
    var reached = ApotheosisTiers.reached(ApotheosisTiers.campaignOf(player));
    return reached.isEmpty() ? Tier.HAVEN : reached.getLast();
  }

  static boolean frontier(ServerPlayer player) {
    return EnvesRules.frontier(ApotheosisTiers.reached(ApotheosisTiers.campaignOf(player)));
  }

  // ---- The offering -------------------------------------------------------------------------

  /** Whether the player carries any of the gate's offerings. */
  static boolean canPay(ServerPlayer player) {
    return EnvesOffering.affordable(player).contains(true);
  }

  // ---- Attempts -----------------------------------------------------------------------------

  /** Why the gate refused; {@code null} when an attempt opened. */
  public enum Refusal {NOT_YET, ALREADY_OPEN, BAD_TIER, NO_OFFERING, FULL, NO_DIMENSION, PEACEFUL}

  /**
   * On Peaceful the Envés neither opens nor advances (Elias, D14): echoes could not fight back, so
   * the gate, the seals and the stairs refuse until the difficulty rises.
   */
  public static boolean peaceful(MinecraftServer server) {
    ServerLevel level = level(server);
    Difficulty difficulty = level != null ? level.getDifficulty() : server.getWorldData().getDifficulty();
    return difficulty == Difficulty.PEACEFUL;
  }

  /** {@link #open(ServerPlayer, Tier, int)} with the first offering the payer carries. */
  public static Refusal open(ServerPlayer payer, Tier tier) {
    return open(payer, tier, -1);
  }

  /**
   * Opens an attempt for the payer's team at the chosen difficulty: takes offering {@code offer} (or,
   * with -1, the first one the payer carries), reserves a slot and starts placing the vestibule and
   * floor I. The payer walks in once they are ready.
   */
  public static Refusal open(ServerPlayer payer, Tier tier, int offer) {
    MinecraftServer server = payer.server;
    if (level(server) == null) return Refusal.NO_DIMENSION;
    if (!frontier(payer)) return Refusal.NOT_YET;
    if (peaceful(server)) return Refusal.PEACEFUL;
    UUID team = teamOf(payer);
    EnvesData data = EnvesData.get(server);
    if (data.forTeam(team).isPresent()) return Refusal.ALREADY_OPEN;
    if (!EnvesRules.validChoice(tier, currentTier(payer))) return Refusal.BAD_TIER;
    if (data.freeSlot() < 0) return Refusal.FULL;
    int chosen = EnvesOffering.choose(EnvesOffering.affordable(payer), offer);
    if (chosen < 0 || !EnvesOffering.take(payer, chosen)) return Refusal.NO_OFFERING;
    create(payer, tier, payer.getRandom().nextLong());
    return null;
  }

  /** Creates the attempt of the payer's team (checks and the offering are the caller's). */
  static Attempt create(ServerPlayer payer, Tier tier, long seed) {
    MinecraftServer server = payer.server;
    EnvesData data = EnvesData.get(server);
    UUID team = teamOf(payer);
    Attempt attempt = new Attempt(UUID.randomUUID(), team, data.freeSlot(), seed, tier, payer.getUUID(),
        server.overworld().getGameTime());
    // The pool starts empty: each member adds their falls the first time they enter (joinPool).
    data.add(attempt);
    EnvesPlacer.queue(server, attempt, EnvesGeometry.VESTIBULE);
    EnvesPlacer.queue(server, attempt, 1);
    WAITING.put(payer.getUUID(), attempt.id);
    EnvesHooks.lifecycle().attemptOpened(attempt);
    LOGGER.info("Envés: {} opened attempt {} for team {} in slot {} at {}",
        payer.getGameProfile().getName(), attempt.id, team, attempt.slot, tier);
    for (ServerPlayer member : onlineMembers(server, team))
      member.sendSystemMessage(Component.translatable("entrelumen.enves.opened", payer.getDisplayName(),
          Component.translatable(tier.nameKey), EnvesConfig.settings().fallsPerMember()));
    return attempt;
  }

  /**
   * A member's first entry into the attempt adds their falls to the group's pool, once per player per
   * attempt (Elias, 29/9); later entries add nothing. Returns the falls added.
   */
  static int joinPool(MinecraftServer server, Attempt attempt, ServerPlayer player) {
    int grant = EnvesRules.joinGrant(attempt.joined.add(player.getUUID()), EnvesConfig.settings().fallsPerMember());
    if (grant == 0) return 0;
    attempt.poolTotal += grant;
    attempt.poolLeft += grant;
    EnvesData.get(server).setDirty();
    for (ServerPlayer member : onlineMembers(server, attempt.team))
      member.sendSystemMessage(Component.translatable("entrelumen.enves.joined", player.getDisplayName(), grant,
          attempt.poolLeft, attempt.poolTotal));
    return grant;
  }

  /** Moves a member into the team's open attempt, at the deepest floor the group reached. */
  public static boolean enter(ServerPlayer player) {
    var attempt = attemptOf(player);
    if (attempt.isEmpty() || attempt.get().status != Status.OPEN || peaceful(player.server)) return false;
    Attempt a = attempt.get();
    int depth = Math.clamp(a.frontline, 1, EnvesLayout.FLOORS);
    if (a.floor(depth).placement != Placement.READY) depth = 1;
    BlockPos arrival = arrival(player.server, a, depth);
    if (!move(player, level(player.server), Vec3.atBottomCenterOf(arrival), ARRIVAL_YAW)) return false;
    a.depthOf.put(player.getUUID(), depth);
    a.emptySince = -1;
    joinPool(player.server, a, player);
    EnvesData.get(player.server).setDirty();
    player.level().playSound(null, arrival, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.8f, 0.7f);
    player.displayClientMessage(Component.translatable("entrelumen.enves.entered",
        Component.translatable("entrelumen.enves.floor." + depth)), true);
    return true;
  }

  /** Through an active exit portal: back to the antechamber. */
  public static void leaveThroughPortal(ServerPlayer player) {
    if (!inEnves(player)) return;
    attemptAt(player.server, player.blockPosition()).ifPresent(a -> {
      int depth = EnvesGeometry.depthAt(player.blockPosition().getY());
      if (depth >= 1) a.depthOf.put(player.getUUID(), depth);
      EnvesData.get(player.server).setDirty();
    });
    toAntechamber(player);
  }

  /** Back to the start ruin's antechamber; without one, to the player's bed or the world spawn. */
  public static boolean toAntechamber(ServerPlayer player) {
    var entrance = EnvesData.get(player.server).entrance;
    ServerLevel overworld = player.server.overworld();
    Vec3 target;
    float yaw = 180f;
    if (entrance.antechamber != null) {
      target = SolsticioTravel.safeSpot(overworld, entrance.antechamber);
    } else {
      float[] y = {player.getYRot()};
      target = SolsticioTravel.homeFor(player, y);
      yaw = y[0];
    }
    boolean moved = move(player, overworld, target, yaw);
    if (moved) {
      overworld.playSound(null, BlockPos.containing(target), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
          SoundSource.PLAYERS, 0.8f, 1.2f);
      hudOutside(player);
    }
    return moved;
  }

  /**
   * The command's give-up, and the gate's second click: it ends the attempt only when a give-up was armed
   * within the last {@link EnvesRules#GIVE_UP_WINDOW} ticks; otherwise it arms one, as a first click would.
   */
  public static boolean giveUp(ServerPlayer player) {
    return giveUp(player, false);
  }

  /**
   * The team gives its attempt up, which ends it for everyone and wipes the slot. While teammates are
   * inside, only the payer, the party owner or a member who is inside may ({@link EnvesRules#mayGiveUp}).
   * The server arms it itself, whatever the client counted: a call with {@code armOnly} (the gate's first
   * click) only arms it and says how many are inside; any call ends it only when an armed give-up from
   * the last {@link EnvesRules#GIVE_UP_WINDOW} ticks is waiting, and otherwise arms one. The armed state
   * expires with the window, so a second click minutes later only asks again. Returns whether the attempt ended.
   */
  public static boolean giveUp(ServerPlayer player, boolean armOnly) {
    var found = attemptOf(player);
    if (found.isEmpty()) return false;
    Attempt attempt = found.get();
    MinecraftServer server = player.server;
    List<ServerPlayer> inside = new ArrayList<>();
    for (ServerPlayer member : membersInside(server, attempt))
      if (attempt.team.equals(teamOf(member))) inside.add(member);
    boolean self = inside.contains(player);
    int others = inside.size() - (self ? 1 : 0);
    if (!EnvesRules.mayGiveUp(player.getUUID().equals(attempt.payer), partyOwner(player, attempt), self, others)) {
      GIVE_UP_ARMED.remove(player.getUUID());
      player.sendSystemMessage(Component.translatable("entrelumen.enves.giveup.members_inside"));
      return false;
    }
    long now = server.overworld().getGameTime();
    if (armOnly || !EnvesRules.giveUpConfirmed(GIVE_UP_ARMED.get(player.getUUID()), now)) {
      GIVE_UP_ARMED.put(player.getUUID(), now);
      player.sendSystemMessage(Component.translatable("entrelumen.enves.giveup.confirm", inside.size()));
      return false;
    }
    GIVE_UP_ARMED.remove(player.getUUID());
    for (ServerPlayer member : onlineMembers(server, attempt.team))
      member.sendSystemMessage(Component.translatable("entrelumen.enves.given_up_by", player.getDisplayName()));
    end(server, attempt, EnvesHooks.EndReason.GIVEN_UP);
    return true;
  }

  /** Ends an attempt: everyone inside goes back to the antechamber and the slot is wiped. */
  public static void end(MinecraftServer server, Attempt attempt, EnvesHooks.EndReason reason) {
    if (attempt.status == Status.ENDED) return;
    attempt.status = Status.ENDED;
    EnvesData data = EnvesData.get(server);
    data.setDirty();
    ServerLevel level = level(server);
    if (level != null)
      for (ServerPlayer player : List.copyOf(level.players()))
        if (!player.isDeadOrDying() && attemptAt(server, player.blockPosition()).map(a -> a.id.equals(attempt.id)).orElse(false)) {
          toAntechamber(player);
          player.sendSystemMessage(Component.translatable("entrelumen.enves.ended." + reason.name().toLowerCase(java.util.Locale.ROOT)));
        }
    for (ServerPlayer member : onlineMembers(server, attempt.team))
      if (!inEnves(member)) member.sendSystemMessage(Component.translatable(
          "entrelumen.enves.ended." + reason.name().toLowerCase(java.util.Locale.ROOT)));
    EnvesPlacer.cancel(attempt);
    EnvesPlacer.queueWipe(server, attempt);
    WAITING.values().removeIf(id -> id.equals(attempt.id));
    EnvesHooks.lifecycle().attemptEnded(attempt, reason);
    LOGGER.info("Envés: attempt {} of team {} ended ({})", attempt.id, attempt.team, reason);
  }

  /** The boss fell: floor V's victory portal wakes. */
  public static void bossDefeated(Attempt attempt) {
    if (attempt.bossDefeated) return;
    attempt.bossDefeated = true;
    MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    if (server == null) return;
    EnvesData.get(server).setDirty();
    ServerLevel level = level(server);
    if (level == null || attempt.floor(EnvesLayout.BOSS_DEPTH).placement != Placement.READY) return;
    int portal = layout(attempt, EnvesLayout.BOSS_DEPTH).exit();
    for (var m : markers(server, attempt, EnvesLayout.BOSS_DEPTH, portal))
      if (m.marker().kind() == EnvesMarkers.Kind.EXIT_PORTAL)
        level.setBlock(m.pos(), PORTAL.get().defaultBlockState().setValue(EnvesPortalBlock.ACTIVE, true), Block.UPDATE_ALL);
    EnvesHooks.lifecycle().bossDefeated(attempt);
  }

  // ---- Seals and stairwells -----------------------------------------------------------------

  /** A member lights the seal at {@code pos}: counted once per floor; the last one opens the way down. */
  public static boolean lightSeal(ServerPlayer player, BlockPos pos) {
    if (!inEnves(player)) return false;
    var found = attemptAt(player.server, pos);
    if (found.isEmpty() || found.get().status != Status.OPEN || !found.get().team.equals(teamOf(player))) return false;
    Attempt attempt = found.get();
    int depth = EnvesGeometry.depthAt(pos.getY());
    if (depth < 1 || depth >= EnvesLayout.BOSS_DEPTH) return false;
    int cell = EnvesGeometry.cellAt(attempt.slot, pos.getX(), pos.getZ());
    var layout = layout(attempt, depth);
    if (cell < 0 || layout.role(cell) != Role.SEAL) return false;
    FloorState state = attempt.floor(depth);
    if (state.lit.get(cell)) return false;
    ServerLevel level = player.serverLevel();
    var context = floor(level, attempt, depth);
    if (!EnvesHooks.seals().mayLight(context, cell, player)) return false;
    state.lit.set(cell);
    EnvesData.get(player.server).setDirty();
    level.setBlock(pos, SEAL.get().defaultBlockState().setValue(EnvesSealBlock.LIT, true), Block.UPDATE_ALL);
    level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.1f);
    int lit = state.lit.cardinality(), total = layout.seals().size();
    for (ServerPlayer member : membersInside(player.server, attempt))
      member.displayClientMessage(Component.translatable("entrelumen.enves.seal_lit", lit, total), true);
    EnvesHooks.seals().lit(context, cell, player, lit, total);
    return true;
  }

  /** Opens floor {@code depth}'s stairwell: its seal blocks become air. */
  static void openStair(MinecraftServer server, Attempt attempt, int depth) {
    FloorState state = attempt.floor(depth);
    if (state.stairOpen) return;
    state.stairOpen = true;
    EnvesData.get(server).setDirty();
    ServerLevel level = level(server);
    if (level == null) return;
    int exit = layout(attempt, depth).exit();
    for (var m : markers(server, attempt, depth, exit))
      if (m.marker().kind() == EnvesMarkers.Kind.STAIR_SEAL) level.setBlock(m.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    var top = marker(server, attempt, depth, exit, EnvesMarkers.Kind.STAIR_TOP);
    top.ifPresent(pos -> level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 0.6f));
    for (ServerPlayer member : membersInside(server, attempt))
      member.displayClientMessage(Component.translatable("entrelumen.enves.stair_open"), false);
  }

  /** The players standing in an attempt's slot. */
  static List<ServerPlayer> membersInside(MinecraftServer server, Attempt attempt) {
    ServerLevel level = level(server);
    if (level == null) return List.of();
    List<ServerPlayer> out = new ArrayList<>();
    for (ServerPlayer player : level.players())
      if (attemptAt(server, player.blockPosition()).map(a -> a.id.equals(attempt.id)).orElse(false)) out.add(player);
    return out;
  }

  // ---- Movement -----------------------------------------------------------------------------

  /** Moves a player between dimensions past the travel guard, leaving mounts behind. */
  static boolean move(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw) {
    if (target == null) return false;
    player.stopRiding();
    player.ejectPassengers();
    target.getChunk(BlockPos.containing(pos));
    player.fallDistance = 0;
    if (target == player.level()) {
      player.teleportTo(target, pos.x, pos.y, pos.z, yaw, player.getXRot());
      placed(player, target, pos);
      return true;
    }
    ownTravel = true;
    try {
      var moved = player.changeDimension(new DimensionTransition(target, pos, Vec3.ZERO, yaw, player.getXRot(),
          DimensionTransition.DO_NOTHING));
      boolean arrived = moved != null && player.level() == target;
      if (arrived) placed(player, target, pos);
      return arrived;
    } finally {
      ownTravel = false;
    }
  }

  /**
   * The server put a player somewhere in the Envés: the no-clip guard starts over from there, and the
   * floor counts as theirs, so the depth cap in {@link #track} does not send them back.
   */
  private static void placed(ServerPlayer player, ServerLevel target, Vec3 pos) {
    if (!target.dimension().equals(LEVEL)) return;
    EnvesGuard.moved(player);
    BlockPos at = BlockPos.containing(pos);
    int depth = EnvesGeometry.depthAt(at.getY());
    if (depth < 1) return;
    attemptAt(player.server, at).ifPresent(a -> {
      a.depthOf.put(player.getUUID(), depth);
      EnvesData.get(player.server).setDirty();
    });
  }

  // ---- Ticking ------------------------------------------------------------------------------

  static void tick(MinecraftServer server) {
    EnvesPlacer.tick(server);
    EnvesDeaths.tick();
    EnvesEntrance.tick(server);
    if (server.getTickCount() % 10 != 3) return;
    EnvesData data = EnvesData.get(server);
    ServerLevel level = level(server);
    long now = server.overworld().getGameTime();
    Map<UUID, List<ServerPlayer>> inside = new HashMap<>();
    if (level != null)
      for (ServerPlayer player : List.copyOf(level.players())) {
        if (player.isDeadOrDying()) continue;
        var attempt = attemptAt(server, player.blockPosition());
        boolean valid = attempt.isPresent() && attempt.get().status == Status.OPEN
            && attempt.get().team.equals(teamOf(player));
        if (!valid) {
          if (!EnvesGuard.bypass(player)) {
            toAntechamber(player);
            player.displayClientMessage(Component.translatable("entrelumen.enves.not_yours"), false);
          }
          continue;
        }
        inside.computeIfAbsent(attempt.get().id, id -> new ArrayList<>()).add(player);
        track(server, level, attempt.get(), player);
      }
    for (Attempt attempt : data.attempts()) {
      switch (attempt.status) {
        case FORMING -> {
          if (attempt.floor(0).placement == Placement.READY && attempt.floor(1).placement == Placement.READY) {
            attempt.status = Status.OPEN;
            attempt.emptySince = now;
            data.setDirty();
            welcome(server, attempt);
          }
        }
        case OPEN -> {
          boolean someone = inside.containsKey(attempt.id);
          if (someone && attempt.emptySince >= 0) {
            attempt.emptySince = -1;
            data.setDirty();
          } else if (!someone && attempt.emptySince < 0) {
            attempt.emptySince = now;
            data.setDirty();
          }
          if (EnvesRules.abandoned(attempt.emptySince, now, EnvesConfig.settings().abandonTicks())) {
            end(server, attempt, EnvesHooks.EndReason.ABANDONED);
            continue;
          }
          advance(server, attempt);
        }
        default -> {}
      }
    }
    sync(server, data, inside);
  }

  /** The waiting payer walks in once floor I is ready, if still near the gate. */
  private static void welcome(MinecraftServer server, Attempt attempt) {
    for (var entry : List.copyOf(WAITING.entrySet())) {
      if (!entry.getValue().equals(attempt.id)) continue;
      WAITING.remove(entry.getKey());
      ServerPlayer payer = server.getPlayerList().getPlayer(entry.getKey());
      if (payer == null || (EnvesEntrance.nearGate(payer) && enter(payer))) continue;
      payer.sendSystemMessage(Component.translatable("entrelumen.enves.ready"));
    }
  }

  /** Queues the next floors and opens stairwells whose seals are all lit. */
  private static void advance(MinecraftServer server, Attempt attempt) {
    for (int depth = 1; depth < EnvesLayout.BOSS_DEPTH; depth++) {
      FloorState state = attempt.floor(depth);
      if (state.placement != Placement.READY) continue;
      int seals = layout(attempt, depth).seals().size();
      FloorState next = attempt.floor(depth + 1);
      if (next.placement == Placement.NONE
          && EnvesRules.nextFloorDue(state.guardReached, state.exitReached, state.lit.cardinality(), seals))
        EnvesPlacer.queue(server, attempt, depth + 1);
      if (!state.stairOpen && EnvesRules.stairOpens(state.lit.cardinality(), seals, next.placement == Placement.READY)
          && EnvesHooks.seals().stairMayOpen(floor(level(server), attempt, depth)))
        openStair(server, attempt, depth);
    }
  }

  /** Floor, exploration, the guard trigger and encounters for one member inside. */
  private static void track(MinecraftServer server, ServerLevel level, Attempt attempt, ServerPlayer player) {
    BlockPos pos = player.blockPosition();
    int depth = EnvesGeometry.depthAt(pos.getY());
    int cell = EnvesGeometry.cellAt(attempt.slot, pos.getX(), pos.getZ());
    if (depth < 0 || cell < 0 || pos.getY() < EnvesGeometry.VOID_Y) {
      rescue(server, attempt, player);
      return;
    }
    if (depth == EnvesGeometry.VESTIBULE) return;
    // Nobody walks past a closed stairwell: deeper than the open stairs (or than the floor the server
    // last put them on) means a blink or a teleport skipped the maze. On Peaceful nobody goes down.
    Integer recorded = attempt.depthOf.get(player.getUUID());
    int walkable = Math.max(EnvesRules.reachableDepth(d -> attempt.floor(d).stairOpen, EnvesLayout.FLOORS),
        recorded == null ? 1 : recorded);
    boolean peaceful = peaceful(server);
    int allowed = peaceful && recorded != null ? Math.min(walkable, recorded) : walkable;
    if (depth > allowed && !EnvesGuard.bypass(player)) {
      rescue(server, attempt, player);
      EnvesGuard.tell(player, depth > walkable ? "entrelumen.enves.no_teleport" : "entrelumen.enves.peaceful");
      return;
    }
    EnvesData data = EnvesData.get(server);
    Integer before = attempt.depthOf.put(player.getUUID(), depth);
    if (before == null || before != depth) data.setDirty();
    if (depth > attempt.frontline) {
      attempt.frontline = depth;
      data.setDirty();
    }
    var layout = layout(attempt, depth);
    if (!layout.has(cell) || !EnvesGeometry.interior(attempt.slot, pos.getX(), pos.getZ())) return;
    FloorState state = attempt.floor(depth);
    if (!state.explored.get(cell)) {
      state.explored.set(cell);
      data.setDirty();
    }
    Role role = layout.role(cell);
    if (role == Role.GUARD && !state.guardReached) {
      state.guardReached = true;
      data.setDirty();
    }
    if (role == Role.EXIT && !state.exitReached) {
      state.exitReached = true;
      data.setDirty();
    }
    if ((role == Role.FIGHT || role == Role.GUARD || role == Role.ARENA_CENTER) && !state.entered.get(cell)) {
      state.entered.set(cell);
      data.setDirty();
      var spawns = markers(server, attempt, depth, cell).stream()
          .filter(m -> m.marker().kind() == EnvesMarkers.Kind.ENCOUNTER || m.marker().kind() == EnvesMarkers.Kind.BOSS_CENTER)
          .toList();
      EnvesHooks.encounters().roomEntered(floor(level, attempt, depth), cell, role, spawns, player);
    }
  }

  /** Someone off every floor (the void, between slots): back to their floor's arrival, no fall counted. */
  static void rescue(MinecraftServer server, Attempt attempt, ServerPlayer player) {
    int depth = attempt.depthOf.getOrDefault(player.getUUID(), attempt.frontline);
    if (attempt.floor(Math.clamp(depth, 1, EnvesLayout.FLOORS)).placement != Placement.READY) depth = 1;
    BlockPos arrival = arrival(server, attempt, depth);
    player.fallDistance = 0;
    player.teleportTo(player.serverLevel(), arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, ARRIVAL_YAW, 0f);
    EnvesGuard.moved(player);
  }

  // ---- Client sync --------------------------------------------------------------------------

  private static void sync(MinecraftServer server, EnvesData data, Map<UUID, List<ServerPlayer>> inside) {
    for (Attempt attempt : data.attempts()) {
      List<ServerPlayer> players = inside.getOrDefault(attempt.id, List.of());
      if (players.isEmpty()) continue;
      List<EnvesNetwork.Mate> mates = new ArrayList<>();
      for (ServerPlayer p : players)
        mates.add(new EnvesNetwork.Mate(p.getGameProfile().getName(), (float) p.getX(), (float) p.getZ(),
            Math.max(0, EnvesGeometry.depthAt(p.blockPosition().getY()))));
      for (ServerPlayer player : players) {
        int depth = Math.max(0, EnvesGeometry.depthAt(player.blockPosition().getY()));
        int shown = Math.max(1, depth);
        var layout = layout(attempt, shown);
        FloorState state = attempt.floor(shown);
        List<EnvesNetwork.Mate> others = mates.stream().filter(m -> !m.name().equals(player.getGameProfile().getName())).toList();
        EnvesNetwork.send(player, new EnvesNetwork.Hud(true, depth, attempt.tier.ordinal(), attempt.poolLeft,
            attempt.poolTotal, state.lit.cardinality(), layout.seals().size(), state.stairOpen, false, others));
        HUD_SHOWN.add(player.getUUID());
        long signature = attempt.id.getLeastSignificantBits() * 31 + shown * 1_000_003L + state.explored.hashCode() * 7L
            + state.lit.hashCode();
        Long sent = SENT_MAPS.get(player.getUUID());
        if (sent == null || sent != signature) {
          SENT_MAPS.put(player.getUUID(), signature);
          EnvesNetwork.send(player, new EnvesNetwork.MapView(shown, EnvesGeometry.slotX(attempt.slot),
              EnvesGeometry.slotZ(attempt.slot), EnvesRules.known(layout, state.explored, state.lit)));
        }
      }
    }
  }

  static void hudOutside(ServerPlayer player) {
    SENT_MAPS.remove(player.getUUID());
    if (HUD_SHOWN.remove(player.getUUID())) EnvesNetwork.send(player, EnvesNetwork.Hud.OUTSIDE);
  }

  // ---- Player events ------------------------------------------------------------------------

  /** Logging in inside an attempt that is gone, or not the team's: back to the antechamber. */
  static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    // A fresh client has no map: the next sync sends it whole.
    SENT_MAPS.remove(player.getUUID());
    EnvesGuard.mapStage(player);
    if (!inEnves(player) || EnvesGuard.bypass(player)) return;
    var attempt = attemptAt(player.server, player.blockPosition());
    if (attempt.isEmpty() || attempt.get().status != Status.OPEN || !attempt.get().team.equals(teamOf(player))) {
      toAntechamber(player);
      player.sendSystemMessage(Component.translatable("entrelumen.enves.closed_while_away"));
    }
  }

  /** A client that leaves forgets the map and the HUD: both are sent again when it comes back. */
  static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
    UUID id = event.getEntity().getUUID();
    SENT_MAPS.remove(id);
    HUD_SHOWN.remove(id);
    GIVE_UP_ARMED.remove(id);
  }

  /** For tests: whether a map signature is remembered for the player. */
  static boolean mapSent(ServerPlayer player) {
    return SENT_MAPS.containsKey(player.getUUID());
  }

  static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    EnvesGuard.mapStage(player);
    if (event.getFrom().equals(LEVEL)) hudOutside(player);
  }

  /** For tests: forget per-player caches. */
  static void forget(ServerPlayer player) {
    SENT_MAPS.remove(player.getUUID());
    HUD_SHOWN.remove(player.getUUID());
    WAITING.remove(player.getUUID());
    GIVE_UP_ARMED.remove(player.getUUID());
  }
}
