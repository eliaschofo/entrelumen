package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests for the playstyle rules: satiety overflow buffs and story-set World Tiers.
 * Excluded from the distributable jar by the {@code RuntimeGameTests*} pattern. Apotheosis is
 * absent here, so the tier cases run against a stand-in World Tier store and the fixture's
 * Haven/Frontier/Ascent advancements.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsGameplay {
  private RuntimeGameTestsGameplay() {}

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;

    Session(GameTestHelper helper, String name) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
          cookie.gameProfile(), cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      player.removeAllEffects();
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen gameplay QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** Mirrors {@code LivingEntity.completeUsingItem}: last use tick, food applied, then Finish. */
  private static void eat(ServerPlayer player, ItemStack food, boolean withTick) {
    player.setItemInHand(InteractionHand.MAIN_HAND, food);
    if (withTick) EventHooks.onItemUseTick(player, food, 1);
    var copy = food.copy();
    var result = EventHooks.onItemUseFinish(player, copy, 0, food.finishUsingItem(player.level(), player));
    player.setItemInHand(InteractionHand.MAIN_HAND, result);
  }

  private static void full(ServerPlayer player) {
    player.getFoodData().setFoodLevel(20);
    player.getFoodData().setSaturation(20);
  }

  private static CompoundTag satiety(ServerPlayer player) {
    return player.getPersistentData().getCompound(SatietyOverflowEvents.TAG);
  }

  private static Holder<MobEffect> effect(SatietyOverflow.Entry entry) {
    return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
        .getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.MOB_EFFECT,
            entry.effect())).orElseThrow();
  }

  private static SatietyOverflow.Entry only(String effect, int maxLevel) {
    return new SatietyOverflow.Entry(ResourceLocation.withDefaultNamespace(effect), 1, 60, 300, maxLevel);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void satietyOverflowAtAFullBarTurnsSurplusIntoBuffs(GameTestHelper helper) {
    var settings = SatietyOverflowEvents.settings();
    helper.assertTrue(settings.equals(SatietyOverflow.DEFAULTS),
        "The shipped datapack did not load the documented defaults");
    try (var full = new Session(helper, "SatietyFull"); var hungry = new Session(helper, "SatietyHungry")) {
      // Hungry players lose nothing to the caps: no buffs, no glut.
      hungry.player.getFoodData().setFoodLevel(10);
      hungry.player.getFoodData().setSaturation(5);
      eat(hungry.player, new ItemStack(Items.BREAD), true);
      helper.assertTrue(hungry.player.getActiveEffects().isEmpty() && satiety(hungry.player).isEmpty()
          && hungry.player.getFoodData().getFoodLevel() == 15, "A hungry meal produced a buff or glut");

      // Full bar: bread loses 5 hunger + 6 saturation = 11 points -> two level-I buffs at 55 %.
      full(full.player);
      eat(full.player, new ItemStack(Items.BREAD), true);
      var plan = SatietyOverflow.plan(11, settings).orElseThrow();
      var effects = full.player.getActiveEffects();
      helper.assertTrue(effects.size() == 2, "Expected two buffs from 11 surplus points, got " + effects);
      for (MobEffectInstance instance : effects) {
        var entry = settings.pool().stream()
            .filter(e -> instance.getEffect().is(e.effect())).findFirst().orElse(null);
        helper.assertTrue(entry != null, "Granted an effect outside the pool: " + instance);
        helper.assertTrue(instance.getAmplifier() == 0 && instance.isAmbient()
            && instance.getDuration() == SatietyOverflow.durationTicks(entry, plan),
            "Buff level, duration or subtlety off the formula: " + instance);
      }
      helper.assertTrue(full.player.getFoodData().getFoodLevel() == 20
          && Math.abs(satiety(full.player).getDouble("glut") - 11) < 1e-4
          && satiety(full.player).contains("granted_at"), "Glut or cooldown was not recorded");

      // Without the use tick the pre-meal state is unknown, so nothing is guessed.
      hungry.player.removeAllEffects();
      full(hungry.player);
      eat(hungry.player, new ItemStack(Items.BREAD), false);
      helper.assertTrue(hungry.player.getActiveEffects().isEmpty(), "Converted a meal without a snapshot");
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void satietyOverflowCooldownAndGlutStopCheapSpam(GameTestHelper helper) {
    var speedOnly = SatietyOverflow.DEFAULTS.withPool(List.of(only("speed", 2)));
    var previous = SatietyOverflowEvents.swapSettings(speedOnly);
    try (var session = new Session(helper, "SatietySpam")) {
      var player = session.player;
      var speed = effect(speedOnly.pool().getFirst());
      full(player);
      eat(player, new ItemStack(Items.BREAD), true);
      var first = player.getEffect(speed);
      int firstDuration = SatietyOverflow.durationTicks(speedOnly.pool().getFirst(),
          SatietyOverflow.plan(11, speedOnly).orElseThrow());
      helper.assertTrue(first != null && first.getDuration() == firstDuration, "First meal did not grant speed");

      // Within the cooldown a second meal grants nothing but still adds glut.
      full(player);
      eat(player, new ItemStack(Items.BREAD), true);
      helper.assertTrue(player.getEffect(speed).getDuration() == firstDuration
          && Math.abs(satiety(player).getDouble("glut") - 22) < 1e-4, "Cooldown ignored or glut not added");

      // Once the cooldown is over, the same bread is worth 11 x 20 / (20 + 22) points.
      player.removeAllEffects();
      var tag = satiety(player);
      tag.putLong("granted_at", SatietyOverflowEvents.now(player) - speedOnly.cooldownTicks());
      player.getPersistentData().put(SatietyOverflowEvents.TAG, tag);
      full(player);
      eat(player, new ItemStack(Items.BREAD), true);
      double effective = SatietyOverflow.effective(11, 22, speedOnly.glutSoftness());
      int reduced = SatietyOverflow.durationTicks(speedOnly.pool().getFirst(),
          SatietyOverflow.plan(effective, speedOnly).orElseThrow());
      var second = player.getEffect(speed);
      helper.assertTrue(second != null && second.getDuration() == reduced && reduced < firstDuration,
          "Glut did not shorten the next buff: " + second + " expected " + reduced);

      // Glut halves every half-life.
      tag = satiety(player);
      tag.putLong("glut_at", tag.getLong("glut_at") - speedOnly.glutHalfLifeTicks());
      tag.putLong("granted_at", tag.getLong("granted_at") - speedOnly.cooldownTicks());
      player.getPersistentData().put(SatietyOverflowEvents.TAG, tag);
      double glut = tag.getDouble("glut");
      full(player);
      eat(player, new ItemStack(Items.BREAD), true);
      helper.assertTrue(Math.abs(satiety(player).getDouble("glut") - (glut / 2 + 11)) < 1e-4,
          "Glut did not decay by half over one half-life");
    } finally {
      SatietyOverflowEvents.swapSettings(previous);
    }
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void satietyOverflowNeverReplacesStrongerEffects(GameTestHelper helper) {
    var pool = SatietyOverflow.DEFAULTS.withPool(List.of(only("speed", 2), only("haste", 2), only("luck", 2)));
    var previous = SatietyOverflowEvents.swapSettings(pool);
    try (var session = new Session(helper, "SatietyStronger"); var capped = new Session(helper, "SatietyCapped")) {
      var player = session.player;
      // Stronger speed, an infinite (beacon-like) haste and a short luck of the same level.
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 1));
      player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, MobEffectInstance.INFINITE_DURATION, 0));
      player.addEffect(new MobEffectInstance(MobEffects.LUCK, 100, 0));
      full(player);
      eat(player, new ItemStack(Items.BREAD), true);
      var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
      var haste = player.getEffect(MobEffects.DIG_SPEED);
      var luck = player.getEffect(MobEffects.LUCK);
      helper.assertTrue(speed.getAmplifier() == 1 && speed.getDuration() == 6000
          && !((CompoundTag) speed.save()).contains("hidden_effect"), "Stronger speed was replaced or shadowed");
      helper.assertTrue(haste.getAmplifier() == 0 && haste.isInfiniteDuration()
          && !((CompoundTag) haste.save()).contains("hidden_effect"), "Infinite haste was touched");
      int luckDuration = SatietyOverflow.durationTicks(pool.pool().get(2), SatietyOverflow.plan(11, pool).orElseThrow());
      helper.assertTrue(luck.getAmplifier() == 0 && luck.getDuration() == luckDuration,
          "The weaker luck was not extended to " + luckDuration + ": " + luck);

      // When nothing would improve, nothing is granted and no cooldown starts.
      var other = capped.player;
      other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12000, 1));
      other.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 12000, 1));
      other.addEffect(new MobEffectInstance(MobEffects.LUCK, 12000, 1));
      Map<Holder<MobEffect>, Integer> before = new HashMap<>();
      other.getActiveEffects().forEach(e -> before.put(e.getEffect(), e.getDuration()));
      full(other);
      eat(other, new ItemStack(Items.BREAD), true);
      Map<Holder<MobEffect>, Integer> after = new HashMap<>();
      other.getActiveEffects().forEach(e -> after.put(e.getEffect(), e.getDuration()));
      helper.assertTrue(before.equals(after) && !satiety(other).contains("granted_at"),
          "Stronger effects were changed or a cooldown started without a grant");
    } finally {
      SatietyOverflowEvents.swapSettings(previous);
    }
    helper.succeed();
  }

  /** Stand-in for Apotheosis's per-player World Tier attachment. */
  private static final class StandInTiers implements ApotheosisTiers.WorldTierAccess {
    final Map<UUID, ApotheosisTiers.Tier> tiers = new HashMap<>();
    int sets;

    @Override
    public ApotheosisTiers.Tier get(ServerPlayer player) {
      return tiers.getOrDefault(player.getUUID(), ApotheosisTiers.Tier.HAVEN);
    }

    @Override
    public void set(ServerPlayer player, ApotheosisTiers.Tier tier) {
      tiers.put(player.getUUID(), tier);
      sets++;
    }
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void storyTierFollowsTheCampaignAndCannotBeChosen(GameTestHelper helper) throws Exception {
    var stand = new StandInTiers();
    var previous = ApotheosisTiers.swapAccess(stand);
    try (var founderSession = new Session(helper, "StoryFounder"); var guestSession = new Session(helper, "StoryGuest")) {
      var founder = founderSession.player;
      var guest = guestSession.player;
      var campaign = Entrelumen.current(founder);
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.get(founder) == ApotheosisTiers.Tier.HAVEN && stand.sets == 0,
          "Act I set a tier other than Haven");
      campaign.act = 3;
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.get(founder) == ApotheosisTiers.Tier.FRONTIER && stand.sets == 1,
          "Closing Act II did not move the tier to Frontier");
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.sets == 1, "Replay set the tier again");
      // A tier picked behind the story's back, up or down, is put back.
      stand.set(founder, ApotheosisTiers.Tier.PINNACLE);
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.get(founder) == ApotheosisTiers.Tier.FRONTIER, "A higher chosen tier survived");
      stand.set(founder, ApotheosisTiers.Tier.HAVEN);
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.get(founder) == ApotheosisTiers.Tier.FRONTIER, "A lower chosen tier survived");
      // The fixture has no Summit or Pinnacle: Act VI with the Ark stays on the highest real tier.
      campaign.act = 6;
      campaign.completed.add(CampaignMilestones.LAST_HORIZON);
      ApotheosisTiers.sync(founder);
      helper.assertTrue(stand.get(founder) == ApotheosisTiers.Tier.ASCENT, "Missing tiers were forced");

      // Joining a party imposes the team's tier; leaving returns to the personal campaign's tier.
      var team = (dev.ftb.mods.ftbteams.data.PartyTeam) FTBTeamsAPI.api().getManager()
          .createPartyTeam(founder, "Story " + founder.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
      try {
        team.invite(founder, List.of(guest.getGameProfile()));
        team.join(guest);
        ApotheosisTiers.sync(guest);
        helper.assertTrue(stand.get(guest) == ApotheosisTiers.Tier.ASCENT, "Joining member kept their own tier");
        int sets = stand.sets;
        ApotheosisTiers.sync(guest);
        helper.assertTrue(stand.sets == sets, "Reconnect-style replay set the tier again");
        team.leave(guest.getUUID());
        ApotheosisTiers.sync(guest);
        helper.assertTrue(stand.get(guest) == ApotheosisTiers.Tier.HAVEN,
            "Leaving the party kept the party's tier");
      } finally {
        team.leave(founder.getUUID());
      }

      // Without Apotheosis (no access) sync still grants advancements and never fails.
      ApotheosisTiers.swapAccess(null);
      ApotheosisTiers.sync(founder);
    } finally {
      ApotheosisTiers.swapAccess(previous);
    }
    helper.succeed();
  }
}
