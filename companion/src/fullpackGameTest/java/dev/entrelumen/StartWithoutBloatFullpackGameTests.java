package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * QA-JAR-only check of "Inicio sin bloat" on the full pack: a new player owns nothing, and every
 * first-join gift found in the catalog audit stays disabled by the pack (see
 * docs/design/heliodor-compass.md): config gifts read false on disk, the Herbs & Harvest
 * advancement is overridden, and Ars Nouveau's remote plush campaign finds its flag already set.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class StartWithoutBloatFullpackGameTests {
  /** mod ID, config file, setting that must read false. */
  private static final List<String[]> SETTINGS = List.of(
      new String[] {"ars_nouveau", "ars_nouveau-common.toml", "spawnBook"},
      new String[] {"integrateddynamics", "integrateddynamics-common.toml", "obtainOnSpawn"},
      new String[] {"herbsandharvest", "herbsandharvest-common.toml", "give_book_on_join"},
      new String[] {"actuallyadditions", "actuallyadditions-common.toml", "giveBookletOnFirstCraft"},
      new String[] {"aether", "aether-common.toml", "\"Gives player Aether Portal Frame item\""},
      new String[] {"modern_industrialization", "modern_industrialization-server.toml", "spawnWithGuideBook"},
      new String[] {"modern_industrialization", "modern_industrialization-server.toml", "respawnWithGuideBook"},
      new String[] {"silentgear", "silentgear-common.toml", "spawn_with_starter_blueprints"},
      new String[] {"silentgear", "silentgear-common.toml", "spawn_with_material_book"});

  private StartWithoutBloatFullpackGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void fullpackPlayerArrivesEmptyHanded(GameTestHelper helper) {
    var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
        new GameProfile(UUID.randomUUID(), "BareHands"), false);
    var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new net.minecraft.network.Connection(
        net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    // Login handlers run at once; tick-triggered advancement rewards within the first ticks.
    helper.runAfterDelay(40, () -> {
      var inventory = player.getInventory();
      List<String> gifts = new ArrayList<>();
      for (var list : List.of(inventory.items, inventory.armor, inventory.offhand))
        for (ItemStack stack : list)
          if (!stack.isEmpty()) gifts.add(stack.getCount() + "x " + stack.getItem());
      helper.assertTrue(gifts.isEmpty(), "A new player received " + gifts);
      helper.succeed();
    });
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void fullpackFirstJoinGiftsStayDisabled(GameTestHelper helper) throws IOException {
    List<String> problems = new ArrayList<>();
    for (String[] setting : SETTINGS) {
      if (!ModList.get().isLoaded(setting[0])) continue;
      var file = FMLPaths.CONFIGDIR.get().resolve(setting[1]);
      String text = Files.isRegularFile(file) ? Files.readString(file) : "";
      var pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(setting[2]) + "\\s*=\\s*false\\s*$");
      if (!pattern.matcher(text).find()) problems.add(setting[1] + " " + setting[2]);
    }
    if (ModList.get().isLoaded("ars_nouveau")) {
      var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          new GameProfile(UUID.randomUUID(), "PlushCheck"),
          net.minecraft.server.level.ClientInformation.createDefault());
      FirstJoinGifts.onLogin(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
      if (!player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
          .getBoolean(FirstJoinGifts.ARS_PLUSH_FLAG))
        problems.add("Ars Nouveau's Starbuncle plush flag is not set before its login handler");
    }
    if (ModList.get().isLoaded("herbsandharvest")) {
      var advancement = helper.getLevel().getServer().getAdvancements()
          .get(ResourceLocation.fromNamespaceAndPath("herbsandharvest", "grant_book_on_first_join"));
      if (advancement != null && !advancement.value().criteria().containsKey("never"))
        problems.add("herbsandharvest:grant_book_on_first_join is not overridden");
    }
    helper.assertTrue(problems.isEmpty(), "First-join gifts still enabled: " + problems);
    helper.succeed();
  }
}
