package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Two QA-only stages; the verifier requires a different JVM and persisted world/player files. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class TeamRestartGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String RECEIPT = "entrelumen_team_restart_qa_receipt.dat";
  private static final String FRAME = "entrelumen:calibration_frame";
  private static final String REGULATOR = "entrelumen:power_regulator";
  private static final Set<String> TEAM_A_COMPLETED = Set.of("atlas_awakened",
      "engineering_module", "arcane_module", "nature_module", "logistics_module",
      "habitation_module", "exploration_module");
  private static final Set<String> TEAM_B_COMPLETED = Set.of("atlas_awakened",
      "lens_assembled", "engineering_module", "arcane_module", "nature_module",
      "logistics_module", "habitation_module", "exploration_module");
  private static final Map<String, Integer> INVENTORY_A = Map.of(
      "entrelumen:atlas", 1, FRAME, 5, REGULATOR, 4, "minecraft:diamond", 3);
  private static final Map<String, Integer> INVENTORY_B = Map.of(
      "entrelumen:atlas", 1, "entrelumen:raw_lens", 1, FRAME, 6,
      REGULATOR, 4, "minecraft:diamond", 4);
  private static final Map<String, Integer> INVENTORY_GUEST = Map.of("minecraft:emerald", 2);
  private static final Map<String, Integer> TOPPED_UP_A = Map.of(
      "entrelumen:atlas", 1, FRAME, 3, REGULATOR, 2, "minecraft:diamond", 3);
  private static final Map<String, Integer> TOPPED_UP_B = Map.of(
      "entrelumen:atlas", 1, "entrelumen:raw_lens", 1, FRAME, 2,
      REGULATOR, 3, "minecraft:diamond", 4);

  private record State(int act, Set<String> completed, int phase,
      Map<String, Integer> deposits, boolean archived) {}

  record Receipt(UUID nonce, long preparedStart, long preparedPid,
      GameProfile ownerA, GameProfile ownerB, GameProfile guestA, UUID teamA, UUID teamB) {}

  private TeamRestartGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void prepareTeamRestartFixture(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    Path receiptPath = receiptPath(server);
    guardNoEarlierFixture(helper, receiptPath);
    assertFixedCosts(helper);

    GameProfile ownerAProfile = profile("RestartA");
    GameProfile ownerBProfile = profile("RestartB");
    GameProfile guestProfile = profile("RestartG");
    UUID teamAId;
    UUID teamBId;
    try (NativePlayerSession ownerASession = connect(helper, ownerAProfile, true);
        NativePlayerSession ownerBSession = connect(helper, ownerBProfile, true);
        NativePlayerSession guestSession = connect(helper, guestProfile, true)) {
      ServerPlayer ownerA = ownerASession.player;
      ServerPlayer ownerB = ownerBSession.player;
      ServerPlayer guest = guestSession.player;
      CampaignData data = CampaignData.get(server);
      var manager = FTBTeamsAPI.api().getManager();
      helper.assertTrue(manager.getTeamForPlayer(ownerA).orElseThrow().isPlayerTeam()
              && manager.getTeamForPlayer(ownerB).orElseThrow().isPlayerTeam()
              && manager.getTeamForPlayer(guest).orElseThrow().isPlayerTeam(),
          "Restart fixture players did not begin in personal FTB teams");

      give(helper, ownerA, Items.BOOK, 1);
      give(helper, ownerA, Items.COPPER_INGOT, 1);
      give(helper, ownerB, Items.BOOK, 1);
      give(helper, ownerB, Items.COPPER_INGOT, 5);
      give(helper, ownerB, Items.GLASS, 4);
      helper.assertTrue(command(ownerA, "entrelumen deliver atlas_awakened") == 1
              && command(ownerB, "entrelumen deliver atlas_awakened") == 1
              && command(ownerB, "entrelumen deliver lens_assembled") == 1,
          "Personal restart histories did not earn their real milestones and rewards");
      assertInventory(helper, ownerA, Map.of("entrelumen:atlas", 1), "owner A personal");
      assertInventory(helper, ownerB,
          Map.of("entrelumen:atlas", 1, "entrelumen:raw_lens", 1), "owner B personal");
      assertState(helper, data.campaigns.personal.get(ownerA.getUUID()), personalA(),
          "owner A personal before party");
      assertState(helper, data.campaigns.personal.get(ownerB.getUUID()), personalB(),
          "owner B personal before party");
      data.campaigns.personal(guest.getUUID());
      assertState(helper, data.campaigns.personal.get(guest.getUUID()), guestPersonal(),
          "guest personal before party");

      PartyTeam teamA = (PartyTeam) manager.createPartyTeam(ownerA,
          "Entrelumen restart A " + ownerA.getUUID(), "", Color4I.WHITE);
      PartyTeam teamB = (PartyTeam) manager.createPartyTeam(ownerB,
          "Entrelumen restart B " + ownerB.getUUID(), "", Color4I.WHITE);
      teamAId = teamA.getId();
      teamBId = teamB.getId();
      helper.assertTrue(!teamAId.equals(teamBId)
              && teamA.invite(ownerA, List.of(guestProfile)) == 1
              && teamA.join(guest) == 1,
          "FTB Teams could not create two distinct parties and join the guest");
      assertMembers(helper, teamA, ownerAProfile.getId(), guestProfile.getId());
      assertMembers(helper, teamB, ownerBProfile.getId());
      helper.assertTrue(CampaignActions.campaignId(ownerA).equals(teamAId)
              && CampaignActions.campaignId(ownerB).equals(teamBId)
              && CampaignActions.campaignId(guest).equals(teamAId),
          "Restart fixture membership does not route to the intended parties");

      Campaigns.Campaign sharedA = data.campaigns.parties.get(teamAId);
      Campaigns.Campaign sharedB = data.campaigns.parties.get(teamBId);
      helper.assertTrue(sharedA != null && sharedB != null,
          "FTB creation did not copy the founders' personal campaigns");
      sharedA.act = CampaignMilestones.ARK_ACT;
      sharedA.completed.addAll(Entrelumen.MODULES);
      sharedB.act = CampaignMilestones.ARK_ACT;
      sharedB.completed.addAll(Entrelumen.MODULES);
      data.setDirty();
      assertState(helper, sharedA, teamAPartialBeforeDeposit(), "team A before deposit");
      assertState(helper, sharedB, teamBPartialBeforeDeposit(), "team B before deposit");
      helper.assertTrue(ArkCommissioning.eligible(sharedA)
              && ArkCommissioning.eligible(sharedB),
          "Restart fixture teams are not eligible for the real Ark deposit route");

      BlockPos controller = ark(helper);
      nearController(ownerA, controller);
      nearController(ownerB, controller);
      ownerA.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item(FRAME), 2));
      ownerB.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item(REGULATOR)));
      helper.assertTrue(ArkActions.deposit(ownerA, teamAId, controller, 0)
              && ArkActions.deposit(ownerB, teamBId, controller, 0),
          "Real Ark controller did not accept distinct partial team deposits");
      assertState(helper, sharedA, teamAPartial(), "team A partial");
      assertState(helper, sharedB, teamBPartial(), "team B partial");
      helper.assertTrue(ownerA.getOffhandItem().isEmpty() && ownerB.getOffhandItem().isEmpty(),
          "Partial Ark deposits did not consume their exact offered items");

      give(helper, ownerA, item(FRAME), 5);
      give(helper, ownerA, item(REGULATOR), 4);
      give(helper, ownerA, Items.DIAMOND, 3);
      give(helper, ownerB, item(FRAME), 6);
      give(helper, ownerB, item(REGULATOR), 4);
      give(helper, ownerB, Items.DIAMOND, 4);
      give(helper, guest, Items.EMERALD, 2);
      assertInventory(helper, ownerA, INVENTORY_A, "owner A prepared");
      assertInventory(helper, ownerB, INVENTORY_B, "owner B prepared");
      assertInventory(helper, guest, INVENTORY_GUEST, "guest prepared");
      assertPersonalSnapshots(helper, data, ownerAProfile.getId(), ownerBProfile.getId(),
          guestProfile.getId());
    }

    var manager = FTBTeamsAPI.api().getManager();
    helper.assertTrue(server.getPlayerList().getPlayer(ownerAProfile.getId()) == null
            && server.getPlayerList().getPlayer(ownerBProfile.getId()) == null
            && server.getPlayerList().getPlayer(guestProfile.getId()) == null,
        "Restart fixture players did not complete normal logout");
    assertMembers(helper, (PartyTeam) manager.getTeamByID(teamAId).orElseThrow(),
        ownerAProfile.getId(), guestProfile.getId());
    assertMembers(helper, (PartyTeam) manager.getTeamByID(teamBId).orElseThrow(),
        ownerBProfile.getId());
    for (UUID id : List.of(ownerAProfile.getId(), ownerBProfile.getId(), guestProfile.getId()))
      helper.assertTrue(Files.isRegularFile(playerDataPath(server, id)),
          "Normal logout did not write playerdata/" + id + ".dat");
    server.overworld().getDataStorage().save();
    // NeoForge writes SavedData on its IO pool; read the file only after that write landed.
    net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
    assertDiskCampaigns(helper, server, ownerAProfile.getId(), ownerBProfile.getId(),
        guestProfile.getId(), teamAId, teamBId, false);

    Receipt receipt = new Receipt(UUID.randomUUID(), processStart(), ProcessHandle.current().pid(),
        ownerAProfile, ownerBProfile, guestProfile, teamAId, teamBId);
    writeReceipt(helper, receiptPath, receipt);
    LOGGER.info("ENTRELUMEN_TEAM_RESTART_PREPARE nonce={} proof={} ownerA={} ownerB={} "
            + "guestA={} teamA={} teamB={} receipt={}", receipt.nonce(), proof(receipt),
        ownerAProfile.getId(), ownerBProfile.getId(), guestProfile.getId(), teamAId, teamBId,
        receiptPath);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 400)
  public static void verifyTeamRestartAfterNewServerProcess(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    Path path = receiptPath(server);
    helper.assertTrue(Files.isRegularFile(path),
        "No restart receipt; run prepareTeamRestartFixture, stop the server, then start it again");
    Receipt receipt = readReceipt(helper, path);
    helper.assertTrue(processStart() != receipt.preparedStart(),
        "Restart verifier ran in the same JVM as prepare; stop and start the server first");
    for (UUID id : List.of(receipt.ownerA().getId(), receipt.ownerB().getId(),
        receipt.guestA().getId())) {
      Path playerPath = playerDataPath(server, id);
      helper.assertTrue(Files.isRegularFile(playerPath)
              && NbtIo.readCompressed(playerPath, NbtAccounter.unlimitedHeap())
                  .contains("Inventory"),
          "Restart verifier lacks the persisted player .dat for " + id);
      helper.assertTrue(server.getPlayerList().getPlayer(id) == null,
          "QA fixture player was already online before persisted reconnect: " + id);
    }
    assertDiskCampaigns(helper, server, receipt.ownerA().getId(), receipt.ownerB().getId(),
        receipt.guestA().getId(), receipt.teamA(), receipt.teamB(), false);
    var manager = FTBTeamsAPI.api().getManager();
    PartyTeam teamA = (PartyTeam) manager.getTeamByID(receipt.teamA()).orElseThrow();
    PartyTeam teamB = (PartyTeam) manager.getTeamByID(receipt.teamB()).orElseThrow();
    assertMembers(helper, teamA, receipt.ownerA().getId(), receipt.guestA().getId());
    assertMembers(helper, teamB, receipt.ownerB().getId());

    try (NativePlayerSession ownerASession = connect(helper, receipt.ownerA(), false);
        NativePlayerSession ownerBSession = connect(helper, receipt.ownerB(), false);
        NativePlayerSession guestSession = connect(helper, receipt.guestA(), false)) {
      ServerPlayer ownerA = ownerASession.player;
      ServerPlayer ownerB = ownerBSession.player;
      ServerPlayer guest = guestSession.player;
      CampaignData data = CampaignData.get(server);
      helper.assertTrue(CampaignActions.campaignId(ownerA).equals(receipt.teamA())
              && CampaignActions.campaignId(ownerB).equals(receipt.teamB())
              && CampaignActions.campaignId(guest).equals(receipt.teamA())
              && Entrelumen.current(ownerA) == data.campaigns.parties.get(receipt.teamA())
              && Entrelumen.current(ownerB) == data.campaigns.parties.get(receipt.teamB())
              && Entrelumen.current(guest) == data.campaigns.parties.get(receipt.teamA()),
          "Reconnected .dat players lost their persisted FTB party identities");
      assertPersonalSnapshots(helper, data, receipt.ownerA().getId(), receipt.ownerB().getId(),
          receipt.guestA().getId());
      assertState(helper, data.campaigns.parties.get(receipt.teamA()), teamAPartial(),
          "team A restored partial ledger");
      assertState(helper, data.campaigns.parties.get(receipt.teamB()), teamBPartial(),
          "team B restored partial ledger");
      assertInventory(helper, ownerA, INVENTORY_A, "owner A restored .dat");
      assertInventory(helper, ownerB, INVENTORY_B, "owner B restored .dat");
      assertInventory(helper, guest, INVENTORY_GUEST, "guest restored .dat");

      BlockPos controller = ark(helper);
      nearController(ownerA, controller);
      nearController(ownerB, controller);
      helper.assertTrue(!ArkActions.deposit(ownerA, receipt.teamB(), controller, 0),
          "Stale identity deposited into the other restored team");
      assertInventory(helper, ownerA, INVENTORY_A, "stale identity A");
      assertState(helper, data.campaigns.parties.get(receipt.teamB()), teamBPartial(),
          "team B after stale identity");

      helper.assertTrue(ArkActions.deposit(ownerA, receipt.teamA(), controller, 0),
          "Team A could not resume its persisted partial Ark deposit");
      assertState(helper, data.campaigns.parties.get(receipt.teamA()), teamACompleted(),
          "team A completed step zero");
      assertInventory(helper, ownerA, TOPPED_UP_A, "team A exact topup");
      helper.assertTrue(!ArkActions.deposit(ownerA, receipt.teamA(), controller, 0),
          "Team A accepted a replay of the completed step");
      assertInventory(helper, ownerA, TOPPED_UP_A, "team A replay");
      assertState(helper, data.campaigns.parties.get(receipt.teamB()), teamBPartial(),
          "team B independence after team A topup");
      assertInventory(helper, ownerB, INVENTORY_B, "owner B independence");

      helper.assertTrue(ArkActions.deposit(ownerB, receipt.teamB(), controller, 0),
          "Team B could not resume its distinct partial Ark deposit");
      assertState(helper, data.campaigns.parties.get(receipt.teamB()), teamBCompleted(),
          "team B completed step zero");
      assertInventory(helper, ownerB, TOPPED_UP_B, "team B exact topup");
      helper.assertTrue(!ArkActions.deposit(ownerB, receipt.teamB(), controller, 0),
          "Team B accepted a replay of the completed step");
      assertInventory(helper, ownerB, TOPPED_UP_B, "team B replay");
      assertInventory(helper, guest, INVENTORY_GUEST, "guest after both topups");
      assertPersonalSnapshots(helper, data, receipt.ownerA().getId(), receipt.ownerB().getId(),
          receipt.guestA().getId());
      helper.assertTrue(Entrelumen.current(guest) == data.campaigns.parties.get(receipt.teamA()),
          "Persisted guest membership changed during the other team's Ark topup");
    }

    server.overworld().getDataStorage().save();
    // NeoForge writes SavedData on its IO pool; read the file only after that write landed.
    net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
    assertDiskCampaigns(helper, server, receipt.ownerA().getId(), receipt.ownerB().getId(),
        receipt.guestA().getId(), receipt.teamA(), receipt.teamB(), true);
    LOGGER.info("ENTRELUMEN_TEAM_RESTART_VERIFY nonce={} proof={} preparedStart={} "
            + "verifiedStart={} teams=2 players=3 step0=both replayDuplicate=false",
        receipt.nonce(), proof(receipt), receipt.preparedStart(), processStart());
    helper.succeed();
  }

  private static void assertFixedCosts(GameTestHelper helper) {
    helper.assertTrue(Entrelumen.MODULES.equals(Set.of("engineering_module", "arcane_module",
            "nature_module", "logistics_module", "habitation_module", "exploration_module"))
            && ArkCommissioning.STEPS.getFirst().requirements().equals(
                Map.of(FRAME, 4, REGULATOR, 2)),
        "Production Ark modules or first-step costs differ from the independent QA fixture");
  }

  private static State personalA() {
    return new State(1, Set.of("atlas_awakened"), 0, Map.of(), false);
  }

  private static State personalB() {
    return new State(1, Set.of("atlas_awakened", "lens_assembled"), 0, Map.of(), false);
  }

  private static State guestPersonal() {
    return new State(1, Set.of(), 0, Map.of(), false);
  }

  private static State teamAPartialBeforeDeposit() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_A_COMPLETED, 0, Map.of(), false);
  }

  private static State teamBPartialBeforeDeposit() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_B_COMPLETED, 0, Map.of(), false);
  }

  private static State teamAPartial() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_A_COMPLETED, 0, Map.of(FRAME, 2), false);
  }

  private static State teamBPartial() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_B_COMPLETED, 0, Map.of(REGULATOR, 1), false);
  }

  private static State teamACompleted() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_A_COMPLETED, 1, Map.of(), false);
  }

  private static State teamBCompleted() {
    return new State(CampaignMilestones.ARK_ACT, TEAM_B_COMPLETED, 1, Map.of(), false);
  }

  private static void assertPersonalSnapshots(GameTestHelper helper, CampaignData data,
      UUID ownerA, UUID ownerB, UUID guest) {
    assertState(helper, data.campaigns.personal.get(ownerA), personalA(), "owner A personal");
    assertState(helper, data.campaigns.personal.get(ownerB), personalB(), "owner B personal");
    assertState(helper, data.campaigns.personal.get(guest), guestPersonal(), "guest personal");
  }

  private static State state(Campaigns.Campaign campaign) {
    return new State(campaign.act, Set.copyOf(campaign.completed), campaign.arkPhase,
        Map.copyOf(campaign.arkDeposits), campaign.archived);
  }

  private static void assertState(GameTestHelper helper, Campaigns.Campaign campaign,
      State expected, String stage) {
    helper.assertTrue(campaign != null && state(campaign).equals(expected),
        stage + " differs: expected=" + expected + " actual="
            + (campaign == null ? "missing" : state(campaign)));
  }

  static BlockPos ark(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(pos,
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:ark_controller"))
            .defaultBlockState());
    int index = 0;
    for (String module : Entrelumen.MODULES) {
      helper.getLevel().setBlockAndUpdate(pos.offset(index % 3 - 1, 0, index / 3 + 1),
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("entrelumen:" + module))
              .defaultBlockState());
      index++;
    }
    return pos;
  }

  static void nearController(ServerPlayer player, BlockPos pos) {
    player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
  }

  static void assertMembers(GameTestHelper helper, PartyTeam team, UUID... members) {
    helper.assertTrue(team.getMembers().equals(Set.of(members))
            && team.getOwner().equals(members[0]),
        "Persisted FTB team membership/owner differs for " + team.getId()
            + ": " + team.getMembers());
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key) || BuiltInRegistries.ITEM.get(key) == Items.AIR)
      throw new IllegalStateException("Restart QA item is absent: " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static void give(GameTestHelper helper, ServerPlayer player, Item item, int count) {
    helper.assertTrue(player.getInventory().add(new ItemStack(item, count)),
        "Could not seed restart QA item " + BuiltInRegistries.ITEM.getKey(item));
  }

  static Map<String, Integer> inventory(ServerPlayer player) {
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
        stage + " inventory differs: expected=" + expected + " actual=" + inventory(player));
  }

  private static int command(ServerPlayer player, String input) throws CommandSyntaxException {
    CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
    return player.server.getCommands().getDispatcher().execute(input, source);
  }

  private static GameProfile profile(String prefix) {
    UUID id = UUID.randomUUID();
    return new GameProfile(id, prefix + id.toString().substring(0, 5));
  }

  static NativePlayerSession connect(GameTestHelper helper, GameProfile profile,
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

  static final class NativePlayerSession implements AutoCloseable {
    final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private boolean closed;

    private NativePlayerSession(ServerPlayer player, Connection connection, EmbeddedChannel channel) {
      this.player = player;
      this.connection = connection;
      this.channel = channel;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      try {
        connection.disconnect(Component.literal("Entrelumen restart QA logout"));
        connection.handleDisconnection();
        if (player.server.getPlayerList().getPlayer(player.getUUID()) == player)
          throw new IllegalStateException("Restart QA player remained connected: "
              + player.getUUID());
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  static Path receiptPath(MinecraftServer server) {
    return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(RECEIPT);
  }

  static Path playerDataPath(MinecraftServer server, UUID id) {
    return server.getWorldPath(LevelResource.ROOT).resolve("playerdata")
        .resolve(id + ".dat");
  }

  private static void guardNoEarlierFixture(GameTestHelper helper, Path path) {
    helper.assertTrue(!Files.exists(path) && !Files.exists(path.resolveSibling(RECEIPT + ".pending")),
        "Existing restart QA receipt/pending file is ambiguous; root must explicitly resetQA"
            + " before another prepare: " + path);
  }

  static long processStart() {
    return ManagementFactory.getRuntimeMXBean().getStartTime();
  }

  private static String proof(Receipt receipt) {
    String payload = "team-restart-v1|" + receipt.nonce() + "|" + receipt.preparedStart()
        + "|" + receipt.preparedPid() + "|" + receipt.ownerA().getId() + "|"
        + receipt.ownerA().getName() + "|" + receipt.ownerB().getId() + "|"
        + receipt.ownerB().getName() + "|" + receipt.guestA().getId() + "|"
        + receipt.guestA().getName() + "|" + receipt.teamA() + "|" + receipt.teamB();
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("JVM has no SHA-256 for restart QA receipt", ex);
    }
  }

  private static void writeReceipt(GameTestHelper helper, Path path, Receipt receipt)
      throws IOException {
    guardNoEarlierFixture(helper, path);
    CompoundTag tag = new CompoundTag();
    tag.putInt("schema", 1);
    tag.putString("stage", "prepared");
    tag.putString("nonce", receipt.nonce().toString());
    tag.putLong("preparedStart", receipt.preparedStart());
    tag.putLong("preparedPid", receipt.preparedPid());
    tag.putString("ownerA", receipt.ownerA().getId().toString());
    tag.putString("ownerAName", receipt.ownerA().getName());
    tag.putString("ownerB", receipt.ownerB().getId().toString());
    tag.putString("ownerBName", receipt.ownerB().getName());
    tag.putString("guestA", receipt.guestA().getId().toString());
    tag.putString("guestAName", receipt.guestA().getName());
    tag.putString("teamA", receipt.teamA().toString());
    tag.putString("teamB", receipt.teamB().toString());
    tag.putString("proof", proof(receipt));
    Files.createDirectories(path.getParent());
    Path pending = path.resolveSibling(RECEIPT + ".pending");
    NbtIo.writeCompressed(tag, pending);
    Files.move(pending, path);
  }

  static Receipt readReceipt(GameTestHelper helper, Path path) throws IOException {
    CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    helper.assertTrue(tag.getInt("schema") == 1 && tag.getString("stage").equals("prepared")
            && !Files.exists(path.resolveSibling(RECEIPT + ".pending")),
        "Restart QA receipt schema/stage is invalid or a pending write remains");
    Receipt receipt = new Receipt(UUID.fromString(tag.getString("nonce")),
        tag.getLong("preparedStart"), tag.getLong("preparedPid"),
        new GameProfile(UUID.fromString(tag.getString("ownerA")), tag.getString("ownerAName")),
        new GameProfile(UUID.fromString(tag.getString("ownerB")), tag.getString("ownerBName")),
        new GameProfile(UUID.fromString(tag.getString("guestA")), tag.getString("guestAName")),
        UUID.fromString(tag.getString("teamA")), UUID.fromString(tag.getString("teamB")));
    helper.assertTrue(receipt.preparedStart() > 0 && receipt.preparedPid() > 0
            && !receipt.ownerA().getId().equals(receipt.ownerB().getId())
            && !receipt.ownerA().getId().equals(receipt.guestA().getId())
            && !receipt.ownerB().getId().equals(receipt.guestA().getId())
            && !receipt.teamA().equals(receipt.teamB())
            && proof(receipt).equals(tag.getString("proof")),
        "Restart QA receipt identities or fixture proof are invalid");
    return receipt;
  }

  private static void assertDiskCampaigns(GameTestHelper helper, MinecraftServer server,
      UUID ownerA, UUID ownerB, UUID guest, UUID teamA, UUID teamB, boolean completed)
      throws IOException {
    Path path = server.getWorldPath(LevelResource.ROOT)
        .resolve("data/entrelumen_campaigns.dat");
    helper.assertTrue(Files.isRegularFile(path), "Authoritative campaign SavedData is absent on disk");
    CompoundTag disk = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    CampaignData loaded = CampaignData.load(disk.getCompound("data"),
        server.overworld().registryAccess());
    assertPersonalSnapshots(helper, loaded, ownerA, ownerB, guest);
    assertState(helper, loaded.campaigns.parties.get(teamA),
        completed ? teamACompleted() : teamAPartial(), "disk team A");
    assertState(helper, loaded.campaigns.parties.get(teamB),
        completed ? teamBCompleted() : teamBPartial(), "disk team B");
  }
}
