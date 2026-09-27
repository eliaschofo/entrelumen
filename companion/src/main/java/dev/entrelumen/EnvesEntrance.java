package dev.entrelumen;

import com.mojang.logging.LogUtils;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesData.Entrance;
import dev.entrelumen.EnvesData.Status;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * The Sealed Stair under the start ruin (art/structures/ruin_start.py, {@code sealed_stair()}): the
 * sun mosaic's heart stays shut until a player whose team reached World Tier Frontier (act III)
 * steps onto it or touches it; then the eight outer cells of the heart give way for good, and the
 * centre and the pedestal stay (the pedestal is the only compass source for later arrivals). Until
 * then the heart only answers with a hint that does not spoil. Below, the gate takes the offering.
 *
 * <p>The start ruin template carries {@code entrelumen:enves_gate} (the bottom middle of the 3×4
 * gate in the antechamber's far wall) and {@code entrelumen:enves_antechamber} (where players come
 * back); the heart is the centre of the template's floor layer.
 */
public final class EnvesEntrance {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final String GATE_MARKER = "entrelumen:enves_gate", ANTECHAMBER_MARKER = "entrelumen:enves_antechamber";
  /** How close to the heart a Frontier player opens it, and to the gate a player must stand to use it. */
  static final int HEART_RADIUS = 6, GATE_REACH = 8;

  /** What the gate screen shows; its ordinal travels in {@link EnvesNetwork.Gate}. */
  public enum GateState {
    /** No attempt: pick a difficulty and pay. */
    READY,
    /** The team's attempt is forming: wait. */
    FORMING,
    /** The team's attempt is open: enter, or give it up. */
    OPEN
  }

