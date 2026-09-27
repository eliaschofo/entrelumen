package dev.entrelumen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTestSequence;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Terra's Engine, the Sunken Workshop's Create puzzle, on the full pack with the pinned Create 6.0.10:
 * the shipped ruin is placed far from the test grid and played with the builds the exporter wrote
 * (tools/build_heliodor_ruins.py, data/entrelumen/ruin_solution/sunken_workshop.json). Cases: the
 * wheels turn when their sluices open and the lines carry them only with the missing pieces back;
 * one wheel cannot carry the pumps, a build with two pumps the wrong way and a build at 2R do not
 * drain, the correct build drains the pit layer by layer and pauses when it breaks; the seal opens the
 * vault for the builder's team at 45 degrees and not at 90; the engine room takes only transmission
 * parts and redstone, the sockets only their piece; and the reset floods the pit and gives parts back.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuinWorkshopFullpackGameTests {
  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  private RuinWorkshopFullpackGameTests() {}

  static final String WORKSHOP = "entrelumen:sunken_workshop";
  /** Holds the ruin's chunks for the whole case (the engine cases outlast the shared play ticket). */
  static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> ENGINE =
      net.minecraft.server.level.TicketType.create("entrelumen_qa_ruin_engine",
          Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

  static List<net.minecraft.world.level.ChunkPos> hold(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
    List<net.minecraft.world.level.ChunkPos> chunks = new ArrayList<>();
    for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++)
      for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
        var chunk = new net.minecraft.world.level.ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(ENGINE, chunk, 0, chunk);
        chunks.add(chunk);
      }
    return chunks;
  }

  static void requireSuite() {
    if (!ModList.get().isLoaded("create")) throw new IllegalStateException("Required real mod is absent: create");
  }

  /** The scripted builds, relative to the sandbox marker. */
  static JsonObject solution() {
    try (var stream = RuinWorkshopFullpackGameTests.class.getResourceAsStream("/data/entrelumen/ruin_solution/sunken_workshop.json")) {
      if (stream == null) throw new IllegalStateException("The engine solution is not in the QA jar");
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static BlockState state(String text) {
    try {
      return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
    } catch (CommandSyntaxException e) {
      throw new IllegalStateException("Bad state " + text, e);
    }
  }

  /** A placed shipped workshop, its engine site and two teams. */
  static final class Engine {
    final GameTestHelper helper;
    final ServerLevel level;
    RuinDefinitions.Definition definition;
    RuinPlacement.Job job;
    RuinData.Ruin ruin;
    RuinWorkshop.Site site;
    ServerPlayer a, b;
    final List<List<net.minecraft.world.level.ChunkPos>> held = new ArrayList<>();
    final JsonObject solution = solution();
    final RuntimeGameTestsRuins.Stop stop = new RuntimeGameTestsRuins.Stop();
    final long started = System.nanoTime();
    long mark;
    int counter;

    Engine(GameTestHelper helper, int slot) {
      this.helper = helper;
      this.level = helper.getLevel().getServer().overworld();
      var shipped = RuinRegistry.get(WORKSHOP).orElseThrow();
      definition = RuntimeGameTestsRuins.qaCopy(shipped);
      BlockPos origin = new BlockPos(60_000 + slot * 256, helper.getLevel().getSeaLevel(), -60_000);
      job = RuinPlacement.startAt(level, definition, origin).orElseThrow();
    }

    GameTestSequence placed() {
      return helper.startSequence()
          .thenWaitUntil(() -> RuntimeGameTestsRuins.awaitJob(helper, job, started, stop))
          .thenExecute(RuntimeGameTestsRuins.guarded(() -> {
            stop.check(helper);
            ruin = job.result();
            held.add(hold(level, ruin.box()));
            site = RuinWorkshop.site(level.getServer(), ruin.id());
            helper.assertTrue(site != null, "The workshop has no engine site");
            a = RuntimeGameTestsRuins.arrive(helper, "Engine" + ruin.id().getPath().hashCode() % 1000 + "A", new BlockPos(1, 1, 1));
            b = RuntimeGameTestsRuins.arrive(helper, "Engine" + ruin.id().getPath().hashCode() % 1000 + "B", new BlockPos(2, 1, 1));
            // Beside the ruin: the builds fill the engine room, and nobody should stand in them.
            RuntimeGameTestsRuins.teleport(a, level, ruin.arrival());
            RuntimeGameTestsRuins.teleport(b, level, ruin.arrival());
          }))
          .thenWaitUntil(within(2400, "The ruin's chunks load", () -> {
            for (var chunk : held.getFirst())
              helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null, "Loading " + chunk);
          }));
    }

    List<RuinData.PlacedMarker> markers(RuinMarkers.Kind kind) {
      return ruin.markers(kind);
    }

    BlockPos sandbox() {
      return markers(RuinMarkers.Kind.SANDBOX).getFirst().pos();
    }

    BlockPos center() {
      var marker = markers(RuinMarkers.Kind.SANDBOX).getFirst();
      int[] size = marker.marker().size();
      return marker.pos().offset((size[0] - 1) / 2, 0, (size[2] - 1) / 2);
    }

    void levers(Set<String> sluices) {
      for (var lever : markers(RuinMarkers.Kind.LEVER)) {
        BlockState state = level.getBlockState(lever.pos());
        boolean on = sluices.contains(lever.marker().param("sluice", ""));
        if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) != on)
          level.setBlock(lever.pos(), state.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
      }
      RuinWorkshop.sluices(level, site);
    }

    void fillSockets() {
      for (var part : markers(RuinMarkers.Kind.PART))
        level.setBlock(part.pos(), RuinPlacement.blockOf(part.marker()), Block.UPDATE_ALL);
    }

    void clearSandbox() {
      for (var sandbox : site.sandboxes)
        for (BlockPos pos : sandbox.cells())
          if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    void build(String name) {
      BlockPos origin = sandbox();
      for (var element : solution.getAsJsonObject("builds").getAsJsonArray(name)) {
        var entry = element.getAsJsonObject();
        var p = entry.getAsJsonArray("pos");
        BlockPos pos = origin.offset(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        helper.assertTrue(site.sandbox(pos) != null, "The " + name + " build leaves the sandbox at " + pos);
        level.setBlock(pos, state(entry.get("state").getAsString()), Block.UPDATE_ALL);
      }
      RuinChallenges.credit(level, center(), RuntimeGameTestsRuins.context(a));
    }

    int rpm() {
      return solution.get("rpm").getAsInt();
    }

    /**
     * A wait that gives up after {@code maxTicks}: it then stops waiting and the next step fails with
     * what was being waited for.
     */
    Runnable within(int maxTicks, String what, Runnable check) {
      long[] start = {-1};
      return () -> {
        if (stop.reason != null) return;
        if (start[0] < 0) start[0] = level.getGameTime();
        try {
          check.run();
        } catch (net.minecraft.gametest.framework.GameTestAssertException e) {
          if (level.getGameTime() - start[0] > maxTicks) {
            stop.reason = what + " (not within " + maxTicks + " ticks): " + e.getMessage();
            return;
          }
          throw e;
        }
      };
    }

    /** A step that first fails for an abandoned wait. */
    Runnable step(Runnable body) {
      return RuntimeGameTestsRuins.guarded(() -> {
        stop.check(helper);
        body.run();
      });
    }

    float speed(BlockPos pos) {
      return CreateCompat.speed(level, pos);
    }

    /** Water sources left in each layer of the drain box, bottom first. */
    int[] sources() {
      var drain = markers(RuinMarkers.Kind.DRAIN).getFirst();
      int[] size = drain.marker().size();
      int[] layers = new int[size[1]];
      var cursor = new BlockPos.MutableBlockPos();
      for (int y = 0; y < size[1]; y++)
        for (int x = 0; x < size[0]; x++)
          for (int z = 0; z < size[2]; z++) {
            var fluid = level.getFluidState(cursor.set(drain.pos().getX() + x, drain.pos().getY() + y, drain.pos().getZ() + z));
            if (fluid.is(Fluids.WATER) && fluid.isSource()) layers[y]++;
          }
      return layers;
    }

    boolean solved(ServerPlayer player, String challenge) {
      return RuntimeGameTestsRuins.team(player).solved.contains(RuinProgress.key(definition.id(), challenge));
    }

    void close() {
      for (var player : new ServerPlayer[] {a, b}) if (player != null && !player.hasDisconnected()) RuntimeGameTestsRuins.leave(player);
      for (var chunks : held)
        for (var chunk : chunks) level.getChunkSource().removeRegionTicket(ENGINE, chunk, 0, chunk);
      if (ruin != null) {
        var server = level.getServer();
        RuinData.get(server).remove(ruin.id());
        RuinProgress.get(server).forget(definition.id());
        RuinWorkshopData.get(server).forget(ruin.id());
      }
      RuinRegistry.remove(definition.id());
      StructureProtection.invalidate(level.getServer());
    }
  }

  /** The sluice id (1..4) of the house a wheel stands in: the id of the nearest sluice cell. */
  static int houseOf(Engine engine, BlockPos wheel) {
    return engine.markers(RuinMarkers.Kind.SLUICE).stream()
        .min(Comparator.comparingDouble(s -> s.pos().distSqr(wheel)))
        .map(s -> Integer.parseInt(s.marker().param("id", "0"))).orElse(0);
  }

  static void waitTicks(Engine engine, int ticks) {
    if (engine.mark == 0) engine.mark = engine.level.getGameTime();
    boolean done = engine.level.getGameTime() - engine.mark >= ticks;
    engine.helper.assertTrue(done, "Waiting " + ticks + " ticks");
    engine.mark = 0;
  }

  /** Every wheel turns once its sluice opens; the lines carry them only with the missing pieces back. */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins_engine")
  public static void workshopWheelsTurnAndOnlyTheRightBuildDrainsThePit(GameTestHelper helper) {
    var engine = new Engine(helper, 0);
    int[] initial = new int[1];
    engine.placed()
        .thenExecute(engine.step(() -> {
          helper.assertTrue(engine.site.wheels.size() == 4 && engine.site.pumps.size() == 4, "Four wheels and four pumps");
          int r = RuinWorkshop.pumpSpeed(engine.site, null);
          helper.assertTrue(r == engine.rpm() && r == 128, "R from the pack's Create values is " + r + ", the exporter says "
              + engine.rpm());
          var wheel = RuinPlacement.blockOf(engine.site.wheels.getFirst().marker()).getBlock();
          var pump = RuinPlacement.blockOf(engine.site.pumps.getFirst().marker()).getBlock();
          helper.assertTrue(CreateCompat.capacity(wheel) == 128 && CreateCompat.impact(pump) == 4,
              "Pack stress values: wheel " + CreateCompat.capacity(wheel) + " SU/RPM, pump " + CreateCompat.impact(pump));
          for (var wheelMarker : engine.site.wheels)
            helper.assertTrue(engine.speed(wheelMarker.pos()) == 0, "A wheel turns behind a closed sluice");
          engine.levers(Set.of("1", "2", "3", "4"));
        }))
        .thenWaitUntil(engine.within(1200, "The wheels turn once their sluices open", () -> {
          for (var wheel : engine.site.wheels)
            helper.assertTrue(Math.abs(engine.speed(wheel.pos())) == 4, "Wheel at " + wheel.pos() + " turns at "
                + engine.speed(wheel.pos()) + " (block " + engine.level.getBlockState(wheel.pos()) + ")");
        }))
        .thenExecute(engine.step(() -> {
          for (var port : engine.markers(RuinMarkers.Kind.PORT))
            if (port.marker().param("role", "").equals("input"))
              helper.assertTrue(engine.speed(port.pos()) == 0, "A line turns with its wheelhouse's piece missing: " + port.pos());
          engine.fillSockets();
        }))
        .thenWaitUntil(engine.within(400, "The lines turn with the pieces back", () -> {
          for (var port : engine.markers(RuinMarkers.Kind.PORT))
            if (port.marker().param("role", "").equals("input"))
              helper.assertTrue(Math.abs(engine.speed(port.pos())) == 4, "Line end " + port.pos() + " at " + engine.speed(port.pos()));
        }))
        .thenExecute(engine.step(() -> {
          initial[0] = Arrays.stream(engine.sources()).sum();
          helper.assertTrue(initial[0] > 500, "The pit is flooded: " + initial[0]);
          engine.build("wrong");
        }))
        .thenWaitUntil(() -> waitTicks(engine, 200))
        .thenExecute(engine.step(() -> {
          var states = RuinWorkshop.pumpStates(engine.level, engine.site, engine.rpm());
          helper.assertTrue(Arrays.stream(states).filter(s -> s == RuinRules.Pump.WRONG).count() == 2
                  && Arrays.stream(states).filter(s -> s == RuinRules.Pump.DRINKING).count() == 2,
              "Two pumps turn the wrong way: " + Arrays.toString(states));
          helper.assertTrue(Arrays.stream(engine.sources()).sum() == initial[0] && !engine.solved(engine.a, "engine"),
              "The pit drains with two pumps the wrong way");
          engine.clearSandbox();
          engine.build("overstress");
        }))
        .thenWaitUntil(() -> waitTicks(engine, 200))
        .thenExecute(engine.step(() -> {
          for (var pump : engine.site.pumps) {
            helper.assertTrue(Math.abs(CreateCompat.theoreticalSpeed(engine.level, pump.pos())) == 2 * engine.rpm(),
                "The overstress build turns its pumps at 2R: " + CreateCompat.theoreticalSpeed(engine.level, pump.pos()));
            helper.assertTrue(CreateCompat.overStressed(engine.level, pump.pos()) && engine.speed(pump.pos()) == 0,
                "2R does not overstress the merged wheels at " + pump.pos());
          }
          helper.assertTrue(Arrays.stream(engine.sources()).sum() == initial[0], "The pit drains at 2R");
          engine.clearSandbox();
          engine.levers(Set.of("1"));
        }))
        .thenWaitUntil(engine.within(1200, "Only the first house's wheel turns", () -> {
          for (var wheel : engine.site.wheels) {
            float speed = engine.speed(wheel.pos());
            helper.assertTrue(houseOf(engine, wheel.pos()) == 1 ? Math.abs(speed) == 4 : speed == 0,
                "Wheel of house " + houseOf(engine, wheel.pos()) + " at " + speed);
          }
        }))
        .thenExecute(engine.step(() -> engine.build("correct")))
        .thenWaitUntil(() -> waitTicks(engine, 300))
        .thenExecute(engine.step(() -> {
          for (var pump : engine.site.pumps)
            helper.assertTrue(CreateCompat.overStressed(engine.level, pump.pos()), "One wheel carries the four pumps at R");
          helper.assertTrue(Arrays.stream(engine.sources()).sum() == initial[0], "The pit drains on one wheel");
          engine.levers(Set.of("1", "2", "3", "4"));
        }))
        .thenWaitUntil(engine.within(3000, "The correct build drains the top layer", () -> {
          int[] layers = engine.sources();
          helper.assertTrue(layers[layers.length - 1] == 0, "Draining the top layer: " + Arrays.toString(layers)
              + ", pumps " + Arrays.toString(RuinWorkshop.pumpStates(engine.level, engine.site, engine.rpm())));
        }))
        .thenExecute(engine.step(() -> {
          int[] layers = engine.sources();
          helper.assertTrue(layers[0] > 0, "The bottom layer drained with the top: " + Arrays.toString(layers));
          // Break the engine: the drain pauses where it is.
          var first = engine.solution.getAsJsonObject("builds").getAsJsonArray("correct").get(0).getAsJsonObject();
          var p = first.getAsJsonArray("pos");
          BlockPos cut = engine.sandbox().offset(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
          engine.level.setBlock(cut, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          engine.counter = Arrays.stream(layers).sum();
        }))
        .thenWaitUntil(() -> waitTicks(engine, 100))
        .thenExecute(engine.step(() -> {
          int left = Arrays.stream(engine.sources()).sum();
          helper.assertTrue(left == engine.counter, "The drain went on with the engine broken: " + engine.counter + " -> " + left);
          engine.build("correct");
        }))
        .thenWaitUntil(engine.within(4000, "The correct build drains the pit", () -> helper.assertTrue(engine.solved(engine.a, "engine"),
            "Draining: " + Arrays.toString(engine.sources()))))
        .thenExecute(engine.step(() -> {
          helper.assertTrue(Arrays.stream(engine.sources()).sum() == 0, "The pit is dry");
          helper.assertTrue(!engine.solved(engine.b, "engine"), "The other team got the engine for free");
          LOGGER.info("Ruin engine QA: the correct build drained {} sources at R={}", initial[0], engine.rpm());
        }))
        .thenExecute(RuntimeGameTestsRuins.guarded(engine::close))
        .thenSucceed();
  }

  // ---- The seal -----------------------------------------------------------------------------------

  static Object sequencedGearshift(ServerLevel level, BlockPos pos) {
    var entity = level.getBlockEntity(pos);
    if (entity == null || !entity.getClass().getName().endsWith("SequencedGearshiftBlockEntity"))
      throw new IllegalStateException("No sequenced gearshift at " + pos + ": " + entity);
    return entity;
  }

  /** Programs a sequenced gearshift to turn by {@code degrees}, then starts it, as a player would. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  static void turn(ServerLevel level, BlockPos pos, int degrees) {
    try {
      Object gearshift = sequencedGearshift(level, pos);
      String base = "com.simibubi.create.content.kinetics.transmission.sequencer.";
      Class<?> instruction = Class.forName(base + "Instruction");
      Class<Enum> kinds = (Class<Enum>) Class.forName(base + "SequencerInstructions");
      Class<Enum> modifiers = (Class<Enum>) Class.forName(base + "InstructionSpeedModifiers");
      var list = (List<Object>) gearshift.getClass().getMethod("getInstructions").invoke(gearshift);
      list.clear();
      list.add(instruction.getConstructor(kinds, modifiers, int.class)
          .newInstance(Enum.valueOf(kinds, "TURN_ANGLE"), Enum.valueOf(modifiers, "FORWARD"), degrees));
      list.add(instruction.getConstructor(kinds).newInstance(Enum.valueOf(kinds, "END")));
      gearshift.getClass().getMethod("run", int.class).invoke(gearshift, 0);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot program the sequenced gearshift", e);
    }
  }

  /** The seal opens the vault for the builder's team at an eighth of a turn, not at a quarter. */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins_engine")
  public static void workshopSealOpensAtAnEighthAndNotAtAQuarter(GameTestHelper helper) {
    var engine = new Engine(helper, 1);
    BlockPos[] gearshift = new BlockPos[1];
    engine.placed()
        .thenExecute(engine.step(() -> {
          engine.fillSockets();
          engine.levers(Set.of("1", "2", "3", "4"));
          var context = RuntimeGameTestsRuins.context(engine.a);
          // The pumps' stage is the other case's; this team has drained the pit already.
          RuinChallenges.solve(engine.level, engine.ruin, engine.definition, "engine", context.campaignId(), context.founder());
          engine.build("seal");
          for (var element : engine.solution.getAsJsonObject("builds").getAsJsonArray("seal")) {
            var entry = element.getAsJsonObject();
            if (entry.get("state").getAsString().contains("sequenced_gearshift")) {
              var p = entry.getAsJsonArray("pos");
              gearshift[0] = engine.sandbox().offset(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
            }
          }
          helper.assertTrue(gearshift[0] != null, "The seal build has no sequenced gearshift");
        }))
        .thenWaitUntil(engine.within(1200, "The lines turn", () -> {
          for (var port : engine.markers(RuinMarkers.Kind.PORT))
            if (port.marker().param("role", "").equals("input"))
              helper.assertTrue(Math.abs(engine.speed(port.pos())) == 4, "Line end " + port.pos() + " at " + engine.speed(port.pos()));
        }))
        .thenExecute(engine.step(() -> turn(engine.level, gearshift[0], 90)))
        .thenWaitUntil(engine.within(600, "The ring turns a quarter and rests", () -> {
          var bearing = CreateCompat.bearing(engine.level, engine.site.seal.pos());
          helper.assertTrue(bearing != null && bearing.running() && bearing.angularSpeed() == 0
              && Math.abs(bearing.angle() - 90) < 1, "Turning a quarter: " + bearing);
        }))
        .thenWaitUntil(() -> waitTicks(engine, 120))
        .thenExecute(engine.step(() -> {
          helper.assertTrue(!engine.solved(engine.a, "seal"), "The seal opened at a quarter turn");
          turn(engine.level, gearshift[0], 45);
        }))
        .thenWaitUntil(engine.within(600, "The seal opens at an eighth", () -> helper.assertTrue(engine.solved(engine.a, "seal"),
            "The seal at " + CreateCompat.bearing(engine.level, engine.site.seal.pos()))))
        .thenExecute(engine.step(() -> {
          var bearing = CreateCompat.bearing(engine.level, engine.site.seal.pos());
          helper.assertTrue(RuinRules.aligned(bearing.angle(), 45, 3), "Opened away from an eighth: " + bearing);
          for (var plug : engine.markers(RuinMarkers.Kind.MODBLOCK))
            helper.assertTrue(engine.level.getBlockState(plug.pos()).isAir(), "The ring still plugs " + plug.pos());
          RuinGates.refresh(engine.a);
          RuinGates.refresh(engine.b);
          for (var gate : engine.markers(RuinMarkers.Kind.GATE)) {
            helper.assertTrue(RuinGates.open(engine.a, gate.pos()), "The builders cannot go down " + gate.pos());
            helper.assertTrue(!RuinGates.open(engine.b, gate.pos()), "Another team goes down " + gate.pos());
          }
          helper.assertTrue(!engine.solved(engine.b, "seal"), "The other team got the seal");
        }))
        .thenExecute(RuntimeGameTestsRuins.guarded(engine::close))
        .thenSucceed();
  }

  // ---- The sandbox and the reset -------------------------------------------------------------------

  static boolean place(ServerPlayer player, BlockPos target, String item) {
    var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    BlockPos below = target.below();
    player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    return BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(target).getBlock()).toString().equals(item);
  }

  /** Uses an item on a block's north face (a wrench there turns a vertical shaft). */
  static void use(ServerPlayer player, BlockPos target, String item) {
    var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND,
        new BlockHitResult(Vec3.atCenterOf(target).add(0, 0, -0.5), Direction.NORTH, target, false));
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
  }

  /** The engine room takes only transmission parts and redstone; the reset gives them back. */
  @GameTest(template = "empty", timeoutTicks = 400000, batch = "ruins_engine")
  public static void workshopSandboxTakesOnlyPartsAndTheResetGivesThemBack(GameTestHelper helper) {
    var engine = new Engine(helper, 2);
    BlockPos[] cells = new BlockPos[4];
    int[] before = new int[1];
    engine.placed()
        .thenExecute(engine.step(() -> {
          var level = engine.level;
          var a = engine.a;
          BlockPos center = engine.center();
          a.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
          RuntimeGameTestsRuins.teleport(a, level, center.below());
          cells[0] = center.offset(1, 0, 1);
          helper.assertTrue(place(a, cells[0], "create:shaft"), "A shaft does not go on Terra's bench");
          helper.assertTrue(place(a, center.offset(-1, 0, 1), "minecraft:repeater"), "Redstone does not go on the bench");
          for (String banned : List.of("create:creative_motor", "create:hand_crank", "create:rotation_speed_controller",
              "create:water_wheel", "minecraft:stone", "minecraft:chest"))
            helper.assertTrue(!place(a, center.offset(-1, 0, -1), banned), banned + " went on the bench");
          helper.assertTrue(!place(a, center.offset(4, 0, 3), "create:shaft"), "A shaft went on the walkway outside the bench");
          // The wrench turns the bench's shaft, never the ruin's own.
          var unturned = level.getBlockState(cells[0]);
          use(a, cells[0], "create:wrench");
          helper.assertTrue(level.getBlockState(cells[0]) != unturned, "The wrench did not turn a part on the bench");
          var port = engine.markers(RuinMarkers.Kind.PORT).stream().filter(p -> p.marker().param("role", "").equals("input"))
              .findFirst().orElseThrow().pos();
          var portState = level.getBlockState(port);
          use(a, port, "create:wrench");
          helper.assertTrue(level.getBlockState(port) == portState, "The wrench turned the ruin's own shaft");
          a.gameMode.destroyBlock(port);
          helper.assertTrue(level.getBlockState(port) == portState, "A player broke the ruin's line");
          // Sockets take their piece only.
          var socket = engine.markers(RuinMarkers.Kind.PART).stream()
              .filter(p -> p.marker().block().startsWith("create:gearbox") && !level.getBlockState(p.pos().below()).isAir())
              .findFirst().orElseThrow();
          helper.assertTrue(!place(a, socket.pos(), "create:shaft"), "A socket took the wrong piece");
          helper.assertTrue(place(a, socket.pos(), "create:gearbox"), "A socket refused its piece");
          cells[1] = socket.pos();
          // Team B leaves a cogwheel on the bench and goes home before the reset.
          RuntimeGameTestsRuins.teleport(engine.b, level, center.below());
          cells[2] = center.offset(0, 0, -2);
          helper.assertTrue(place(engine.b, cells[2], "create:cogwheel"), "B's cog did not go on the bench");
          RuntimeGameTestsRuins.leave(engine.b);
          // Drain part of the pit by hand and turn a lever: the reset brings both back.
          var drain = engine.markers(RuinMarkers.Kind.DRAIN).getFirst();
          before[0] = Arrays.stream(engine.sources()).sum();
          for (int i = 0; i < 6; i++) RuinWorkshop.drainStep(level, drain);
          helper.assertTrue(Arrays.stream(engine.sources()).sum() < before[0], "Nothing drained by hand");
          engine.levers(Set.of("2"));
          RuinWorkshop.claimed(level, engine.ruin);
          helper.assertTrue(RuinWorkshop.pending(level.getServer(), engine.ruin.id()), "No reset after a claim");
          // Everyone leaves; ten minutes pass. (A waits in creative, out of the ruin, so the fall does not matter.)
          a.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
          a.teleportTo(level, engine.ruin.box().maxX() + 24, engine.ruin.box().maxY() + 8, engine.ruin.box().maxZ() + 24, 0, 0);
        }))
        .thenWaitUntil(engine.within(400, "The ruin counts as empty", () -> helper.assertTrue(
            RuinWorkshopData.get(engine.level.getServer()).peek(engine.ruin.id()).map(e -> e.emptySince >= 0).orElse(false),
            "The ruin is not empty yet")))
        .thenExecute(engine.step(() -> {
          var entry = RuinWorkshopData.get(engine.level.getServer()).peek(engine.ruin.id()).orElseThrow();
          entry.emptySince -= RuinWorkshop.RESET_DELAY;
        }))
        .thenWaitUntil(engine.within(400, "The workshop resets", () -> helper.assertTrue(
            !RuinWorkshop.pending(engine.level.getServer(), engine.ruin.id()), "The workshop has not reset")))
        .thenExecute(engine.step(() -> {
          var level = engine.level;
          for (var sandbox : engine.site.sandboxes)
            for (BlockPos pos : sandbox.cells())
              helper.assertTrue(level.getBlockState(pos).isAir(), "A part stayed on the bench at " + pos);
          helper.assertTrue(level.getBlockState(cells[1]).isAir(), "The socket kept its piece");
          var a = engine.a;
          helper.assertTrue(a.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:gearbox"))) >= 1
                  && a.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:shaft"))) >= 1,
              "A's parts did not come back to A");
          var returns = engine.markers(RuinMarkers.Kind.RETURNS).getFirst().pos();
          helper.assertTrue(level.getBlockEntity(returns) instanceof net.minecraft.world.Container container
                  && java.util.stream.IntStream.range(0, container.getContainerSize())
                      .anyMatch(i -> container.getItem(i).is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:cogwheel")))),
              "B's cogwheel is not in the barrel at the door");
          helper.assertTrue(Arrays.stream(engine.sources()).sum() >= before[0], "The pit did not flood again");
          for (var sluice : engine.markers(RuinMarkers.Kind.SLUICE))
            helper.assertTrue(!level.getBlockState(sluice.pos()).isAir(), "A sluice stayed open at " + sluice.pos());
          var bearing = CreateCompat.bearing(level, engine.site.seal.pos());
          helper.assertTrue(bearing != null && !bearing.running(), "The seal ring is not back in place");
          for (var plug : engine.markers(RuinMarkers.Kind.MODBLOCK))
            helper.assertTrue(!level.getBlockState(plug.pos()).isAir(), "The ring does not plug " + plug.pos());
        }))
        .thenExecute(RuntimeGameTestsRuins.guarded(engine::close))
        .thenSucceed();
  }
}
