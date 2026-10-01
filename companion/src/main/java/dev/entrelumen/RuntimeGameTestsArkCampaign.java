package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Ark and the campaign around it (ultra review, 1 October 2026): what a party takes from its
 * founder and gives back when it is disbanded, fake players without a team, modules and relics that
 * cannot be lost, {@code /rtp} in the End and under attack, and deliveries that spare marked copies.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsArkCampaign {
  private RuntimeGameTestsArkCampaign() {}

  private static final class QaPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
          new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      try {
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    public void close() {
      try {
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team instanceof dev.ftb.mods.ftbteams.data.PartyTeam party) party.leave(player.getUUID());
      } catch (Exception ignored) {
        // A failed test may leave its party behind; the QA world is discarded.
      }
      try {
        ArkData.get(player.server).arks.remove(player.getUUID());
        connection.disconnect(net.minecraft.network.chat.Component.literal("Ark campaign QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private static BlockState state(ServerLevel level, ArkMultiblock.Part part, int rotation) {
    try {
      return BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), part.state(), false).blockState()
          .rotate(ArkMultiblock.rotation(rotation));
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
      throw new IllegalStateException(part.state(), invalid);
    }
  }

  /** The whole Ark, controller included, in the {@code ark} template. */
  private static ArkMultiblock.Anchor buildArk(GameTestHelper helper, int rotation) {
    var level = helper.getLevel();
    var anchor = new ArkMultiblock.Anchor(helper.absolutePos(new BlockPos(10, 2, 10)), rotation);
    Set<String> slots = new HashSet<>(ArkRules.moduleIds());
    slots.add(ArkMultiblock.CONTROLLER);
    for (var part : ArkMultiblock.definition().parts()) {
      if (ArkMultiblock.BEACON.equals(part.slot())) continue;
      if (!part.core() && !slots.contains(part.slot())) continue;
      level.setBlock(ArkMultiblock.world(anchor, part.offset()), state(level, part, anchor.rotation()), 3);
    }
    return anchor;
  }

  private static BlockPos slot(ArkMultiblock.Anchor anchor, String slot) {
    return ArkMultiblock.world(anchor, ArkMultiblock.definition().slots().get(slot));
  }

  private static dev.ftb.mods.ftbteams.data.PartyTeam party(ServerPlayer founder, String name) throws Exception {
    return (dev.ftb.mods.ftbteams.data.PartyTeam) FTBTeamsAPI.api().getManager().createPartyTeam(founder,
        name + " " + founder.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
  }

  /** The sole owner leaves: FTB Teams deletes the party (TeamEvent.DELETED). */
  private static void disband(dev.ftb.mods.ftbteams.data.PartyTeam party, ServerPlayer owner) throws Exception {
    if (FTBTeamsAPI.api().getManager().getTeamByID(party.getId()).isPresent()) party.leave(owner.getUUID());
  }

  // ---- F22 and F31: the party lifecycle ----------------------------------------------------------

  @GameTest(template = "ark", timeoutTicks = 200)
  public static void aSoloArkAndItsShopsFollowTheFounderIntoAPartyAndBack(GameTestHelper helper) throws Exception {
    var level = helper.getLevel();
    var data = ArkData.get(level.getServer());
    var anchor = buildArk(helper, 2);
    BlockPos controller = slot(anchor, ArkMultiblock.CONTROLLER);
    UUID partyId = null;
    try (var qa = new QaPlayer(helper, "HandoffArk")) {
      var player = qa.player;
      UUID personal = player.getUUID();
      helper.assertTrue(ArkState.place(level, controller, ArkMultiblock.CONTROLLER, personal)
          == ArkState.Placement.FOUNDED, "The solo player did not found an Ark");
      var ark = data.arks.get(personal);
      String shop = ArkRules.shopId("bakery", controller.asLong());
      data.knownShops.computeIfAbsent(personal, id -> new TreeSet<>()).add(shop);

      var party = party(player, "Handoff");
      partyId = party.getId();
      helper.assertTrue(CampaignActions.campaignId(player).equals(partyId) && data.arks.get(partyId) == ark
          && !data.arks.containsKey(personal) && ArkState.status(player).activeModules().size() == 6,
          "Founding a party left the Ark behind: " + ArkState.status(player));
      helper.assertTrue(ArkState.place(level, controller, ArkMultiblock.CONTROLLER, partyId)
          == ArkState.Placement.JOINED, "Re-placing the controller did not join the party's Ark");
      helper.assertTrue(data.knownShops.getOrDefault(partyId, Set.of()).contains(shop),
          "The party does not know the founder's shop");

      // An Ark left under the founder's own UUID (a party founded before the hand-over) is taken over.
      data.arks.put(personal, data.arks.remove(partyId));
      ArkState.onPlaced(level, controller, ArkMultiblock.CONTROLLER, player);
      helper.assertTrue(data.arks.get(partyId) == ark && !data.arks.containsKey(personal),
          "Re-placing the controller did not take the founder's own Ark over");
      helper.assertTrue(!ArkState.mayTakeOver(player, UUID.randomUUID()),
          "A stranger's Ark could be taken over");

      disband(party, player);
      helper.assertTrue(CampaignActions.campaignId(player).equals(personal) && data.arks.get(personal) == ark
          && !data.arks.containsKey(partyId), "After the party was disbanded the founder does not own the Ark");

      // An Ark left under a party that no longer exists is abandoned: its former member takes it over.
      data.arks.put(partyId, data.arks.remove(personal));
      ArkState.onPlaced(level, controller, ArkMultiblock.CONTROLLER, player);
      helper.assertTrue(data.arks.get(personal) == ark && !data.arks.containsKey(partyId),
          "The Ark of a disbanded party could not be taken over");
      data.knownShops.remove(personal);
    } finally {
      if (partyId != null) {
        data.arks.remove(partyId);
        data.knownShops.remove(partyId);
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void aPartyTakesTheFoundersPlotKeyAndSiteAndGivesThePlotBack(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var city = SolsticioData.get(server);
    var nature = NatureRestorationData.get(server);
    var mine = new SolsticioData.Plot(new BlockPos(20_000, 69, 20_000));
    var other = new SolsticioData.Plot(new BlockPos(20_032, 69, 20_000));
    Set<UUID> keys = new HashSet<>();
    try (var qa = new QaPlayer(helper, "HandoffPlot")) {
      var player = qa.player;
      UUID personal = player.getUUID();
      keys.add(personal);
      mine.owner = personal;
      mine.ownerName = player.getGameProfile().getName();
      city.plots.add(mine);
      city.plots.add(other);
      city.forgedKeys.add(personal);
      var site = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)));
      nature.mark(personal, site);

      var first = party(player, "Plot");
      keys.add(first.getId());
      helper.assertTrue(mine.owner.equals(first.getId()) && city.forgedKeys.contains(first.getId())
          && site.equals(nature.site(first.getId())), "The party did not take the founder's plot, key and site");
      disband(first, player);
      helper.assertTrue(personal.equals(mine.owner) && city.forgedKeys.contains(first.getId()),
          "Disbanding did not give the plot back to an owner without one");

      var second = party(player, "Plot again");
      keys.add(second.getId());
      helper.assertTrue(second.getId().equals(mine.owner), "The second party did not take the plot");
      other.owner = personal;
      disband(second, player);
      helper.assertTrue(mine.owner == null && mine.ownerName.isEmpty() && mine.claimedAt == 0
          && personal.equals(other.owner), "Disbanding did not free the plot of an owner who has one");
    } finally {
      city.plots.remove(mine);
      city.plots.remove(other);
      city.forgedKeys.removeAll(keys);
      city.setDirty();
      StructureProtection.invalidate(server);
    }
    helper.succeed();
  }

  // ---- F26: fake players FTB Teams does not know --------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void fakePlayersWithoutATeamTouchNothing(GameTestHelper helper) {
    var level = helper.getLevel();
    var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[ArkDeployer]"));
    helper.assertTrue(CampaignActions.campaignIdOrNull(fake) == null, "An unknown fake player has a campaign");
    BlockPos controller = helper.absolutePos(new BlockPos(2, 1, 2));
    BlockPos module = helper.absolutePos(new BlockPos(4, 1, 2));
    level.setBlockAndUpdate(controller, block("entrelumen:ark_controller"));
    level.setBlockAndUpdate(module, block("entrelumen:nature_module"));
    fake.moveTo(controller.getX() + 0.5, controller.getY() + 1, controller.getZ() + 1.5);
    ArkState.onPlaced(level, controller, ArkMultiblock.CONTROLLER, fake);
    helper.assertTrue(ArkData.get(level.getServer()).owner(level.dimension(),
        new ArkMultiblock.Anchor(controller, 0)) == null, "A fake player founded an Ark");
    helper.assertTrue(!ArkFieldJournals.inspect(fake, controller), "A fake player opened the Ark's screen");
    for (BlockPos pos : new BlockPos[] {controller, module})
      helper.assertTrue(level.getBlockState(pos).useWithoutItem(level, fake,
          new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)) == InteractionResult.PASS,
          "A fake player used an Ark block");
    fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
    helper.assertTrue(NatureRestoration.use(fake, module, InteractionHand.MAIN_HAND).status()
        == NatureRestoration.Status.UNAVAILABLE, "A fake player marked a garden site");
    var atlas = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:atlas")));
    fake.setItemInHand(InteractionHand.MAIN_HAND, atlas);
    helper.assertTrue(atlas.getItem().use(level, fake, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
        "A fake player opened the Atlas");
    helper.succeed();
  }

  private static BlockState block(String id) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
  }

  // ---- F6: modules and relics cannot be lost -----------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void modulesAndRelicsSurviveExplosionsFireAndTime(GameTestHelper helper) {
    var level = helper.getLevel();
    for (String id : Entrelumen.MODULES) {
      BlockState state = block("entrelumen:" + id);
      helper.assertTrue(state.getBlock().getExplosionResistance() >= 1200f && !state.requiresCorrectToolForDrops()
          && state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE)
          && state.is(BlockTags.MINEABLE_WITH_PICKAXE), "The " + id + " block can be lost or needs a pickaxe");
      ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("entrelumen:" + id)));
      helper.assertTrue(stack.has(DataComponents.FIRE_RESISTANT), "The " + id + " item burns");
      lasting(helper, level, stack, id);
    }
    for (var relic : Solsticio.RELICS) lasting(helper, level, new ItemStack(relic.get()), "relic");
    // A creeper-sized blast next to a placed module leaves it standing.
    BlockPos module = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(module, block("entrelumen:arcane_module"));
    level.explode(null, module.getX() + 1.5, module.getY() + 0.5, module.getZ() + 0.5, 3f, Level.ExplosionInteraction.TNT);
    helper.assertTrue(level.getBlockState(module).is(BuiltInRegistries.BLOCK.get(
        ResourceLocation.parse("entrelumen:arcane_module"))), "An explosion destroyed a module");
    helper.succeed();
  }

  private static void lasting(GameTestHelper helper, ServerLevel level, ItemStack stack, String id) {
    var entity = new ItemEntity(level, 0, 0, 0, stack);
    helper.assertTrue(entity.lifespan == Integer.MAX_VALUE, "A dropped " + id + " despawns");
    helper.assertTrue(!entity.hurt(level.damageSources().explosion(null, null), 10f)
        && !entity.hurt(level.damageSources().cactus(), 10f) && !entity.hurt(level.damageSources().lava(), 10f),
        "A dropped " + id + " can be destroyed");
  }

  // ---- F21 and F46: /rtp -------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void rtpWaitsForTheDragonAndAHitCancelsTheSearch(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    ServerLevel end = server.getLevel(Level.END);
    helper.assertTrue(end != null && ArkCommands.dragonLocked(end) && !ArkCommands.dragonLocked(helper.getLevel()),
        "/rtp is open in the End before the dragon died, or closed in the Overworld");
    var data = ArkData.get(server);
    try (var qa = new QaPlayer(helper, "RtpHurt")) {
      var player = qa.player;
      var ark = new ArkData.Ark(player.level().dimension(),
          new ArkMultiblock.Anchor(new BlockPos(29_000_000 - 64, 64, 29_000_000 - 64), 0));
      ark.core = ark.controller = true;
      ark.modules.add(ArkRules.Module.EXPLORATION.id);
      data.arks.put(player.getUUID(), ark);
      helper.assertTrue(ArkCommands.rtp(player) == 1 && ArkCommands.searching(player), "/rtp did not start");
      helper.assertTrue(player.hurt(player.damageSources().fellOutOfWorld(), 1f), "The QA player could not be hurt");
      helper.assertTrue(!ArkCommands.searching(player) && !data.rtpUsed.containsKey(player.getUUID()),
          "A hit did not cancel the /rtp search, or spent its cooldown");
    }
    helper.succeed();
  }

  // ---- F41: deliveries spare marked copies -------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void deliveriesTakePlainCopiesFirstAndNeverAnErrand(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "PlainFirst")) {
      var player = qa.player;
      var inventory = player.getInventory();
      ItemStack bound = new ItemStack(Items.COMPASS);
      bound.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
          Optional.of(GlobalPos.of(player.level().dimension(), BlockPos.ZERO)), false));
      inventory.setItem(0, bound);
      inventory.setItem(1, new ItemStack(Items.COMPASS));
      ItemStack errand = new ItemStack(Items.MAP);
      CompoundTag marker = new CompoundTag();
      marker.putString(SolsticioStory.ERRAND, "cartographer_map");
      errand.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
      inventory.setItem(2, errand);
      inventory.setItem(3, new ItemStack(Items.MAP, 2));
      Map<String, Integer> available = Entrelumen.availableMaterials(player);
      helper.assertTrue(available.get("minecraft:compass") == 2 && available.get("minecraft:map") == 2,
          "The materials count an errand item: " + available);
      Entrelumen.consume(player, stack -> Entrelumen.material(stack, "minecraft:compass"), 1);
      helper.assertTrue(inventory.getItem(0).has(DataComponents.LODESTONE_TRACKER) && inventory.getItem(1).isEmpty(),
          "The delivery took the bound compass before the plain one");
      Entrelumen.consume(player, stack -> Entrelumen.material(stack, "minecraft:map"), 2);
      helper.assertTrue(inventory.getItem(2).getCount() == 1 && inventory.getItem(3).isEmpty()
          && Entrelumen.availableMaterials(player).getOrDefault("minecraft:map", 0) == 0,
          "The delivery took the errand map");
      Entrelumen.consume(player, stack -> Entrelumen.material(stack, "minecraft:compass"), 1);
      helper.assertTrue(inventory.getItem(0).isEmpty(), "A marked copy is not taken once the plain ones run out");
    }
    helper.succeed();
  }
}
