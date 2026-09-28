package dev.entrelumen;

import dev.entrelumen.EnvesBalance.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The encounters hook (docs/design/dungeon-enves.md, «Encuentros»): the first time someone of the
 * group steps into a fight room, the floor's echo table fills its spawn points with one elite and one
 * or two escorts, or two elites; the guard room gets the stair's champion (three affixes) with its
 * escorts, and the stair waits for it; the arena's centre wakes the White Wither. Sometimes a grottol
 * digs out in the Geodes and runs.
 */
public final class EnvesEncounters {
  private EnvesEncounters() {}

  static final long SALT_FIGHT = 41, SALT_GUARD = 42;

  static void roomEntered(EnvesHooks.Floor floor, int cell, EnvesLayout.Role role, List<EnvesHooks.WorldMarker> spawns,
      ServerPlayer first) {
    switch (role) {
      case FIGHT -> fight(floor, cell, spawns, first);
      case GUARD -> guard(floor, cell, spawns, first);
      case ARENA_CENTER -> {
        var center = spawns.stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.BOSS_CENTER).findFirst();
        EnvesBoss.awaken(floor, center.map(EnvesHooks.WorldMarker::pos)
            .orElse(floor.origin(cell).offset(EnvesGeometry.C, 1, EnvesGeometry.C)), first);
      }
      default -> {}
    }
  }

  /** Spawn points far from the one who walked in first, farthest first. */
  static List<BlockPos> points(List<EnvesHooks.WorldMarker> spawns, ServerPlayer first, boolean champion) {
    List<BlockPos> out = new ArrayList<>();
    for (var marker : spawns)
      if (marker.marker().kind() == EnvesMarkers.Kind.ENCOUNTER && marker.marker().argument().equals("champion") == champion)
        out.add(marker.pos());
    out.sort(Comparator.comparingDouble((BlockPos p) -> -p.distToCenterSqr(first.position())));
    return out;
  }

  static void fight(EnvesHooks.Floor floor, int cell, List<EnvesHooks.WorldMarker> spawns, ServerPlayer first) {
    var table = EnvesContentConfig.table(floor.tileset());
    var balance = EnvesContentConfig.balance();
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT_FIGHT);
    List<BlockPos> points = points(spawns, first, false);
    if (points.isEmpty()) points = List.of(floor.origin(cell).offset(EnvesGeometry.C, 1, EnvesGeometry.C));
    List<EnvesEchoes.Spec> group = new ArrayList<>();
    if (table.elites().isEmpty()) {
      for (int i = 0; i < 3; i++) group.add(spec(floor, EnvesEchoTables.draw(table.escorts(), random), Role.ESCORT, List.of()));
    } else {
      var composition = EnvesEchoTables.Group.draw(random);
      for (int i = 0; i < composition.elites; i++) {
        int count = balance.affixCount(Role.ELITE, floor.depth(), random);
        group.add(spec(floor, EnvesEchoTables.draw(table.elites(), random), Role.ELITE, EnvesAffix.pick(count, random)));
      }
      for (int i = 0; i < composition.escorts && !table.escorts().isEmpty(); i++)
        group.add(spec(floor, EnvesEchoTables.draw(table.escorts(), random), Role.ESCORT, List.of()));
    }
    ServerLevel level = floor.level();
    for (int i = 0; i < group.size(); i++) {
      if (group.get(i).entry() == null) continue;
      BlockPos at = points.get(i % points.size());
      EnvesEchoes.spawn(level, group.get(i), Vec3.atBottomCenterOf(at), first);
    }
    if (table.treasure() != null && random.nextDouble() < balance.grottolChance()) {
      BlockPos at = points.get(points.size() - 1);
      Mob grottol = EnvesEchoes.spawn(level, spec(floor, table.treasure(), Role.TREASURE, List.of()), Vec3.atBottomCenterOf(at), first);
      if (grottol != null)
        for (ServerPlayer player : Enves.membersInside(level.getServer(), floor.attempt()))
          if (player.distanceToSqr(grottol) < 48 * 48)
            player.displayClientMessage(Component.translatable("entrelumen.enves.grottol"), true);
    }
  }

  static EnvesEchoes.Spec spec(EnvesHooks.Floor floor, EnvesEchoTables.Entry entry, Role role, List<EnvesAffix> affixes) {
    return new EnvesEchoes.Spec(entry, role, floor.attempt(), floor.depth(), affixes);
  }

  /** The guard room: the stair's champion in the middle, escorts at the corners; floor V's antechamber has no champion. */
  static void guard(EnvesHooks.Floor floor, int cell, List<EnvesHooks.WorldMarker> spawns, ServerPlayer first) {
    var table = EnvesContentConfig.table(floor.tileset());
    var balance = EnvesContentConfig.balance();
    Random random = EnvesPuzzleRules.random(floor.attempt().seed, floor.depth(), cell, SALT_GUARD);
    ServerLevel level = floor.level();
    List<BlockPos> corners = points(spawns, first, false);
    List<BlockPos> middle = points(spawns, first, true);
    BlockPos center = middle.isEmpty() ? floor.origin(cell).offset(EnvesGeometry.C, 1, EnvesGeometry.C) : middle.getFirst();
    int escorts = floor.depth() >= 3 ? 3 : 2;
    if (!table.champions().isEmpty() && floor.depth() < EnvesLayout.BOSS_DEPTH) {
      spawnChampion(floor, center, first, random);
    } else {
      escorts = 3;
    }
    for (int i = 0; i < escorts && !table.escorts().isEmpty() && !corners.isEmpty(); i++)
      EnvesEchoes.spawn(level, spec(floor, EnvesEchoTables.draw(table.escorts(), random), Role.ESCORT, List.of()),
          Vec3.atBottomCenterOf(corners.get(i % corners.size())), first);
  }

  static Mob spawnChampion(EnvesHooks.Floor floor, BlockPos at, ServerPlayer first, Random random) {
    var table = EnvesContentConfig.table(floor.tileset());
    var balance = EnvesContentConfig.balance();
    var entry = EnvesEchoTables.draw(table.champions(), random);
    if (entry == null) return null;
    int count = balance.affixCount(Role.CHAMPION, floor.depth(), random);
    Mob champion = EnvesEchoes.spawn(floor.level(), spec(floor, entry, Role.CHAMPION, EnvesAffix.pick(count, random)),
        Vec3.atBottomCenterOf(at), first);
    var runs = EnvesRuns.get(floor.level().getServer());
    var state = runs.run(floor.attempt().id).floor(floor.depth());
    if (champion == null) {
      // Neither the champion nor its fallback exists in this pack: the stair does not wait for it.
      state.championSpawned = state.championDead = true;
      runs.setDirty();
      return null;
    }
    state.champion = champion.getUUID();
    state.championSpawned = true;
    runs.setDirty();
    floor.level().playSound(null, at, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.6f, 1.4f);
    for (ServerPlayer player : Enves.membersInside(floor.level().getServer(), floor.attempt()))
      if (EnvesGeometry.depthAt(player.getBlockY()) == floor.depth())
        player.displayClientMessage(Component.translatable("entrelumen.enves.champion.appears", champion.getDisplayName()), true);
    return champion;
  }

  /** The champion fell: the stair may open (with every seal lit and the floor below ready). */
  static void championFell(ServerLevel level, Mob champion, EnvesEchoes.Data data) {
    var runs = EnvesRuns.get(level.getServer());
    var run = runs.find(data.attempt());
    if (run.isEmpty()) return;
    var state = run.get().floor(data.depth());
    if (state.championDead) return;
    state.championDead = true;
    runs.setDirty();
    EnvesData.get(level.getServer()).attempt(data.attempt()).ifPresent(attempt -> {
      int seals = Enves.layout(attempt, data.depth()).seals().size();
      boolean litAll = attempt.floor(data.depth()).lit.cardinality() >= seals;
      for (ServerPlayer player : Enves.membersInside(level.getServer(), attempt))
        player.displayClientMessage(Component.translatable(litAll ? "entrelumen.enves.champion.fell_open"
            : "entrelumen.enves.champion.fell", seals - attempt.floor(data.depth()).lit.cardinality()), false);
    });
  }

  /**
   * Whether the stair of a floor may open as far as its champion goes: the champion fell, or the
   * floor has none (floor V, or a table without champions).
   */
  static boolean championAllows(EnvesHooks.Floor floor) {
    if (floor.depth() >= EnvesLayout.BOSS_DEPTH || floor.layout().guard() < 0) return true;
    if (EnvesContentConfig.table(floor.tileset()).champions().isEmpty()) return true;
    var run = EnvesRuns.get(floor.level().getServer()).find(floor.attempt().id);
    if (run.isEmpty()) return false;
    var state = run.get().floor(floor.depth());
    return state.championDead;
  }

  /** Operators and the engine's own tests: the floor's champion no longer bars the stair. */
  static void waiveChampion(EnvesHooks.Floor floor) {
    var runs = EnvesRuns.get(floor.level().getServer());
    runs.run(floor.attempt().id).floor(floor.depth()).championDead = true;
    runs.setDirty();
  }

  /**
   * The guard room was entered before the content was there (an attempt older than it): its champion
   * comes when someone of the group stands in it. A champion is never spawned twice; one that left
   * the world without falling is waived by {@code /entrelumen admin enves seals}.
   */
  static void checkChampion(ServerLevel level, EnvesData.Attempt attempt, int depth, ServerPlayer present) {
    var runs = EnvesRuns.get(level.getServer());
    var state = runs.run(attempt.id).floor(depth);
    var floor = Enves.floor(level, attempt, depth);
    int guard = floor.layout().guard();
    if (guard < 0 || state.championDead || depth >= EnvesLayout.BOSS_DEPTH) return;
    if (EnvesContentConfig.table(floor.tileset()).champions().isEmpty()) return;
    if (state.championSpawned || !attempt.floor(depth).entered.get(guard)) return;
    BlockPos at = floor.markers(guard).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.ENCOUNTER
        && m.marker().argument().equals("champion")).map(EnvesHooks.WorldMarker::pos).findFirst()
        .orElse(floor.origin(guard).offset(EnvesGeometry.C, 1, EnvesGeometry.C));
    spawnChampion(floor, at, present, EnvesPuzzleRules.random(attempt.seed, depth, guard, SALT_GUARD));
  }
}
