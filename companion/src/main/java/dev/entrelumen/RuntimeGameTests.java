package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

/** Development-only integration tests; excluded from the distributable jar. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTests {
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void registeredRecipesCraftAtlas(GameTestHelper helper) {
    for (String id :
        List.of("atlas", "raw_lens", "survey_notes", "signal_core", "ark_controller")) {
      ResourceLocation key = ResourceLocation.fromNamespaceAndPath("entrelumen", id);
      helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key), "Missing item: " + id);
      helper.assertTrue(
          helper.getLevel().getRecipeManager().byKey(key).isPresent(), "Missing recipe: " + id);
    }
    for (String id : Entrelumen.MODULES)
      helper.assertTrue(
          BuiltInRegistries.BLOCK.containsKey(
              ResourceLocation.fromNamespaceAndPath("entrelumen", id)),
          "Missing module block: " + id);
    var input =
        CraftingInput.of(
            2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(Items.COPPER_INGOT)));
    var recipe =
        helper
            .getLevel()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    helper.assertTrue(recipe.isPresent(), "Atlas ingredients do not match a crafting recipe");
    var output = recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess());
    helper.assertTrue(
        BuiltInRegistries.ITEM.getKey(output.getItem()).toString().equals("entrelumen:atlas")
            && output.getCount() == 1,
        "Crafting did not produce one Atlas");
    helper.succeed();
  }

  private static ServerPlayer player(GameTestHelper helper, String name) {
    var cookie =
        net.minecraft.server.network.CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), name), false);
    ServerPlayer player =
        new ServerPlayer(
            helper.getLevel().getServer(),
            helper.getLevel(),
            cookie.gameProfile(),
            cookie.clientInformation());
    var connection =
        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
    new io.netty.channel.embedded.EmbeddedChannel(connection);
    net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
    player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
    return player;
  }

  private static int command(ServerPlayer player, String command) {
    try {
      return player
          .server
          .getCommands()
          .getDispatcher()
          .execute(command, player.createCommandSourceStack());
    } catch (Exception e) {
      throw new IllegalStateException("Player command failed", e);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void deliveryConsumesServerInventoryOnce(GameTestHelper helper) {
    var player = player(helper, "DeliveryTest");
    player.getInventory().add(new ItemStack(Items.BOOK, 2));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 3));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1, "First delivery rejected");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Delivery did not consume exact materials");
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0, "Repeated delivery accepted");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Atlas reward missing or duplicated");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Repeated delivery consumed inventory");
    helper.assertTrue(
        Entrelumen.current(player).completed.contains("atlas_awakened"),
        "Delivery did not persist milestone");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void savedDataDiskRoundTrip(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var data = CampaignData.get(server);
    UUID id = UUID.randomUUID();
    var original = data.campaigns.personal(id);
    original.act = 4;
    original.completed.add("field_survey");
    original.arkPhase = 2;
    data.setDirty();
    server.overworld().getDataStorage().save();
    var path =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/entrelumen_campaigns.dat");
    helper.succeedWhen(
        () -> {
          try {
            var tag =
                net.minecraft.nbt.NbtIo.readCompressed(
                    path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var loaded =
                CampaignData.load(tag.getCompound("data"), helper.getLevel().registryAccess());
            var restored = loaded.campaigns.personal(id);
            helper.assertTrue(
                restored.act == 4
                    && restored.arkPhase == 2
                    && restored.completed.contains("field_survey"),
                "Persisted campaign did not survive disk read");
          } catch (java.io.IOException e) {
            helper.fail("SavedData IO has not completed: " + e.getMessage());
          }
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void questMirrorCannotGrantCampaign(GameTestHelper helper) {
    var player = player(helper, "QuestMirrorTest");
    var file = ServerQuestFile.getInstance().orElseThrow();
    var chapter = new Chapter(0x7E110001L, file, file.getDefaultChapterGroup());
    var quest = new Quest(0x7E110002L, chapter);
    chapter.addQuest(quest);
    var task = new CampaignTask(0x7E110003L, quest);
    quest.addTask(task);
    var tag = new CompoundTag();
    tag.putString("milestone", "atlas_awakened");
    task.readData(tag, helper.getLevel().registryAccess());
    var teamData = file.getOrCreateTeamData(player);
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 0, "Unfinished milestone appeared completed");
    player.getInventory().add(new ItemStack(Items.BOOK));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1, "Delivery rejected");
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 1, "Delivered milestone did not mirror");
    Entrelumen.current(player).completed.remove("atlas_awakened");
    task.submitTask(teamData, player, ItemStack.EMPTY);
    helper.assertTrue(teamData.getProgress(task) == 0, "Stale quest progress was not reset");
    helper.assertTrue(
        teamData.getCompletedTime(task.id).isEmpty()
            && teamData.getCompletedTime(quest.id).isEmpty(),
        "Stale completion timestamps still unlock quest dependencies");
    helper.assertTrue(
        !Entrelumen.current(player).completed.contains("atlas_awakened"),
        "Quest projection granted campaign progress");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void realPartyLifecyclePreservesPersonalSnapshots(GameTestHelper helper)
      throws Exception {
    var founder = player(helper, "FounderTest");
    var guest = player(helper, "GuestTest");
    Entrelumen.current(founder).act = 3;
    Entrelumen.current(founder).completed.add("first_signal");
    Entrelumen.current(guest).act = 2;
    var team =
        (dev.ftb.mods.ftbteams.data.PartyTeam)
            FTBTeamsAPI.api()
                .getManager()
                .createPartyTeam(
                    founder,
                    "Runtime party " + founder.getUUID(),
                    "",
                    dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    helper.assertTrue(Entrelumen.current(founder).act == 3, "Party creation lost founder progress");
    Entrelumen.current(founder).act = 4;
    team.invite(founder, List.of(guest.getGameProfile()));
    team.join(guest);
    helper.assertTrue(
        Entrelumen.current(guest).act == 4, "Joining did not use destination campaign");
    team.leave(guest.getUUID());
    helper.assertTrue(
        Entrelumen.current(guest).act == 2, "Leaving did not restore personal snapshot");
    team.leave(founder.getUUID());
    helper.assertTrue(Entrelumen.current(founder).act == 3, "Founder personal snapshot changed");
    helper.assertTrue(
        CampaignData.get(founder.server).campaigns.parties.get(team.getId()).archived,
        "Dissolved campaign not archived");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void offhandDeliveryAndMissingMaterialAreAtomic(GameTestHelper helper) {
    var player = player(helper, "OffhandTest");
    player.getInventory().add(new ItemStack(Items.BOOK));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0, "Incomplete delivery accepted");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1,
        "Failed delivery consumed a partial ingredient");
    player.setItemInHand(
        net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.COPPER_INGOT, 2));
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 1,
        "Offhand ingredient was not counted");
    helper.assertTrue(player.getOffhandItem().getCount() == 1, "Offhand exact consumption failed");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 0, "Main inventory ingredient not consumed");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Delivery did not grant one Atlas");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void statusReportsMissingMaterialsAndPrerequisites(GameTestHelper helper) {
    var player = player(helper, "StatusTest");
    player.setItemInHand(
        net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.COPPER_INGOT, 1));
    var lines = Entrelumen.statusLines(player);
    boolean foundCopper = false, foundPrerequisite = false;
    for (var line : lines)
      if (line.getContents()
          instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
        if (translated.getKey().equals("entrelumen.project.material")) {
          Object[] args = translated.getArgs();
          if (args[0] instanceof net.minecraft.network.chat.Component name
              && name.getString()
                  .equals(new ItemStack(Items.COPPER_INGOT).getHoverName().getString())
              && args[1].equals(1)
              && args[2].equals(1)
              && args[3].equals(0)) foundCopper = true;
        }
        if (translated.getKey().equals("entrelumen.project.prerequisite.missing"))
          foundPrerequisite = true;
      }
    helper.assertTrue(
        foundCopper, "Status does not report the offhand available/required/missing amount");
    helper.assertTrue(foundPrerequisite, "Status omitted prerequisite information");
    helper.succeed();
  }

  @GameTest(template = "empty", batch = "campaign_reload", timeoutTicks = 1200)
  public static void datapackReloadIsAtomicAndRejectsCycles(GameTestHelper helper)
      throws Exception {
    var server = helper.getLevel().getServer();
    var selected = List.copyOf(server.getPackRepository().getSelectedIds());
    var directory =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
            .resolve("entrelumen_runtime_reload");
    var definition = directory.resolve("data/entrelumen/campaign/projects.json");
    java.nio.file.Files.createDirectories(definition.getParent());
    java.nio.file.Files.writeString(
        directory.resolve("pack.mcmeta"),
        "{\"pack\":{\"pack_format\":48,\"description\":\"Entrelumen runtime reload test\"}}");
    com.google.gson.JsonObject original;
    try (var reader =
        server
            .getResourceManager()
            .getResource(ProjectReloadListener.RESOURCE)
            .orElseThrow()
            .openAsReader()) {
      original = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
    }
    var valid = original.deepCopy();
    valid
        .getAsJsonObject("atlas_awakened")
        .getAsJsonObject("items")
        .addProperty("minecraft:book", 2);
    java.nio.file.Files.writeString(definition, valid.toString());
    server.getPackRepository().reload();
    var withTest = new ArrayList<>(selected);
    withTest.add("file/entrelumen_runtime_reload");
    class ReloadSequence {
      int step;
      java.util.concurrent.CompletableFuture<Void> pending = server.reloadResources(withTest);
      Map<String, Projects.Project> accepted;

      void run() {
        helper.assertTrue(pending.isDone(), "Waiting for asynchronous datapack reload");
        try {
          if (step == 0) {
            pending.join();
            accepted = Projects.all();
            helper.assertTrue(
                accepted.get("atlas_awakened").items().get("minecraft:book") == 2,
                "Higher priority datapack did not replace bundled definitions");
            var invalid = valid.deepCopy();
            invalid
                .getAsJsonObject("atlas_awakened")
                .getAsJsonObject("items")
                .addProperty("minecraft:book", 7);
            invalid
                .getAsJsonObject("world_network")
                .addProperty("reward", "entrelumen:nonexistent_item");
            java.nio.file.Files.writeString(definition, invalid.toString());
            step = 1;
            pending = server.reloadResources(withTest);
            helper.fail("Waiting for invalid resource rejection");
          } else if (step == 1) {
            assertRejected("unknown or unusable item ID");
            helper.assertTrue(
                Projects.all() == accepted
                    && Projects.all().get("atlas_awakened").items().get("minecraft:book") == 2,
                "Invalid reload partially replaced active definitions");
            var cycle = valid.deepCopy();
            var requires = new com.google.gson.JsonArray();
            requires.add("first_signal");
            cycle.getAsJsonObject("atlas_awakened").add("requires", requires);
            java.nio.file.Files.writeString(definition, cycle.toString());
            step = 2;
            pending = server.reloadResources(withTest);
            helper.fail("Waiting for cyclic resource rejection");
          } else if (step == 2) {
            assertRejected("prerequisite cycle");
            helper.assertTrue(
                Projects.all() == accepted, "Cyclic reload replaced active definitions");
            java.nio.file.Files.writeString(definition, original.toString());
            step = 3;
            pending = server.reloadResources(selected);
            helper.fail("Waiting for original datapacks to restore");
          } else {
            pending.join();
            helper.assertTrue(
                Projects.all().get("atlas_awakened").items().get("minecraft:book")
                    == original
                        .getAsJsonObject("atlas_awakened")
                        .getAsJsonObject("items")
                        .get("minecraft:book")
                        .getAsInt(),
                "Original data not restored");
          }
        } catch (java.io.IOException e) {
          throw new IllegalStateException("Could not write isolated test datapack", e);
        }
      }

      void assertRejected(String expected) {
        helper.assertTrue(
            pending.isCompletedExceptionally(), "Invalid datapack unexpectedly accepted");
        try {
          pending.join();
          throw new IllegalStateException("Expected rejection");
        } catch (java.util.concurrent.CompletionException e) {
          StringBuilder causes = new StringBuilder();
          for (Throwable cause = e; cause != null; cause = cause.getCause())
            causes.append(cause.getMessage()).append('\n');
          helper.assertTrue(
              causes.toString().contains(expected),
              "Reload failed for unrelated reason: " + causes);
        }
      }
    }
    var sequence = new ReloadSequence();
    helper.succeedWhen(sequence::run);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasRouteRejectsStaleTeamAndCannotDoubleConsume(GameTestHelper helper)
      throws Exception {
    var player = player(helper, "AtlasRouteTest");
    player.getInventory().add(new ItemStack(Items.BOOK, 2));
    player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 2));
    var original = AtlasNetwork.snapshot(player, true, "");
    var team =
        (dev.ftb.mods.ftbteams.data.PartyTeam)
            FTBTeamsAPI.api()
                .getManager()
                .createPartyTeam(
                    player,
                    "Atlas " + player.getUUID(),
                    "",
                    dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var stale =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                original.campaign(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        stale.message().equals("entrelumen.atlas.stale") && stale.campaign().equals(team.getId()),
        "Stale action was not rejected with refreshed team identity");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 2
            && player.getInventory().countItem(Items.COPPER_INGOT) == 2,
        "Stale team action consumed materials");
    var delivered =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                stale.campaign(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        delivered.projects().stream()
            .anyMatch(project -> project.id().equals("atlas_awakened") && project.completed()),
        "GUI route did not report authoritative completion");
    helper.assertTrue(
        command(player, "entrelumen deliver atlas_awakened") == 0,
        "Command replay after GUI route accepted");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1
            && player.getInventory().countItem(Items.COPPER_INGOT) == 1,
        "Cross-route replay consumed materials");
    helper.assertTrue(
        player
                .getInventory()
                .countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")))
            == 1,
        "Cross-route reward duplicated");
    team.leave(player.getUUID());
    var afterLeave =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(
                team.getId(), CampaignActions.Action.DELIVER, "atlas_awakened"));
    helper.assertTrue(
        afterLeave.message().equals("entrelumen.atlas.stale")
            && afterLeave.campaign().equals(original.campaign()),
        "Leaving did not invalidate old party screen");
    helper.assertTrue(
        player.getInventory().countItem(Items.BOOK) == 1,
        "Stale party screen consumed personal materials");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasAdvanceAndSnapshotCodecUseAuthoritativeState(GameTestHelper helper) {
    var player = player(helper, "AtlasAdvanceTest");
    var campaign = Entrelumen.current(player);
    var initial = AtlasNetwork.snapshot(player, true, "");
    var denied =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(denied.act() == 1 && !denied.canAdvance(), "GUI advanced without projects");
    campaign.completed.addAll(Projects.forAct(1));
    var advanced =
        AtlasNetwork.handleRequest(
            player,
            new AtlasNetwork.Request(initial.campaign(), CampaignActions.Action.ADVANCE, ""));
    helper.assertTrue(
        advanced.act() == 2 && campaign.act == 2,
        "GUI advancement did not use persistent campaign");
    helper.assertTrue(
        advanced.projects().stream()
            .allMatch(project -> Projects.all().get(project.id()).act() == 2),
        "Snapshot leaked unrelated acts");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
    try {
      AtlasNetwork.Snapshot.CODEC.encode(buffer, advanced);
      var decoded = AtlasNetwork.Snapshot.CODEC.decode(buffer);
      helper.assertTrue(decoded.equals(advanced), "Snapshot wire roundtrip changed state");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasOpenNeedsNoItemOrCampaignToken(GameTestHelper helper) throws Exception {
    var player = player(helper, "AtlasOpenTest");
    var original = AtlasNetwork.handleOpen(player);
    helper.assertTrue(
        original.open() && original.campaign().equals(player.getUUID()),
        "Open did not resolve personal campaign");
    helper.assertTrue(
        player.getInventory().isEmpty() && Entrelumen.current(player).completed.isEmpty(),
        "Opening consumed or granted inventory/progression");
    var party =
        FTBTeamsAPI.api()
            .getManager()
            .createPartyTeam(
                player,
                "Bootstrap " + player.getUUID(),
                "",
                dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
    var current = AtlasNetwork.handleOpen(player);
    helper.assertTrue(
        current.campaign().equals(party.getId()) && current.open(),
        "Open reused stale personal campaign");
    helper.assertTrue(
        command(player, "entrelumen") == 1, "No-argument accessibility command failed");
    helper.assertTrue(
        player.getInventory().isEmpty() && Entrelumen.current(player).completed.isEmpty(),
        "Accessibility command changed inventory/progression");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
    try {
      AtlasNetwork.OpenRequest.CODEC.encode(buffer, AtlasNetwork.OpenRequest.INSTANCE);
      helper.assertTrue(
          buffer.readableBytes() == 0
              && AtlasNetwork.OpenRequest.CODEC
                  .decode(buffer)
                  .equals(AtlasNetwork.OpenRequest.INSTANCE),
          "Open request unexpectedly requires payload state");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }
}
