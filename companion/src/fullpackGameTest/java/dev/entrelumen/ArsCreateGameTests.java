package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** QA-JAR-only execution of Ars Technica's actual Source Motor and Create shaft. */
@GameTestHolder("entrelumen")
@PrefixGameTestTemplate(false)
public final class ArsCreateGameTests {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int RPM = 32;
  private static final int MAX_WAIT = 120;

  private record Machine(BlockPos jarPos, BlockPos motorPos, BlockPos shaftPos,
      BlockEntity jar, BlockEntity motor, BlockEntity shaft, int sourceCost, int seededSource) {}

  private ArsCreateGameTests() {}

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void finiteArsSourceDrivesAndStopsCreateShaft(GameTestHelper helper) {
    for (String mod : List.of("ars_nouveau", "ars_technica", "create"))
      helper.assertTrue(ModList.get().isLoaded(mod), "Required real mod is absent: " + mod);
    helper.assertTrue(!ModList.get().isLoaded("entrelumen_gametest_fixture"),
        "Fullpack QA cannot run against the synthetic fixture");

    BlockPos jarPos = helper.absolutePos(new BlockPos(1, 1, 2));
    BlockPos motorPos = helper.absolutePos(new BlockPos(2, 1, 2));
    BlockPos shaftPos = helper.absolutePos(new BlockPos(3, 1, 2));
    var level = helper.getLevel();
    level.setBlockAndUpdate(jarPos, block("ars_nouveau:source_jar").defaultBlockState());
    level.setBlockAndUpdate(motorPos, block("ars_technica:source_motor")
        .defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
    level.setBlockAndUpdate(shaftPos, block("create:shaft")
        .defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));

    BlockEntity jar = entity(helper, jarPos, "SourceJarTile");
    BlockEntity motor = entity(helper, motorPos, "SourceMotorBlockEntity");
    BlockEntity shaft = entity(helper, shaftPos, "BracketedKineticBlockEntity");
    // Configure the native Create scroll behaviour, just as the motor's GUI does. The
    // motor itself decides when to draw Source and how to propagate rotation.
    Object speedControl = declaredField(motor, "generatedSpeed");
    call(speedControl, "setValue", new Class<?>[] {int.class}, RPM);
    int cost = number(callDeclared(motor, "getSourceCost")).intValue();
    helper.assertTrue(cost > 0 && cost <= 5_000,
        "Configured native motor cost cannot fit two cycles in a finite Source Jar: " + cost);
    int seed = 2 * cost;
    int stored = number(call(jar, "setSource", new Class<?>[] {int.class}, seed)).intValue();
    helper.assertTrue(stored == seed && source(jar) == seed,
        "Native Source Jar rejected the bounded starting Source: " + seed);
    helper.assertTrue(speed(motor) == 0 && speed(shaft) == 0,
        "Unfueled native kinetic chain started before Source consumption");

    awaitRunning(helper, new Machine(jarPos, motorPos, shaftPos, jar, motor, shaft, cost, seed), 0);
  }

  private static void awaitRunning(GameTestHelper helper, Machine machine, int elapsed) {
    assertPlaced(helper, machine);
    int remaining = source(machine.jar());
    float motorSpeed = speed(machine.motor());
    float shaftSpeed = speed(machine.shaft());
    if (remaining < machine.seededSource() && motorSpeed != 0 && shaftSpeed != 0) {
      helper.assertTrue(remaining >= 0 && remaining <= machine.seededSource() - machine.sourceCost(),
          "Source changed outside a native motor draw: " + remaining);
      helper.assertTrue((Boolean) call(machine.motor(), "isFueled"),
          "Motor rotates without its native fueled state");
      helper.assertTrue(Math.abs(motorSpeed) == RPM && Math.abs(shaftSpeed) == RPM,
          "Real Create shaft did not inherit the configured motor RPM: motor="
              + motorSpeed + " shaft=" + shaftSpeed);
      helper.assertTrue(!(Boolean) call(machine.motor(), "isOverStressed"),
          "Motor is overstressed in the isolated native kinetic chain");
      Object motorNetwork = call(machine.motor(), "getOrCreateNetwork");
      Object shaftNetwork = call(machine.shaft(), "getOrCreateNetwork");
      helper.assertTrue(motorNetwork == shaftNetwork,
          "Motor and shaft do not share a real Create kinetic network");
      @SuppressWarnings("unchecked")
      Map<Object, Float> sources = (Map<Object, Float>) publicField(motorNetwork, "sources");
      float capacity = number(call(motorNetwork, "calculateCapacity")).floatValue();
      helper.assertTrue(sources.containsKey(machine.motor()) && capacity > 0,
          "The real Create network has no positive Source Motor capacity: " + capacity);
      awaitStopped(helper, machine, 0, motorSpeed, capacity);
      return;
    }
    helper.assertTrue(elapsed < MAX_WAIT / 2,
        "Native motor did not consume Source and power its Create shaft; remaining="
            + remaining + " motorRPM=" + motorSpeed + " shaftRPM=" + shaftSpeed);
    helper.runAfterDelay(2, () -> awaitRunning(helper, machine, elapsed + 2));
  }

