package dev.entrelumen;

import dev.entrelumen.EnvesBalance.Role;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesLayout.Dir;
import dev.entrelumen.EnvesPuzzleRules.SealPuzzle;
import dev.entrelumen.EnvesPuzzleRules.SealVariant;
import dev.entrelumen.EnvesRuns.Puzzle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * The seals (docs/design/dungeon-enves.md, «Sellos»), in three variants shuffled per floor so no two
 * seals of a floor are alike:
 * <ul>
 * <li>the guardian: an echo with one affix more than the floor's elites stands before the seal
 * from the first step into the room; the seal lights once it falls;</li>
 * <li>the circle: touching the seal wakes it; somebody of the group must stand within its circle
 * for 20 s, while echoes come in three waves; empty for too long, the circle cools and starts over;</li>
 * <li>a small puzzle: the four braziers' echo, the four levers and their lamps, or the sour light
 * bent by mirrors onto the seal. Solving it lights the seal.</li>
 * </ul>
 * Also the stair's other lock: the floor's champion ({@link EnvesEncounters#championAllows}).
 */
public final class EnvesSeals {
  private EnvesSeals() {}

  static final long SALT_GUARDIAN = 61, SALT_PUZZLE = 62, SALT_WAVES = 63;
  /** The candles of the seal room's pedestals: four diagonal and four on the axes, five blocks out. */
  static final int PEDESTAL = 5;

  static final EnvesHooks.Seals HOOK = new EnvesHooks.Seals() {
    @Override
    public boolean mayLight(EnvesHooks.Floor floor, int cell, ServerPlayer player) {
      return EnvesSeals.mayLight(floor, cell, player);
    }

    @Override
    public void lit(EnvesHooks.Floor floor, int cell, ServerPlayer player, int lit, int total) {
      EnvesSeals.lit(floor, cell, player, lit, total);
    }

    @Override
    public boolean stairMayOpen(EnvesHooks.Floor floor) {
      return !Enves.peaceful(floor.level().getServer()) && EnvesEncounters.championAllows(floor);
    }
  };

  static SealVariant variant(EnvesHooks.Floor floor, int cell) {
    return EnvesPuzzleRules.sealVariants(floor.attempt().seed, floor.depth(), floor.layout().seals()).getOrDefault(cell, SealVariant.GUARDIAN);
  }

  static EnvesRuns.Seal state(EnvesHooks.Floor floor, int cell) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var seal = runs.run(floor.attempt().id).floor(floor.depth()).seal(cell);
    if (!seal.decided) {
      seal.variant = variant(floor, cell);
      seal.decided = true;
      runs.setDirty();
    }
    return seal;
  }

  static BlockPos sealPos(EnvesHooks.Floor floor, int cell) {
    return floor.markers(cell).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.SEAL).map(EnvesHooks.WorldMarker::pos)
        .findFirst().orElse(floor.origin(cell).offset(EnvesGeometry.C, 2, EnvesGeometry.C));
  }

  /** The single door of a seal room (seals are dead ends). */
  static Direction door(EnvesHooks.Floor floor, int cell) {
    for (Dir dir : Dir.values())
      if (floor.layout().linked(cell, dir))
        return switch (dir) {
          case N -> Direction.NORTH;
          case E -> Direction.EAST;
          case S -> Direction.SOUTH;
          case W -> Direction.WEST;
        };
    return Direction.NORTH;
  }

  // ---- The hook -----------------------------------------------------------------------------

  static boolean mayLight(EnvesHooks.Floor floor, int cell, ServerPlayer player) {
    if (EnvesHooks.forcing()) {
      EnvesEncounters.waiveChampion(floor);
      return true;
    }
    if (Enves.peaceful(floor.level().getServer())) {
      player.displayClientMessage(Component.translatable("entrelumen.enves.peaceful"), true);
      return false;
    }
    var seal = state(floor, cell);
    switch (seal.variant) {
      case GUARDIAN -> {
        if (seal.guardianDead) return true;
        if (!seal.guardianSpawned) spawnGuardian(floor, cell, player);
        player.displayClientMessage(Component.translatable("entrelumen.enves.seal.guarded"), true);
        return false;
      }
      case CIRCLE -> {
        if (seal.solved) return true;
        if (!seal.circle) startCircle(floor, cell, player);
        else player.displayClientMessage(Component.translatable("entrelumen.enves.seal.circle.hold"), true);
        return false;
      }
      default -> {
        Puzzle puzzle = dress(floor, cell);
        if (puzzle == null) {
          // The room fits no puzzle: the seal became a circle.
          if (seal.variant == SealVariant.CIRCLE) return mayLight(floor, cell, player);
          return false;
        }
        if (puzzle.solved) return true;
        switch (puzzle.kind) {
          case "seal_braziers" -> EnvesPuzzles.play(floor.level(), puzzle);
          case "seal_levers" -> player.displayClientMessage(Component.translatable("entrelumen.enves.seal.levers"), true);
          default -> player.displayClientMessage(Component.translatable("entrelumen.enves.seal.mirrors"), true);
        }
        return false;
      }
    }
  }

  static void lit(EnvesHooks.Floor floor, int cell, ServerPlayer player, int lit, int total) {
    if (lit < total || EnvesEncounters.championAllows(floor)) return;
    for (ServerPlayer member : Enves.membersInside(floor.level().getServer(), floor.attempt()))
      member.displayClientMessage(Component.translatable("entrelumen.enves.seal.champion_waits"), false);
  }

  // ---- Presence -----------------------------------------------------------------------------

  /** A member stands in a seal room (the tracker, twice a second). */
  static void presence(EnvesHooks.Floor floor, int cell, ServerPlayer player) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var state = runs.run(floor.attempt().id).floor(floor.depth());
    boolean first = !state.visited.get(cell);
    if (first) {
      state.visited.set(cell);
      runs.setDirty();
    }
    if (floor.attempt().floor(floor.depth()).lit.get(cell)) return;
    var seal = state(floor, cell);
    switch (seal.variant) {
      case GUARDIAN -> {
        if (seal.guardianSpawned && !seal.guardianDead && EnvesEchoes.lost(floor.level(), seal.guardian)) {
          // It left the world without falling (Peaceful, a capture, a command): another one stands.
          seal.guardianSpawned = false;
          seal.guardian = null;
          runs.setDirty();
        }
        if (!seal.guardianSpawned) spawnGuardian(floor, cell, player);
      }
      case CIRCLE -> {
        if (first) player.displayClientMessage(Component.translatable("entrelumen.enves.seal.circle.hint"), true);
      }
      default -> {
        Puzzle puzzle = dress(floor, cell);
        if (first && puzzle != null && puzzle.kind.equals("seal_braziers")) EnvesPuzzles.play(floor.level(), puzzle);
      }
    }
  }

  // ---- The guardian -------------------------------------------------------------------------

  static void spawnGuardian(EnvesHooks.Floor floor, int cell, ServerPlayer target) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var seal = state(floor, cell);
    if (seal.guardianSpawned) return;
    var table = EnvesContentConfig.table(floor.tileset());
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT_GUARDIAN);
    var entry = EnvesEchoTables.draw(table.elites().isEmpty() ? table.escorts() : table.elites(), random);
    if (entry == null) {
      seal.guardianSpawned = seal.guardianDead = true;
      runs.setDirty();
      return;
    }
    int count = EnvesContentConfig.balance().affixCount(Role.GUARDIAN, floor.depth(), random);
    BlockPos seat = EnvesEchoes.safeSpot(floor.level(), sealPos(floor, cell).below().relative(door(floor, cell), 3), 3);
    Mob guardian = EnvesEchoes.spawn(floor.level(), new EnvesEchoes.Spec(entry, Role.GUARDIAN, floor.attempt(), floor.depth(),
        EnvesAffix.pick(count, random)), Vec3.atBottomCenterOf(seat), target);
    seal.guardianSpawned = true;
    if (guardian == null) seal.guardianDead = true;
    else seal.guardian = guardian.getUUID();
    runs.setDirty();
  }

  /** A guardian fell: its seal is free. */
  static void guardianFell(ServerLevel level, Mob guardian, EnvesEchoes.Data data) {
    var runs = EnvesRuns.get(level.getServer());
    var run = runs.find(data.attempt());
    if (run.isEmpty()) return;
    var floor = run.get().floors.get(data.depth());
    if (floor == null) return;
    for (var seal : floor.seals.values())
      if (guardian.getUUID().equals(seal.guardian)) {
        seal.guardianDead = true;
        runs.setDirty();
        level.playSound(null, guardian.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1f, 1.2f);
        if (guardian.getLastHurtByMob() instanceof ServerPlayer player)
          player.displayClientMessage(Component.translatable("entrelumen.enves.seal.guardian_fell"), true);
      }
  }

  // ---- The circle ---------------------------------------------------------------------------

  /** Circles running right now, with the boss bar that shows their progress. */
  record Circle(UUID attempt, int depth, int cell, BlockPos seal, ServerBossEvent bar) {}

  private static final Map<String, Circle> CIRCLES = new HashMap<>();

  static String key(UUID attempt, int depth, int cell) {
    return attempt + "/" + depth + "/" + cell;
  }

  static void startCircle(EnvesHooks.Floor floor, int cell, ServerPlayer player) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var seal = state(floor, cell);
    seal.circle = true;
    // Waves already sent are not sent again: cooling the circle on purpose farms nothing.
    seal.held = seal.empty = 0;
    runs.setDirty();
    BlockPos pos = sealPos(floor, cell);
    floor.level().playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.7f);
    for (ServerPlayer member : Enves.membersInside(floor.level().getServer(), floor.attempt()))
      if (member.distanceToSqr(Vec3.atCenterOf(pos)) < 24 * 24)
        member.displayClientMessage(Component.translatable("entrelumen.enves.seal.circle.start",
            EnvesContentConfig.balance().seals().circleSeconds()), true);
  }

  static Circle circle(MinecraftServer server, Attempt attempt, int depth, int cell, BlockPos seal) {
    return CIRCLES.computeIfAbsent(key(attempt.id, depth, cell), k -> {
      var bar = new ServerBossEvent(Component.translatable("entrelumen.enves.seal.circle.bar"), BossEvent.BossBarColor.YELLOW,
          BossEvent.BossBarOverlay.NOTCHED_20);
      bar.setProgress(0);
      return new Circle(attempt.id, depth, cell, seal, bar);
    });
  }

  /** Every tick: the circles that run. Progress while a member stands inside; it cools when nobody does. */
  static void tick(MinecraftServer server) {
    ServerLevel level = Enves.level(server);
    if (level == null || Enves.peaceful(server)) return; // on Peaceful the circles wait
    var runs = EnvesRuns.get(server);
    var balance = EnvesContentConfig.balance().seals();
    int goal = balance.circleSeconds() * 20, grace = balance.circleGraceSeconds() * 20;
    double radius = balance.circleRadius();
    List<String> alive = new ArrayList<>();
    for (var run : runs.runs()) {
      var attempt = EnvesData.get(server).attempt(run.attempt);
      if (attempt.isEmpty() || attempt.get().status != EnvesData.Status.OPEN) continue;
      for (var floorEntry : run.floors.entrySet())
        for (var sealEntry : floorEntry.getValue().seals.entrySet()) {
          var seal = sealEntry.getValue();
          if (!seal.circle || seal.solved) continue;
          int depth = floorEntry.getKey(), cell = sealEntry.getKey();
          var floor = Enves.floor(level, attempt.get(), depth);
          BlockPos pos = sealPos(floor, cell);
          if (!level.isLoaded(pos)) continue;
          Circle circle = circle(server, attempt.get(), depth, cell, pos);
          alive.add(key(attempt.get().id, depth, cell));
          List<ServerPlayer> inside = new ArrayList<>(), near = new ArrayList<>();
          for (ServerPlayer player : Enves.membersInside(server, attempt.get())) {
            double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
            boolean sameFloor = EnvesGeometry.depthAt(player.getBlockY()) == depth;
            if (sameFloor && player.isAlive() && dx * dx + dz * dz <= radius * radius) inside.add(player);
            if (sameFloor && dx * dx + dz * dz <= 20 * 20) near.add(player);
          }
          for (ServerPlayer player : near) circle.bar().addPlayer(player);
          for (ServerPlayer player : List.copyOf(circle.bar().getPlayers())) if (!near.contains(player)) circle.bar().removePlayer(player);
          if (!inside.isEmpty()) {
            seal.held++;
            seal.empty = 0;
          } else {
            seal.held = Math.max(0, seal.held - 2);
            seal.empty++;
          }
          circle.bar().setProgress(Math.min(1f, seal.held / (float) goal));
          if (server.getTickCount() % 5 == 0) ring(level, pos, radius, !inside.isEmpty());
          int wave = Math.min(2, seal.held * 3 / goal);
          if (seal.waves <= wave) {
            sendWave(floor, cell, pos, seal.waves, inside.isEmpty() ? (near.isEmpty() ? null : near.getFirst()) : inside.getFirst());
            seal.waves++;
          }
          if (seal.held >= goal) {
            seal.solved = true;
            seal.circle = false;
            circle.bar().removeAllPlayers();
            CIRCLES.remove(key(attempt.get().id, depth, cell));
            ServerPlayer who = inside.isEmpty() ? (near.isEmpty() ? null : near.getFirst()) : inside.getFirst();
            if (who != null) Enves.lightSeal(who, pos);
          } else if (seal.empty > grace) {
            seal.circle = false;
            seal.held = seal.empty = 0;
            circle.bar().removeAllPlayers();
            CIRCLES.remove(key(attempt.get().id, depth, cell));
            for (ServerPlayer player : near) player.displayClientMessage(Component.translatable("entrelumen.enves.seal.circle.cooled"), true);
          }
          runs.setDirty();
        }
    }
    CIRCLES.entrySet().removeIf(entry -> {
      if (alive.contains(entry.getKey())) return false;
      entry.getValue().bar().removeAllPlayers();
      return true;
    });
  }

  private static void ring(ServerLevel level, BlockPos pos, double radius, boolean held) {
    for (int i = 0; i < 24; i++) {
      double a = Math.PI * 2 * i / 24;
      level.sendParticles(held ? EnvesPuzzles.LIGHT : net.minecraft.core.particles.ParticleTypes.SMOKE,
          pos.getX() + 0.5 + Math.cos(a) * radius, pos.getY() - 0.8, pos.getZ() + 0.5 + Math.sin(a) * radius, 1, 0, 0, 0, 0);
    }
  }

  /** The circle's waves: two escorts, then an elite, then two escorts (one more of each from floor III). */
  static void sendWave(EnvesHooks.Floor floor, int cell, BlockPos seal, int wave, ServerPlayer target) {
    var table = EnvesContentConfig.table(floor.tileset());
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT_WAVES + wave);
    List<EnvesEchoes.Spec> group = new ArrayList<>();
    int extra = floor.depth() >= 3 ? 1 : 0;
    if (wave == 1 && !table.elites().isEmpty()) {
      int count = EnvesContentConfig.balance().affixCount(Role.ELITE, floor.depth(), random);
      group.add(new EnvesEchoes.Spec(EnvesEchoTables.draw(table.elites(), random), Role.ELITE, floor.attempt(), floor.depth(),
          EnvesAffix.pick(count, random)));
    }
    int escorts = (wave == 1 ? 0 : 2) + extra;
    for (int i = 0; i < escorts && !table.escorts().isEmpty(); i++)
      group.add(new EnvesEchoes.Spec(EnvesEchoTables.draw(table.escorts(), random), Role.ESCORT, floor.attempt(), floor.depth(), List.of()));
    Direction door = door(floor, cell);
    List<Direction> bays = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
    bays.remove(door);
    for (int i = 0; i < group.size(); i++) {
      if (group.get(i).entry() == null) continue;
      BlockPos at = EnvesEchoes.safeSpot(floor.level(), seal.below().relative(bays.get(i % bays.size()), 7), 3);
      EnvesEchoes.spawn(floor.level(), group.get(i), Vec3.atBottomCenterOf(at), target);
    }
  }

  // ---- The puzzles --------------------------------------------------------------------------

  /**
   * Builds (once) a puzzle seal's pieces in its room. The kind drawn for the seal comes first; if the
   * room's art leaves no room for it (another tileset's seal room), the next kind is tried, and a room
   * that fits none turns the seal into a circle.
   */
  static Puzzle dress(EnvesHooks.Floor floor, int cell) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var state = runs.run(floor.attempt().id).floor(floor.depth());
    Puzzle existing = state.puzzles.get(cell);
    if (existing != null) return existing;
    if (state(floor, cell).variant != SealVariant.PUZZLE) return null;
    ServerLevel level = floor.level();
    BlockPos seal = sealPos(floor, cell);
    if (!level.isLoaded(seal)) return null;
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT_PUZZLE);
    SealPuzzle first = EnvesPuzzleRules.sealPuzzle(floor.attempt().seed, floor.depth(), cell);
    List<SealPuzzle> order = new ArrayList<>(List.of(SealPuzzle.values()));
    order.remove(first);
    order.addFirst(first);
    for (SealPuzzle kind : order) {
      Puzzle p = build(level, floor, seal, kind, random);
      if (p == null) continue;
      EnvesPuzzles.put(level.getServer(), floor.attempt(), floor.depth(), cell, p);
      level.playSound(null, seal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 0.7f);
      return p;
    }
    state(floor, cell).variant = SealVariant.CIRCLE;
    runs.setDirty();
    return null;
  }

  /** Whether a piece may stand at {@code pos}: air or a candle there, and air or a solid foot below. */
  static boolean fits(ServerLevel level, BlockPos pos) {
    var here = level.getBlockState(pos);
    var below = level.getBlockState(pos.below());
    boolean free = here.isAir() || here.is(net.minecraft.tags.BlockTags.CANDLES);
    boolean foot = below.isAir() || below.isFaceSturdy(level, pos.below(), Direction.UP);
    boolean floored = !below.isAir() || level.getBlockState(pos.below(2)).isFaceSturdy(level, pos.below(2), Direction.UP);
    return free && foot && floored;
  }

  static Puzzle build(ServerLevel level, EnvesHooks.Floor floor, BlockPos seal, SealPuzzle kind, Random random) {
    var balance = EnvesContentConfig.balance();
    int d = PEDESTAL;
    BlockPos[] diagonals = {seal.offset(-d, 0, -d), seal.offset(d, 0, -d), seal.offset(d, 0, d), seal.offset(-d, 0, d)};
    BlockPos[] axes = {seal.offset(0, 0, -d), seal.offset(d, 0, 0), seal.offset(0, 0, d), seal.offset(-d, 0, 0)};
    Puzzle p = new Puzzle();
    switch (kind) {
      case BRAZIERS -> {
        for (BlockPos pos : diagonals) if (!fits(level, pos)) return null;
        p.kind = "seal_braziers";
        p.solution = EnvesPuzzleRules.echo(random, 4, balance.echoLength(floor.depth()));
        for (BlockPos pos : diagonals) {
          pedestal(level, pos);
          level.setBlock(pos, EnvesContent.BRAZIER.get().defaultBlockState(), Block.UPDATE_ALL);
          p.elements.add(pos);
        }
        p.extras.add(seal);
      }
      case LEVERS -> {
        for (BlockPos pos : diagonals) if (!fits(level, pos)) return null;
        for (BlockPos pos : axes) if (!fits(level, pos)) return null;
        p.kind = "seal_levers";
        var board = EnvesPuzzleRules.levers(random, 4, 4);
        p.data = board.masks().clone();
        p.state = new int[] {board.start(), 0};
        Direction[] facing = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
        for (int i = 0; i < 4; i++) {
          pedestal(level, axes[i]);
          level.setBlock(axes[i], Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
              .setValue(LeverBlock.FACING, facing[i]).setValue(LeverBlock.POWERED, false), Block.UPDATE_CLIENTS);
          p.elements.add(axes[i]);
        }
        for (BlockPos pos : diagonals) {
          pedestal(level, pos);
          p.extras.add(pos);
        }
        p.extras.add(seal);
        EnvesPuzzles.showLamps(level, p);
      }
      case MIRRORS -> {
        EnvesPuzzleRules.Mirrors mirrors = null;
        for (int attempt = 0; attempt < 12 && mirrors == null; attempt++) {
          var candidate = EnvesPuzzleRules.mirrors(random, balance.mirrorTurns(floor.depth()));
          if (lightFits(level, seal, candidate)) mirrors = candidate;
        }
        if (mirrors == null) return null;
        p.kind = "seal_mirrors";
        // The light runs along the room's axes a block over the pedestals: their candles would stop it.
        for (BlockPos pos : axes)
          if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CANDLES)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        List<Integer> data = new ArrayList<>(List.of(mirrors.font()[0], mirrors.font()[1], mirrors.direction()[0], mirrors.direction()[1]));
        for (int i = 0; i < mirrors.mirrors().size(); i++) {
          int[] off = mirrors.mirrors().get(i);
          BlockPos pos = seal.offset(off[0], 0, off[1]);
          pedestal(level, pos);
          level.setBlock(pos, EnvesContent.MIRROR.get().defaultBlockState().setValue(EnvesContentBlocks.Mirror.AIM, mirrors.start()[i]),
              Block.UPDATE_ALL);
          p.elements.add(pos);
          data.add(off[0]);
          data.add(off[1]);
        }
        BlockPos font = seal.offset(mirrors.font()[0], 0, mirrors.font()[1]);
        pedestal(level, font);
        level.setBlock(font, Blocks.VERDANT_FROGLIGHT.defaultBlockState(), Block.UPDATE_ALL);
        p.solution = mirrors.solution().clone();
        p.state = mirrors.start().clone();
        p.data = data.stream().mapToInt(Integer::intValue).toArray();
        p.extras.add(font);
        p.extras.add(seal);
      }
    }
    return p;
  }

  /** Every piece and every block of the solved light's path is free in this room (the art may differ per tileset). */
  static boolean lightFits(ServerLevel level, BlockPos seal, EnvesPuzzleRules.Mirrors mirrors) {
    if (!fits(level, seal.offset(mirrors.font()[0], 0, mirrors.font()[1]))) return false;
    for (int[] off : mirrors.mirrors()) if (!fits(level, seal.offset(off[0], 0, off[1]))) return false;
    for (int[] cell : EnvesPuzzleRules.trace(mirrors, mirrors.solution()).cells()) {
      var state = level.getBlockState(seal.offset(cell[0], 0, cell[1]));
      boolean piece = mirrors.mirrors().stream().anyMatch(m -> m[0] == cell[0] && m[1] == cell[1]);
      if (!piece && !state.isAir() && !state.is(net.minecraft.tags.BlockTags.CANDLES)) return false;
    }
    return true;
  }

  /** A calcite foot under a puzzle piece, where the room had none. */
  private static void pedestal(ServerLevel level, BlockPos piece) {
    BlockPos below = piece.below();
    if (level.getBlockState(below).isAir()) level.setBlock(below, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_ALL);
  }

  /** A seal's puzzle is solved: the seal lights for the group. */
  static void puzzleSolved(ServerLevel level, Attempt attempt, Puzzle puzzle, ServerPlayer player) {
    var runs = EnvesRuns.get(level.getServer());
    var seal = runs.run(attempt.id).floor(puzzle.depth).seal(puzzle.cell);
    seal.solved = true;
    runs.setDirty();
    BlockPos pos = puzzle.extras.getLast();
    level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.08);
    EnvesPuzzles.schedule(level.getServer(), 12, () -> Enves.lightSeal(player, pos));
  }

  /** Dresses every puzzle seal of a floor as soon as it is placed. */
  static void floorReady(EnvesHooks.Floor floor) {
    if (floor.depth() >= EnvesLayout.BOSS_DEPTH) return;
    for (int cell : floor.layout().seals()) {
      state(floor, cell);
      dress(floor, cell);
    }
  }

  /** The server stopped: every circle's bar goes. */
  static void forgetAll() {
    CIRCLES.values().forEach(circle -> circle.bar().removeAllPlayers());
    CIRCLES.clear();
  }

  /** An attempt ended: its circles' bars go. */
  static void forget(UUID attempt) {
    CIRCLES.entrySet().removeIf(entry -> {
      if (!entry.getValue().attempt().equals(attempt)) return false;
      entry.getValue().bar().removeAllPlayers();
      return true;
    });
  }
}
