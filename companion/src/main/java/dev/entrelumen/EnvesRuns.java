package dev.entrelumen;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the Envés's content remembers of each attempt, beside the engine's {@link EnvesData}: the
 * blessing of each floor's shrine, the stair's champion, each seal's variant and guardian or circle,
 * and every puzzle of seals and vaults with its elements, answer and state. In
 * {@code data/entrelumen_enves_runs.dat} of the overworld; an attempt's entry goes with it.
 */
public final class EnvesRuns extends SavedData {
  static final int VERSION = 1;
  static final String NAME = "entrelumen_enves_runs";

  /** One puzzle of a seal or a vault. {@code elements} are clicked; {@code extras} only show. */
  public static final class Puzzle {
    public String kind = "";
    public int depth, cell;
    public final List<BlockPos> elements = new ArrayList<>();
    public final List<BlockPos> extras = new ArrayList<>();
    public int[] solution = new int[0];
    public int[] state = new int[0];
    /** Kind-specific numbers: lever masks, the mirrors' font direction, the brazier notes. */
    public int[] data = new int[0];
    public int progress, misses;
    public boolean solved, heard;
    /** Transient: game time the echo started playing, or -1. */
    public transient long echoAt = -1;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("kind", kind);
      tag.putInt("depth", depth);
      tag.putInt("cell", cell);
      tag.putLongArray("elements", elements.stream().mapToLong(BlockPos::asLong).toArray());
      tag.putLongArray("extras", extras.stream().mapToLong(BlockPos::asLong).toArray());
      tag.putIntArray("solution", solution);
      tag.putIntArray("state", state);
      tag.putIntArray("data", data);
      tag.putInt("progress", progress);
      tag.putInt("misses", misses);
      tag.putBoolean("solved", solved);
      tag.putBoolean("heard", heard);
      return tag;
    }

