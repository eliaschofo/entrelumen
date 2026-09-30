package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests of the Sour Shackle on a server without Curios: the QA players are made wearers
 * through {@link SourShackle#wearing}, and every mark comes from a real {@code Player.attack}, so the
 * attack and damage events are the ones the game fires. Each case has its own batch, so no other test's
 * mobs stand within a burst. Excluded from the distributable jar by the {@code RuntimeGameTests*} pattern.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsSourShackle {
  private RuntimeGameTestsSourShackle() {}

  /** The players these tests make wearers. */
  private static final Set<UUID> WEARERS = ConcurrentHashMap.newKeySet();

  private static final class Qa implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;
    final List<Entity> spawned = new ArrayList<>();

    Qa(GameTestHelper helper, String name, boolean wearer) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      Vec3 at = helper.absoluteVec(new Vec3(-2.5, 2, 0.5));
      player.teleportTo(at.x, at.y, at.z);
      if (wearer) {
        SourShackle.wearing = p -> WEARERS.contains(p.getUUID()) || CuriosCompat.equipped(p, SourShackle.ITEM.get());
        WEARERS.add(player.getUUID());
      }
    }

    /** A still, sturdy mob at a relative position: no AI, no gravity, no knockback, 100 health. */
    <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z) {
      T mob = helper.spawnWithNoFreeWill(type, new Vec3(x, 2, z));
      mob.setNoGravity(true);
      var health = mob.getAttribute(Attributes.MAX_HEALTH);
      if (health != null) health.setBaseValue(100);
      var knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
      if (knockback != null) knockback.setBaseValue(1);
      mob.setHealth(mob.getMaxHealth());
      Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
      mob.moveTo(at.x, at.y, at.z, 0, 0);
      spawned.add(mob);
      return mob;
    }

    /** A melee hit that lands: the target's invulnerability frames are cleared first. */
    void hit(LivingEntity target) {
      target.invulnerableTime = 0;
      player.attack(target);
    }

    int marks() {
      return SourShackle.wearer(player).marks();
    }

    @Override
    public void close() {
      WEARERS.remove(player.getUUID());
      for (Entity e : spawned) e.discard();
      try {
        connection.disconnect(Component.literal("Sour Shackle QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void requireNoCurios(GameTestHelper helper) {
    helper.assertTrue(!CuriosCompat.loaded(), "This isolated suite expects a server without Curios");
  }

  // ---- Marks ---------------------------------------------------------------------------------

  /** Hits on two different mobs add to one count; the fifth, on either, bursts and empties it. */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "sour_shackle_marks")
  public static void marksAccumulateAcrossTwoMobs(GameTestHelper helper) {
    requireNoCurios(helper);
    var qa = new Qa(helper, "ShackleMarks", true);
    try {
      var a = qa.mob(helper, EntityType.PIG, 0.5, 0.5);
      var b = qa.mob(helper, EntityType.PIG, 0.5, 12.5);
      qa.hit(a);
      qa.hit(b);
      qa.hit(a);
      qa.hit(b);
      helper.assertTrue(qa.marks() == 4, "four hits on two mobs: " + qa.marks() + " marks");
      helper.assertTrue(a.getHealth() > 95 && b.getHealth() > 95, "no burst before the fifth mark");
      float before = b.getHealth();
      qa.hit(b);
      helper.assertTrue(qa.marks() == 0, "the fifth mark empties the count: " + qa.marks());
      helper.assertTrue(before - b.getHealth() >= 15, "the fifth hit bursts on its target: " + (before - b.getHealth()));
      helper.assertTrue(SourShackle.wearer(qa.player).cooling(helper.getLevel().getGameTime()), "the cooldown starts");
      // A hit that is not the wearer's own swing (a sweep, thorns, another source) never marks.
      a.hurt(helper.getLevel().damageSources().playerAttack(qa.player), 1);
      helper.assertTrue(qa.marks() == 0, "damage outside a swing marked");
    } finally {
      qa.close();
    }
    helper.succeed();
  }

  /** Someone without the shackle marks nothing. */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "sour_shackle_unworn")
  public static void withoutTheShackleNothingIsMarked(GameTestHelper helper) {
    var qa = new Qa(helper, "ShackleNone", false);
    try {
      var a = qa.mob(helper, EntityType.PIG, 0.5, 0.5);
      for (int i = 0; i < 6; i++) qa.hit(a);
      helper.assertTrue(!qa.player.hasData(SourShackle.WEARER.get()), "a player without the shackle got marks");
      helper.assertTrue(a.getHealth() > 95, "a burst without the shackle");
    } finally {
      qa.close();
    }
    helper.succeed();
  }

  // ---- The burst -----------------------------------------------------------------------------

  /**
   * 15 to every living thing within 5 blocks of the target, armour or not, and nothing to what stands
   * past 5, to the wearer or to a tamed pet.
   */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "sour_shackle_radius")
  public static void theBurstDeals15WithinFiveBlocksAndNotBeyond(GameTestHelper helper) {
    requireNoCurios(helper);
    var qa = new Qa(helper, "ShackleRadius", true);
    try {
      helper.assertTrue(SourShackle.burstTagged(helper.getLevel()), "sour_burst must bypass armour and invulnerability frames");
      var target = qa.mob(helper, EntityType.PIG, 0.5, 0.5);
      var near = qa.mob(helper, EntityType.PIG, 5.0, 0.5);          // 4.5 blocks from the target's feet
      var armoured = qa.mob(helper, EntityType.PIG, 0.5, 4.5);      // 4.0 blocks, with 20 armour and 8 toughness
      armoured.getAttribute(Attributes.ARMOR).setBaseValue(20);
      armoured.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
      var edge = qa.mob(helper, EntityType.PIG, 0.5, -4.5);         // exactly 5.0 blocks
      var far = qa.mob(helper, EntityType.PIG, 6.0, 0.5);           // 5.5 blocks
      var pet = qa.mob(helper, EntityType.WOLF, 0.5, 2.5);
      pet.tame(qa.player);
      for (int i = 0; i < 4; i++) qa.hit(target);
      float t0 = target.getHealth(), n0 = near.getHealth(), a0 = armoured.getHealth(), e0 = edge.getHealth(),
          f0 = far.getHealth(), p0 = pet.getHealth(), w0 = qa.player.getHealth();
      qa.hit(target);
      helper.assertTrue(n0 - near.getHealth() == 15F, "4.5 blocks away took " + (n0 - near.getHealth()));
      helper.assertTrue(a0 - armoured.getHealth() == 15F, "armour must not soften it: " + (a0 - armoured.getHealth()));
      helper.assertTrue(e0 - edge.getHealth() == 15F, "at exactly 5 blocks it took " + (e0 - edge.getHealth()));
      helper.assertTrue(f0 == far.getHealth(), "5.5 blocks away took " + (f0 - far.getHealth()));
      helper.assertTrue(p0 == pet.getHealth(), "a tamed pet took " + (p0 - pet.getHealth()));
      helper.assertTrue(w0 == qa.player.getHealth(), "the wearer took " + (w0 - qa.player.getHealth()));
      helper.assertTrue(t0 - target.getHealth() >= 15F, "the target took " + (t0 - target.getHealth()));
    } finally {
      qa.close();
    }
    helper.succeed();
  }

  /** 300% against a {@code c:bosses} entity: the Sour Light itself, 45 through its armour. */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "sour_shackle_boss")
  public static void theBurstDeals45ToABoss(GameTestHelper helper) {
    requireNoCurios(helper);
    var qa = new Qa(helper, "ShackleBoss", true);
    try {
      var target = qa.mob(helper, EntityType.PIG, 0.5, 0.5);
      WhiteWither boss = qa.mob(helper, EnvesContent.WHITE_WITHER.get(), 3.5, 0.5);
      boss.phase(WhiteWither.Phase.FIGHT);
      helper.assertTrue(boss.getType().is(Tags.EntityTypes.BOSSES), "the Sour Light must be in c:bosses");
      helper.assertTrue(boss.getArmorValue() > 0, "the test wants an armoured boss");
      for (int i = 0; i < 4; i++) qa.hit(target);
      float b0 = boss.getHealth(), t0 = target.getHealth();
      qa.hit(target);
      helper.assertTrue(b0 - boss.getHealth() == 45F, "the boss took " + (b0 - boss.getHealth()));
      helper.assertTrue(t0 - target.getHealth() < 45F, "a common mob took the boss's share");
    } finally {
      qa.close();
    }
    helper.succeed();
  }

  // ---- The cooldown --------------------------------------------------------------------------

  /** After a burst, hits add no marks for 8 s (160 ticks); the first hit after that marks again. */
  @GameTest(template = "empty", timeoutTicks = 400, batch = "sour_shackle_cooldown")
  public static void theCooldownBlocksMarksForEightSeconds(GameTestHelper helper) {
    requireNoCurios(helper);
    var qa = new Qa(helper, "ShackleCooldown", true);
    var a = qa.mob(helper, EntityType.PIG, 0.5, 0.5);
    var b = qa.mob(helper, EntityType.PIG, 0.5, 12.5);
    for (int i = 0; i < 5; i++) qa.hit(i % 2 == 0 ? a : b);
    long burst = helper.getLevel().getGameTime();
    if (qa.marks() != 0 || !SourShackle.wearer(qa.player).cooling(burst)) {
      qa.close();
      helper.fail("no burst to start the cooldown");
      return;
    }
    for (int delay : new int[] {20, 80, 150, 158}) {
      helper.runAfterDelay(delay, () -> {
        long elapsed = helper.getLevel().getGameTime() - burst;
        if (elapsed >= SourShackleRules.COOLDOWN_TICKS) return;   // the scheduler slipped past the window; nothing to assert
        qa.hit(a);
        qa.hit(b);
        if (qa.marks() != 0) {
          qa.close();
          helper.fail("a hit " + elapsed + " ticks after the burst marked: " + qa.marks());
        }
      });
    }
    helper.runAfterDelay(170, () -> {
      long elapsed = helper.getLevel().getGameTime() - burst;
      try {
        helper.assertTrue(elapsed >= SourShackleRules.COOLDOWN_TICKS, "checked too early: " + elapsed);
        helper.assertTrue(!SourShackle.wearer(qa.player).cooling(helper.getLevel().getGameTime()), "still cooling at " + elapsed);
        qa.hit(a);
        helper.assertTrue(qa.marks() == 1, "the first hit after the cooldown: " + qa.marks() + " marks");
      } finally {
        qa.close();
      }
      helper.succeed();
    });
  }

  // ---- The drop ------------------------------------------------------------------------------

  private static List<ItemStack> rollBossChest(GameTestHelper helper, ServerPlayer opener) {
    LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(
        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/boss")));
    helper.assertTrue(table != LootTable.EMPTY, "entrelumen:enves/boss did not load");
    // What Lootr passes when it fills a chest for one player: the origin, their luck and them.
    var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, opener.position())
        .withParameter(LootContextParams.THIS_ENTITY, opener).withLuck(opener.getLuck()).create(LootContextParamSets.CHEST);
    return table.getRandomItems(params);
  }

  private static int count(List<ItemStack> items, net.minecraft.world.item.Item item) {
    return items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  /**
   * Each player's first boss chest gives the shackle; every later one gives 64 shards instead, even
   * when the first shackle was thrown away, and a second player still gets their own.
   */
  @GameTest(template = "empty", timeoutTicks = 100, batch = "sour_shackle_drop")
  public static void theFirstDropIsTheShackleAndARepeatIs64Shards(GameTestHelper helper) {
    var first = new Qa(helper, "ShackleFirst", false);
    var second = new Qa(helper, "ShackleSecond", false);
    try {
      var shackle = SourShackle.ITEM.get();
      var shard = EnvesContent.SOUR_LIGHT_SHARD.get();
      helper.assertTrue(!SourShackle.owned(first.player), "a new player owns no shackle");
      var one = rollBossChest(helper, first.player);
      helper.assertTrue(count(one, shackle) == 1, "the first boss chest must give the shackle: " + one);
      int base = count(one, shard);
      helper.assertTrue(base >= 12 && base <= 16, "the chest's own shards: " + base);
      helper.assertTrue(SourShackle.owned(first.player), "rolling it marks the player as an owner");
      first.player.getInventory().clearContent();   // thrown away, or left in a chest
      for (int repeat = 0; repeat < 3; repeat++) {
        var again = rollBossChest(helper, first.player);
        int shards = count(again, shard);
        helper.assertTrue(count(again, shackle) == 0, "a repeat gave another shackle: " + again);
        helper.assertTrue(shards >= 12 + SourShackleRules.REPEAT_SHARDS && shards <= 16 + SourShackleRules.REPEAT_SHARDS,
            "a repeat must add 64 shards: " + shards);
        helper.assertTrue(again.stream().anyMatch(s -> s.is(shard) && s.getCount() == SourShackleRules.REPEAT_SHARDS),
            "the 64 shards come as their own stack");
      }
      var theirs = rollBossChest(helper, second.player);
      helper.assertTrue(count(theirs, shackle) == 1, "another player's first chest gives them a shackle too");
      // The flag survives death: NeoForge copies death-proof attachments to the respawned body this way.
      var reborn = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), first.player.getGameProfile(),
          net.minecraft.server.level.ClientInformation.createDefault());
      net.neoforged.neoforge.attachment.AttachmentInternals.copyEntityAttachments(first.player, reborn, true);
      helper.assertTrue(SourShackle.owned(reborn), "ownership must survive death");
      helper.assertTrue(count(rollBossChest(helper, reborn), shackle) == 0, "a respawned owner got another shackle");
    } finally {
      first.close();
      second.close();
    }
    helper.succeed();
  }
}
