package dev.entrelumen;

import dev.entrelumen.EnvesPuzzleRules.VaultPuzzle;
import dev.entrelumen.EnvesRuns.Puzzle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * The vaults' locks (docs/design/dungeon-enves.md, «Acertijos y bóvedas»): the 3 × 4 gate of the
 * {@code enves:vault_gate} markers is the lock itself. Its bottom row holds the puzzle's pieces, the
 * rows above are bars to see the vault through, and what the lock needs is either in the vault behind
 * the bars or in the doorway before it:
 * <ul>
 * <li>glyph stones: behind the bars, on the mechanism's row, three glyphs spell a code; the stones
 * open when they spell it backwards (the Envés copies everything reversed);</li>
 * <li>braziers: the gate remembers an order and plays it when someone comes near or touches it;
 * light the three braziers in that order;</li>
 * <li>levers: six levers in the doorway flip the three lamps over the gate; light all three;</li>
 * <li>the offering: a cold brazier asks for a few sour light shards.</li>
 * </ul>
 * Solving opens the gate with {@link EnvesPlacer#openVault}.
 */
public final class EnvesVaults {
  private EnvesVaults() {}

  /** A glyph stone showing a glyph: the gate's stones and the clue behind the bars are the same block, so they read the same. */
  static BlockState glyph(int glyph) {
    return EnvesContent.GLYPH.get().defaultBlockState().setValue(EnvesContentBlocks.Glyph.GLYPH, glyph);
  }

  static final long SALT = 51;

  /** A gate seen from outside: rows bottom to top, each left to right; the door's outward side and the viewer's right. */
  record Gate(List<List<BlockPos>> rows, Direction out, Direction right) {
    BlockPos bottomCentre() {
      return rows.getFirst().get(rows.getFirst().size() / 2);
    }

    List<BlockPos> all() {
      List<BlockPos> out = new ArrayList<>();
      rows.forEach(out::addAll);
      return out;
    }
  }

  static Direction side(String argument) {
    return switch (argument) {
      case "e" -> Direction.EAST;
      case "s" -> Direction.SOUTH;
      case "w" -> Direction.WEST;
      default -> Direction.NORTH;
    };
  }

  /** Groups one side's gate markers into rows and columns as the one standing outside sees them. */
  static Gate gate(List<BlockPos> markers, Direction out) {
    Direction right = out.getOpposite().getClockWise();
    Map<Integer, List<BlockPos>> rows = new TreeMap<>();
    for (BlockPos pos : markers) rows.computeIfAbsent(pos.getY(), y -> new ArrayList<>()).add(pos);
    List<List<BlockPos>> ordered = new ArrayList<>();
    for (var row : rows.values()) {
      row.sort(Comparator.comparingInt(p -> p.getX() * right.getStepX() + p.getZ() * right.getStepZ()));
      ordered.add(List.copyOf(row));
    }
    return new Gate(List.copyOf(ordered), out, right);
  }

  static BlockState bars() {
    var id = ResourceLocation.tryParse(EnvesConfig.settings().vaultGateBlock());
    Block block = id == null ? Blocks.IRON_BARS : BuiltInRegistries.BLOCK.get(id);
    return (block == Blocks.AIR ? Blocks.IRON_BARS : block).defaultBlockState();
  }

  /** The hook: builds (or rebuilds, after a restart) the lock of a vault. */
  static void place(EnvesHooks.Floor floor, int cell, List<EnvesHooks.WorldMarker> markers, EnvesHooks.WorldMarker mechanism) {
    ServerLevel level = floor.level();
    Map<String, List<BlockPos>> sides = new TreeMap<>();
    for (var marker : markers) sides.computeIfAbsent(marker.marker().argument(), k -> new ArrayList<>()).add(marker.pos());
    if (sides.isEmpty()) return;
    var runs = EnvesRuns.get(level.getServer());
    Puzzle puzzle = runs.run(floor.attempt().id).floor(floor.depth()).puzzles.get(cell);
    if (puzzle != null && puzzle.solved) {
      for (var marker : markers) level.setBlock(marker.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      return;
    }
    // Every side but the lock's is plain bars.
    String lockSide = sides.keySet().iterator().next();
    for (var entry : sides.entrySet())
      if (!entry.getKey().equals(lockSide)) for (BlockPos pos : entry.getValue()) level.setBlock(pos, bars(), Block.UPDATE_ALL);
    Gate gate = gate(sides.get(lockSide), side(lockSide));
    if (gate.rows().size() < 4 || gate.rows().stream().anyMatch(r -> r.size() != 3)) {
      for (BlockPos pos : gate.all()) level.setBlock(pos, bars(), Block.UPDATE_ALL);
      return;
    }
    if (puzzle == null) {
      BlockPos anchor = mechanism != null ? mechanism.pos() : gate.bottomCentre().relative(gate.out().getOpposite(), 2);
      puzzle = build(floor, cell, gate, anchor);
      EnvesPuzzles.put(level.getServer(), floor.attempt(), floor.depth(), cell, puzzle);
    }
    render(level, puzzle, gate);
  }

  static Puzzle build(EnvesHooks.Floor floor, int cell, Gate gate, BlockPos anchor) {
    return build(floor, cell, gate, anchor, EnvesPuzzleRules.vaultPuzzle(floor.attempt().seed, floor.depth(), cell));
  }

  /** Builds the lock of {@code kind} (the tests ask for each kind in turn). */
  static Puzzle build(EnvesHooks.Floor floor, int cell, Gate gate, BlockPos anchor, VaultPuzzle kind) {
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT);
    var balance = EnvesContentConfig.balance();
    Puzzle p = new Puzzle();
    List<BlockPos> bottom = gate.rows().getFirst();
    if (kind == VaultPuzzle.LEVERS) {
      List<BlockPos> levers = leverSpots(floor.level(), gate);
      if (levers.size() < 3) kind = VaultPuzzle.GLYPHS;
      else {
        p.kind = "vault_levers";
        var board = EnvesPuzzleRules.levers(random, levers.size(), 3);
        p.elements.addAll(levers);
        p.data = board.masks().clone();
        p.state = new int[] {board.start(), 0};
        p.extras.addAll(gate.rows().get(3));
        for (int r = 0; r < 3; r++) p.extras.addAll(gate.rows().get(r));
        return p;
      }
    }
    if (kind == VaultPuzzle.GLYPHS) {
      // The clue stands behind the bars; another tileset's vault may have art there: then braziers.
      for (int k = -1; k <= 1; k++) {
        BlockPos clue = anchor.relative(gate.right(), k).above();
        if (!floor.level().getBlockState(clue).isAir() || !(floor.level().getBlockState(clue.below()).isAir()
            || floor.level().getBlockState(clue.below()).canBeReplaced())) kind = VaultPuzzle.BRAZIERS;
      }
    }
    switch (kind) {
      case GLYPHS -> {
        p.kind = "vault_glyphs";
        p.solution = EnvesPuzzleRules.glyphCode(random);
        p.state = EnvesPuzzleRules.glyphStart(random, p.solution);
        p.elements.addAll(bottom);
        for (int k = -1; k <= 1; k++) p.extras.add(anchor.relative(gate.right(), k).above());
        for (int r = 1; r < 4; r++) p.extras.addAll(gate.rows().get(r));
      }
      case BRAZIERS -> {
        p.kind = "vault_braziers";
        p.solution = EnvesPuzzleRules.echo(random, 3, balance.echoLength(floor.depth()));
        p.elements.addAll(bottom);
        for (int r = 1; r < 4; r++) p.extras.addAll(gate.rows().get(r));
      }
      default -> {
        p.kind = "vault_offering";
        p.data = new int[] {balance.offeringShards(floor.depth())};
        p.elements.add(bottom.get(1));
        p.extras.add(bottom.get(0));
        p.extras.add(bottom.get(2));
        for (int r = 1; r < 4; r++) p.extras.addAll(gate.rows().get(r));
      }
    }
    return p;
  }

  /**
   * Where levers go: in the outer half of the doorway (one block out of the gate), on its two side
   * walls, three high. Only spots with a solid wall to hang from count.
   */
  static List<BlockPos> leverSpots(ServerLevel level, Gate gate) {
    List<BlockPos> out = new ArrayList<>();
    for (int r = 0; r < 3; r++) {
      var row = gate.rows().get(r);
      for (int side : new int[] {0, 2}) {
        BlockPos spot = row.get(side).relative(gate.out());
        Direction toWall = side == 0 ? gate.right().getOpposite() : gate.right();
        BlockPos wall = spot.relative(toWall);
        if (level.getBlockState(spot).isAir() && level.getBlockState(wall).isFaceSturdy(level, wall, toWall.getOpposite()))
          out.add(spot);
      }
    }
    return out;
  }

  /** Puts a lock's blocks in the world as its state says. */
  static void render(ServerLevel level, Puzzle p, Gate gate) {
    BlockState bars = bars();
    for (BlockPos pos : gate.all()) level.setBlock(pos, bars, Block.UPDATE_ALL);
    List<BlockPos> bottom = gate.rows().getFirst();
    switch (p.kind) {
      case "vault_glyphs" -> {
        for (int i = 0; i < 3; i++) level.setBlock(bottom.get(i), glyph(p.state[i]), Block.UPDATE_ALL);
        for (int k = 0; k < 3; k++) {
          BlockPos clue = p.extras.get(k);
          level.setBlock(clue.below(), Blocks.CALCITE.defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(clue, glyph(p.solution[k]), Block.UPDATE_ALL);
        }
      }
      case "vault_braziers" -> {
        for (BlockPos pos : bottom) level.setBlock(pos, EnvesContent.BRAZIER.get().defaultBlockState(), Block.UPDATE_ALL);
      }
      case "vault_offering" -> level.setBlock(bottom.get(1), EnvesContent.BRAZIER.get().defaultBlockState(), Block.UPDATE_ALL);
      case "vault_levers" -> {
        for (int i = 0; i < p.elements.size(); i++) {
          BlockPos spot = p.elements.get(i);
          int row = -1;
          for (int r = 0; r < 3; r++) if (spot.getY() == gate.rows().get(r).getFirst().getY()) row = r;
          boolean left = spot.relative(gate.out().getOpposite()).equals(gate.rows().get(Math.max(0, row)).getFirst());
          Direction facing = left ? gate.right() : gate.right().getOpposite();
          level.setBlock(spot, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
              .setValue(LeverBlock.FACING, facing).setValue(LeverBlock.POWERED, (p.state[1] & 1 << i) != 0), Block.UPDATE_CLIENTS);
        }
        EnvesPuzzles.showLamps(level, p);
      }
      default -> {}
    }
  }

  /** The lock is solved: the gate goes, and the levers with it. */
  static void open(EnvesHooks.Floor floor, Puzzle puzzle) {
    ServerLevel level = floor.level();
    EnvesPlacer.openVault(floor, puzzle.cell);
    if (puzzle.kind.equals("vault_levers"))
      for (BlockPos lever : puzzle.elements) level.setBlock(lever, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    BlockPos at = puzzle.elements.isEmpty() ? floor.origin(puzzle.cell) : puzzle.elements.getFirst();
    level.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
    level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.8f);
  }

  /** A member comes near a braziers' gate for the first time: it plays its echo once, unasked. */
  static void near(ServerLevel level, EnvesData.Attempt attempt, int depth, ServerPlayer player) {
    var run = EnvesRuns.get(level.getServer()).find(attempt.id);
    if (run.isEmpty()) return;
    var floor = run.get().floors.get(depth);
    if (floor == null) return;
    for (Puzzle p : floor.puzzles.values()) {
      if (p.solved || p.heard || !p.kind.equals("vault_braziers") || p.elements.isEmpty()) continue;
      if (player.blockPosition().distSqr(p.elements.get(1)) <= 5 * 5) EnvesPuzzles.play(level, p);
    }
  }
}
