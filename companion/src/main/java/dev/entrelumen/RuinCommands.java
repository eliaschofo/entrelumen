package dev.entrelumen;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator commands for the Plan v2 ruins, under {@code /entrelumen admin ruins}: {@code list},
 * {@code place <id>} (around the world spawn or the arrival rules), {@code place <id> here} (centred
 * on the operator, no site search), {@code tp <id>} and {@code reset <id>} (every team's progress in
 * that ruin; the blocks stay).
 */
public final class RuinCommands {
  private RuinCommands() {}

  static void register(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("entrelumen")
        .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
            .then(Commands.literal("ruins")
                .then(Commands.literal("list").executes(RuinCommands::list))
                .then(Commands.literal("place").then(Commands.argument("id", ResourceLocationArgument.id())
                    .suggests((context, builder) -> {
                      RuinRegistry.all().keySet().forEach(builder::suggest);
                      return builder.buildFuture();
                    })
                    .executes(context -> place(context, false))
                    .then(Commands.literal("here").executes(context -> place(context, true)))))
                .then(Commands.literal("tp").then(Commands.argument("id", ResourceLocationArgument.id())
                    .executes(RuinCommands::teleport)))
                .then(Commands.literal("reset").then(Commands.argument("id", ResourceLocationArgument.id())
                    .executes(RuinCommands::reset))))));
  }

  private static int list(CommandContext<CommandSourceStack> context) {
    var server = context.getSource().getServer();
    var data = RuinData.get(server);
    for (var definition : RuinRegistry.all().values()) {
      var id = ResourceLocation.parse(definition.id());
      String state = !definition.available(RuinRegistry::modLoaded) ? "mod absent"
          : data.find(id).map(ruin -> "placed " + ruin.box()).orElse(RuinPlacement.busy(server, id) ? "placing"
              : data.reservation(id).map(r -> "reserved " + r.origin().toShortString()).orElse("not placed"));
      context.getSource().sendSuccess(() -> Component.literal(definition.id() + " (act " + definition.act() + ", "
          + definition.dimension() + "): " + state), false);
    }
    return RuinRegistry.all().size();
  }

  private static int place(CommandContext<CommandSourceStack> context, boolean here) {
    String id = ResourceLocationArgument.getId(context, "id").toString();
    var definition = RuinRegistry.get(id).orElse(null);
    var source = context.getSource();
    if (definition == null) {
      source.sendFailure(Component.literal("Unknown ruin " + id));
      return 0;
    }
    var level = source.getLevel();
    if (!definition.dimension().equals(level.dimension().location().toString())) {
      source.sendFailure(Component.literal(id + " belongs in " + definition.dimension()));
      return 0;
    }
    BlockPos at = BlockPos.containing(source.getPosition());
    var job = here
        ? RuinPlacement.startAt(level, definition, at)
        : RuinPlacement.start(level, definition,
            definition.placement().trigger() == RuinDefinitions.Trigger.ACT ? level.getSharedSpawnPos() : at);
    source.sendSuccess(() -> Component.literal(job.isPresent() ? "Placing " + id : id + " is already placed"), true);
    return 1;
  }

  private static int teleport(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var player = context.getSource().getPlayerOrException();
    var ruin = RuinData.get(player.server).find(ResourceLocationArgument.getId(context, "id"));
    if (ruin.isEmpty()) return 0;
    var level = player.server.getLevel(ruin.get().dimension());
    if (level == null) return 0;
    BlockPos arrival = ruin.get().arrival();
    player.teleportTo(level, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, player.getYRot(), 0f);
    return 1;
  }

  private static int reset(CommandContext<CommandSourceStack> context) {
    String id = ResourceLocationArgument.getId(context, "id").toString();
    int teams = RuinProgress.get(context.getSource().getServer()).forget(id);
    context.getSource().sendSuccess(() -> Component.literal("Reset " + id + " for " + teams + " team(s)"), true);
    return teams;
  }
}
