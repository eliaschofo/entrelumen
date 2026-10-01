package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the companion's FTB Quests integration: the party claim guard (team rewards
 * claimed in a party stay claimed after leaving it, online or kicked offline), the milestone reward
 * reset of a merged history, the carried-item task (armour, off hand and Curios count) and the
 * Apotheosis World Tier tutorial ended at login. Players are real connected players; quest data is the
 * pack's real quest book.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class QuestIntegrationFullpackGameTests {
  private QuestIntegrationFullpackGameTests() {}

  // --- F14: party claim guard ---------------------------------------------------------------

  /** Solo progress, create a party, claim there, leave: the personal team must show it claimed. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void partyClaimSurvivesLeavingTheParty(GameTestHelper helper) throws Exception {
    ServerQuestFile file = file(helper);
    Reward reward = teamRewards(helper, file, 1).getFirst();
    var manager = FTBTeamsAPI.api().getManager();
    try (Session founder = Session.connect(helper, "ClaimSolo")) {
      UUID id = founder.player.getUUID();
      TeamData personal = file.getOrCreateTeamData(manager.getPlayerTeamForPlayerID(id).orElseThrow());
      helper.assertFalse(personal.isRewardClaimed(id, reward), "Fresh personal team already claimed the reward");

      PartyTeam party = (PartyTeam) manager.createPartyTeam(founder.player,
          "Entrelumen claim QA " + id, "", Color4I.WHITE);
      founder.partyId = party.getId();
      helper.assertTrue(QuestClaimGuard.partyEntry(founder.player.server, id) > 0,
          "Creating a party did not record the entry time");
      TeamData partyData = file.getOrCreateTeamData(party);
      long claimedAt = System.currentTimeMillis();
      helper.assertTrue(partyData.markRewardAsClaimed(id, reward, claimedAt), "The party could not claim the reward");

      founder.leaveParty();
      helper.assertTrue(manager.getTeamForPlayer(founder.player).orElseThrow().isPlayerTeam(),
          "Leaving did not return the founder to the personal team");
      helper.assertTrue(personal.isRewardClaimed(id, reward),
          "The personal team offers a reward the party already claimed (double claim)");
      helper.assertTrue(personal.getRewardClaimTime(id, reward).orElseThrow().getTime() == claimedAt,
          "The copied claim did not keep the party's claim time");
      helper.assertTrue(QuestClaimGuard.partyEntry(founder.player.server, id) == 0,
          "Leaving did not clear the recorded entry time");
      // Idempotent: copying again marks nothing new.
      helper.assertTrue(QuestClaimGuard.copyClaims(file, partyData, personal, id, 0) == 0,
          "A second copy marked a claim again");
    }
    helper.succeed();
  }

  /** A joiner whose solo rewards the party claimed before they joined can still claim them after leaving. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void joinerKeepsRewardsThePartyClaimedBeforeJoining(GameTestHelper helper) throws Exception {
    ServerQuestFile file = file(helper);
    List<Reward> rewards = teamRewards(helper, file, 2);
    Reward before = rewards.get(0), during = rewards.get(1);
    var manager = FTBTeamsAPI.api().getManager();
    try (Session founder = Session.connect(helper, "ClaimOwner");
        Session guest = Session.connect(helper, "ClaimJoiner")) {
      UUID guestId = guest.player.getUUID();
      PartyTeam party = (PartyTeam) manager.createPartyTeam(founder.player,
          "Entrelumen join QA " + founder.player.getUUID(), "", Color4I.WHITE);
      founder.partyId = guest.partyId = party.getId();
      TeamData partyData = file.getOrCreateTeamData(party);
      helper.assertTrue(partyData.markRewardAsClaimed(founder.player.getUUID(), before,
          System.currentTimeMillis() - 60_000L), "The party could not claim before the join");

      helper.assertTrue(party.invite(founder.player, List.of(guest.player.getGameProfile())) == 1
          && party.join(guest.player) == 1, "Native FTB invitation or join failed");
      helper.assertTrue(partyData.markRewardAsClaimed(guestId, during, System.currentTimeMillis()),
          "The party could not claim during membership");

      guest.leaveParty();
      TeamData personal = file.getOrCreateTeamData(manager.getPlayerTeamForPlayerID(guestId).orElseThrow());
      helper.assertFalse(personal.isRewardClaimed(guestId, before),
          "A claim the party made before the guest joined was copied to the guest");
      helper.assertTrue(personal.isRewardClaimed(guestId, during),
          "A claim the party made while the guest was a member was not copied");
    }
    helper.succeed();
  }

  /** A member kicked while offline (no player entity) is guarded too. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void offlineKickKeepsPartyClaims(GameTestHelper helper) throws Exception {
    ServerQuestFile file = file(helper);
    Reward reward = teamRewards(helper, file, 1).getFirst();
    var manager = FTBTeamsAPI.api().getManager();
    try (Session founder = Session.connect(helper, "KickOwner")) {
      PartyTeam party = (PartyTeam) manager.createPartyTeam(founder.player,
          "Entrelumen kick QA " + founder.player.getUUID(), "", Color4I.WHITE);
      founder.partyId = party.getId();
      Session guest = Session.connect(helper, "KickedGuest");
      GameProfile guestProfile = guest.player.getGameProfile();
      UUID guestId = guestProfile.getId();
      try {
        helper.assertTrue(party.invite(founder.player, List.of(guestProfile)) == 1
            && party.join(guest.player) == 1, "Native FTB invitation or join failed");
        TeamData partyData = file.getOrCreateTeamData(party);
        helper.assertTrue(partyData.markRewardAsClaimed(guestId, reward, System.currentTimeMillis()),
            "The party could not claim the reward");
      } finally {
        guest.disconnectOnly();
      }
      helper.assertTrue(founder.player.server.getPlayerList().getPlayer(guestId) == null,
          "The guest is still online");
      helper.assertTrue(party.kick(founder.player.createCommandSourceStack().withSuppressedOutput(),
          List.of(guestProfile)) == 1, "The offline kick failed");
      helper.assertTrue(manager.getTeamForPlayerID(guestId).orElseThrow().isPlayerTeam(),
          "The kicked guest is not back in a personal team");
      TeamData personal = file.getOrCreateTeamData(manager.getPlayerTeamForPlayerID(guestId).orElseThrow());
      helper.assertTrue(personal.isRewardClaimed(guestId, reward),
          "An offline kick dropped the party's claim (double claim)");
      helper.assertTrue(QuestClaimGuard.partyEntry(founder.player.server, guestId) == 0,
          "The offline kick did not clear the recorded entry time");
    }
    helper.succeed();
  }

  // --- F39: merged milestone claims are reset -----------------------------------------------

  /** A joiner's merged claim of a milestone the party campaign lacks is reset, so the party can earn it. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void mergedMilestoneClaimIsReset(GameTestHelper helper) throws Exception {
    ServerQuestFile file = file(helper);
    var manager = FTBTeamsAPI.api().getManager();
    try (Session founder = Session.connect(helper, "MilestoneOwner");
        Session guest = Session.connect(helper, "MilestoneGuest")) {
      UUID guestId = guest.player.getUUID();
      PartyTeam party = (PartyTeam) manager.createPartyTeam(founder.player,
          "Entrelumen milestone QA " + founder.player.getUUID(), "", Color4I.WHITE);
      founder.partyId = guest.partyId = party.getId();
      Campaigns.Campaign shared = Entrelumen.current(founder.player);
      CampaignTask milestone = file.getAllTasks().stream()
          .filter(CampaignTask.class::isInstance).map(CampaignTask.class::cast)
          .filter(task -> !task.getQuest().getRewards().isEmpty())
          .filter(task -> !CampaignMilestones.isComplete(shared, task.milestone()))
          .findFirst().orElse(null);
      helper.assertTrue(milestone != null, "No rewarded campaign milestone is open for a fresh party");
      TeamData personal = file.getOrCreateTeamData(manager.getPlayerTeamForPlayerID(guestId).orElseThrow());
      for (Reward reward : milestone.getQuest().getRewards())
        personal.markRewardAsClaimed(guestId, reward, System.currentTimeMillis() - 60_000L);

      helper.assertTrue(party.invite(founder.player, List.of(guest.player.getGameProfile())) == 1
          && party.join(guest.player) == 1, "Native FTB invitation or join failed");
      TeamData partyData = file.getOrCreateTeamData(party);
      Reward first = milestone.getQuest().getRewards().iterator().next();
      helper.assertTrue(partyData.isRewardClaimed(guestId, first),
          "FTB no longer merges the joiner's claims; this check needs a new fixture");

      milestone.submitTask(partyData, guest.player, ItemStack.EMPTY);
      for (Reward reward : milestone.getQuest().getRewards())
        helper.assertFalse(partyData.isRewardClaimed(guestId, reward),
            "A merged claim of an unreached milestone blocks the party's reward: " + reward);
      helper.assertTrue(partyData.getProgress(milestone) == 0 && !partyData.isCompleted(milestone.getQuest()),
          "The unreached milestone kept merged progress");
    }
    helper.succeed();
  }

  // --- F25: carried-item task ---------------------------------------------------------------

  /** Armour, off hand and the main inventory all count; progress never goes down. */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void carriedItemTaskCountsWornItems(GameTestHelper helper) throws Exception {
    ServerQuestFile file = file(helper);
    try (Session session = Session.connect(helper, "CarriedItem")) {
      ServerPlayer player = session.player;
      TeamData data = file.getOrCreateTeamData(player);
      Quest quest = openQuest(helper, file, data);
      CarriedItemTask task = carried(file, quest, Items.IRON_CHESTPLATE, 3, helper);

      player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
      helper.assertTrue(task.countCarried(player) == 1, "Worn armour did not count");
      player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_CHESTPLATE));
      helper.assertTrue(task.countCarried(player) == 2, "The off hand did not count");
      task.submitTask(data, player, ItemStack.EMPTY);
      helper.assertTrue(data.getProgress(task) == 0, "Progress moved before the target was carried");

      player.getInventory().add(new ItemStack(Items.IRON_CHESTPLATE));
      helper.assertTrue(task.countCarried(player) == 3, "The main inventory did not count");
      task.submitTask(data, player, ItemStack.EMPTY);
      helper.assertTrue(data.getProgress(task) == 3, "Carrying the target did not set full progress");
      helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
          && player.getOffhandItem().is(Items.IRON_CHESTPLATE)
          && player.getInventory().countItem(Items.IRON_CHESTPLATE) == 3,
          "The carried-item task consumed something");

      player.getInventory().clearContent();
      task.submitTask(data, player, ItemStack.EMPTY);
      helper.assertTrue(data.getProgress(task) == 3, "Dropping the items lowered progress");
    }
    helper.succeed();
  }

  /** An item worn in a Curios slot counts (Terra's Arm on the hands). */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void carriedItemTaskCountsCurios(GameTestHelper helper) throws Throwable {
    if (!ModList.get().isLoaded(CuriosCompat.MOD_ID))
      throw new IllegalStateException("Required real mod is absent: " + CuriosCompat.MOD_ID);
    ServerQuestFile file = file(helper);
    try (Session session = Session.connect(helper, "CarriedCurio")) {
      ServerPlayer player = session.player;
      TeamData data = file.getOrCreateTeamData(player);
      CarriedItemTask task = carried(file, openQuest(helper, file, data), TerraArm.ITEM.get(), 1, helper);
      helper.assertTrue(task.countCarried(player) == 0, "An empty player carries the arm");
      var lookup = MethodHandles.publicLookup();
      Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
      Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
      MethodHandle inventory = lookup.findStatic(api, "getCuriosInventory",
          MethodType.methodType(Optional.class, LivingEntity.class));
      MethodHandle setEquipped = lookup.findVirtual(handler, "setEquippedCurio",
          MethodType.methodType(void.class, String.class, int.class, ItemStack.class));
      Object curios = ((Optional<?>) inventory.invoke(player)).orElseThrow();
      setEquipped.invoke(curios, "hands", 0, new ItemStack(TerraArm.ITEM.get()));
      helper.assertTrue(player.getInventory().countItem(TerraArm.ITEM.get()) == 0
          && task.countCarried(player) == 1, "An arm worn in a Curios slot did not count");
      task.submitTask(data, player, ItemStack.EMPTY);
      helper.assertTrue(data.getProgress(task) == 1, "The worn arm did not complete the task");
      setEquipped.invoke(curios, "hands", 0, ItemStack.EMPTY);
    }
    helper.succeed();
  }

  /** The type is registered under its exact id and the quest-file fields round-trip. */
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void carriedItemTaskFormatRoundTrips(GameTestHelper helper) {
    ServerQuestFile file = file(helper);
    Quest[] first = {null};
    file.forAllQuests(quest -> {
      if (first[0] == null) first[0] = quest;
    });
    Quest quest = first[0];
    helper.assertTrue(quest != null, "The quest book has no quests");
    CarriedItemTask task = carried(file, quest, Items.ELYTRA, 2, helper);
    helper.assertTrue(task.getType().getTypeId().toString().equals("entrelumen:carried_item"),
        "The carried-item task has the wrong type id: " + task.getType().getTypeId());
    CompoundTag saved = new CompoundTag();
    task.writeData(saved, file.holderLookup());
    helper.assertTrue(saved.contains("item") && saved.getLong("count") == 2,
        "The carried-item task did not write item and count: " + saved);
    CarriedItemTask copy = new CarriedItemTask(task.id + 1, quest);
    copy.readData(saved, file.holderLookup());
    helper.assertTrue(copy.getItem().is(Items.ELYTRA) && copy.getMaxProgress() == 2,
        "The carried-item task did not read back its item and count");
    CompoundTag defaults = saved.copy();
    defaults.remove("count");
    copy.readData(defaults, file.holderLookup());
    helper.assertTrue(copy.getMaxProgress() == 1, "A missing count did not default to 1");
    helper.succeed();
  }

  // --- F56: Apotheosis tier tutorial ---------------------------------------------------------

  /** Logging in awards world_tiers_activated once, without moving the tier off Haven. */
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void loginEndsTheWorldTierTutorial(GameTestHelper helper) throws Throwable {
    if (!ModList.get().isLoaded("apotheosis"))
      throw new IllegalStateException("Required real mod is absent: apotheosis");
    try (Session session = Session.connect(helper, "TierTutorial")) {
      ServerPlayer player = session.player;
      var stat = Stats.CUSTOM.get(BuiltInRegistries.CUSTOM_STAT.get(ApotheosisTiers.TIERS_ACTIVATED_STAT));
      helper.assertTrue(player.getStats().getValue(stat) == 1,
          "Login did not award world_tiers_activated exactly once: " + player.getStats().getValue(stat));
      Class<?> worldTier = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
      MethodHandle tutorial = MethodHandles.publicLookup().findStatic(worldTier, "isTutorialActive",
          MethodType.methodType(boolean.class, Player.class));
      helper.assertFalse((boolean) tutorial.invoke(player), "Apotheosis still shows its tier tutorial");
      var access = ApotheosisTiers.access();
      helper.assertTrue(access == null || access.get(player) == ApotheosisTiers.Tier.HAVEN,
          "The login award moved the tier off Haven");
      helper.assertFalse(ApotheosisTiers.endTutorial(player), "The award ran a second time");
      helper.assertTrue(player.getStats().getValue(stat) == 1, "The stat grew past 1");
    }
    helper.succeed();
  }

  // --- helpers --------------------------------------------------------------------------------

  private static ServerQuestFile file(GameTestHelper helper) {
    var file = ServerQuestFile.getInstance().orElse(null);
    if (file == null) throw new IllegalStateException("FTB Quests has no server quest file");
    return file;
  }

  /** Team rewards of distinct non-repeatable quests that carry no campaign milestone. */
  private static List<Reward> teamRewards(GameTestHelper helper, ServerQuestFile file, int count) {
    List<Reward> found = new ArrayList<>();
    file.forAllQuests(quest -> {
      if (found.size() >= count || quest.canBeRepeated() || !QuestClaimGuard.hasTeamReward(quest)) return;
      if (quest.getTasks().stream().anyMatch(CampaignTask.class::isInstance)) return;
      quest.getRewards().stream().filter(Reward::isTeamReward).findFirst().ifPresent(found::add);
    });
    if (found.size() < count)
      throw new IllegalStateException("The quest book has fewer than " + count + " team rewards");
    return found;
  }

  /** A quest the fresh player's team can start, with no campaign task. */
  private static Quest openQuest(GameTestHelper helper, ServerQuestFile file, TeamData data) {
    Quest[] open = {null};
    file.forAllQuests(quest -> {
      if (open[0] == null && data.canStartTasks(quest)
          && quest.getTasks().stream().noneMatch(CampaignTask.class::isInstance)) open[0] = quest;
    });
    if (open[0] == null) throw new IllegalStateException("No quest is open to a fresh team");
    return open[0];
  }

  /** A standalone carried-item task on {@code quest}, read from the documented quest-file format. */
  private static CarriedItemTask carried(ServerQuestFile file, Quest quest, net.minecraft.world.item.Item item,
      long count, GameTestHelper helper) {
    CompoundTag tag = new CompoundTag();
    tag.put("item", new ItemStack(item).save(file.holderLookup()));
    tag.putLong("count", count);
    CarriedItemTask task = new CarriedItemTask(0x7E57_C0DE_0000_0000L + new java.util.Random().nextInt(1 << 20), quest);
    task.readData(tag, file.holderLookup());
    helper.assertTrue(task.getItem().is(item) && task.getMaxProgress() == count,
        "The carried-item task did not read its quest-file fields");
    return task;
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final EmbeddedChannel channel;
    UUID partyId;
    boolean disconnected;

    private Session(ServerPlayer player, Connection connection, EmbeddedChannel channel) {
      this.player = player;
      this.connection = connection;
      this.channel = channel;
    }

    static Session connect(GameTestHelper helper, String prefix) {
      UUID id = UUID.randomUUID();
      var cookie = CommonListenerCookie.createInitial(new GameProfile(id, prefix + id.toString().substring(0, 4)), false);
      ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          cookie.gameProfile(), cookie.clientInformation());
      var connection = new Connection(PacketFlow.SERVERBOUND);
      var session = new Session(player, connection, new EmbeddedChannel(connection));
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      return session;
    }

    void leaveParty() throws Exception {
      player.server.getCommands().getDispatcher().execute("ftbteams party leave",
          player.createCommandSourceStack().withSuppressedOutput());
      partyId = null;
    }

    void disconnectOnly() {
      if (disconnected) return;
      disconnected = true;
      try {
        connection.disconnect(Component.literal("Entrelumen quest integration QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }

    @Override
    public void close() throws Exception {
      try {
        if (!disconnected && partyId != null) {
          var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
          if (team != null && partyId.equals(team.getId())) leaveParty();
        }
      } finally {
        disconnectOnly();
      }
    }
  }
}
