package dev.entrelumen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The challenges of the placed ruins, per team. A click on a marker's cell is looked up in an index
 * of every placed ruin's markers and answered from the team's {@link RuinProgress}; the world only
 * shows the state of the team that last worked on a challenge. Solving a challenge opens the gates
 * that need it for that team ({@link RuinGates}); the pedestal gives the key piece once the team
 * solved what it asks for. Pure rules live in {@link RuinRules}.
 */
public final class RuinChallenges {
  /** A lever or button use in a ruin credits a redstone lock powered within this many ticks. */
  static final int CREDIT_TICKS = 600;
  /** How long a solved lever lock and aimed mirrors stay before they reset for the next team. */
  static final int RESET_TICKS = 100, MIRROR_RESET_TICKS = 200;
  static final int LORE_INTERVAL = 20;

  record Node(RuinData.Ruin ruin, RuinData.PlacedMarker marker) {}

  record Scheduled(long tick, Runnable task) {}

  record Credit(UUID campaign, UUID founder, long tick) {}

  private static final class State {
    int revision = -1;
    final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Node>> nodes = new HashMap<>();
    final List<Scheduled> scheduled = new ArrayList<>();
    final Map<ResourceLocation, Credit> credits = new HashMap<>();
    final Set<String> pendingResets = new HashSet<>();
  }

  private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

  private RuinChallenges() {}

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

  /** The marker at a position of any placed ruin, or null. */
  @Nullable
  static Node node(ServerLevel level, BlockPos pos) {
    State state = state(level.getServer());
    var data = RuinData.get(level.getServer());
    if (state.revision != data.revision()) {
      state.nodes.clear();
      for (var ruin : data.ruins())
        for (var marker : ruin.markers())
          state.nodes.computeIfAbsent(ruin.dimension(), ignored -> new Long2ObjectOpenHashMap<>())
              .put(marker.pos().asLong(), new Node(ruin, marker));
      state.revision = data.revision();
    }
    var nodes = state.nodes.get(level.dimension());
    return nodes == null ? null : nodes.get(pos.asLong());
  }

  static void schedule(MinecraftServer server, int delay, Runnable task) {
    state(server).scheduled.add(new Scheduled(server.overworld().getGameTime() + delay, task));
  }

  /** Claims a pending reset {@code key}; false when one is already scheduled. */
  static boolean pendingReset(MinecraftServer server, String key) {
    return state(server).pendingResets.add(key);
  }

  static void resetDone(MinecraftServer server, String key) {
    state(server).pendingResets.remove(key);
  }

  static void tick(MinecraftServer server) {
    State state = state(server);
    long now = server.overworld().getGameTime();
    if (!state.scheduled.isEmpty()) {
      List<Scheduled> due = new ArrayList<>();
      state.scheduled.removeIf(task -> {
        if (task.tick() > now) return false;
        due.add(task);
        return true;
      });
      due.forEach(task -> task.task().run());
    }
    if (server.getTickCount() % LORE_INTERVAL == 0) visits(server);
  }

  // ---- Clicks -------------------------------------------------------------------------------

