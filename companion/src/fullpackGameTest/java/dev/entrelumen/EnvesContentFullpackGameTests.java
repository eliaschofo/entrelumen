package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Role;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.Placement;
import dev.entrelumen.EnvesData.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * QA-JAR-only checks of the Envés's content with the whole pack: every echo of the tables is a live
 * mob of its mod, a floor I fight fills with Cataclysm's draugr (not their vanilla fallbacks) that
 * Apotheosis's tier augments leave alone, the chests give Apotheosis affix gear and gems at the
 * attempt's rarity, and the boss chest that rises when the White Wither falls is Lootr's.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class EnvesContentFullpackGameTests {
  private EnvesContentFullpackGameTests() {}

  private static final class QaPlayer implements AutoCloseable {
    final ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
      connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
    }

    @Override
    public void close() {
      try {
        Enves.forget(player);
        EnvesGuard.forget(player);
        connection.disconnect(net.minecraft.network.chat.Component.literal("Envés content full-pack QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  static DataComponentType<?> component(String id) {
    return BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id));
  }

  /** The rarity id an Apotheosis affix item carries ({@code apotheosis:rarity} holds a Placebo DynamicHolder). */
  static String rarity(ItemStack stack) {
    Object holder = stack.get(component("apotheosis:rarity"));
    if (holder == null) return "";
    try {
      return String.valueOf(holder.getClass().getMethod("getId").invoke(holder));
    } catch (ReflectiveOperationException e) {
      return holder.toString();
    }
  }

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_fullpack_content")
  public static void echoesAreThePacksMobsAndChestsGiveApotheosisGear(GameTestHelper helper) {
    // Every echo of every table is a live entity of its mod, and a mob.
    for (var table : EnvesContentConfig.tables().entrySet())
      for (var entry : table.getValue().all()) {
        var id = ResourceLocation.parse(entry.entity());
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), table.getKey() + ": " + id + " is not in the pack");
        var made = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
        helper.assertTrue(made instanceof Mob, id + " is no mob");
        made.discard();
      }
    helper.assertTrue(EnvesLoot.apotheosis(), "Apotheosis's loot API did not resolve");
    helper.assertTrue(NeoForgeRegistries.ATTACHMENT_TYPES.containsKey(EnvesEchoes.APOTHEOSIS_AUGMENTED), "Apotheosis's augment marker moved");
    var qa = new QaPlayer(helper, "EchoPack");
    ServerPlayer player = qa.player;
    Entrelumen.current(player).act = ApotheosisTiers.FRONTIER_ACT;
    long seed = 1;
    while (EnvesLayout.descent(seed).floor(1).withRole(EnvesLayout.Role.FIGHT).isEmpty()) seed++;
    Attempt attempt = Enves.create(player, Tier.FRONTIER, seed);
    AtomicInteger stage = new AtomicInteger();
    helper.onEachTick(() -> {
      try {
        switch (stage.get()) {
          case 0 -> {
            if (attempt.status != Status.OPEN) return;
            helper.assertTrue(Enves.enter(player), "could not enter");
            player.hasChangedDimension();
            player.setInvulnerable(true);
            int cell = Enves.layout(attempt, 1).withRole(EnvesLayout.Role.FIGHT).getFirst();
            BlockPos spot = EnvesEchoes.safeSpot(player.serverLevel(), Enves.origin(attempt, 1, cell).offset(EnvesGeometry.C, 1, EnvesGeometry.C), 6);
            Enves.move(player, player.serverLevel(), Vec3.atBottomCenterOf(spot), 0f);
            stage.set(1);
          }
          case 1 -> {
            var echoes = EnvesEchoes.of(attempt.id);
            if (echoes.size() < 2) return;
            int draugr = 0;
            for (Mob mob : echoes) {
              var data = EnvesEchoes.data(mob).orElseThrow();
              String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
              if (data.roleOf() == Role.ELITE) {
                helper.assertTrue(type.equals("cataclysm:draugr") || type.equals("cataclysm:elite_draugr"),
                    "a floor I elite is a " + type + ", not a Cataclysm draugr");
                draugr++;
              }
              @SuppressWarnings("unchecked")
              var augmented = (net.neoforged.neoforge.attachment.AttachmentType<Boolean>) NeoForgeRegistries.ATTACHMENT_TYPES
                  .get(EnvesEchoes.APOTHEOSIS_AUGMENTED);
              helper.assertTrue(Boolean.TRUE.equals(mob.getData(augmented)), "Apotheosis may still augment " + type);
            }
            helper.assertTrue(draugr >= 1, "no draugr echo");
            ServerLevel level = player.serverLevel();
            Vec3 origin = Vec3.atCenterOf(Enves.arrival(player.server, attempt, 1));
            var boss = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/boss")));
            var items = boss.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.CHEST));
            List<ItemStack> gear = new ArrayList<>(), gems = new ArrayList<>();
            for (ItemStack stack : items) {
              if (stack.has(component("apotheosis:affixes"))) gear.add(stack);
              if (stack.has(component("apotheosis:gem"))) gems.add(stack);
            }
            helper.assertTrue(gear.size() == 3, "the boss chest gave " + gear.size() + " affix pieces: " + items);
            for (ItemStack piece : gear)
              helper.assertTrue(rarity(piece).equals("apotheosis:epic"), "Frontier's boss chest gives epic pieces, not " + rarity(piece));
            helper.assertTrue(gems.size() == 2, "the boss chest gave " + gems.size() + " gems");
            Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
            qa.close();
            stage.set(2);
            helper.succeed();
          }
          default -> {}
        }
      } catch (RuntimeException | Error failure) {
        if (attempt.live()) Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
        if (stage.get() < 2) qa.close();
        stage.set(2);
        throw failure;
      }
    });
  }

  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_fullpack_content")
  public static void theBossChestThatRisesIsLootrs(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EclipsePack");
    ServerPlayer player = qa.player;
    Entrelumen.current(player).act = ApotheosisTiers.FRONTIER_ACT;
    Attempt attempt = Enves.create(player, Tier.FRONTIER, 555);
    AtomicInteger stage = new AtomicInteger();
    AtomicReference<BlockPos> chest = new AtomicReference<>();
    helper.onEachTick(() -> {
      try {
        switch (stage.get()) {
          case 0 -> {
            if (attempt.status != Status.OPEN) return;
            helper.assertTrue(Enves.enter(player), "could not enter");
            player.hasChangedDimension();
            player.setInvulnerable(true);
            EnvesPlacer.queue(player.server, attempt, EnvesLayout.BOSS_DEPTH);
            stage.set(1);
          }
          case 1 -> {
            if (attempt.floor(EnvesLayout.BOSS_DEPTH).placement != Placement.READY) return;
            var floor = Enves.floor(player.serverLevel(), attempt, EnvesLayout.BOSS_DEPTH);
            chest.set(floor.markers(floor.layout().exit()).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.CHEST)
                .findFirst().orElseThrow().pos());
            int center = floor.layout().withRole(EnvesLayout.Role.ARENA_CENTER).getFirst();
            BlockPos spot = EnvesEchoes.safeSpot(player.serverLevel(), floor.origin(center).offset(EnvesGeometry.C + 3, 1, EnvesGeometry.C), 4);
            Enves.move(player, player.serverLevel(), Vec3.atBottomCenterOf(spot), 0f);
            stage.set(2);
          }
          case 2 -> {
            var run = EnvesRuns.get(player.server).run(attempt.id);
            if (!run.bossSpawned) return;
            var boss = player.serverLevel().getEntity(run.boss);
            helper.assertTrue(boss instanceof WhiteWither, "no White Wither");
            ((WhiteWither) boss).kill();
            stage.set(3);
          }
          case 3 -> {
            if (!attempt.bossDefeated) return;
            var state = player.serverLevel().getBlockState(chest.get());
            helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("lootr:lootr_chest"),
                "the boss chest is not Lootr's: " + state);
            helper.assertTrue(player.serverLevel().getBlockEntity(chest.get()) instanceof RandomizableContainer container
                && container.getLootTable() != null && container.getLootTable().location().toString().equals("entrelumen:enves/boss"),
                "the boss chest has no boss loot table");
            Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
            qa.close();
            stage.set(4);
            helper.succeed();
          }
          default -> {}
        }
      } catch (RuntimeException | Error failure) {
        if (attempt.live()) Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
        if (stage.get() < 4) qa.close();
        stage.set(4);
        throw failure;
      }
    });
  }
}
