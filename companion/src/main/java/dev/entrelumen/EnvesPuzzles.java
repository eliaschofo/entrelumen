package dev.entrelumen;

import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesRuns.Puzzle;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Vector3f;

/**
 * The puzzles of seals and vaults at play: clicks on their elements (and on a vault's gate), the
 * braziers' echo played back, the sour light traced through the mirrors, and what solving does (a
 * vault opens, a seal lights). The rules are {@link EnvesPuzzleRules}; the state lives in
 * {@link EnvesRuns}, so a restart keeps every half-solved lock.
 *
 * <p>Kinds: {@code vault_glyphs}, {@code vault_braziers}, {@code vault_levers}, {@code vault_offering},
 * {@code seal_braziers}, {@code seal_levers}, {@code seal_mirrors}.
 */
public final class EnvesPuzzles {
  private EnvesPuzzles() {}

  /** Echo tempo: a brazier flashes every this many ticks, and stays lit most of it. */
  static final int ECHO_STEP = 14, ECHO_LIT = 9;
  /** The notes of the braziers (pitch): a rising chord, one per brazier. */
  static final float[] NOTES = {0.6f, 0.8f, 1.0f, 1.25f};
  static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(0.86f, 0.9f, 0.48f), 1.1f);
  static final BlockState LAMP_OFF = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
  static final BlockState LAMP_ON = Blocks.VERDANT_FROGLIGHT.defaultBlockState();

  /** One clickable place of a puzzle: an element (index ≥ 0) or a part that replays the clue (-1). */
  record Ref(UUID attempt, int depth, int cell, int element) {}

  private static int indexed = -1;
  private static EnvesRuns indexedRuns;
  private static final Long2ObjectOpenHashMap<Ref> INDEX = new Long2ObjectOpenHashMap<>();

  record Scheduled(long at, Runnable task) {}

  private static final List<Scheduled> SCHEDULED = new ArrayList<>();

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnvesPuzzles::onRightClick);
  }

  static void schedule(MinecraftServer server, int delay, Runnable task) {
    SCHEDULED.add(new Scheduled(server.getTickCount() + delay, task));
  }

  // ---- Index --------------------------------------------------------------------------------

  private static void reindex(MinecraftServer server) {
    var runs = EnvesRuns.get(server);
    if (indexedRuns == runs && indexed == runs.revision()) return;
    INDEX.clear();
    for (var run : runs.runs())
      for (var floor : run.floors.entrySet())
        for (var entry : floor.getValue().puzzles.entrySet()) {
          Puzzle p = entry.getValue();
          for (int i = 0; i < p.elements.size(); i++)
            INDEX.put(p.elements.get(i).asLong(), new Ref(run.attempt, floor.getKey(), entry.getKey(), i));
          if (p.kind.startsWith("vault"))
            for (BlockPos pos : p.extras) INDEX.putIfAbsent(pos.asLong(), new Ref(run.attempt, floor.getKey(), entry.getKey(), -1));
        }
    indexed = runs.revision();
    indexedRuns = runs;
  }

  /** The server stopped: nothing of its puzzles stays in memory. */
  static void forget() {
    INDEX.clear();
    indexedRuns = null;
    indexed = -1;
    SCHEDULED.clear();
  }

  static Puzzle puzzle(MinecraftServer server, Ref ref) {
    var run = EnvesRuns.get(server).find(ref.attempt());
    if (run.isEmpty()) return null;
    var floor = run.get().floors.get(ref.depth());
    return floor == null ? null : floor.puzzles.get(ref.cell());
  }

  /** Stores a new puzzle for a cell and indexes its elements. */
  static void put(MinecraftServer server, Attempt attempt, int depth, int cell, Puzzle puzzle) {
    var runs = EnvesRuns.get(server);
    puzzle.depth = depth;
    puzzle.cell = cell;
    runs.run(attempt.id).floor(depth).puzzles.put(cell, puzzle);
    runs.touchPuzzles();
  }

  // ---- Clicks -------------------------------------------------------------------------------

  static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
    if (!level.dimension().equals(Enves.LEVEL)) return;
    BlockPos pos = event.getPos();
    if (level.getBlockState(pos).is(EnvesContent.SHRINE.get())) {
      if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) EnvesShrines.use(player, pos);
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.SUCCESS);
      return;
    }
    reindex(player.server);
    Ref ref = INDEX.get(pos.asLong());
    if (ref == null) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
    if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
    var attempt = EnvesData.get(player.server).attempt(ref.attempt());
    if (attempt.isEmpty() || attempt.get().status != EnvesData.Status.OPEN || !attempt.get().team.equals(Enves.teamOf(player))) return;
    Puzzle puzzle = puzzle(player.server, ref);
    if (puzzle == null || puzzle.solved) return;
    click(level, attempt.get(), puzzle, ref.element(), player);
  }

  /** A member works an element of a puzzle ({@code element} -1: they touched the gate). */
  static void click(ServerLevel level, Attempt attempt, Puzzle puzzle, int element, ServerPlayer player) {
    var runs = EnvesRuns.get(level.getServer());
    switch (puzzle.kind) {
      case "vault_glyphs" -> {
        if (element < 0) {
          player.displayClientMessage(Component.translatable("entrelumen.enves.vault.glyphs.hint"), true);
          return;
        }
        puzzle.state[element] = (puzzle.state[element] + 1) % EnvesPuzzleRules.GLYPHS;
        BlockPos pos = puzzle.elements.get(element);
        level.setBlock(pos, EnvesContent.GLYPH.get().defaultBlockState().setValue(EnvesContentBlocks.Glyph.GLYPH, puzzle.state[element]),
            Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 1.6f);
        if (EnvesPuzzleRules.glyphsOpen(puzzle.state, puzzle.solution)) solve(level, attempt, puzzle, player);
        else if (EnvesPuzzleRules.glyphsLiteral(puzzle.state, puzzle.solution)) {
          puzzle.misses++;
          level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.5f);
          player.displayClientMessage(Component.translatable("entrelumen.enves.vault.glyphs.backwards"), true);
        }
      }
      case "vault_braziers", "seal_braziers" -> {
        if (element < 0) {
          play(level, puzzle);
          return;
        }
        if (puzzle.echoAt >= 0) return;
        int before = puzzle.progress;
        puzzle.progress = EnvesPuzzleRules.echoStep(puzzle.solution, puzzle.progress, element);
        flash(level, puzzle, element, 10);
        if (puzzle.progress >= puzzle.solution.length) {
          for (BlockPos pos : puzzle.elements) setLit(level, pos, true);
          solve(level, attempt, puzzle, player);
        } else if (puzzle.progress <= before && !(before == 0 && puzzle.progress == 1)) {
          puzzle.misses++;
          level.playSound(null, puzzle.elements.get(element), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1f, 0.5f);
          level.playSound(null, puzzle.elements.get(element), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
          if (puzzle.misses == 1 || puzzle.misses % 4 == 0)
            player.displayClientMessage(Component.translatable("entrelumen.enves.braziers.wrong"), true);
        }
      }
      case "vault_levers", "seal_levers" -> {
        if (element < 0) {
          player.displayClientMessage(Component.translatable("entrelumen.enves.levers.hint"), true);
          return;
        }
        int lamps = puzzle.state[0] ^ puzzle.data[element];
        puzzle.state[0] = lamps;
        puzzle.state[1] ^= 1 << element;
        BlockPos lever = puzzle.elements.get(element);
        BlockState state = level.getBlockState(lever);
        if (state.hasProperty(LeverBlock.POWERED))
          level.setBlock(lever, state.setValue(LeverBlock.POWERED, (puzzle.state[1] & 1 << element) != 0), Block.UPDATE_CLIENTS);
        level.playSound(null, lever, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, (puzzle.state[1] & 1 << element) != 0 ? 0.6f : 0.5f);
        showLamps(level, puzzle);
        if (lamps == (1 << lampCount(puzzle)) - 1) solve(level, attempt, puzzle, player);
      }
      case "vault_offering" -> {
        int need = puzzle.data[0];
        var shard = EnvesContent.SOUR_LIGHT_SHARD.get();
        int have = 0;
        for (ItemStack stack : Entrelumen.deliveryStacks(player)) if (stack.is(shard)) have += stack.getCount();
        if (have < need) {
          player.displayClientMessage(Component.translatable("entrelumen.enves.vault.offering.ask", need), true);
          level.playSound(null, puzzle.elements.getFirst(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 0.8f);
          return;
        }
        int left = need;
        for (ItemStack stack : Entrelumen.deliveryStacks(player)) {
          if (left <= 0) break;
          if (!stack.is(shard)) continue;
          int take = Math.min(left, stack.getCount());
          stack.shrink(take);
          left -= take;
        }
        player.getInventory().setChanged();
        setLit(level, puzzle.elements.getFirst(), true);
        level.playSound(null, puzzle.elements.getFirst(), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6f, 1.4f);
        solve(level, attempt, puzzle, player);
      }
      case "seal_mirrors" -> {
        if (element < 0) return;
        puzzle.state[element] ^= 1;
        BlockPos pos = puzzle.elements.get(element);
        level.setBlock(pos, EnvesContent.MIRROR.get().defaultBlockState().setValue(EnvesContentBlocks.Mirror.AIM, puzzle.state[element]),
            Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8f, 1.2f);
        if (beam(puzzle).reached()) solve(level, attempt, puzzle, player);
      }
      default -> {}
    }
    runs.setDirty();
  }

  static int lampCount(Puzzle puzzle) {
    return puzzle.kind.equals("vault_levers") ? 3 : 4;
  }

  static void showLamps(ServerLevel level, Puzzle puzzle) {
    int count = lampCount(puzzle);
    for (int i = 0; i < count && i < puzzle.extras.size(); i++)
      level.setBlock(puzzle.extras.get(i), (puzzle.state[0] & 1 << i) != 0 ? LAMP_ON : LAMP_OFF, Block.UPDATE_ALL);
  }

  /** Solving: a vault opens, a seal lights for the solver's group. */
  static void solve(ServerLevel level, Attempt attempt, Puzzle puzzle, ServerPlayer player) {
    puzzle.solved = true;
    EnvesRuns.get(level.getServer()).setDirty();
    BlockPos at = puzzle.elements.isEmpty() ? player.blockPosition() : puzzle.elements.getFirst();
    level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.4f);
    level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 1, 1, 1, 0.05);
    if (puzzle.kind.startsWith("vault")) {
      var floor = Enves.floor(level, attempt, puzzle.depth);
      schedule(level.getServer(), 10, () -> EnvesVaults.open(floor, puzzle));
      for (ServerPlayer member : Enves.membersInside(level.getServer(), attempt))
        if (member.distanceToSqr(player) < 32 * 32) member.displayClientMessage(Component.translatable("entrelumen.enves.vault.open"), true);
    } else {
      EnvesSeals.puzzleSolved(level, attempt, puzzle, player);
    }
  }

  // ---- The braziers' echo -------------------------------------------------------------------

  /** Plays the echo: each brazier of the sequence flares in turn, with its note. */
  static void play(ServerLevel level, Puzzle puzzle) {
    if (puzzle.solved || puzzle.echoAt >= 0) return;
    puzzle.echoAt = level.getServer().getTickCount();
    puzzle.progress = 0;
    puzzle.heard = true;
    for (BlockPos pos : puzzle.elements) setLit(level, pos, false);
    level.playSound(null, puzzle.elements.getFirst(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 0.5f);
    EnvesRuns.get(level.getServer()).setDirty();
  }

  static void flash(ServerLevel level, Puzzle puzzle, int element, int ticks) {
    BlockPos pos = puzzle.elements.get(element);
    setLit(level, pos, true);
    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, NOTES[element % NOTES.length]);
    level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.15, 0.2, 0.15, 0.01);
    schedule(level.getServer(), ticks, () -> {
      if (!puzzle.solved) setLit(level, pos, false);
    });
  }

  static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
    BlockState state = level.getBlockState(pos);
    if (state.hasProperty(EnvesContentBlocks.Brazier.LIT) && state.getValue(EnvesContentBlocks.Brazier.LIT) != lit)
      level.setBlock(pos, state.setValue(EnvesContentBlocks.Brazier.LIT, lit), Block.UPDATE_ALL);
  }

  // ---- The mirrors --------------------------------------------------------------------------

  /** The light of a mirror puzzle as it stands: data holds the font's offset and direction. */
  static EnvesPuzzleRules.Beam beam(Puzzle puzzle) {
    return EnvesPuzzleRules.trace(mirrors(puzzle), puzzle.state);
  }

  static EnvesPuzzleRules.Mirrors mirrors(Puzzle puzzle) {
    List<int[]> offsets = new ArrayList<>();
    for (int i = 0; i < puzzle.elements.size(); i++) offsets.add(new int[] {puzzle.data[4 + 2 * i], puzzle.data[5 + 2 * i]});
    return new EnvesPuzzleRules.Mirrors(new int[] {puzzle.data[0], puzzle.data[1]}, new int[] {puzzle.data[2], puzzle.data[3]},
        offsets, puzzle.solution, new boolean[offsets.size()], puzzle.state);
  }

  // ---- Ticking ------------------------------------------------------------------------------

  static void tick(MinecraftServer server) {
    long now = server.getTickCount();
    if (!SCHEDULED.isEmpty()) {
      List<Runnable> due = new ArrayList<>();
      for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
        Scheduled s = it.next();
        if (s.at() <= now) {
          due.add(s.task());
          it.remove();
        }
      }
      due.forEach(Runnable::run);
    }
    ServerLevel level = Enves.level(server);
    if (level == null) return;
    for (var run : EnvesRuns.get(server).runs()) {
      var attempt = EnvesData.get(server).attempt(run.attempt);
      if (attempt.isEmpty() || attempt.get().status != EnvesData.Status.OPEN) continue;
      for (var floor : run.floors.values())
        for (Puzzle puzzle : floor.puzzles.values()) {
          if (puzzle.echoAt >= 0) echoTick(level, puzzle, now);
          if (puzzle.kind.equals("seal_mirrors") && now % 4 == 0 && !puzzle.elements.isEmpty()) lightTick(level, puzzle);
        }
    }
  }

  private static void echoTick(ServerLevel level, Puzzle puzzle, long now) {
    long t = now - puzzle.echoAt - 10;
    if (t < 0) return;
    int step = (int) (t / ECHO_STEP);
    if (step >= puzzle.solution.length) {
      puzzle.echoAt = -1;
      return;
    }
    if (t % ECHO_STEP == 0) flash(level, puzzle, puzzle.solution[step], ECHO_LIT);
  }

  private static void lightTick(ServerLevel level, Puzzle puzzle) {
    BlockPos seal = puzzle.extras.size() > 1 ? puzzle.extras.get(1) : null;
    BlockPos font = puzzle.extras.getFirst();
    if (seal == null || !level.isLoaded(font)) return;
    if (level.getNearestPlayer(seal.getX() + 0.5, seal.getY(), seal.getZ() + 0.5, 18, false) == null) return;
    var beam = beam(puzzle);
    for (int[] cell : beam.cells())
      level.sendParticles(LIGHT, seal.getX() + cell[0] + 0.5, seal.getY() + 0.5, seal.getZ() + cell[1] + 0.5, 1, 0.05, 0.05, 0.05, 0);
    if (beam.reached()) level.sendParticles(ParticleTypes.END_ROD, seal.getX() + 0.5, seal.getY() + 0.5, seal.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
  }
}
