package dev.entrelumen;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests of the Plan v2 ruins (docs/design/heliodor-ruins.md): templates, placement in the
 * Overworld and in dimensions, protection, every challenge type solved and reset per team, the
 * per-team gate, the pedestal's grant and re-grant, the bosses and the compass. Challenge cases
 * build a small fixture ruin inside their 5x5x5 test area; placement cases place real templates far
 * away, in their own batch, so their chunk generation never runs beside timing-sensitive cases.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsRuins {
  private RuntimeGameTestsRuins() {}

  static ServerPlayer arrive(GameTestHelper helper, String name, BlockPos relative) {
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new GameProfile(UUID.randomUUID(), name), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
        cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    player.getInventory().clearContent();
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    var pos = helper.absolutePos(relative);
    player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    return player;
  }

  static void leave(ServerPlayer player) {
    player.connection.disconnect(net.minecraft.network.chat.Component.literal("Ruin QA finished"));
  }

  static HeliodorCompass.Context context(ServerPlayer player) {
    return HeliodorCompass.context(player).orElseThrow();
  }

  static RuinProgress.Team team(ServerPlayer player) {
    var context = context(player);
    return RuinProgress.get(player.server).team(context.campaignId(), context.founder());
  }

  /** A fixture ruin: its definition and its record, with markers at test-relative cells. */
  static final class Fixture implements AutoCloseable {
    final GameTestHelper helper;
    final RuinDefinitions.Definition definition;
    final RuinData.Ruin ruin;

    Fixture(GameTestHelper helper, String json, List<Object[]> markers) {
      this.helper = helper;
      String id = "entrelumen:qa_" + UUID.randomUUID().toString().substring(0, 8);
      this.definition = RuinDefinitions.parse(id, JsonParser.parseString(json), RuinRegistry::modLoaded,
          item -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item)),
          entity -> BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(entity)));
      RuinRegistry.add(definition);
      List<RuinData.PlacedMarker> placed = new ArrayList<>();
      List<BlockPos> pedestals = new ArrayList<>();
      for (Object[] marker : markers) {
        var parsed = RuinMarkers.parse((String) marker[0]).orElseThrow();
        BlockPos pos = helper.absolutePos((BlockPos) marker[1]);
        RuinPlacement.placeMarker(helper.getLevel(), definition, parsed, pos);
        placed.add(new RuinData.PlacedMarker(parsed, pos));
        if (parsed.kind() == RuinMarkers.Kind.PEDESTAL) pedestals.add(pos);
      }
      // The empty template is 5x5x5: the fixture never reaches a neighbouring case.
      BlockPos low = helper.absolutePos(new BlockPos(0, 0, 0)), high = helper.absolutePos(new BlockPos(4, 4, 4));
      var box = BoundingBox.fromCorners(low, high);
      this.ruin = new RuinData.Ruin(ResourceLocation.parse(id), ResourceLocation.parse(definition.template()),
          helper.getLevel().dimension(), definition.act(), box, low, helper.absolutePos(new BlockPos(0, 1, 0)),
          pedestals, helper.getLevel().getGameTime(), placed);
      RuinData.get(helper.getLevel().getServer()).add(ruin);
      StructureProtection.invalidate(helper.getLevel().getServer());
    }

    RuinChallenges.Node node(BlockPos relative) {
      return RuinChallenges.node(helper.getLevel(), helper.absolutePos(relative));
    }

    String key(String challenge) {
      return RuinProgress.key(definition.id(), challenge);
    }

    @Override
    public void close() {
      var server = helper.getLevel().getServer();
      RuinData.get(server).remove(ruin.id());
      RuinProgress.get(server).forget(ruin.id().toString());
      RuinRegistry.remove(definition.id());
      StructureProtection.invalidate(server);
    }
  }

  static final String BASE = """
      {"act": 1, "dimension": "minecraft:overworld", "scale": "medium", "template": "entrelumen:ruins/dome_greenhouse",
       "placement": {"mode": "surface", "min": 250, "max": 500}, "piece": "entrelumen:signal_ember",
       "project": "first_signal", "loot": "entrelumen:chests/ruin_act1_signal_tower", """;

  /** A right click through the server's real interaction path (events, protection, block use). */
  static void click(ServerPlayer player, BlockPos pos) {
    player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
  }

  static boolean lit(GameTestHelper helper, BlockPos relative) {
    var state = helper.getBlockState(relative);
    return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
  }

  // ---- Templates and placement ----------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void ruinTemplatesLoadWithKnownBlocksAndMarkers(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    for (var definition : RuinRegistry.all().values()) {
      var prepared = RuinPlacement.prepare(server, ResourceLocation.parse(definition.template()));
      helper.assertTrue(prepared.markers().stream().filter(m -> m.marker().kind() == RuinMarkers.Kind.PEDESTAL).count() == 1,
          definition.id() + ": one pedestal");
      helper.assertTrue(!prepared.slices().isEmpty() && prepared.blocks() > 1000, definition.id() + ": empty template");
      // Blocks of a mod that is not loaded (Create on the isolated server) stay air; the rest must exist.
      for (var local : prepared.markers())
        if (!local.marker().block().isEmpty() && loadedNamespace(local.marker().block()))
          helper.assertTrue(RuinPlacement.blockOf(local.marker()) != null, definition.id() + ": bad block in " + local.marker());
      var resource = server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("entrelumen",
          "structure/" + ResourceLocation.parse(definition.template()).getPath() + ".nbt")).orElseThrow();
      try (var stream = resource.open()) {
        var tag = net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        for (var entry : tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
          var compound = (net.minecraft.nbt.CompoundTag) entry;
          String name = compound.getString("Name");
          if (!loadedNamespace(name)) continue;
          helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(name)),
              definition.id() + ": unknown block " + name);
          // Every property the template names must exist on the block (1.21.1 states).
          var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
          var properties = compound.getCompound("Properties");
          for (String key : properties.getAllKeys())
            helper.assertTrue(block.getStateDefinition().getProperty(key) != null
                    && block.getStateDefinition().getProperty(key).getValue(properties.getString(key)).isPresent(),
                definition.id() + ": " + name + " has no " + key + "=" + properties.getString(key));
        }
      }
    }
    helper.assertTrue(RuinRegistry.all().size() == 10, "Ten ruins load: " + RuinRegistry.all().keySet());
    // A template that cannot fit the world is refused before anything is written.
    var level = helper.getLevel();
    var deep = RuinRegistry.get("entrelumen:sunken_workshop").orElseThrow();
    helper.assertTrue(!RuinPlacement.fits(level, level.getMinBuildHeight() - 3, 49)
        && !RuinPlacement.fits(level, level.getMaxBuildHeight() - 10, 49)
        && RuinPlacement.fits(level, 0, 49), "World bounds check");
    helper.assertTrue(deep.template().endsWith("sunken_workshop"), "Workshop definition");
    helper.succeed();
  }

  /** Whether a block id's namespace is Minecraft's or a loaded mod's. */
  static boolean loadedNamespace(String block) {
    int colon = block.indexOf(':');
    String namespace = colon < 0 ? "minecraft" : block.substring(0, colon);
    return namespace.equals("minecraft") || RuinRegistry.modLoaded(namespace);
  }

  /**
   * Placement cases wait on worldgen, which takes wall time while the isolated server ticks as fast
   * as it can: they give up after this long, whatever the tick count.
   */
  static final long WALL_LIMIT_NANOS = 900_000_000_000L;

  /** Why a wait ended without its condition; the next step fails the case with it. */
  static final class Stop {
    String reason;

    void check(GameTestHelper helper) {
      if (reason != null) helper.fail(reason);
    }
  }

  /**
   * A sequence step that turns an unexpected exception into this case's failure: sequences only
   * catch assertion errors, and anything else would reach the server tick.
   */
  static Runnable guarded(Runnable step) {
    return () -> {
      try {
        step.run();
      } catch (net.minecraft.gametest.framework.GameTestAssertException e) {
        throw e;
      } catch (RuntimeException e) {
        LOGGER.error("Ruin QA step failed", e);
        throw new net.minecraft.gametest.framework.GameTestAssertException(e.toString());
      }
    };
  }

  /** Waits for a placement job; stops waiting (for the next step to fail) on failure or past the wall limit. */
  static void awaitJob(GameTestHelper helper, RuinPlacement.Job job, long started, Stop stop) {
    if (stop.reason != null) return;
    if (job.failed()) {
      stop.reason = "Placement failed at " + job.stage();
      return;
    }
    if (System.nanoTime() - started > WALL_LIMIT_NANOS) {
      stop.reason = "Placement did not finish in 15 minutes; stage " + job.stage();
      return;
    }
    helper.assertTrue(job.done(), "Placing, stage " + job.stage());
  }

  static void waitForJob(GameTestHelper helper, RuinPlacement.Job job, Runnable then) {
    long started = System.nanoTime();
    Stop stop = new Stop();
    helper.startSequence()
        .thenWaitUntil(() -> awaitJob(helper, job, started, stop))
        .thenExecute(guarded(() -> {
          stop.check(helper);
          then.run();
        }))
        .thenSucceed();
  }

  /** Every template marker is in the registry and its cell holds what the marker says. */
  static void assertMarkersPlaced(GameTestHelper helper, ServerLevel level, RuinDefinitions.Definition definition,
      RuinData.Ruin ruin) {
    helper.assertTrue(!ruin.markers().isEmpty(), "No markers registered");
    for (var marker : ruin.markers()) {
      var state = level.getBlockState(marker.pos());
      var kind = marker.marker().kind();
      helper.assertTrue(!state.is(Blocks.STRUCTURE_BLOCK), "A marker stayed a structure block: " + marker);
      switch (kind) {
        case PEDESTAL -> helper.assertTrue(state.is(RuinContent.PEDESTAL.get()), "No pedestal at " + marker);
        case MIRROR -> helper.assertTrue(state.is(RuinContent.MIRROR.get()), "No mirror at " + marker);
        case SOCKET -> helper.assertTrue(state.is(RuinContent.SOCKET.get()), "No socket at " + marker);
        case GATE -> helper.assertTrue(state.is(RuinContent.GATE.get()), "No gate at " + marker);
        case HIDDEN -> helper.assertTrue(state.is(RuinContent.HIDDEN.get()), "No loose stone at " + marker);
        case LOCK -> helper.assertTrue(state.is(RuinContent.LOCK.get()), "No lock at " + marker);
        case CHEST -> {
          String loot = marker.marker().param("loot", definition.loot());
          // A table of an absent mod (the wheelhouse parts without Create) gives way to the ruin's loot.
          if (!RuinPlacement.lootExists(level, loot)) loot = definition.loot();
          String expected = loot;
          helper.assertTrue(level.getBlockEntity(marker.pos()) instanceof RandomizableContainerBlockEntity container
              && container.getLootTable() != null && container.getLootTable().location().toString().equals(expected),
              "Container without its loot at " + marker);
          if (RuinRegistry.modLoaded("lootr"))
            helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("lootr"),
                "Not a Lootr container: " + state);
        }
        case PART, SANDBOX -> helper.assertTrue(state.isAir(), "A missing piece or the sandbox is not empty: " + marker);
        case MODBLOCK -> {
          var expected = RuinRegistry.modLoaded(marker.marker().param("mod", "")) ? RuinPlacement.blockOf(marker.marker()) : null;
          helper.assertTrue(expected == null ? state.isAir() : state.is(expected.getBlock()), "Mod block cell holds " + state + ": " + marker);
        }
        case NOTE -> helper.assertTrue(state.getBlock() instanceof net.minecraft.world.level.block.LecternBlock
            && state.getValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK), "No note on its lectern: " + marker);
        default -> {
          var expected = RuinPlacement.blockOf(marker.marker());
          if (expected != null)
            helper.assertTrue(state.getBlock() == expected.getBlock(), "Marker cell holds " + state + ": " + marker);
        }
      }
    }
  }

  /** Surface ruins stand on ground: under each column's lowest block on the ground layer lies no gap. */
  static void assertGrounded(GameTestHelper helper, ServerLevel level, RuinPlacement.Job job, RuinData.Ruin ruin) {
    var p = job.prepared;
    int checked = 0;
    for (int i = 0; i < p.lowColumns().length; i += 7) {
      if (p.lowY()[i] > p.ground()) continue;
      int x = (int) (p.lowColumns()[i] >> 32), z = (int) p.lowColumns()[i];
      BlockPos under = ruin.origin().offset(x, p.lowY()[i] - 1, z);
      helper.assertTrue(!level.getBlockState(under).canBeReplaced(), "The ruin floats above " + under);
      checked++;
    }
    helper.assertTrue(checked > 0, "No ground column checked");
  }

  /** A copy of a shipped ruin under its own id, so cases never share a registry entry. */
  static RuinDefinitions.Definition qaCopy(RuinDefinitions.Definition definition) {
    var copy = new RuinDefinitions.Definition("entrelumen:qa_" + definition.name() + "_"
        + UUID.randomUUID().toString().substring(0, 6), definition.act(), definition.dimension(), definition.mods(),
        definition.scale(), definition.template(), definition.placement(), definition.piece(), definition.project(),
        definition.loot(), definition.challenges(), definition.gates(), definition.pedestal(), definition.dormant());
    RuinRegistry.add(copy);
    return copy;
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void overworldRuinIsSitedPlacedRegisteredProtectedAndAnchored(GameTestHelper helper) {
    var level = helper.getLevel();
    var server = level.getServer();
    // The Signal Tower has no cellar: it fits the superflat test world, whose ground is near its floor.
    var definition = qaCopy(RuinRegistry.get("entrelumen:signal_tower").orElseThrow());
    // Far from the test grid and from any other case; the ring search runs from here.
    BlockPos center = helper.absolutePos(BlockPos.ZERO).offset(24_000, 0, 16_000);
    var data = RuinData.get(server);
    var id = ResourceLocation.parse(definition.id());
    data.remove(id);
    var job = RuinPlacement.start(level, definition, center).orElseThrow();
    helper.assertTrue(RuinPlacement.start(level, definition, center).orElseThrow() == job, "A second start queued a second job");
    waitForJob(helper, job, () -> {
      try {
        var ruin = job.result();
        helper.assertTrue(ruin != null && data.find(id).orElseThrow().equals(ruin), "Not registered");
        double distance = Math.hypot(ruin.center().getX() - center.getX(), ruin.center().getZ() - center.getZ());
        helper.assertTrue(distance >= definition.placement().min() - 32 && distance <= definition.placement().max() + 32,
            "Outside the ring: " + distance);
        helper.assertTrue(RuinPlacement.start(level, definition, center).isEmpty(), "Placed twice");
        helper.assertTrue(data.reservation(id).isEmpty(), "Reservation left behind");
        assertMarkersPlaced(helper, level, definition, ruin);
        assertGrounded(helper, level, job, ruin);
        // Indestructible: the protection covers the registered box.
        var box = ruin.box();
        var player = arrive(helper, "RuinBreaker", new BlockPos(1, 2, 1));
        var inside = ruin.markers(RuinMarkers.Kind.PEDESTAL).getFirst().pos().below();
        var verdict = StructureProtection.check(level, inside, ProtectionRules.Action.BREAK, StructureProtection.actor(player));
        helper.assertTrue(!verdict.allowed(), "The ruin can be broken: " + verdict);
        helper.assertTrue(StructureProtection.check(level, box.getCenter().above(200), ProtectionRules.Action.BREAK,
            StructureProtection.actor(player)).allowed(), "Protection leaks above the box");
        // Compass anchor: the shipped objective points at the placed ruin.
        var objective = new CompassTargets.Objective("qa_ruin", 1, CompassTargets.Kind.STRUCTURE,
            new CompassTargets.Target(CompassTargets.TargetType.ANCHOR, definition.id(), "minecraft:overworld", null, 1500),
            new CompassTargets.Condition(CompassTargets.ConditionType.RUIN, definition.id(), 1), true);
        player.teleportTo(ruin.center().getX() + 0.5, 200, ruin.center().getZ() + 40.5);
        var state = HeliodorCompass.resolve(player, objective, CompassData.get(server), true);
        helper.assertTrue(state.state() == CompassState.POINTING
            && state.target().equals(Optional.of(GlobalPos.of(Level.OVERWORLD, ruin.center()))), "Compass: " + state);
        leave(player);
        LOGGER.info("Ruin QA: {} placed at {} ({} blocks from the search centre)", definition.template(), box,
            (int) distance);
      } finally {
        data.remove(id);
        RuinRegistry.remove(definition.id());
        StructureProtection.invalidate(server);
      }
    });
  }

  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void landmarkTemplateIsPlacedInSlicesAcrossTicks(GameTestHelper helper) {
    var level = helper.getLevel();
    var definition = qaCopy(RuinRegistry.get("entrelumen:light_temple").orElseThrow());
    var id = ResourceLocation.parse(definition.id());
    var data = RuinData.get(level.getServer());
    data.remove(id);
    BlockPos origin = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() - 20_000, level.getSeaLevel() - 4,
        helper.absolutePos(BlockPos.ZERO).getZ() + 20_000);
    var job = RuinPlacement.startAt(level, definition, origin).orElseThrow();
    waitForJob(helper, job, () -> {
      try {
        var ruin = job.result();
        helper.assertTrue(ruin.origin().equals(origin), "A fixed origin moved: " + ruin.origin());
        helper.assertTrue(job.busyTicks > 4, "85 wide in " + job.busyTicks + " ticks: not sliced");
        helper.assertTrue(job.worstNanos < 250_000_000L, "One tick spent " + job.worstNanos / 1_000_000 + " ms");
        helper.assertTrue(ruin.box().getXSpan() == job.prepared.sizeX() && ruin.box().getZSpan() == job.prepared.sizeZ()
            && ruin.box().maxY() == origin.getY() + job.prepared.sizeY() - 1, "Box " + ruin.box());
        assertMarkersPlaced(helper, level, definition, ruin);
        LOGGER.info("Ruin QA: temple {} template blocks over {} ticks, worst {} ms", job.prepared.blocks(), job.busyTicks,
            job.worstNanos / 1_000_000);
      } finally {
        data.remove(id);
        RuinRegistry.remove(definition.id());
        StructureProtection.invalidate(level.getServer());
      }
    });
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void dimensionRuinsFindACavernOrFloat(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var nether = server.getLevel(Level.NETHER);
    var end = server.getLevel(Level.END);
    helper.assertTrue(nether != null && end != null, "The test world has no Nether or End");
    var foundry = qaCopy(RuinRegistry.get("entrelumen:nether_foundry").orElseThrow());
    var observatory = qaCopy(RuinRegistry.get("entrelumen:void_observatory").orElseThrow());
    var data = RuinData.get(server);
    BlockPos arrival = new BlockPos(3_000, 64, -3_000);
    var inNether = RuinPlacement.start(nether, foundry, arrival).orElseThrow();
    var inEnd = RuinPlacement.start(end, observatory, new BlockPos(100, 50, 0)).orElseThrow();
    long started = System.nanoTime();
    Stop stop = new Stop();
    helper.startSequence()
        .thenWaitUntil(() -> {
          awaitJob(helper, inNether, started, stop);
          awaitJob(helper, inEnd, started, stop);
        })
        .thenExecute(guarded(() -> {
          stop.check(helper);
          try {
            helper.assertTrue(!inNether.failed() && !inEnd.failed(), "A dimension ruin failed");
            var cavern = inNether.result();
            double distance = Math.hypot(cavern.center().getX() - arrival.getX(), cavern.center().getZ() - arrival.getZ());
            helper.assertTrue(distance >= 118 && distance <= 532, "Foundry outside the ring: " + distance);
            helper.assertTrue(cavern.dimension().equals(Level.NETHER) && cavern.origin().getY() > 20 && cavern.origin().getY() < 110,
                "Foundry at " + cavern.origin());
            var floating = inEnd.result();
            helper.assertTrue(floating.dimension().equals(Level.END), "Observatory not in the End");
            BlockPos below = floating.center().below(floating.box().getYSpan());
            LOGGER.info("Ruin QA: foundry at {}, void observatory at {} (below it {})", cavern.box(), floating.box(),
                end.getBlockState(below));
          } finally {
            for (var definition : List.of(foundry, observatory)) {
              data.remove(ResourceLocation.parse(definition.id()));
              RuinRegistry.remove(definition.id());
            }
            StructureProtection.invalidate(server);
          }
        }))
        .thenSucceed();
  }

  // ---- Challenges, per team -------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void braziersLightInOrderPerTeamAndResetOnAWrongTurn(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"light": {"type": "braziers"}}, "gates": {"vault": ["light"]}, "pedestal": ["light"]}""",
        List.of(new Object[] {"brazier challenge=light order=1 block=minecraft:campfire[lit=false]", new BlockPos(0, 1, 0)},
            new Object[] {"brazier challenge=light order=1 block=minecraft:campfire[lit=false]", new BlockPos(2, 1, 0)},
            new Object[] {"brazier challenge=light order=2 block=minecraft:waxed_copper_bulb[lit=false]", new BlockPos(4, 1, 0)},
            new Object[] {"lamp challenge=light block=minecraft:waxed_copper_bulb[lit=false]", new BlockPos(0, 1, 4)},
            new Object[] {"gate id=vault", new BlockPos(4, 1, 4)}))) {
      var a = arrive(helper, "BrazierA", new BlockPos(2, 1, 2));
      var b = arrive(helper, "BrazierB", new BlockPos(2, 1, 3));
      click(a, helper.absolutePos(new BlockPos(2, 1, 0)));
      helper.assertTrue(lit(helper, new BlockPos(2, 1, 0)) && team(a).lit(fixture.key("light")).equals(Set.of(1)),
          "The first brazier did not light");
      click(a, helper.absolutePos(new BlockPos(4, 1, 0)));
      helper.assertTrue(team(a).lit(fixture.key("light")).isEmpty() && !lit(helper, new BlockPos(2, 1, 0)),
          "A second-turn brazier before the first turn did not reset");
      for (var cell : List.of(new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(4, 1, 0)))
        click(a, helper.absolutePos(cell));
      helper.assertTrue(team(a).solved.contains(fixture.key("light")), "In order, it did not solve");
      helper.assertTrue(lit(helper, new BlockPos(0, 1, 4)), "The lamp did not wake");
      helper.assertTrue(!team(b).solved.contains(fixture.key("light")), "Another team solved it too");
      // The world shows the team that works on it: B's first touch shows B's (empty) progress.
      click(b, helper.absolutePos(new BlockPos(0, 1, 0)));
      helper.assertTrue(lit(helper, new BlockPos(0, 1, 0)) && !lit(helper, new BlockPos(2, 1, 0))
          && !lit(helper, new BlockPos(0, 1, 4)), "The braziers do not show team B's state");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void gateLetsOnlyTheSolvingTeamThrough(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"wall": {"type": "hidden"}}, "gates": {"cellar": ["wall"]}, "pedestal": ["wall"]}""",
        List.<Object[]>of(new Object[] {"hidden challenge=wall look=mossy_stone_bricks", new BlockPos(0, 1, 0)},
            new Object[] {"gate id=cellar look=moss_block climb=true", new BlockPos(4, 1, 4)}))) {
      var a = arrive(helper, "GateA", new BlockPos(2, 1, 1));
      var b = arrive(helper, "GateB", new BlockPos(2, 1, 2));
      var gate = helper.absolutePos(new BlockPos(4, 1, 4));
      var state = helper.getLevel().getBlockState(gate);
      helper.assertTrue(state.is(RuinContent.GATE.get()) && state.getValue(RuinBlocks.LOOK) == RuinBlocks.Look.MOSS_BLOCK,
          "The false floor is not in place");
      helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(0, 1, 0))).is(RuinContent.HIDDEN.get()),
          "The loose stone is not in place");
      helper.assertTrue(!state.getCollisionShape(helper.getLevel(), gate, CollisionContext.of(a)).isEmpty(),
          "Closed gates must be solid");
      click(a, helper.absolutePos(new BlockPos(0, 1, 0)));
      helper.assertTrue(team(a).solved.contains(fixture.key("wall")), "The loose stone did not answer");
      RuinGates.refresh(a);
      RuinGates.refresh(b);
      helper.assertTrue(state.getCollisionShape(helper.getLevel(), gate, CollisionContext.of(a)).isEmpty(),
          "The solving team cannot pass");
      helper.assertTrue(!state.getCollisionShape(helper.getLevel(), gate, CollisionContext.of(b)).isEmpty(),
          "Another team passes a gate it did not open");
      helper.assertTrue(!state.getCollisionShape(helper.getLevel(), gate, CollisionContext.empty()).isEmpty(),
          "Mobs and items pass the gate");
      helper.assertTrue(state.isLadder(helper.getLevel(), gate, a) && !state.isLadder(helper.getLevel(), gate, b),
          "The shaft is climbable only for the team");
      helper.assertTrue(!StructureProtection.check(helper.getLevel(), gate, ProtectionRules.Action.BREAK,
          StructureProtection.actor(a)).allowed(), "The gate can be broken");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void mirrorsSendTheLightToTheReceptor(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"light": {"type": "mirrors"}}, "pedestal": ["light"]}""",
        List.<Object[]>of(new Object[] {"mirror challenge=light facing=se", new BlockPos(0, 1, 0)},
            new Object[] {"mirror challenge=light facing=n", new BlockPos(4, 1, 0)},
            new Object[] {"receptor challenge=light block=minecraft:glass", new BlockPos(2, 3, 4)}))) {
      var a = arrive(helper, "MirrorA", new BlockPos(2, 1, 2));
      var b = arrive(helper, "MirrorB", new BlockPos(2, 1, 3));
      BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0)), second = helper.absolutePos(new BlockPos(4, 1, 0));
      // (0,0) aims south-east at (2,4): already right. (4,0) faces north: it needs south-west, five clicks.
      for (int i = 0; i < 4; i++) click(a, second);
      helper.assertTrue(!team(a).solved.contains(fixture.key("light")), "Solved with a mirror astray");
      click(a, second);
      helper.assertTrue(helper.getLevel().getBlockState(second).getValue(RuinBlocks.AIM) == 5, "Mirror did not turn");
      helper.assertTrue(team(a).solved.contains(fixture.key("light")), "Both mirrors aim at the receptor: not solved");
      helper.assertTrue(!team(b).solved.contains(fixture.key("light")), "Another team solved it too");
      helper.assertTrue(helper.getLevel().getBlockState(first).getValue(RuinBlocks.AIM) == 3, "The first mirror moved");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void leverLockSolvesForTheTeamThatSetItAndResets(GameTestHelper helper) {
    var fixture = new Fixture(helper, BASE + """
        "challenges": {"guards": {"type": "hidden"}, "sluices": {"type": "redstone", "requires": ["guards"]},
         "core": {"type": "redstone"}}, "pedestal": ["sluices"]}""",
        List.<Object[]>of(
            new Object[] {"hidden challenge=guards", new BlockPos(4, 1, 4)},
            new Object[] {"lever challenge=sluices block=minecraft:lever[face=floor,facing=north,powered=false]", new BlockPos(0, 1, 0)},
            new Object[] {"lever challenge=sluices block=minecraft:lever[face=floor,facing=north,powered=false]", new BlockPos(2, 1, 0)},
            new Object[] {"lock challenge=core", new BlockPos(4, 1, 0)}));
    helper.setBlock(new BlockPos(4, 1, 1), Blocks.LEVER.defaultBlockState()
        .setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
    var a = arrive(helper, "LeverA", new BlockPos(1, 1, 2));
    var b = arrive(helper, "LeverB", new BlockPos(1, 1, 3));
    BlockPos first = helper.absolutePos(new BlockPos(0, 1, 0)), second = helper.absolutePos(new BlockPos(2, 1, 0));
    helper.startSequence()
        .thenExecute(guarded(() -> {
          click(a, first);
          click(a, second);
        }))
        .thenIdle(3)
        .thenExecute(guarded(() -> {
          helper.assertTrue(!team(a).solved.contains(fixture.key("sluices")), "Solved before the guards");
          helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).getValue(LeverBlock.POWERED),
              "Levers are not usable inside a protected ruin");
          click(a, helper.absolutePos(new BlockPos(4, 1, 4)));
        }))
        .thenIdle(RuinChallenges.RESET_TICKS + 5)
        .thenExecute(guarded(() -> {
          helper.assertTrue(!helper.getBlockState(new BlockPos(0, 1, 0)).getValue(LeverBlock.POWERED),
              "A finished lock did not reset its levers");
          click(a, first);
          click(a, second);
        }))
        .thenIdle(3)
        .thenExecute(guarded(() -> {
          helper.assertTrue(team(a).solved.contains(fixture.key("sluices")), "The right levers did not open the lock");
          helper.assertTrue(!team(b).solved.contains(fixture.key("sluices")), "Another team solved the lock");
          // A redstone lock core credits the team that last worked a lever of the ruin.
          click(b, helper.absolutePos(new BlockPos(4, 1, 1)));
        }))
        .thenIdle(2)
        .thenExecute(guarded(() -> {
          helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 0)).getValue(BlockStateProperties.POWERED),
              "The lock core is not powered");
          helper.assertTrue(team(b).solved.contains(fixture.key("core")) && !team(a).solved.contains(fixture.key("core")),
              "The core did not credit the lever's team");
        }))
        .thenIdle(RuinChallenges.RESET_TICKS + 5)
        .thenExecute(guarded(() -> {
          helper.assertTrue(!helper.getBlockState(new BlockPos(2, 1, 0)).getValue(LeverBlock.POWERED),
              "Levers stayed on for the next team");
          leave(a);
          leave(b);
          fixture.close();
        }))
        .thenSucceed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void offeringsAreConsumedPerTeam(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"gifts": {"type": "offering", "item": "minecraft:gold_ingot", "need": 2}}, "pedestal": ["gifts"]}""",
        List.<Object[]>of(new Object[] {"socket challenge=gifts look=altar", new BlockPos(0, 1, 0)},
            new Object[] {"socket challenge=gifts look=altar", new BlockPos(2, 1, 0)},
            new Object[] {"socket challenge=gifts item=#minecraft:saplings look=pot", new BlockPos(4, 1, 0)}))) {
      var a = arrive(helper, "GiftA", new BlockPos(2, 1, 2));
      var b = arrive(helper, "GiftB", new BlockPos(2, 1, 3));
      a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 4));
      click(a, helper.absolutePos(new BlockPos(0, 1, 0)));
      helper.assertTrue(a.getMainHandItem().getCount() == 4 && team(a).filled(fixture.key("gifts")).isEmpty(),
          "A wrong offering was taken");
      a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT, 3));
      click(a, helper.absolutePos(new BlockPos(0, 1, 0)));
      click(a, helper.absolutePos(new BlockPos(0, 1, 0)));
      helper.assertTrue(a.getMainHandItem().getCount() == 2, "The same socket took two offerings");
      helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).getValue(RuinBlocks.FILLED), "The socket shows empty");
      a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_SAPLING));
      click(a, helper.absolutePos(new BlockPos(4, 1, 0)));
      helper.assertTrue(a.getMainHandItem().isEmpty() && team(a).solved.contains(fixture.key("gifts")),
          "Two of three sockets (a tag in one) did not solve");
      b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
      click(b, helper.absolutePos(new BlockPos(2, 1, 0)));
      helper.assertTrue(!team(b).solved.contains(fixture.key("gifts")) && team(b).filled(fixture.key("gifts")).equals(Set.of(1)),
          "Team B does not keep its own offerings");
      helper.assertTrue(!helper.getBlockState(new BlockPos(0, 1, 0)).getValue(RuinBlocks.FILLED),
          "The sockets do not show team B's state");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 600)
  public static void bossRisesPerTeamWithItsBarAndSolvesWhenBeaten(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var difficulty = server.getWorldData().getDifficulty();
    server.setDifficulty(Difficulty.NORMAL, true);
    var fixture = new Fixture(helper, BASE + """
        "challenges": {"guard": {"type": "boss", "entity": "minecraft:vindicator", "name": "entrelumen.ruin.boss.toll_guardian",
         "count": 2, "radius": 8, "color": "red", "attributes": {"minecraft:generic.max_health": 77, "minecraft:generic.scale": 1.4}}},
         "pedestal": ["guard"]}""",
        List.<Object[]>of(new Object[] {"boss challenge=guard", new BlockPos(2, 1, 2)}));
    var a = arrive(helper, "BossA", new BlockPos(0, 1, 0));
    var b = arrive(helper, "BossB", new BlockPos(4, 1, 4));
    try {
      RuinBosses.scan(server);
      var groupA = RuinBosses.group(server, fixture.ruin.id(), "guard", context(a).campaignId());
      var groupB = RuinBosses.group(server, fixture.ruin.id(), "guard", context(b).campaignId());
      helper.assertTrue(groupA != null && groupB != null && groupA != groupB, "Each team did not get its own guardians");
      helper.assertTrue(groupA.mobs.size() == 2, "Count 2 raised " + groupA.mobs.size());
      var mob = (LivingEntity) helper.getLevel().getEntity(groupA.mobs.getFirst());
      helper.assertTrue(mob != null && mob.getMaxHealth() == 77f && mob.getAttributeValue(Attributes.SCALE) == 1.4,
          "Attributes not applied");
      helper.assertTrue(mob.getCustomName() != null && mob.getCustomName().getContents()
          instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals("entrelumen.ruin.boss.toll_guardian"),
          "The guardian has no name");
      RuinBosses.scan(server);
      helper.assertTrue(groupA.bar.getPlayers().contains(a) && groupA.bar.getPlayers().contains(b), "No boss bar");
      RuinBosses.scan(server);
      helper.assertTrue(RuinBosses.group(server, fixture.ruin.id(), "guard", context(a).campaignId()) == groupA,
          "A second group rose for the same team");
      for (UUID id : List.copyOf(groupA.mobs)) {
        var entity = (LivingEntity) helper.getLevel().getEntity(id);
        entity.hurt(helper.getLevel().damageSources().playerAttack(b), 10_000f);
      }
      helper.assertTrue(team(a).solved.contains(fixture.key("guard")), "Beating team A's guardians did not solve it for A");
      helper.assertTrue(!team(b).solved.contains(fixture.key("guard")), "Team B solved it without its fight");
      helper.assertTrue(RuinBosses.group(server, fixture.ruin.id(), "guard", context(a).campaignId()) == null
          && groupA.bar.getPlayers().isEmpty(), "The bar stayed");
      RuinBosses.scan(server);
      helper.assertTrue(RuinBosses.group(server, fixture.ruin.id(), "guard", context(a).campaignId()) == null,
          "Guardians rose again for a team that beat them");
    } finally {
      var groupB = RuinBosses.group(server, fixture.ruin.id(), "guard", context(b).campaignId());
      if (groupB != null) for (UUID id : groupB.mobs) {
        var entity = helper.getLevel().getEntity(id);
        if (entity != null) entity.discard();
      }
      RuinBosses.forget(server);
      leave(a);
      leave(b);
      fixture.close();
      server.setDifficulty(difficulty, true);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void pedestalGivesOnePiecePerTeamAndReplacesALostOne(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"wall": {"type": "hidden"}}, "pedestal": ["wall"]}""",
        List.<Object[]>of(new Object[] {"pedestal", new BlockPos(0, 1, 0)},
            new Object[] {"hidden challenge=wall", new BlockPos(2, 1, 0)}))) {
      var a = arrive(helper, "PieceA", new BlockPos(2, 1, 2));
      var b = arrive(helper, "PieceB", new BlockPos(2, 1, 3));
      var pedestal = helper.absolutePos(new BlockPos(0, 1, 0));
      var ember = RuinContent.PIECES.get("signal_ember").get();
      click(a, pedestal);
      helper.assertTrue(a.getInventory().countItem(ember) == 0, "The pedestal gave a piece before the challenge");
      click(a, helper.absolutePos(new BlockPos(2, 1, 0)));
      click(a, pedestal);
      helper.assertTrue(a.getInventory().countItem(ember) == 1, "No piece after the challenge");
      var first = a.getInventory().items.stream().filter(s -> s.is(ember)).findFirst().orElseThrow();
      var binding = KeyPieces.binding(first);
      helper.assertTrue(binding != null && binding.campaign().equals(context(a).campaignId()) && binding.generation() == 1
          && binding.ruin().equals(fixture.definition.id()), "Piece not bound to the team: " + binding);
      click(a, pedestal);
      helper.assertTrue(a.getInventory().countItem(ember) == 1, "A second piece while the team carries one");
      // Lost: the copy goes where nobody on the team can carry it; the pedestal replaces it.
      ItemStack lost = first.copy();
      first.setCount(0);
      click(a, pedestal);
      var replacement = a.getInventory().items.stream().filter(s -> s.is(ember)).findFirst().orElseThrow();
      helper.assertTrue(KeyPieces.binding(replacement).generation() == 2, "No replacement for a lost piece");
      helper.assertTrue(KeyPieces.deliverable(a, List.of(lost)).isEmpty() && KeyPieces.deliverable(a, List.of(replacement)).size() == 1,
          "The replaced copy still counts");
      helper.assertTrue(KeyPieces.deliverable(b, List.of(replacement)).isEmpty(), "Another team can deliver this team's piece");
      helper.assertTrue(KeyPieces.deliverable(a, List.of(new ItemStack(ember))).size() == 1, "Unbound pieces must count");
      // The old copy fades as soon as someone carries it.
      a.getInventory().add(lost);
      lost.inventoryTick(a.level(), a, 0, false);
      a.tickCount = 20;
      lost.inventoryTick(a.level(), a, 0, false);
      helper.assertTrue(lost.isEmpty(), "A replaced copy did not fade");
      helper.assertTrue(b.getInventory().countItem(ember) == 0, "Team B got a piece");
      click(b, pedestal);
      helper.assertTrue(b.getInventory().countItem(ember) == 0, "Team B took a piece without the challenge");
      // Delivered: nothing more.
      Entrelumen.current(a).completed.add("first_signal");
      a.getInventory().clearContent();
      click(a, pedestal);
      helper.assertTrue(a.getInventory().countItem(ember) == 0, "The pedestal gave a piece after delivery");
      Entrelumen.current(a).completed.remove("first_signal");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void projectsTakeOnlyTheTeamsCurrentKeyPiece(GameTestHelper helper) {
    var a = arrive(helper, "ProjectA", new BlockPos(1, 1, 1));
    var b = arrive(helper, "ProjectB", new BlockPos(3, 1, 1));
    var campaign = Entrelumen.current(a);
    var project = Projects.all().get("first_signal");
    String ember = "entrelumen:signal_ember";
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ember));
    helper.assertTrue(project.items().get(ember) == 1, "First Signal does not ask for the Signal Ember");
    campaign.completed.addAll(project.prerequisites());
    project.items().forEach((id, count) -> {
      if (!id.equals(ember)) a.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count));
    });
    var campaignId = CampaignActions.campaignId(a);
    java.util.function.BooleanSupplier deliver = () ->
        CampaignActions.perform(a, campaignId, CampaignActions.Action.DELIVER, "first_signal").success();
    helper.assertTrue(!deliver.getAsBoolean() && !campaign.completed.contains("first_signal"),
        "First Signal closed without its piece");
    String ruin = "entrelumen:qa_piece_" + UUID.randomUUID().toString().substring(0, 6);
    team(b).pieces.put(ruin, 1);
    a.getInventory().add(KeyPieces.bound(item, context(b).campaignId(), ruin, 1));
    helper.assertTrue(!deliver.getAsBoolean(), "Another team's piece counted");
    team(a).pieces.put(ruin, 2);
    a.getInventory().add(KeyPieces.bound(item, context(a).campaignId(), ruin, 1));
    helper.assertTrue(!deliver.getAsBoolean() && Entrelumen.availableMaterials(a).getOrDefault(ember, 0) == 0,
        "A replaced copy counted");
    a.getInventory().add(KeyPieces.bound(item, context(a).campaignId(), ruin, 2));
    helper.assertTrue(Entrelumen.availableMaterials(a).get(ember) == 1, "The Atlas does not see the team's piece");
    helper.assertTrue(deliver.getAsBoolean() && campaign.completed.contains("first_signal"),
        "The team's own piece did not close First Signal");
    helper.assertTrue(a.getInventory().items.stream().filter(stack -> stack.is(item))
        .noneMatch(stack -> KeyPieces.binding(stack).generation() == 2 && KeyPieces.binding(stack).campaign().equals(campaignId)),
        "The delivery did not take the team's piece");
    leave(a);
    leave(b);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void landmarkPedestalAdoptsThePiecesOfAbsentMods(GameTestHelper helper) {
    var landmark = RuinRegistry.get("entrelumen:cliff_observatory").orElseThrow();
    var pieces = RuinChallenges.pieces(landmark).stream().map(RuinDefinitions.Definition::piece).toList();
    boolean twilight = RuinRegistry.modLoaded("twilightforest"), aether = RuinRegistry.modLoaded("aether");
    helper.assertTrue(pieces.contains("entrelumen:voices_eyepiece")
        && pieces.contains("entrelumen:forest_testimony") == !twilight
        && pieces.contains("entrelumen:sun_key") == !aether, "Landmark pieces: " + pieces);
    helper.assertTrue(RuinRegistry.available().stream().noneMatch(d -> d.id().equals("entrelumen:twilight_sanctuary")) == !twilight,
        "A ruin of an absent mod would be placed");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void enteringARuinRecordsTheVisitAndMovesTheCompass(GameTestHelper helper) {
    try (var fixture = new Fixture(helper, BASE + """
        "challenges": {"wall": {"type": "hidden"}}, "pedestal": ["wall"]}""",
        List.<Object[]>of(new Object[] {"lore radius=1 height=3 block=minecraft:lectern", new BlockPos(2, 1, 2)},
            new Object[] {"hidden challenge=wall", new BlockPos(0, 1, 0)}))) {
      var a = arrive(helper, "VisitA", new BlockPos(4, 1, 4));
      var condition = new CompassTargets.Condition(CompassTargets.ConditionType.RUIN, fixture.definition.id(), 1);
      helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.LECTERN), "The lore marker lost its lectern");
      RuinChallenges.visits(a.server);
      helper.assertTrue(!team(a).visited.contains(fixture.definition.id())
          && !HeliodorCompass.satisfied(condition, context(a)), "Visited from outside the lore volume");
      var inside = helper.absolutePos(new BlockPos(3, 1, 2));
      a.teleportTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
      RuinChallenges.visits(a.server);
      helper.assertTrue(team(a).visited.contains(fixture.definition.id()) && HeliodorCompass.satisfied(condition, context(a)),
          "Entering did not record the visit");
      leave(a);
    }
    helper.succeed();
  }

  // ---- Every shipped ruin, end to end, from its own markers ----------------------------------

  /** Keeps a placed ruin's chunks loaded while a case plays it; expires if abandoned. */
  static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> PLAY =
      net.minecraft.server.level.TicketType.create("entrelumen_qa_ruin_play",
          Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 2400);

  static List<net.minecraft.world.level.ChunkPos> hold(ServerLevel level, BoundingBox box) {
    List<net.minecraft.world.level.ChunkPos> chunks = new ArrayList<>();
    for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++)
      for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
        var chunk = new net.minecraft.world.level.ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(PLAY, chunk, 0, chunk);
        chunks.add(chunk);
      }
    return chunks;
  }

  static void teleport(ServerPlayer player, ServerLevel level, BlockPos pos) {
    player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0f, 0f);
  }

  /** An item a socket accepts: its own item, the challenge's, or the first of the tag. */
  static ItemStack offering(RuinDefinitions.Challenge challenge, RuinMarkers.Marker socket) {
    String wanted = socket.param("item", challenge.item());
    int count = socket.params().containsKey("count") ? socket.count() : challenge.count();
    if (wanted.startsWith("#")) {
      var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
          ResourceLocation.parse(wanted.substring(1)));
      var item = BuiltInRegistries.ITEM.getTag(tag).orElseThrow().stream().findFirst().orElseThrow().value();
      return new ItemStack(item, count);
    }
    return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(wanted.isEmpty() ? "minecraft:stone" : wanted)), count);
  }

  /** Solves one challenge through its markers the way a player would. */
  static void solve(GameTestHelper helper, ServerLevel level, RuinData.Ruin ruin, RuinDefinitions.Definition definition,
      RuinDefinitions.Challenge challenge, ServerPlayer player) {
    String c = challenge.id();
    switch (challenge.type()) {
      case BRAZIERS -> {
        var nodes = new ArrayList<>(ruin.markers(RuinMarkers.Kind.BRAZIER, c));
        nodes.sort(Comparator.comparingInt(marker -> marker.marker().order()));
        for (var node : nodes) click(player, node.pos());
      }
      case MIRRORS -> {
        var receptor = ruin.markers(RuinMarkers.Kind.RECEPTOR, c).getFirst().pos();
        for (var mirror : ruin.markers(RuinMarkers.Kind.MIRROR, c))
          for (int i = 0; i < 8 && !RuinMarkers.aimed(level.getBlockState(mirror.pos()).getValue(RuinBlocks.AIM),
              mirror.pos().getX(), mirror.pos().getZ(), receptor.getX(), receptor.getZ()); i++)
            click(player, mirror.pos());
      }
      case REDSTONE -> {
        helper.assertTrue(ruin.markers(RuinMarkers.Kind.LOCK, c).isEmpty(), "Lock cores are covered by the fixture case");
        for (var lever : ruin.markers(RuinMarkers.Kind.LEVER, c)) {
          boolean answer = !"false".equals(lever.marker().param("on", "true"));
          if (level.getBlockState(lever.pos()).getValue(BlockStateProperties.POWERED) != answer) click(player, lever.pos());
        }
        RuinChallenges.levers(level, ruin, c, context(player));
      }
      case OFFERING -> {
        var sockets = ruin.markers(RuinMarkers.Kind.SOCKET, c);
        int need = challenge.need() <= 0 ? sockets.size() : challenge.need();
        for (var socket : sockets.subList(0, need)) {
          player.setItemInHand(InteractionHand.MAIN_HAND, offering(challenge, socket.marker()));
          click(player, socket.pos());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      }
      case BOSS -> {
        var spawn = ruin.markers(RuinMarkers.Kind.BOSS, c).getFirst().pos();
        teleport(player, level, spawn);
        RuinBosses.scan(level.getServer());
        var group = RuinBosses.group(level.getServer(), ruin.id(), c, context(player).campaignId());
        if (group != null)
          for (UUID id : List.copyOf(group.mobs)) {
            var mob = (LivingEntity) level.getEntity(id);
            helper.assertTrue(mob != null, "A guardian of " + c + " is missing");
            mob.hurt(level.damageSources().playerAttack(player), 100_000f);
          }
      }
      case HIDDEN -> {
        for (var hidden : ruin.markers(RuinMarkers.Kind.HIDDEN, c)) click(player, hidden.pos());
      }
      // The engine plays in RuinWorkshopFullpackGameTests with real Create builds; here the pedestal,
      // the gates and the second team are what the case checks, so the engine is credited directly.
      case PUMPS, SEAL -> RuinChallenges.solve(level, ruin, definition, c, context(player).campaignId(), context(player).founder());
    }
    helper.assertTrue(team(player).solved.contains(RuinProgress.key(definition.id(), c)),
        definition.id() + ": " + c + " (" + challenge.type() + ") was not solved from its markers");
  }

  /**
   * Places a copy of a shipped ruin at a fixed far origin, then plays it from its markers: every
   * challenge, the gates, the lore visit and the pedestal, for one team, while a second team stays
   * locked out. A ruin of an absent mod has nothing to play.
   */
  static void playShipped(GameTestHelper helper, String id, int slot) {
    var shipped = RuinRegistry.get(id).orElse(null);
    helper.assertTrue(shipped != null, "Missing definition " + id);
    if (!shipped.available(RuinRegistry::modLoaded)) {
      helper.succeed();
      return;
    }
    var server = helper.getLevel().getServer();
    ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(shipped.dimension())));
    helper.assertTrue(level != null, "No level " + shipped.dimension());
    var definition = qaCopy(shipped);
    int y = switch (definition.placement().mode()) {
      case SURFACE -> helper.getLevel().getSeaLevel();
      case CAVERN -> 40;
      case SKY -> 90;
    };
    BlockPos origin = new BlockPos(52_000 + slot * 256, y, -52_000);
    var job = RuinPlacement.startAt(level, definition, origin).orElseThrow();
    List<List<net.minecraft.world.level.ChunkPos>> held = new ArrayList<>();
    ServerPlayer[] players = new ServerPlayer[2];
    long started = System.nanoTime();
    Stop stop = new Stop();
    helper.startSequence()
        .thenWaitUntil(() -> awaitJob(helper, job, started, stop))
        .thenExecute(guarded(() -> {
          stop.check(helper);
          held.add(hold(level, job.result().box()));
          players[0] = arrive(helper, "Play" + slot + "A", new BlockPos(1, 1, 1));
          players[1] = arrive(helper, "Play" + slot + "B", new BlockPos(2, 1, 1));
          var pedestal = job.result().markers(RuinMarkers.Kind.PEDESTAL).getFirst().pos();
          teleport(players[0], level, pedestal);
          teleport(players[1], level, pedestal);
        }))
        .thenWaitUntil(() -> {
          if (stop.reason != null) return;
          if (System.nanoTime() - started > WALL_LIMIT_NANOS) {
            stop.reason = "The ruin's chunks did not load";
            return;
          }
          for (var chunk : held.getFirst())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
        })
        .thenExecute(guarded(() -> {
          stop.check(helper);
          var ruin = job.result();
          var a = players[0];
          var b = players[1];
          assertMarkersPlaced(helper, level, definition, ruin);
          var pedestal = ruin.markers(RuinMarkers.Kind.PEDESTAL).getFirst().pos();
          var piece = BuiltInRegistries.ITEM.get(ResourceLocation.parse(definition.piece()));
          click(a, pedestal);
          helper.assertTrue(a.getInventory().countItem(piece) == 0, "The pedestal gave its piece before the challenges");
          RuinGates.refresh(a);
          for (var gate : ruin.markers(RuinMarkers.Kind.GATE))
            helper.assertTrue(!RuinGates.open(a, gate.pos()), "A gate opened before its challenges: " + gate);
          for (var challenge : definition.challenges().values()) solve(helper, level, ruin, definition, challenge, a);
          teleport(a, level, pedestal);
          click(a, pedestal);
          helper.assertTrue(a.getInventory().countItem(piece) == 1, "No " + definition.piece() + " after every challenge");
          RuinGates.refresh(a);
          RuinGates.refresh(b);
          for (var gate : ruin.markers(RuinMarkers.Kind.GATE)) {
            helper.assertTrue(RuinGates.open(a, gate.pos()), "The solving team cannot pass " + gate);
            helper.assertTrue(!RuinGates.open(b, gate.pos()), "Another team passes " + gate);
          }
          click(b, pedestal);
          helper.assertTrue(b.getInventory().countItem(piece) == 0, "The other team took a piece without playing");
          helper.assertTrue(!StructureProtection.check(level, pedestal.below(), ProtectionRules.Action.BREAK,
              StructureProtection.actor(b)).allowed(), "The ruin is not protected");
          var lore = ruin.markers(RuinMarkers.Kind.LORE);
          teleport(a, level, lore.isEmpty() ? pedestal : lore.getFirst().pos());
          RuinChallenges.visits(server);
          helper.assertTrue(team(a).visited.contains(definition.id()), "The visit was not recorded");
          LOGGER.info("Ruin QA: played {} at {} in {}: {} challenges, {} gates, {} markers", id, ruin.box(),
              level.dimension().location(), definition.challenges().size(), ruin.markers(RuinMarkers.Kind.GATE).size(),
              ruin.markers().size());
        }))
        .thenExecute(guarded(() -> {
          for (var player : players) if (player != null) leave(player);
          for (var chunks : held)
            for (var chunk : chunks) level.getChunkSource().removeRegionTicket(PLAY, chunk, 0, chunk);
          RuinData.get(server).remove(ResourceLocation.parse(definition.id()));
          RuinProgress.get(server).forget(definition.id());
          RuinRegistry.remove(definition.id());
          StructureProtection.invalidate(server);
        }))
        .thenSucceed();
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void signalTowerPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:signal_tower", 0);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void sunkenWorkshopPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:sunken_workshop", 1);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void viaductPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:viaduct", 2);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void domeGreenhousePlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:dome_greenhouse", 3);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void netherFoundryPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:nether_foundry", 4);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void cliffObservatoryPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:cliff_observatory", 5);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void twilightSanctuaryPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:twilight_sanctuary", 6);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void sunAntechamberPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:sun_antechamber", 7);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void lightTemplePlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:light_temple", 8);
  }

  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void voidObservatoryPlaysFromItsMarkers(GameTestHelper helper) {
    playShipped(helper, "entrelumen:void_observatory", 9);
  }
}
