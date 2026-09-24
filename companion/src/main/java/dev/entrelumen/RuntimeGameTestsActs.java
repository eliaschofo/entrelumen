package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests of the act renumbering of 24 September 2026 and the Heart of Heliodor;
 * excluded from the distributable jar by the RuntimeGameTests* pattern. The Aether is not on this
 * server, so the Sun Spirit's death is reproduced by a loot context for its loot table
 * ({@code aether:entities/sun_spirit}) run through NeoForge's real loot-modifier pass; the full-pack
 * QA kills a real Sun Spirit.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsActs {
  private RuntimeGameTestsActs() {}

  /** A real server player on an in-memory connection, with its own FTB team and campaign. */
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
        connection.disconnect(net.minecraft.network.chat.Component.literal("Acts QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** The loot a Sun Spirit killed by {@code killer} rolls, after every loaded loot modifier. */
  private static ObjectArrayList<ItemStack> sunSpiritLoot(GameTestHelper helper, ServerPlayer killer,
      ResourceLocation table) {
    var level = helper.getLevel();
    var victim = EntityType.ZOMBIE.create(level);
    victim.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
    var source = level.damageSources().playerAttack(killer);
    var params = new LootParams.Builder(level)
        .withParameter(LootContextParams.THIS_ENTITY, victim)
        .withParameter(LootContextParams.ORIGIN, victim.position())
        .withParameter(LootContextParams.DAMAGE_SOURCE, source)
        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
        .create(LootContextParamSets.ENTITY);
    LootContext context = new LootContext.Builder(params).create(Optional.empty());
    // The table's own drops (the gold dungeon key and the sun altar in the Aether) stand in as gold.
    var loot = new ObjectArrayList<ItemStack>();
    loot.add(new ItemStack(Items.GOLD_INGOT));
    return CommonHooks.modifyLoot(table, loot, context);
  }

  private static long hearts(ObjectArrayList<ItemStack> loot) {
    return loot.stream().filter(stack -> stack.is(HeliodorHeart.ITEM.get())).mapToInt(ItemStack::getCount).sum();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void sunSpiritDropsTheHeartOncePerCampaign(GameTestHelper helper) {
    var table = HeliodorHeart.SUN_SPIRIT_LOOT.location();
    try (var first = new QaPlayer(helper, "HeartFirst"); var second = new QaPlayer(helper, "HeartSecond");
        var other = new QaPlayer(helper, "HeartOther")) {
      var campaign = Entrelumen.current(first.player);
      helper.assertTrue(!HeliodorHeartRules.recovered(campaign), "A new campaign already had its Heart");
      var loot = sunSpiritLoot(helper, first.player, table);
      helper.assertTrue(hearts(loot) == 1 && loot.getFirst().is(Items.GOLD_INGOT) && loot.size() == 2,
          "The first Sun Spirit did not add exactly one Heart to its own loot: " + loot);
      helper.assertTrue(campaign.completed.contains(HeliodorHeartRules.RECOVERED)
          && CampaignMilestones.isComplete(campaign, HeliodorHeartRules.RECOVERED),
          "The team's campaign did not record its Heart");
      var again = sunSpiritLoot(helper, first.player, table);
      helper.assertTrue(hearts(again) == 0 && again.size() == 1, "A second Sun Spirit dropped another Heart");
      // Another team gets its own; another loot table never gives one.
      helper.assertTrue(hearts(sunSpiritLoot(helper, second.player, table)) == 1,
          "A second team did not get its own Heart");
      var zombie = sunSpiritLoot(helper, other.player, ResourceLocation.withDefaultNamespace("entities/zombie"));
      helper.assertTrue(hearts(zombie) == 0 && !HeliodorHeartRules.recovered(Entrelumen.current(other.player)),
          "Another mob's loot gave a Heart");
      // Machines that kill with a fake player get nothing and record nothing.
      var fake = FakePlayerFactory.getMinecraft(helper.getLevel());
      helper.assertTrue(hearts(sunSpiritLoot(helper, fake, table)) == 0, "A fake player took a Heart");
      // An archived campaign gets nothing either.
      var archivedCampaign = Entrelumen.current(other.player);
      archivedCampaign.archived = true;
      helper.assertTrue(hearts(sunSpiritLoot(helper, other.player, table)) == 0
          && !archivedCampaign.completed.contains(HeliodorHeartRules.RECOVERED), "An archived campaign took a Heart");
      archivedCampaign.archived = false;
      helper.assertTrue(hearts(sunSpiritLoot(helper, other.player, table)) == 1, "The un-archived team got no Heart");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void heartOfHeliodorIsOneEpicFireproofStackWithLore(GameTestHelper helper) {
    var level = helper.getLevel();
    var stack = new ItemStack(HeliodorHeart.ITEM.get());
    helper.assertTrue(stack.getMaxStackSize() == 1 && stack.getRarity() == Rarity.EPIC
        && stack.has(DataComponents.FIRE_RESISTANT) && !stack.isDamageableItem(),
        "The Heart is not one epic, fireproof stack without durability");
    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
        .equals(ResourceLocation.fromNamespaceAndPath("entrelumen", "heart_of_heliodor")), "Wrong Heart ID");
    for (var recipe : level.getRecipeManager().getRecipes()) {
      ItemStack result;
      try {
        result = recipe.value().getResultItem(level.registryAccess());
      } catch (RuntimeException special) {
        continue;
      }
      helper.assertTrue(!result.is(HeliodorHeart.ITEM.get()), "A recipe makes the Heart: " + recipe.id());
    }
    var lines = stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL);
    long lore = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
        && text.getKey().equals("entrelumen.heart_of_heliodor.tooltip")).count();
    helper.assertTrue(lore == 1 && lines.size() == 2, "The tooltip is not the name and one line of lore: " + lines);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void droppedHeartSurvivesDamageAndReturnsFromTheVoid(GameTestHelper helper) {
    var level = helper.getLevel();
    BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
    level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    var dropped = new ItemEntity(level, floor.getX() + 0.5, floor.getY() + 1.2, floor.getZ() + 0.5,
        new ItemStack(HeliodorHeart.ITEM.get()));
    dropped.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    level.addFreshEntity(dropped);
    helper.assertTrue(!dropped.hurt(level.damageSources().cactus(), 100f)
        && !dropped.hurt(level.damageSources().explosion(null, null), 100f)
        && !dropped.hurt(level.damageSources().lava(), 100f) && dropped.isAlive(),
        "A dropped Heart took damage");
    helper.assertTrue(dropped.lifespan == Integer.MAX_VALUE, "A dropped Heart despawns: " + dropped.lifespan);
    // A second Heart that never touched ground, pushed below the world: it waits where it appeared.
    var floating = new ItemEntity(level, floor.getX() + 0.5, floor.getY() + 6.5, floor.getZ() + 2.5,
        new ItemStack(HeliodorHeart.ITEM.get()));
    floating.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    floating.setNoGravity(true); // as over an Aether void: it never finds ground before it falls out
    level.addFreshEntity(floating);
    AtomicReference<BlockPos> ground = new AtomicReference<>();
    AtomicReference<BlockPos> origin = new AtomicReference<>();
    helper.startSequence()
        .thenWaitUntil(() -> {
          var saved = net.minecraft.nbt.NbtUtils.readBlockPos(dropped.getPersistentData(), HeliodorHeart.GROUND);
          var appeared = net.minecraft.nbt.NbtUtils.readBlockPos(floating.getPersistentData(), HeliodorHeart.ORIGIN);
          helper.assertTrue(dropped.onGround() && saved.isPresent() && appeared.isPresent(),
              "The Heart did not record where it rested and appeared");
          ground.set(saved.get());
          origin.set(appeared.get());
        })
        .thenExecute(() -> {
          int below = level.getMinBuildHeight() - 8;
          dropped.teleportTo(dropped.getX(), below, dropped.getZ());
          floating.teleportTo(floating.getX(), below, floating.getZ());
        })
        .thenIdle(2)
        .thenExecute(() -> {
          helper.assertTrue(dropped.isAlive() && dropped.blockPosition().equals(ground.get()) && dropped.isCurrentlyGlowing(),
              "The Heart did not return to the ground it rested on: " + dropped.blockPosition() + " vs " + ground.get());
          helper.assertTrue(floating.isAlive() && floating.blockPosition().equals(origin.get()) && floating.isNoGravity(),
              "A Heart without ground did not wait where it appeared: " + floating.blockPosition());
          dropped.discard();
          floating.discard();
        })
        .thenSucceed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void atlasHandsTheHeartBackOnceOnlyInActSix(GameTestHelper helper) {
    try (var qa = new QaPlayer(helper, "HeartBack")) {
      var player = qa.player;
      var campaign = Entrelumen.current(player);
      campaign.act = CampaignMilestones.ARK_ACT;
      helper.assertTrue(!HeliodorHeart.release(player), "An Atlas without the Heart handed one back");
      campaign.completed.add(HeliodorHeartRules.RECOVERED);
      campaign.completed.add(HeliodorHeartRules.PROJECT);
      helper.assertTrue(HeliodorHeartRules.inAtlas(campaign) && !HeliodorHeart.release(player)
          && player.getInventory().countItem(HeliodorHeart.ITEM.get()) == 0,
          "The Atlas handed the Heart back before act VI");
      campaign.act = Campaigns.FINAL_ACT;
      helper.assertTrue(HeliodorHeart.release(player) && !HeliodorHeart.release(player)
          && player.getInventory().countItem(HeliodorHeart.ITEM.get()) == 1
          && !HeliodorHeartRules.inAtlas(campaign) && campaign.completed.contains(HeliodorHeartRules.RELEASED),
          "The Atlas did not hand the Heart back exactly once in act VI");
      // A Sun Spirit killed afterwards still gives nothing: the Heart was this campaign's one.
      helper.assertTrue(hearts(sunSpiritLoot(helper, player, HeliodorHeart.SUN_SPIRIT_LOOT.location())) == 0,
          "A Sun Spirit gave a second Heart after the first came back");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void arkActIsFiveAndOnlyTheActivationOpensSix(GameTestHelper helper) {
    helper.assertTrue(CampaignMilestones.ARK_ACT == 5 && Campaigns.FINAL_ACT == 6 && Solsticio.ACT == 6
        && Solsticio.GATE.equals(new ProtectionRules.ActGate(6, CampaignMilestones.LAST_HORIZON)),
        "The act constants or Solsticio's gate changed");
    helper.assertTrue(CampaignMilestones.MODULE_IDS.stream().allMatch(id -> Projects.all().get(id).act() == 5)
        && Projects.forAct(6).isEmpty() && Projects.forAct(4).contains(HeliodorHeartRules.PROJECT),
        "The loaded projects are not renumbered");
    try (var qa = new QaPlayer(helper, "ArkActQA")) {
      var player = qa.player;
      var campaign = Entrelumen.current(player);
      campaign.act = CampaignMilestones.ARK_ACT;
      campaign.completed.addAll(Projects.forAct(5));
      campaign.completed.add("end_arrival");
      campaign.arkPhase = CampaignMilestones.PHASE_IDS.size();
      var actor = StructureProtection.actor(player);
      helper.assertTrue(!Solsticio.GATE.satisfiedBy(actor) && Entrelumen.advance(player) == 0
          && campaign.act == CampaignMilestones.ARK_ACT && !AtlasNetwork.handleOpen(player).canAdvance(),
          "The Ark act advanced or opened Solsticio without the activation");
      var key = new ItemStack(Solsticio.LIGHT_KEY.get());
      helper.assertTrue(LightKeyItem.cross(player, key) == key && key.is(Solsticio.LIGHT_KEY.get())
          && player.level() == helper.getLevel(), "A crafted Light Key crossed before the activation");
      helper.assertTrue(CampaignMilestones.finish(campaign) && campaign.act == Campaigns.FINAL_ACT
          && Solsticio.GATE.satisfiedBy(StructureProtection.actor(player)) && Entrelumen.advance(player) == 0,
          "The activation did not open act VI and its gate");
    }
    helper.succeed();
  }
}
