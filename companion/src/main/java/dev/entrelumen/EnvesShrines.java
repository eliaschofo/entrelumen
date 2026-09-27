package dev.entrelumen;

import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesPuzzleRules.Blessing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;

/**
 * Shrines (docs/design/dungeon-enves.md, «Santuarios»): the shrine room of a floor holds an altar
 * with one of five blessings, drawn from the attempt's seed. The first touch blesses the whole group
 * until the end of the floor, and the altar goes dark: Fervor (+25% damage), Refugio (25% less damage
 * taken), Presteza (Speed II and Haste II), Claridad (the floor's whole map) and Fortuna (the floor's
 * loot rolls one rarity higher more often). A blessing works on its floor only.
 */
public final class EnvesShrines {
  private EnvesShrines() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOW, EnvesShrines::onIncomingDamage);
  }

  /** The hook: the altar replaces the engine's beacon placeholder. */
  static void place(EnvesHooks.Floor floor, EnvesHooks.WorldMarker altar) {
    var runs = EnvesRuns.get(floor.level().getServer());
    var state = runs.run(floor.attempt().id).floor(floor.depth());
    if (state.blessing.isEmpty()) state.blessing = EnvesPuzzleRules.blessing(floor.attempt().seed, floor.depth()).id();
    state.shrine = altar.pos().immutable();
    runs.setDirty();
    floor.level().setBlock(altar.pos(), EnvesContent.SHRINE.get().defaultBlockState()
        .setValue(EnvesContentBlocks.Shrine.SPENT, state.blessingTaken), Block.UPDATE_ALL);
  }

  /** A member touches the altar. */
  static boolean use(ServerPlayer player, BlockPos pos) {
    if (!Enves.inEnves(player)) return false;
    var found = Enves.attemptAt(player.server, pos);
    if (found.isEmpty() || found.get().status != EnvesData.Status.OPEN || !found.get().team.equals(Enves.teamOf(player)))
      return false;
    Attempt attempt = found.get();
    int depth = EnvesGeometry.depthAt(pos.getY());
    var runs = EnvesRuns.get(player.server);
    var state = runs.run(attempt.id).floor(depth);
    Blessing blessing = Blessing.byId(state.blessing);
    if (blessing == null) blessing = EnvesPuzzleRules.blessing(attempt.seed, depth);
    if (state.blessingTaken) {
      player.displayClientMessage(Component.translatable("entrelumen.enves.shrine.spent"), true);
      return false;
    }
    state.blessing = blessing.id();
    state.blessingTaken = true;
    runs.setDirty();
    ServerLevel level = player.serverLevel();
    level.setBlock(pos, EnvesContent.SHRINE.get().defaultBlockState().setValue(EnvesContentBlocks.Shrine.SPENT, true), Block.UPDATE_ALL);
    level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1f, 1.2f);
    level.sendParticles(dust(blessing), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.02);
    if (blessing == Blessing.CLARITY) {
      var layout = Enves.layout(attempt, depth);
      for (int cell : layout.cells()) attempt.floor(depth).explored.set(cell);
      EnvesData.get(player.server).setDirty();
    }
    for (ServerPlayer member : Enves.membersInside(player.server, attempt))
      member.sendSystemMessage(Component.translatable("entrelumen.enves.shrine.blessed", player.getDisplayName(),
          Component.translatable("entrelumen.enves.blessing." + blessing.id()),
          Component.translatable("entrelumen.enves.blessing." + blessing.id() + ".effect")));
    return true;
  }

  /** The blessing a player enjoys right now: the one taken on the floor they stand on. */
  public static Blessing active(ServerPlayer player) {
    if (!Enves.inEnves(player)) return null;
    var found = Enves.attemptAt(player.server, player.blockPosition());
    if (found.isEmpty() || !found.get().team.equals(Enves.teamOf(player))) return null;
    int depth = EnvesGeometry.depthAt(player.getBlockY());
    if (depth < 1) return null;
    var run = EnvesRuns.get(player.server).find(found.get().id);
    if (run.isEmpty()) return null;
    var state = run.get().floors.get(depth);
    return state != null && state.blessingTaken ? Blessing.byId(state.blessing) : null;
  }

  /** Whether the Fortune blessing is taken on a floor (the loot asks). */
  public static boolean fortune(MinecraftServer server, Attempt attempt, int depth) {
    var run = EnvesRuns.get(server).find(attempt.id);
    if (run.isEmpty()) return false;
    var state = run.get().floors.get(depth);
    return state != null && state.blessingTaken && Blessing.FORTUNE.id().equals(state.blessing);
  }

  /** Fervor and Refugio act on every blow, whatever its source. */
  static void onIncomingDamage(LivingIncomingDamageEvent event) {
    if (event.getSource().getEntity() instanceof ServerPlayer attacker && active(attacker) == Blessing.FERVOR)
      event.setAmount(event.getAmount() * (float) (1 + EnvesPuzzleRules.FERVOR_DAMAGE));
    if (event.getEntity() instanceof ServerPlayer victim && active(victim) == Blessing.REFUGE)
      event.setAmount(event.getAmount() * (float) (1 - EnvesPuzzleRules.REFUGE_REDUCTION));
  }

  /** Once a second: Presteza's effects on those who stand on their blessed floor; the altars' glow. */
  static void tick(MinecraftServer server) {
    if (server.getTickCount() % 20 != 13) return;
    ServerLevel level = Enves.level(server);
    if (level == null) return;
    for (ServerPlayer player : level.players()) {
      Blessing blessing = active(player);
      if (blessing == Blessing.HASTE) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60, 1, true, true));
      }
    }
    for (var run : EnvesRuns.get(server).runs())
      for (var entry : run.floors.entrySet()) {
        var floor = entry.getValue();
        if (floor.shrine == null || floor.blessingTaken || !level.isLoaded(floor.shrine)) continue;
        if (level.getNearestPlayer(floor.shrine.getX() + 0.5, floor.shrine.getY(), floor.shrine.getZ() + 0.5, 20, false) == null) continue;
        Blessing blessing = Blessing.byId(floor.blessing);
        if (blessing != null)
          level.sendParticles(dust(blessing), floor.shrine.getX() + 0.5, floor.shrine.getY() + 1.1, floor.shrine.getZ() + 0.5, 6,
              0.35, 0.4, 0.35, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, floor.shrine.getX() + 0.5, floor.shrine.getY() + 1.4, floor.shrine.getZ() + 0.5, 1,
            0.2, 0.3, 0.2, 0.01);
      }
  }

  static DustParticleOptions dust(Blessing blessing) {
    int rgb = switch (blessing) {
      case FERVOR -> 0xE0503A;
      case REFUGE -> 0x5A8CE0;
      case HASTE -> 0xF0F0C8;
      case CLARITY -> 0x7FE0E0;
      case FORTUNE -> 0xF6D77A;
    };
    return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.2f);
  }
}
