package dev.entrelumen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * QA-JAR-only checks of the mods Elias chose in rounds 1-3 of the mod ping-pong
 * (docs/design/mod-pingpong.md): the batch is loaded, the rejected builds are not, Carry On refuses
 * the pack's sensitive blocks and Almost Unified gives duplicated crops one item. Recipe stages and
 * removals are checked at load by entrelumen_pingpong_balance.js.
 */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ModPingpongFullpackGameTests {
  static final List<String> BATCH = List.of("modern_industrialization", "oritech", "enderio", "mahoutsukai",
      "ad_astra", "deep_aether", "eternal_starlight", "undergarden", "naturalist", "friendsandfoes",
      "creeperoverhaul", "endermanoverhaul", "mowziesmobs", "refinedstorage", "refinedstorage_mekanism_integration",
      "mekanismcovers", "mekanisticrouters", "pamhc2crops", "croptopia", "twilightdelight", "ends_delight",
      "mynethersdelight", "aethersdelight", "createdeco", "littletiles", "another_furniture", "carryon",
      "repurposed_structures");
  /** No working NeoForge 1.21.1 server build: GregTech CEu Modern 7.0.2 and Chisels & Bits 21.1.33. */
  static final List<String> REJECTED = List.of("gtceu", "chiselsandbits");
  static final List<String> FORBIDDEN_BLOCKS = List.of("entrelumen:peace_altar", "entrelumen:renewal_altar",
      "entrelumen:ark_controller", "entrelumen:engineering_module", "entrelumen:survey_station",
      "entrelumen:heliodor_pedestal", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
      "lootr:lootr_chest", "ae2:controller", "enderio:conduit", "aether:treasure_chest");
  static final List<String> ALLOWED_BLOCKS = List.of("minecraft:chest", "minecraft:barrel");

  private ModPingpongFullpackGameTests() {}

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void pingpongBatchLoadedWithoutRejectedBuilds(GameTestHelper helper) {
    List<String> missing = BATCH.stream().filter(mod -> !ModList.get().isLoaded(mod)).toList();
    List<String> present = REJECTED.stream().filter(mod -> ModList.get().isLoaded(mod)).toList();
    helper.assertTrue(missing.isEmpty(), "Mod ping-pong batch missing: " + missing);
    helper.assertTrue(present.isEmpty(), "Rejected builds loaded: " + present);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void carryOnRefusesSensitiveBlocks(GameTestHelper helper) throws ReflectiveOperationException {
    Method permitted = Class.forName("tschipp.carryon.common.config.ListHandler").getMethod("isPermitted", Block.class);
    helper.assertTrue(Modifier.isStatic(permitted.getModifiers()), "ListHandler.isPermitted is no longer static");
    List<String> carried = new ArrayList<>();
    List<String> absent = new ArrayList<>();
    for (String name : FORBIDDEN_BLOCKS) {
      var block = BuiltInRegistries.BLOCK.getOptional(id(name));
      if (block.isEmpty()) absent.add(name);
      else if ((boolean) permitted.invoke(null, block.get())) carried.add(name);
    }
    List<String> refused = new ArrayList<>();
    for (String name : ALLOWED_BLOCKS)
      if (!(boolean) permitted.invoke(null, BuiltInRegistries.BLOCK.get(id(name)))) refused.add(name);
    helper.assertTrue(absent.isEmpty(), "Blacklist test blocks not registered: " + absent);
    helper.assertTrue(carried.isEmpty(), "Carry On may pick up: " + carried);
    helper.assertTrue(refused.isEmpty(), "Carry On refuses ordinary containers: " + refused);
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void unifiedCropsDropFarmersDelightTomatoes(GameTestHelper helper) {
    var crop = BuiltInRegistries.BLOCK.getOptional(id("croptopia:tomato_crop"));
    helper.assertTrue(crop.isPresent(), "croptopia:tomato_crop is not registered");
    BlockState state = crop.get().defaultBlockState();
    var age = state.getBlock().getStateDefinition().getProperty("age");
    helper.assertTrue(age instanceof IntegerProperty, "croptopia:tomato_crop has no integer age");
    IntegerProperty ageProperty = (IntegerProperty) age;
    state = state.setValue(ageProperty, Collections.max(ageProperty.getPossibleValues()));
    BlockPos pos = helper.absolutePos(BlockPos.ZERO);
    List<ItemStack> drops = state.getDrops(new LootParams.Builder(helper.getLevel())
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY));
    Set<String> items = new TreeSet<>();
    drops.forEach(stack -> items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    helper.assertTrue(items.contains("farmersdelight:tomato"), "Mature Croptopia tomato dropped " + items);
    helper.assertFalse(items.contains("croptopia:tomato"), "Croptopia tomato was not unified: " + items);
    helper.succeed();
  }
}
