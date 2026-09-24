package dev.entrelumen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Reads the Silent Gear state the luminous set bonus needs without linking against Silent Gear:
 * whether a gear stack carries the {@code entrelumen:radiant} trait (every part made of the Luminous
 * Ingot gives it) and whether the stack is broken. Handles are resolved once; without Silent Gear,
 * or if its API moved, every query answers {@code false} and the luminous set works on its own.
 */
public final class SilentGearCompat {
  /** The trait that the luminous Silent Gear material grants; data in entrelumen:silentgear_traits. */
  public static final ResourceLocation RADIANT = ResourceLocation.fromNamespaceAndPath("entrelumen", "radiant");
  public static final ResourceLocation MATERIAL = ResourceLocation.fromNamespaceAndPath("entrelumen", "luminous");

  private static final Handles HANDLES = Handles.resolve();

  private SilentGearCompat() {}

  private record Handles(MethodHandle traitLevel, MethodHandle broken, Object radiant) {
    static Handles resolve() {
      if (!ModList.get().isLoaded("silentgear")) return null;
      try {
        var lookup = MethodHandles.publicLookup();
        Class<?> resource = Class.forName("net.silentchaos512.gear.api.util.DataResource");
        Object radiant = lookup.findStatic(resource, "trait", MethodType.methodType(resource, ResourceLocation.class))
            .invoke(RADIANT);
        MethodHandle level = lookup.findStatic(Class.forName("net.silentchaos512.gear.util.TraitHelper"),
            "getTraitLevel", MethodType.methodType(int.class, ItemStack.class, resource));
        MethodHandle broken = lookup.findStatic(Class.forName("net.silentchaos512.gear.util.GearHelper"),
            "isBroken", MethodType.methodType(boolean.class, ItemStack.class));
        return new Handles(level, broken, radiant);
      } catch (Throwable unavailable) {
        com.mojang.logging.LogUtils.getLogger().warn("ENTRELUMEN Silent Gear API unavailable: {}", unavailable.toString());
        return null;
      }
    }
  }

  public static boolean available() {
    return HANDLES != null;
  }

  private static boolean silentGear(ItemStack stack) {
    return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("silentgear");
  }

  /** Radiant trait level of a Silent Gear stack; 0 for anything else. */
  public static int radiantLevel(ItemStack stack) {
    if (HANDLES == null || !silentGear(stack)) return 0;
    try {
      return (int) HANDLES.traitLevel.invoke(stack, HANDLES.radiant);
    } catch (Throwable error) {
      return 0;
    }
  }

  public static boolean broken(ItemStack stack) {
    if (HANDLES == null || !silentGear(stack)) return false;
    try {
      return (boolean) HANDLES.broken.invoke(stack);
    } catch (Throwable error) {
      return false;
    }
  }

  /** A Silent Gear piece made with the Luminous Ingot that still works. */
  public static boolean radiant(ItemStack stack) {
    return radiantLevel(stack) > 0 && !broken(stack);
  }
}
