package dev.entrelumen;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * What the ruins read from Create 6 without a compile dependency: rotation speeds, networks and
 * overstress of kinetic block entities, a mechanical bearing's angle, and the stress values of the
 * pack's config (the public {@code com.simibubi.create.api.stress.BlockStressValues}). Every call
 * degrades to "nothing turns" when Create is absent or its classes changed; the first failure is
 * logged once.
 */
public final class CreateCompat {
  private static final Logger LOGGER = LogUtils.getLogger();
  static final String KINETIC = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
  static final String BEARING = "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity";
  static final String STRESS = "com.simibubi.create.api.stress.BlockStressValues";
  private static final Map<String, Optional<Class<?>>> CLASSES = new HashMap<>();
  private static final Map<String, Optional<Method>> METHODS = new HashMap<>();
  private static final Map<String, Optional<Field>> FIELDS = new HashMap<>();
  private static boolean warned;

  private CreateCompat() {}

  public static boolean loaded() {
    return ModList.get() != null && ModList.get().isLoaded("create");
  }

  private static synchronized Optional<Class<?>> type(String name) {
    return CLASSES.computeIfAbsent(name, key -> {
      try {
        return Optional.of(Class.forName(key, false, CreateCompat.class.getClassLoader()));
      } catch (ClassNotFoundException | LinkageError e) {
        return Optional.empty();
      }
    });
  }

  private static synchronized Optional<Method> method(String owner, String name, Class<?>... parameters) {
    return METHODS.computeIfAbsent(owner + "#" + name + parameters.length, key -> type(owner).flatMap(type -> {
      try {
        return Optional.of(type.getMethod(name, parameters));
      } catch (NoSuchMethodException e) {
        warn(owner + "." + name, e);
        return Optional.empty();
      }
    }));
  }