    static Puzzle load(CompoundTag tag) {
      Puzzle p = new Puzzle();
      p.kind = tag.getString("kind");
      p.depth = tag.getInt("depth");
      p.cell = tag.getInt("cell");
      for (long pos : tag.getLongArray("elements")) p.elements.add(BlockPos.of(pos));
      for (long pos : tag.getLongArray("extras")) p.extras.add(BlockPos.of(pos));
      p.solution = tag.getIntArray("solution");
      p.state = tag.getIntArray("state");
      p.data = tag.getIntArray("data");
      p.progress = tag.getInt("progress");
      p.misses = tag.getInt("misses");
      p.solved = tag.getBoolean("solved");
      p.heard = tag.getBoolean("heard");
      return p;
    }
  }

  /** One seal: its variant, its guardian or its circle. */
  public static final class Seal {
    public EnvesPuzzleRules.SealVariant variant = EnvesPuzzleRules.SealVariant.GUARDIAN;
    public UUID guardian;
    public boolean guardianSpawned, guardianDead, solved;
    /** The circle: ticks held so far, whether it runs, ticks nobody stood in it, waves already sent. */
    public int held, empty, waves;
    public boolean circle;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("variant", variant.name());
      if (guardian != null) tag.putUUID("guardian", guardian);
      tag.putBoolean("guardianSpawned", guardianSpawned);
      tag.putBoolean("guardianDead", guardianDead);
      tag.putBoolean("solved", solved);
      tag.putInt("held", held);
      tag.putInt("empty", empty);
      tag.putInt("waves", waves);
      tag.putBoolean("circle", circle);
      return tag;
    }

    static Seal load(CompoundTag tag) {
      Seal s = new Seal();
      try {
        s.variant = EnvesPuzzleRules.SealVariant.valueOf(tag.getString("variant"));
      } catch (IllegalArgumentException e) {
        s.variant = EnvesPuzzleRules.SealVariant.GUARDIAN;
      }
      if (tag.hasUUID("guardian")) s.guardian = tag.getUUID("guardian");
      s.guardianSpawned = tag.getBoolean("guardianSpawned");
      s.guardianDead = tag.getBoolean("guardianDead");
      s.solved = tag.getBoolean("solved");
      s.held = tag.getInt("held");
      s.empty = tag.getInt("empty");
      s.waves = tag.getInt("waves");
      s.circle = tag.getBoolean("circle");
      return s;
    }
  }

  /** One floor of an attempt. */
  public static final class Floor {
    public String blessing = "";
    public boolean blessingTaken, dressed;
    /** The shrine's altar, once placed. */
    public BlockPos shrine;
    public UUID champion;
    public boolean championSpawned, championDead;
    public final Map<Integer, Seal> seals = new HashMap<>();
    public final Map<Integer, Puzzle> puzzles = new HashMap<>();
    /** Seal and vault rooms somebody of the group has stood in. */
    public final BitSet visited = new BitSet(EnvesLayout.CELLS);

    public Seal seal(int cell) {
      return seals.computeIfAbsent(cell, c -> new Seal());
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("blessing", blessing);
      tag.putBoolean("blessingTaken", blessingTaken);
      tag.putBoolean("dressed", dressed);
      if (shrine != null) tag.putLong("shrine", shrine.asLong());
      if (champion != null) tag.putUUID("champion", champion);
      tag.putBoolean("championSpawned", championSpawned);
      tag.putBoolean("championDead", championDead);
      CompoundTag seals = new CompoundTag();
      this.seals.forEach((cell, seal) -> seals.put(Integer.toString(cell), seal.save()));
      tag.put("seals", seals);
      CompoundTag puzzles = new CompoundTag();
      this.puzzles.forEach((cell, puzzle) -> puzzles.put(Integer.toString(cell), puzzle.save()));
      tag.put("puzzles", puzzles);
      tag.putLongArray("visited", visited.toLongArray());
      return tag;
    }

    static Floor load(CompoundTag tag) {
      Floor f = new Floor();
      f.blessing = tag.getString("blessing");
      f.blessingTaken = tag.getBoolean("blessingTaken");
      f.dressed = tag.getBoolean("dressed");
      if (tag.contains("shrine")) f.shrine = BlockPos.of(tag.getLong("shrine"));
      if (tag.hasUUID("champion")) f.champion = tag.getUUID("champion");
      f.championSpawned = tag.getBoolean("championSpawned");
      f.championDead = tag.getBoolean("championDead");
      CompoundTag seals = tag.getCompound("seals");
      for (String key : seals.getAllKeys()) f.seals.put(Integer.parseInt(key), Seal.load(seals.getCompound(key)));
      CompoundTag puzzles = tag.getCompound("puzzles");
      for (String key : puzzles.getAllKeys()) f.puzzles.put(Integer.parseInt(key), Puzzle.load(puzzles.getCompound(key)));
      f.visited.or(BitSet.valueOf(tag.getLongArray("visited")));
      return f;
    }
  }

  /** One attempt. */
  public static final class Run {
    public final UUID attempt;
    public final Map<Integer, Floor> floors = new HashMap<>();
    public UUID boss;
    public boolean bossSpawned, bossChestPlaced;

    Run(UUID attempt) {
      this.attempt = attempt;
    }

    public Floor floor(int depth) {
      return floors.computeIfAbsent(depth, d -> new Floor());
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("attempt", attempt);
      if (boss != null) tag.putUUID("boss", boss);
      tag.putBoolean("bossSpawned", bossSpawned);
      tag.putBoolean("bossChestPlaced", bossChestPlaced);
      ListTag floors = new ListTag();
      this.floors.forEach((depth, floor) -> {
        CompoundTag f = floor.save();
        f.putInt("depth", depth);
        floors.add(f);
      });
      tag.put("floors", floors);
      return tag;
    }

    static Run load(CompoundTag tag) {
      Run r = new Run(tag.getUUID("attempt"));
      if (tag.hasUUID("boss")) r.boss = tag.getUUID("boss");
      r.bossSpawned = tag.getBoolean("bossSpawned");
      r.bossChestPlaced = tag.getBoolean("bossChestPlaced");
      for (Tag element : tag.getList("floors", Tag.TAG_COMPOUND)) {
        CompoundTag f = (CompoundTag) element;
        r.floors.put(f.getInt("depth"), Floor.load(f));
      }
      return r;
    }
  }

  private final Map<UUID, Run> runs = new LinkedHashMap<>();
  /** Bumped whenever a puzzle is added or removed, so click indexes rebuild. */
  private int revision;

  public static EnvesRuns get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(EnvesRuns::new, EnvesRuns::load), NAME);
  }

  public Run run(UUID attempt) {
    Run run = runs.get(attempt);
    if (run == null) {
      run = new Run(attempt);
      runs.put(attempt, run);
      setDirty();
    }
    return run;
  }

  public Optional<Run> find(UUID attempt) {
    return Optional.ofNullable(runs.get(attempt));
  }

  public List<Run> runs() {
    return List.copyOf(runs.values());
  }

  public void remove(UUID attempt) {
    if (runs.remove(attempt) != null) {
      revision++;
      setDirty();
    }
  }

  public int revision() {
    return revision;
  }

  public void touchPuzzles() {
    revision++;
    setDirty();
  }

  public static EnvesRuns load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION) throw new IllegalStateException("Unsupported Entrelumen Envés runs version");
    EnvesRuns data = new EnvesRuns();
    for (Tag element : tag.getList("runs", Tag.TAG_COMPOUND)) {
      try {
        Run run = Run.load((CompoundTag) element);
        data.runs.put(run.attempt, run);
      } catch (RuntimeException e) {
        com.mojang.logging.LogUtils.getLogger().error("Skipping a corrupt Envés run", e);
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    ListTag list = new ListTag();
    for (Run run : runs.values()) list.add(run.save());
    tag.put("runs", list);
    return tag;
  }
}
