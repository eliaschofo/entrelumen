package dev.entrelumen;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

/**
 * The light relay in the world (the Signal Tower's challenge): the {@link LightRelay} floors are read
 * from a ruin's markers and blocks. A vitral's turn is shown by the lightning rod over it (none:
 * pass; up; or lying towards the way it sends the light); a relay mirror turns a quarter per click;
 * the first floor's brazier is lit by hand, every other one by the receptor under it. The relay is
 * evaluated from the first floor up on every click; white at the last receptor solves it for the
 * clicking team, and the relay scrambles itself again for the next team.
 */
public final class RuinRelay {
  static final int RESET_TICKS = 200, SHOW_INTERVAL = 20;

  private RuinRelay() {}

  /** One floor in the world: its model and the markers that show its state. */
  record Level(int floor, LightRelay.Floor model, RuinData.PlacedMarker light, @Nullable RuinData.PlacedMarker receptor) {}

  static LightRelay.Cell cell(BlockPos pos) {
    return new LightRelay.Cell(pos.getX(), pos.getY(), pos.getZ());
  }

  static BlockPos pos(LightRelay.Cell cell) {
    return new BlockPos(cell.x(), cell.y(), cell.z());
  }

  /** The turn a vitral shows by the rod over it. */
  static LightRelay.Turn turn(ServerLevel level, BlockPos vitral) {
    BlockState above = level.getBlockState(vitral.above());
    if (!above.is(Blocks.LIGHTNING_ROD)) return LightRelay.Turn.PASS;
    return switch (above.getValue(LightningRodBlock.FACING)) {
      case UP, DOWN -> LightRelay.Turn.UP;
      case NORTH -> LightRelay.Turn.NORTH;
      case EAST -> LightRelay.Turn.EAST;
      case SOUTH -> LightRelay.Turn.SOUTH;
      case WEST -> LightRelay.Turn.WEST;
    };
  }

