package dev.entrelumen;

import dev.entrelumen.ApotheosisTiers.Tier;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
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
 * The Envés's saved state, in {@code data/entrelumen_enves.dat} of the overworld: the Sealed Stair
 * of the start ruin and every attempt with its slot, pool and floors. Survives restarts; a floor
 * still being placed or wiped resumes from the start of that floor. The layouts are not stored:
 * {@link EnvesLayout#descent} redraws them from each attempt's seed.
 */
public final class EnvesData extends SavedData {
  static final int VERSION = 1;

  /** The Sealed Stair under the start ruin: its seal, the gate and the antechamber. */
  public static final class Entrance {
    public boolean open;
    public BlockPos heart;
    public final List<BlockPos> sealCells = new ArrayList<>();
    /** The mosaic blocks the seal cells held, for an operator who closes the heart again. */
    public final List<net.minecraft.world.level.block.state.BlockState> sealStates = new ArrayList<>();
    public final List<BlockPos> gate = new ArrayList<>();
    public BlockPos antechamber;
    /** The stair's underground box (min x, y, z, max x, y, z): no natural spawns inside. */
    public int[] box = new int[0];

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("open", open);
      if (heart != null) tag.putLong("heart", heart.asLong());
      tag.putLongArray("seal", sealCells.stream().mapToLong(BlockPos::asLong).toArray());
      ListTag states = new ListTag();
      for (var state : sealStates) states.add(net.minecraft.nbt.NbtUtils.writeBlockState(state));
      tag.put("sealStates", states);
      tag.putLongArray("gate", gate.stream().mapToLong(BlockPos::asLong).toArray());
      if (antechamber != null) tag.putLong("antechamber", antechamber.asLong());
      tag.putIntArray("box", box);
      return tag;
    }

    static Entrance load(CompoundTag tag) {
      Entrance e = new Entrance();
      e.open = tag.getBoolean("open");
      if (tag.contains("heart")) e.heart = BlockPos.of(tag.getLong("heart"));
      for (long pos : tag.getLongArray("seal")) e.sealCells.add(BlockPos.of(pos));
      for (Tag state : tag.getList("sealStates", Tag.TAG_COMPOUND))
        e.sealStates.add(net.minecraft.nbt.NbtUtils.readBlockState(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) state));
      for (long pos : tag.getLongArray("gate")) e.gate.add(BlockPos.of(pos));
      if (tag.contains("antechamber")) e.antechamber = BlockPos.of(tag.getLong("antechamber"));
      e.box = tag.getIntArray("box");
      return e;
    }

    public boolean inBox(BlockPos pos) {
      return box.length == 6 && pos.getX() >= box[0] && pos.getY() >= box[1] && pos.getZ() >= box[2]
          && pos.getX() <= box[3] && pos.getY() <= box[4] && pos.getZ() <= box[5];
    }
  }

  public enum Placement {NONE, QUEUED, PLACING, READY}

  public enum Status {
    /** Floors 0 and I are being placed; nobody is inside yet. */
    FORMING,
    OPEN,
    /** Over: its blocks are being wiped, then the slot is free. */
    ENDED
  }

  /** One floor of an attempt (depth 0 is the vestibule). */
  public static final class FloorState {
    public Placement placement = Placement.NONE;
    public final BitSet explored = new BitSet(EnvesLayout.CELLS);
    public final BitSet lit = new BitSet(EnvesLayout.CELLS);
    /** Fight and guard rooms somebody of the group has entered (encounters trigger once). */
    public final BitSet entered = new BitSet(EnvesLayout.CELLS);
    public boolean stairOpen, guardReached, exitReached;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("placement", placement.name());
      tag.putLongArray("explored", explored.toLongArray());
      tag.putLongArray("lit", lit.toLongArray());
      tag.putLongArray("entered", entered.toLongArray());
      tag.putBoolean("stairOpen", stairOpen);
      tag.putBoolean("guard", guardReached);
      tag.putBoolean("exit", exitReached);
      return tag;
    }

    static FloorState load(CompoundTag tag) {
      FloorState f = new FloorState();
      try {
        f.placement = Placement.valueOf(tag.getString("placement"));
      } catch (IllegalArgumentException e) {
        f.placement = Placement.NONE;
      }
      f.explored.or(BitSet.valueOf(tag.getLongArray("explored")));
      f.lit.or(BitSet.valueOf(tag.getLongArray("lit")));
      f.entered.or(BitSet.valueOf(tag.getLongArray("entered")));
      f.stairOpen = tag.getBoolean("stairOpen");
      f.guardReached = tag.getBoolean("guard");
      f.exitReached = tag.getBoolean("exit");
      return f;
    }
  }

  /** One paid attempt of a team: its slot, difficulty, pool and floors. */
  public static final class Attempt {
    public final UUID id;
    public final UUID team;
    public final int slot;
    public final long seed;
    public final Tier tier;
    public final UUID payer;
    public final long openedAt;
    public Status status = Status.FORMING;
    public int poolTotal, poolLeft;
    /** Deepest floor anyone of the group has reached. */
    public int frontline = 1;
    /** Game time since which nobody of the group is inside; -1 while someone is. */
    public long emptySince = -1;
    public boolean bossDefeated;
    /** Each member's floor, for respawns and rejoining. */
    public final Map<UUID, Integer> depthOf = new HashMap<>();
    public final FloorState[] floors = new FloorState[EnvesGeometry.DEPTHS];

    public Attempt(UUID id, UUID team, int slot, long seed, Tier tier, UUID payer, long openedAt) {
      this.id = id;
      this.team = team;
      this.slot = slot;
      this.seed = seed;
      this.tier = tier;
      this.payer = payer;
      this.openedAt = openedAt;
      for (int i = 0; i < floors.length; i++) floors[i] = new FloorState();
    }

    public FloorState floor(int depth) {
      return floors[depth];
    }

    public boolean live() {
      return status != Status.ENDED;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", id);
      tag.putUUID("team", team);
      tag.putInt("slot", slot);
      tag.putLong("seed", seed);
      tag.putString("tier", tier.name());
      tag.putUUID("payer", payer);
      tag.putLong("openedAt", openedAt);
      tag.putString("status", status.name());
      tag.putInt("poolTotal", poolTotal);
      tag.putInt("poolLeft", poolLeft);
      tag.putInt("frontline", frontline);
      tag.putLong("emptySince", emptySince);
      tag.putBoolean("bossDefeated", bossDefeated);
      CompoundTag depths = new CompoundTag();
      depthOf.forEach((player, depth) -> depths.putInt(player.toString(), depth));
      tag.put("depthOf", depths);
      ListTag list = new ListTag();
      for (FloorState floor : floors) list.add(floor.save());
      tag.put("floors", list);
      return tag;
    }

    static Attempt load(CompoundTag tag) {
      Tier tier;
      try {
        tier = Tier.valueOf(tag.getString("tier"));
      } catch (IllegalArgumentException e) {
        tier = Tier.FRONTIER;
      }
      Attempt a = new Attempt(tag.getUUID("id"), tag.getUUID("team"), tag.getInt("slot"), tag.getLong("seed"), tier,
          tag.getUUID("payer"), tag.getLong("openedAt"));
      try {
        a.status = Status.valueOf(tag.getString("status"));
      } catch (IllegalArgumentException e) {
        a.status = Status.ENDED;
      }
      a.poolTotal = tag.getInt("poolTotal");
      a.poolLeft = tag.getInt("poolLeft");
      a.frontline = Math.clamp(tag.getInt("frontline"), 1, EnvesLayout.FLOORS);
      a.emptySince = tag.getLong("emptySince");
      a.bossDefeated = tag.getBoolean("bossDefeated");
      CompoundTag depths = tag.getCompound("depthOf");
      for (String key : depths.getAllKeys()) {
        try {
          a.depthOf.put(UUID.fromString(key), depths.getInt(key));
        } catch (IllegalArgumentException ignored) {
          // a corrupt key only loses that member's floor
        }
      }
      ListTag list = tag.getList("floors", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(list.size(), a.floors.length); i++) a.floors[i] = FloorState.load(list.getCompound(i));
      return a;
    }
  }

  public Entrance entrance = new Entrance();
  private final Map<UUID, Attempt> attempts = new LinkedHashMap<>();

  public static EnvesData get(MinecraftServer server) {
    return server.overworld().getDataStorage()
        .computeIfAbsent(new Factory<>(EnvesData::new, EnvesData::load), "entrelumen_enves");
  }

  public List<Attempt> attempts() {
    return Collections.unmodifiableList(new ArrayList<>(attempts.values()));
  }

  public Optional<Attempt> attempt(UUID id) {
    return Optional.ofNullable(attempts.get(id));
  }

  /** The team's live attempt (forming or open), if any. */
  public Optional<Attempt> forTeam(UUID team) {
    return attempts.values().stream().filter(a -> a.team.equals(team) && a.live()).findFirst();
  }

  public Optional<Attempt> inSlot(int slot) {
    return attempts.values().stream().filter(a -> a.slot == slot).findFirst();
  }

  /** The lowest slot no attempt holds (ended ones hold theirs until wiped). */
  public int freeSlot() {
    boolean[] used = new boolean[EnvesGeometry.MAX_SLOTS];
    for (Attempt a : attempts.values()) if (a.slot >= 0 && a.slot < used.length) used[a.slot] = true;
    for (int i = 0; i < used.length; i++) if (!used[i]) return i;
    return -1;
  }

  void add(Attempt attempt) {
    attempts.put(attempt.id, attempt);
    setDirty();
  }

  void remove(UUID id) {
    if (attempts.remove(id) != null) setDirty();
  }

  public static EnvesData load(CompoundTag tag, HolderLookup.Provider lookup) {
    if (tag.getInt("version") > VERSION) throw new IllegalStateException("Unsupported Entrelumen Envés data version");
    EnvesData data = new EnvesData();
    data.entrance = Entrance.load(tag.getCompound("entrance"));
    for (Tag element : tag.getList("attempts", Tag.TAG_COMPOUND)) {
      try {
        Attempt attempt = Attempt.load((CompoundTag) element);
        data.attempts.put(attempt.id, attempt);
      } catch (RuntimeException e) {
        com.mojang.logging.LogUtils.getLogger().error("Skipping a corrupt Envés attempt", e);
      }
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    tag.putInt("version", VERSION);
    tag.put("entrance", entrance.save());
    ListTag list = new ListTag();
    for (Attempt attempt : attempts.values()) list.add(attempt.save());
    tag.put("attempts", list);
    return tag;
  }
}
