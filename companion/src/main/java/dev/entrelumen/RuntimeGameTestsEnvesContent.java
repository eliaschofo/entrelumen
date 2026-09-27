package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import dev.entrelumen.ApotheosisTiers.Tier;
import dev.entrelumen.EnvesBalance.Role;
import dev.entrelumen.EnvesData.Attempt;
import dev.entrelumen.EnvesData.Placement;
import dev.entrelumen.EnvesData.Status;
import dev.entrelumen.EnvesPuzzleRules.Blessing;
import dev.entrelumen.EnvesPuzzleRules.SealVariant;
import dev.entrelumen.EnvesPuzzleRules.VaultPuzzle;
import dev.entrelumen.EnvesRuns.Puzzle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Isolated GameTests of the Envés's content (excluded from the distributable jar): echoes filling a
 * room at the attempt's scale with their own drops, each elite affix, the shrines' blessings, the three
 * seal variants, every vault lock, the stair's champion, the gate's two offerings, the loot tables, and
 * the White Wither (no griefing, low flight, a told charge, the defeat hook and its chest). The pack's
 * mods are absent here, so echoes are their vanilla fallbacks and loot its plain fallback; the full-pack
 * GameTests check the real mobs and Apotheosis items.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class RuntimeGameTestsEnvesContent {
  private RuntimeGameTestsEnvesContent() {}

  static final class QaPlayer implements AutoCloseable {
    ServerPlayer player;
    final net.minecraft.network.Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    QaPlayer(GameTestHelper helper, String name) {
      var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
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

    @Override
    public void close() {
      try {
        Enves.forget(player);
        EnvesGuard.forget(player);
        connection.disconnect(net.minecraft.network.chat.Component.literal("Envés content QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void frontier(ServerPlayer player) {
    Entrelumen.current(player).act = ApotheosisTiers.FRONTIER_ACT;
    CampaignData.get(player.server).setDirty();
  }

  /** An attempt of the player's team at Frontier with a chosen seed (no offering: the admin path). */
  private static Attempt attempt(ServerPlayer player, long seed) {
    frontier(player);
    return Enves.create(player, Tier.FRONTIER, seed);
  }

  private static long seedWith(java.util.function.LongPredicate wanted) {
    for (long seed = 1; seed < 100_000; seed++) if (wanted.test(seed)) return seed;
    throw new IllegalStateException("no seed");
  }

  /** A little script of steps, one per tick when its condition holds. */
  private static final class Script {
    final GameTestHelper helper;
    final List<BooleanSupplier> waits = new ArrayList<>();
    final List<Runnable> steps = new ArrayList<>();
    final Runnable cleanup;
    int at;

    Script(GameTestHelper helper, Runnable cleanup) {
      this.helper = helper;
      this.cleanup = cleanup;
    }

    Script then(BooleanSupplier ready, Runnable step) {
      waits.add(ready);
      steps.add(step);
      return this;
    }

    Script then(Runnable step) {
      return then(() -> true, step);
    }

    void run() {
      AtomicBoolean done = new AtomicBoolean();
      helper.onEachTick(() -> {
        if (done.get()) return;
        try {
          while (at < steps.size() && waits.get(at).getAsBoolean()) steps.get(at++).run();
          if (at >= steps.size()) {
            done.set(true);
            cleanup.run();
            helper.succeed();
          }
        } catch (RuntimeException | Error failure) {
          done.set(true);
          try {
            cleanup.run();
          } catch (RuntimeException | Error ignored) {
            // the first failure is the one to report
          }
          throw failure;
        }
      });
    }
  }

  private static void end(ServerPlayer player, Attempt attempt) {
    if (attempt != null && attempt.live()) Enves.end(player.server, attempt, EnvesHooks.EndReason.ADMIN);
  }

  private static void moveTo(ServerPlayer player, BlockPos feet) {
    Enves.move(player, Enves.level(player.server), Vec3.atBottomCenterOf(feet), Enves.ARRIVAL_YAW);
    player.hasChangedDimension();
  }

  private static List<Mob> echoes(Attempt attempt) {
    return EnvesEchoes.of(attempt.id).stream().filter(Mob::isAlive).toList();
  }

  private static String key(Mob mob) {
    return mob.getCustomName() != null && mob.getCustomName().getContents() instanceof TranslatableContents t ? t.getKey() : "";
  }

  // ---- Encounters ---------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_content")
  public static void aFightRoomFillsWithScaledEchoesThatDropOnlyTheirOwnLoot(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EchoFight");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, seedWith(s -> !EnvesLayout.descent(s).floor(1).withRole(EnvesLayout.Role.FIGHT).isEmpty()));
    AtomicReference<List<Mob>> group = new AtomicReference<>();
    AtomicReference<Vec3> fell = new AtomicReference<>();
    new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    }).then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      player.setInvulnerable(true);
      int cell = Enves.layout(attempt, 1).withRole(EnvesLayout.Role.FIGHT).getFirst();
      moveTo(player, EnvesEchoes.safeSpot(player.serverLevel(), Enves.origin(attempt, 1, cell).offset(EnvesGeometry.C, 1, EnvesGeometry.C), 6));
    }).then(() -> echoes(attempt).size() >= 2, () -> {
      List<Mob> mobs = echoes(attempt);
      group.set(mobs);
      helper.assertTrue(mobs.size() <= 3, "more than one elite and two escorts: " + mobs.size());
      int elites = 0;
      for (Mob mob : mobs) {
        var data = EnvesEchoes.data(mob).orElseThrow();
        helper.assertTrue(mob.isPersistenceRequired(), "an echo despawns");
        helper.assertTrue(mob.getCustomName() != null && mob.isCustomNameVisible() == (data.roleOf() != Role.ESCORT), "echo name");
        if (data.roleOf() == Role.ELITE) {
          elites++;
          helper.assertTrue(data.affixes().size() == 1, "floor I elites carry one affix: " + data.affixes());
          helper.assertTrue(key(mob).equals("entrelumen.enves.echo.affixed"), "an elite is named «Eco de … — affix»: " + key(mob));
          double health = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
          helper.assertTrue(health == 60 || health == 75, "a draugr's Frontier floor I health, got " + health);
          helper.assertTrue(Math.abs(data.damage() - 1.2) < 1e-4 || Math.abs(data.damage() - 1.15) < 1e-4, "damage factor " + data.damage());
        } else {
          helper.assertTrue(data.roleOf() == Role.ESCORT, "a fight room holds elites and escorts only");
          helper.assertTrue(key(mob).equals("entrelumen.enves.echo"), "an escort is «Eco de …»");
          helper.assertTrue(mob.getAttributeBaseValue(Attributes.MAX_HEALTH) == 24, "escort health");
        }
      }
      helper.assertTrue(elites >= 1 && elites <= 2, "one or two elites: " + elites);
      Mob elite = mobs.stream().filter(m -> EnvesEchoes.data(m).orElseThrow().roleOf() == Role.ELITE).findFirst().orElseThrow();
      fell.set(elite.position());
      elite.hurt(player.damageSources().playerAttack(player), 10_000);
      for (Mob mob : mobs)
        if (mob != elite && EnvesEchoes.data(mob).orElseThrow().roleOf() == Role.ESCORT) mob.hurt(player.damageSources().playerAttack(player), 10_000);
    }).then(() -> !player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(fell.get())).inflate(6)).isEmpty(), () -> {
      var items = player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(fell.get())).inflate(16));
      int shards = 0;
      var plain = List.of(Items.EMERALD, Items.DIAMOND, Items.ECHO_SHARD, Items.IRON_INGOT, Items.GOLD_INGOT, Items.AMETHYST_SHARD,
          Items.HEART_OF_THE_SEA);
      for (ItemEntity item : items) {
        ItemStack stack = item.getItem();
        if (stack.is(EnvesContent.SOUR_LIGHT_SHARD.get())) shards += stack.getCount();
        else helper.assertTrue(plain.contains(stack.getItem()), "an echo dropped something of its own: " + stack);
      }
      helper.assertTrue(shards >= 1, "an elite always drops a shard");
    }).run();
  }

  // ---- Affixes ------------------------------------------------------------------------------

  private static Attempt loose(ServerPlayer player) {
    return new Attempt(UUID.randomUUID(), player.getUUID(), 4095, 7, Tier.FRONTIER, player.getUUID(), 0);
  }

  private static Mob echo(ServerLevel level, Attempt attempt, BlockPos at, ServerPlayer target, EnvesAffix... affixes) {
    var entry = new EnvesEchoTables.Entry("minecraft:zombie", 1, null, 40.0, 1.0, Map.of(), "");
    Mob mob = EnvesEchoes.spawn(level, new EnvesEchoes.Spec(entry, Role.ELITE, attempt, 1, List.of(affixes)), Vec3.atBottomCenterOf(at), target);
    if (mob == null) throw new IllegalStateException("no echo");
    return mob;
  }

  private static void platform(ServerLevel level, BlockPos center, int radius) {
    for (int dx = -radius; dx <= radius; dx++)
      for (int dz = -radius; dz <= radius; dz++) {
        level.setBlockAndUpdate(center.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
        for (int dy = 0; dy < 5; dy++) level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
      }
  }

  @GameTest(template = "empty", timeoutTicks = 1200, batch = "enves_affixes")
  public static void everyAffixDoesWhatItsNameSays(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "AffixTarget");
    ServerPlayer player = qa.player;
    ServerLevel level = helper.getLevel();
    BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
    platform(level, center, 9);
    player.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0f, 0f);
    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
    player.setHealth(200);
    Attempt attempt = loose(player);
    List<Mob> spawned = new ArrayList<>();
    AtomicLong started = new AtomicLong();
    new Script(helper, () -> {
      spawned.forEach(Mob::discard);
      qa.close();
    }).then(() -> {
      // Veloz and Blindado: modifiers.
      Mob swift = echo(level, attempt, center.offset(-6, 0, -6), null, EnvesAffix.SWIFT);
      Mob armored = echo(level, attempt, center.offset(-6, 0, -4), null, EnvesAffix.ARMORED);
      Mob plain = echo(level, attempt, center.offset(-6, 0, -2), null);
      spawned.addAll(List.of(swift, armored, plain));
      helper.assertTrue(swift.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(EnvesAffixes.id("veloz")), "Veloz has no speed");
      helper.assertTrue(swift.getAttributeValue(Attributes.MOVEMENT_SPEED) > plain.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3,
          "Veloz is not faster");
      helper.assertTrue(armored.getAttributeValue(Attributes.ARMOR) >= plain.getAttributeValue(Attributes.ARMOR) + EnvesAffix.ARMORED_ARMOR,
          "Blindado has no armor");
      helper.assertTrue(armored.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= EnvesAffix.ARMORED_KNOCKBACK, "Blindado is pushed");
      // Vampírico heals a share of what it deals.
      Mob vampire = echo(level, attempt, center.offset(-6, 0, 0), null, EnvesAffix.VAMPIRIC);
      spawned.add(vampire);
      vampire.setHealth(10);
      player.invulnerableTime = 0;
      float full = player.getHealth();
      player.hurt(level.damageSources().mobAttack(vampire), 10);
      float dealt = full - player.getHealth();
      helper.assertTrue(dealt > 0 && Math.abs(vampire.getHealth() - (10 + dealt * EnvesAffix.VAMPIRIC_LEECH)) < 0.01,
          "Vampírico healed to " + vampire.getHealth() + " after dealing " + dealt);
      // Perforante: armor stops half of what it would.
      for (var piece : List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)) {
        ItemStack stack = new ItemStack(piece);
        player.setItemSlot(player.getEquipmentSlotForItem(stack), stack);
      }
      Mob piercer = echo(level, attempt, center.offset(-6, 0, 2), null, EnvesAffix.PIERCING);
      spawned.add(piercer);
      player.setHealth(200);
      player.invulnerableTime = 0;
      player.hurt(level.damageSources().mobAttack(plain), 10);
      float plainLoss = 200 - player.getHealth();
      player.setHealth(200);
      player.invulnerableTime = 0;
      player.hurt(level.damageSources().mobAttack(piercer), 10);
      float pierceLoss = 200 - player.getHealth();
      helper.assertTrue(pierceLoss > plainLoss * 1.8, "Perforante did " + pierceLoss + " against " + plainLoss);
      for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
        player.setItemSlot(slot, ItemStack.EMPTY);
      player.setHealth(200);
      // Ardiente: fire-proof, and sets the player alight within three blocks.
      Mob burning = echo(level, attempt, center.offset(1, 0, 1), null, EnvesAffix.BURNING);
      burning.setNoAi(true);
      spawned.add(burning);
      helper.assertTrue(burning.hasEffect(MobEffects.FIRE_RESISTANCE), "Ardiente burns itself");
      player.clearFire();
      // Espectral: in front of the player (who faces south), it comes back behind them.
      Mob ghost = echo(level, attempt, center.offset(0, 0, 6), player, EnvesAffix.SPECTRAL);
      ghost.setNoAi(true);
      ghost.setTarget(player);
      spawned.add(ghost);
      started.set(level.getGameTime());
    }).then(() -> player.getRemainingFireTicks() > 0 || level.getGameTime() - started.get() > 60, () ->
        helper.assertTrue(player.getRemainingFireTicks() > 0, "Ardiente did not set the player alight")
    ).then(() -> {
      Mob ghost = spawned.getLast();
      return ghost.getZ() < player.getZ() || level.getGameTime() - started.get() > 400;
    }, () -> {
      Mob ghost = spawned.getLast();
      helper.assertTrue(ghost.getZ() < player.getZ() && ghost.distanceTo(player) < 3.6,
          "Espectral did not come back behind the player: " + ghost.position() + " vs " + player.position());
    }).run();
  }

  // ---- Shrines ------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_content")
  public static void aShrineBlessesTheGroupOnItsFloorOnly(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "ShrineTaker");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, 26_09);
    AtomicReference<BlockPos> altar = new AtomicReference<>();
    AtomicInteger index = new AtomicInteger();
    List<Mob> spawned = new ArrayList<>();
    Script script = new Script(helper, () -> {
      spawned.forEach(Mob::discard);
      end(player, attempt);
      qa.close();
    });
    script.then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      ServerLevel level = player.serverLevel();
      // Away from the stairwell in the middle of the start room.
      BlockPos arrival = Enves.arrival(player.server, attempt, 1);
      altar.set(EnvesEchoes.safeSpot(level, arrival.offset(-2, 0, 0), 2));
    });
    for (Blessing blessing : Blessing.values()) {
      script.then(() -> {
        ServerLevel level = player.serverLevel();
        var runs = EnvesRuns.get(player.server);
        var floor = runs.run(attempt.id).floor(1);
        floor.blessing = blessing.id();
        floor.blessingTaken = false;
        runs.setDirty();
        moveTo(player, Enves.arrival(player.server, attempt, 1));
        var context = Enves.floor(level, attempt, 1);
        EnvesShrines.place(context, new EnvesHooks.WorldMarker(new EnvesMarkers.Marker(EnvesMarkers.Kind.SHRINE, ""), altar.get(),
            context.layout().start(), EnvesLayout.Role.SHRINE));
        helper.assertTrue(!level.getBlockState(altar.get()).getValue(EnvesContentBlocks.Shrine.SPENT), "a new altar is spent");
        helper.assertTrue(EnvesShrines.use(player, altar.get()), "the altar did not bless");
        helper.assertTrue(level.getBlockState(altar.get()).getValue(EnvesContentBlocks.Shrine.SPENT), "the altar stays lit");
        helper.assertTrue(!EnvesShrines.use(player, altar.get()), "an altar blesses twice");
        helper.assertTrue(EnvesShrines.active(player) == blessing, "the blessing is not active: " + EnvesShrines.active(player));
        switch (blessing) {
          case FERVOR -> {
            Sheep sheep = EntityType.SHEEP.create(level);
            sheep.moveTo(Vec3.atBottomCenterOf(EnvesEchoes.safeSpot(level, altar.get().offset(0, 0, 2), 2)));
            level.addFreshEntity(sheep);
            spawned.add(sheep);
            sheep.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            sheep.setHealth(100);
            sheep.hurt(player.damageSources().playerAttack(player), 4);
            helper.assertTrue(Math.abs(100 - sheep.getHealth() - 5) < 0.01, "Fervor: " + (100 - sheep.getHealth()));
          }
          case REFUGE -> {
            player.setHealth(20);
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().generic(), 8);
            helper.assertTrue(Math.abs(20 - player.getHealth() - 6) < 0.01, "Refugio: " + (20 - player.getHealth()));
            player.setHealth(20);
          }
          case CLARITY -> {
            for (int cell : Enves.layout(attempt, 1).cells())
              helper.assertTrue(attempt.floor(1).explored.get(cell), "Claridad left cell " + cell + " dark");
          }
          case FORTUNE -> helper.assertTrue(EnvesShrines.fortune(player.server, attempt, 1), "Fortuna is not on the floor");
          case HASTE -> {}
        }
      });
      if (blessing == Blessing.HASTE)
        script.then(() -> player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.hasEffect(MobEffects.DIG_SPEED), () -> {});
    }
    script.then(() -> {
      // Off the blessed floor, no blessing: the vestibule is floor 0.
      BlockPos vestibule = Enves.origin(attempt, 0, Enves.layout(attempt, 1).start()).offset(EnvesGeometry.C - 3, 1, EnvesGeometry.C - 3);
      moveTo(player, EnvesEchoes.safeSpot(player.serverLevel(), vestibule, 4));
      helper.assertTrue(EnvesShrines.active(player) == null, "a blessing outlived its floor");
    });
    script.run();
  }

  // ---- Seals --------------------------------------------------------------------------------

  /** Solves a puzzle the way a player would, click by click. */
  static void solve(ServerLevel level, Attempt attempt, Puzzle puzzle, ServerPlayer player) {
    switch (puzzle.kind) {
      case "vault_braziers", "seal_braziers" -> {
        puzzle.echoAt = -1;
        for (int brazier : puzzle.solution.clone()) EnvesPuzzles.click(level, attempt, puzzle, brazier, player);
      }
      case "vault_levers", "seal_levers" -> {
        int n = puzzle.elements.size(), full = (1 << EnvesPuzzles.lampCount(puzzle)) - 1;
        for (int set = 0; set < 1 << n; set++) {
          int s = puzzle.state[0];
          for (int i = 0; i < n; i++) if ((set & 1 << i) != 0) s ^= puzzle.data[i];
          if (s != full) continue;
          for (int i = 0; i < n; i++) if ((set & 1 << i) != 0) EnvesPuzzles.click(level, attempt, puzzle, i, player);
          return;
        }
        throw new IllegalStateException("an unsolvable lever board");
      }
      case "seal_mirrors" -> {
        for (int i = 0; i < puzzle.elements.size() && !puzzle.solved; i++)
          if (puzzle.state[i] != puzzle.solution[i]) EnvesPuzzles.click(level, attempt, puzzle, i, player);
      }
      case "vault_glyphs" -> {
        for (int i = 0; i < 3 && !puzzle.solved; i++)
          for (int turn = 0; turn < EnvesPuzzleRules.GLYPHS && puzzle.state[i] != puzzle.solution[2 - i] && !puzzle.solved; turn++)
            EnvesPuzzles.click(level, attempt, puzzle, i, player);
      }
      case "vault_offering" -> {
        player.getInventory().add(new ItemStack(EnvesContent.SOUR_LIGHT_SHARD.get(), puzzle.data[0]));
        EnvesPuzzles.click(level, attempt, puzzle, 0, player);
      }
      default -> throw new IllegalStateException(puzzle.kind);
    }
  }

  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_content")
  public static void theThreeSealsAskForTheirGuardianTheirCircleAndTheirPuzzle(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "SealKeeper");
    ServerPlayer player = qa.player;
    int depth = 3;
    Attempt attempt = attempt(player, 2709);
    Script script = new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    });
    script.then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      player.setInvulnerable(true);
      EnvesPlacer.queue(player.server, attempt, depth);
    }).then(() -> attempt.floor(depth).placement == Placement.READY, () -> {
      var floor = Enves.floor(player.serverLevel(), attempt, depth);
      var variants = EnvesPuzzleRules.sealVariants(attempt.seed, depth, floor.layout().seals());
      helper.assertTrue(variants.size() == 3 && java.util.EnumSet.copyOf(variants.values()).size() == 3,
          "floor III's three seals are not one of each: " + variants);
      moveTo(player, Enves.arrival(player.server, attempt, depth));
    });
    for (int index = 0; index < 3; index++) {
      int i = index;
      AtomicReference<BlockPos> seal = new AtomicReference<>();
      AtomicInteger cell = new AtomicInteger();
      script.then(() -> {
        var floor = Enves.floor(player.serverLevel(), attempt, depth);
        cell.set(floor.layout().seals().get(i));
        seal.set(EnvesSeals.sealPos(floor, cell.get()));
        BlockPos near = EnvesEchoes.safeSpot(player.serverLevel(), seal.get().below().relative(EnvesSeals.door(floor, cell.get()), 2), 3);
        moveTo(player, near);
      }).then(() -> EnvesRuns.get(player.server).run(attempt.id).floor(depth).visited.get(cell.get()), () -> {
        var floor = Enves.floor(player.serverLevel(), attempt, depth);
        var state = EnvesRuns.get(player.server).run(attempt.id).floor(depth).seal(cell.get());
        switch (state.variant) {
          case GUARDIAN -> {
            helper.assertTrue(state.guardianSpawned && state.guardian != null, "no guardian stood at the seal");
            helper.assertTrue(!Enves.lightSeal(player, seal.get()), "a guarded seal lit");
            var guardian = player.serverLevel().getEntity(state.guardian);
            helper.assertTrue(guardian instanceof Mob, "the guardian is gone");
            var data = EnvesEchoes.data(guardian).orElseThrow();
            helper.assertTrue(data.roleOf() == Role.GUARDIAN && data.affixes().size() == 3, "a floor III guardian has three affixes");
            ((Mob) guardian).hurt(player.damageSources().playerAttack(player), 100_000);
          }
          case CIRCLE -> helper.assertTrue(!Enves.lightSeal(player, seal.get()) && state.circle, "the circle did not wake");
          case PUZZLE -> {
            Puzzle puzzle = EnvesRuns.get(player.server).run(attempt.id).floor(depth).puzzles.get(cell.get());
            helper.assertTrue(puzzle != null && puzzle.kind.startsWith("seal_"), "the puzzle seal was not dressed");
            helper.assertTrue(!Enves.lightSeal(player, seal.get()), "an unsolved puzzle's seal lit");
            solve(player.serverLevel(), attempt, puzzle, player);
            helper.assertTrue(puzzle.solved, "the puzzle " + puzzle.kind + " is not solved");
          }
        }
      }).then(() -> {
        var state = EnvesRuns.get(player.server).run(attempt.id).floor(depth).seal(cell.get());
        if (state.variant == SealVariant.GUARDIAN && state.guardianDead) Enves.lightSeal(player, seal.get());
        return attempt.floor(depth).lit.get(cell.get());
      }, () -> helper.assertTrue(player.serverLevel().getBlockState(seal.get()).getValue(EnvesSealBlock.LIT), "the seal block is dark"));
    }
    script.run();
  }

  // ---- Vaults -------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_content")
  public static void everyVaultLockOpensItsGateWhenSolved(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "VaultBreaker");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, seedWith(s -> !EnvesLayout.descent(s).floor(1).withRole(EnvesLayout.Role.VAULT).isEmpty()));
    AtomicReference<Puzzle> current = new AtomicReference<>();
    AtomicReference<EnvesVaults.Gate> gate = new AtomicReference<>();
    Script script = new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    });
    script.then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      player.setInvulnerable(true);
      int cell = Enves.layout(attempt, 1).withRole(EnvesLayout.Role.VAULT).getFirst();
      var floor = Enves.floor(player.serverLevel(), attempt, 1);
      helper.assertTrue(EnvesRuns.get(player.server).run(attempt.id).floor(1).puzzles.get(cell) != null, "placing the vault built no lock");
      List<BlockPos> markers = new ArrayList<>();
      String side = null;
      for (var m : floor.markers(cell))
        if (m.marker().kind() == EnvesMarkers.Kind.VAULT_GATE && (side == null || side.equals(m.marker().argument()))) {
          side = m.marker().argument();
          markers.add(m.pos());
        }
      gate.set(EnvesVaults.gate(markers, EnvesVaults.side(side)));
      moveTo(player, EnvesEchoes.safeSpot(player.serverLevel(), gate.get().bottomCentre().relative(gate.get().out(), 3), 3));
    });
    for (VaultPuzzle kind : VaultPuzzle.values()) {
      script.then(() -> {
        ServerLevel level = player.serverLevel();
        int cell = Enves.layout(attempt, 1).withRole(EnvesLayout.Role.VAULT).getFirst();
        var floor = Enves.floor(level, attempt, 1);
        var runs = EnvesRuns.get(player.server);
        runs.run(attempt.id).floor(1).puzzles.remove(cell);
        BlockPos anchor = gate.get().bottomCentre().relative(gate.get().out().getOpposite(), 2);
        Puzzle puzzle = EnvesVaults.build(floor, cell, gate.get(), anchor, kind);
        EnvesPuzzles.put(player.server, attempt, 1, cell, puzzle);
        EnvesVaults.render(level, puzzle, gate.get());
        current.set(puzzle);
        for (BlockPos pos : gate.get().all()) helper.assertTrue(!level.getBlockState(pos).isAir(), kind + ": the gate is open");
        if (puzzle.kind.equals("vault_glyphs")) {
          // Copying the clue as seen is the first mistake: it does not open.
          for (int i = 0; i < 3; i++)
            while (puzzle.state[i] != puzzle.solution[i]) EnvesPuzzles.click(level, attempt, puzzle, i, player);
          helper.assertTrue(!puzzle.solved && puzzle.misses >= 1, "the literal reading opened the glyphs or went unnoticed");
        }
        solve(level, attempt, puzzle, player);
        helper.assertTrue(puzzle.solved, kind + " was not solved");
      }).then(() -> gate.get().all().stream().allMatch(p -> player.serverLevel().getBlockState(p).isAir()), () -> {
        if (current.get().kind.equals("vault_offering"))
          helper.assertTrue(player.getInventory().countItem(EnvesContent.SOUR_LIGHT_SHARD.get()) == 0, "the offering was not taken");
      });
    }
    script.run();
  }

  // ---- The stair's champion -----------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_content")
  public static void theChampionBarsTheStairUntilItFalls(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "StairClimber");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, 1234);
    AtomicReference<Mob> champion = new AtomicReference<>();
    AtomicLong waitFrom = new AtomicLong();
    new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    }).then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      player.setInvulnerable(true);
      int guard = Enves.layout(attempt, 1).guard();
      moveTo(player, EnvesEchoes.safeSpot(player.serverLevel(), Enves.origin(attempt, 1, guard).offset(EnvesGeometry.C + 4, 1, EnvesGeometry.C), 4));
    }).then(() -> EnvesRuns.get(player.server).run(attempt.id).floor(1).championSpawned, () -> {
      var state = EnvesRuns.get(player.server).run(attempt.id).floor(1);
      var entity = player.serverLevel().getEntity(state.champion);
      helper.assertTrue(entity instanceof Mob, "no champion");
      champion.set((Mob) entity);
      var data = EnvesEchoes.data(entity).orElseThrow();
      helper.assertTrue(data.roleOf() == Role.CHAMPION && data.affixes().size() == 3, "a champion with three affixes");
      helper.assertTrue(champion.get().getAttributeBaseValue(Attributes.MAX_HEALTH) == 180, "the royal draugr's 180 health");
      for (int seal : Enves.layout(attempt, 1).seals()) attempt.floor(1).lit.set(seal);
    }).then(() -> attempt.floor(2).placement == Placement.READY, () -> waitFrom.set(player.serverLevel().getGameTime()))
        .then(() -> player.serverLevel().getGameTime() - waitFrom.get() > 40, () -> {
          helper.assertTrue(!attempt.floor(1).stairOpen, "the stair opened with its champion standing");
          champion.get().hurt(player.damageSources().playerAttack(player), 100_000);
        }).then(() -> attempt.floor(1).stairOpen, () -> {}).run();
  }

  // ---- The offering -------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 2000, batch = "enves_content")
  public static void theGateTakesANetherStarOrSourLightShards(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "OfferingPayer");
    ServerPlayer player = qa.player;
    List<Attempt> opened = new ArrayList<>();
    try {
      frontier(player);
      var shard = EnvesContent.SOUR_LIGHT_SHARD.get();
      int shards = EnvesConfig.settings().offerings().get(1).count();
      helper.assertTrue(Enves.open(player, Tier.FRONTIER) == Enves.Refusal.NO_OFFERING, "opened with nothing");
      player.getInventory().add(new ItemStack(shard, shards));
      var view = EnvesEntrance.gateView(player);
      helper.assertTrue(view.offers().size() == 2 && !view.offers().get(0).affordable() && view.offers().get(1).affordable(),
          "the gate does not show a star it lacks and shards it has");
      helper.assertTrue(Enves.open(player, Tier.FRONTIER, 0) == Enves.Refusal.NO_OFFERING, "paid a star it did not carry");
      helper.assertTrue(Enves.open(player, Tier.FRONTIER) == null, "shards alone did not open the gate");
      helper.assertTrue(player.getInventory().countItem(shard) == 0, "the shards were not taken");
      opened.add(Enves.attemptOf(player).orElseThrow());
      Enves.end(player.server, opened.getLast(), EnvesHooks.EndReason.ADMIN);
      player.getInventory().add(new ItemStack(Items.NETHER_STAR));
      player.getInventory().add(new ItemStack(shard, shards));
      helper.assertTrue(Enves.open(player, Tier.FRONTIER, 1) == null, "the chosen shards did not open the gate");
      helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 1 && player.getInventory().countItem(shard) == 0,
          "the gate took other than the chosen offering");
      opened.add(Enves.attemptOf(player).orElseThrow());
      Enves.end(player.server, opened.getLast(), EnvesHooks.EndReason.ADMIN);
      helper.assertTrue(Enves.open(player, Tier.FRONTIER) == null, "a Nether star did not open the gate");
      helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 0, "the star was not taken");
      opened.add(Enves.attemptOf(player).orElseThrow());
    } finally {
      for (Attempt attempt : opened) end(player, attempt);
      qa.close();
    }
    helper.succeed();
  }

  // ---- Loot ---------------------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 24000, batch = "enves_content")
  public static void theLootTablesLoadAndPayByFloorAndTier(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "LootRoller");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, 99);
    new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    }).then(() -> attempt.status == Status.OPEN, () -> {
      ServerLevel level = Enves.level(player.server);
      Vec3 origin = Vec3.atCenterOf(Enves.arrival(player.server, attempt, 1));
      var shard = EnvesContent.SOUR_LIGHT_SHARD.get();
      for (String name : List.of("room", "vault", "boss", "echo_elite", "echo_guardian", "echo_champion", "grottol")) {
        LootTable table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("entrelumen", "enves/" + name)));
        helper.assertTrue(table != LootTable.EMPTY, "entrelumen:enves/" + name + " did not load");
        var params = name.startsWith("echo") || name.equals("grottol")
            ? new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, player).withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY)
            : new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
        var items = table.getRandomItems(params);
        int shards = items.stream().filter(s -> s.is(shard)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(shards >= 1, name + " gave no shards");
        if (name.equals("boss")) {
          helper.assertTrue(shards >= 12 && shards <= 16, "the boss chest's shards: " + shards);
          long gear = items.stream().filter(s -> s.isEnchanted() && s.getMaxStackSize() == 1).count();
          helper.assertTrue(gear == 3, "the boss chest gives three pieces, got " + gear + ": " + items);
        }
        if (name.equals("vault")) {
          long gear = items.stream().filter(s -> s.isEnchanted() && s.getMaxStackSize() == 1).count();
          helper.assertTrue(gear == 2, "a vault gives two pieces, got " + gear);
        }
      }
    }).run();
  }

  // ---- The White Wither ---------------------------------------------------------------------

  @GameTest(template = "empty", timeoutTicks = 1200, batch = "enves_boss_arena")
  public static void theWhiteWitherBreaksNothingHoversLowAndTellsItsCharge(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "WitherBait");
    ServerPlayer player = qa.player;
    ServerLevel level = helper.getLevel();
    BlockPos home = helper.absolutePos(new BlockPos(2, 1, 2));
    platform(level, home, 14);
    List<BlockPos> fragile = new ArrayList<>();
    for (int dx = -2; dx <= 2; dx += 4)
      for (int dz = -2; dz <= 2; dz += 4) {
        BlockPos glass = home.offset(dx, 3, dz);
        level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        fragile.add(glass);
      }
    BlockPos dirt = home.offset(0, 0, -8);
    level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
    fragile.add(dirt);
    player.teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 10.5, 180f, 0f);
    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
    player.setHealth(400);
    Attempt attempt = loose(player);
    WhiteWither boss = EnvesBoss.spawn(level, attempt, home);
    helper.assertTrue(boss != null, "no White Wither");
    AtomicLong telegraphAt = new AtomicLong(-1), chargeAt = new AtomicLong(-1);
    AtomicReference<Float> before = new AtomicReference<>();
    new Script(helper, () -> {
      boss.discard();
      qa.close();
    }).then(() -> {
      helper.assertTrue(!boss.hurt(level.damageSources().playerAttack(player), 50), "hurt while condensing");
    }).then(() -> boss.phase() == WhiteWither.Phase.FIGHT, () -> {
      double height = boss.getY() - home.getY();
      helper.assertTrue(height > 1.5 && height < 3.5, "the White Wither does not hover low: " + height);
      boss.setTarget(player);
      // Hurting a wither makes the vanilla one break the blocks round it; this one must not.
      boss.hurt(level.damageSources().playerAttack(player), 5);
      Vec3 from = new Vec3(home.getX() + 0.5, home.getY() + 3, home.getZ() - 4.5);
      level.addFreshEntity(new SourSkull(level, boss, from, Vec3.atCenterOf(dirt).subtract(from)));
      before.set(player.getHealth());
      boss.telegraph(player);
      telegraphAt.set(level.getGameTime());
      helper.assertTrue(boss.phase() == WhiteWither.Phase.TELEGRAPH, "no warning before the charge");
      helper.assertTrue(boss.chargeLength() >= 8, "the charge's line is too short to reach the player: " + boss.chargeLength());
    }).then(() -> {
      if (boss.phase() == WhiteWither.Phase.TELEGRAPH) {
        helper.assertTrue(player.getHealth() >= before.get(), "struck during the warning");
        return false;
      }
      return true;
    }, () -> {
      chargeAt.set(level.getGameTime());
      helper.assertTrue(chargeAt.get() - telegraphAt.get() >= EnvesContentConfig.balance().boss().telegraphTicks() - 1,
          "the warning lasted only " + (chargeAt.get() - telegraphAt.get()) + " ticks");
    }).then(() -> boss.phase() == WhiteWither.Phase.EXPOSED || level.getGameTime() - chargeAt.get() > 60, () -> {
      helper.assertTrue(boss.phase() == WhiteWither.Phase.EXPOSED, "the charge did not end exposed");
      helper.assertTrue(boss.struck().contains(player.getUUID()), "the charge missed a player standing on its line");
      helper.assertTrue(player.getHealth() < before.get(), "the charge did no damage");
      float health = boss.getHealth();
      boss.invulnerableTime = 0;
      boss.hurt(level.damageSources().playerAttack(player), 10);
      float lost = health - boss.getHealth();
      helper.assertTrue(lost > 10 * (1 + EnvesContentConfig.balance().boss().exposedBonus()) * 0.5, "exposed, it took only " + lost);
    }).then(() -> level.getGameTime() - chargeAt.get() > 60, () -> {
      for (BlockPos pos : fragile) helper.assertTrue(!level.getBlockState(pos).isAir(), "the White Wither broke " + pos);
    }).run();
  }

  @GameTest(template = "empty", timeoutTicks = 36000, batch = "enves_content")
  public static void theWhiteWitherWakesInTheArenaAndItsFallOpensThePortalAndTheChest(GameTestHelper helper) {
    var qa = new QaPlayer(helper, "EclipseWalker");
    ServerPlayer player = qa.player;
    Attempt attempt = attempt(player, 555);
    AtomicReference<EnvesHooks.WorldMarker> chest = new AtomicReference<>();
    new Script(helper, () -> {
      end(player, attempt);
      qa.close();
    }).then(() -> attempt.status == Status.OPEN, () -> {
      helper.assertTrue(Enves.enter(player), "could not enter");
      player.hasChangedDimension();
      player.setInvulnerable(true);
      EnvesPlacer.queue(player.server, attempt, EnvesLayout.BOSS_DEPTH);
    }).then(() -> attempt.floor(EnvesLayout.BOSS_DEPTH).placement == Placement.READY, () -> {
      var floor = Enves.floor(player.serverLevel(), attempt, EnvesLayout.BOSS_DEPTH);
      int portal = floor.layout().exit();
      chest.set(floor.markers(portal).stream().filter(m -> m.marker().kind() == EnvesMarkers.Kind.CHEST).findFirst().orElseThrow());
      helper.assertTrue(!player.serverLevel().getBlockState(chest.get().pos()).is(Blocks.CHEST), "the boss chest stood before the boss fell");
      helper.assertTrue(!attempt.bossDefeated, "floor V began defeated");
      int center = floor.layout().withRole(EnvesLayout.Role.ARENA_CENTER).getFirst();
      moveTo(player, EnvesEchoes.safeSpot(player.serverLevel(), floor.origin(center).offset(EnvesGeometry.C + 3, 1, EnvesGeometry.C), 4));
    }).then(() -> EnvesRuns.get(player.server).run(attempt.id).bossSpawned, () -> {
      var run = EnvesRuns.get(player.server).run(attempt.id);
      var boss = player.serverLevel().getEntity(run.boss);
      helper.assertTrue(boss instanceof WhiteWither, "the arena woke no White Wither");
      var data = EnvesEchoes.data(boss).orElseThrow();
      helper.assertTrue(data.roleOf() == Role.BOSS && ((WhiteWither) boss).getMaxHealth() == 1020, "Frontier's boss has 1020 health");
      ((WhiteWither) boss).kill();
    }).then(() -> attempt.bossDefeated, () -> {
      ServerLevel level = player.serverLevel();
      var floor = Enves.floor(level, attempt, EnvesLayout.BOSS_DEPTH);
      for (var m : floor.markers(floor.layout().exit()))
        if (m.marker().kind() == EnvesMarkers.Kind.EXIT_PORTAL)
          helper.assertTrue(level.getBlockState(m.pos()).getValue(EnvesPortalBlock.ACTIVE), "the victory portal is dark");
      helper.assertTrue(level.getBlockEntity(chest.get().pos()) instanceof ChestBlockEntity box && box.getLootTable() != null
          && box.getLootTable().location().toString().equals("entrelumen:enves/boss"), "no boss chest rose by the portal");
    }).run();
  }
}
