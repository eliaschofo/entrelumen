package dev.entrelumen;

import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.Status;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * Falls inside the Envés (Elias, 26/9): the player keeps everything, respawns at the start of the
 * floor where they fell, and the group's pool loses one fall; the fall that empties it ends the
 * attempt and sends everyone back. Keeping the inventory is exact vanilla {@code keepInventory}
 * for that one death, so Curios, backpacks, graves and experience behave as with the gamerule:
 * {@code EnvesKeepInventoryMixin} answers {@code true} for {@code keepInventory} while the death and
 * the respawn of such a player are processed, and at no other time. The mark survives a logout on
 * the death screen (it is in the player's persistent data).
 */
public final class EnvesDeaths {
  /** Persistent-data key on the dead player: the attempt and floor of the fall. */
  static final String MARK = "entrelumen_enves_fall";
  private static int keepInventory;

  private EnvesDeaths() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnvesDeaths::onDeath);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EnvesDeaths::onDrops);
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnvesDeaths::onRespawnPosition);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnvesDeaths::onRespawned);
  }

  /** Read by the mixin: whether {@code keepInventory} must answer true right now. */
  public static boolean keepInventory() {
    return keepInventory > 0;
  }

  /** Safety net: a death or respawn that threw never leaves the rule on past its tick. */
  static void tick() {
    keepInventory = 0;
  }

  static void onDeath(LivingDeathEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !Enves.inEnves(player)) return;
    Optional<Attempt> found = Enves.attemptAt(player.server, player.blockPosition());
    if (found.isEmpty() || found.get().status != Status.OPEN || !found.get().team.equals(Enves.teamOf(player))) return;
    Attempt attempt = found.get();
    int depth = EnvesGeometry.depthAt(player.blockPosition().getY());
    if (depth < 1) depth = attempt.depthOf.getOrDefault(player.getUUID(), 1);
    CompoundTag mark = new CompoundTag();
    mark.putUUID("attempt", attempt.id);
    mark.putInt("depth", depth);
    player.getPersistentData().put(MARK, mark);
    keepInventory++;
    attempt.depthOf.put(player.getUUID(), depth);
    attempt.poolLeft = EnvesRules.afterFall(attempt.poolLeft);
    EnvesData.get(player.server).setDirty();
    EnvesHooks.lifecycle().playerFell(attempt, player, attempt.poolLeft);
    for (ServerPlayer member : Enves.onlineMembers(player.server, attempt.team))
      member.sendSystemMessage(Component.translatable("entrelumen.enves.fell", player.getDisplayName(),
          attempt.poolLeft, attempt.poolTotal));
    if (EnvesRules.exhausted(attempt.poolLeft)) Enves.end(player.server, attempt, EnvesHooks.EndReason.FALLS_SPENT);
  }

  static void onDrops(LivingDropsEvent event) {
    if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(MARK))
      keepInventory = 0;
  }

  /**
   * Where a fallen player comes back: their floor's start while the attempt is open, else the
   * antechamber. The keep-inventory window opens here and closes when the respawn is done.
   */
  static void onRespawnPosition(PlayerRespawnPositionEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || event.isFromEndFight()) return;
    CompoundTag mark = player.getPersistentData().getCompound(MARK);
    if (!mark.hasUUID("attempt")) return;
    keepInventory++;
    UUID id = mark.getUUID("attempt");
    int depth = mark.getInt("depth");
    var server = player.server;
    Optional<Attempt> attempt = EnvesData.get(server).attempt(id);
    ServerLevel enves = Enves.level(server);
    if (attempt.isPresent() && attempt.get().status == Status.OPEN && enves != null) {
      BlockPos arrival = Enves.arrival(server, attempt.get(), depth);
      event.setDimensionTransition(new DimensionTransition(enves, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, Enves.ARRIVAL_YAW, 0f,
          DimensionTransition.DO_NOTHING));
      event.setCopyOriginalSpawnPosition(true);
      return;
    }
    var entrance = EnvesData.get(server).entrance;
    if (entrance.antechamber != null) {
      ServerLevel overworld = server.overworld();
      event.setDimensionTransition(new DimensionTransition(overworld,
          SolsticioTravel.safeSpot(overworld, entrance.antechamber), Vec3.ZERO, 180f, 0f, DimensionTransition.DO_NOTHING));
      event.setCopyOriginalSpawnPosition(true);
    }
  }

  static void onRespawned(PlayerEvent.PlayerRespawnEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    keepInventory = 0;
    player.getPersistentData().remove(MARK);
    if (!Enves.inEnves(player)) Enves.hudOutside(player);
    player.fallDistance = 0;
  }
}