  /** Shows a vitral's turn with the rod over it. */
  static void setTurn(ServerLevel level, BlockPos vitral, LightRelay.Turn turn) {
    BlockPos at = vitral.above();
    BlockState current = level.getBlockState(at);
    if (!current.isAir() && !current.is(Blocks.LIGHTNING_ROD)) return;
    BlockState rod = switch (turn) {
      case PASS -> Blocks.AIR.defaultBlockState();
      case UP -> Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP);
      case NORTH -> Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.NORTH);
      case EAST -> Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.EAST);
      case SOUTH -> Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.SOUTH);
      case WEST -> Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.WEST);
    };
    if (current != rod) level.setBlock(at, rod, Block.UPDATE_CLIENTS);
  }

  /** A relay mirror's facing (n, e, s, w) from its aim. */
  static LightRelay.Dir facing(BlockState mirror) {
    int aim = mirror.getValue(RuinBlocks.AIM);
    return LightRelay.Dir.values()[(aim / 2) % 4];
  }

  /** Whether light crosses a cell that holds no relay piece. */
  static boolean open(ServerLevel level, BlockPos pos) {
    if (!level.isLoaded(pos)) return false;
    BlockState state = level.getBlockState(pos);
    return state.isAir() || state.is(Blocks.LIGHTNING_ROD) || state.getCollisionShape(level, pos).isEmpty();
  }

  /** The relay's floors, from the lowest; floors without a light are skipped. */
  static List<Level> floors(ServerLevel level, RuinData.Ruin ruin, String challenge) {
    Map<Integer, RuinData.PlacedMarker> lights = new TreeMap<>();
    Map<Integer, Map<LightRelay.Cell, LightRelay.Element>> elements = new HashMap<>();
    Map<Integer, RuinData.PlacedMarker> receptors = new HashMap<>();
    for (var marker : ruin.markers()) {
      var m = marker.marker();
      if (!m.challenge().equals(challenge)) continue;
      int floor = m.floor();
      switch (m.kind()) {
        case LIGHT -> lights.put(floor, marker);
        case VITRAL -> elements.computeIfAbsent(floor, k -> new HashMap<>()).put(cell(marker.pos()),
            new LightRelay.Vitral(m.param("colour", ""), turn(level, marker.pos())));
        case MIRROR -> {
          BlockState state = level.getBlockState(marker.pos());
          if (state.is(RuinContent.MIRROR.get()))
            elements.computeIfAbsent(floor, k -> new HashMap<>()).put(cell(marker.pos()), new LightRelay.Mirror(facing(state)));
        }
        case COLLECTOR -> elements.computeIfAbsent(floor, k -> new HashMap<>()).put(cell(marker.pos()), new LightRelay.Collector());
        case RECEPTOR -> receptors.put(floor, marker);
        default -> {}
      }
    }
    List<Level> floors = new ArrayList<>();
    for (var entry : lights.entrySet()) {
      var receptor = receptors.get(entry.getKey());
      var target = receptor == null ? Set.<String>of() : LightRelay.colours(receptor.marker().param("target", ""));
      var model = new LightRelay.Floor(cell(entry.getValue().pos()), elements.getOrDefault(entry.getKey(), Map.of()),
          receptor == null ? cell(entry.getValue().pos().above(64)) : cell(receptor.pos()), target);
      floors.add(new Level(entry.getKey(), model, entry.getValue(), receptor));
    }
    return floors;
  }

  static boolean lit(BlockState state) {
    return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
  }

  static void setLit(ServerLevel level, BlockPos pos, boolean value) {
    BlockState state = level.getBlockState(pos);
    if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != value)
      level.setBlock(pos, state.setValue(BlockStateProperties.LIT, value), Block.UPDATE_CLIENTS);
  }

  /**
   * Runs the relay from the first floor up: lights each brazier its floor below earned, each receptor
   * that gets its exact colours, and shows the light when asked. True when the last floor is satisfied.
   */
  static boolean evaluate(ServerLevel level, RuinData.Ruin ruin, String challenge, boolean show) {
    boolean powered = false;
    boolean last = false;
    for (Level floor : floors(level, ruin, challenge)) {
      boolean hand = "true".equals(floor.light().marker().param("hand", "false"));
      if (hand) powered = lit(level.getBlockState(floor.light().pos()));
      else setLit(level, floor.light().pos(), powered);
      if (!powered) {
        if (floor.receptor() != null) setLit(level, floor.receptor().pos(), false);
        last = false;
        continue;
      }
      var trace = LightRelay.trace(floor.model(), cell -> open(level, pos(cell)));
      boolean ok = floor.receptor() != null && LightRelay.satisfied(floor.model(), trace);
      if (floor.receptor() != null) setLit(level, floor.receptor().pos(), ok);
      if (show) show(level, trace);
      powered = ok;
      last = ok;
    }
    return last;
  }

  /** The colour of a beam, the way stained glass would tint it; white once red, green and blue meet. */
  static Vector3f colour(Set<String> colours) {
    if (colours.containsAll(Set.of("red", "green", "blue"))) return new Vector3f(1f, 1f, 1f);
    float r = 0, g = 0, b = 0;
    int n = 0;
    for (String colour : colours) {
      float[] rgb = switch (colour) {
        case "red" -> new float[] {0.9f, 0.15f, 0.12f};
        case "green" -> new float[] {0.2f, 0.8f, 0.2f};
        case "blue" -> new float[] {0.2f, 0.35f, 0.95f};
        case "orange" -> new float[] {1f, 0.6f, 0.1f};
        case "yellow" -> new float[] {1f, 0.9f, 0.2f};
        case "light_blue" -> new float[] {0.45f, 0.75f, 1f};
        default -> new float[] {0.9f, 0.9f, 0.85f};
      };
      r += rgb[0];
      g += rgb[1];
      b += rgb[2];
      n++;
    }
    return n == 0 ? new Vector3f(1f, 0.95f, 0.8f) : new Vector3f(r / n, g / n, b / n);
  }

  private static void show(ServerLevel level, LightRelay.Trace trace) {
    trace.lit().forEach((cell, colours) -> level.sendParticles(new DustParticleOptions(colour(colours), 1.2f),
        cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5, 1, 0.05, 0.05, 0.05, 0));
  }

  /** A player worked a relay piece: turn it, run the relay, and solve it for the team at white. */
  static void use(ServerLevel level, RuinChallenges.Node node, ServerPlayer player, HeliodorCompass.Context context,
      RuinDefinitions.Definition definition, String challenge) {
    var marker = node.marker();
    BlockPos pos = marker.pos();
    switch (marker.marker().kind()) {
      case VITRAL -> {
        setTurn(level, pos, turn(level, pos).next());
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 1f, 1.4f);
      }
      case MIRROR -> {
        BlockState state = level.getBlockState(pos);
        if (!state.is(RuinContent.MIRROR.get())) return;
        int quarter = (state.getValue(RuinBlocks.AIM) / 2 + 1) % 4;
        level.setBlock(pos, state.setValue(RuinBlocks.AIM, quarter * 2), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 1f, 1.3f);
      }
      case LIGHT -> {
        if (!"true".equals(marker.marker().param("hand", "false"))) return;
        if (!lit(level.getBlockState(pos))) {
          setLit(level, pos, true);
          level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
        }
      }
      default -> {
        return;
      }
    }
    boolean white = evaluate(level, node.ruin(), challenge, true);
    if (!white) return;
    level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1.2f);
    if (RuinChallenges.solve(level, node.ruin(), definition, challenge, context.campaignId(), context.founder()))
      player.displayClientMessage(Component.translatable("entrelumen.ruin.relay.white"), true);
    scheduleReset(level, node.ruin(), challenge);
  }

  /** The relay scrambles itself again a while after it went white: the next team starts over. */
  static void scheduleReset(ServerLevel level, RuinData.Ruin ruin, String challenge) {
    String key = ruin.id() + "|relay|" + challenge;
    if (!RuinChallenges.pendingReset(level.getServer(), key)) return;
    RuinChallenges.schedule(level.getServer(), RESET_TICKS, () -> {
      RuinChallenges.resetDone(level.getServer(), key);
      reset(level, ruin, challenge);
    });
  }

  /** Every vitral and mirror back to its template state, every light and receptor out. */
  static void reset(ServerLevel level, RuinData.Ruin ruin, String challenge) {
    for (var marker : ruin.markers()) {
      var m = marker.marker();
      if (!m.challenge().equals(challenge) || !level.isLoaded(marker.pos())) continue;
      switch (m.kind()) {
        case VITRAL -> setTurn(level, marker.pos(), LightRelay.Turn.of(m.param("turn", "pass")).orElse(LightRelay.Turn.PASS));
        case MIRROR -> {
          BlockState state = level.getBlockState(marker.pos());
          if (state.is(RuinContent.MIRROR.get()))
            level.setBlock(marker.pos(), state.setValue(RuinBlocks.AIM,
                RuinMarkers.DIRECTIONS.indexOf(m.param("facing", "n"))), Block.UPDATE_CLIENTS);
        }
        case LIGHT, RECEPTOR -> setLit(level, marker.pos(), false);
        default -> {}
      }
    }
  }

  /** While a relay's first light burns and someone is inside, its light shows every second. */
  static void tick(MinecraftServer server) {
    if (server.getTickCount() % SHOW_INTERVAL != 0) return;
    for (var ruin : RuinData.get(server).ruins()) {
      var lights = ruin.markers(RuinMarkers.Kind.LIGHT);
      if (lights.isEmpty()) continue;
      ServerLevel level = server.getLevel(ruin.dimension());
      if (level == null) continue;
      var first = lights.stream().filter(l -> "true".equals(l.marker().param("hand", "false"))).findFirst().orElse(null);
      if (first == null || !level.isLoaded(first.pos()) || !lit(level.getBlockState(first.pos()))) continue;
      if (level.players().stream().noneMatch(p -> ruin.box().isInside(p.blockPosition()))) continue;
      evaluate(level, ruin, first.marker().challenge(), true);
    }
  }
}
