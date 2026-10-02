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
    return qaCopy(definition, definition.placement());
  }

  /** A QA copy searched in another ring (a small one keeps a test's sea small). */
  static RuinDefinitions.Definition qaCopy(RuinDefinitions.Definition definition, RuinDefinitions.Placement placement) {
    var copy = new RuinDefinitions.Definition("entrelumen:qa_" + definition.name() + "_"
        + UUID.randomUUID().toString().substring(0, 6), definition.act(), definition.dimension(), definition.mods(),
        definition.scale(), definition.template(), placement, definition.piece(), definition.project(),
        definition.loot(), definition.challenges(), definition.gates(), definition.pedestal(), definition.dormant(),
        definition.gifts());
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

  /**
   * The Atlas recipe replaces a lost Atlas once the team took one from the Signal Tower's pedestal, not
   * before (Elias, 27 September): until then the inventory's grid gives no result.
   */
  @GameTest(template = "empty", timeoutTicks = 200, batch = "ruins")
  public static void theAtlasRecipeWaitsForTheSignalTowersPedestal(GameTestHelper helper) {
    var player = arrive(helper, "AtlasCopier", new BlockPos(1, 1, 1));
    var menu = player.inventoryMenu;
    var atlas = BuiltInRegistries.ITEM.get(ResourceLocation.parse(AtlasGate.ATLAS));
    try {
      var campaign = Entrelumen.current(player);
      campaign.act = 1;
      team(player).gifts.remove(AtlasGate.ATLAS);
      menu.getSlot(1).set(new ItemStack(Items.COPPER_INGOT));
      menu.getSlot(3).set(new ItemStack(Items.BOOK));
      helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "An Atlas before the pedestal: " + menu.getSlot(0).getItem());
      team(player).gifts.add(AtlasGate.ATLAS);
      menu.slotsChanged(menu.getCraftSlots());
      helper.assertTrue(menu.getSlot(0).getItem().is(atlas), "No Atlas after the pedestal: " + menu.getSlot(0).getItem());
      team(player).gifts.remove(AtlasGate.ATLAS);
      campaign.act = 2;
      menu.slotsChanged(menu.getCraftSlots());
      helper.assertTrue(menu.getSlot(0).getItem().is(atlas), "A campaign past act I cannot replace its Atlas");
      helper.succeed();
    } finally {
      menu.getCraftSlots().clearContent();
      leave(player);
    }
  }

  // ---- A ring under water (Elias, 27 September: a ruin never fails to place) ----------------------

  /** The superflat test world's ground and the sea the next two cases raise on it. */
  static final int FLAT_GROUND = -61, SEABED = -62, SEA = -52;

  /**
   * A walled sea over a square of {@code half} around {@code (cx, cz)}: a gravel seabed at
   * {@link #SEABED} and water up to {@link #SEA}, with a stone rim so that it does not spill onto the
   * flat land around it.
   */
  static void sea(ServerLevel level, int cx, int cz, int half) {
    var cursor = new BlockPos.MutableBlockPos();
    for (int x = cx - half; x <= cx + half; x++)
      for (int z = cz - half; z <= cz + half; z++) {
        boolean rim = x == cx - half || x == cx + half || z == cz - half || z == cz + half;
        for (int y = SEABED - 1; y <= SEA + 1; y++) {
          var state = rim ? (y <= SEA ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState())
              : y <= SEABED ? Blocks.GRAVEL.defaultBlockState()
              : y <= SEA ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
          if (!level.getBlockState(cursor.set(x, y, z)).equals(state))
            level.setBlock(cursor, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
      }
  }

  /** Raises a sea, then runs a ring search for a small-ring QA copy of the Cliff Observatory from its middle. */
  static void searchOverSea(GameTestHelper helper, int cx, int cz, int half, int maxRings,
      java.util.function.BiConsumer<RuinPlacement.Job, BlockPos> check) {
    var level = helper.getLevel();
    var server = level.getServer();
    var shipped = RuinRegistry.get("entrelumen:cliff_observatory").orElseThrow();
    var ring = new RuinDefinitions.Placement(shipped.placement().mode(), shipped.placement().trigger(), 24, 48);
    var definition = qaCopy(shipped, ring);
    var id = ResourceLocation.parse(definition.id());
    var center = new BlockPos(cx, FLAT_GROUND, cz);
    var area = new BoundingBox(cx - half, SEABED - 1, cz - half, cx + half, SEA + 1, cz + half);
    List<List<net.minecraft.world.level.ChunkPos>> held = new ArrayList<>();
    held.add(hold(level, area));
    RuinPlacement.Job[] job = new RuinPlacement.Job[1];
    long started = System.nanoTime();
    Stop stop = new Stop();
    helper.startSequence()
        .thenWaitUntil(() -> {
          refresh(level, held.getFirst());
          for (var chunk : held.getFirst())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
        })
        .thenExecute(guarded(() -> {
          sea(level, cx, cz, half);
          RuinData.get(server).remove(id);
          job[0] = RuinPlacement.start(level, definition, center).orElseThrow();
          job[0].maxRings = maxRings;
        }))
        .thenWaitUntil(() -> {
          refresh(level, held.getFirst());
          awaitJob(helper, job[0], started, stop);
        })
        .thenExecute(guarded(() -> {
          stop.check(helper);
          held.add(hold(level, job[0].result().box().inflatedBy(64)));
        }))
        .thenWaitUntil(() -> {
          refresh(level, held.getLast());
          for (var chunk : held.getLast())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
        })
        .thenExecute(guarded(() -> {
          try {
            check.accept(job[0], center);
          } finally {
            for (var chunks : held)
              for (var chunk : chunks) level.getChunkSource().removeRegionTicket(PLAY, chunk, 0, chunk);
            RuinData.get(server).remove(id);
            RuinRegistry.remove(definition.id());
            StructureProtection.invalidate(server);
          }
        }))
        .thenSucceed();
  }

  /** Every site of the ruin's own ring is under water: the search widens to the next ring and finds land. */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void aRingUnderWaterWidensToTheNextRing(GameTestHelper helper) {
    int cx = -56_000, cz = -56_000;
    searchOverSea(helper, cx, cz, 96, RuinRules.WIDER_RINGS, (job, center) -> {
      var ruin = job.result();
      var p = job.prepared;
      double distance = Math.hypot(ruin.center().getX() - center.getX(), ruin.center().getZ() - center.getZ());
      int[] next = RuinRules.ring(24, 48, 1);
      helper.assertTrue(job.ring >= 1 && job.islet == null, "Ring " + job.ring + ", islet " + job.islet);
      helper.assertTrue(distance >= next[0] - 32 && distance <= next[1] + 32, "Not in the next ring: " + distance);
      helper.assertTrue(ruin.origin().getY() + p.ground() == FLAT_GROUND, "Not on the land past the sea: ground "
          + (ruin.origin().getY() + p.ground()));
      LOGGER.info("Ruin QA: a ring under water; placed {} blocks out, in ring {}", (int) distance, job.ring);
    });
  }

  /**
   * No ring has land (an ocean world): the ruin stands on an islet raised from the gravel seabed, flat
   * around it a little over the water, sloping into the sea, solid down to the seabed.
   */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void anOceanWithNoLandGetsAnIsletUnderTheRuin(GameTestHelper helper) {
    int cx = -56_000, cz = -48_000;
    searchOverSea(helper, cx, cz, 128, 0, (job, center) -> {
      var level = helper.getLevel();
      var ruin = job.result();
      var p = job.prepared;
      var shape = p.shape();
      var origin = ruin.origin();
      var cursor = new BlockPos.MutableBlockPos();
      helper.assertTrue(job.islet != null && job.isletBuilt, "No islet: " + job.islet);
      int ground = origin.getY() + p.ground();
      helper.assertTrue(ground == SEA + RuinTerrain.ISLET_HEIGHT, "The ruin's ground " + ground + " is not on the islet");
      // Nothing floats: under every platform column, solid down to the seabed.
      int platforms = 0;
      for (int c = 0; c < p.sizeX() * p.sizeZ(); c++) {
        if (!shape.platform()[c]) continue;
        int x = origin.getX() + c % p.sizeX(), z = origin.getZ() + c / p.sizeX();
        for (int y = origin.getY() + shape.low()[c] - 1; y > SEABED; y--)
          helper.assertTrue(!level.getBlockState(cursor.set(x, y, z)).canBeReplaced(), "A gap under the ruin at " + cursor);
        platforms++;
      }
      helper.assertTrue(platforms > 200, "Only " + platforms + " platform columns");
      // The islet: flat around the ruin, then down into the sea, every column solid from the seabed up,
      // and made of the seabed's gravel.
      int x0 = origin.getX(), z0 = origin.getZ(), x1 = x0 + p.sizeX() - 1, z1 = z0 + p.sizeZ() - 1;
      int flat = 0, shore = 0, under = 0, gravel = 0, stray = 0;
      for (int x = x0 - 50; x <= x1 + 50; x++)
        for (int z = z0 - 50; z <= z1 + 50; z++) {
          double d = RuinTerrain.outside(x, z, x0, z0, x1, z1);
          if (d == 0) continue;
          int top = SEABED;
          while (top < SEA + 4 && !level.getBlockState(cursor.set(x, top + 1, z)).canBeReplaced()) top++;
          for (int y = top + 1; y <= SEA + 4; y++)
            helper.assertTrue(level.getBlockState(cursor.set(x, y, z)).canBeReplaced(), "Something floats over the islet at " + cursor);
          if (d > 1 && d <= RuinTerrain.MAX_MARGIN) {
            helper.assertTrue(top == ground, "The islet is not flat by the ruin at " + x + "," + z + ": " + top);
            flat++;
          }
          if (top == SEA || top == SEA + 1) shore++;
          if (top > SEABED && top < SEA) under++;
          for (int y = SEABED + 1; y <= top; y++) {
            var state = level.getBlockState(cursor.set(x, y, z));
            if (state.is(Blocks.GRAVEL)) gravel++;
            else if (d > RuinTerrain.MAX_MARGIN) stray++;
          }
        }
      helper.assertTrue(flat > 500, "Only " + flat + " flat columns by the ruin");
      helper.assertTrue(shore > 50 && under > 50, "No shore or slope: " + shore + " / " + under);
      helper.assertTrue(gravel > 5000 && stray == 0, "The islet is not the seabed's gravel: " + gravel + ", " + stray + " other");
      helper.assertTrue(RuinData.get(level.getServer()).find(ruin.id()).isPresent(), "The ruin is not registered");
      LOGGER.info("Ruin QA: an islet under the ruin, {} flat columns, {} on the shore, {} under the sea, {} gravel",
          flat, shore, under, gravel);
    });
  }

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

  /** A site search of a shipped surface ruin from the noise alone, around {@code (cx, cz)}, in the ring given. */
  static RuinPlacement.Sites searchNoise(GameTestHelper helper, RuinPlacement.Prepared p, String ruin, int cx, int cz,
      int[] ring, double[] border) {
    var level = helper.getLevel();
    var source = level.getChunkSource();
    return RuinPlacement.site(new RuinPlacement.Siting(source.getGenerator(), source.randomState(), level,
        source.getGenerator().getBiomeSource(), RuinDefinitions.Mode.SURFACE, level.getSeed(), ruin, cx, FLAT_GROUND, cz,
        ring[0], ring[1], p.sizeX(), p.sizeZ(), p.above(), p.ground(), List.of(), 0, border));
  }

  /**
   * The test world is the thin default superflat: the Sunken Workshop (17 layers under its ground) and
   * the Dome Greenhouse (6) still find sites, standing as high as the world's bottom needs.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void deepRuinsFindSitesOnAThinSuperflat(GameTestHelper helper) throws Exception {
    var level = helper.getLevel();
    var server = level.getServer();
    for (String id : List.of("entrelumen:sunken_workshop", "entrelumen:dome_greenhouse")) {
      var definition = RuinRegistry.get(id).orElseThrow();
      var p = RuinPlacement.prepare(server, ResourceLocation.parse(definition.template()));
      var sites = searchNoise(helper, p, id, 30_000, -30_000, new int[] {definition.placement().min(),
          definition.placement().max()}, null);
      helper.assertTrue(!sites.dry().isEmpty(), id + ": no site on the superflat");
      int floor = RuinRules.surfaceFloor(FLAT_GROUND, level.getMinBuildHeight(), p.ground());
      for (var site : sites.dry()) {
        helper.assertTrue(site.floor() == floor, id + ": floor " + site.floor() + ", expected " + floor);
        helper.assertTrue(RuinPlacement.fits(level, site.floor() - p.ground(), p.sizeY()), id + ": does not fit at " + site);
      }
    }
    helper.succeed();
  }

  /**
   * A world border 300 blocks around the search's centre: the definition's ring (400..1200) lies past
   * it and finds nothing; cut to the border it finds sites, all of them inside with their margin.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void aSearchStaysInsideTheWorldBorder(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var definition = RuinRegistry.get("entrelumen:cliff_observatory").orElseThrow();
    var p = RuinPlacement.prepare(server, ResourceLocation.parse(definition.template()));
    int cx = 34_000, cz = -34_000, margin = RuinPlacement.borderMargin(RuinDefinitions.Mode.SURFACE);
    double[] border = {cx - 300, cz - 300, cx + 300, cz + 300};
    int[] ring = {400, 1200};
    var outside = searchNoise(helper, p, definition.id(), cx, cz, ring, border);
    helper.assertTrue(outside.dry().isEmpty() && outside.wet().isEmpty(), "Sites past the border: " + outside.dry());
    int fit = RuinRules.borderFit(border, cx, cz, p.sizeX(), p.sizeZ(), margin);
    int[] clamped = RuinRules.clampRing(ring, fit);
    helper.assertTrue(clamped != null && clamped[1] == fit && clamped[0] == 0, "Ring cut to " + Arrays.toString(clamped));
    var inside = searchNoise(helper, p, definition.id(), cx, cz, clamped, border);
    helper.assertTrue(!inside.dry().isEmpty(), "No site inside the border");
    for (var site : inside.dry())
      helper.assertTrue(RuinRules.inside(border, site.x(), site.z(), p.sizeX(), p.sizeZ(), margin), "Past the border: " + site);
    helper.succeed();
  }

  /** A piece of a test structure: only its box matters. */
  static net.minecraft.world.level.levelgen.structure.StructurePiece testPiece(BoundingBox box) {
    return new net.minecraft.world.level.levelgen.structure.StructurePiece(
        net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.JIGSAW, 0, box) {
      @Override
      protected void addAdditionalSaveData(
          net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
          net.minecraft.nbt.CompoundTag tag) {}

      @Override
      public void postProcess(net.minecraft.world.level.WorldGenLevel level,
          net.minecraft.world.level.StructureManager structures,
          net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random,
          BoundingBox box, net.minecraft.world.level.ChunkPos chunk, BlockPos pos) {}
    };
  }

  /**
   * The structure check reads the exact boxes of the pieces of every structure referenced by the chunks
   * under the grown footprint: a start referenced twice counts once, a box beside the footprint or wholly
   * under the lowest the blend reaches does not count, and the shaping leaves the columns under a piece.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void theStructureCheckReadsPieceBoxes(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos base = helper.absolutePos(new BlockPos(2, 0, 2));
    var chunk = level.getChunkAt(base);
    var neighbour = level.getChunk(chunk.getPos().x + 1, chunk.getPos().z);
    var structure = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
        .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_PLAINS);
    var pieceBox = new BoundingBox(base.getX() - 3, base.getY() - 4, base.getZ() - 3, base.getX() + 3, base.getY() + 6,
        base.getZ() + 3);
    // The footprint grown by the margin: the piece's chunk and the next one east.
    var grown = new BoundingBox(base.getX() - 8, level.getMinBuildHeight(), base.getZ() - 8, base.getX() + 24,
        level.getMaxBuildHeight() - 1, base.getZ() + 8);
    int before = RuinPlacement.structureHits(level, grown, base.getY() - 32);
    Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart>
        starts = new HashMap<>(chunk.getAllStarts());
    var references = copyReferences(chunk.getAllReferences());
    var neighbourReferences = copyReferences(neighbour.getAllReferences());
    try {
      var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunk.getPos(), 0,
          new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(testPiece(pieceBox))));
      chunk.setStartForStructure(structure, start);
      chunk.addReferenceForStructure(structure, chunk.getPos().toLong());
      neighbour.addReferenceForStructure(structure, chunk.getPos().toLong());
      int hits = RuinPlacement.structureHits(level, grown, base.getY() - 32);
      helper.assertTrue(hits == before + 1, "One structure, referenced twice: " + hits + " over " + before);
      var obstacles = RuinPlacement.obstacles(level, grown, base.getY() - 32);
      helper.assertTrue(obstacles.pieces().contains(pieceBox), "Its piece's box: " + obstacles.pieces());
      // Beside the footprint (still in the referencing chunks) and wholly under the blend: no hit.
      var east = new BoundingBox(base.getX() + 4, grown.minY(), grown.minZ(), grown.maxX(), grown.maxY(), grown.maxZ());
      helper.assertTrue(!RuinPlacement.obstacles(level, east, base.getY() - 32).pieces().contains(pieceBox),
          "A piece beside the box");
      helper.assertTrue(!RuinPlacement.obstacles(level, grown, base.getY() + 7).pieces().contains(pieceBox),
          "A piece under the blend's reach");
      // The shaping's guard: the piece's columns of a grid laid over the grown box, and no other.
      int width = grown.getXSpan(), depth = grown.getZSpan();
      boolean[] guarded = RuinRules.covered(List.<int[]>of(new int[] {pieceBox.minX(), pieceBox.minZ(), pieceBox.maxX(),
          pieceBox.maxZ()}), grown.minX(), grown.minZ(), width, depth);
      int count = 0;
      for (boolean g : guarded) if (g) count++;
      helper.assertTrue(count == 7 * 7, "Guarded columns: " + count);
    } finally {
      chunk.setAllStarts(starts);
      chunk.setAllReferences(references);
      neighbour.setAllReferences(neighbourReferences);
    }
    helper.succeed();
  }

  static Map<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> copyReferences(
      Map<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> references) {
    Map<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> copy = new HashMap<>();
    references.forEach((structure, refs) -> copy.put(structure, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(refs)));
    return copy;
  }

  /** The Sunken Workshop's pit froze in a cold biome: draining takes the ice with the water, top layer first. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void drainingTakesTheIceOverThePit(GameTestHelper helper) {
    var level = helper.getLevel();
    // A stone basin 3x3 inside, water at y 1 and its ice lid at y 2.
    for (int x = 0; x <= 4; x++)
      for (int z = 0; z <= 4; z++) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        boolean wall = x == 0 || z == 0 || x == 4 || z == 4;
        helper.setBlock(new BlockPos(x, 1, z), wall ? Blocks.STONE : Blocks.WATER);
        helper.setBlock(new BlockPos(x, 2, z), wall ? Blocks.STONE : (x + z) % 2 == 0 ? Blocks.ICE : Blocks.FROSTED_ICE);
      }
    var drain = new RuinData.PlacedMarker(RuinMarkers.parse("drain challenge=pit size=3,2,3").orElseThrow(),
        helper.absolutePos(new BlockPos(1, 1, 1)));
    helper.assertTrue(!RuinWorkshop.drainStep(level, drain), "The first step drains something");
    for (int x = 1; x <= 3; x++)
      for (int z = 1; z <= 3; z++) {
        helper.assertBlockNotPresent(Blocks.ICE, new BlockPos(x, 2, z));
        helper.assertBlockNotPresent(Blocks.FROSTED_ICE, new BlockPos(x, 2, z));
      }
    boolean dry = false;
    for (int i = 0; i < 8 && !dry; i++) dry = RuinWorkshop.drainStep(level, drain);
    helper.assertTrue(dry, "The pit never drained");
    for (int x = 1; x <= 3; x++)
      for (int y = 1; y <= 2; y++)
        for (int z = 1; z <= 3; z++)
          helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir(),
              "Left at " + x + "," + y + "," + z);
    helper.succeed();
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
      // Chorus fruit cannot hop past a closed gate: aimed inside the ruin, or aimed above it and
      // falling into it, the teleport is cancelled for B; A, whose gates are open, and a target
      // outside the ruin pass.
      var inside = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
      var above = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 9, 2)));
      var outside = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)).offset(0, 0, 40));
      helper.assertTrue(fixture.ruin.box().isInside(RuinGates.landing(helper.getLevel(), BlockPos.containing(above))),
          "The QA aim above the ruin does not land in it");
      helper.assertTrue(chorusCancelled(b, inside) && chorusCancelled(b, above),
          "Chorus fruit carried a team past a gate it did not open");
      helper.assertTrue(!chorusCancelled(a, inside) && !chorusCancelled(b, outside),
          "Chorus fruit was refused to the team that opened the gates, or outside the ruin");
      b.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
      helper.assertTrue(!chorusCancelled(b, inside), "Creative mode was refused a chorus teleport");
      leave(a);
      leave(b);
    }
    helper.succeed();
  }

  /** Posts a chorus fruit teleport of {@code player} to {@code target}; true when it was cancelled. */
  static boolean chorusCancelled(ServerPlayer player, Vec3 target) {
    var event = new net.neoforged.neoforge.event.entity.EntityTeleportEvent.ChorusFruit(player, target.x, target.y,
        target.z);
    return net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled();
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
      helper.assertTrue(mob.getTags().contains(RuinBosses.TAG), "The guardian carries no entity tag");
      // Other entities are left alone: joining and dying give them no NeoForge data.
      var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
      pig.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 4))));
      helper.assertTrue(helper.getLevel().addFreshEntity(pig), "A pig could not join");
      pig.hurt(helper.getLevel().damageSources().playerAttack(b), 10_000f);
      helper.assertTrue(!pig.isAlive() && !pig.saveWithoutId(new net.minecraft.nbt.CompoundTag()).contains("NeoForgeData"),
          "A plain entity got persistent data from the guardian listeners");
      // A guardian of a finished run (saved with a chunk, no live group) never joins again.
      var stale = net.minecraft.world.entity.EntityType.VINDICATOR.create(helper.getLevel());
      stale.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 0))));
      stale.addTag(RuinBosses.TAG);
      stale.getPersistentData().putString(RuinBosses.TAG, "entrelumen:qa_finished|guard|" + UUID.randomUUID());
      helper.assertTrue(!helper.getLevel().addFreshEntity(stale), "A guardian of a finished run came back");
      // One saved before the entity tag existed (persistent, named, only the persistent key) stays
      // out too; a name-tagged mob without the key still joins.
      var legacy = net.minecraft.world.entity.EntityType.VINDICATOR.create(helper.getLevel());
      legacy.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));
      legacy.setCustomName(net.minecraft.network.chat.Component.literal("Old guardian"));
      legacy.setPersistenceRequired();
      legacy.getPersistentData().putString(RuinBosses.TAG, "entrelumen:qa_finished|guard|" + UUID.randomUUID());
      helper.assertTrue(!helper.getLevel().addFreshEntity(legacy) && legacy.getTags().contains(RuinBosses.TAG),
          "A guardian saved before the entity tag came back");
      var named = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
      named.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 4))));
      named.setCustomName(net.minecraft.network.chat.Component.literal("Named pig"));
      named.setPersistenceRequired();
      helper.assertTrue(helper.getLevel().addFreshEntity(named) && !named.getTags().contains(RuinBosses.TAG),
          "A name-tagged mob was taken for a guardian");
      named.discard();
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
    // Act IV's landmark is the Temple since the roster of 26 September.
    var landmark = RuinRegistry.get("entrelumen:light_temple").orElseThrow();
    var pieces = RuinChallenges.pieces(landmark).stream().map(RuinDefinitions.Definition::piece).toList();
    boolean twilight = RuinRegistry.modLoaded("twilightforest"), aether = RuinRegistry.modLoaded("aether");
    helper.assertTrue(pieces.contains("entrelumen:sacred_flame")
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

  // ---- How the ruins meet the terrain (Elias, 26 September; RuinTerrain) ------------------------

  /**
   * Raises a test landscape: every column of {@code area} gets {@code under} up to {@code height(x, z)},
   * its top three blocks {@code surface}, and air above, up to 96 blocks over the area or the column's
   * own top if higher: a ruin left by an earlier run of the persistent test world goes, and so does the
   * natural terrain of a full-pack world.
   */
  static void landscape(ServerLevel level, BoundingBox area, java.util.function.IntBinaryOperator height,
      net.minecraft.world.level.block.state.BlockState surface, net.minecraft.world.level.block.state.BlockState under) {
    var cursor = new BlockPos.MutableBlockPos();
    var air = Blocks.AIR.defaultBlockState();
    for (int x = area.minX(); x <= area.maxX(); x++)
      for (int z = area.minZ(); z <= area.maxZ(); z++) {
        int top = height.applyAsInt(x, z);
        int ceiling = Math.min(level.getMaxBuildHeight() - 1, Math.max(area.maxY() + 96,
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z)));
        for (int y = area.minY(); y <= ceiling; y++) {
          var state = y > top ? air : y >= top - 2 ? surface : under;
          if (!level.getBlockState(cursor.set(x, y, z)).equals(state))
            level.setBlock(cursor, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
      }
  }

  /** Places a ruin centred on a test landscape, then checks it with the chunks held again. */
  static void placeOnLandscape(GameTestHelper helper, String id, BoundingBox area,
      java.util.function.IntBinaryOperator height, net.minecraft.world.level.block.state.BlockState surface,
      net.minecraft.world.level.block.state.BlockState under, java.util.function.Consumer<RuinPlacement.Job> check) {
    var level = helper.getLevel();
    var server = level.getServer();
    var definition = qaCopy(RuinRegistry.get(id).orElseThrow());
    List<List<net.minecraft.world.level.ChunkPos>> held = new ArrayList<>();
    held.add(hold(level, area));
    RuinPlacement.Job[] job = new RuinPlacement.Job[1];
    long started = System.nanoTime();
    Stop stop = new Stop();
    helper.startSequence()
        .thenWaitUntil(() -> {
          refresh(level, held.getFirst());
          for (var chunk : held.getFirst())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
        })
        .thenExecute(guarded(() -> {
          landscape(level, area, height, surface, under);
          var center = new BlockPos((area.minX() + area.maxX()) / 2, 0, (area.minZ() + area.maxZ()) / 2);
          job[0] = RuinPlacement.startCentred(level, definition, center).orElseThrow();
        }))
        .thenWaitUntil(() -> awaitJob(helper, job[0], started, stop))
        .thenExecute(guarded(() -> {
          stop.check(helper);
          held.add(hold(level, area));
        }))
        .thenWaitUntil(() -> {
          refresh(level, held.getLast());
          for (var chunk : held.getLast())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
        })
        .thenExecute(guarded(() -> {
          try {
            check.accept(job[0]);
          } finally {
            for (var chunks : held)
              for (var chunk : chunks) level.getChunkSource().removeRegionTicket(PLAY, chunk, 0, chunk);
            RuinData.get(server).remove(ResourceLocation.parse(definition.id()));
            RuinRegistry.remove(definition.id());
            StructureProtection.invalidate(server);
          }
        }))
        .thenSucceed();
  }

  /**
   * The Dome Greenhouse on a dune that rises 24 blocks over 113: no floating slab under it, no cliff at
   * its edge, a smooth blend that is gone past the margin, and its lawn the dune's own sand.
   */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void aRuinMeetsASlopeWithoutCliffsOrFloatingSlabs(GameTestHelper helper) {
    var level = helper.getLevel();
    int cx = -36_000, cz = 44_000, half = 56, bottom = level.getMinBuildHeight() + 1;
    var area = new BoundingBox(cx - half, bottom, cz - half, cx + half, bottom + 38, cz + half);
    java.util.function.IntBinaryOperator dune = (x, z) -> bottom + 14 + (x - (cx - half)) * 24 / (2 * half);
    placeOnLandscape(helper, "entrelumen:dome_greenhouse", area, dune, Blocks.SAND.defaultBlockState(),
        Blocks.SANDSTONE.defaultBlockState(), job -> {
          var p = job.prepared;
          var shape = p.shape();
          var origin = job.result().origin();
          int ground = origin.getY() + p.ground(), m = RuinTerrain.MAX_MARGIN, w = RuinPlacement.grownWidth(p);
          var cursor = new BlockPos.MutableBlockPos();
          helper.assertTrue(job.margin >= RuinTerrain.MIN_MARGIN && job.margin <= RuinTerrain.MAX_MARGIN,
              "Margin " + job.margin);
          helper.assertTrue(job.palette.surface().is(Blocks.SAND), "The site's ground is not the dune's sand: " + job.palette);
          // No floating slab: under every platform column, solid down to the dune.
          int platforms = 0;
          for (int c = 0; c < p.sizeX() * p.sizeZ(); c++) {
            if (!shape.platform()[c]) continue;
            int x = origin.getX() + c % p.sizeX(), z = origin.getZ() + c / p.sizeX();
            for (int y = origin.getY() + shape.low()[c] - 1; y > dune.applyAsInt(x, z); y--)
              if (!p.cell(c % p.sizeX(), y - origin.getY(), c / p.sizeX()))
                helper.assertTrue(!level.getBlockState(cursor.set(x, y, z)).canBeReplaced(), "A gap under the platform at " + cursor);
            platforms++;
          }
          helper.assertTrue(platforms > 500, "Only " + platforms + " platform columns");
          // Around the platform: flush at its edge, smooth over the margin, untouched past it.
          int steepest = 0, flushChecked = 0, untouched = 0;
          for (int i = 0; i < shape.distance().length; i++) {
            int x = i % w - m, z = i / w - m;
            boolean inside = x >= 0 && z >= 0 && x < p.sizeX() && z < p.sizeZ();
            if (inside && shape.top()[z * p.sizeX() + x] >= 0) continue;     // the ruin's own blocks
            int wx = origin.getX() + x, wz = origin.getZ() + z;
            int here = RuinPlacement.groundAt(level, cursor, wx, wz,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1);
            float d = shape.distance()[i];
            if (d > 0 && d <= 1.5f) {
              helper.assertTrue(Math.abs(here - ground) <= 1, "A cliff at the edge: " + here + " by ground " + ground
                  + " at " + wx + "," + wz);
              flushChecked++;
            }
            if (d > 0 && d <= job.margin && x + 1 < p.sizeX() + m) {
              int east = RuinPlacement.groundAt(level, cursor, wx + 1, wz,
                  level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx + 1, wz) - 1);
              if (!(x + 1 >= 0 && z >= 0 && x + 1 < p.sizeX() && z < p.sizeZ() && shape.top()[z * p.sizeX() + x + 1] >= 0))
                steepest = Math.max(steepest, Math.abs(east - here));
            }
            if (d >= job.margin && !inside) {
              helper.assertTrue(here == dune.applyAsInt(wx, wz), "Changed past the margin at " + wx + "," + wz);
              untouched++;
            }
          }
          helper.assertTrue(flushChecked > 100 && untouched > 100, "Checked " + flushChecked + " / " + untouched);
          helper.assertTrue(steepest <= 3, "A step of " + steepest + " in the blend");
          // The template's soil is the dune's sand outside the art's keep_soil beds; the garden under the
          // dome is a designed bed and keeps its own.
          int stray = 0, kept = 0, sand = 0;
          for (int x = 0; x < p.sizeX(); x++)
            for (int z = 0; z < p.sizeZ(); z++)
              for (int y = 0; y < p.sizeY(); y++) {
                var state = level.getBlockState(cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
                if (RuinTerrain.soil(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                  if (RuinTerrain.kept(x, y, z, shape.keep())) kept++;
                  else stray++;
                }
              }
          for (int i = 0; i < shape.distance().length; i++) {
            float d = shape.distance()[i];
            if (d <= 0 || d > job.margin) continue;
            int wx = origin.getX() + i % w - m, wz = origin.getZ() + i / w - m;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            if (level.getBlockState(cursor.set(wx, top, wz)).is(Blocks.SAND)) sand++;
          }
          helper.assertTrue(stray == 0, stray + " soil blocks outside the garden stayed in the greenhouse");
          helper.assertTrue(shape.keep().isEmpty() || kept > 100, "The garden lost its drawn soil: " + kept);
          helper.assertTrue(sand > 300, "Only " + sand + " sand blocks around the greenhouse");
          LOGGER.info("Ruin QA terrain: greenhouse on a dune, ground {}, margin {}, steepest step {}, lowest {}",
              ground, job.margin, steepest, job.lowest);
        });
  }

  /**
   * The Cliff Observatory on a dune (Elias, 27 September): its crag is the dune's own rock, sandstone
   * band for band, not a grey boulder on the sand; the observatory's calcite, copper and tuff masonry
   * stays as drawn.
   */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void theObservatorysCragIsTheSitesOwnRock(GameTestHelper helper) {
    var level = helper.getLevel();
    int cx = -36_000, cz = 52_000, half = 44, bottom = level.getMinBuildHeight() + 1;
    var area = new BoundingBox(cx - half, bottom, cz - half, cx + half, bottom + 30, cz + half);
    java.util.function.IntBinaryOperator dune = (x, z) -> bottom + 14 + (x - (cx - half)) * 4 / (2 * half);
    placeOnLandscape(helper, "entrelumen:cliff_observatory", area, dune, Blocks.SAND.defaultBlockState(),
        Blocks.SANDSTONE.defaultBlockState(), job -> {
          var p = job.prepared;
          var origin = job.result().origin();
          helper.assertTrue(!p.shape().rock().isEmpty(), "The observatory has no site_rock box");
          helper.assertTrue(job.palette.rocks().equals(List.of(Blocks.SANDSTONE.defaultBlockState())),
              "The dune's rock is not its sandstone: " + job.palette.rocks());
          var cursor = new BlockPos.MutableBlockPos();
          int grey = 0, sandstone = 0, calcite = 0, copper = 0, tuffBricks = 0;
          for (int x = 0; x < p.sizeX(); x++)
            for (int y = 0; y < p.sizeY(); y++)
              for (int z = 0; z < p.sizeZ(); z++) {
                var state = level.getBlockState(cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
                if (state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)) {
                  if (grey++ == 0) LOGGER.info("Ruin QA terrain: grey rock {} at local {},{},{}", state, x, y, z);
                }
                else if (state.is(Blocks.SANDSTONE)) sandstone++;
                else if (state.is(Blocks.CALCITE)) calcite++;
                else if (state.is(Blocks.WAXED_OXIDIZED_CUT_COPPER)) copper++;
                else if (state.is(Blocks.TUFF_BRICKS)) tuffBricks++;
              }
          helper.assertTrue(grey == 0, grey + " grey rock blocks stayed in the crag");
          helper.assertTrue(sandstone > 2000, "Only " + sandstone + " sandstone blocks in the crag");
          helper.assertTrue(calcite > 100 && copper > 100 && tuffBricks > 100,
              "The observatory's masonry changed: calcite " + calcite + ", copper " + copper + ", tuff bricks " + tuffBricks);
          LOGGER.info("Ruin QA terrain: the observatory's crag on a dune, {} sandstone, masonry kept ({} calcite, {} copper, {} tuff bricks)",
              sandstone, calcite, copper, tuffBricks);
        });
  }

  /**
   * The Viaduct across a valley: its hub stands on a plateau and its east arm crosses a valley 18 blocks
   * deep. The piers there go down to the valley floor in their own block, like an aqueduct's; the arches
   * between them stay open.
   */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins")
  public static void theViaductsPiersReachTheValleyFloor(GameTestHelper helper) {
    var level = helper.getLevel();
    int cx = -44_000, cz = 44_000, half = 62, bottom = level.getMinBuildHeight() + 1, plateau = bottom + 26;
    var area = new BoundingBox(cx - half, bottom, cz - half, cx + half, plateau, cz + half);
    java.util.function.IntBinaryOperator valley = (x, z) -> plateau - Math.clamp((12 - Math.abs(x - (cx + 31))) * 2, 0, 18);
    placeOnLandscape(helper, "entrelumen:viaduct", area, valley, Blocks.GRASS_BLOCK.defaultBlockState(),
        Blocks.STONE.defaultBlockState(), job -> {
          var p = job.prepared;
          var shape = p.shape();
          var origin = job.result().origin();
          int ground = origin.getY() + p.ground();
          helper.assertTrue(ground == plateau, "The hub is not on the plateau: " + ground + " vs " + plateau);
          var cursor = new BlockPos.MutableBlockPos();
          int tall = 0, tallest = 0, open = 0;
          for (int c = 0; c < p.sizeX() * p.sizeZ(); c++) {
            int x = origin.getX() + c % p.sizeX(), z = origin.getZ() + c / p.sizeX();
            int floor = valley.applyAsInt(x, z);
            if (floor >= plateau - 8) continue;
            if (shape.thin(c)) {
              var pier = shape.pier()[c];
              int from = origin.getY() + shape.low()[c] - 1;
              for (int y = from; y > floor; y--)
                helper.assertTrue(level.getBlockState(cursor.set(x, y, z)).equals(pier),
                    "A pier stops short at " + cursor + ": " + level.getBlockState(cursor));
              tall++;
              tallest = Math.max(tallest, from - floor);
            } else if (!shape.contact()[c] && shape.low()[c] > p.ground() + 2) {
              // An arch over the valley: open under its span.
              if (level.getBlockState(cursor.set(x, origin.getY() + shape.low()[c] - 1, z)).isAir()) open++;
            }
          }
          helper.assertTrue(tall > 20 && tallest >= 14, "Piers over the valley: " + tall + " columns, the tallest " + tallest);
          helper.assertTrue(open > 20, "Only " + open + " open arch columns over the valley");
          LOGGER.info("Ruin QA terrain: viaduct over a valley, {} pier columns down to its floor, the tallest {} blocks, "
              + "{} open arch columns", tall, tallest, open);
        });
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

  /** Adds the tickets again, which restarts their expiry: a fresh test world can take long to generate. */
  static void refresh(ServerLevel level, List<net.minecraft.world.level.ChunkPos> chunks) {
    for (var chunk : chunks) level.getChunkSource().addRegionTicket(PLAY, chunk, 0, chunk);
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
        if (group == null && !team(player).solved.contains(RuinProgress.key(definition.id(), c)))
          LOGGER.warn("Ruin QA: no guardians rose for {} {}: {} in {} at {}, {} blocks from the marker {}, creative {},"
                  + " spectator {}, difficulty {}, registered {}, defined {}", definition.id(), c,
              player.getName().getString(), player.level().dimension().location(), player.blockPosition(),
              String.format(Locale.ROOT, "%.1f", Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(spawn)))), spawn,
              player.isCreative(), player.isSpectator(), level.getDifficulty(),
              RuinData.get(level.getServer()).find(ruin.id()).isPresent(), RuinRegistry.get(ruin.id()).isPresent());
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
      case RELAY -> playRelay(helper, level, ruin, c, player);
    }
    helper.assertTrue(team(player).solved.contains(RuinProgress.key(definition.id(), c)),
        definition.id() + ": " + c + " (" + challenge.type() + ") was not solved from its markers");
  }

  /**
   * Plays a light relay the way a player would, from the solution its markers carry (solve=): the
   * starting pieces light nothing, then floor by floor each receptor lights only once its floor's
   * pieces are set, and on the last floor the amber decoy's colours reach the lens before the answer.
   */
  static void playRelay(GameTestHelper helper, ServerLevel level, RuinData.Ruin ruin, String c, ServerPlayer player) {
    var floors = RuinRelay.floors(level, ruin, c);
    helper.assertTrue(!floors.isEmpty(), "The relay has no floors");
    var first = floors.getFirst();
    helper.assertTrue("true".equals(first.light().marker().param("hand", "false")), "The first floor is not lit by hand");
    click(player, first.light().pos());
    helper.assertTrue(RuinRelay.lit(level.getBlockState(first.light().pos())), "The first light did not catch");
    String key = RuinProgress.key(RuinRegistry.get(ruin.id().toString()).orElseThrow().id(), c);
    helper.assertTrue(!team(player).solved.contains(key), "The relay went white in its starting state");
    for (int i = 0; i < floors.size(); i++) {
      int floor = floors.get(i).floor();
      helper.assertTrue(floors.get(i).receptor() == null
              || !RuinRelay.lit(level.getBlockState(floors.get(i).receptor().pos())),
          "Floor " + floor + " lit its receptor in its starting state");
      if (i == floors.size() - 1) {
        var now = RuinRelay.floors(level, ruin, c).get(i);
        var trace = LightRelay.trace(now.model(), cell -> RuinRelay.open(level, RuinRelay.pos(cell)));
        helper.assertTrue(!LightRelay.satisfied(now.model(), trace),
            "The last floor reached its colours before its answer: " + trace.received());
      }
      for (var marker : ruin.markers()) {
        var m = marker.marker();
        if (!m.challenge().equals(c) || m.floor() != floor || !m.params().containsKey("solve")) continue;
        for (int k = 0; k < 8; k++) {
          boolean done = switch (m.kind()) {
            case VITRAL -> RuinRelay.turn(level, marker.pos()).id().equals(m.param("solve", ""));
            case MIRROR -> RuinRelay.facing(level.getBlockState(marker.pos())).id().substring(0, 1).equals(m.param("solve", ""));
            default -> true;
          };
          if (done) break;
          click(player, marker.pos());
        }
      }
      var receptor = floors.get(i).receptor();
      if (i < floors.size() - 1)
        helper.assertTrue(receptor != null && RuinRelay.lit(level.getBlockState(receptor.pos())),
            "Floor " + floor + "'s solution does not light its receptor");
    }
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
          // Blocks and entities: a guardian raised where the entities are still loading is not visible yet.
          refresh(level, held.getFirst());
          for (var chunk : held.getFirst())
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                && level.areEntitiesLoaded(chunk.toLong()), "Loading " + chunk);
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
          for (String gift : definition.gifts())
            helper.assertTrue(a.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse(gift))) == 1,
                "The pedestal did not give " + gift);
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
