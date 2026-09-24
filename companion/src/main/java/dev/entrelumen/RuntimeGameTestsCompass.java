package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Isolated GameTests for the start of the game: empty inventory, the start ruin, the pedestal and
 * the Heliodor compass. Development-only; excluded from the distributable jar.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsCompass {
  private static final Logger LOGGER = LogUtils.getLogger();

  private RuntimeGameTestsCompass() {}

  /** A logged-in player whose inventory is left exactly as the login produced it. */
  private static ServerPlayer arrive(GameTestHelper helper, String name) {
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new GameProfile(UUID.randomUUID(), name), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(
        net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    return player;
  }

  private static boolean emptyInventory(ServerPlayer player) {
    var inventory = player.getInventory();
    return inventory.items.stream().allMatch(ItemStack::isEmpty)
        && inventory.armor.stream().allMatch(ItemStack::isEmpty)
        && inventory.offhand.stream().allMatch(ItemStack::isEmpty);
  }

  private static int compasses(ServerPlayer player) {
    int count = 0;
    for (var stack : player.getInventory().items)
      if (stack.is(HeliodorContent.COMPASS.get())) count += stack.getCount();
    return count;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void newPlayerArrivesWithAnEmptyInventory(GameTestHelper helper) {
    var player = arrive(helper, "EmptyHands");
    helper.assertTrue(emptyInventory(player), "Login handed the player items");
    // Only the isolated server must stay without a start ruin: a real server (the full-pack QA
    // run) places it on its first start by design.
    helper.assertTrue(!(player.server instanceof GameTestServer)
            || RuinData.get(player.server).find(HeliodorRuins.START).isEmpty(),
        "The isolated GameTest world must never place the start ruin implicitly");
    // Tick-triggered advancements (a common gift route) fire on the player's first ticks.
    helper.runAfterDelay(10, () -> {
      helper.assertTrue(emptyInventory(player), "A tick after login handed the player items");
      helper.assertTrue(!CompassData.get(player.server).claimed.contains(player.getUUID()),
          "Arriving must not claim the compass: it waits on the pedestal");
      helper.succeed();
    });
  }

  /** Keeps the far test site generated while the start-ruin case waits for it; expires if abandoned. */
  private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> RUIN_SITE =
      net.minecraft.server.level.TicketType.create("entrelumen_qa_ruin_site",
          Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 1200);

  @GameTest(template = "empty", timeoutTicks = 3600)
  public static void startRuinIsAnchoredRegisteredAndPlacedOnce(GameTestHelper helper) {
    var level = helper.getLevel();
    var template = level.getStructureManager().get(HeliodorRuins.START).orElseThrow();
    var size = template.getSize();
    // Fresh terrain far from the test grid; the world registry stays untouched.
    var near = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 0);
    // The site search reads every column within its radius. On the superflat world those chunks
    // cost nothing; on a full-pack noise world they are ~120 fresh chunks, far more than one
    // server tick may generate (a 60 s watchdog stop). A real start has its spawn area generated
    // before the ruin is placed, so the case asks worldgen for the area through a ticket first and
    // places the ruin once every chunk is ready.
    int reach = HeliodorRuins.SEARCH_RADIUS + Math.max(size.getX(), size.getZ()) / 2 + 2;
    int radius = (reach + 15) / 16;
    var site = new net.minecraft.world.level.ChunkPos(near);
    Runnable hold = () -> level.getChunkSource().addRegionTicket(RUIN_SITE, site, radius, site);
    hold.run();
    helper.startSequence()
        .thenWaitUntil(() -> {
          hold.run();
          for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
              helper.assertTrue(level.getChunkSource().getChunkNow(site.x + dx, site.z + dz) != null,
                  "Waiting for the ruin test site to generate");
        })
        .thenExecute(() -> {
          try {
            placeStartRuin(helper, level, size, near);
          } finally {
            level.getChunkSource().removeRegionTicket(RUIN_SITE, site, radius, site);
          }
        })
        .thenSucceed();
  }

  private static void placeStartRuin(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
      net.minecraft.core.Vec3i size, BlockPos near) {
    int x0 = near.getX() - size.getX() / 2, z0 = near.getZ() - size.getZ() / 2;
    // Load the column that is sampled: an unloaded one reads as the bottom of the world.
    level.getChunk((x0 + 1) >> 4, (z0 + 1) >> 4);
    int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        x0 + 1, z0 + 1) - 1;
    // A two-deep dip under the footprint must be filled by the foundation.
    level.setBlockAndUpdate(new BlockPos(x0 + 1, ground, z0 + 1), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(new BlockPos(x0 + 1, ground - 1, z0 + 1), Blocks.AIR.defaultBlockState());
    var data = new RuinData();
    data.pendingStart = true;
    var oldSpawn = level.getSharedSpawnPos();
    float oldAngle = level.getSharedSpawnAngle();
    RuinData.Ruin ruin;
    try {
      ruin = HeliodorRuins.place(level, data, near, true).orElseThrow();
      helper.assertTrue(level.getSharedSpawnPos().equals(ruin.arrival()),
          "World spawn was not moved beside the ruin");
    } finally {
      level.setDefaultSpawnPos(oldSpawn, oldAngle);
    }
    var origin = ruin.origin();
    helper.assertTrue(origin.equals(new BlockPos(x0, ground, z0)),
        "Template floor was not anchored at ground level: " + origin);
    helper.assertTrue(ruin.box().getXSpan() == size.getX() && ruin.box().getZSpan() == size.getZ()
        && ruin.box().maxY() == origin.getY() + size.getY() - 1,
        "Registered box does not match the template size " + size + ": " + ruin.box());
    helper.assertTrue(ruin.box().minY() <= ground - 1, "Foundation depth missing from the box");
    for (int x = 0; x < size.getX(); x++)
      for (int z = 0; z < size.getZ(); z++) {
        var below = origin.offset(x, -1, z);
        helper.assertTrue(!level.getBlockState(below).canBeReplaced(),
            "The ruin floats above " + below);
      }
    helper.assertTrue(ruin.pedestals().size() == 1
        && level.getBlockState(ruin.pedestals().getFirst()).is(HeliodorContent.PEDESTAL.get()),
        "The template pedestal was not found or registered");
    var arrival = ruin.arrival();
    boolean outside = arrival.getX() < ruin.box().minX() || arrival.getX() > ruin.box().maxX()
        || arrival.getZ() < ruin.box().minZ() || arrival.getZ() > ruin.box().maxZ();
    int gap = Math.max(Math.max(ruin.box().minX() - arrival.getX(), arrival.getX() - ruin.box().maxX()),
        Math.max(ruin.box().minZ() - arrival.getZ(), arrival.getZ() - ruin.box().maxZ()));
    helper.assertTrue(outside && gap == 1 && HeliodorRuins.standable(level, arrival),
        "Arrival point is not a safe spot beside the ruin: " + arrival);

    // Once per world: a second call returns the record and never rebuilds.
    level.setBlockAndUpdate(origin, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    var again = HeliodorRuins.place(level, data, near.offset(40, 0, 40), true).orElseThrow();
    helper.assertTrue(again.equals(ruin) && data.ruins().size() == 1
        && level.getBlockState(origin).is(Blocks.MOSSY_COBBLESTONE),
        "The start ruin was placed twice");
    helper.assertTrue(level.getSharedSpawnPos().equals(oldSpawn), "The spawn moved on a repeat call");

    // Persistent: the registry survives a save/load with its box and ID.
    var reloaded = RuinData.load(data.save(new CompoundTag(), level.registryAccess()),
        level.registryAccess());
    helper.assertTrue(reloaded.ruins().equals(data.ruins()) && !reloaded.pendingStart(),
        "Ruin registry did not survive a reload");

    // Brand-new players appear at the arrival point; returning players are left alone.
    var newcomer = arrive(helper, "Newcomer");
    helper.assertTrue(HeliodorRuins.welcome(newcomer, ruin)
        && newcomer.blockPosition().equals(arrival), "A new player did not appear beside the ruin");
    var veteran = arrive(helper, "Veteran");
    veteran.getStats().setValue(veteran, Stats.CUSTOM.get(Stats.PLAY_TIME), 200);
    var before = veteran.blockPosition();
    helper.assertTrue(!HeliodorRuins.welcome(veteran, ruin) && veteran.blockPosition().equals(before),
        "A returning player was moved to the ruin");
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void pedestalGivesEachPlayerOneCompassEver(GameTestHelper helper) {
    var pedestal = helper.absolutePos(new BlockPos(2, 1, 2));
    helper.getLevel().setBlockAndUpdate(pedestal, HeliodorContent.PEDESTAL.get().defaultBlockState());
    helper.assertTrue(helper.getLevel().getBlockState(pedestal)
        .getDestroySpeed(helper.getLevel(), pedestal) < 0, "The pedestal can be broken");
    var first = arrive(helper, "FirstFinder");
    var second = arrive(helper, "SecondFinder");
    var hit = new BlockHitResult(Vec3.atCenterOf(pedestal), Direction.UP, pedestal, false);
    for (var player : List.of(first, second)) {
      player.getInventory().clearContent();
      player.teleportTo(pedestal.getX() + 0.5, pedestal.getY() + 1, pedestal.getZ() + 1.5);
    }
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The first player did not receive a compass");
    var stack = first.getInventory().items.stream()
        .filter(s -> s.is(HeliodorContent.COMPASS.get())).findFirst().orElseThrow();
    helper.assertTrue(stack.has(HeliodorContent.COMPASS_STATE.get()), "The compass arrived unattuned");
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "The pedestal duplicated a compass");
    // Holding something does not bypass the claim (the compass sits in another slot).
    first.getInventory().selected = 8;
    first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
    first.gameMode.useItemOn(first, helper.getLevel(), first.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 1, "An item in hand bypassed the one-per-player rule");
    second.gameMode.useItemOn(second, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(second) == 1, "A later player could not take their own compass");
    // One per UUID ever: losing it does not reopen the pedestal.
    first.getInventory().clearContent();
    first.gameMode.useItemOn(first, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
    helper.assertTrue(compasses(first) == 0, "A lost compass was replaced by the pedestal");
    var data = CompassData.get(first.server);
    var reloaded = CompassData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
        helper.getLevel().registryAccess());
    helper.assertTrue(reloaded.claimed.contains(first.getUUID()) && reloaded.claimed.contains(second.getUUID()),
        "Claims did not survive a reload");
    helper.succeed();
  }

  private static CompassTargets.Objective objective(String id, int act, CompassTargets.Kind kind,
      CompassTargets.Target target, CompassTargets.Condition condition) {
    return new CompassTargets.Objective(id, act, kind, target, condition, true);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void compassMovesToTheNextObjectiveWhenTheTeamMeetsTheCondition(GameTestHelper helper)
      throws Exception {
    var founder = arrive(helper, "CompassLead");
    var guest = arrive(helper, "CompassGuest");
    founder.getInventory().clearContent();
    guest.getInventory().clearContent();
    var marker = helper.absolutePos(new BlockPos(2, 1, 2));
    var list = List.of(
        objective("qa_marker", 1, CompassTargets.Kind.ARTIFACT,
            new CompassTargets.Target(CompassTargets.TargetType.POSITION, "", "minecraft:overworld", marker, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.ITEM, "minecraft:amethyst_shard", 2)),
        objective("qa_end", 1, CompassTargets.Kind.BOSS,
            new CompassTargets.Target(CompassTargets.TargetType.DIMENSION, "", "minecraft:the_end", null, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, "qa_compass_gate", 1)),
        objective("qa_act_two", 2, CompassTargets.Kind.STRUCTURE,
            new CompassTargets.Target(CompassTargets.TargetType.POSITION, "", "minecraft:overworld", marker, 0),
            new CompassTargets.Condition(CompassTargets.ConditionType.MILESTONE, "qa_never", 1)));

    var state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_marker") && state.state() == CompassState.POINTING
        && state.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, marker)))
        && state.kind() == 2 && state.dimension() == 0, "First objective not shown: " + state);
    founder.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 1));
    helper.assertTrue(HeliodorCompass.compute(founder, list).objective().equals("qa_marker"),
        "Advanced before the full key-item count");
    founder.getInventory().add(new ItemStack(Items.AMETHYST_SHARD, 1));
    state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_end") && state.state() == CompassState.ELSEWHERE
        && state.dimension() == 2 && state.kind() == 1 && !state.spinning(),
        "Meeting the condition did not move the compass: " + state);
    founder.getInventory().clearContent();
    helper.assertTrue(HeliodorCompass.compute(founder, list).objective().equals("qa_end"),
        "Dropping the key item rewound the compass");

    // A party starts from its founder's progress, and every member follows the same objective.
    var team = FTBTeamsAPI.api().getManager().createPartyTeam(founder, "Compass " + founder.getUUID(),
        "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    ((dev.ftb.mods.ftbteams.data.PartyTeam) team).invite(founder, List.of(guest.getGameProfile()));
    ((dev.ftb.mods.ftbteams.data.PartyTeam) team).join(guest);
    helper.assertTrue(HeliodorCompass.compute(guest, list).objective().equals("qa_end"),
        "A team member does not share the team objective");
    Entrelumen.current(guest).completed.add("qa_compass_gate");
    state = HeliodorCompass.compute(founder, list);
    helper.assertTrue(state.objective().equals("qa_act_two") && state.state() == CompassState.LOCKED
        && !state.spinning(), "A later act's objective was not held: " + state);
    Entrelumen.current(founder).act = 2;
    state = HeliodorCompass.compute(guest, list);
    helper.assertTrue(state.objective().equals("qa_act_two") && state.state() == CompassState.POINTING,
        "Opening the act did not reveal the next objective: " + state);
    helper.assertTrue(CompassData.get(founder.server).reached(team.getId(), null)
        .containsAll(List.of("qa_marker", "qa_end")), "Reached objectives were not persisted");

    // The shipped draft: the ruin anchor first, then the first campaign milestone moves it on.
    // The isolated world has no start ruin (NOT_FOUND); a full-pack server placed one at its
    // first start, and the needle then points at it. Either way it never spins.
    var solo = arrive(helper, "DraftReader");
    var draft = HeliodorCompass.compute(solo);
    var ruin = RuinData.get(solo.server).find(HeliodorRuins.START)
        .filter(r -> r.dimension().equals(Level.OVERWORLD)
            && CompassData.horizontalDistanceSqr(r.center(), solo.blockPosition())
                <= (long) CompassTargets.DEFAULT_RADIUS * CompassTargets.DEFAULT_RADIUS);
    boolean drafted = ruin.isPresent()
        ? draft.state() == CompassState.POINTING
            && draft.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, ruin.get().center())))
        : draft.state() == CompassState.NOT_FOUND;
    helper.assertTrue(draft.objective().equals("heliodor_ruin") && drafted && !draft.spinning(),
        "Draft does not start at the ruin, or spins without one: " + draft);
    Entrelumen.current(solo).completed.add("atlas_awakened");
    helper.assertTrue(HeliodorCompass.compute(solo).objective().equals("village_survey"),
        "Awakening the Atlas did not move the draft compass on");
    // The carried item follows the same state.
    var carried = new ItemStack(HeliodorContent.COMPASS.get());
    HeliodorCompass.refresh(solo, carried);
    var carriedState = carried.get(HeliodorContent.COMPASS_STATE.get());
    helper.assertTrue(carriedState != null && carriedState.objective().equals("village_survey"),
        "The item component does not mirror the objective");
    var view = HeliodorCompass.view(solo);
    helper.assertTrue(view.objective().equals("village_survey") && view.lore().equals(List.of("heliodor_ruin"))
        && view.dimension().equals("minecraft:overworld"), "Atlas view is wrong: " + view);
    var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
        helper.getLevel().registryAccess());
    try {
      var snapshot = AtlasNetwork.snapshot(solo, true, "");
      AtlasNetwork.Snapshot.CODEC.encode(buffer, snapshot);
      helper.assertTrue(AtlasNetwork.Snapshot.CODEC.decode(buffer).equals(snapshot),
          "The Atlas compass section does not survive the wire");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }

  /**
   * Measures bounded searches. On the isolated superflat world (structure starts disabled, cost
   * test bypassing that option) every predicted plains village is confirmed and rejected: the worst
   * case. On a full-pack server the same searches run on real terrain, where the village may exist.
   * Either way the search stays within the radius, never blocks a tick on worldgen and answers an
   * impossible target without searching. Numbers are logged for the design doc.
   */
  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void compassSearchesStayBoundedAndCooperative(GameTestHelper helper) {
    var level = helper.getLevel();
    var locator = CompassLocator.of(level.getServer());
    var origin = helper.absolutePos(BlockPos.ZERO).offset(0, 0, 8192);
    boolean flat = level.getChunkSource().getGenerator()
        instanceof net.minecraft.world.level.levelgen.FlatLevelSource;
    // The biome under the origin, sampled like BiomeJob does (plains on the superflat world).
    var sampled = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
        net.minecraft.core.QuartPos.fromBlock(origin.getX()),
        net.minecraft.core.QuartPos.fromBlock(origin.getY()),
        net.minecraft.core.QuartPos.fromBlock(origin.getZ()),
        level.getChunkSource().randomState().sampler());
    String underfoot = sampled.unwrapKey().orElseThrow().location().toString();
    long radiusSqr = (long) CompassTargets.MAX_RADIUS * CompassTargets.MAX_RADIUS;
    Map<String, CompassLocator.Outcome> outcomes = new LinkedHashMap<>();
    Map<String, CompassTargets.Target> targets = new LinkedHashMap<>();
    targets.put("village_plains", new CompassTargets.Target(CompassTargets.TargetType.STRUCTURE,
        "minecraft:village_plains", "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    // An End biome never occurs in an overworld biome source, flat or noise.
    targets.put("absent", new CompassTargets.Target(CompassTargets.TargetType.BIOME,
        "minecraft:the_end", "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    targets.put("underfoot", new CompassTargets.Target(CompassTargets.TargetType.BIOME,
        underfoot, "minecraft:overworld", null, CompassTargets.MAX_RADIUS));
    AtomicReference<String> failure = new AtomicReference<>();
    targets.forEach((name, target) -> {
      // Ignore the flat world's "no structures" option to measure the real scan cost.
      if (!locator.request("qa-cost-" + name + "-" + origin.toShortString(), level, target, origin, false,
          outcome -> outcomes.put(name, outcome))) failure.set("Search was not queued: " + name);
    });
    helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
    // A structure that cannot generate in this dimension (End cities need End biomes) costs no
    // search at all. The superflat world also lacks snowy villages, a full-pack overworld does not.
    var immediate = new AtomicReference<CompassLocator.Outcome>();
    boolean queued = locator.request("qa-cost-impossible-" + origin.toShortString(), level,
        new CompassTargets.Target(CompassTargets.TargetType.STRUCTURE, "minecraft:end_city",
            "minecraft:overworld", null, CompassTargets.MAX_RADIUS), origin, false, immediate::set);
    helper.assertTrue(!queued && immediate.get() != null && immediate.get().found().isEmpty(),
        "An impossible structure was searched");
    helper.succeedWhen(() -> {
      helper.assertTrue(outcomes.size() == targets.size(), "Searches still running");
      outcomes.forEach((name, outcome) -> LOGGER.info("COMPASS COST {} ({}): found={} {}", name,
          flat ? "superflat" : "full pack", outcome.found().map(BlockPos::toShortString).orElse("none"),
          outcome.cost()));
      var plains = outcomes.get("village_plains");
      if (flat)
        helper.assertTrue(plains.cost().candidates() > 0 && plains.found().isEmpty()
            && plains.cost().positions() == 189 * 189,
            "Plains villages must be predicted, never started, over the whole 1500-block square: "
                + plains.cost());
      else
        helper.assertTrue(plains.found().map(pos -> CompassData.horizontalDistanceSqr(origin, pos) <= radiusSqr)
                .orElse(plains.cost().positions() == 189 * 189),
            "A village search left the 1500-block radius or stopped early: " + plains.found() + " "
                + plains.cost());
      var absent = outcomes.get("absent");
      helper.assertTrue(absent.found().isEmpty() && absent.cost().positions() == 93 * 93,
          "An absent biome must sample the whole radius: " + absent.cost());
      var near = outcomes.get("underfoot");
      helper.assertTrue(near.found().isPresent() && near.cost().ticks() == 1,
          "The biome under the player must be found at once: " + underfoot + " " + near.cost());
      for (var outcome : outcomes.values())
        helper.assertTrue(outcome.cost().maxStepNanos() < 250_000_000L,
            "A single search step blocked the server tick: " + outcome.cost());
    });
  }
}