  private static void awaitStopped(GameTestHelper helper, Machine machine, int elapsed,
      float runningSpeed, float runningCapacity) {
    assertPlaced(helper, machine);
    int remaining = source(machine.jar());
    float motorSpeed = speed(machine.motor());
    float shaftSpeed = speed(machine.shaft());
    if (remaining == 0 && motorSpeed == 0 && shaftSpeed == 0
        && !(Boolean) call(machine.motor(), "isFueled")) {
      LOGGER.info("ENTRELUMEN_ARS_CREATE_SOURCE_MOTOR seeded={} costPerSecond={} runningRPM={} "
              + "networkCapacity={} stoppedRPM=0", machine.seededSource(), machine.sourceCost(),
          runningSpeed, runningCapacity);
      helper.succeed();
      return;
    }
    helper.assertTrue(remaining >= 0 && remaining <= machine.seededSource(),
        "Native Source Jar gained Source while driving an isolated motor: " + remaining);
    helper.assertTrue(elapsed < MAX_WAIT,
        "Native Source Motor failed to stop after finite Source depletion; remaining="
            + remaining + " motorRPM=" + motorSpeed + " shaftRPM=" + shaftSpeed);
    helper.runAfterDelay(2,
        () -> awaitStopped(helper, machine, elapsed + 2, runningSpeed, runningCapacity));
  }

  private static void assertPlaced(GameTestHelper helper, Machine machine) {
    helper.assertTrue(helper.getLevel().getBlockEntity(machine.jarPos()) == machine.jar()
            && helper.getLevel().getBlockEntity(machine.motorPos()) == machine.motor()
            && helper.getLevel().getBlockEntity(machine.shaftPos()) == machine.shaft(),
        "The native Ars/Create blocks were replaced during the power test");
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Required native block is absent: " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }

  private static BlockEntity entity(GameTestHelper helper, BlockPos pos, String typeName) {
    BlockEntity entity = helper.getLevel().getBlockEntity(pos);
    helper.assertTrue(entity != null && entity.getClass().getSimpleName().equals(typeName),
        "Expected real " + typeName + " at " + pos + ", got " + entity);
    return entity;
  }

  private static int source(BlockEntity jar) {
    return number(call(jar, "getSource")).intValue();
  }

  private static float speed(BlockEntity kinetic) {
    return number(call(kinetic, "getSpeed")).floatValue();
  }

  private static Number number(Object value) {
    if (!(value instanceof Number number))
      throw new IllegalStateException("Native Ars/Create API returned a nonnumeric value: " + value);
    return number;
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
      throw new IllegalStateException("Native Ars Technica field changed: " + name, ex);
    }
  }

  private static Object callDeclared(Object target, String name) {
    try {
      Method method = target.getClass().getDeclaredMethod(name);
      method.setAccessible(true);
      return method.invoke(target);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Ars Technica method changed: " + name, ex);
    }
  }

  private static Object call(Object target, String name) {
    return call(target, name, new Class<?>[0]);
  }

  private static Object call(Object target, String name, Class<?>[] parameterTypes, Object... args) {
    try {
      return target.getClass().getMethod(name, parameterTypes).invoke(target, args);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Native Ars/Create method changed: " + name, ex);
    }
  }
}
