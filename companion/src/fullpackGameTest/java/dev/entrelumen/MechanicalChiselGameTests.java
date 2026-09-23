package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** QA-JAR-only execution of the loaded Mechanical Chisel with a real Chipped filter. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class MechanicalChiselGameTests {
  private static final int RPM = 32;
  private static final int NO_POWER_TICKS = 12;
  private static final int STABLE_TICKS = 40;

  private record Machine(BlockPos chiselPos, BlockPos motorPos, BlockEntity chisel,
      BlockEntity motor, IItemHandler inventory, Item input, Item output) {}

  private MechanicalChiselGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void poweredMechanicalChiselConvertsRealChippedStoneOnce(GameTestHelper helper) {
    requireNativeMods();
    var level = helper.getLevel();
    Item input = item("minecraft:stone");
    Item output = item("chipped:carved_stone");
    BlockPos motorPos = helper.absolutePos(new BlockPos(1, 1, 2));
    BlockPos chiselPos = helper.absolutePos(new BlockPos(2, 1, 2));
    // Catch the native Z-axis ejection in either rotation direction during the stability check.
    for (int dz = -2; dz <= 2; dz++)
      level.setBlockAndUpdate(chiselPos.offset(0, -1, dz), Blocks.STONE.defaultBlockState());
    Block chiselBlock = block("rechiseledcreate:mechanical_chisel");
    var axis = chiselBlock.getStateDefinition().getProperty("axis_along_first");
    helper.assertTrue(axis instanceof BooleanProperty,
        "Native Mechanical Chisel lost its horizontal kinetic axis property");
    level.setBlockAndUpdate(chiselPos, chiselBlock.defaultBlockState()
        .setValue(BlockStateProperties.FACING, Direction.UP)
        .setValue((BooleanProperty) axis, true));
    BlockEntity chisel = entity(helper, chiselPos,
        "com.supermartijn642.rechiseled.create.mechanical_chisel.MechanicalChiselBlockEntity");
    IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK,
        chiselPos, Direction.UP);
    helper.assertTrue(inventory != null && inventory.getSlots() == 32
            && inventory.getClass().getName().equals(
                "com.simibubi.create.content.processing.recipe.ProcessingInventory"),
        "Mechanical Chisel did not expose its native Create processing inventory");
    assertEmpty(helper, inventory);
    helper.assertTrue(drops(helper, chiselPos).isEmpty(),
        "Mechanical Chisel fixture has pre-existing item drops");

    Object filtering = declaredField(chisel, "filtering");
    helper.assertTrue(Boolean.TRUE.equals(call(filtering, "setFilter",
        new Class<?>[] {ItemStack.class}, new ItemStack(output))),
        "Native Create filter rejected chipped:carved_stone");
    ItemStack selected = (ItemStack) call(filtering, "getFilter");
    helper.assertTrue(selected.is(output) && selected.getCount() == 1,
        "Mechanical Chisel did not retain the real Chipped output filter");

    helper.assertTrue(inventory.insertItem(0, new ItemStack(input), false).isEmpty(),
        "Native Mechanical Chisel capability refused one stone input");
    assertOnlySlot(helper, inventory, 0, input);
    @SuppressWarnings("unchecked")
    List<ItemStack> options = (List<ItemStack>) callDeclared(chisel, "getRecipes");
    helper.assertTrue(!options.isEmpty(),
        "Mechanical Chisel found no loaded stone-to-Chipped conversion with its real filter");
    for (ItemStack option : options)
      helper.assertTrue(option.is(output) && option.getCount() == 1,
          "Mechanical Chisel can select a different or non-1:1 filtered result: " + option);
    float unpoweredTime = floatField(inventory, "remainingTime");
    helper.assertTrue(unpoweredTime > 0 && !booleanField(inventory, "appliedRecipe")
            && speed(chisel) == 0,
        "Stone began processing without a kinetic source");
    helper.runAfterDelay(NO_POWER_TICKS,
        () -> powerAfterUnpoweredCheck(helper, motorPos, chiselPos, chisel,
            inventory, input, output, unpoweredTime));
  }

  private static void powerAfterUnpoweredCheck(GameTestHelper helper, BlockPos motorPos,
      BlockPos chiselPos, BlockEntity chisel, IItemHandler inventory, Item input,
      Item output, float unpoweredTime) {
    helper.assertTrue(helper.getLevel().getBlockEntity(chiselPos) == chisel
            && speed(chisel) == 0 && floatField(inventory, "remainingTime") == unpoweredTime
            && !booleanField(inventory, "appliedRecipe"),
        "Mechanical Chisel advanced its native recipe without kinetic power");
    assertOnlySlot(helper, inventory, 0, input);
    helper.assertTrue(drops(helper, chiselPos).isEmpty(),
        "Unpowered Mechanical Chisel emitted an item");

    helper.getLevel().setBlockAndUpdate(motorPos, block("create:creative_motor")
        .defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
    BlockEntity motor = entity(helper, motorPos,
        "com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
    // The native scroll behaviour updates Create's actual kinetic network.
    call(publicField(motor, "generatedSpeed"), "setValue", new Class<?>[] {int.class}, RPM);
    awaitPower(helper, new Machine(chiselPos, motorPos, chisel, motor,
        inventory, input, output), 0);
  }

  private static void awaitPower(GameTestHelper helper, Machine machine, int waited) {
    assertPlaced(helper, machine);
    float motorSpeed = speed(machine.motor());
    float chiselSpeed = speed(machine.chisel());
    if (Math.abs(motorSpeed) == RPM && Math.abs(chiselSpeed) == RPM) {
      Object motorNetwork = call(machine.motor(), "getOrCreateNetwork");
      helper.assertTrue(motorNetwork != null
              && motorNetwork == call(machine.chisel(), "getOrCreateNetwork")
              && !Boolean.TRUE.equals(call(machine.chisel(), "isOverStressed")),
          "Motor and Mechanical Chisel lack one unstressed Create kinetic network");
      awaitOutput(helper, machine, 0, false);
      return;
    }
    helper.assertTrue(waited < 80,
        "Native motor did not rotate the Mechanical Chisel at 32 RPM: motor="
            + motorSpeed + " chisel=" + chiselSpeed);
    helper.runAfterDelay(2, () -> awaitPower(helper, machine, waited + 2));
  }

  private static void awaitOutput(GameTestHelper helper, Machine machine, int waited,
      boolean sawAppliedRecipe) {
    assertPlaced(helper, machine);
    helper.assertTrue(Math.abs(speed(machine.chisel())) == RPM,
        "Mechanical Chisel lost kinetic power during conversion");
    boolean applied = booleanField(machine.inventory(), "appliedRecipe");
    if (applied) assertOnlySlot(helper, machine.inventory(), 1, machine.output());
    List<ItemEntity> drops = drops(helper, machine.chiselPos());
    if (!drops.isEmpty()) {
      helper.assertTrue(sawAppliedRecipe || applied,
          "Mechanical Chisel emitted an item without a visible native recipe application");
      assertSingleOutput(helper, drops, machine.output());
      ItemEntity emitted = drops.getFirst();
      assertEmpty(helper, machine.inventory());
      helper.runAfterDelay(STABLE_TICKS, () -> {
        assertPlaced(helper, machine);
        helper.assertTrue(Math.abs(speed(machine.chisel())) == RPM,
            "Mechanical Chisel lost power before the duplicate check");
        assertEmpty(helper, machine.inventory());
        List<ItemEntity> finalDrops = drops(helper, machine.chiselPos());
        assertSingleOutput(helper, finalDrops, machine.output());
        helper.assertTrue(finalDrops.getFirst().getUUID().equals(emitted.getUUID()),
            "Mechanical Chisel's original output disappeared or was replaced");
        LogUtils.getLogger().info("ENTRELUMEN_MECHANICAL_CHISEL input=minecraft:stone "
            + "filter=chipped:carved_stone motorRPM={} output=1:1 unpowered=false duplicate=false",
            RPM);
        helper.succeed();
      });
      return;
    }
    if (!applied) assertOnlySlot(helper, machine.inventory(), 0, machine.input());
    helper.assertTrue(waited < 120,
        "Powered Mechanical Chisel did not eject exactly one Chipped output; applied="
            + applied + " remaining=" + floatField(machine.inventory(), "remainingTime"));
    helper.runAfterDelay(2,
        () -> awaitOutput(helper, machine, waited + 2, sawAppliedRecipe || applied));
  }

  private static void assertPlaced(GameTestHelper helper, Machine machine) {
    helper.assertTrue(helper.getLevel().getBlockEntity(machine.chiselPos()) == machine.chisel()
            && helper.getLevel().getBlockEntity(machine.motorPos()) == machine.motor(),
        "Native Mechanical Chisel or motor was replaced during the test");
  }

  private static void assertSingleOutput(GameTestHelper helper, List<ItemEntity> drops,
      Item output) {
    helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(output)
            && drops.getFirst().getItem().getCount() == 1,
        "Mechanical Chisel emitted an extra, missing, or wrong item: "
            + drops.stream().map(e -> e.getItem().toString()).toList());
  }

  private static void assertOnlySlot(GameTestHelper helper, IItemHandler inventory,
      int occupied, Item item) {
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      helper.assertTrue(slot == occupied ? stack.is(item) && stack.getCount() == 1
              : stack.isEmpty(),
          "Native Mechanical Chisel inventory has unexpected slot " + slot + ": " + stack);
    }
  }

  private static void assertEmpty(GameTestHelper helper, IItemHandler inventory) {
    for (int slot = 0; slot < inventory.getSlots(); slot++)
      helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(),
          "Mechanical Chisel kept or duplicated an item in slot " + slot);
  }

  private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
    return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
        new AABB(pos).inflate(2.0), ItemEntity::isAlive);
  }

  private static void requireNativeMods() {
    if (ModList.get().isLoaded("entrelumen_gametest_fixture"))
      throw new IllegalStateException("Mechanical Chisel QA requires the real full pack");
    for (String mod : List.of("rechiseledcreate", "rechiseled_chipped", "rechiseled",
        "chipped", "create"))
      if (!ModList.get().isLoaded(mod))
        throw new IllegalStateException("Required real Mechanical Chisel mod is absent: " + mod);
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key)
        || BuiltInRegistries.BLOCK.get(key) == Blocks.AIR)
      throw new IllegalStateException("Required native Mechanical Chisel block is absent: " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key)
        || BuiltInRegistries.ITEM.get(key) == Items.AIR)
      throw new IllegalStateException("Required native Mechanical Chisel item is absent: " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static BlockEntity entity(GameTestHelper helper, BlockPos pos, String className) {
    BlockEntity entity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(entity != null && entity.getClass().getName().equals(className),
        "Expected native " + className + " at " + pos + ", got " + entity);
    return entity;
  }

  private static float speed(BlockEntity kinetic) {
    return ((Number) call(kinetic, "getSpeed")).floatValue();
  }

  private static float floatField(Object target, String name) {
    return ((Number) publicField(target, name)).floatValue();
  }

  private static boolean booleanField(Object target, String name) {
    return (Boolean) publicField(target, name);
  }

  private static Object publicField(Object target, String name) {
    try {
      return target.getClass().getField(name).get(target);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Create field changed: " + name, ex);
    }
  }

  private static Object declaredField(Object target, String name) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Mechanical Chisel field changed: " + name, ex);
    }
  }

  private static Object callDeclared(Object target, String name) {
    try {
      Method method = target.getClass().getDeclaredMethod(name);
      method.setAccessible(true);
      return method.invoke(target);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Mechanical Chisel method changed: " + name, ex);
    }
  }

  private static Object call(Object target, String name) {
    return call(target, name, new Class<?>[0]);
  }

  private static Object call(Object target, String name, Class<?>[] parameters, Object... args) {
    try {
      return target.getClass().getMethod(name, parameters).invoke(target, args);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Create method changed: " + name, ex);
    }
  }
}
