package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the vein resonators with the real Curios 9.5.1 and FTB Ultimine 2101.1.15: the
 * pack config keeps Ultimine at zero blocks, a resonator worn in the player's charm slot sets Ultimine's
 * own limit to 16 per tier, two resonators count as the best one, vanilla slots do nothing, and a real
 * Ultimine break of a coal vein takes exactly the tier's reach. The six pack recipes chain the tiers.
 * Curios and Ultimine are reached only by reflection.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class VeinResonatorFullpackGameTests {
  private VeinResonatorFullpackGameTests() {}

  static void requireSuite() {
    for (String mod : List.of(CuriosCompat.MOD_ID, UltimineCompat.MOD_ID))
      if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Required real mod is absent: " + mod);
  }

  /** Curios API calls the test needs besides {@link CuriosCompat}. */
  private record Curios(MethodHandle inventory, MethodHandle itemSlots, MethodHandle entitySlots,
      MethodHandle setEquipped, MethodHandle stacksHandler, MethodHandle slots) {
    static Curios resolve() throws Throwable {
      var lookup = MethodHandles.publicLookup();
      Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
      Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
      Class<?> stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
      return new Curios(
          lookup.findStatic(api, "getCuriosInventory", MethodType.methodType(Optional.class, LivingEntity.class)),
          lookup.findStatic(api, "getItemStackSlots", MethodType.methodType(Map.class, ItemStack.class, LivingEntity.class)),
          lookup.findStatic(api, "getEntitySlots", MethodType.methodType(Map.class, LivingEntity.class)),
          lookup.findVirtual(handler, "setEquippedCurio",
              MethodType.methodType(void.class, String.class, int.class, ItemStack.class)),
          lookup.findVirtual(handler, "getStacksHandler", MethodType.methodType(Optional.class, String.class)),
          lookup.findVirtual(stacks, "getSlots", MethodType.methodType(int.class)));
    }

    Object handler(LivingEntity entity) throws Throwable {
      return ((Optional<?>) inventory.invoke(entity)).orElseThrow();
    }

    void equip(LivingEntity entity, int index, ItemStack stack) throws Throwable {
      setEquipped.invoke(handler(entity), "charm", index, stack);
    }

    int charmSlots(LivingEntity entity) throws Throwable {
      Optional<?> charms = (Optional<?>) stacksHandler.invoke(handler(entity), "charm");
      return charms.isEmpty() ? 0 : (int) slots.invoke(charms.get());
    }
  }

  private static final class Session implements AutoCloseable {
    final ServerPlayer player;
    final Connection connection;
    final io.netty.channel.embedded.EmbeddedChannel channel;

    Session(GameTestHelper helper, String name, BlockPos relative) {
      var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
      player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
          cookie.clientInformation());
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      player.setGameMode(GameType.SURVIVAL);
      var pos = helper.absolutePos(relative);
      player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void close() {
      try {
        connection.disconnect(Component.literal("Entrelumen vein resonator full-pack QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  /** The server's periodic check, run now: a GameTest player has no ticking connection. */
  private static void check(ServerPlayer player) {
    player.tickCount = VeinResonator.INTERVAL_TICKS * 100;
    player.doTick();
  }

  private static ItemStack resonator(int tier) {
    return new ItemStack(VeinResonator.item(tier));
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void resonatorRecipesChainTheSixTiers(GameTestHelper helper) {
    requireSuite();
    var level = helper.getLevel();
    List<String> problems = new ArrayList<>();
    for (int tier = 1; tier <= VeinResonator.TIERS; tier++) {
      var id = ResourceLocation.fromNamespaceAndPath("entrelumen", VeinResonator.id(tier));
      var holder = level.getRecipeManager().byKey(id);
      if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
        problems.add(id + " is not a loaded crafting recipe");
        continue;
      }
      if (!recipe.getResultItem(level.registryAccess()).is(VeinResonator.item(tier))) problems.add(id + " makes something else");
      int previous = tier - 1;
      boolean consumesPrevious = previous >= 1 && recipe.getIngredients().stream().anyMatch(i -> i.test(resonator(previous)));
      if ((previous >= 1) != consumesPrevious) problems.add(id + " does not chain from tier " + previous);
      int producers = 0;
      for (var other : level.getRecipeManager().getRecipes()) {
        try {
          if (other.value().getResultItem(level.registryAccess()).is(VeinResonator.item(tier))) producers++;
        } catch (RuntimeException special) {
          // Special recipes have no fixed result.
        }
      }
      if (producers != 1) problems.add(id + " has " + producers + " producing recipes");
    }
    helper.assertTrue(problems.isEmpty(), "Resonator recipes: " + problems);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void wornResonatorSetsUltimineToSixteenBlocksPerTier(GameTestHelper helper) {
    requireSuite();
    Curios curios;
    try {
      curios = Curios.resolve();
    } catch (Throwable error) {
      helper.fail("Curios API unavailable: " + error);
      return;
    }
    helper.assertTrue(CuriosCompat.available() && UltimineCompat.maxBlocksAttribute() != null,
        "The companion cannot reach Curios or FTB Ultimine");
    helper.assertTrue(UltimineCompat.configuredMaxBlocks() == 0,
        "The pack's ftbultimine-server.snbt must keep max_blocks at 0, found " + UltimineCompat.configuredMaxBlocks());
    var session = new Session(helper, "ResonatorCurios", new BlockPos(2, 1, 2));
    var player = session.player;
    int[] charms = {0};
    List<String> observed = new ArrayList<>();
    var sequence = helper.startSequence();
    sequence
        .thenExecute(() -> {
          check(player);
          helper.assertTrue(UltimineCompat.effectiveMaxBlocks(player) == 0, "Ultimine works without a resonator: "
              + UltimineCompat.effectiveMaxBlocks(player));
          // Carried, held or in a vanilla slot: nothing.
          player.getInventory().add(resonator(6));
          player.setItemInHand(InteractionHand.MAIN_HAND, resonator(4));
          player.setItemInHand(InteractionHand.OFF_HAND, resonator(2));
          check(player);
          helper.assertTrue(VeinResonator.wornTier(player) == 0 && UltimineCompat.effectiveMaxBlocks(player) == 0,
              "A resonator outside a Curios slot enabled Ultimine");
          player.getInventory().clearContent();
          try {
            helper.assertTrue(((Map<?, ?>) curios.entitySlots.invoke(player)).containsKey("charm"),
                "Players have no Curios charm slot");
            helper.assertTrue(((Map<?, ?>) curios.itemSlots.invoke(resonator(1), player)).containsKey("charm"),
                "Curios does not accept a resonator as a charm");
            charms[0] = curios.charmSlots(player);
            helper.assertTrue(charms[0] >= 1, "The charm slot has no room");
            curios.equip(player, 0, resonator(1));
          } catch (Throwable error) {
            helper.fail("Equipping through Curios failed: " + error);
          }
        })
        .thenIdle(2);
    // Curios applies a changed slot on its next tick, as when a player swaps it by hand.
    for (int step = 1; step <= VeinResonator.TIERS; step++) {
      int tier = step;
      sequence.thenExecute(() -> {
            check(player);
            int limit = UltimineCompat.effectiveMaxBlocks(player);
            observed.add(tier + "=" + limit);
            helper.assertTrue(VeinResonator.wornTier(player) == tier && limit == 16 * tier,
                "Tier " + tier + " gives " + limit + " Ultimine blocks instead of " + 16 * tier);
            if (tier == VeinResonator.TIERS) return;
            try {
              curios.equip(player, 0, resonator(tier + 1));
            } catch (Throwable error) {
              helper.fail("Equipping tier " + (tier + 1) + " failed: " + error);
            }
          })
          .thenIdle(2);
    }
    sequence
        .thenExecute(() -> {
          if (charms[0] < 2) return;
          try {
            curios.equip(player, 0, resonator(2));
            curios.equip(player, 1, resonator(5));
          } catch (Throwable error) {
            helper.fail("Equipping two resonators failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          if (charms[0] >= 2)
            helper.assertTrue(UltimineCompat.effectiveMaxBlocks(player) == 80,
                "Two resonators do not count as the best one: " + UltimineCompat.effectiveMaxBlocks(player));
          try {
            for (int i = 0; i < Math.min(charms[0], 2); i++) curios.equip(player, i, ItemStack.EMPTY);
          } catch (Throwable error) {
            helper.fail("Taking off the resonators failed: " + error);
          }
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          var attribute = player.getAttribute(UltimineCompat.maxBlocksAttribute());
          helper.assertTrue(UltimineCompat.effectiveMaxBlocks(player) == 0 && !attribute.hasModifier(VeinResonator.MODIFIER),
              "Ultimine outlived the resonator: " + UltimineCompat.effectiveMaxBlocks(player));
          com.mojang.logging.LogUtils.getLogger().info("ENTRELUMEN_VEIN_RESONATOR limits {} charmSlots={}", observed, charms[0]);
          session.close();
        })
        .thenSucceed();
  }

  /** FTB Ultimine's own key state on the server, as its key packet sets it. */
  private static void ultimineKey(ServerPlayer player, boolean pressed) throws ReflectiveOperationException {
    Class<?> mod = Class.forName("dev.ftb.mods.ftbultimine.FTBUltimine");
    Object instance = mod.getMethod("getInstance").invoke(null);
    mod.getMethod("setKeyPressed", ServerPlayer.class, boolean.class).invoke(instance, player, pressed);
  }

  private static int coal(GameTestHelper helper) {
    int count = 0;
    for (int x = 0; x < 5; x++)
      for (int y = 1; y <= 2; y++)
        for (int z = 0; z < 5; z++)
          if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.COAL_ORE)) count++;
    return count;
  }

  /** One real break with the Ultimine key held, looking at the vein's top centre; blocks broken. */
  private static int ultimineBreak(GameTestHelper helper, ServerPlayer player) throws ReflectiveOperationException {
    for (int x = 0; x < 5; x++)
      for (int y = 1; y <= 2; y++)
        for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.COAL_ORE);
    var feet = helper.absolutePos(new BlockPos(2, 3, 2));
    player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
    var target = helper.absolutePos(new BlockPos(2, 2, 2));
    player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
    player.getFoodData().setFoodLevel(20);
    int before = coal(helper);
    ultimineKey(player, true);
    player.gameMode.destroyBlock(target);
    ultimineKey(player, false);
    return before - coal(helper);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void ultimineBreaksExactlyTheResonatorReach(GameTestHelper helper) {
    requireSuite();
    Curios curios;
    try {
      curios = Curios.resolve();
    } catch (Throwable error) {
      helper.fail("Curios API unavailable: " + error);
      return;
    }
    var session = new Session(helper, "ResonatorVein", new BlockPos(2, 3, 2));
    var player = session.player;
    int[] broken = new int[2];
    helper.startSequence()
        .thenExecute(() -> {
          check(player);
          try {
            broken[0] = ultimineBreak(helper, player);
            curios.equip(player, 0, resonator(1));
          } catch (Throwable error) {
            helper.fail("Ultimine break without a resonator failed: " + error);
          }
          check(player);
        })
        .thenIdle(2)
        .thenExecute(() -> {
          check(player);
          try {
            broken[1] = ultimineBreak(helper, player);
            curios.equip(player, 0, ItemStack.EMPTY);
          } catch (Throwable error) {
            helper.fail("Ultimine break with a resonator failed: " + error);
          }
          com.mojang.logging.LogUtils.getLogger().info("ENTRELUMEN_VEIN_RESONATOR broken without={} tier1={}",
              broken[0], broken[1]);
          helper.assertTrue(broken[0] == 1, "Without a resonator one break took " + broken[0] + " blocks");
          helper.assertTrue(broken[1] == VeinResonator.reach(1),
              "Tier 1 broke " + broken[1] + " blocks of a 50-block vein instead of " + VeinResonator.reach(1));
          check(player);
          session.close();
        })
        .thenSucceed();
  }
}
