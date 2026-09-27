package dev.entrelumen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesData.Attempt;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /entrelumen enves} (status of the team's attempt) and {@code giveup}; operators get
 * {@code /entrelumen admin enves ...} to open, walk, light, reveal and end attempts and to open or
 * close the Sealed Stair, for QA and repairs.
 */
public final class EnvesCommands {
  private EnvesCommands() {}

  static void register(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("enves")
            .executes(ctx -> status(ctx.getSource().getPlayerOrException()))
            .then(Commands.literal("giveup").executes(ctx -> Enves.giveUp(ctx.getSource().getPlayerOrException()) ? 1 : 0)))
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("enves")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("open").then(Commands.argument("tier", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      for (Tier tier : Tier.values()) builder.suggest(tier.name().toLowerCase(Locale.ROOT));
                      return builder.buildFuture();
                    })
                    .executes(EnvesCommands::open)))
                .then(Commands.literal("tp").then(Commands.argument("depth", IntegerArgumentType.integer(1, EnvesLayout.FLOORS))
                    .executes(ctx -> tp(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "depth")))))
                .then(Commands.literal("seals").executes(ctx -> lightAll(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reveal").executes(ctx -> reveal(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("boss").executes(ctx -> {
                  var attempt = Enves.attemptOf(ctx.getSource().getPlayerOrException());
                  attempt.ifPresent(Enves::bossDefeated);
                  return attempt.isPresent() ? 1 : 0;
                }))
                .then(Commands.literal("end").executes(ctx -> {
                  var attempt = Enves.attemptOf(ctx.getSource().getPlayerOrException());
                  attempt.ifPresent(a -> Enves.end(ctx.getSource().getServer(), a, EnvesHooks.EndReason.ADMIN));
                  return attempt.isPresent() ? 1 : 0;
                }))
                .then(Commands.literal("seal")
                    .then(Commands.literal("open").executes(ctx ->
                        EnvesEntrance.openSeal(ctx.getSource().getServer().overworld(), null) ? 1 : 0))
                    .then(Commands.literal("close").executes(ctx ->
                        EnvesEntrance.closeSeal(ctx.getSource().getServer().overworld()) ? 1 : 0))))));
  }

  private static int status(ServerPlayer player) {
    var attempt = Enves.attemptOf(player);
    if (attempt.isEmpty()) {
      player.sendSystemMessage(Component.translatable("entrelumen.enves.status.none"));
      return 0;
    }
    Attempt a = attempt.get();
    int depth = Math.clamp(a.depthOf.getOrDefault(player.getUUID(), a.frontline), 1, EnvesLayout.FLOORS);
    player.sendSystemMessage(Component.translatable("entrelumen.enves.status", Component.translatable(a.tier.nameKey),
        Component.translatable("entrelumen.enves.floor." + a.frontline), a.poolLeft, a.poolTotal,
        a.floor(depth).lit.cardinality(), Enves.layout(a, depth).seals().size()));
    return 1;
  }

  private static int list(CommandSourceStack source) {
    var data = EnvesData.get(source.getServer());
    source.sendSuccess(() -> Component.literal("Sealed Stair: open=" + data.entrance.open + " heart=" + data.entrance.heart
        + " gate=" + (data.entrance.gate.isEmpty() ? "-" : data.entrance.gate.getFirst())), false);
    for (Attempt a : data.attempts()) {
      StringBuilder floors = new StringBuilder();
      for (int d = 0; d < EnvesGeometry.DEPTHS; d++) floors.append(' ').append(d).append('=').append(a.floor(d).placement);
      source.sendSuccess(() -> Component.literal(a.id + " team=" + a.team + " slot=" + a.slot + " " + a.status + " "
          + a.tier + " pool=" + a.poolLeft + "/" + a.poolTotal + " frontline=" + a.frontline + floors), false);
    }
    return data.attempts().size();
  }

  private static int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer player = ctx.getSource().getPlayerOrException();
    Tier tier;
    try {
      tier = Tier.valueOf(StringArgumentType.getString(ctx, "tier").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return 0;
    }
    if (Enves.attemptOf(player).isPresent() || EnvesData.get(player.server).freeSlot() < 0) return 0;
    Attempt a = Enves.create(player, tier, player.getRandom().nextLong());
    ctx.getSource().sendSuccess(() -> Component.literal("Envés attempt " + a.id + " in slot " + a.slot), true);
    return 1;
  }

  private static int tp(ServerPlayer player, int depth) {
    var attempt = Enves.attemptOf(player);
    if (attempt.isEmpty() || attempt.get().floor(depth).placement != EnvesData.Placement.READY) return 0;
    BlockPos arrival = Enves.arrival(player.server, attempt.get(), depth);
    return Enves.move(player, Enves.level(player.server), Vec3.atBottomCenterOf(arrival), Enves.ARRIVAL_YAW) ? 1 : 0;
  }

  private static int lightAll(ServerPlayer player) {
    var attempt = Enves.attemptAt(player.server, player.blockPosition());
    int depth = EnvesGeometry.depthAt(player.blockPosition().getY());
    if (attempt.isEmpty() || depth < 1 || depth >= EnvesLayout.BOSS_DEPTH) return 0;
    int lit = 0;
    for (int seal : Enves.layout(attempt.get(), depth).seals()) {
      var pos = Enves.marker(player.server, attempt.get(), depth, seal, EnvesMarkers.Kind.SEAL);
      if (pos.isPresent() && Enves.lightSeal(player, pos.get())) lit++;
    }
    return lit;
  }

  private static int reveal(ServerPlayer player) {
    var attempt = Enves.attemptAt(player.server, player.blockPosition());
    int depth = EnvesGeometry.depthAt(player.blockPosition().getY());
    if (attempt.isEmpty() || depth < 1) return 0;
    for (int cell : Enves.layout(attempt.get(), depth).cells()) attempt.get().floor(depth).explored.set(cell);
    EnvesData.get(player.server).setDirty();
    return 1;
  }
}
