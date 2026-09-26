package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ark v2 against the pack's real mods: the Arcane module's mana attributes (Ars Nouveau, Iron's Spells,
 * Psi), the Waystones traveller hooks, the Ark's Rechiseled blocks, Curios' equipped stacks for wireless
 * charging and the refusal of Moonlight's {@code /moonlight back}.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ArkFullpackGameTests {
  private ArkFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("ars_nouveau", "irons_spellbooks", "psi", "waystones", "rechiseled", "curios", "moonlight"))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Session(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
    }

    public void close() {
      try {
        connection.disconnect(net.minecraft.network.chat.Component.literal("Ark QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void arcaneRaisesTheRealManaAttributesOfArsIronsAndPsi(GameTestHelper helper) {
    try (var session = new Session(helper, "ArkManaQA")) {
      var player = session.player;
      for (String id : ArkEffects.DEFAULT_MANA_ATTRIBUTES) {
        var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).orElse(null);
        helper.assertTrue(attribute != null && player.getAttribute(attribute) != null,
            "The pinned magic mods do not give players the attribute " + id);
      }
      var arcane = new ArkRules.Status(true, true, true, Set.of("arcane_module"), 0);
      ArkEffects.apply(player, arcane, false);
      for (String id : ArkEffects.DEFAULT_MANA_ATTRIBUTES) {
        var instance = player.getAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).orElseThrow());
        helper.assertTrue(instance.hasModifier(ArkEffects.MANA), "No overflowing mana on " + id);
      }
      var ironsMax = player.getAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(
          ResourceLocation.parse("irons_spellbooks:max_mana")).orElseThrow());
      helper.assertTrue(Math.abs(ironsMax.getValue() - ironsMax.getBaseValue() * 1.5) < 1e-6,
          "Iron's Spells' maximum mana did not rise by half: " + ironsMax.getValue());
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
      helper.assertTrue(!ironsMax.hasModifier(ArkEffects.MANA), "The mana bonus outlived the module");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void waystonesChargesATravellerNoExperience(GameTestHelper helper) throws Exception {
    Class<?> registry = Class.forName("net.blay09.mods.waystones.requirement.RequirementRegistry");
    Object condition = registry.getMethod("getConditionResolver", ResourceLocation.class)
        .invoke(null, WaystonesBridge.TRAVELLER);
    helper.assertTrue(condition != null, "Waystones does not know the condition entrelumen:ark_traveller");
    var parsed = (java.util.Collection<?>) Class.forName("net.blay09.mods.waystones.requirement.RequirementModifierParser")
        .getMethod("parse", String.class).invoke(null, "[entrelumen:ark_traveller] multiply_xp_cost(0)");
    helper.assertTrue(!parsed.isEmpty(), "Waystones rejected the pack's traveller line");
    Class<?> points = Class.forName("net.blay09.mods.waystones.requirement.ExperiencePointsRequirement");
    Class<?> levels = Class.forName("net.blay09.mods.waystones.requirement.ExperienceLevelRequirement");
    Class<?> combined = Class.forName("net.blay09.mods.waystones.requirement.CombinedRequirement");
    Object xp = points.getConstructor(int.class).newInstance(27);
    Object lv = levels.getConstructor(int.class).newInstance(3);
    Object all = combined.getConstructor(java.util.Collection.class).newInstance(new java.util.ArrayList<>(List.of(xp, lv)));
    WaystonesBridge.waive(all);
    helper.assertTrue((int) points.getMethod("getPoints").invoke(xp) == 0 && (int) levels.getMethod("getLevels").invoke(lv) == 0,
        "The traveller's warp still costs experience");
    try (var session = new Session(helper, "ArkWarpQA")) {
      var player = session.player;
      helper.assertTrue(!ArkEffects.traveller(player), "A player is a traveller without the module");
      ArkEffects.apply(player, new ArkRules.Status(true, true, true, Set.of("exploration_module"), 0), false);
      helper.assertTrue(ArkEffects.traveller(player), "The Exploration module did not mark the traveller");
      ArkEffects.apply(player, ArkRules.Status.NONE, false);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void theArksBlocksExistAndCuriosAndMoonlightBehave(GameTestHelper helper) {
    for (var part : ArkMultiblock.definition().parts())
      helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(part.block()), "The Ark needs an absent block: " + part.block());
    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("rechiseled:amethyst_block_polished_connecting")),
        "Rechiseled's connecting amethyst is absent");
    try (var session = new Session(helper, "ArkPackQA")) {
      var player = session.player;
      helper.assertTrue(CuriosCompat.available() && !CuriosCompat.equippedStacks(player).isEmpty(),
          "Curios' equipped stacks are not reachable for wireless charging");
      // An operator's /moonlight back parses to Moonlight's own nodes; the companion cancels it there.
      var source = player.createCommandSourceStack().withPermission(4);
      var parse = player.server.getCommands().getDispatcher().parse("moonlight back", source);
      var event = new net.neoforged.neoforge.event.CommandEvent(parse);
      ArkCommands.refuseBack(event);
      helper.assertTrue(parse.getReader().getRemainingLength() == 0 && event.isCanceled(),
          "/moonlight back is not refused");
    }
    helper.succeed();
  }
}
