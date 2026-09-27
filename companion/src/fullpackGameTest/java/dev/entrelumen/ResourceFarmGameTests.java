package dev.entrelumen;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only tests against loaded Botany Pots and Botany Pots Tiers recipes and blocks. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ResourceFarmGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String BASE = "botanypots:white_terracotta_botany_pot";
  private static final String BASE_HOPPER = "botanypots:white_terracotta_hopper_botany_pot";
  private static final String MATERIAL = "minecraft:white_terracotta";
  private static final String HOPPER = "minecraft:hopper";
  private static final String FRAME = "entrelumen:calibration_frame";
  private static final Tier[] TIERS = {
    new Tier("elite", "minecraft:iron_ingot", "minecraft:ender_pearl", "entrelumen:propagation_core"),
    new Tier("ultra", "minecraft:diamond", "minecraft:nether_star", "entrelumen:ecosystem_capsule"),
    new Tier("mega", "minecraft:netherite_ingot", "minecraft:enchanted_golden_apple", "entrelumen:renewal_engine")
  };

  private record Tier(String name, String material, String formerCatalyst, String catalyst) {
    String pot() { return "botanypotstiers:" + name + "_white_terracotta_botany_pot"; }
    String hopperPot() { return "botanypotstiers:" + name + "_white_terracotta_hopper_botany_pot"; }
    String upgrade() { return "botanypotstiers:" + name + "_upgrade"; }
    String recipe(String suffix) { return "botanypotstiers:pots/" + name + "_white_terracotta_" + suffix; }
  }

  static void requireNativeInputs() {
    for (String mod : List.of("botanypots", "botanypotstiers"))
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real resource farm mod is absent: " + mod);
    for (String id : List.of(BASE, BASE_HOPPER, FRAME, TIERS[0].pot(), TIERS[1].pot(),
        TIERS[2].pot(), TIERS[2].hopperPot(), TIERS[0].upgrade(), TIERS[1].upgrade(),
        TIERS[2].upgrade(), TIERS[0].catalyst(), TIERS[1].catalyst(), TIERS[2].catalyst()))
      item(id);
  }

  /**
   * Elias's playtest of 24 September 2026 (docs/design/recipe-design-rules.md): each tier's component is
   * drawn in the middle of its upgrade (iron, component, iron) and every per-colour tier recipe consumes
   * that upgrade instead of its old catalyst, so the component closes one recipe per tier. Hopper pots are
   * basic automation and keep their native recipes.
   */
  @GameTest(template = "empty", timeoutTicks = 200)
  public static void nativeWhitePotCraftingRequiresRealIntegrationItems(GameTestHelper helper) {
    requireNativeInputs();
    List<String> observed = new ArrayList<>();
    observed.add(checkRecipe(helper, "botanypots:botanypots/crafting/white_terracotta_hopper_botany_pot",
        BASE_HOPPER, false, 2, 1, -1, "_", HOPPER, BASE));
    observed.add(checkRecipe(helper, "botanypots:botanypots/crafting/white_terracotta_hopper_botany_pot_quick",
        BASE_HOPPER, true, 3, 3, -1, "_",
        MATERIAL, HOPPER, MATERIAL, MATERIAL, "minecraft:flower_pot", MATERIAL, "_", MATERIAL, "_"));

    String previous = BASE;
    for (Tier tier : TIERS) {
      observed.add(checkRecipe(helper, tier.upgrade(), tier.upgrade(), true, 3, 1, 1,
          tier.formerCatalyst(), tier.material(), tier.catalyst(), tier.material()));
      String[] direct = {MATERIAL, tier.upgrade(), MATERIAL, MATERIAL, previous,
          MATERIAL, tier.material(), MATERIAL, tier.material()};
      observed.add(checkRecipe(helper, tier.recipe("botany_pot"), tier.pot(), true, 3, 3, 1,
          tier.formerCatalyst(), direct));
      rejects(helper, tier.recipe("botany_pot"), 3, 3, direct, 1, tier.catalyst());
      String[] same = {"_", tier.upgrade(), "_", "_", previous, "_", tier.material(), "_", tier.material()};
      observed.add(checkRecipe(helper, tier.recipe("botany_pot_same_material"), tier.pot(),
          true, 3, 3, 1, tier.formerCatalyst(), same));
      rejects(helper, tier.recipe("botany_pot_same_material"), 3, 3, same, 1, tier.catalyst());
      observed.add(checkRecipe(helper, tier.recipe("hopper_botany_pot_upgrade_quick"),
          tier.hopperPot(), true, 3, 3, 2, tier.formerCatalyst(),
          HOPPER, MATERIAL, tier.upgrade(), MATERIAL, previous, MATERIAL,
          tier.material(), MATERIAL, tier.material()));
      observed.add(checkRecipe(helper, tier.recipe("hopper_botany_pot"), tier.hopperPot(),
          false, 2, 1, -1, "_", HOPPER, tier.pot()));
      previous = tier.pot();
    }
    LOGGER.info("ENTRELUMEN_RESOURCE_FARM_RECIPES {}", observed);
    helper.succeed();
  }

  /** The grid with `item` in `slot` must not match: the component alone no longer makes a tier pot. */
  private static void rejects(GameTestHelper helper, String id, int width, int height, String[] slots, int slot,
      String item) {
    var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id))
        .orElseThrow().value();
    List<ItemStack> grid = new ArrayList<>();
    for (String cell : slots) grid.add("_".equals(cell) ? ItemStack.EMPTY : new ItemStack(item(cell)));
    grid.set(slot, new ItemStack(item(item)));
    helper.assertTrue(!recipe.matches(CraftingInput.of(width, height, grid), helper.getLevel()),
        id + " still accepts " + item + " in place of its upgrade");
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void giftedNativeUpgradesPreserveFilledPotAcrossThreeTiers(GameTestHelper helper)
      throws Exception {
    requireNativeInputs();
    try (NativePlayer donorSession = player(helper, "FarmGiver");
        NativePlayer receiverSession = player(helper, "FarmGuest")) {
      ServerPlayer donor = donorSession.player;
      ServerPlayer receiver = receiverSession.player;
      var donorTeam = FTBTeamsAPI.api().getManager().createPartyTeam(donor,
          "Farm donor " + donor.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
      donorSession.partyId = donorTeam.getId();
      var receiverTeam = FTBTeamsAPI.api().getManager().createPartyTeam(receiver,
          "Farm guest " + receiver.getUUID(), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
      receiverSession.partyId = receiverTeam.getId();
      helper.assertTrue(!donorTeam.getId().equals(receiverTeam.getId()),
          "Gift test players unexpectedly share a team");
      Entrelumen.current(donor).act = 5;
      Entrelumen.current(receiver).act = 1;
      receiver.setGameMode(GameType.SURVIVAL);
      receiver.setShiftKeyDown(true);

      BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
      helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
      helper.getLevel().setBlockAndUpdate(pos, block(BASE_HOPPER).defaultBlockState());
      Container filled = pot(helper, pos);
      helper.assertTrue(filled.getContainerSize() == 15, "Native pot storage size changed");
      filled.setItem(0, new ItemStack(Items.DIRT));
      filled.setItem(1, new ItemStack(Items.WHEAT_SEEDS));
      filled.setItem(2, new ItemStack(Items.DIAMOND_HOE));
      for (int slot = 3; slot < filled.getContainerSize(); slot++)
        filled.setItem(slot, new ItemStack(Items.WHEAT, slot));
      // Native Botany Pot metadata, beyond the container's soil/seed/tool/storage slots.
      filled.getClass().getMethod("updateGrowthTime", float.class).invoke(filled, 17f);
      receiver.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);

      List<String> observed = new ArrayList<>();
      for (Tier tier : TIERS) {
        CompoundTag before = ((BlockEntity) pot(helper, pos))
            .saveWithFullMetadata(helper.getLevel().registryAccess());
        before.remove("id"); // The tier has a different native block entity type.
        List<ItemStack> slots = contents(pot(helper, pos));
        donor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(tier.upgrade()), 2));
        receiver.setItemInHand(InteractionHand.MAIN_HAND, donor.getMainHandItem().copy());
        donor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack gifted = receiver.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = receiver.gameMode.useItemOn(receiver, helper.getLevel(), gifted,
            InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction(), tier.name() + " gift did not activate native useOn");
        helper.assertTrue(gifted.getCount() == 1, tier.name() + " upgrade did not consume exactly one");
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(block(tier.hopperPot())),
            tier.name() + " upgrade changed hopper/color or missed target tier");
        Container upgraded = pot(helper, pos);
        assertContents(helper, tier.name(), slots, upgraded);
        CompoundTag after = ((BlockEntity) upgraded).saveWithFullMetadata(helper.getLevel().registryAccess());
        after.remove("id");
        helper.assertTrue(before.equals(after), tier.name() + " useOn lost native block entity metadata");
        observed.add(tier.hopperPot() + ":" + result + ":remaining=" + gifted.getCount());
      }
      helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
          new AABB(pos).inflate(2)).isEmpty(), "Upgrade duplicated pot inventory into world drops");
      helper.assertTrue(Entrelumen.current(receiver).act == 1,
          "Gifted farm upgrade unexpectedly advanced recipient campaign");
      LOGGER.info("ENTRELUMEN_RESOURCE_FARM_UPGRADES {}", observed);
    }
    // A cleanup failure must fail the test before it is reported as successful.
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 2600)
  public static void nativeMegaHopperProducesWheatAndFullStockKeepsContents(GameTestHelper helper)
      throws Exception {
    requireNativeInputs();
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.GLOWSTONE.defaultBlockState());
    helper.getLevel().setBlockAndUpdate(pos, block(TIERS[2].hopperPot()).defaultBlockState());
    Container farm = pot(helper, pos);
    farm.setItem(0, new ItemStack(Items.DIRT));
    farm.setItem(1, new ItemStack(Items.WHEAT_SEEDS));
    helper.assertTrue(farm.getClass().getMethod("getOrInvalidateSoil").invoke(farm) != null,
        "Native dirt soil recipe is unavailable");
    helper.assertTrue(farm.getClass().getMethod("getOrInvalidateCrop").invoke(farm) != null,
        "Native wheat crop recipe is unavailable");
    int required = (Integer) farm.getClass().getMethod("getRequiredGrowthTicks").invoke(farm);
    int wait = Math.max(100, required * 2 + 40);
    helper.assertTrue(required > 0 && wait * 2 + 40 < 2600,
        "Native growth requirement exceeds this bounded GameTest: " + required);
    helper.runAfterDelay(wait, () -> {
      Container grown = pot(helper, pos);
      int harvested = 0;
      for (int slot = 3; slot < grown.getContainerSize(); slot++)
        if (grown.getItem(slot).is(Items.WHEAT)) harvested += grown.getItem(slot).getCount();
      helper.assertTrue(harvested > 0, "Native mega hopper produced no wheat after " + wait + " ticks");
      helper.assertTrue(grown.getItem(0).is(Items.DIRT) && grown.getItem(1).is(Items.WHEAT_SEEDS),
          "Production consumed native soil or seed");
      for (int slot = 3; slot < grown.getContainerSize(); slot++)
        grown.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
      final int observedHarvest = harvested;
      helper.runAfterDelay(wait, () -> {
        Container full = pot(helper, pos);
        for (int slot = 3; slot < full.getContainerSize(); slot++)
          helper.assertTrue(full.getItem(slot).is(Items.COBBLESTONE)
              && full.getItem(slot).getCount() == 64,
              "Full storage was overwritten at slot " + slot);
        helper.assertTrue(full.getItem(0).is(Items.DIRT) && full.getItem(1).is(Items.WHEAT_SEEDS),
            "Full storage destroyed soil or seed");
        LOGGER.info("ENTRELUMEN_RESOURCE_FARM_HARVEST pot={} wheat={} nativeGrowthTicks={} fullSlots=12",
            TIERS[2].hopperPot(), observedHarvest, required);
        helper.succeed();
      });
    });
  }

  private static String checkRecipe(GameTestHelper helper, String id, String expected, boolean shaped,
      int width, int height, int changedSlot, String oldItem, String... slots) {
    helper.assertTrue(slots.length == width * height, "Invalid test grid for " + id);
    var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id));
    helper.assertTrue(holder.isPresent(), "Loaded native recipe is missing: " + id);
    var raw = holder.orElseThrow().value();
    helper.assertTrue(raw instanceof CraftingRecipe, "Native crafting serializer changed: " + id);
    helper.assertTrue(shaped ? raw instanceof ShapedRecipe : raw instanceof ShapelessRecipe,
        "Native recipe shape changed: " + id);
    CraftingRecipe recipe = (CraftingRecipe) raw;
    List<ItemStack> grid = new ArrayList<>();
    for (String slot : slots) grid.add("_".equals(slot) ? ItemStack.EMPTY : new ItemStack(item(slot)));
    CraftingInput input = CraftingInput.of(width, height, grid);
    helper.assertTrue(recipe.matches(input, helper.getLevel()), "Real ingredients do not match " + id);
    ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
    String actual = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
    helper.assertTrue(actual.equals(expected) && output.getCount() == 1,
        id + " produced " + actual + " x" + output.getCount() + " instead of " + expected + " x1");
    var remainders = recipe.getRemainingItems(input);
    helper.assertTrue(remainders.size() == grid.size()
        && remainders.stream().allMatch(ItemStack::isEmpty),
        "Native crafting remainder changed or retained an input for " + id);
    if (changedSlot >= 0) {  // -1: a native recipe the pack leaves alone
      List<ItemStack> oldGrid = new ArrayList<>();
      for (ItemStack stack : grid) oldGrid.add(stack.copy());
      oldGrid.set(changedSlot, "_".equals(oldItem) ? ItemStack.EMPTY : new ItemStack(item(oldItem)));
      helper.assertTrue(!recipe.matches(CraftingInput.of(width, height, oldGrid), helper.getLevel()),
          "Recipe still accepts the former or missing integration cost: " + id);
    }
    return id + "=" + actual + "x" + output.getCount();
  }

  private static Container pot(GameTestHelper helper, BlockPos pos) {
    BlockEntity entity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(entity instanceof Container && entity.getClass().getName().contains("botany"),
        "Placed block lacks a native Botany Pot container at " + pos);
    return (Container) entity;
  }

  private static List<ItemStack> contents(Container pot) {
    List<ItemStack> result = new ArrayList<>();
    for (int slot = 0; slot < pot.getContainerSize(); slot++) result.add(pot.getItem(slot).copy());
    return result;
  }

  private static void assertContents(GameTestHelper helper, String tier, List<ItemStack> expected,
      Container actual) {
    helper.assertTrue(actual.getContainerSize() == expected.size(), tier + " changed pot storage size");
    for (int slot = 0; slot < expected.size(); slot++) {
      ItemStack before = expected.get(slot);
      ItemStack after = actual.getItem(slot);
      helper.assertTrue(ItemStack.isSameItemSameComponents(before, after)
          && before.getCount() == after.getCount(),
          tier + " lost soil/seed/tool/storage in slot " + slot);
    }
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Required real farm item is absent: " + id);
    Item item = BuiltInRegistries.ITEM.get(key);
    if (item == Items.AIR) throw new IllegalStateException("Required real farm item is air: " + id);
    return item;
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Required real farm block is absent: " + id);
    Block block = BuiltInRegistries.BLOCK.get(key);
    if (block == Blocks.AIR) throw new IllegalStateException("Required real farm block is air: " + id);
    return block;
  }

  private static NativePlayer player(GameTestHelper helper, String name) {
    var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
    ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
        cookie.gameProfile(), cookie.clientInformation());
    var connection = new Connection(PacketFlow.SERVERBOUND);
    var session = new NativePlayer(player, connection, new EmbeddedChannel(connection));
    try {
      net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
      player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
      player.getInventory().clearContent();
      return session;
    } catch (RuntimeException | Error failure) {
      try {
        session.close();
      } catch (Exception cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  private static final class NativePlayer implements AutoCloseable {
    private final ServerPlayer player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private UUID partyId;

    private NativePlayer(ServerPlayer player, Connection connection, EmbeddedChannel channel) {
      this.player = player;
      this.connection = connection;
      this.channel = channel;
    }

    @Override
    public void close() throws Exception {
      try {
        var manager = FTBTeamsAPI.api().getManager();
        var team = manager.getTeamForPlayer(player).orElse(null);
        if (partyId != null && team != null && partyId.equals(team.getId())) {
          if (!team.getOwner().equals(player.getUUID())
              || !team.getMembers().equals(Set.of(player.getUUID())))
            throw new IllegalStateException("Refusing to clean a farm QA party with other members");
          player.server.getCommands().getDispatcher().execute("ftbteams party leave",
              player.createCommandSourceStack().withSuppressedOutput());
          if (manager.getTeamByID(partyId).isPresent())
            throw new IllegalStateException("Native FTB Teams cleanup retained farm QA party " + partyId);
        }
      } finally {
        try {
          connection.disconnect(Component.literal("Entrelumen farm QA finished"));
          connection.handleDisconnection();
          if (player.server.getPlayerList().getPlayer(player.getUUID()) == player)
            throw new IllegalStateException("Farm QA player remained connected: " + player.getUUID());
        } finally {
          channel.finishAndReleaseAll();
        }
      }
    }
  }
}
