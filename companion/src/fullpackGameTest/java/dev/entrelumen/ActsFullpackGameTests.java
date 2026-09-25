package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Installed-pack QA of the act renumbering of 24 September 2026 with the real Aether 1.5.10: a
 * real Sun Spirit dies to a player and drops the Heart of Heliodor once per campaign; the Heart
 * closes act IV through the Atlas; act V plays on Summit; the compass guides to the gold dungeon in
 * act IV and to Solsticio in act VI.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ActsFullpackGameTests {
  private ActsFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("aether", "apotheosis"))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final EmbeddedChannel channel;

    Session(GameTestHelper helper, String name) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          cookie.gameProfile(), cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new EmbeddedChannel(connection);
      try {
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
      } catch (RuntimeException | Error failure) {
        close();
        throw failure;
      }
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen acts QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static int command(ServerPlayer player, String command) {
    try {
      return player.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
    } catch (Exception e) {
      throw new IllegalStateException("Player command failed: " + command, e);
    }
  }

  /** Spawns a real Sun Spirit at {@code at} and has {@code killer} deal the killing blow. */
  private static LivingEntity killSunSpirit(GameTestHelper helper, ServerPlayer killer, BlockPos at) {
    var level = helper.getLevel();
    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(HeliodorHeart.SUN_SPIRIT).orElseThrow(
        () -> new IllegalStateException("aether:sun_spirit is not registered"));
    Entity created = type.create(level);
    helper.assertTrue(created instanceof LivingEntity, "aether:sun_spirit is not a living entity");
    var spirit = (LivingEntity) created;
    spirit.moveTo(Vec3.atBottomCenterOf(at));
    level.addFreshEntity(spirit);
    helper.assertTrue(spirit.getLootTable().equals(HeliodorHeart.SUN_SPIRIT_LOOT),
        "The Sun Spirit's loot table is not " + HeliodorHeart.SUN_SPIRIT_LOOT.location() + ": " + spirit.getLootTable());
    // Only its own ice crystals wound it in play; a player-owned kill bypasses that, as a reflected crystal would.
    spirit.hurt(level.damageSources().source(DamageTypes.GENERIC_KILL, killer), Float.MAX_VALUE);
    helper.assertTrue(!spirit.isAlive(), "The Sun Spirit did not die");
    return spirit;
  }

  private static List<ItemEntity> drops(GameTestHelper helper, BlockPos at) {
    return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4));
  }

  private static long count(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
    return drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void sunSpiritDropsTheHeartOnceAndItClosesActFour(GameTestHelper helper) {
    requireSuite();
    var level = helper.getLevel();
    BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
    for (BlockPos pos : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, -1, 3)))
      level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    var goldKey = BuiltInRegistries.ITEM.get(ResourceLocation.parse("aether:gold_dungeon_key"));
    try (var session = new Session(helper, "SunSpiritQA")) {
      var player = session.player;
      var campaign = Entrelumen.current(player);
      campaign.act = 4;
      for (int act = 1; act <= 3; act++) campaign.completed.addAll(Projects.forAct(act));
      campaign.completed.addAll(List.of("spectral_archive", "horizon_survey", "pollinator_treaty", "sealed_memory",
          "aether_arrival", "twilight_arrival", "bumblezone_arrival"));

      killSunSpirit(helper, player, at);
      var first = drops(helper, at);
      helper.assertTrue(count(first, HeliodorHeart.ITEM.get()) == 1 && count(first, goldKey) >= 1,
          "The first Sun Spirit did not drop exactly one Heart beside its gold dungeon key: "
              + first.stream().map(e -> e.getItem().toString()).toList());
      helper.assertTrue(campaign.completed.contains(HeliodorHeartRules.RECOVERED), "The campaign did not record the Heart");
      first.forEach(Entity::discard);

      killSunSpirit(helper, player, at);
      var second = drops(helper, at);
      helper.assertTrue(count(second, HeliodorHeart.ITEM.get()) == 0 && count(second, goldKey) >= 1,
          "A second Sun Spirit dropped another Heart for the same campaign");
      second.forEach(Entity::discard);

      // The Atlas: without the Heart the act cannot close; with it, act IV closes and act V plays on Summit.
      player.getInventory().add(new ItemStack(Items.PAPER, 3));
      player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 1));
      helper.assertTrue(command(player, "entrelumen deliver atlas_voices") == 0 && campaign.act == 4,
          "The Voices of the Atlas closed without the Heart");
      player.getInventory().add(new ItemStack(HeliodorHeart.ITEM.get()));
      helper.assertTrue(command(player, "entrelumen deliver " + HeliodorHeartRules.PROJECT) == 1
          && player.getInventory().countItem(HeliodorHeart.ITEM.get()) == 0 && HeliodorHeartRules.inAtlas(campaign),
          "The Heart was not delivered into the Atlas");
      helper.assertTrue(command(player, "entrelumen deliver atlas_voices") == 1
          && command(player, "entrelumen advance") == 1 && campaign.act == CampaignMilestones.ARK_ACT,
          "Act IV did not close with the Heart in the Atlas");
      ApotheosisTiers.sync(player);
      helper.assertTrue("SUMMIT".equals(player.getData(ApotheosisContent.STORY_TIER)),
          "Act V, the Ark, does not play on Summit: " + player.getData(ApotheosisContent.STORY_TIER));
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void compassGuidesToTheSunSpiritInActFourAndToSolsticioInActSix(GameTestHelper helper) {
    requireSuite();
    var objectives = CompassTargets.active();
    var ids = objectives.stream().map(CompassTargets.Objective::id).toList();
    helper.assertTrue(ids.contains("gold_dungeon") && ids.contains("solsticio"), "Objectives missing: " + ids);
    var gold = objectives.get(ids.indexOf("gold_dungeon"));
    helper.assertTrue(gold.act() == 4 && gold.advanceWhen().type() == CompassTargets.ConditionType.MILESTONE
        && gold.advanceWhen().value().equals(HeliodorHeartRules.RECOVERED), "The gold dungeon is not act IV's Heart");
    helper.assertTrue(objectives.get(ids.indexOf("silver_dungeon")).act() == 4
        && objectives.get(ids.indexOf("the_end")).act() == 5 && objectives.get(ids.indexOf("solsticio")).act() == 6
        && objectives.getLast().act() == 6, "Compass acts not renumbered: " + objectives);
    // Everything before the gold dungeon done, the Heart missing: act IV points at the Sun Spirit.
    java.util.Set<String> before = new java.util.HashSet<>(ids.subList(0, ids.indexOf("gold_dungeon")));
    var waiting = CompassProgress.evaluate(objectives, 4, before, condition -> false);
    helper.assertTrue(!waiting.lockedByAct() && waiting.current().id().equals("gold_dungeon"),
        "Act IV does not point at the gold dungeon: " + waiting);
    var recovered = CompassProgress.evaluate(objectives, 4, before,
        condition -> condition.value().equals(HeliodorHeartRules.RECOVERED));
    helper.assertTrue(recovered.newlyReached().contains("gold_dungeon") && recovered.lockedByAct()
        && recovered.current().act() == 5, "The Heart did not move the compass on to act V: " + recovered);
    java.util.Set<String> all = new java.util.HashSet<>(ids.subList(0, ids.indexOf("solsticio")));
    var sixth = CompassProgress.evaluate(objectives, 6, all, condition -> false);
    helper.assertTrue(!sixth.lockedByAct() && sixth.current().id().equals("solsticio"),
        "Act VI does not point at Solsticio: " + sixth);
    helper.succeed();
  }
}