  private EnvesEntrance() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnvesEntrance::onRightClickBlock);
  }

  /**
   * Called by {@link HeliodorRuins#place} once the start ruin stands: records the stair and sets the
   * gate. Only the world's own start ruin owns the Sealed Stair; a placement into another registry
   * (GameTests place throwaway ones far away) leaves the world's record alone.
   */
  static void onStartRuinPlaced(ServerLevel level, StructureTemplate template, BlockPos origin,
      StructurePlaceSettings settings, int ground) {
    if (!worldsStartRuin(level, origin)) return;
    BlockPos gate = null, antechamber = null;
    for (var info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
      if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
      String metadata = info.nbt().getString("metadata");
      if (GATE_MARKER.equals(metadata)) gate = info.pos().immutable();
      if (ANTECHAMBER_MARKER.equals(metadata)) antechamber = info.pos().immutable();
    }
    if (gate == null || ground <= 0) return; // a start ruin without the Sealed Stair
    Vec3i size = template.getSize();
    record(level, origin.offset(size.getX() / 2, ground, size.getZ() / 2), gate, antechamber,
        new int[] {origin.getX(), origin.getY(), origin.getZ(), origin.getX() + size.getX() - 1, origin.getY() + ground - 1,
            origin.getZ() + size.getZ() - 1});
  }

  /** Whether this world's ruin registry holds the start ruin placed at {@code origin} in {@code level}. */
  static boolean worldsStartRuin(ServerLevel level, BlockPos origin) {
    return RuinData.get(level.getServer()).find(HeliodorRuins.START)
        .filter(ruin -> ruin.dimension().equals(level.dimension()) && ruin.origin().equals(origin))
        .isPresent();
  }

  /** Records a Sealed Stair (placement, and GameTests building one by hand) and sets the gate blocks. */
  static void record(ServerLevel level, BlockPos heart, BlockPos gate, BlockPos antechamber, int[] box) {
    EnvesData data = EnvesData.get(level.getServer());
    Entrance entrance = new Entrance();
    entrance.heart = heart.immutable();
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++)
        if (dx != 0 || dz != 0) {
          BlockPos cell = heart.offset(dx, 0, dz);
          entrance.sealCells.add(cell);
          entrance.sealStates.add(level.getBlockState(cell));
        }
    for (int dy = 0; dy < 4; dy++)
      for (int dx = -1; dx <= 1; dx++) entrance.gate.add(gate.offset(dx, dy, 0));
    entrance.antechamber = antechamber == null ? gate.offset(0, 0, 4) : antechamber.immutable();
    entrance.box = box.clone();
    data.entrance = entrance;
    data.setDirty();
    for (BlockPos pos : entrance.gate) level.setBlock(pos, Enves.GATE.get().defaultBlockState(), Block.UPDATE_ALL);
    LOGGER.info("The Sealed Stair: heart {}, gate {}, antechamber {}", heart, gate, entrance.antechamber);
  }

  // ---- The seal -----------------------------------------------------------------------------

  /** Once a second: a Frontier player near the heart opens it. */
  static void tick(MinecraftServer server) {
    if (server.getTickCount() % 20 != 11) return;
    Entrance entrance = EnvesData.get(server).entrance;
    if (entrance.open || entrance.heart == null) return;
    for (ServerPlayer player : server.overworld().players()) {
      if (player.isSpectator()) continue;
      BlockPos pos = player.blockPosition();
      if (Math.abs(pos.getX() - entrance.heart.getX()) > HEART_RADIUS || Math.abs(pos.getZ() - entrance.heart.getZ()) > HEART_RADIUS
          || Math.abs(pos.getY() - entrance.heart.getY()) > 4) continue;
      if (Enves.frontier(player)) {
        openSeal(server.overworld(), player);
        return;
      }
    }
  }

  /** The heart gives way: its eight outer cells become air, for good. */
  public static boolean openSeal(ServerLevel level, ServerPlayer by) {
    EnvesData data = EnvesData.get(level.getServer());
    Entrance entrance = data.entrance;
    if (entrance.open || entrance.heart == null) return false;
    entrance.open = true;
    data.setDirty();
    for (BlockPos pos : entrance.sealCells) {
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
    }
    level.playSound(null, entrance.heart, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 0.6f);
    level.playSound(null, entrance.heart, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f);
    for (ServerPlayer player : level.players())
      if (player.blockPosition().closerThan(entrance.heart, 24))
        player.sendSystemMessage(Component.translatable("entrelumen.enves.seal_opened"));
    LOGGER.info("The Sealed Stair opened{}", by == null ? "" : " for " + by.getGameProfile().getName());
    return true;
  }

  /** Operators only: closes the heart again (tests and repairs). */
  public static boolean closeSeal(ServerLevel level) {
    EnvesData data = EnvesData.get(level.getServer());
    Entrance entrance = data.entrance;
    if (entrance.heart == null) return false;
    entrance.open = false;
    data.setDirty();
    for (int i = 0; i < entrance.sealCells.size(); i++)
      level.setBlock(entrance.sealCells.get(i), i < entrance.sealStates.size() && !entrance.sealStates.get(i).isAir()
          ? entrance.sealStates.get(i) : Blocks.CHISELED_TUFF.defaultBlockState(), Block.UPDATE_ALL);
    return true;
  }

  /** Touching the sealed heart: it opens for a Frontier team, otherwise it only answers with the hint. */
  static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(Level.OVERWORLD)) return;
    Entrance entrance = EnvesData.get(player.server).entrance;
    if (entrance.open || !entrance.sealCells.contains(event.getPos())) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
    if (Enves.frontier(player)) openSeal(player.serverLevel(), player);
    else player.displayClientMessage(Component.translatable("entrelumen.enves.seal_hint"), true);
  }

  // ---- The gate -----------------------------------------------------------------------------

  static boolean nearGate(ServerPlayer player) {
    Entrance entrance = EnvesData.get(player.server).entrance;
    if (entrance.gate.isEmpty() || !player.level().dimension().equals(Level.OVERWORLD)) return false;
    for (BlockPos pos : entrance.gate) if (pos.closerToCenterThan(player.position(), GATE_REACH)) return true;
    return false;
  }

  /** Clicking the gate: the hint before Frontier, the gate screen after. */
  static void useGate(ServerPlayer player, BlockPos pos) {
    if (Enves.level(player.server) == null) {
      player.displayClientMessage(Component.translatable("entrelumen.enves.no_dimension"), true);
      return;
    }
    if (!Enves.frontier(player)) {
      player.displayClientMessage(Component.translatable("entrelumen.enves.gate_hint"), true);
      return;
    }
    EnvesNetwork.send(player, gateView(player));
  }

  static EnvesNetwork.Gate gateView(ServerPlayer player) {
    var settings = EnvesConfig.settings();
    List<Integer> tiers = new ArrayList<>();
    for (Tier tier : EnvesRules.choices(Enves.currentTier(player))) tiers.add(tier.ordinal());
    var attempt = Enves.attemptOf(player);
    GateState state = attempt.isEmpty() ? GateState.READY
        : attempt.get().status == Status.OPEN ? GateState.OPEN : GateState.FORMING;
    return new EnvesNetwork.Gate(state.ordinal(), settings.offeringItem(), settings.offeringCount(),
        Enves.countOffering(player) >= settings.offeringCount(), tiers,
        attempt.map(a -> a.tier.ordinal()).orElse(-1), attempt.map(a -> a.frontline).orElse(0),
        attempt.map(a -> a.poolLeft).orElse(0), attempt.map(a -> a.poolTotal).orElse(0));
  }

  /** The gate screen's choice, judged again on the server. */
  static void act(ServerPlayer player, EnvesNetwork.GateAction action) {
    if (!nearGate(player) && !EnvesGuard.bypass(player)) return;
    switch (action.action()) {
      case OPEN -> {
        if (action.tier() < 0 || action.tier() >= Tier.values().length) return;
        var refusal = Enves.open(player, Tier.values()[action.tier()]);
        player.displayClientMessage(Component.translatable(refusal == null ? "entrelumen.enves.forming"
            : "entrelumen.enves.refused." + refusal.name().toLowerCase(java.util.Locale.ROOT)), true);
      }
      case ENTER -> {
        if (!Enves.enter(player)) player.displayClientMessage(Component.translatable("entrelumen.enves.cannot_enter"), true);
      }
      case GIVE_UP -> {
        if (Enves.giveUp(player)) EnvesNetwork.send(player, gateView(player));
      }
    }
  }
}
