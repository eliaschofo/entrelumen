package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only lifecycle coverage using connected players and FTB Teams' event-emitting API. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class TeamLifecycleGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String FRAME = "entrelumen:calibration_frame";
  private static final String REGULATOR = "entrelumen:power_regulator";

  private record CampaignState(int act, Set<String> completed, int arkPhase,
      Map<String, Integer> deposits, boolean archived) {
    CampaignState withArchived(boolean value) {
      return new CampaignState(act, completed, arkPhase, deposits, value);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void nativePartySnapshotsArchiveRecoveryAndReconnect(GameTestHelper helper)
      throws Exception {
    GameProfile founderProfile = profile("TeamFounder");
    GameProfile guestProfile = profile("TeamGuest");
    try (NativePlayerSession founderSession = connect(helper, founderProfile, true);
        NativePlayerSession guestSession = connect(helper, guestProfile, true)) {
      ServerPlayer founder = founderSession.player;
      ServerPlayer guest = guestSession.player;
      CampaignData data = CampaignData.get(founder.server);
      var manager = FTBTeamsAPI.api().getManager();
      helper.assertTrue(manager.getTeamForPlayer(founder).orElseThrow().isPlayerTeam()
          && manager.getTeamForPlayer(guest).orElseThrow().isPlayerTeam(),
          "Native players did not begin in personal FTB teams");

      // Earn two different personal histories and real rewards before changing teams.
      give(helper, founder, Items.BOOK, 1);
      give(helper, founder, Items.COPPER_INGOT, 1);
      give(helper, guest, Items.BOOK, 1);
      give(helper, guest, Items.COPPER_INGOT, 5);
      give(helper, guest, Items.GLASS, 4);
      helper.assertTrue(command(founder, "entrelumen deliver atlas_awakened") == 1
          && command(guest, "entrelumen deliver atlas_awakened") == 1
          && command(guest, "entrelumen deliver lens_assembled") == 1,
          "Personal campaign fixture did not earn real milestones and rewards");
      Item atlas = item("entrelumen:atlas");
      Item lens = item("entrelumen:raw_lens");
      helper.assertTrue(founder.getInventory().countItem(atlas) == 1
          && founder.getInventory().countItem(lens) == 0
          && guest.getInventory().countItem(atlas) == 1
          && guest.getInventory().countItem(lens) == 1,
          "Personal deliveries produced unexpected reward counts");
      give(helper, founder, Items.DIAMOND, 2);
      give(helper, guest, Items.DIAMOND, 3);
      Campaigns.Campaign founderPersonal = Entrelumen.current(founder);
      Campaigns.Campaign guestPersonal = Entrelumen.current(guest);
      CampaignState founderAtCreation = state(founderPersonal);
      CampaignState guestBeforeJoin = state(guestPersonal);
      Map<String, Integer> founderItemsAtCreation = inventory(founder);
      Map<String, Integer> guestItemsAtCreation = inventory(guest);

      PartyTeam party = (PartyTeam) manager.createPartyTeam(founder,
          "Entrelumen QA " + founder.getUUID(), "", Color4I.WHITE);
      UUID partyId = party.getId();
      founderSession.partyId = partyId;
      guestSession.partyId = partyId;
      Campaigns.Campaign shared = data.campaigns.parties.get(partyId);
      helper.assertTrue(shared != null && shared != founderPersonal
          && state(shared).equals(founderAtCreation)
          && state(founderPersonal).equals(founderAtCreation)
          && party.getMembers().equals(Set.of(founder.getUUID()))
          && manager.getTeamForPlayer(founder).orElseThrow().getId().equals(partyId),
          "FTB creation did not copy the founder campaign exactly once");
      assertInventory(helper, founder, founderItemsAtCreation, "party creation founder");
      assertInventory(helper, guest, guestItemsAtCreation, "party creation guest");

      helper.assertTrue(party.invite(founder, List.of(guestProfile)) == 1
          && party.join(guest) == 1, "Native FTB invitation or join failed");
      helper.assertTrue(party.getMembers().equals(Set.of(founder.getUUID(), guest.getUUID()))
          && Entrelumen.current(guest) == shared
          && !shared.completed.contains("lens_assembled")
          && state(guestPersonal).equals(guestBeforeJoin)
          && state(shared).equals(founderAtCreation),
          "Joining merged the guest history or changed the destination campaign");
      assertInventory(helper, founder, founderItemsAtCreation, "guest join founder");
      assertInventory(helper, guest, guestItemsAtCreation, "guest join guest");

      // A real delivery while joined changes only the party campaign.
      give(helper, founder, Items.BREAD, 3);
      give(helper, founder, Items.BOWL, 4);
      helper.assertTrue(command(founder, "entrelumen deliver travellers_table") == 1
          && shared.completed.contains("travellers_table")
          && !founderPersonal.completed.contains("travellers_table")
          && !guestPersonal.completed.contains("travellers_table")
          && founder.getInventory().countItem(Items.BREAD) == 0
          && founder.getInventory().countItem(Items.BOWL) == 0,
          "Party delivery did not consume exact materials into party-only progress");

      // Seed valid, bounded Ark ledgers to inspect their lifecycle; this is not an Ark deposit test.
      for (Campaigns.Campaign campaign : List.of(founderPersonal, guestPersonal, shared)) {
        campaign.act = 6;
        campaign.completed.addAll(Entrelumen.MODULES);
      }
      founderPersonal.arkDeposits.put(FRAME, 1);
      guestPersonal.arkDeposits.put(REGULATOR, 1);
      shared.arkDeposits.put(FRAME, 2);
      data.setDirty();
      helper.assertTrue(ArkCommissioning.eligible(founderPersonal)
          && ArkCommissioning.eligible(guestPersonal)
          && ArkCommissioning.eligible(shared), "Ark ledger fixtures are not valid act-six campaigns");
      CampaignState founderSnapshot = state(founderPersonal);
      CampaignState guestSnapshot = state(guestPersonal);
      CampaignState partySnapshot = state(shared);
      Map<String, Integer> founderItems = inventory(founder);
      Map<String, Integer> guestItems = inventory(guest);

      helper.assertTrue(command(guest, "ftbteams party leave") == 1
          && manager.getTeamForPlayer(guest).orElseThrow().isPlayerTeam()
          && Entrelumen.current(guest) == guestPersonal
          && state(guestPersonal).equals(guestSnapshot)
          && state(shared).equals(partySnapshot)
          && party.getMembers().equals(Set.of(founder.getUUID())),
          "Guest leave did not restore its personal milestones and deposits");
      assertInventory(helper, founder, founderItems, "guest leave founder");
      assertInventory(helper, guest, guestItems, "guest leave guest");

      guestSession.close();
      try (NativePlayerSession reconnectedGuest = connect(helper, guestProfile, false)) {
        reconnectedGuest.partyId = partyId;
        ServerPlayer guestAgain = reconnectedGuest.player;
        helper.assertTrue(manager.getTeamForPlayer(guestAgain).orElseThrow().isPlayerTeam()
            && state(Entrelumen.current(guestAgain)).equals(guestSnapshot)
            && state(shared).equals(partySnapshot),
            "Same-UUID reconnect rejoined the party or lost the personal snapshot");
        assertInventory(helper, guestAgain, guestItems, "same-UUID reconnect guest");

        helper.assertTrue(command(founder, "ftbteams party leave") == 1
            && manager.getTeamByID(partyId).isEmpty()
            && manager.getTeamForPlayer(founder).orElseThrow().isPlayerTeam()
            && Entrelumen.current(founder) == founderPersonal
            && state(founderPersonal).equals(founderSnapshot)
            && state(shared).equals(partySnapshot.withArchived(true)),
            "Last-owner leave did not restore personal progress and archive the party");
        assertInventory(helper, founder, founderItems, "owner leave founder");
        assertInventory(helper, guestAgain, guestItems, "owner leave guest");

        String recover = "entrelumen admin recover " + partyId;
        var adminNode = founder.server.getCommands().getDispatcher().getRoot()
            .getChild("entrelumen").getChild("admin");
        CommandSourceStack ordinary = guestAgain.createCommandSourceStack();
        helper.assertTrue(!ordinary.hasPermission(2) && !adminNode.canUse(ordinary),
            "A normal player unexpectedly has admin recovery permission");
        boolean denied = false;
        try {
          denied = command(guestAgain, recover) == 0;
        } catch (CommandSyntaxException expected) {
          denied = true;
        }
        helper.assertTrue(denied && state(guestPersonal).equals(guestSnapshot)
            && state(shared).equals(partySnapshot.withArchived(true)),
            "Unprivileged recovery changed personal or archived progress");

        helper.assertTrue(adminNode.canUse(founder.createCommandSourceStack().withPermission(2))
            && command(founder, recover,
                founder.createCommandSourceStack().withPermission(2)) == 1,
            "Admin source could not recover the archived party");
        helper.assertTrue(state(Entrelumen.current(founder)).equals(partySnapshot)
            && state(shared).equals(partySnapshot.withArchived(true))
            && state(Entrelumen.current(guestAgain)).equals(guestSnapshot),
            "Admin recovery did not copy the archive to its own personal campaign");
        assertInventory(helper, founder, founderItems, "admin recovery founder");
        assertInventory(helper, guestAgain, guestItems, "admin recovery guest");
        helper.assertTrue(founder.getInventory().countItem(atlas) == 1
            && founder.getInventory().countItem(lens) == 0
            && guestAgain.getInventory().countItem(atlas) == 1
            && guestAgain.getInventory().countItem(lens) == 1,
            "Team lifecycle duplicated or removed a campaign reward");
        LOGGER.info("ENTRELUMEN_TEAM_LIFECYCLE creationCopy=true joinNoMerge=true "
                + "partyDelivery=true guestRestore=true reconnect=true archive=true "
                + "adminDenied=true adminRecovered=true rewards=atlas:2,lens:1 "
                + "deposits=founder:1,guest:1,party:2");
      }
    }
    helper.succeed();
  }

  private static CampaignState state(Campaigns.Campaign campaign) {
    return new CampaignState(campaign.act, Set.copyOf(campaign.completed), campaign.arkPhase,
        Map.copyOf(campaign.arkDeposits), campaign.archived);
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Required real reward is absent: " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static void give(GameTestHelper helper, ServerPlayer player, Item item, int count) {
    helper.assertTrue(player.getInventory().add(new ItemStack(item, count)),
        "Could not seed real campaign material " + BuiltInRegistries.ITEM.getKey(item));
  }

  private static Map<String, Integer> inventory(ServerPlayer player) {
    Map<String, Integer> result = new TreeMap<>();
    count(result, player.getInventory().items);
    count(result, player.getInventory().armor);
    count(result, player.getInventory().offhand);
    return Map.copyOf(result);
  }

  private static void count(Map<String, Integer> result, Collection<ItemStack> stacks) {
    for (ItemStack stack : stacks)
      if (!stack.isEmpty())
        result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            stack.getCount(), Integer::sum);
  }

  private static void assertInventory(GameTestHelper helper, ServerPlayer player,
      Map<String, Integer> expected, String stage) {
    helper.assertTrue(inventory(player).equals(expected),
        stage + " changed inventory totals: expected=" + expected + " actual=" + inventory(player));
  }

  private static int command(ServerPlayer player, String input) throws CommandSyntaxException {
    return command(player, input, player.createCommandSourceStack().withSuppressedOutput());
  }

  private static int command(ServerPlayer player, String input, CommandSourceStack source)
      throws CommandSyntaxException {
    return player.server.getCommands().getDispatcher().execute(input, source.withSuppressedOutput());
  }

  private static GameProfile profile(String prefix) {
    UUID id = UUID.randomUUID();
    return new GameProfile(id, prefix + id.toString().substring(0, 5));
  }

  private static NativePlayerSession connect(GameTestHelper helper, GameProfile profile,
      boolean fresh) {
    var cookie = CommonListenerCookie.createInitial(profile, false);
    ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new Connection(PacketFlow.SERVERBOUND);
    var session = new NativePlayerSession(player, connection, new EmbeddedChannel(connection));
    try {
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      if (fresh) player.getInventory().clearContent();
      return session;
    } catch (RuntimeException | Error failure) {
      try {
        session.close();
      } catch (Exception cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private static final class NativePlayerSession implements AutoCloseable {
    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private UUID partyId;
    private boolean closed;

    private NativePlayerSession(ServerPlayer player, Connection connection, EmbeddedChannel channel) {
      this.player = player;
      this.connection = connection;
      this.channel = channel;
    }

    @Override
    public void close() throws Exception {
      if (closed) return;
      closed = true;
      try {
        var manager = FTBTeamsAPI.api().getManager();
        var team = manager.getTeamForPlayer(player).orElse(null);
        if (partyId != null && team != null && partyId.equals(team.getId())) {
          if (team.getOwner().equals(player.getUUID())
              && !team.getMembers().equals(Set.of(player.getUUID())))
            throw new IllegalStateException("Refusing to disband a QA party with other members");
          player.server.getCommands().getDispatcher().execute("ftbteams party leave",
              player.createCommandSourceStack().withSuppressedOutput());
          if (manager.getTeamForPlayer(player).orElseThrow().getId().equals(partyId))
            throw new IllegalStateException("QA player remained in party " + partyId);
          if (team.getOwner().equals(player.getUUID()) && manager.getTeamByID(partyId).isPresent())
            throw new IllegalStateException("QA party survived final owner cleanup " + partyId);
        }
      } finally {
        try {
          connection.disconnect(Component.literal("Entrelumen team QA finished"));
          connection.handleDisconnection();
          if (player.server.getPlayerList().getPlayer(player.getUUID()) == player)
            throw new IllegalStateException("Team QA player remained connected: " + player.getUUID());
        } finally {
          channel.finishAndReleaseAll();
        }
      }
    }
  }
}