  public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
        || player instanceof FakePlayer || player.isSpectator()) return;
    BlockState clicked = level.getBlockState(event.getPos());
    if (clicked.getBlock() instanceof LeverBlock || clicked.is(net.minecraft.tags.BlockTags.BUTTONS))
      HeliodorCompass.context(player).ifPresent(context -> credit(level, event.getPos(), context));
    Node node = node(level, event.getPos());
    if (node == null) return;
    var kind = node.marker().marker().kind();
    boolean ours = switch (kind) {
      case BRAZIER, MIRROR, SOCKET, HIDDEN, PEDESTAL, VITRAL, LIGHT -> true;
      default -> false;
    };
    if (kind == RuinMarkers.Kind.LEVER && event.getHand() == InteractionHand.MAIN_HAND) {
      // The lever toggles as usual (protection lets anyone use it); the lock is read a tick later.
      HeliodorCompass.context(player).ifPresent(context -> {
        state(level.getServer()).credits.put(node.ruin().id(),
            new Credit(context.campaignId(), context.founder(), level.getGameTime()));
        schedule(level.getServer(), 1, () -> levers(level, node.ruin(), node.marker().marker().challenge(), context));
      });
      return;
    }
    if (!ours) {
      if (kind == RuinMarkers.Kind.LOCK || kind == RuinMarkers.Kind.RECEPTOR || kind == RuinMarkers.Kind.LAMP) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
      }
      return;
    }
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
    if (event.getHand() != InteractionHand.MAIN_HAND) return;
    var context = HeliodorCompass.context(player).orElse(null);
    if (context == null) return;
    use(level, node, player, context);
  }

  /** What a team member's click on a node does. */
  static void use(ServerLevel level, Node node, ServerPlayer player, HeliodorCompass.Context context) {
    var kind = node.marker().marker().kind();
    if (kind == RuinMarkers.Kind.PEDESTAL) {
      pedestal(level, node.ruin(), player, context);
      return;
    }
    var definition = RuinRegistry.get(node.ruin().id()).orElse(null);
    if (definition == null) return;
    String challengeId = node.marker().marker().challenge();
    var challenge = definition.challenges().get(challengeId);
    if (challenge == null) return;
    var progress = RuinProgress.get(level.getServer());
    var team = progress.team(context.campaignId(), context.founder());
    String key = RuinProgress.key(definition.id(), challengeId);
    if (challenge.type() == RuinDefinitions.ChallengeType.RELAY) {
      if (team.solved.contains(key)) {
        player.displayClientMessage(Component.translatable("entrelumen.ruin.done"), true);
        return;
      }
      if (!RuinRules.ready(keys(definition, challenge.requires()), team.solved)) {
        player.displayClientMessage(Component.translatable("entrelumen.ruin.not_ready"), true);
        return;
      }
      RuinRelay.use(level, node, player, context, definition, challengeId);
      return;
    }
    if (kind == RuinMarkers.Kind.MIRROR) {
      mirror(level, node, definition, challengeId, player, context, team.solved.contains(key)
          || !RuinRules.ready(keys(definition, challenge.requires()), team.solved));
      return;
    }
    if (team.solved.contains(key)) {
      show(level, node.ruin(), definition, challengeId, team);
      player.displayClientMessage(Component.translatable("entrelumen.ruin.done"), true);
      return;
    }
    if (!RuinRules.ready(keys(definition, challenge.requires()), team.solved)) {
      player.displayClientMessage(Component.translatable("entrelumen.ruin.not_ready"), true);
      return;
    }
    switch (kind) {
      case BRAZIER -> brazier(level, node, definition, challengeId, player, context, team);
      case SOCKET -> socket(level, node, definition, challenge, player, context, team);
      case HIDDEN -> {
        sound(level, node.marker().pos(), SoundEvents.STONE_BUTTON_CLICK_ON, 1.2f);
        solve(level, node.ruin(), definition, challengeId, context.campaignId(), context.founder());
      }
      default -> {}
    }
  }

  static List<String> keys(RuinDefinitions.Definition definition, List<String> challenges) {
    return challenges.stream().map(challenge -> RuinProgress.key(definition.id(), challenge)).toList();
  }

  private static void brazier(ServerLevel level, Node node, RuinDefinitions.Definition definition, String challenge,
      ServerPlayer player, HeliodorCompass.Context context, RuinProgress.Team team) {
    var nodes = node.ruin().markers(RuinMarkers.Kind.BRAZIER, challenge);
    int[] orders = nodes.stream().mapToInt(marker -> marker.marker().order()).toArray();
    int index = nodes.indexOf(node.marker());
    String key = RuinProgress.key(definition.id(), challenge);
    Set<Integer> lit = team.lit(key);
    var step = RuinRules.light(orders, lit, index);
    RuinProgress.get(level.getServer()).setDirty();
    BlockPos pos = node.marker().pos();
    switch (step) {
      case LIT -> {
        sound(level, pos, SoundEvents.FLINTANDSTEEL_USE, 1f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
      }
      case RESET -> {
        sound(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.8f);
        player.displayClientMessage(Component.translatable("entrelumen.ruin.reset"), true);
      }
      case AGAIN -> {}
    }
    show(level, node.ruin(), definition, challenge, team);
    if (RuinRules.allLit(orders, lit)) solve(level, node.ruin(), definition, challenge, context.campaignId(), context.founder());
  }

  private static void socket(ServerLevel level, Node node, RuinDefinitions.Definition definition,
      RuinDefinitions.Challenge challenge, ServerPlayer player, HeliodorCompass.Context context, RuinProgress.Team team) {
    var sockets = node.ruin().markers(RuinMarkers.Kind.SOCKET, challenge.id());
    int index = sockets.indexOf(node.marker());
    String key = RuinProgress.key(definition.id(), challenge.id());
    Set<Integer> filled = team.filled(key);
    if (filled.contains(index)) {
      player.displayClientMessage(Component.translatable("entrelumen.ruin.socket.again"), true);
      return;
    }
    var marker = node.marker().marker();
    String wanted = marker.param("item", challenge.item());
    int count = marker.params().containsKey("count") ? marker.count() : challenge.count();
    ItemStack held = player.getMainHandItem();
    if (held.isEmpty() || !accepts(wanted, held) || held.getCount() < count) {
      player.displayClientMessage(Component.translatable("entrelumen.ruin.socket.wants", count, wantedName(wanted)), true);
      return;
    }
    if (!player.getAbilities().instabuild) held.shrink(count);
    filled.add(index);
    RuinProgress.get(level.getServer()).setDirty();
    sound(level, node.marker().pos(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f);
    show(level, node.ruin(), definition, challenge.id(), team);
    if (RuinRules.offered(sockets.size(), challenge.need(), filled))
      solve(level, node.ruin(), definition, challenge.id(), context.campaignId(), context.founder());
  }

  static boolean accepts(String wanted, ItemStack stack) {
    if (wanted.isEmpty()) return true;
    if (wanted.startsWith("#"))
      return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(wanted.substring(1))));
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wanted);
  }

  private static Component wantedName(String wanted) {
    if (wanted.isEmpty()) return Component.translatable("entrelumen.ruin.socket.anything");
    if (wanted.startsWith("#")) {
      var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(wanted.substring(1)));
      var first = BuiltInRegistries.ITEM.getTag(tag).flatMap(set -> set.stream().findFirst());
      return first.map(item -> Component.translatable("entrelumen.ruin.socket.like", new ItemStack(item.value()).getHoverName()))
          .orElse(Component.literal(wanted));
    }
    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(wanted));
    return new ItemStack(item).getHoverName();
  }

  private static void mirror(ServerLevel level, Node node, RuinDefinitions.Definition definition, String challenge,
      ServerPlayer player, HeliodorCompass.Context context, boolean idle) {
    BlockPos pos = node.marker().pos();
    BlockState state = level.getBlockState(pos);
    if (!state.is(RuinContent.MIRROR.get())) return;
    int aim = RuinMarkers.turn(state.getValue(RuinBlocks.AIM));
    level.setBlock(pos, state.setValue(RuinBlocks.AIM, aim), Block.UPDATE_CLIENTS);
    sound(level, pos, SoundEvents.SPYGLASS_USE, 1.3f);
    var receptor = node.ruin().markers(RuinMarkers.Kind.RECEPTOR, challenge).stream().findFirst().orElse(null);
    if (receptor == null) return;
    var mirrors = node.ruin().markers(RuinMarkers.Kind.MIRROR, challenge);
    boolean all = true;
    for (var mirror : mirrors) {
      BlockState current = level.getBlockState(mirror.pos());
      boolean aimed = current.is(RuinContent.MIRROR.get()) && RuinMarkers.aimed(current.getValue(RuinBlocks.AIM),
          mirror.pos().getX(), mirror.pos().getZ(), receptor.pos().getX(), receptor.pos().getZ());
      if (aimed && mirror.pos().equals(pos)) beam(level, pos, receptor.pos());
      all &= aimed;
    }
    if (!aimed(level, pos, receptor.pos())) ray(level, pos, aim);
    if (!all) return;
    for (var mirror : mirrors) beam(level, mirror.pos(), receptor.pos());
    sound(level, receptor.pos(), SoundEvents.BEACON_ACTIVATE, 1.2f);
    if (!idle) solve(level, node.ruin(), definition, challenge, context.campaignId(), context.founder());
    else if (RuinProgress.get(level.getServer()).team(context.campaignId(), context.founder()).solved
        .contains(RuinProgress.key(definition.id(), challenge)))
      player.displayClientMessage(Component.translatable("entrelumen.ruin.done"), true);
    else player.displayClientMessage(Component.translatable("entrelumen.ruin.not_ready"), true);
    // The next team finds the mirrors turned away again.
    String reset = node.ruin().id() + "|mirrors|" + challenge;
    if (state(level.getServer()).pendingResets.add(reset))
      schedule(level.getServer(), MIRROR_RESET_TICKS, () -> {
        state(level.getServer()).pendingResets.remove(reset);
        for (var mirror : mirrors) {
          BlockState current = level.getBlockState(mirror.pos());
          if (current.is(RuinContent.MIRROR.get()))
            level.setBlock(mirror.pos(), current.setValue(RuinBlocks.AIM,
                RuinMarkers.DIRECTIONS.indexOf(mirror.marker().param("facing", "n"))), Block.UPDATE_CLIENTS);
        }
      });
  }

  private static boolean aimed(ServerLevel level, BlockPos mirror, BlockPos receptor) {
    BlockState state = level.getBlockState(mirror);
    return state.is(RuinContent.MIRROR.get()) && RuinMarkers.aimed(state.getValue(RuinBlocks.AIM), mirror.getX(),
        mirror.getZ(), receptor.getX(), receptor.getZ());
  }

  /** Where a mirror sends the light now: a short ray of particles. */
  private static void ray(ServerLevel level, BlockPos from, int aim) {
    for (int i = 1; i <= 12; i++)
      level.sendParticles(ParticleTypes.END_ROD, from.getX() + 0.5 + RuinMarkers.DX[aim] * i * 0.75,
          from.getY() + 0.6, from.getZ() + 0.5 + RuinMarkers.DZ[aim] * i * 0.75, 1, 0, 0, 0, 0);
  }

  /** The light running from a mirror to its receptor. */
  private static void beam(ServerLevel level, BlockPos from, BlockPos to) {
    Vec3 a = Vec3.atCenterOf(from), b = Vec3.atCenterOf(to);
    int steps = (int) Math.ceil(a.distanceTo(b) * 1.5);
    for (int i = 0; i <= steps; i++) {
      Vec3 at = a.lerp(b, i / (double) Math.max(1, steps));
      level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
  }

  /** A redstone lock of levers, read a tick after one of them moved. */
  static void levers(ServerLevel level, RuinData.Ruin ruin, String challenge, HeliodorCompass.Context context) {
    var definition = RuinRegistry.get(ruin.id()).orElse(null);
    if (definition == null || !definition.challenges().containsKey(challenge)) return;
    var levers = ruin.markers(RuinMarkers.Kind.LEVER, challenge);
    boolean[] answer = new boolean[levers.size()], powered = new boolean[levers.size()];
    for (int i = 0; i < levers.size(); i++) {
      answer[i] = !"false".equals(levers.get(i).marker().param("on", "true"));
      BlockState state = level.getBlockState(levers.get(i).pos());
      powered[i] = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }
    if (!RuinRules.unlocked(answer, powered)) return;
    var team = RuinProgress.get(level.getServer()).team(context.campaignId(), context.founder());
    var requires = keys(definition, definition.challenges().get(challenge).requires());
    if (RuinRules.ready(requires, team.solved)) solve(level, ruin, definition, challenge, context.campaignId(), context.founder());
    else context.player().displayClientMessage(Component.translatable("entrelumen.ruin.not_ready"), true);
    resetLevers(level, ruin, challenge, RESET_TICKS);
  }

  /** Puts every lever of a lock back as the template had it, after {@code delay} ticks. */
  static void resetLevers(ServerLevel level, RuinData.Ruin ruin, String challenge, int delay) {
    String reset = ruin.id() + "|levers|" + challenge;
    if (!state(level.getServer()).pendingResets.add(reset)) return;
    schedule(level.getServer(), delay, () -> {
      state(level.getServer()).pendingResets.remove(reset);
      for (var lever : ruin.markers(RuinMarkers.Kind.LEVER, challenge)) {
        BlockState current = level.getBlockState(lever.pos());
        BlockState initial = RuinPlacement.blockOf(lever.marker());
        if (!(current.getBlock() instanceof LeverBlock)) continue;
        boolean off = initial == null || !initial.hasProperty(BlockStateProperties.POWERED)
            || !initial.getValue(BlockStateProperties.POWERED);
        if (current.getValue(BlockStateProperties.POWERED) == off)
          level.setBlock(lever.pos(), current.setValue(BlockStateProperties.POWERED, !off), Block.UPDATE_ALL);
      }
    });
  }

  /** A lock core received power: credit the team that last worked a lever or button of the ruin. */
  static void lockPowered(ServerLevel level, BlockPos pos) {
    Node node = node(level, pos);
    if (node == null || node.marker().marker().kind() != RuinMarkers.Kind.LOCK) return;
    var definition = RuinRegistry.get(node.ruin().id()).orElse(null);
    String challenge = node.marker().marker().challenge();
    if (definition == null || !definition.challenges().containsKey(challenge)) return;
    Credit credit = state(level.getServer()).credits.get(node.ruin().id());
    UUID campaign = null, founder = null;
    if (credit != null && level.getGameTime() - credit.tick() <= CREDIT_TICKS) {
      campaign = credit.campaign();
      founder = credit.founder();
    } else {
      ServerPlayer nearest = null;
      for (ServerPlayer player : level.players())
        if (node.ruin().box().isInside(player.blockPosition())
            && (nearest == null || player.distanceToSqr(Vec3.atCenterOf(pos)) < nearest.distanceToSqr(Vec3.atCenterOf(pos))))
          nearest = player;
      var context = nearest == null ? null : HeliodorCompass.context(nearest).orElse(null);
      if (context != null) {
        campaign = context.campaignId();
        founder = context.founder();
      }
    }
    if (campaign == null) return;
    var team = RuinProgress.get(level.getServer()).team(campaign, founder);
    if (RuinRules.ready(keys(definition, definition.challenges().get(challenge).requires()), team.solved))
      solve(level, node.ruin(), definition, challenge, campaign, founder);
  }

  /** Records a lever or button used anywhere in a ruin, for its redstone locks and its engine. */
  static void credit(ServerLevel level, BlockPos pos, HeliodorCompass.Context context) {
    for (var ruin : RuinData.get(level.getServer()).ruins())
      if (ruin.contains(level.dimension(), pos))
        state(level.getServer()).credits.put(ruin.id(), new Credit(context.campaignId(), context.founder(), level.getGameTime()));
  }

  /** The team that last worked a ruin within {@code maxAge} ticks, or null. */
  @Nullable
  static Credit lastCredit(MinecraftServer server, ResourceLocation ruin, int maxAge) {
    Credit credit = state(server).credits.get(ruin);
    return credit != null && server.overworld().getGameTime() - credit.tick() <= maxAge ? credit : null;
  }

  // ---- Solving --------------------------------------------------------------------------------

  /** Records a solved challenge for a campaign; the world and the online team hear about it once. */
  static boolean solve(ServerLevel level, RuinData.Ruin ruin, RuinDefinitions.Definition definition, String challenge,
      UUID campaign, @Nullable UUID founder) {
    var progress = RuinProgress.get(level.getServer());
    var team = progress.team(campaign, founder);
    if (!team.solved.add(RuinProgress.key(definition.id(), challenge))) return false;
    progress.setDirty();
    for (var lamp : ruin.markers(RuinMarkers.Kind.LAMP, challenge)) lit(level, lamp.pos(), true);
    for (var drain : ruin.markers(RuinMarkers.Kind.DRAIN, challenge)) drain(level, drain);
    var members = members(level.getServer(), campaign);
    for (ServerPlayer member : members) {
      member.displayClientMessage(Component.translatable("entrelumen.ruin.solved").withStyle(ChatFormatting.GOLD), true);
      RuinGates.refresh(member);
    }
    level.playSound(null, ruin.box().getCenter(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, 1f);
    return true;
  }

  /** Online players whose campaign is {@code campaign}. */
  static List<ServerPlayer> members(MinecraftServer server, UUID campaign) {
    List<ServerPlayer> members = new ArrayList<>();
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      try {
        if (CampaignActions.campaignId(player).equals(campaign)) members.add(player);
      } catch (RuntimeException noTeam) {
        // Not in a team yet.
      }
    }
    return members;
  }

  /** Empties the water of a drain box (the pit stays dry: it is the world's, not a team's). */
  private static void drain(ServerLevel level, RuinData.PlacedMarker drain) {
    int[] size = drain.marker().size();
    var cursor = new BlockPos.MutableBlockPos();
    for (int y = size[1] - 1; y >= 0; y--)
      for (int x = 0; x < size[0]; x++)
        for (int z = 0; z < size[2]; z++) {
          cursor.set(drain.pos().getX() + x, drain.pos().getY() + y, drain.pos().getZ() + z);
          BlockState state = level.getBlockState(cursor);
          if (state.getFluidState().is(Fluids.WATER)) {
            if (state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.BUBBLE_COLUMN
                || state.getBlock() == Blocks.SEAGRASS || state.getBlock() == Blocks.TALL_SEAGRASS
                || state.getBlock() == Blocks.KELP || state.getBlock() == Blocks.KELP_PLANT)
              level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            else if (state.hasProperty(BlockStateProperties.WATERLOGGED))
              level.setBlock(cursor, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
          }
        }
    sound(level, drain.pos(), SoundEvents.BUCKET_EMPTY, 0.6f);
  }

  /** Shows a challenge's nodes as one team left them: lit braziers, filled sockets, lamps. */
  static void show(ServerLevel level, RuinData.Ruin ruin, RuinDefinitions.Definition definition, String challenge,
      RuinProgress.Team team) {
    String key = RuinProgress.key(definition.id(), challenge);
    boolean solved = team.solved.contains(key);
    var braziers = ruin.markers(RuinMarkers.Kind.BRAZIER, challenge);
    Set<Integer> lit = team.lit.getOrDefault(key, Set.of());
    for (int i = 0; i < braziers.size(); i++) lit(level, braziers.get(i).pos(), solved || lit.contains(i));
    var sockets = ruin.markers(RuinMarkers.Kind.SOCKET, challenge);
    Set<Integer> filled = team.filled.getOrDefault(key, Set.of());
    for (int i = 0; i < sockets.size(); i++) {
      BlockState state = level.getBlockState(sockets.get(i).pos());
      if (state.is(RuinContent.SOCKET.get()) && state.getValue(RuinBlocks.FILLED) != filled.contains(i))
        level.setBlock(sockets.get(i).pos(), state.setValue(RuinBlocks.FILLED, filled.contains(i)), Block.UPDATE_CLIENTS);
    }
    for (var lamp : ruin.markers(RuinMarkers.Kind.LAMP, challenge)) lit(level, lamp.pos(), solved);
  }

  private static void lit(ServerLevel level, BlockPos pos, boolean value) {
    BlockState state = level.getBlockState(pos);
    if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != value)
      level.setBlock(pos, state.setValue(BlockStateProperties.LIT, value), Block.UPDATE_CLIENTS);
  }

  private static void sound(ServerLevel level, BlockPos pos, SoundEvent sound, float pitch) {
    level.playSound(null, pos, sound, SoundSource.BLOCKS, 1f, pitch);
  }

  // ---- The key pedestal --------------------------------------------------------------------------

  /**
   * The pieces this ruin's pedestal holds: its own, plus those of the same act's ruins skipped for a
   * missing mod when this ruin is the act's landmark.
   */
  static List<RuinDefinitions.Definition> pieces(RuinDefinitions.Definition definition) {
    List<RuinDefinitions.Definition> pieces = new ArrayList<>();
    pieces.add(definition);
    pieces.addAll(RuinDefinitions.fallbacks(RuinRegistry.all().values(), RuinRegistry::modLoaded)
        .getOrDefault(definition.id(), List.of()));
    return pieces;
  }

  static void pedestal(ServerLevel level, RuinData.Ruin ruin, ServerPlayer player, HeliodorCompass.Context context) {
    var definition = RuinRegistry.get(ruin.id()).orElse(null);
    if (definition == null) {
      player.displayClientMessage(Component.translatable("entrelumen.ruin.pedestal.empty"), true);
      return;
    }
    var progress = RuinProgress.get(level.getServer());
    var team = progress.team(context.campaignId(), context.founder());
    boolean solved = team.solved.containsAll(keys(definition, definition.pedestal()));
    List<Component> lines = new ArrayList<>();
    for (var piece : pieces(definition)) {
      Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(piece.piece()));
      Component name = new ItemStack(item).getHoverName();
      int generation = team.piece(piece.id());
      boolean delivered = CampaignMilestones.isComplete(context.campaign(), piece.project());
      boolean held = generation > 0 && KeyPieces.held(context.members(), item, context.campaignId(), piece.id(), generation);
      switch (RuinRules.grant(solved, delivered, generation, held)) {
        case GIVE -> {
          team.pieces.put(piece.id(), generation + 1);
          progress.setDirty();
          ItemStack stack = KeyPieces.bound(item, context.campaignId(), piece.id(), generation + 1);
          if (!player.getInventory().add(stack)) player.drop(stack, false);
          if (piece == definition) RuinWorkshop.claimed(level, ruin);
          lines.add(Component.translatable(generation == 0 ? "entrelumen.ruin.pedestal.given"
              : "entrelumen.ruin.pedestal.again", name));
          level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
        }
        case HELD -> lines.add(Component.translatable("entrelumen.ruin.pedestal.held", name));
        case DELIVERED -> lines.add(Component.translatable("entrelumen.ruin.pedestal.delivered", name));
        case LOCKED -> {
          if (lines.isEmpty()) lines.add(Component.translatable("entrelumen.ruin.pedestal.locked"));
        }
      }
    }
    // Gifts wait on the pedestal too (the Signal Tower's Atlas): one for a player who carries none. The
    // team has taken it from here, which opens its recipe (AtlasGate).
    if (solved)
      for (String gift : definition.gifts()) {
        if (team.gifts.add(gift)) progress.setDirty();
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(gift));
        if (item == net.minecraft.world.item.Items.AIR || player.getInventory().countItem(item) > 0) continue;
        ItemStack stack = new ItemStack(item);
        Component name = stack.getHoverName();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        lines.add(Component.translatable("entrelumen.ruin.pedestal.gift", name));
      }
    lines.stream().distinct().forEach(line -> player.displayClientMessage(line, true));
    if (lines.size() > 1) lines.forEach(player::sendSystemMessage);
  }

  // ---- Visits and lore ----------------------------------------------------------------------

  /** The first team member inside a ruin's lore volume records the visit and hears its lore. */
  static void visits(MinecraftServer server) {
    var data = RuinData.get(server);
    if (data.ruins().stream().noneMatch(ruin -> !ruin.markers().isEmpty())) return;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      if (player.isSpectator() || player instanceof FakePlayer) continue;
      BlockPos here = player.blockPosition();
      for (var ruin : data.ruins()) {
        if (ruin.markers().isEmpty() || !ruin.contains(player.level().dimension(), here)) continue;
        if (!inLore(ruin, here)) continue;
        var context = HeliodorCompass.context(player).orElse(null);
        if (context == null) continue;
        var progress = RuinProgress.get(server);
        var team = progress.team(context.campaignId(), context.founder());
        if (!team.visited.add(ruin.id().toString())) continue;
        progress.setDirty();
        String name = ruin.id().getPath();
        for (ServerPlayer member : context.members()) {
          member.sendSystemMessage(Component.translatable("entrelumen.ruin.visited",
              Component.translatable("entrelumen.compass.objective." + name)).withStyle(ChatFormatting.GOLD));
          member.sendSystemMessage(Component.translatable("entrelumen.compass.lore." + name)
              .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
      }
    }
  }

  static boolean inLore(RuinData.Ruin ruin, BlockPos here) {
    var lore = ruin.markers(RuinMarkers.Kind.LORE);
    if (lore.isEmpty()) return true;
    for (var marker : lore) {
      int radius = Integer.parseInt(marker.marker().param("radius", "6"));
      int height = Integer.parseInt(marker.marker().param("height", "5"));
      BlockPos at = marker.pos();
      if (Math.abs(here.getX() - at.getX()) <= radius && Math.abs(here.getZ() - at.getZ()) <= radius
          && here.getY() >= at.getY() - 1 && here.getY() < at.getY() + height) return true;
    }
    return false;
  }
}
