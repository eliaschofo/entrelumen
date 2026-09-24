package dev.entrelumen;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Installed-pack QA for the Apotheosis family: Apothic stat pickup of the ENTRELUMEN shelves,
 * the Atlas Library on real Eterna, World Tier advancements granted by closing acts, spawner
 * augments on a real Apothic spawner and the loaded KubeJS recipe set against the generated script.
 * Apotheosis classes are reached only by reflection; nothing here links against them.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ApotheosisGameTests {
  private ApotheosisGameTests() {}

  static void requireSuite() {
    for (String mod : List.of("apotheosis", "apothic_enchanting", "apothic_spawners", "apothic_attributes", "kubejs"))
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
        connection.disconnect(Component.literal("Entrelumen Apotheosis QA finished"));
        connection.handleDisconnection();
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  private static Object invokeStatic(String type, String method, Class<?>[] parameters, Object... arguments)
      throws ReflectiveOperationException {
    return Class.forName(type).getMethod(method, parameters).invoke(null, arguments);
  }

  private static Block block(String id) {
    return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
  }

  private static void clear(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3)))
      level.setBlockAndUpdate(pos, pos.getY() == center.getY() - 1 ? Blocks.STONE.defaultBlockState()
          : Blocks.AIR.defaultBlockState());
  }

  private static void ring(GameTestHelper helper, BlockPos center, Block shelf) {
    for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS)
      if (offset.getY() == 0) helper.getLevel().setBlockAndUpdate(center.offset(offset), shelf.defaultBlockState());
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void apothicEnchantingReadsEntrelumenShelfStats(GameTestHelper helper) throws Exception {
    requireSuite();
    var level = helper.getLevel();
    String registry = "dev.shadowsoffire.apothic_enchanting.table.EnchantingStatRegistry";
    Class<?>[] args = {BlockState.class, LevelReader.class, BlockPos.class};
    // eterna, max eterna, quanta, arcana, clues as shipped in data/entrelumen/enchanting_stats.
    Map<String, float[]> expected = Map.of(
        "entrelumen:cartographer_shelf", new float[] {3, 40, 0, 0, 0},
        "entrelumen:patina_shelf", new float[] {4, 60, 12, 0, 0},
        "entrelumen:lumen_shelf", new float[] {7.5f, 80, 0, 15, 1},
        "entrelumen:horizon_shelf", new float[] {12.5f, 100, 10, 10, 0});
    var pos = helper.absolutePos(new BlockPos(1, 1, 1));
    for (var entry : expected.entrySet()) {
      var state = block(entry.getKey()).defaultBlockState();
      float[] want = entry.getValue();
      float eterna = (float) invokeStatic(registry, "getEterna", args, state, level, pos);
      float max = (float) invokeStatic(registry, "getMaxEterna", args, state, level, pos);
      float quanta = (float) invokeStatic(registry, "getQuanta", args, state, level, pos);
      float arcana = (float) invokeStatic(registry, "getArcana", args, state, level, pos);
      int clues = (int) invokeStatic(registry, "getBonusClues", args, state, level, pos);
      helper.assertTrue(eterna == want[0] && max == want[1] && quanta == want[2] && arcana == want[3]
          && clues == (int) want[4], entry.getKey() + " stats were not read by Apothic Enchanting: "
          + eterna + "/" + max + "/" + quanta + "/" + arcana + "/" + clues);
    }
    // A table surrounded by sixteen shelves stops at each shelf's own maximum.
    var center = helper.absolutePos(new BlockPos(3, 2, 3));
    clear(helper, center);
    ring(helper, center, block("entrelumen:cartographer_shelf"));
    float cartographer = AtlasLibraryEterna.around(level, center);
    ring(helper, center, block("entrelumen:horizon_shelf"));
    float horizon = AtlasLibraryEterna.around(level, center);
    helper.assertTrue(AtlasLibraryEterna.usesApothic() && cartographer == 40f && horizon == 100f,
        "Table Eterna did not follow Apothic maxima: cartographer=" + cartographer + " horizon=" + horizon);
    helper.succeed();
  }

  private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
    return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
  }

  private static ItemStack book(Holder<Enchantment> enchantment, int level) {
    var book = new ItemStack(Items.ENCHANTED_BOOK);
    var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    stored.set(enchantment, level);
    book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
    return book;
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void atlasLibraryCapsByRealEternaWithoutDuplication(GameTestHelper helper) throws Exception {
    requireSuite();
    var level = helper.getLevel();
    var center = helper.absolutePos(new BlockPos(3, 2, 3));
    clear(helper, center);
    level.setBlockAndUpdate(center, ApotheosisContent.ATLAS_LIBRARY.get().defaultBlockState());
    ring(helper, center, block("entrelumen:patina_shelf"));
    float eterna = AtlasLibraryEterna.around(level, center);
    helper.assertTrue(eterna == 60f, "Sixteen patina shelves should give 60 Eterna, got " + eterna);
    var library = (AtlasLibraryBlockEntity) level.getBlockEntity(center);
    var sharpness = enchantment(helper, Enchantments.SHARPNESS);
    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, center, Direction.UP);
    for (int i = 0; i < 4; i++) helper.assertTrue(handler.insertItem(0, book(sharpness, 10), false).isEmpty(),
        "Hopper-style deposit refused");
    helper.assertTrue(library.pool() == 4 * 256, "Four Sharpness X books did not add 1024 lumen: " + library.pool());
    // 60 Eterna caps multi-level enchantments at 24; 25 is refused however large the pool.
    helper.assertTrue(library.withdraw(sharpness, ItemStack.EMPTY, 25) == null && library.pool() == 1024,
        "The server accepted a level above the real Eterna cap");
    var sharpnessEleven = library.withdraw(sharpness, ItemStack.EMPTY, 11);
    helper.assertTrue(sharpnessEleven != null && library.pool() == 0
        && EnchantmentHelper.getEnchantmentsForCrafting(sharpnessEleven).getLevel(sharpness) == 11,
        "Sharpness XI was not written for exactly 1024 lumen");
    // An enchantment never deposited, bought from the shared pool after refilling it.
    var looting = enchantment(helper, Enchantments.LOOTING);
    handler.insertItem(0, book(sharpness, 12), false);
    long before = library.pool();
    var lootingBook = library.withdraw(looting, ItemStack.EMPTY, 4);
    helper.assertTrue(before == 1024 && lootingBook != null && library.pool() == before - 40,
        "Looting IV (base 5, never deposited) did not cost 40 lumen");
    // Break and restore keeps exactly one pool.
    long stored = library.pool();
    try (var session = new Session(helper, "AtlasFullQA")) {
      var player = session.player;
      level.destroyBlock(center, true, player);
      var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2),
          entity -> entity.getItem().is(ApotheosisContent.ATLAS_LIBRARY.get().asItem()));
      helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().has(DataComponents.BLOCK_ENTITY_DATA),
          "Breaking did not drop exactly one library with its pool");
      var carried = drops.getFirst().getItem().copy();
      drops.getFirst().discard();
      player.teleportTo(center.getX() + 0.5, center.getY() + 3, center.getZ() + 0.5);
      player.setItemInHand(InteractionHand.MAIN_HAND, carried);
      player.gameMode.useItemOn(player, level, carried, InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(center.below()).add(0, 0.5, 0), Direction.UP, center.below(), false));
      helper.assertTrue(level.getBlockEntity(center) instanceof AtlasLibraryBlockEntity restored
          && restored.pool() == stored && player.getMainHandItem().isEmpty(),
          "Placing the drop did not restore the pool exactly once");
    }
    helper.succeed();
  }

  private static boolean done(ServerPlayer player, ApotheosisTiers.Tier tier) {
    var holder = player.server.getAdvancements().get(tier.advancement);
    return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean apotheosisUnlocked(Player player, String tier) throws ReflectiveOperationException {
    Class worldTier = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
    Object value = Enum.valueOf(worldTier, tier);
    return (boolean) worldTier.getMethod("isUnlocked", Player.class, worldTier).invoke(null, player, value);
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void worldTierOverridesOpenWhenTheTeamClosesActs(GameTestHelper helper) throws Exception {
    requireSuite();
    var server = helper.getLevel().getServer();
    for (var tier : ApotheosisTiers.Tier.values()) {
      var holder = server.getAdvancements().get(tier.advancement);
      helper.assertTrue(holder != null, "Missing " + tier.advancement);
      var criteria = holder.value().criteria();
      var campaignCriterion = criteria.get("campaign");
      var trigger = campaignCriterion == null ? null
          : BuiltInRegistries.TRIGGER_TYPES.getKey(campaignCriterion.trigger());
      String expectedTrigger = tier == ApotheosisTiers.Tier.HAVEN ? "minecraft:tick" : "minecraft:impossible";
      var description = holder.value().display().orElseThrow().getDescription().getContents();
      helper.assertTrue(criteria.keySet().equals(java.util.Set.of("campaign"))
          && trigger != null && trigger.toString().equals(expectedTrigger)
          && description instanceof TranslatableContents translatable
          && translatable.getKey().equals("entrelumen.apotheosis.tier." + tier.name().toLowerCase(java.util.Locale.ROOT) + ".desc"),
          "Pack override did not take precedence for " + tier.advancement);
    }
    var session = new Session(helper, "TierFullQA");
    var player = session.player;
    var campaign = Entrelumen.current(player);
    campaign.act = 2;
    campaign.completed.addAll(Projects.forAct(2));
    helper.assertTrue(!apotheosisUnlocked(player, "FRONTIER"), "Frontier was open before Act II closed");
    helper.assertTrue(Entrelumen.advance(player) == 1 && campaign.act == 3, "Closing Act II failed");
    // No direct sync: the companion's own server tick must grant Frontier.
    helper.runAfterDelay(40, () -> {
      try {
        helper.assertTrue(done(player, ApotheosisTiers.Tier.FRONTIER) && apotheosisUnlocked(player, "FRONTIER")
            && apotheosisUnlocked(player, "HAVEN") && !apotheosisUnlocked(player, "ASCENT"),
            "Apotheosis does not see Frontier (and only Frontier) after Act II");
        campaign.completed.addAll(Projects.forAct(3));
        helper.assertTrue(Entrelumen.advance(player) == 1 && campaign.act == 4, "Closing Act III failed");
        ApotheosisTiers.sync(player);
        helper.assertTrue(apotheosisUnlocked(player, "ASCENT") && !apotheosisUnlocked(player, "SUMMIT"),
            "Ascent did not open after Act III");
        campaign.act = 6;
        ApotheosisTiers.sync(player);
        helper.assertTrue(apotheosisUnlocked(player, "SUMMIT") && !apotheosisUnlocked(player, "PINNACLE"),
            "Summit did not open after Act V, or Pinnacle opened before the Ark");
        campaign.completed.add(CampaignMilestones.LAST_HORIZON);
        helper.assertTrue(ApotheosisTiers.sync(player) == 1 && apotheosisUnlocked(player, "PINNACLE")
            && ApotheosisTiers.sync(player) == 0, "Pinnacle was not granted exactly once after the Ark");
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException(error);
      } finally {
        session.close();
      }
      helper.succeed();
    });
  }

  private static CompoundTag spawnerTag(GameTestHelper helper, BlockPos pos) {
    return helper.getLevel().getBlockEntity(pos).saveWithoutMetadata(helper.getLevel().registryAccess());
  }

  private static BlockHitResult hit(BlockPos pos) {
    return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void spawnerAugmentsModifyRealSpawnersAndRunesAreGone(GameTestHelper helper) throws Exception {
    requireSuite();
    var level = helper.getLevel();
    var pos = helper.absolutePos(new BlockPos(2, 2, 2));
    clear(helper, pos);
    level.setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
    helper.assertTrue(level.getBlockEntity(pos).getClass().getName().endsWith("ApothSpawnerTile"),
        "Vanilla spawner is not the Apothic spawner tile");
    try (var session = new Session(helper, "AugmentQA")) {
      var player = session.player;
      player.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
      int count = spawnerTag(helper, pos).getShort("SpawnCount");
      // The original Apothic item no longer applies the modifier.
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FERMENTED_SPIDER_EYE));
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
      helper.assertTrue(spawnerTag(helper, pos).getShort("SpawnCount") == count
          && player.getMainHandItem().getCount() == 1, "The native modifier item still changed the spawner");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("entrelumen:augment_spawn_count"), 2));
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
      helper.assertTrue(spawnerTag(helper, pos).getShort("SpawnCount") == count + 2
          && player.getMainHandItem().getCount() == 1, "Spawn count augment did not add 2 once");
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.QUARTZ));
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
      helper.assertTrue(spawnerTag(helper, pos).getShort("SpawnCount") == count
          && player.getMainHandItem().isEmpty() && player.getOffhandItem().getCount() == 1,
          "Quartz inverse did not revert the count, or consumed the quartz");
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("entrelumen:augment_ignore_players"), 2));
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
      helper.assertTrue(spawnerTag(helper, pos).getCompound("stats").getBoolean("apothic_spawners:ignore_players"),
          "Solitude augment did not set ignore_players");
      player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.QUARTZ));
      player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos));
      helper.assertTrue(!spawnerTag(helper, pos).getCompound("stats").getBoolean("apothic_spawners:ignore_players"),
          "Solitude inverse did not clear ignore_players");
    }
    // Loaded modifier recipes: exactly the 32 Apothic Spawners paths, each on an ENTRELUMEN augment.
    var manager = level.getServer().getRecipeManager();
    var type = (RecipeType<?>) BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse("apothic_spawners:spawner_modifier"));
    Map<String, Boolean> modifiers = new HashMap<>();
    for (RecipeHolder<?> holder : manager.getRecipes()) {
      if (holder.value().getType() != type) continue;
      Ingredient mainhand = (Ingredient) holder.value().getClass().getMethod("getMainhandInput").invoke(holder.value());
      var items = mainhand.getItems();
      modifiers.put(holder.id().toString(), items.length == 1
          && BuiltInRegistries.ITEM.getKey(items[0].getItem()).getPath().startsWith("augment_")
          && BuiltInRegistries.ITEM.getKey(items[0].getItem()).getNamespace().equals("entrelumen"));
    }
    helper.assertTrue(modifiers.size() == 32 && modifiers.values().stream().allMatch(Boolean::booleanValue)
        && modifiers.keySet().stream().allMatch(id -> id.startsWith("apothic_spawners:spawner_modifiers/")),
        "Loaded spawner modifiers are not exactly the 32 augment recipes: " + modifiers);
    for (String rune : List.of("apotheosis:spawner_rune", "apotheosis:infused_spawner_rune",
        "apotheosis:no_ai_spawner_rune", "apotheosis:pinnacle_spawner_upgrade_rune"))
      helper.assertTrue(manager.byKey(ResourceLocation.parse(rune)).isEmpty(), "Rune recipe still loaded: " + rune);
    helper.succeed();
  }

  private static JsonArray constant(String script, String name) {
    var match = Pattern.compile("^const entrelumenApotheosis" + name + " = (.*);$", Pattern.MULTILINE).matcher(script);
    if (!match.find()) throw new IllegalStateException("Generated script lacks " + name);
    return JsonParser.parseString(match.group(1)).getAsJsonArray();
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void kubejsApotheosisRecipesMatchTheGeneratedScript(GameTestHelper helper) throws Exception {
    requireSuite();
    var script = Files.readString(FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/entrelumen_apotheosis_balance.js"));
    var manager = helper.getLevel().getServer().getRecipeManager();
    var registries = helper.getLevel().registryAccess();
    int edits = 0, removals = 0, additions = 0;
    for (var row : constant(script, "Rows")) {
      var object = row.getAsJsonObject();
      var recipe = manager.byKey(ResourceLocation.parse(object.get("id").getAsString())).orElseThrow(
          () -> new IllegalStateException("Edited recipe missing: " + object.get("id")));
      var component = new ItemStack(item(object.get("component").getAsString()));
      helper.assertTrue(recipe.value().getIngredients().stream().anyMatch(i -> i.test(component))
          && BuiltInRegistries.ITEM.getKey(recipe.value().getResultItem(registries).getItem()).toString()
              .equals(object.get("output").getAsString()),
          "Loaded recipe lacks its staged component: " + object.get("id"));
      edits++;
    }
    for (var id : constant(script, "Removals")) {
      helper.assertTrue(manager.byKey(ResourceLocation.parse(id.getAsString())).isEmpty(), "Removed recipe loaded: " + id);
      removals++;
    }
    for (var row : constant(script, "Additions")) {
      var object = row.getAsJsonObject();
      Recipe<?> recipe = manager.byKey(ResourceLocation.parse(object.get("id").getAsString())).orElseThrow(
          () -> new IllegalStateException("Added recipe missing: " + object.get("id"))).value();
      helper.assertTrue(BuiltInRegistries.ITEM.getKey(recipe.getResultItem(registries).getItem()).toString()
          .equals(object.get("output").getAsString()), "Added recipe has the wrong output: " + object.get("id"));
      additions++;
    }
    helper.assertTrue(edits == 4 && removals == 46 && additions == 21,
        "Unexpected generated counts " + edits + "/" + removals + "/" + additions);
    helper.succeed();
  }
}