  private static synchronized Optional<Field> field(String owner, String name) {
    return FIELDS.computeIfAbsent(owner + "." + name, key -> type(owner).flatMap(type -> {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return Optional.of(field);
      } catch (ReflectiveOperationException | RuntimeException e) {
        warn(owner + "." + name, e);
        return Optional.empty();
      }
    }));
  }

  private static void warn(String what, Throwable e) {
    if (warned) return;
    warned = true;
    LOGGER.warn("Create compat: {} is not reachable ({}); the ruin engines stay still", what, e.toString());
  }

  private static Object call(Method method, Object target, Object... args) throws ReflectiveOperationException {
    return method.invoke(target, args);
  }

  /** The kinetic block entity at a position, or null. */
  @Nullable
  public static BlockEntity kinetic(Level level, BlockPos pos) {
    BlockEntity entity = level.getBlockEntity(pos);
    return entity != null && type(KINETIC).map(t -> t.isInstance(entity)).orElse(false) ? entity : null;
  }

  /** Create's speed (0 when overstressed), in RPM along the block's axis; 0 without a kinetic block. */
  public static float speed(Level level, BlockPos pos) {
    return number(kinetic(level, pos), "getSpeed");
  }

  /** The speed the block would turn at without overstress. */
  public static float theoreticalSpeed(Level level, BlockPos pos) {
    return number(kinetic(level, pos), "getTheoreticalSpeed");
  }

  public static boolean overStressed(Level level, BlockPos pos) {
    BlockEntity entity = kinetic(level, pos);
    if (entity == null) return false;
    try {
      var method = method(KINETIC, "isOverStressed");
      return method.isPresent() && (boolean) call(method.get(), entity);
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn("isOverStressed", e);
      return false;
    }
  }

  /** The id of the kinetic network the block belongs to, or null. */
  @Nullable
  public static Long network(Level level, BlockPos pos) {
    BlockEntity entity = kinetic(level, pos);
    if (entity == null) return null;
    try {
      var network = field(KINETIC, "network");
      return network.isPresent() ? (Long) network.get().get(entity) : null;
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn("network", e);
      return null;
    }
  }

  /** The stress and capacity the block last heard from its network (SU). */
  public static float[] stress(Level level, BlockPos pos) {
    BlockEntity entity = kinetic(level, pos);
    if (entity == null) return new float[] {0, 0};
    try {
      var stress = field(KINETIC, "stress");
      var capacity = field(KINETIC, "capacity");
      if (stress.isEmpty() || capacity.isEmpty()) return new float[] {0, 0};
      return new float[] {stress.get().getFloat(entity), capacity.get().getFloat(entity)};
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn("stress", e);
      return new float[] {0, 0};
    }
  }

  private static float number(@Nullable BlockEntity entity, String name) {
    if (entity == null) return 0;
    try {
      var method = method(KINETIC, name);
      return method.isPresent() ? ((Number) call(method.get(), entity)).floatValue() : 0;
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn(name, e);
      return 0;
    }
  }

  // ---- Mechanical bearings -----------------------------------------------------------------

  /** A mechanical bearing's state: whether its contraption is assembled, its angle and speed. */
  public record Bearing(boolean running, float angle, float angularSpeed, @Nullable Entity contraption) {}

  @Nullable
  public static Bearing bearing(Level level, BlockPos pos) {
    BlockEntity entity = level.getBlockEntity(pos);
    if (entity == null || !type(BEARING).map(t -> t.isInstance(entity)).orElse(false)) return null;
    try {
      boolean running = (boolean) call(method(BEARING, "isRunning").orElseThrow(), entity);
      float angle = ((Number) call(method(BEARING, "getInterpolatedAngle", float.class).orElseThrow(), entity, 0f)).floatValue();
      float angular = ((Number) call(method(BEARING, "getAngularSpeed").orElseThrow(), entity)).floatValue();
      Object moved = call(method(BEARING, "getMovedContraption").orElseThrow(), entity);
      return new Bearing(running, angle, angular, moved instanceof Entity contraption ? contraption : null);
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn("bearing", e);
      return null;
    }
  }

  // ---- Stress values from the pack's config ---------------------------------------------------

  /** A block's stress impact per RPM, from Create's registry and config; 0 when unknown. */
  public static double impact(Block block) {
    return stressValue("getImpact", block);
  }

  /** A generator's stress capacity per RPM; 0 when unknown. */
  public static double capacity(Block block) {
    return stressValue("getCapacity", block);
  }

  private static double stressValue(String name, Block block) {
    try {
      var method = method(STRESS, name, Block.class);
      return method.isPresent() ? ((Number) call(method.get(), null, block)).doubleValue() : 0;
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn(name, e);
      return 0;
    }
  }

  /** The RPM a generator declares in {@code BlockStressValues.RPM}; 0 when it declares none. */
  public static int generatedRpm(Block block) {
    try {
      var registry = type(STRESS).orElseThrow().getField("RPM").get(null);
      Object rpm = type("com.simibubi.create.api.registry.SimpleRegistry").orElseThrow()
          .getMethod("get", Object.class).invoke(registry, block);
      if (rpm == null) return 0;
      return ((Number) type(STRESS + "$GeneratedRpm").orElseThrow().getMethod("value").invoke(rpm)).intValue();
    } catch (ReflectiveOperationException | RuntimeException e) {
      warn("BlockStressValues.RPM", e);
      return 0;
    }
  }

  @Nullable
  public static Block block(String id) {
    return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(null);
  }

  /**
   * R for the workshop from the pack's live Create values: its wheels' capacity and speed, its pumps'
   * impact ({@link RuinRules#pumpSpeed}); 0 when Create or a value is missing.
   */
  public static int pumpSpeed(Block wheel, Block pump, int wheels, int pumps) {
    int rpm = generatedRpm(wheel);
    if (rpm <= 0) rpm = largeWheelRpm(wheel);
    return RuinRules.pumpSpeed(capacity(wheel), rpm, impact(pump), wheels, pumps);
  }

  /** Create's water wheels turn at 8 RPM divided by their size when they declare no RPM. */
  static int largeWheelRpm(Block wheel) {
    String id = BuiltInRegistries.BLOCK.getKey(wheel).toString();
    return id.equals("create:large_water_wheel") ? 4 : id.equals("create:water_wheel") ? 8 : 0;
  }
}
